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
| **🎤 Voice entry** | Home → 🎤 Speak, or "Say it instead of typing" in the editor: "spent 450 on lunch with team at Paradise yesterday" fills amount, category, payee, note, payment mode and date (Indian number words, lakh/k, Hinglish like "kal 300 ka petrol"). Mic buttons also on payee, note, searches and names. Runs Android's `SpeechRecognizer` in an in-app listening sheet (on-device on Android 12+ where available, else the phone's speech service). `RECORD_AUDIO` is asked on first use with a guided explainer; if blocked, a step-by-step Settings guide; if the phone has no speech service, setup steps + Play Store link; every error (offline, language missing, busy) explains the fix, with Google's voice screen as a fallback. Audio is never saved; CI blocks any background-mic permission. |
| **📘 Smart Money Guide** | *My salary plan:* needs / wants / save split for your take-home (savings % scales with income), compared with this month's actual; a money ladder in the right order (starter fund → health cover → term cover if someone depends on you → clear costly debt → 6-month fund → SIP → yearly step-up → yearly check-up) using your own EMIs and essentials; monthly SIP split by age and risk comfort; 10/20-year and step-up projections. *Investing:* the golden rules. *Online spend:* 24 app tricks (menu mark-ups, minimum-order traps, fake countdowns, no-cost EMI, BNPL, auto-renewing trials…) led by your own biggest online leak. |
| **🔔 Daily money nudges (3 a day)** | 💰 8:30 am *invest with purpose* (salary-day "pay yourself first", goal SIP still due, money rescued from leaks, start-a-SIP), 🛑 1 pm *avoid unnecessary spending* (delivery orders this week, leaks vs your usual, wishlist ready, budget per day left, subscriptions), 💡 7:30 pm *saving trick* (no-spend day so far, tax room in Jan–Mar). Personal nudges first with per-type cooldowns; otherwise a unique item from the slot's own pool (60–90 items: 80 new India-specific tips + the 100 daily cards + online-spending tips + research cards), shuffled per install, no repeats within a cycle. Each slot can be turned off or re-timed; hidden entries never used; lock screen shows no amounts. Home's daily card keeps "✅ I did it" (+15 XP). |
| **Statement import (v2 parser)** | From Home, Activity or "Open with" in Gmail/Drive. Digital PDFs are read **by column position** (every character's x/y): header detection (Date / Narration / Withdrawal / Deposit / Balance / Amount / Dr-Cr), right-aligned amounts snapped to their column, glued columns split, wrapped narrations joined, multi-page headers, newest-first or oldest-first, every row verified against the running balance. Up to 200 pages (a full year). Scanned pages fall back to OCR. |
| **Bank layouts covered by tests** | HDFC (two-line headers, wrapped narrations), SBI (newest-first, split dates), **ICICI OpTransactionHistory** (centred headings, payee name above the date line, dd.mm.yyyy, serial-number column), credit-card statements (Cr marks), netbanking CSV / XLSX / HTML-xls. Unsupported layout? Import screen → "Send a reading report" (names and account numbers masked). |
| **No double entries** | Count-based dedupe on every import: same bank reference, or same day + amount + direction + merchant = already in app (hidden, never re-added); same amount within a day = "maybe" (unticked). Re-importing the same file adds nothing and says when you last imported it. Undo after any import. |
| **Edit everywhere** | Every entry opens the full editor; long-press in Activity to multi-select → change category or delete (with Undo); import rows editable (payee, amount, date, note, category); dues, repeating entries, bills, goals, wealth items all editable. |
| **🙈 Hidden entries** | Hide any entry (editor toggle, or multi-select in Activity). Hidden entries are left out of every list, search, total, report, trend, Wrapped, export and notification. Settings → Hidden entries unlocks them with fingerprint / screen lock until the app leaves the screen; the count isn't shown while locked. Duplicate checks still see them, so re-imports don't resurface them. 👁 on Home masks the big amounts. |
| **Undo a bad import** | Statements → Import history lists each import (entries, date range) with one-tap Delete + Undo. Activity has an "📄 Imported" filter for picking individual rows. |
| **Export** | Excel (.xlsx with real dates/numbers, frozen header, filters, Monthly summary + By category sheets) or CSV (UTF-8 with BOM so ₹ shows in Excel); any period and entry types; Save to phone or Share; explains export vs full backup for uninstalling. |
| **🎯 Goals & plans** | Templates for Indian life goals (home down payment, child's education / wedding / future fund, retirement, car, trip, emergency) with goal-specific inflation (education 10%, home 7%…). Today's cost → future cost → monthly SIP (flat or +10%/year step-up), counting what's already saved; investment mix by horizon (equity ≥7y, balanced 3–7y, safe <3y); Sukanya Samriddhi note for girls' goals. Affordability vs the salary plan, leak-swap ("halving food delivery covers 45% of this SIP"), "money rescued" from lower leaks → one tap into a goal, SIP-day reminders, Home card "Goal SIPs this month: 2 of 3 done". Plans stored in DataStore (no DB migration). |
| **💯 Money Health Score** | 0–100 on Home from six yardsticks: savings rate (25%), emergency fund (20%), insurance (15%), debt load (15%), leak control (15%), investing habit (10%). Missing data is left out and the rest re-weighted (no unfair low score for new users). One "best next step" (weakest × most important) with a button to the screen that fixes it. |
| **🧰 Tools hub** | Every tool in one grid with a live status ("2 ready to decide", "₹28k/year", "Score 72"). Home top bar (grid icon) and Grow. |
| **🛒 Wishlist (cooling-off)** | Park a purchase instead of buying: waits 2 / 7 / 14 / 30 days by price, then "still want it?" (notification). Shows price in hours of your work (Robin & Dominguez), cost per use, and a 4-question need-or-want check. Buy → pre-filled entry; Skip → counted as money kept (+30 XP, "Impulse Tamer" badge). |
| **📚 Books & research** | 25 sourced cards (Franklin, Housel, Stanley & Danko, Robin & Dominguez, Sethi, Halan, Buffett 2013 letter, Bogle, Kahneman, Ariely; Prelec & Simester 2001, Knutson 2007, Worchel 1975, Thaler & Benartzi 2004, Dunn et al. 2008, Van Boven & Gilovich 2003, Gal & McShane 2012…) plus Indian rules (CCPA dark-pattern guidelines 2023 with National Consumer Helpline 1915; RBI e-mandates). Each: the finding in plain words + one action. Paraphrased; only public-domain lines quoted. |
| **⏱️ Hours-of-work price** | Wants and leaks in the entry screen show "That's 10.0 hours of your work", with a nudge to use the wishlist for bigger ones. |
| **🧾 Tax saver** | Old-regime deductions for the April–March year: 80C (₹1.5L), NPS 80CCD(1B) (₹50k), 80D self / parents (₹25k, ₹50k if 60+). Auto-suggests entries (PPF, LIC, ELSS, tuition, health insurers by name; index funds correctly excluded), one-tap reassign, "paid outside the app" amounts (EPF from payslip, home-loan principal), tax saved at your slab + 4% cess, days to 31 March, reminders 15 Jan / 15 Feb / 15 Mar / 25 Mar. New-regime users told plainly these don't apply. |
| **🔁 Subscriptions** | Detects small regular wants/leaks (3 of 4 months, steady amount; rent/EMIs/SIPs excluded): monthly + yearly cost, Keep / Cancelled / Remind-before-renewal (creates a bill), yearly savings from cancelling, and an alert if a cancelled one charges again (with UPI AutoPay mandate advice). |
| **💳 Card bill from statement** | Card statement's summary box (due date, total due, minimum due; inline or boxed layout) → one-tap bill reminder, with a "pay the total, not the minimum" nudge. |
| **Budget alerts** | Warning when a new entry crosses 80% / 100% of a category or the monthly budget; daily check also catches statement imports. Once per threshold per month. |
| **Grow** | Levels and XP, streaks, 21-day habit grid, 14 badges, 8 challenges (No delivery week, Junk-free 10, Leak-free week...), savings goals, cut-a-leak → SIP calculator, emergency fund target, 18 golden rules. |
| **Repeating entries** | Tick "Repeat every month" when saving rent, EMI, SIP, salary or a subscription. It's added to Review on its day each month (catching up at most 3 months if the phone was off). Pause or stop any time in Settings. |
| **Attachments** | Profile photo; receipts and GPay / PhonePe / Paytm screenshots on any entry (or share a screenshot to the app to start an entry); monthly bank, credit card and UPI statements (Report → Statements). All encrypted in the app with a Keystore key; in-app viewer with zoom and PDF pages; "Open in app" for password-protected statements. Not in `.kcbak` backups. |
| **✨ Auto-read (optional, on by default)** | **Screenshots:** share or attach a GPay / PhonePe / Paytm / BHIM screenshot and amount, payee, date, UPI ref and category are filled in (only empty fields; failed payments are ignored). **Statements:** attach a bank, credit card or UPI statement, or open/share it from Gmail, Drive or Files ("Read with Kharcha Check"), and get a review list of every transaction. Digital PDFs are read exactly (PdfBox text extraction), scanned pages fall back to OCR, password-protected PDFs work on every Android version, and .xlsx / HTML ".xls" / CSV downloads are parsed directly. Debit/credit is confirmed against the running balance; already-logged entries are unticked; edit any row, then "Add & confirm" or "Save as drafts" (to the Review inbox). Runs entirely on the phone: no internet, no Google services. Toggle in Settings. |
| **Home-screen widget** | Streak, logged-today status, one-tap **+ Add**, and today's money thought. Shows no amounts, on purpose. |
| **Wealth & net worth** | Mutual funds valued daily from AMFI NAVs (search ~30,000 schemes by name), FDs, PPF/EPF/NPS, stocks, gold, bank balances, cash, property and loans. Net worth, gain %, allocation and a monthly net-worth trend. |
| **Backups (v3)** | `.kcbak` now holds entries **and** statements, receipts and the profile photo (optional), streamed through chunked AES-256-GCM so any size works without running out of memory; damaged or incomplete backups are detected and never half-restored. |
| **Automatic backup** | Daily encrypted `.kcbak` into a folder you choose (pick Google Drive for cloud backup); keeps the newest 7; password stored wrapped by Android Keystore so it runs unattended. |
| **Crash reports** | Saved locally; next launch offers to email the technical details. Nothing sent silently. |
| **Settings** | Income, budget, category budgets, categories, age, expected return, reminders, app lock, screenshot block, theme, password backup / restore, CSV export, delete everything, privacy centre. |

## No SMS access

The app has no SMS permission and never reads messages. Entries come from manual entry, bank / card statement
import (PDF, Excel, CSV), payment screenshots, or text the user chooses to paste or share. CI fails the build if an
SMS permission ever appears in the release manifest.

## App size

| Download | Size | For |
| --- | --- | --- |
| `KharchaCheck-…-arm64.apk` | ~12 MB | Almost every phone from the last ~7 years |
| `KharchaCheck.apk` | ~17 MB | All phones (adds 32-bit ARM) |
| Play Store (`.aab`) | ~10–15 MB per phone | Play delivers only what each phone needs |

How (all features kept): no x86 emulator libraries in release builds; native libraries compressed in
downloadable APKs only (`-PcompressNativeLibs`); compressed OCR model; Bouncy Castle's unused post-quantum
tables and PdfBox's Chinese/Japanese/Korean font maps excluded. Test builds (`smart-TEST`) keep x86 for emulators.

## Offline and storage

Everything works without internet and is stored only on the phone (SQLCipher database + encrypted file vault). Internet is used for just three optional things: AMFI fund prices (last prices kept offline, gzipped), voice entry on phones without offline speech, and backups if the user picks a cloud folder. Entries take ~107 bytes each (integer paise, 1-byte enum codes, empty defaults), screenshots are saved as WebP, and a daily offline `UpkeepWorker` keeps the database compact. **Settings → Storage & offline** shows every byte and has one-tap clean-up. Details: `docs/DATABASE.md`.

## Website

`docs/index.html` is the public website (static HTML, no build step). In the repo settings, set
**Pages → Source: Deploy from a branch → `main` / `docs`**, and it goes live at
`https://souravroy0708.github.io/kharcha-check-android/`. It follows the app's privacy rules: no cookies,
no analytics, fonts and scripts served from the site itself, and a strict Content-Security-Policy.
The "Try it" demo reads a pasted bank message entirely in the browser (`docs/assets/site.js`).

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
unit tests → release APK → privacy gate (no SMS, no AD_ID, no location/contacts, HTTPS-only)
→ signed `.aab` bundles (when the `KC_KEYSTORE_*` secrets exist) → GitHub Release with `KharchaCheck.apk`.

Artifacts: `KharchaCheck-apks-2.0.N` and `KharchaCheck-play-bundles-2.0.N`. Upload
`KharchaCheck-2.0.N.aab` to Play. Publishing steps: [`docs/PLAY_STORE.md`](docs/PLAY_STORE.md).

> **Signing key:** keep `~/kharchacheck-signing/` backed up offline. If you lose the upload key you need
> Google's key reset process. If you already set up signing for v1, the same secrets keep working.

## Project layout

```
app/src/main/java/com/kharchacheck/app/
  core/model      Money (integer paise), Txn, Category, 73 default categories
  domain          ReportEngine, AnalyticsEngine (trends, regulars, outliers, heatmap, weekly digest), Gamification, DailyWisdom (100 cards), GoldenRules, InvestMath,
                  AmountExpression, RecurringPlanner, CsvWriter, BackupCrypto, sms/SmsParser   ← pure Kotlin, unit tested
  domain/read     PdfTable (position-aware), ReceiptParser, StatementParser, SpreadsheetReader (xlsx/html/csv), Classifier
  domain/export   Exporter (CSV + XLSX), XlsxWriter;  domain/Dedupe   ← pure Kotlin, unit tested
  read            OcrEngine (Tesseract, swappable), AutoReader (PdfBox text → OCR fallback → parse)
  data            Room + SQLCipher, Keystore-wrapped DB key, DataStore settings, FinanceRepository,
                  FileVault (encrypted attachments), AttachmentRepository
  ui              theme, components, navigation, AppViewModel
  feature         Home, EditTxn, Activity/Inbox, Report, Grow, Settings, Onboarding/Lock
  work            DailyWorker (reminder, month-end report, repeating entries), Notifier
  widget          KharchaWidget (home screen)
  export          ReportPdf, BackupManager
app/src/test      114 tests: SMS golden set, reports, gamification, daily cards, recurring, crypto, CSV, keypad
```

`./gradlew testStandardDebugUnitTest` runs the tests locally.

## Architecture notes

Honest comparison with typical premium apps: [`docs/COMPARISON.md`](docs/COMPARISON.md).
Full review with findings and roadmap: [`docs/ARCHITECTURE_REVIEW.md`](docs/ARCHITECTURE_REVIEW.md).
Database changes: read [`docs/DATABASE.md`](docs/DATABASE.md) first.


- **Where the internet is used, and why:** fund prices (needs daily data; whole-file download keeps holdings private) and backups (to the user's own storage). Everything else, including OCR, statement reading and analytics, stays on the phone because it is just as good there and keeps money data private.
- **Analytics run on the phone**, off the main thread, from the user's own entries. Thousands of entries compute in milliseconds, so there is no analytics server: it would add cost and an upload of everyone's spending without improving any chart. A backend is worth it for multi-device sync or family sharing, as a deliberate product decision.
- **Charts are drawn with Compose `Canvas`** (`ui/components/Charts.kt`): zero extra dependencies, exact brand styling, and each chart exposes a spoken summary for TalkBack.

## Upgrading from v1

v1 stored demo data in `kharcha_check.db`; v2 uses a new encrypted `kharcha_v2.db` and starts empty.
`versionCode` starts at 1000, so v2 always installs over v1.

## Licences

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) (also in the app: Settings → Open-source licences).

Fonts: Anek and Mukta, SIL Open Font License (see `FONT-LICENSE-*.txt`). Money tips are educational, not financial advice.
