# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An Android DjVu + PDF reader (`com.rectobook.app`), Kotlin + Jetpack Compose,
with DjVuLibre vendored as C++ under `app/src/main/cpp/djvulibre` and reached
through a hand-written JNI layer. PDF goes through the prebuilt
`io.legere:pdfiumandroid` AAR instead — PDFium builds via GN/depot_tools, which
is a project of its own.

Single Gradle module `:app`, minSdk 24 / targetSdk 35, one activity, no
fragments, no DI framework, no navigation library.

## Build and run

`JAVA_HOME` is not set in this environment and Gradle fails outright without it.
Android Studio's bundled JBR is Java 25, which AGP rejects with a message that
is only the version number. Use the 21 JDK, and give Gradle an absolute project
path — the wrapper is not on `PATH`:

```powershell
$env:JAVA_HOME = 'C:\Users\Andrei\.jdks\jbr-21.0.11'
& 'C:\Projects\djvu_reader\gradlew.bat' -p C:\Projects\djvu_reader :app:assembleDebug --console=plain -q
```

The `$env:` assignment does not persist between tool calls — repeat it, or chain
build and install in one call. A clean build compiles DjVuLibre for three ABIs
and takes minutes; an incremental Kotlin build is seconds.

`:app:assembleRelease` signs with the key named in `keystore.properties` and
writes four APKs to `app/build/outputs/apk/release/`, then copies them to
`app/build/outputs/github/` under their download names: `rectobook-<version>.apk`
(universal) and `rectobook-<version>-<abi>.apk`. Release runs R8
and resource shrinking, so it needs its own pass on a device - debug proves
nothing about what the shrinker removed. Splits switch on whenever a task name
contains "Release", so build debug and release in **separate** Gradle calls,
or debug comes out split too and `app-debug.apk` is missing.

For installing, launching, screenshotting and driving the UI on the emulator,
**use the `run-on-emulator` skill** (`.claude/skills/run-on-emulator/`) — it
carries the adb paths, the coordinate scaling, and the quirks that cost time
(screenshots must be piped through Bash; PowerShell corrupts the PNG).

There are no test source sets. `app/src/` contains only `main/`, although the
JUnit/Espresso/Compose-test dependencies are declared. Adding a test means
creating `app/src/test/` or `app/src/androidTest/` first.

## Architecture

**Format abstraction.** `PagedDocument` (`PagedDocument.kt`) is the whole of what
the reader needs from a file format: page count, page size, render, render
region, page text, text rects, char-at-point, links, outline. `DjvuDocument` and
`PdfDocument` implement it; `openDocument()` sniffs the bytes via
`DocumentFormat.of(file)` and picks one. Anything format-specific belongs behind
this interface — the reader UI never branches on format except to name it.

Geometry crossing this boundary is in **fractions of the page** (`PageRect`), not
pixels, because a page is rendered at whatever width the viewport and zoom ask
for and a highlight has to land on the text at every one of them.

**Native layer.** `DjvuNative.kt` is the raw JNI surface (opaque `Long` handles)
over `app/src/main/cpp/djvu_jni.cpp`. Structured results (words, links, outline)
cross as one `ByteArray` with a documented big-endian encoding rather than as
object arrays or JNI strings — the string call expects Java's modified UTF-8,
which OCR output is under no obligation to be. **Every JNI call blocks while
DjVuLibre decodes; none may run on the main thread.**

`libdjvu` compiles at C++14 (its own `CMakeLists.txt`) because upstream still
uses the `register` storage class, a hard error from C++17 on. The app's own
C++ stays at C++17.

**State.** `ReaderViewModel` owns the open document across configuration changes
and closes the native handle in `onCleared`. All document access is serialised
through a `documentLock` `Mutex` on `Dispatchers.IO` — a render in flight must
not race the document being closed. It also owns the page bitmap `LruCache`
(sized off the heap, evicted bitmaps deliberately *not* recycled), the page-text
cache, link cache, search, selection and outline state.

