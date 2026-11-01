# Kharcha Check — 100% On-Device Native Android Expense Tracker (`kharcha-check-android`)

**"Paisa kahan gaya — ab pata chalega."**

Native Android application built in **Kotlin 2.2 + Jetpack Compose (Material 3)** implementing the full **1 Nov 2026 Product & Build Spec**. Every byte of financial data stays on the phone, and the app ships **without the `android.permission.INTERNET` permission** (enforced via `tools:node="remove"` in `AndroidManifest.xml` and verified by a dedicated CI gate in `.github/workflows/build-apk.yml`).

---

## Core Spec Implementation Summary

| Spec Area | Implementation in `kharcha-check-android` |
| :--- | :--- |
| **Provable Privacy (Zero `INTERNET`)** | `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />` in `AndroidManifest.xml`, `allowBackup="false"`, `data_extraction_rules.xml` excluding all domains, and a GitHub Actions CI step that fails the build if the merged release manifest contains `INTERNET`. |
| **9-Step Indian Bank SMS Parser** | `SmsParser.kt`: 1. Sender filter (40+ Indian bank/card/wallet TRAI headers like `HDFCBK`, `SBIUPI`, `ICICIB`, `AXISBK`, `KOTAKB`), 2. OTP/promo/statement/mandate/failed exclusion, 3. Debit/credit/refund/ATM/transfer classification, 4. Amount/mode/last4/merchant/UTR extraction, 5. Merchant alias table (`Bundl Technologies` → `Swiggy`, `Blink Commerce` → `Blinkit`, `Kiranakart` → `Zepto`, etc.), 6. 2-minute & `refNo` de-duplication, 7. User/Learned rules + keyword map, 8. Confidence scoring (`AUTO` vs `NEEDS_REVIEW`), 9. Self-transfer & Credit Card bill (`CRED`/`CheQ`) exclusion. |
| **Golden Test Set (≥ 95% Accuracy)** | `SmsParser.GOLDEN_SAMPLES` + JVM unit test `SmsParserTest.kt` (executed automatically on every GitHub Actions run via `./gradlew testDebugUnitTest`, and runnable live on-device from **Settings → Run On-Device Golden Set Test**). |
| **Live Capture, Backfill & Share-to-App** | `SmsReceiver.kt` (`SMS_RECEIVED` broadcast receiver, zero foreground service/polling), `SmsBackfill.kt` (one-time 30 / 90 / 180 day inbox reader storing only `smsId` and parsed fields, never raw SMS body), and `ACTION_SEND` `text/plain` intent filter in `MainActivity.kt` so users can share bank SMS without granting SMS permission. |
| **< 5-Second Quick Add** | `QuickAddScreen` in `ui/HomeAndQuickAdd.kt`: Custom numeric keypad, 1-tap category chips, defaults to `Now` and `lastUsedAccountId`, and measures time-to-log in seconds. |
| **9-Step Guided Month-End Review** | `MonthEndReviewFlow.kt`: 1. Headline (Earned, Spent, Saved, Savings Rate vs 3m avg), 2. Where it went (Fixed vs Flexible bars), 3. Overspend flags (max 3 categories), 4. Small leaks (`< ₹200` grouped by merchant), 5. Top 10 merchants & single payments, 6. Interactive Swipe/Tap cards (`Needed` / `Fine` / `Avoidable`) with live Avoidable total, 7. Weekday & Time-of-day strips + UPI/Card/Cash split, 8. Pick 1–3 focus targets for next month, 9. Savings intent + native **PDF Export** (`PdfReportExporter.kt`). |
| **Encrypted Storage & `.kcbak` Backup** | `CryptoVault.kt` (Android Keystore `AES/GCM/NoPadding` 256-bit key + passphrase-encrypted `PBKDF2WithHmacSHA256` + `AES/GCM` `.kcbak` export/import via Storage Access Framework). |
| **App Lock & `FLAG_SECURE`** | `OnboardingAndLock.kt` + `MainActivity.kt`: 4-digit SHA-256 salted PIN lock + `WindowManager.LayoutParams.FLAG_SECURE` toggle to hide the screen in Recent Apps and block screenshots. |
| **Bilingual English & Hindi (`हिन्दी`)** | `ui/I18n.kt`: Instant 1-tap toggle between `English` and `हिन्दी` in the top bar and Settings. |

---

## Build, Sign & Generate APK + Play Bundle (`.aab`) on GitHub Actions

Just like `bas1minute-android` and `gmm-android`, this repo includes automated scripts in `./scripts/`:

```bash
# 1. Initialize git, create GitHub repo, push, generate signing key, set GitHub secrets, and build APK + AAB:
./scripts/ship-to-github.sh souravroy0708/kharcha-check-android

# Or step-by-step:
./scripts/setup-signing.sh      # Creates ~/kharchacheck-signing/kharchacheck-release.jks and uploads 4 KC_KEYSTORE_* secrets
./scripts/get-play-bundle.sh    # Triggers GitHub Actions workflow, watches it finish, and downloads APK + AAB into ./dist/
```
