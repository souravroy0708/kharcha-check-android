# Kharcha Check: Play Console fill-in guide & closed testing

Everything to enter in Play Console, in the order the console asks for it, plus the closed-testing steps.
Character limits were checked: title 30/30 · short description 78/80 · full description 2353/4000 · release notes 420/500.

---

## 0. Before you start (one-time)

- [ ] **Privacy policy online.** GitHub → your repo → **Settings → Pages** → Source: *Deploy from a branch* → Branch `main`, folder `/docs` → Save.
      After a minute it's live at: **https://souravroy0708.github.io/kharcha-check-android/privacy.html**. Open it on your phone to confirm.
- [ ] **Signed release bundle.** The `KC_KEYSTORE_*` secrets must be set so CI produces `KharchaCheck-2.0.N.aab`
      (download it from the latest GitHub Release or the Actions run). **Back up the upload keystore**; losing it means a key reset with Google.
- [ ] **Line up 15–20 testers now**, not 12. Google needs **12 still opted in on day 14**; people drop out. Family, friends, colleagues,
      anyone with an Android phone and a Gmail address. Collect their Gmail addresses.
- [ ] **Screenshots made from demo data** (section 3). Never use your real bank entries in public screenshots.

---

## 1. Create the app (Home → Create app)

| Field | Enter |
| --- | --- |
| App name | `Kharcha Check: Expense Tracker` |
| Default language | English (India) – en-IN |
| App or game | App |
| Free or paid | Free |
| Declarations | Tick Developer Program Policies and US export laws |

---

## 2. Store settings (Grow users → Store presence → Store settings)

| Field | Enter |
| --- | --- |
| App category | **Finance** |
| Tags | Choose up to 5 that Play offers for budgeting / expense tracking / personal finance |
| Email address | `tosourav6@gmail.com` |
| Website | `https://souravroy0708.github.io/kharcha-check-android/` (optional) |
| Phone | Leave empty (optional) |
| External marketing | Your choice (on is fine) |

---

## 3. Main store listing (Grow users → Store presence → Main store listing)

**App name** (30 max)
```
Kharcha Check: Expense Tracker
```

**Short description** (80 max)
```
Private expense tracker: import bank statements, stop leaks, plan goals & save
```

**Full description** (4000 max)
```
Paisa kahan gaya? Ab pata chalega.

Kharcha Check is a private, ad-free expense tracker made for India. Log a spend in seconds or import your bank statement, see exactly where your money leaks, and turn those savings into goals that matter: a home, your child's education, retirement.

PRIVATE BY DESIGN
• No account, no sign-up, no ads, no tracking
• Your entries are encrypted on your phone
• The app never reads your SMS
• Hide private entries behind your fingerprint
• Backups are encrypted with your password and go only where you choose

IMPORT BANK & CARD STATEMENTS
• PDF (including password-protected), Excel or CSV, up to a full year
• Every amount is checked against your running balance; if one can't be confirmed, it's left blank for you to fill instead of guessed
• Re-importing the same statement never creates duplicates
• Credit card statements set your bill reminder automatically

SEE AND STOP MONEY LEAKS
• Needs, wants and leaks, with budgets and alerts at 80% and 100%
• Subscription finder: see every repeating payment and its yearly cost
• Wishlist with a cooling-off period: park a purchase, decide after the wait, and count what you saved by skipping
• Every want shown in hours of your work
• Guide to online shopping traps, with your rights under India's dark-pattern rules

PLAN, SAVE AND INVEST
• Salary plan: how much to spend, save and invest from your income
• Goal planner with inflation: home, child's education or wedding, retirement, car, travel, emergency fund
• Monthly SIP needed for each goal, flat or stepping up 10% a year
• Net worth with mutual fund values updated daily from AMFI
• Tax saver for 80C, NPS and health insurance, with reminders before 31 March

BUILD BETTER HABITS
• Money Health Score from six simple yardsticks, with your next best step
• Three short daily nudges: invest with purpose, avoid unnecessary spending, and a practical saving trick
• Research-backed tips from respected books and studies
• Streaks, XP, badges and challenges
• Monthly report and a shareable "Wrapped" story of your year

ALSO INCLUDED
Split bills and track who owes whom · bill and card due-date reminders · recurring entries · Excel and CSV export · home-screen widget · dark mode · app lock.

Kharcha Check helps you understand and plan your money. It is educational and not financial, tax or investment advice.
```

