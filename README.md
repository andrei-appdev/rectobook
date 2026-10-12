# Rectobook

An Android reader for DjVu and PDF documents, aimed at scanned books and
technical literature. DjVu decoding is done natively by a vendored DjVuLibre
reached through a hand-written JNI layer; PDF goes through PDFium.

- **Format abstraction.** Both formats implement one `PagedDocument` interface
  (page count and size, render, region render, text layer, character hit-testing,
  links, outline). The format is sniffed from the file's bytes, and the UI never
  branches on it.
- **Two-layer rendering.** Each page is rendered once at fit-width. Zooming past
  that draws viewport-sized tiles over it, cut from the page at the current
  scale, so detail stays sharp at any zoom without a full-page bitmap growing
  with it. Pages and tiles have separate LRU caches.
- **Text layer.** Full-text search, word selection and internal and external
  links, from the DjVu OCR layer or PDF text. Geometry crosses the native
  boundary as fractions of the page, so highlights land at every zoom.
- **Native boundary.** Structured results (words, links, outline) cross JNI as
  one big-endian `ByteArray` rather than as Java strings, since OCR text is not
  guaranteed to be valid modified UTF-8. All native calls run off the main
  thread and are serialised against document close by a coroutine `Mutex`.
- **Contrast.** A levels stretch for faded scans, applied as a draw-time
  `ColorFilter` rather than baked into decoded bitmaps.
- **Documents and library.** `content://` URIs from the system picker or another
  app are copied into the cache and opened by path; recent documents persist in
  a Preferences DataStore.
  - Light and dark themes following the system; English, Russian and Spanish.

## Technology stack

| | |
| --- | --- |
| Language | Kotlin 2.0, C++17 (JNI); DjVuLibre built at C++14 |
| UI | Jetpack Compose (BOM 2024.12), single activity, no fragments |
| State | AndroidX ViewModel, Kotlin coroutines |
| Persistence | AndroidX DataStore (Preferences) |
| Native build | CMake, NDK 27; `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| Build | Gradle 8.11, Android Gradle Plugin 8.7, JDK 17–21 |
| Platform | minSdk 24 (Android 7.0), targetSdk 35 |

## Components

| Component | Role | License |
| --- | --- | --- |
| [DjVuLibre](https://djvu.sourceforge.net/) 3.5 | DjVu decoding, vendored source in `app/src/main/cpp/djvulibre` | GPL-2.0-or-later |
| [PdfiumAndroid](https://github.com/johngray1965/PdfiumAndroid) 1.0.20 (Google PDFium) | PDF rendering and text, prebuilt AAR | Apache-2.0 / BSD-3-Clause |
| [Fira Sans / Fira Sans Condensed](https://github.com/mozilla/Fira) | Bundled UI typefaces | SIL OFL-1.1 |
| AndroidX, Jetpack Compose, Kotlin stdlib | App framework | Apache-2.0 |

## Building

```
./gradlew :app:assembleDebug
```

Needs the Android SDK with NDK 27 and `JAVA_HOME` pointing at a JDK 17–21;
newer JDKs are rejected by this AGP version. A clean build compiles DjVuLibre
for three ABIs and takes a few minutes.

## License

GPL-2.0-or-later — see [`LICENSE`](LICENSE). Third-party notices are in
[`licenses/README.md`](licenses/README.md).
