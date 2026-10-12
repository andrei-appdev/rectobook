// JNI bridge over the DjVuLibre ddjvu API.
//
// The ddjvu API is asynchronous: calls kick off background decoding and report
// progress, completion and failure through a per-context message queue. Every
// entry point here presents a blocking interface to Kotlin, so each one follows
// the same shape - start the work, then pump the message queue until the
// corresponding job reports done. That means these calls block for as long as
// decoding takes and must not be invoked on the main thread.

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "djvulibre/libdjvu/ddjvuapi.h"
#include "djvulibre/libdjvu/miniexp.h"

namespace {

constexpr const char *kTag = "DjvuNative";

// A document plus the context that owns it. ddjvu contexts carry the decoded
// page cache, so keeping one per document lets close() reclaim everything.
struct Document {
    ddjvu_context_t *context = nullptr;
    ddjvu_document_t *document = nullptr;
};

Document *asDocument(jlong handle) {
    return reinterpret_cast<Document *>(handle);
}

// Drains the message queue, logging any errors. Pass wait=true to block until
// at least one message arrives - only safe when a job is still running, since
// otherwise there may be no further message to wake us.
bool drainMessages(ddjvu_context_t *context, bool wait) {
    bool ok = true;
    if (wait) {
        ddjvu_message_wait(context);
    }
    const ddjvu_message_t *message;
    while ((message = ddjvu_message_peek(context))) {
        if (message->m_any.tag == DDJVU_ERROR) {
            const char *text = message->m_error.message;
            __android_log_print(ANDROID_LOG_ERROR, kTag, "ddjvu error: %s",
                                text ? text : "(no detail)");
            ok = false;
        }
        ddjvu_message_pop(context);
    }
    return ok;
}

void appendInt(std::string &out, int value) {
    out.push_back(static_cast<char>((value >> 24) & 0xFF));
    out.push_back(static_cast<char>((value >> 16) & 0xFF));
    out.push_back(static_cast<char>((value >> 8) & 0xFF));
    out.push_back(static_cast<char>(value & 0xFF));
}

// Appends every piece of text in a hidden-text s-expression, each with the box
// it occupies on the page.
//
// A node is (type xmin ymin xmax ymax rest...), where rest is either the text
// itself for a leaf or the child nodes for anything above one. The coordinates
// are in the page's own pixels with the origin at the bottom left, which is
// DjVu's convention and not Android's; the Kotlin side flips them.
//
// The encoding is xmin, ymin, xmax, ymax, byte count, then that many UTF-8
// bytes, per word, big-endian. It is read straight back in Kotlin, so it never
// has to survive anything but this one hop.
void collectWords(miniexp_t exp, std::string &out) {
    if (!miniexp_consp(exp)) {
        return;
    }

    if (miniexp_symbolp(miniexp_car(exp))) {
        miniexp_t rest = miniexp_cdr(exp);
        int box[4];
        bool boxed = true;
        for (int &edge : box) {
            miniexp_t value = miniexp_car(rest);
            if (!miniexp_numberp(value)) {
                boxed = false;
                break;
            }
            edge = miniexp_to_int(value);
            rest = miniexp_cdr(rest);
        }

        if (boxed) {
            if (miniexp_stringp(miniexp_car(rest))) {
                const char *text = miniexp_to_str(miniexp_car(rest));
                if (text && *text) {
                    for (int edge : box) {
                        appendInt(out, edge);
                    }
                    const auto length = static_cast<int>(std::strlen(text));
                    appendInt(out, length);
                    out.append(text, length);
                }
                return;
            }
            while (miniexp_consp(rest)) {
                collectWords(miniexp_car(rest), out);
                rest = miniexp_cdr(rest);
            }
            return;
        }
    }

    // Not shaped like a text node after all, so walk it as a plain list.
    while (miniexp_consp(exp)) {
        collectWords(miniexp_car(exp), out);
        exp = miniexp_cdr(exp);
    }
}

void releaseDocument(Document *doc) {
    if (!doc) {
        return;
    }
    if (doc->document) {
        ddjvu_document_release(doc->document);
    }
    if (doc->context) {
        ddjvu_context_release(doc->context);
    }
    delete doc;
}

// The target of a `(maparea ...)`, which is either a bare string or a
// `(url "target" "hint")` form. Returns null for anything else, including the
// empty target a decorative area carries.
const char *mapareaTarget(miniexp_t area) {
    miniexp_t url = miniexp_car(miniexp_cdr(area));
    if (miniexp_stringp(url)) {
        return miniexp_to_str(url);
    }
    if (miniexp_consp(url) && miniexp_symbolp(miniexp_car(url))) {
        const char *name = miniexp_to_name(miniexp_car(url));
        if (name && std::strcmp(name, "url") == 0) {
            miniexp_t target = miniexp_car(miniexp_cdr(url));
            if (miniexp_stringp(target)) {
                return miniexp_to_str(target);
            }
        }
    }
    return nullptr;
}

// The bounding box of a maparea's shape, as {xmin, ymin, xmax, ymax} in the
// page's own pixels.
//
// DjVu allows rectangles, ovals and polygons. A reader only needs somewhere to
// aim a finger, so every shape is reduced to the box around it - a polygon
// hyperlink is rare, and a slightly generous target for one is better than
// ignoring it.
bool mapareaBox(miniexp_t area, int box[4]) {
    // (maparea url comment shape rest...) - so the shape is the fourth
    // element, past the comment. Searched for rather than indexed, because
    // what follows it varies and a list is cheap to walk.
    miniexp_t shape = miniexp_nil;
    const char *name = nullptr;
    for (miniexp_t rest = miniexp_cddr(area); miniexp_consp(rest); rest = miniexp_cdr(rest)) {
        miniexp_t candidate = miniexp_car(rest);
        if (!miniexp_consp(candidate) || !miniexp_symbolp(miniexp_car(candidate))) {
            continue;
        }
        const char *head = miniexp_to_name(miniexp_car(candidate));
        if (!head) {
            continue;
        }
        if (std::strcmp(head, "rect") == 0 || std::strcmp(head, "oval") == 0 ||
            std::strcmp(head, "text") == 0 || std::strcmp(head, "poly") == 0 ||
            std::strcmp(head, "line") == 0) {
            shape = candidate;
            name = head;
            break;
        }
    }
    if (!name) {
        return false;
    }

    std::vector<int> values;
    for (miniexp_t rest = miniexp_cdr(shape); miniexp_consp(rest); rest = miniexp_cdr(rest)) {
        miniexp_t value = miniexp_car(rest);
        if (!miniexp_numberp(value)) {
            break;
        }
        values.push_back(miniexp_to_int(value));
    }
    if (values.size() < 4) {
        return false;
    }

    // rect, oval and text are all x, y, width, height.
    if (std::strcmp(name, "rect") == 0 || std::strcmp(name, "oval") == 0 ||
        std::strcmp(name, "text") == 0) {
        box[0] = values[0];
        box[1] = values[1];
        box[2] = values[0] + values[2];
        box[3] = values[1] + values[3];
        return true;
    }
    // poly and line are a run of x, y pairs.
    if (std::strcmp(name, "poly") == 0 || std::strcmp(name, "line") == 0) {
        int xmin = values[0], xmax = values[0], ymin = values[1], ymax = values[1];
        for (size_t i = 0; i + 1 < values.size(); i += 2) {
            xmin = std::min(xmin, values[i]);
            xmax = std::max(xmax, values[i]);
            ymin = std::min(ymin, values[i + 1]);
            ymax = std::max(ymax, values[i + 1]);
        }
        box[0] = xmin;
        box[1] = ymin;
        box[2] = xmax;
        box[3] = ymax;
        return true;
    }
    return false;
}

// Flattens an outline into the packed buffer, depth first.
//
// The tree is `(bookmarks entry...)` where an entry is `("title" "target"
// child...)`, exactly as djvused prints it. Each entry is written as its depth,
// then its target, then its title, each string as a length and that many
// bytes. Depth is what lets Kotlin rebuild the tree from a flat run.
void collectOutline(miniexp_t entries, int depth, std::string &out) {
    for (miniexp_t rest = entries; miniexp_consp(rest); rest = miniexp_cdr(rest)) {
        miniexp_t entry = miniexp_car(rest);
        if (!miniexp_consp(entry)) {
            continue;
        }
        miniexp_t title = miniexp_car(entry);
        miniexp_t target = miniexp_car(miniexp_cdr(entry));
        if (!miniexp_stringp(title)) {
            continue;
        }
        const char *titleText = miniexp_to_str(title);
        const char *targetText = miniexp_stringp(target) ? miniexp_to_str(target) : "";
        if (!titleText) {
            continue;
        }

        appendInt(out, depth);
        const auto targetLength = static_cast<int>(std::strlen(targetText));
        appendInt(out, targetLength);
        out.append(targetText, targetLength);
        const auto titleLength = static_cast<int>(std::strlen(titleText));
        appendInt(out, titleLength);
        out.append(titleText, titleLength);

        collectOutline(miniexp_cddr(entry), depth + 1, out);
    }
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_rectobook_app_DjvuNative_nativeVersion(JNIEnv *env, jobject) {
    return env->NewStringUTF(ddjvu_get_version_string());
}

JNIEXPORT jlong JNICALL
Java_com_rectobook_app_DjvuNative_nativeOpenDocument(JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) {
        return 0;
    }

    auto *doc = new Document();
    doc->context = ddjvu_context_create("rectobook");
    if (!doc->context) {
        env->ReleaseStringUTFChars(jpath, path);
        releaseDocument(doc);
        return 0;
    }

    doc->document = ddjvu_document_create_by_filename_utf8(doc->context, path, /*cache=*/1);
    env->ReleaseStringUTFChars(jpath, path);
    if (!doc->document) {
        releaseDocument(doc);
        return 0;
    }

    // Wait for the document header, without which page count and page access
    // are not yet meaningful.
    while (!ddjvu_document_decoding_done(doc->document)) {
        drainMessages(doc->context, /*wait=*/true);
    }
    if (ddjvu_document_decoding_error(doc->document)) {
        releaseDocument(doc);
        return 0;
    }

    return reinterpret_cast<jlong>(doc);
}

JNIEXPORT void JNICALL
Java_com_rectobook_app_DjvuNative_nativeCloseDocument(JNIEnv *, jobject, jlong handle) {
    releaseDocument(asDocument(handle));
}

JNIEXPORT jint JNICALL
Java_com_rectobook_app_DjvuNative_nativeGetPageCount(JNIEnv *, jobject, jlong handle) {
    Document *doc = asDocument(handle);
    if (!doc || !doc->document) {
        return 0;
    }
    return ddjvu_document_get_pagenum(doc->document);
}

// Returns {width, height, dpi}, or null if the page info could not be read.
// This queries page geometry without fully decoding the page.
JNIEXPORT jintArray JNICALL
Java_com_rectobook_app_DjvuNative_nativeGetPageInfo(JNIEnv *env, jobject, jlong handle,
                                                    jint pageNumber) {
    Document *doc = asDocument(handle);
    if (!doc || !doc->document) {
        return nullptr;
    }

    ddjvu_pageinfo_t info;
    ddjvu_status_t status;
    while ((status = ddjvu_document_get_pageinfo(doc->document, pageNumber, &info)) < DDJVU_JOB_OK) {
        drainMessages(doc->context, /*wait=*/true);
    }
    if (status >= DDJVU_JOB_FAILED) {
        return nullptr;
    }

    jint values[3] = {info.width, info.height, info.dpi};
    jintArray result = env->NewIntArray(3);
    if (!result) {
        return nullptr;
    }
    env->SetIntArrayRegion(result, 0, 3, values);
    return result;
}

// Returns the UTF-8 bytes of a page's hidden text layer, or null if the page
// could not be read. An empty array means the page carries no text - most DjVu
// scans have a text layer from OCR, but nothing guarantees one.
//
// Bytes rather than a jstring: NewStringUTF wants Java's modified UTF-8, which
// differs from real UTF-8 above the BMP, and this text comes from an OCR engine
// we do not control. Kotlin decodes it properly.
// Returns a page's hyperlinks, packed as:
//
//   rotation, then per link: xmin, ymin, xmax, ymax, targetLength, target bytes
//
// all integers big-endian, matching nativeGetPageWords.
//
// The rotation leads because link coordinates need it and text coordinates do
// not. ddjvuapi says so itself, under a "[brain damage warning]": maparea
// coordinates are relative to the rotated page while the hidden text is
// relative to the unrotated one. Kotlin does the mapping, where the page's
// size is already known.
// Returns the document's outline, packed as a depth-first run of
// depth, target length, target, title length, title - all integers
// big-endian, all strings UTF-8. Empty when the document has no outline.
JNIEXPORT jbyteArray JNICALL
Java_com_rectobook_app_DjvuNative_nativeGetOutline(JNIEnv *env, jobject, jlong handle) {
    Document *doc = asDocument(handle);
    if (!doc || !doc->document) {
        return nullptr;
    }

    miniexp_t outline;
    while ((outline = ddjvu_document_get_outline(doc->document)) == miniexp_dummy) {
        drainMessages(doc->context, /*wait=*/true);
    }

    std::string collected;
    if (miniexp_consp(outline)) {
        // The list opens with the `bookmarks` symbol; the entries follow it.
        collectOutline(miniexp_cdr(outline), /*depth=*/0, collected);
        ddjvu_miniexp_release(doc->document, outline);
    }

    const auto size = static_cast<jsize>(collected.size());
    jbyteArray result = env->NewByteArray(size);
    if (!result) {
        return nullptr;
    }
    if (size > 0) {
        env->SetByteArrayRegion(result, 0, size,
                                reinterpret_cast<const jbyte *>(collected.data()));
    }
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_rectobook_app_DjvuNative_nativeGetPageLinks(JNIEnv *env, jobject, jlong handle,
                                                     jint pageNumber) {
    Document *doc = asDocument(handle);
    if (!doc || !doc->document) {
        return nullptr;
    }

    ddjvu_pageinfo_t info;
    ddjvu_status_t status;
    while ((status = ddjvu_document_get_pageinfo(doc->document, pageNumber, &info)) <
           DDJVU_JOB_OK) {
        drainMessages(doc->context, /*wait=*/true);
    }
    if (status >= DDJVU_JOB_FAILED) {
        return nullptr;
    }

    miniexp_t anno;
    while ((anno = ddjvu_document_get_pageanno(doc->document, pageNumber)) == miniexp_dummy) {
        drainMessages(doc->context, /*wait=*/true);
    }

    std::string collected;
    appendInt(collected, info.rotation);

    if (miniexp_consp(anno)) {
        // Zero-terminated, and malloc'd by the library for us to free. The
        // expressions it points at belong to the annotations, so they stay
        // valid until those are released.
        miniexp_t *areas = ddjvu_anno_get_hyperlinks(anno);
        if (areas) {
            for (miniexp_t *area = areas; *area; ++area) {
                int box[4];
                const char *target = mapareaTarget(*area);
                if (!target || !*target || !mapareaBox(*area, box)) {
                    continue;
                }
                for (int edge : box) {
                    appendInt(collected, edge);
                }
                const auto length = static_cast<int>(std::strlen(target));
                appendInt(collected, length);
                collected.append(target, length);
            }
            free(areas);
        }
        ddjvu_miniexp_release(doc->document, anno);
    }

    const auto size = static_cast<jsize>(collected.size());
    jbyteArray result = env->NewByteArray(size);
    if (!result) {
        return nullptr;
    }
    if (size > 0) {
        env->SetByteArrayRegion(result, 0, size,
                                reinterpret_cast<const jbyte *>(collected.data()));
    }
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_rectobook_app_DjvuNative_nativeGetPageWords(JNIEnv *env, jobject, jlong handle,
                                                     jint pageNumber) {
    Document *doc = asDocument(handle);
    if (!doc || !doc->document) {
        return nullptr;
    }

    miniexp_t text;
    while ((text = ddjvu_document_get_pagetext(doc->document, pageNumber, "word")) ==
           miniexp_dummy) {
        drainMessages(doc->context, /*wait=*/true);
    }

    std::string collected;
    // Anything that is not a list is either the empty list (no text on this
    // page) or one of the `failed`/`stopped` symbols. Only lists hold text, and
    // only lists are registered with the document's garbage collector.
    if (miniexp_consp(text)) {
        collectWords(text, collected);
        ddjvu_miniexp_release(doc->document, text);
    }

    const auto size = static_cast<jsize>(collected.size());
    jbyteArray result = env->NewByteArray(size);
    if (!result) {
        return nullptr;
    }
    if (size > 0) {
        env->SetByteArrayRegion(result, 0, size,
                                reinterpret_cast<const jbyte *>(collected.data()));
    }
    return result;
}

// Renders part of a page into the supplied ARGB_8888 bitmap.
//
// The page is conceptually scaled to pageWidth x pageHeight, and the region of
// that scaled page starting at (clipX, clipY) and the size of the bitmap is
// what gets written. Passing the bitmap's own dimensions as the page size with
// a (0,0) clip renders the whole page; a larger page size with a moving clip is
// how zoomed/tiled rendering works.
JNIEXPORT jboolean JNICALL
Java_com_rectobook_app_DjvuNative_nativeRenderPage(JNIEnv *env, jobject, jlong handle,
                                                   jint pageNumber, jobject bitmap,
                                                   jint pageWidth, jint pageHeight,
                                                   jint clipX, jint clipY) {
    Document *doc = asDocument(handle);
    if (!doc || !doc->document || !bitmap) {
        return JNI_FALSE;
    }

    AndroidBitmapInfo bitmapInfo;
    if (AndroidBitmap_getInfo(env, bitmap, &bitmapInfo) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return JNI_FALSE;
    }
    if (bitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "bitmap must be ARGB_8888");
        return JNI_FALSE;
    }
    if (bitmapInfo.width == 0 || bitmapInfo.height == 0) {
        return JNI_FALSE;
    }
    if (pageWidth <= 0 || pageHeight <= 0 || clipX < 0 || clipY < 0) {
        return JNI_FALSE;
    }
    // The requested region has to sit inside the scaled page.
    if (clipX + static_cast<jint>(bitmapInfo.width) > pageWidth ||
        clipY + static_cast<jint>(bitmapInfo.height) > pageHeight) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "clip %dx%d at (%d,%d) exceeds page %dx%d", bitmapInfo.width,
                            bitmapInfo.height, clipX, clipY, pageWidth, pageHeight);
        return JNI_FALSE;
    }

