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
  6px outside each corner of buttons and cards.

Six screens: library, browse, reader, go-to-page, search, thumbnails.

## Steps

**1. Theme foundation** ✅ — `ui/theme/`: Industry colour scheme, Barlow
typography, `Shapes` all `RectangleShape`, and a `Modifier.blueprintCorners()`
drawing the registration marks. Swap `MaterialTheme` for `DjvuTheme`. Existing
screens then read as Industry with no structural change.

**2. Library screen** ✅ — `DJVU READER` header, corpus line, `OPEN FILE` button
with corner marks, and Recent cards with a page glyph, title, meta, progress
bar and `P.142` label. Needs `RecentDocument` to carry `pageCount` and
`sizeBytes`; today it stores only key, name, last page and timestamp, so the
meta line and progress bar would otherwise be invented. The card's page
thumbnail is a *drawn glyph* in the design, not a real render, so no document
needs opening to show it.

**3. Reader chrome** ✅ — auto-hiding top and bottom bars (tap to toggle,
`translateY(±102%)`, 220 ms) replacing the fixed bar and floating zoom pill.
Top: back, title, `djvu · N pp`, search, thumbnails. Bottom: page button,
progress line, segmented −/`100%`/+, plus the centre zoom flash. A reskin of
the controls only; the zoom and scroll mechanics stay as they are.

**4. Go-to-page sheet** ✅ — bottom sheet instead of the `AlertDialog`. The quick
marks (Cover, Contents, Ch. 4…) are a document outline, which is not extracted
yet: either drop them or add `ddjvu_document_get_outline` in the same pass.

*Built without the outline.* `ddjvu_document_get_outline` returns entries as
`(name url subs…)`, and the url may name a page (`#chapter1.djvu`) rather than
number it (`#12`). Resolving a name needs `DjVuDocument::url_to_page`, which is
C++ and not in the ddjvu C API, so a JNI pass would resolve only some entries
and silently drop the rest. The row instead carries the jumps we do know —
`1 First` and `N Last` — in the design's own mark styling. Real outline
support is worth its own step alongside search, which needs C++-level access
too.

**5. Thumbnail grid** — 3 columns, current page ringed in accent. Reuses
`renderPage` at small width but needs its own cache bucket; sharing the page
`LruCache` would evict full-size pages and undo the flicker fix.

**6. Search** *(not a reskin)* — new JNI over `ddjvu_document_get_pagetext`,
walking the miniexp text tree per page, then the hit list with the highlighted
term. The largest item, and best done after 1–5.

## Decisions taken

- **The Browse screen is not being ported as drawn.** It browses
  `/storage/documents/scans`, which Android 11+ disallows without
  `MANAGE_EXTERNAL_STORAGE` (Play-restricted, and it would undo the SAF work).
  `OPEN FILE` launches the system picker instead. In-app browsing, if wanted
  later, should go through `ACTION_OPEN_DOCUMENT_TREE`.
- **Fonts are bundled** as OFL TTFs rather than downloadable fonts: works
  offline, no Play Services dependency, ~200 KB.
- **Light theme only**, matching the design, rather than deriving a dark
  variant.

## Departures in what is built (steps 1–4)

- **`Open bundled sample`** sits under `OPEN FILE`. Not in the design, but it
  is the only path that does not need the system picker, so it stays until the
  picker is verified on hardware.
- **`scanned 600 dpi`** is missing from the card meta line. DPI is available
  from `pageSize()` but is not stored per document, so printing it would mean
  opening every recent entry to list them.
- **No search or thumbnail buttons** in the top bar yet; they arrive with
  steps 5–6 rather than sitting there dead.
- **The zoom readout is tappable** and resets to fit-width. The design draws it
  as a plain label, but the reset already existed and is worth keeping.
- **No `backdrop-filter: blur(6px)`** behind the bars. Compose's `Modifier.blur`
  blurs a composable's own content, not what is behind it; a real backdrop blur
  needs a `RenderEffect` on the window and API 31+. The bars use the design's
  94% background tint alone.
- **Registration marks are crosses, not brackets.** `.corner::before/after`
  span the full 11px box and cross at 5px, so each arm sticks out on both
  sides of the corner.
- **The sheet's corner marks are drawn inside it.** The design offsets them
  -6px on a panel that is already full width, so on a real screen both marks
  would fall off the edges; `blueprintCorners(overhang = 0.dp)` puts them just
  inside instead.
