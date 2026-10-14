# Rectobook

A simple, lightweight reader for DjVu and PDF books on Android. It is made for
scanned books, textbooks and papers: open a file and read, with nothing in the
way. No ads, no tracking, no account - and it is free software (GPL).

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
