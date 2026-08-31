# TODO

## Tasks
- add app versioning

- multilanguage support?

## PDF, left over from the first pass

- [ ] **Password-protected files** are reported and refused, not prompted for.
      `PdfiumCore.newDocument` takes a password, so this is a dialog and a
      retry rather than new decoding work.
- [ ] **Page rotation is unverified.** PDFium's page width and height are
      believed to account for `/Rotate`, but nothing tested here had a rotated
      page. A landscape scan is the case to check.
- [ ] **Native code is now 13.2 MB across three ABIs**, of which PDFium is
      4.9 MB on arm64 and 3.3 MB on armeabi-v7a. A device only installs its
      own, so this is an App Bundle / ABI-splits question rather than a real
      per-user cost - but the project builds one universal APK today.

## Rendering

- [ ] **`documentLock` now serialises tile decodes too.** Every render, text
      extraction, link lookup and per-drag `selectionRects` call queues on the
      one mutex, and zoomed reading adds a tile decode per settled pan. Nothing
      observably stalls today, but a drag over text at high zoom is the case
      that would show it first.
- [ ] **A cancelled render still does its work.** The JNI call blocks and is
      not interruptible, so scrolling a page away mid-decode finishes the
      decode and then throws the bitmap away without caching it. Banking the
      result instead would cost nothing.
- [ ] **Nothing is prefetched.** Pages render when they enter composition, so a
      fast scroll shows blank paper first. `LazyColumn`'s own one-item
      lookahead is all there is.
- [ ] **PDF thumbnails block reader renders.** `PdfDocument` takes a
      process-wide `PdfiumLock`, so the library's thumbnailer and the reader
      contend on it.

## Repo hygiene

- [x] **Add a top-level `LICENSE`.** GPLv2 text at the repo root, with the
      component inventory and the distribution obligation in
      `licenses/README.md`. Source files carry no GPL header yet - the GPL
      recommends one per file, and nothing here has one.
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