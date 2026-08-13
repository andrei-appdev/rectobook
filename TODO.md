# TODO

## Repo hygiene

- [ ] **Add a top-level `LICENSE`.** DjVuLibre is GPLv2+, so the combined work
      falls under the GPL once distributed. The only license text currently in
      the repo is `app/src/main/cpp/djvulibre/COPYING`, which reads as covering
      just the vendored library rather than this app. Needed before the code
      goes anywhere public.
- [ ] **Add `.gitattributes`.** Git reported LF→CRLF conversion for `gradlew`
      and the `.properties` files. Committed blobs are normalized to LF, so
      Linux/macOS/CI clones are fine, but a future checkout can rewrite the
      local `gradlew` to CRLF and break it under Git Bash. `* text=auto` plus
      `gradlew text eol=lf` prevents this.

## Features

- [ ] **`content://` URI support.** `DjvuNative.nativeOpenDocument` takes a
      filesystem path only, so the standard Android file picker cannot feed it.
      Needs either a copy-to-cache step or the ddjvu streaming API
      (`ddjvu_document_create` + `ddjvu_stream_write`).
- [ ] Page navigation (next/previous, jump to page).
- [ ] Tiled rendering with zoom and pan — full-page bitmaps do not scale to
      large scans at high zoom.
- [ ] Text layer: extraction, search, selection.
- [ ] Document outline / bookmarks.
- [ ] Bitmap reuse and a page cache; recycle aggressively, since page bitmaps
      are large.

## Verification gaps

Everything below builds and links, but is not exercised at runtime yet.

- [ ] **Colour channel order.** The bundled sample is bitonal, where R=G=B on
      every pixel, so a swapped red/blue channel would look identical. The
      mask/XOR choice in `djvu_jni.cpp` is reasoned from DjVuLibre's source
      (`ddjvuapi.cpp:2215`) but unproven. Render a colour document to confirm.
- [ ] **ARM ABIs at runtime.** `arm64-v8a` and `armeabi-v7a` compile and link,
      but only `x86_64` has actually run (emulator). Test on real hardware.
- [ ] **Memory behaviour on large pages.** The sample renders at ~1030px wide;
      nothing has been tested at high zoom or on very large scans.
- [ ] Behaviour on malformed or truncated `.djvu` files.

## Environment notes

- Gradle 8.11.1 supports Java 8–23. Android Studio's bundled JBR is Java 25,
  so the Gradle JDK is pinned to Temurin 21 via `.idea/gradle.xml` and
  `.gradle/config.properties`. Both are gitignored (machine-specific paths),
  so **a fresh clone must set its Gradle JDK again**. Command-line builds need
  `JAVA_HOME` pointed at a Java ≤23 install.
- `libdjvu` is compiled at C++14 (`app/src/main/cpp/djvulibre/CMakeLists.txt`)
  because upstream still uses the `register` storage class, a hard error from
  C++17 on. The app's own code stays at C++17.
- JNI calls block while DjVuLibre decodes — always call them off the main
  thread.