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
- [x] **Native code is 13.2 MB across three ABIs.** A release build now comes
      out per ABI as well as universal (arm64 10.3 MB, universal 23.8 MB, from
      29.6 MB before R8). Keep `app/build/outputs/mapping/release/mapping.txt`
      with each GitHub release - without it, a user's stack trace from that
      build cannot be retraced.

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

move ## Small screens

Found driving the app at `wm size 480x854` / `wm density 240` - 320x569dp, the
tight end of what Android still ships. Everything works; what follows is what
stops fitting. The library, outline, search, go-to-page, link sheet, the full
Settings screen and both formats' rendering are all fine at that size, and
search highlights still land on the right words.

- [x] **The reader's settings panel truncates its segmented labels.** Fixed by
      widening the panel from 70% of the screen to 84%: at 320dp `WITH BARS`
      now draws in full in all four bar rows. The longer Russian `С панелями`
      and Spanish `Con barras` are still the first to run out of room, and are
      untested at this width.
- [ ] **In landscape on a short screen the panel is still a slot.** At 854x480
      it is now about 300px tall rather than 215, so the contrast slider - the
      control the panel is mostly opened for - is above the fold and the close
      X stays put while the settings under it scroll. What is left is that
      nothing says the rest scrolls: the panel's lower edge cuts a row of
      controls off cleanly, with no shadow or peek to suggest more below.
- [ ] **The page stepper disappears into the page under it.** The bottom bar is
      deliberately translucent so the page runs under it, but the two chevrons
      are thin strokes with nothing behind them: over a dense block of scanned
      text they read as part of the scan and cannot be found by eye. Standing
      them either side of the counter helps - the digits are heavy enough to
      find, and the arrows are now where the eye already is - but it does not
      make the strokes themselves any easier to see. Not specific to small
      screens; it depends on what is behind the bar. The navigation strip below
      already solves this for the system buttons by painting the app's own
      colour behind them.

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