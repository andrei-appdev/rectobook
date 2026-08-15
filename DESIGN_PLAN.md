# Applying the "Industry" design

Source: Claude Design project *Mobile djvu reader app*
(`fcf8b6b3-2103-402f-846c-ecf9886b6512`), file `DjVu Reader.dc.html`, with
`styles.css` as the token source of truth. `android-frame.jsx` is only the
preview device bezel and `support.js` is the generated preview runtime
(`sc-if` / `sc-for` / `DCLogic`); neither affects the Android implementation.

## The design system

A blueprint / technical-drawing look, and a wholesale departure from the
current Material 3 defaults.

- **Palette** — background `#f2f2f3`, surface `#e9e9ea`, text `#1d1f20`,
  accent `#5980a6`, accent-700 `#416180`, dividers at 16% text.
- **Type** — Barlow Condensed (headings, 600) and Barlow (body). Uppercase,
  letterspaced micro-labels; tabular numerals for anything counting pages.
- **Shape** — radius 0 everywhere; the stylesheet explicitly squares off
  `.card, .btn, .input, .tag, .seg, .dialog`. Cards and dialogs are
  transparent with a hairline border rather than filled surfaces.
- **Signature motif** — `.blueprint > .corner`: registration marks sitting
  6px outside each corner. *Still in the system, no longer used by these
  screens* — the design has since taken the marks off the `OPEN FILE` button,
  the recent cards and the go-to-page sheet, which was everywhere this app drew
  them.

Five screens: library, browse, reader, go-to-page, search.

## Steps

**1. Theme foundation** ✅ — `ui/theme/`: Industry colour scheme, Barlow
typography, `Shapes` all `RectangleShape`, and a `Modifier.blueprintCorners()`
drawing the registration marks. Swap `MaterialTheme` for `DjvuTheme`. Existing
screens then read as Industry with no structural change.

**2. Library screen** ✅ — `DJVU READER` header, corpus line, `OPEN FILE` button,
and Recent cards with a page glyph, title, meta, progress
bar and `P.142` label. Needs `RecentDocument` to carry `pageCount` and
`sizeBytes`; today it stores only key, name, last page and timestamp, so the
meta line and progress bar would otherwise be invented. The card's page
thumbnail is a *drawn glyph* in the design, not a real render, so no document
needs opening to show it.

**3. Reader chrome** ✅ — auto-hiding top and bottom bars (tap to toggle,
`translateY(±102%)`, 220 ms) replacing the fixed bar and floating zoom pill.
Top: back, title, `djvu · N pp`, search. Bottom: page button, page slider,
segmented −/`100%`/+, plus the centre zoom flash. A reskin of the controls
only; the zoom and scroll mechanics stay as they are.

**4. Go-to-page sheet** ✅ — bottom sheet instead of the `AlertDialog`, carrying
a numeric field and `JUMP`.

*The quick marks are gone.* They were drawn as a document outline (Cover,
Contents, Ch. 4…), which is not extracted: `ddjvu_document_get_outline` returns
entries as `(name url subs…)` and the url may name a page (`#chapter1.djvu`)
rather than number it (`#12`). Resolving a name needs
`DjVuDocument::url_to_page`, which is C++ and not in the ddjvu C API, so a JNI
pass would resolve some entries and silently drop the rest. It was built with
`1 First` / `N Last` standing in; the design has since dropped the row
altogether, so the sheet is now just the field and the button. Real outline
support belongs with search, which needs C++-level access too.

**5. Page slider** ✅ — a slider across the whole document in the bottom bar,
where the decorative progress line used to be. Seeks live, as the design's
range input does.

**6. Search** ✅ *(not a reskin)* — `nativeGetPageText` over
`ddjvu_document_get_pagetext`, walking the miniexp tree per page, then the hit
list with the highlighted term.

*Words are joined here, not by ddjvu.* Asking for the text at `page`
granularity returns one pre-joined string in which some words run together —
`DjVu is an image` came back as `DjVuisanimage` — so the JNI asks for `word`
detail and puts the spaces in itself.

*The scan streams.* Hits are published as pages are read rather than at the
end, so the first ones are usable while a long document is still being walked.
Extracted text is cached per page, so a second search does not decode it again.

**~~Thumbnail grid~~** — *dropped from the design.* It was a 3-column grid with
the current page ringed in accent; it was never built, and the screen and its
top-bar button are both gone from the mockup.

## Decisions taken

- **The Browse screen is not being ported as drawn.** It browses
  `/storage/documents/scans`, which Android 11+ disallows without
  `MANAGE_EXTERNAL_STORAGE` (Play-restricted, and it would undo the SAF work).
  `OPEN FILE` launches the system picker instead. In-app browsing, if wanted
  later, should go through `ACTION_OPEN_DOCUMENT_TREE`.
- **Fonts are bundled** as OFL TTFs rather than downloadable fonts: works
  offline, no Play Services dependency.
- **Fira Sans replaces Barlow.** Barlow has no Cyrillic - not a subsetting
  slip, the family simply does not cover it - and Android falls back a glyph at
  a time, so a Russian document title came out half in Barlow and half in the
  system font. Fira Sans and Fira Sans Condensed cover Latin and Cyrillic in
  both the upright and condensed cuts the design needs. IBM Plex was the other
  candidate and is a third the size, but note if it is ever revisited: the
  Plex Sans Condensed on Google Fonts is a Latin-only subset, and only IBM's
  own repo carries the Cyrillic one.
- **Light theme only**, matching the design, rather than deriving a dark
  variant.

## Departures in what is built (steps 1–5)

- **`Open bundled sample`** sits under `OPEN FILE`. Not in the design, but it
  is the only path that does not need the system picker, so it stays until the
  picker is verified on hardware.
- **`scanned 600 dpi`** is missing from the card meta line. DPI is available
  from `pageSize()` but is not stored per document, so printing it would mean
  opening every recent entry to list them.
- **The search screen says more than the design draws.** The design has one
  label, a match count. A real scan takes seconds, so the same line carries
  progress while it runs, and says so when the document has no text layer at
  all rather than reporting no matches. Hits are capped at 400 and the count
  reads `400+` when the cap is reached: searching a common word in a long scan
  matches more times than anyone scrolls.
- **Search quality is the document's, not ours.** Hidden text comes from
  whatever OCR made the file, and the bundled sample splits and merges words
  (`chec k`, `levelofsupport`). Matching across those would need the text
  normalised beyond whitespace, which is its own piece of work.
- **The page gap is 2dp**, not the design's 16px. Asked for directly: at this
  width the backdrop reads as a rule between two sheets rather than a gutter
  they float in.
- **The slider is drawn square.** The design drops in a browser
  `<input type="range">` with `accent-color`, which renders a round handle on a
  round track — the one control in the mockup the stylesheet does not reach.
  Since the system squares off everything it does style, the handle is a
  caliper mark on the same 2dp rule the library cards use.
- **The zoom readout is tappable** and resets to fit-width. The design draws it
  as a plain label, but the reset already existed and is worth keeping.
- **No `backdrop-filter: blur(6px)`** behind the bars. Compose's `Modifier.blur`
  blurs a composable's own content, not what is behind it; a real backdrop blur
  needs a `RenderEffect` on the window and API 31+. The bars use the design's
  94% background tint alone.
- **The go-to sheet keeps one hairline** along its top edge. The design's
  `.dialog` is bordered all round, but the panel is full width and sits on the
  bottom edge, so that is the only side ever on screen.
