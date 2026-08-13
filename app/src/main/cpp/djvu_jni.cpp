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

#include <cstring>

#include "djvulibre/libdjvu/ddjvuapi.h"

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

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_djvureader_app_DjvuNative_nativeVersion(JNIEnv *env, jobject) {
    return env->NewStringUTF(ddjvu_get_version_string());
}

JNIEXPORT jlong JNICALL
Java_com_djvureader_app_DjvuNative_nativeOpenDocument(JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) {
        return 0;
    }

    auto *doc = new Document();
    doc->context = ddjvu_context_create("djvureader");
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
Java_com_djvureader_app_DjvuNative_nativeCloseDocument(JNIEnv *, jobject, jlong handle) {
    releaseDocument(asDocument(handle));
}

JNIEXPORT jint JNICALL
Java_com_djvureader_app_DjvuNative_nativeGetPageCount(JNIEnv *, jobject, jlong handle) {
    Document *doc = asDocument(handle);
    if (!doc || !doc->document) {
        return 0;
    }
    return ddjvu_document_get_pagenum(doc->document);
}

// Returns {width, height, dpi}, or null if the page info could not be read.
// This queries page geometry without fully decoding the page.
JNIEXPORT jintArray JNICALL
Java_com_djvureader_app_DjvuNative_nativeGetPageInfo(JNIEnv *env, jobject, jlong handle,
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

// Renders part of a page into the supplied ARGB_8888 bitmap.
//
// The page is conceptually scaled to pageWidth x pageHeight, and the region of
// that scaled page starting at (clipX, clipY) and the size of the bitmap is
// what gets written. Passing the bitmap's own dimensions as the page size with
// a (0,0) clip renders the whole page; a larger page size with a moving clip is
// how zoomed/tiled rendering works.
JNIEXPORT jboolean JNICALL
Java_com_djvureader_app_DjvuNative_nativeRenderPage(JNIEnv *env, jobject, jlong handle,
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