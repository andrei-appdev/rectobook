# Licensing

Rectobook is free software under the **GNU General Public License, version
2 or (at your option) any later version**. The full text is in [`../LICENSE`](../LICENSE).

    Copyright (C) 2026 andrei.appdev@gmail.com

    This program is free software; you can redistribute it and/or modify it
    under the terms of the GNU General Public License as published by the Free
    Software Foundation; either version 2 of the License, or (at your option)
    any later version.

    This program is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
    or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
    for more details.

## Third-party components

| Component | Used for | License | Text |
| --- | --- | --- | --- |
| [DjVuLibre](https://djvu.sourceforge.net/) 3.5 — Leon Bottou, Yann Le Cun, AT&T, LizardTech | DjVu decoding; vendored as C++ under `app/src/main/cpp/djvulibre` | GPL-2.0-or-later | [`app/src/main/cpp/djvulibre/COPYING`](../app/src/main/cpp/djvulibre/COPYING), notice in `COPYRIGHT` |
| [PdfiumAndroid](https://github.com/johngray1965/PdfiumAndroid) (`io.legere:pdfiumandroid`) | PDF decoding; prebuilt AAR, links Google's PDFium | Apache-2.0 (wrapper), BSD-3-Clause (PDFium) | [`pdfiumandroid-LICENSE.txt`](pdfiumandroid-LICENSE.txt) |
| [Fira Sans / Fira Sans Condensed](https://github.com/mozilla/Fira) — Mozilla Foundation, Telefónica S.A. | Bundled UI typefaces under `app/src/main/res/font` | SIL OFL-1.1 | [`fira-sans-OFL.txt`](fira-sans-OFL.txt) |
| AndroidX, Jetpack Compose, Kotlin stdlib | App framework | Apache-2.0 | upstream |