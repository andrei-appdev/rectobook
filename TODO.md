# TODO

## Tasks
- add app versioning

- multilanguage support?
- menu with settings, about and other options?

## Bugs
- Page counter reads `56 / 57` at the true end of the document. `currentPage` is
  `firstVisibleItemIndex + 1`, and at the bottom of the list the topmost visible
  page is the second-to-last one.

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