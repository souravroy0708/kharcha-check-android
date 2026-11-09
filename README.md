# Kharcha Check (Android) v2

**Paisa kahan gaya? Ab pata chalega.** A private, offline expense tracker for India that helps you find the
leaks in your spending, turn the savings into investments, and make it a daily habit.

- **Private by design.** No account, no server holding your entries, no analytics, no ads. Starts empty: every number comes from you.
- **Encrypted on your phone** (SQLCipher AES-256, key held by Android Keystore). Excluded from Android cloud backup.
- **Internet only where it adds real value:** AMFI's public NAV file for live fund values (whole file, so holdings stay private), HTTPS-only.
  Backups go to a folder you pick (e.g. Google Drive), encrypted with your password first.

## What's inside

| Tab | What it does |
| --- | --- |
| **Home** | Spend ring vs budget (inner ring = leaks), today's spend, safe-to-spend per day, month-end pace, income / invested / leaks. **Today's money thought**: one of 100 original tips, a new one daily with no repeat for 100 days, plus one small thing to try today. Quick add, 7-day streak, 50/30/20 bar, leak card, recent entries. |
| **+ (Add)** | Calculator keypad (`120+45`), Expense / Income / Invest / Transfer, 73 categories in Needs, Wants, **Leaks**, Save & Invest, Income, Transfers, plus your own. Payee, date, time, payment mode, "Was it worth it?" (Needed / Worth it / Regret), goal link, note, tags, "always use this category" learning, duplicate, delete with undo. Leak nudge shows what the money could become. |
| **Activity** | Month browser, search (payee, note, tag, category, amount), filters: Needs, Wants, Leaks, Income, Invest, Transfers, Regret, To review. |
| **Report** | Savings rate, 50/30/20 check, where it went (vs last month, vs budget), what you could cut (10-year SIP value), daily and weekday patterns, payment modes, top payees, plain-language takeaways. PDF and CSV export. |
| **Report → Trends** | 3 / 6 / 12-month analytics: income vs spend vs invest per month (tap a month), savings-rate line with 20% goal, category donut + Needs/Wants/Leaks split, every category with share, monthly average, yearly run-rate and trend, Needs/Wants/Leaks stacked over time, weekday × time-of-day heatmap, auto-detected regular payments (rent, EMIs, SIPs, subscriptions), unusual spends, top payees, payment modes. Tap any category for a drill-down: month-by-month bars vs average, budget, payees, all entries, and a "halve it and invest" projection. Monthly view adds a tappable calendar heatmap. Monday notification: "your week in money". |
| **Split & dues** | Tick "🤝 Split this bill" on an expense: only your share counts as spending, and each person's share becomes "owes you". Per-person balances, settle up, add "I owe" dues, and 💬 Remind drafts a polite WhatsApp-ready message via the share sheet. |
| **Bills & due dates** | Credit cards, rent, electricity, insurance, EMIs with due day and amount. Reminders N days before, the day before, on the day and when a day late; "Mark paid"; upcoming bills on Home. |
| **Kharcha Wrapped** | Spotify-Wrapped-style story for any month (Report) or the year so far (Trends): spend vs saved, #1 category, go-to payee, biggest day, leaks, no-spend days, and a money persona (The Saver, The Foodie, The Investor…). Each card shares as an image. |
| **Quick Settings tile** | "Add expense" from the notification shade. |
| **Grow** | Levels and XP, streaks, 21-day habit grid, 14 badges, 8 challenges (No delivery week, Junk-free 10, Leak-free week...), savings goals, cut-a-leak → SIP calculator, emergency fund target, 18 golden rules. |
| **Repeating entries** | Tick "Repeat every month" when saving rent, EMI, SIP, salary or a subscription. It's added to Review on its day each month (catching up at most 3 months if the phone was off). Pause or stop any time in Settings. |
| **Attachments** | Profile photo; receipts and GPay / PhonePe / Paytm screenshots on any entry (or share a screenshot to the app to start an entry); monthly bank, credit card and UPI statements (Report → Statements). All encrypted in the app with a Keystore key; in-app viewer with zoom and PDF pages; "Open in app" for password-protected statements. Not in `.kcbak` backups. |
| **✨ Auto-read (optional, on by default)** | **Screenshots:** share or attach a GPay / PhonePe / Paytm / BHIM screenshot and amount, payee, date, UPI ref and category are filled in (only empty fields; failed payments are ignored). **Statements:** attach a bank, credit card or UPI statement, or open/share it from Gmail, Drive or Files ("Read with Kharcha Check"), and get a review list of every transaction. Digital PDFs are read exactly (PdfBox text extraction), scanned pages fall back to OCR, password-protected PDFs work on every Android version, and .xlsx / HTML ".xls" / CSV downloads are parsed directly. Debit/credit is confirmed against the running balance; already-logged entries are unticked; edit any row, then "Add & confirm" or "Save as drafts" (to the Review inbox). Runs entirely on the phone: no internet, no Google services. Toggle in Settings. |
| **Home-screen widget** | Streak, logged-today status, one-tap **+ Add**, and today's money thought. Shows no amounts, on purpose. |
| **Wealth & net worth** | Mutual funds valued daily from AMFI NAVs (search ~30,000 schemes by name), FDs, PPF/EPF/NPS, stocks, gold, bank balances, cash, property and loans. Net worth, gain %, allocation and a monthly net-worth trend. |
| **Automatic backup** | Daily encrypted `.kcbak` into a folder you choose (pick Google Drive for cloud backup); keeps the newest 7; password stored wrapped by Android Keystore so it runs unattended. |
| **Crash reports** | Saved locally; next launch offers to email the technical details. Nothing sent silently. |
| **Settings** | Income, budget, category budgets, categories, age, expected return, reminders, app lock, screenshot block, theme, password backup / restore, CSV export, delete everything, privacy centre. |