**Graphics**

| Asset | Requirement | File |
| --- | --- | --- |
| App icon | 512 × 512 PNG | `kharcha-check-icon-512.png` (also `docs/store/play-icon-512.png`) |
| Feature graphic | 1024 × 500 PNG/JPG | `kharcha-check-feature-graphic.png` (also `docs/store/`) |
| Phone screenshots | At least 2, ideally 4–8, portrait 9:16 (1080 × 1920 is ideal) | Take on your phone, see below |

**Screenshots to take** (fill the app with a few weeks of *demo* entries first; turn off "Hide in recent apps & block screenshots" in Settings while capturing):
1. **Home**: spending ring, Money Health Score, daily tip
2. **Statement import review**: rows marked "already in app" / "✎ Enter amount" (use a demo CSV, not your real PDF)
3. **Monthly report**: needs / wants / leaks split
4. **Goals & plans**: a goal with its monthly SIP
5. **Wishlist**: an item with "hours of your work"
6. **Tools** hub grid

---

## 4. App content (Policy and programs → App content)

Complete **every** item; the release can't go to review until all are done.

### Privacy policy
`https://souravroy0708.github.io/kharcha-check-android/privacy.html`

### App access
**All functionality is available without any special access.** (No login. App lock is off by default.)

### Ads
**No, my app does not contain ads.**

### Content rating
Start questionnaire → email `tosourav6@gmail.com` → category **All other app types**. Answer **No** to: violence, sexuality,
language, controlled substances, crude humour, gambling (real or simulated), user-generated content / users interacting,
sharing location, digital purchases, unrestricted internet access. Expected result: suitable for everyone.

### Target audience and content
- Target age: **18 and over** only (a money app for adults)
- Could the app unintentionally appeal to children? **No**

### News apps
**No.**

### Data safety
| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** |
| Do you provide a way for users to request that their data is deleted? | **Yes** (Settings → Delete everything; uninstalling; or email) |
| Data types collected | **App info and performance → Crash logs**, and **Audio → Voice or sound recordings** (🎤 voice entry) |
| Crash logs: collected / shared | Collected: **Yes** · Shared: **No** |
| Processed ephemerally? | **No** |
| Required or optional? | **Optional** (users choose to send the report) |
| Purpose | **App functionality** |
| Voice or sound recordings: collected / shared | Collected: **Yes** · Shared: **No** · Processed ephemerally: **Yes** · **Optional** · Purpose: **App functionality**. Safe answer: on phones without on-device recognition, Android's speech service (usually Google) converts the speech off the phone. The app never stores or uploads audio itself. |
| Everything else (financial info, personal info, photos, files, location, contacts, messages…) | **Not collected.** Entries, statements and photos stay on the device, encrypted; backups go only to storage the user picks. The AMFI price download sends no user data. |

### Permissions shown on the listing
`RECORD_AUDIO` (microphone) is a normal runtime permission: **no Permissions declaration form** is needed (unlike SMS / call log). It is requested only when the user taps 🎤, after an in-app explanation.

### Government apps
**No.**

### Financial features
Select **Other** and describe it as:
```
Personal budgeting and expense tracking: manual entries and user-imported bank statements, savings-goal calculators and mutual fund value tracking from public AMFI NAV data. The app does not offer loans, credit, payments, money transfers, banking, trading or insurance, and does not give personalised financial advice.
```
Don't pick "My app doesn't provide any financial features"; for a Finance-category money tracker a reviewer may judge that inaccurate.
Don't pick loans, payments, banking, trading or financial advice; the app does none of these.

### Health apps
**My app does not have any health features.**

### Advertising ID
**No**, the app does not use an advertising ID (it is removed from the manifest).

---

## 5. Countries / regions

