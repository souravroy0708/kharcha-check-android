# Publishing Kharcha Check on Google Play

Upload the **standard** edition first. It has no sensitive permissions, so review is quick.

## 1. Create the app
Play Console → **Create app**: name `Kharcha Check`, default language English (India), App, Free.
Accept the declarations.

## 2. Privacy policy URL (required)
1. Push this repo to GitHub (public), then **Settings → Pages → Deploy from branch → `main` / `/docs`**.
2. Your URL: `https://<github-user>.github.io/kharcha-check-android/privacy.html`
3. Paste it in **Policy → App content → Privacy policy**.

## 3. First release
1. Run `./scripts/get-play-bundle.sh` (or download the `KharchaCheck-play-bundles-2.0.N` artifact).
2. **Testing → Internal testing → Create release** → upload `KharchaCheck-2.0.N-standard.aab`.
3. Accept **Play App Signing** when asked. Add yourself as a tester, install from the opt-in link, test.
4. New personal developer accounts must run a **closed test with at least 12 testers for 14 days**
   before production access. Start this early.
5. Then **Production → Create release**, same `.aab` (or a newer one).

## 4. Store listing
- **Short description (max 80):** `Private expense tracker. Find money leaks, save more. No internet needed.`
- **Full description:**
  > Paisa kahan gaya? Ab pata chalega.
  >
  > Kharcha Check is a private expense tracker for India. Log any spend in about 5 seconds, see exactly where your money goes, and find the leaks that are easiest to cut: food delivery, impulse buys, unused subscriptions, late fees.
  >
  > • ✨ Auto-read: share a GPay / PhonePe / Paytm screenshot and the entry fills itself, or turn a bank or credit card statement (PDF, Excel or CSV, even password-protected) into entries in one go, all on your phone with nothing uploaded
  > • Fast manual entry with a calculator keypad and 73 Indian categories
  > • Needs, Wants and Leaks, with a monthly 50/30/20 check
  > • Monthly report with savings rate, takeaways and PDF/CSV export
  > • Trends over 3, 6 or 12 months: income vs spending, savings rate, category breakdown, spending heatmap, regular payments and unusual spends
  > • Streaks, a 21-day habit grid, badges, levels and challenges like "No delivery week"
  > • A new money tip every day: 100 tips on saving, investing, cutting leaks and staying safe
  > • Savings goals and a calculator that shows what a cut leak could become as a SIP
  > • Split bills with friends and track who owes you, with friendly reminders
  > • Bill and credit card due-date reminders so you never pay a late fee
  > • Kharcha Wrapped: your month or year in money as a shareable story
  > • Wealth & net worth: mutual funds at daily prices, FDs, PPF, bank balances and loans in one number
  > • Automatic encrypted backup to Google Drive or any folder you choose
  > • Repeating entries for rent, EMI, SIP and subscriptions, confirmed in one tap
  > • Attach receipts, GPay / PhonePe screenshots and monthly bank or card statements, encrypted on your phone
  > • Home-screen widget with your streak, quick add and the day's tip (never shows amounts)
  > • Share any bank SMS or UPI message to add it instantly
  >
  > Private by design: no account, no ads, no tracking. Your entries are encrypted on your phone; backups go only where you choose, encrypted with your password. Track your mutual funds at today's prices without revealing what you hold.
  >
  > Educational tips, not financial advice.
- **Category:** Finance. **Tags:** Budgeting, Personal finance.
- **Graphics:** ready in `docs/store/`: `play-icon-512.png` (app icon) and `feature-graphic-1024x500.png`. Add at least 2 phone screenshots
  (Home with the daily tip, Add entry, Report, Grow). Use your own entries, not someone else's real data.

## 5. App content (Policy → App content)
| Item | Answer |
| --- | --- |
| Ads | No ads |
| App access | All functionality available without special access (no login) |
| Content rating | Questionnaire → Utility / productivity; no violence, gambling, etc. Expect "Everyone" / "3+" |
| Target audience | 18 and over (money management for adults) |
| News app | No |
| Government app | No |
| Financial features | Declare it as personal finance / budgeting (money management). It offers **no** loans, payments, banking, crypto, trading or insurance sales. |
| Health apps | No |
| Data safety | See below |