    ddjvu_page_t *page = ddjvu_page_create_by_pageno(doc->document, pageNumber);
    if (!page) {
        return JNI_FALSE;
    }
    while (!ddjvu_page_decoding_done(page)) {
        drainMessages(doc->context, /*wait=*/true);
    }
    if (ddjvu_page_decoding_error(page)) {
        ddjvu_page_release(page);
        return JNI_FALSE;
    }

    // Android's ARGB_8888 is byte-ordered R,G,B,A in memory. RGBMASK32 writes a
    // native-endian word per pixel, so on little-endian ABIs these masks land
    // each channel in the right byte; the fourth value is XORed into the result,
    // which is what sets the alpha byte (untouched by the RGB masks) to opaque.
    unsigned int masks[4] = {0x000000FFu, 0x0000FF00u, 0x00FF0000u, 0xFF000000u};
    ddjvu_format_t *format = ddjvu_format_create(DDJVU_FORMAT_RGBMASK32, 4, masks);
    if (!format) {
        ddjvu_page_release(page);
        return JNI_FALSE;
    }
    // Match Android's top-left origin; ddjvu defaults to PostScript-style
    // bottom-up ordering.
    ddjvu_format_set_row_order(format, 1);
    ddjvu_format_set_y_direction(format, 1);

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        ddjvu_format_release(format);
        ddjvu_page_release(page);
        return JNI_FALSE;
    }

    // ddjvu_page_render leaves the buffer untouched when it cannot produce an
    // image, so start from opaque white rather than whatever the bitmap held.
    std::memset(pixels, 0xFF, static_cast<size_t>(bitmapInfo.stride) * bitmapInfo.height);

    ddjvu_rect_t pageRect;
    pageRect.x = 0;
    pageRect.y = 0;
    pageRect.w = static_cast<unsigned int>(pageWidth);
    pageRect.h = static_cast<unsigned int>(pageHeight);

    ddjvu_rect_t renderRect;
    renderRect.x = clipX;
    renderRect.y = clipY;
    renderRect.w = bitmapInfo.width;
    renderRect.h = bitmapInfo.height;

    const int rendered = ddjvu_page_render(page, DDJVU_RENDER_COLOR, &pageRect, &renderRect,
                                           format, bitmapInfo.stride,
                                           static_cast<char *>(pixels));

    AndroidBitmap_unlockPixels(env, bitmap);
    ddjvu_format_release(format);
    ddjvu_page_release(page);

    return rendered ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"