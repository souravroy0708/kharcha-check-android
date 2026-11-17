# Third-party software in Kharcha Check

| Component | Used for | Licence |
| --- | --- | --- |
| Tesseract OCR 5 (via Tesseract4Android 4.9.0) | Offline reading of screenshots and statements | Apache License 2.0 |
| tessdata_fast `eng.traineddata` | English recognition model (bundled in `assets/tessdata`) | Apache License 2.0 |
| PdfBox-Android 2.0.27 (port of Apache PDFBox) | Exact text from digital PDF statements, password-protected PDFs | Apache License 2.0 |
| Bouncy Castle (bcprov, bcpkix, bcutil) | PDF decryption, used by PdfBox | MIT licence |
| Leptonica | Image processing for Tesseract | BSD 2-Clause |
| libjpeg (IJG) / libpng | Image decoding for Tesseract | IJG licence / libpng licence |
| SQLCipher for Android (Community Edition) | Encrypted database | BSD-style (Zetetic) |
| AndroidX, Jetpack Compose, Kotlin, kotlinx | App framework | Apache License 2.0 |
| Anek and Mukta fonts | Typography | SIL Open Font License 1.1 |

Full licence texts: https://www.apache.org/licenses/LICENSE-2.0, https://github.com/DanBloomberg/leptonica/blob/master/leptonica-license.txt,
https://www.zetetic.net/sqlcipher/license/, `FONT-LICENSE-Anek.txt`, `FONT-LICENSE-Mukta.txt`.