## Two editions, one codebase

| | `standard` | `smart` |
| --- | --- | --- |
| SMS permissions | **None** | `READ_SMS`, `RECEIVE_SMS`, off until the user turns it on |
| Share / paste bank SMS | Yes | Yes |
| Play Store | Publish now, no declaration | Needs the **SMS permission declaration** approved first |

Both use the same `applicationId`, so publish `standard` first, then upload `smart` as an update once Play approves.
Captured SMS always lands in the Review inbox first; every field stays editable; message text is never stored.

## Brand assets

`docs/store/` has the logo source (`icon.svg`), the Play Store icon (`play-icon-512.png`) and feature graphic
(`feature-graphic-1024x500.png`, source `feature.svg`). The app icon is an adaptive vector with a monochrome
layer for Android 13+ themed icons; the notification icon matches.

## Build and release (same flow as Bas 1 Minute)

```bash
./scripts/ship-to-github.sh              # first time: repo, signing key, secrets, first build
./scripts/get-play-bundle.sh             # later: build on GitHub, download APKs + AABs into ./dist
./update-from-zip.sh ~/Downloads/kharcha-check-android.zip   # apply a new zip from Claude
```

Every push to `main` runs `.github/workflows/build-apk.yml`:
unit tests → `standard` + `smart` APKs → privacy gate (no INTERNET / network state / AD_ID; no SMS in standard)
→ signed `.aab` bundles (when the `KC_KEYSTORE_*` secrets exist) → GitHub Release with `KharchaCheck.apk`.

Artifacts: `KharchaCheck-apks-2.0.N` and `KharchaCheck-play-bundles-2.0.N`. Upload
`KharchaCheck-2.0.N-standard.aab` to Play. Publishing steps: [`docs/PLAY_STORE.md`](docs/PLAY_STORE.md).

> **Signing key:** keep `~/kharchacheck-signing/` backed up offline. If you lose the upload key you need
> Google's key reset process. If you already set up signing for v1, the same secrets keep working.

## Project layout

```
app/src/main/java/com/kharchacheck/app/
  core/model      Money (integer paise), Txn, Category, 73 default categories
  domain          ReportEngine, AnalyticsEngine (trends, regulars, outliers, heatmap, weekly digest), Gamification, DailyWisdom (100 cards), GoldenRules, InvestMath,
                  AmountExpression, RecurringPlanner, CsvWriter, BackupCrypto, sms/SmsParser   ← pure Kotlin, unit tested
  domain/read     ReceiptParser, StatementParser (lines + tables), SpreadsheetReader (xlsx/html/csv), Classifier   ← pure Kotlin, unit tested
  read            OcrEngine (Tesseract, swappable), AutoReader (PdfBox text → OCR fallback → parse)
  data            Room + SQLCipher, Keystore-wrapped DB key, DataStore settings, FinanceRepository,
                  FileVault (encrypted attachments), AttachmentRepository
  ui              theme, components, navigation, AppViewModel
  feature         Home, EditTxn, Activity/Inbox, Report, Grow, Settings, Onboarding/Lock
  work            DailyWorker (reminder, month-end report, repeating entries), Notifier
  widget          KharchaWidget (home screen)
  export          ReportPdf, BackupManager
app/src/standard  SmsSupport stub (no permissions)
app/src/smart     SmsSupport, SmsReceiver, manifest with SMS permissions
app/src/test      62 tests: SMS golden set, reports, gamification, daily cards, recurring, crypto, CSV, keypad
```

`./gradlew testStandardDebugUnitTest` runs the tests locally.

## Architecture notes

- **Where the internet is used, and why:** fund prices (needs daily data; whole-file download keeps holdings private) and backups (to the user's own storage). Everything else, including OCR, statement reading and analytics, stays on the phone because it is just as good there and keeps money data private.
- **Analytics run on the phone**, off the main thread, from the user's own entries. Thousands of entries compute in milliseconds, so there is no analytics server: it would add cost and an upload of everyone's spending without improving any chart. A backend is worth it for multi-device sync or family sharing, as a deliberate product decision.
- **Charts are drawn with Compose `Canvas`** (`ui/components/Charts.kt`): zero extra dependencies, exact brand styling, and each chart exposes a spoken summary for TalkBack.

## Upgrading from v1

v1 stored demo data in `kharcha_check.db`; v2 uses a new encrypted `kharcha_v2.db` and starts empty.
`versionCode` starts at 1000, so v2 always installs over v1.

## Licences

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) (also in the app: Settings → Open-source licences).

Fonts: Anek and Mukta, SIL Open Font License (see `FONT-LICENSE-*.txt`). Money tips are educational, not financial advice.