**Rendering is two layers.** `renderPage` draws a whole page at **fit-width and
never at more** — zooming does not re-render it. Detail above that scale comes
from `renderTile`, which calls `PagedDocument.renderRegion` for just the part of
the page on screen and draws it over the whole-page bitmap. A tile is
viewport-sized whatever the zoom, so 5× costs less memory than a whole page
grown to 5× did, and `MAX_RENDER_PIXELS` stopped being a sharpness ceiling.
`PageView` decides what to ask for from its own `onGloballyPositioned` bounds,
snapped to a grid and given a skirt so a drag asks for a handful of tiles
rather than one per frame; the whole-page bitmap stays underneath throughout,
so a page never blanks. Tiles live in their own `LruCache`, apart from the page
one they would otherwise evict.

**Tone is a draw-time filter.** The contrast setting (`ContrastSheet.kt`,
persisted globally by `LibraryStore`) is a `ColorFilter` levels stretch passed
to both the page `Image` and the tile draw — never baked into a decode, so it
costs no memory, applies to DjVu and PDF alike, and moves on the same frame as
the slider.

**Screens.** `MainActivity.kt` holds the reader itself (page list, zoom, pan,
selection, links) and switches on `ReaderState` — Empty renders `LibraryScreen`,
Loaded renders `DocumentReader`. Other screens are one file each: `SearchScreen`,
`OutlineScreen`, `AboutScreen`, `GoToPageSheet`, `OpenLinkSheet`. Chrome widgets
shared across them (`IconAction`, `PrimaryAction`, `StrokeIcon`, `Hairline`,
the bars, the page slider) live in `ReaderChrome.kt`.

**Documents are copied in.** Android hands out `content://` URIs; DjVuLibre opens
by filesystem path. `DocumentImporter` copies into the cache on every open (the
bytes behind a URI can change). `LibraryStore` persists recents as one JSON
string in a Preferences DataStore, keyed by the persisted content URI.

## Conventions that matter here

- **Design system.** `DESIGN_PLAN.md` is the source of truth: a blueprint /
  technical-drawing look, radius 0 everywhere, hairline borders instead of
  filled surfaces, Fira Sans / Fira Sans Condensed bundled as OFL TTFs. Read
  colours from `Industry` (the `IndustryPalette` composition local), not from
  `MaterialTheme` — the Material scheme is filled in only for the few remaining
  Material parts (sheets, spinner). Light and dark both exist and follow the
  system; there is no in-app switch.
- **Strings.** Every string the reader sees is in `res/values/strings.xml`, with
  `values-ru` and `values-es` translations and `res/xml/locales_config.xml`
  listing them. Labels the design sets in capitals are stored sentence-case and
  uppercased at draw time via `upperStringResource` — Turkish and Greek casing
  is not a decision to bake into a resource or hand to a translator.
- **Comments explain why, not what.** The existing code documents the decision
  behind a piece of code — why a cache is not recycled, why words are joined in
  the JNI rather than by ddjvu, why the page gap is 2dp. Match that register.
- The app logs nothing of its own; there is no `Log` tag to filter on. To trace
  something, add a temporary one.

## Other files

- `TODO.md` — open work, known gaps, and environment notes. Keep it current;
  the QA subagent files findings here.
- `LICENSE` / `licenses/` — the app is GPL-2.0-or-later, because DjVuLibre is
  GPLv2+ and compiled in rather than isolated behind a process boundary. The
  root `LICENSE` is the GPLv2 text; `licenses/README.md` is the component
  inventory (DjVuLibre, PdfiumAndroid/PDFium, Fira Sans) and the note that
  shipping a build obliges offering the source of the whole app.
- `store/` — artwork for the Play listing. `play-icon-512.png` is the launcher
  mark rendered off the same geometry as `res/drawable/ic_launcher_foreground.xml`,
  cropped to the centre 72dp a launcher mask leaves visible — Play rounds the
  corners itself. The legacy `mipmap-*dpi` PNGs are the same crop; there is no
  committed render script, so regenerate them together if the mark changes.
- `/reviews` is gitignored — design/QA review runs with their screenshots.