### Data safety form
Entries, attachments and settings stay on the device, so they are **not "collected"**. What does leave the phone:
- **AMFI NAV download:** a plain HTTPS download of a public file; no user data is sent. Not collection.
- **Automatic backup:** an encrypted file written, at the user's request, to a folder/app the user picks
  (e.g. Google Drive). You never receive it. This is a user-initiated transfer, not collection or sharing.
- **Crash reports:** only if the user taps "Send report" and sends the email. Declare this to be safe:

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** |
| Data type | **App info and performance → Crash logs** |
| Collected / shared | Collected (not shared) |
| Processed ephemerally? | No |
| Required or optional? | **Optional** (user chooses to send) |
| Purpose | App functionality (fixing bugs) |
| Is all user data encrypted in transit? | Yes (HTTPS for downloads; email uses the user's mail provider's TLS) |
| Way to request deletion? | Yes: email the developer; and Settings → Delete everything removes all on-device data |

Photos, screenshots and statements you attach are stored only on the device (encrypted), so they are not
"collected" either. The app uses the system Photo Picker and file/folder pickers, so it requests **no storage or
media permissions**.

Re-check this form whenever you add a library: the CI privacy gate guarantees no network access, which is
what makes "No" accurate.

## 6. Smart edition (optional SMS reading), later

> **Why the smart APK won't install from WhatsApp, browsers or file managers in India:** Google Play Protect's
> "enhanced fraud protection" automatically blocks *sideloaded* apps that request READ_SMS / RECEIVE_SMS.
> This is not a bug in the app. The smart edition can only reach users through the Play Store, after the
> permission declaration below is approved. Play-installed apps are not affected.
>
> **Also:** if a copy was installed from the Play internal-testing link, Play re-signed it with Google's app
> signing key. A GitHub-built APK (signed with your upload key) can't install over it ("App not installed").
> Uninstall first (back up from Settings), or use the test build below.
>
> **Testing SMS reading yourself:** CI also builds `KharchaCheck-2.0.N-smart-TEST.apk`. It is a separate app
> ("Kharcha Check Test", package `com.kharchacheck.app.debug`) so it installs next to the real one. Install it
> over USB, which Play Protect's sideloading rule doesn't block:
> `adb install -r KharchaCheck-2.0.N-smart-TEST.apk`

Only after the standard edition is live:
1. Upload `KharchaCheck-2.0.N-smart.aab` to **Internal testing**. Play will ask for the **Permissions declaration**.
2. **Core functionality:** choose **SMS-based money management** ("apps that track and manage budget").
3. **Declaration text** (edit to taste):
   > Kharcha Check is a personal budgeting app. With the user's explicit opt-in in Settings, it reads incoming
   > bank and card transaction SMS (filtered by bank sender IDs) to pre-fill expense entries. OTPs, promotional
   > and personal messages are ignored. Message text is never stored; only amount, payee, date and payment
   > mode are kept, and every entry goes to a review inbox where the user confirms or edits it. The app has
   > no INTERNET permission, so SMS data cannot leave the device. Users can also share or paste messages
   > instead of granting the permission.
4. **Demo video:** record a short screen capture: Settings → turn on "Read bank SMS" → the in-app
   explanation → permission prompt → an entry appears in Review → editing it → turning the feature off.
   Upload it as an unlisted YouTube video and paste the link.
5. Once approved, promote the smart `.aab` to production. If rejected, keep shipping `standard`; share/paste
   still covers the use case.

## 7. Every later release
Push to `main` → download the new `.aab` → upload to the same track. `versionCode` = 1000 + GitHub run number,
so it always increases. Upload `mapping.txt` from the `KharchaCheck-mapping-2.0.N` artifact if Play asks.