Testing tracks and production each have their own country list. Start with **India**; add more later if you like.

---

## 6. Closed testing (Test and release → Testing → Closed testing)

1. **Create track** (or use the default *Closed testing – Alpha*).
2. **Countries/regions** → add **India**.
3. **Testers** tab → choose **Email list** → *Create email list* (e.g. "Kharcha testers") → paste your 15–20 Gmail addresses → Save → tick the list.
   *Feedback URL or email*: `tosourav6@gmail.com` → **Save**.
4. **Create new release** → upload `KharchaCheck-2.0.N.aab` (accept Play App Signing if asked).
   Release name: leave the default. Release notes (en-IN):
```
First closed test of Kharcha Check.
• Import bank and card statements (PDF, Excel, CSV) with amounts checked against your balance
• Budgets, alerts, subscriptions finder and a cooling-off wishlist
• Goal planner with inflation, tax saver and net worth
• Money Health Score and three daily money nudges
Please try importing a real statement and tell us if any amount looks wrong (Import screen → "Send a reading report").
```
5. **Next → Save** → open **Publishing overview** → **Send changes for review**. First reviews can take several days.
6. When approved, the Testers tab shows a **"Join on Android" / opt-in link**. Send it with the message below.

**Message for testers (WhatsApp / email)**
```
Hi! I've built a private expense tracker app called Kharcha Check and need your help testing it on Google Play for 14 days.

1. Open this link on your Android phone (use the Gmail I added): <OPT-IN LINK>
2. Tap "Become a tester", then "Download it on Google Play" and install.
3. Please keep it installed for at least 14 days and use it now and then: add a few expenses, try importing a bank statement (PDF/Excel/CSV), set a budget or goal.
4. Please don't uninstall or leave the test before 14 days, or Google won't count it.

Found a bug or have an idea? Reply here or email tosourav6@gmail.com. Thank you! 🙏
```

**During the 14 days**
- Check **Testers** in the console: the count of opted-in testers must stay **12+** until you apply. Add more if anyone drops.
- Remind testers on day 3 and day 10 to open the app.
- Ship at least one update from their feedback (bump via a normal CI build, upload to the same closed track). Google looks for real testing.
- Note every piece of feedback and what you changed; you'll need it for the application.

---

## 7. Apply for production (Dashboard, after 14 days with 12+ opted in)

Dashboard → **Apply for production** → answer the questionnaire. Suggested answers (edit with your real details):

- **How did you recruit testers?** Friends, family and colleagues who track household expenses, invited by email and WhatsApp.
- **How easy was it?** Fairly easy; I invited ~18 people to keep at least 12 active.
- **Describe tester engagement.** Testers logged daily expenses, imported bank statements from HDFC / SBI / ICICI and others, set budgets and goals, and used the wishlist and reports.
- **Summarise the feedback.** *(List real points, e.g.)* a statement layout from one bank needed better column detection; a request for clearer amount editing in import review; notification timing preferences.
- **How did you collect feedback?** WhatsApp group, email, and the app's in-app "Send a reading report" for statement-import issues.
- **What changes did you make?** *(List real fixes and the versions they shipped in.)*
- **Who is the app for?** Indian adults who want to track spending privately, cut unnecessary expenses and plan savings goals.
- **How does it provide value?** Private (no account, no ads, encrypted, no SMS access); accurate statement import checked against the running balance; leak control, goals with inflation-adjusted SIPs and tax-saving tracking.
- **Expected installs in the first year?** Pick a realistic range.
- **How did you decide it's ready?** All reported issues fixed, crash-free during testing, statements from several banks imported correctly, release reviewed against policy.

Google usually replies **within about 7 days**. If it says more testing is needed, the closed test restarts with the same testers;
keep them engaged and apply again.

---

## 8. Timeline

| Step | Typical time |
| --- | --- |
| Store listing + App content | 1–2 hours |
| Closed test review | 1–7 days |
| Closed test with 12+ testers | 14 days (continuous) |
| Production application review | up to ~7 days |
| Production release review | 1–7 days |
| **Total** | **about 3–4 weeks** |
