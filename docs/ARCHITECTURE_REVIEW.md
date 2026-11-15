# Kharcha Check: architecture review

*Reviewer's verdict:* **feature-complete for v1. The risk now is quality, not missing features.**
In a short time the app reached ~12,000 lines of Kotlin with ~40 features. The domain layer is strong
and well tested; the Android layer has only ever been verified by compiling and by your own use.
Ship what exists, make it trustworthy, and let real users tell you what to build next.

## What's solid

| Area | Why it's good |
| --- | --- |
| Layering | Pure-Kotlin domain (`domain/`) with no Android imports: parsers, reports, analytics, gamification, export all testable on a laptop. |
| Money correctness | Integer paise everywhere, never floating point; Indian digit grouping done by hand. |
| Security | SQLCipher DB + Keystore-wrapped key; encrypted file vault; FLAG_SECURE; app lock; no SMS / location / contacts / ad ID, enforced by CI. |
| Statement accuracy | Column-aware PDF reading, every amount verified against the running balance, **unconfirmed → 0, never guessed**. |
| Data safety | Count-based dedupe, undo on every destructive action, password backups, auto-backup. |
| Tests | 114 unit tests on the logic that decides money values, including a faithful ICICI statement replica and goal-planning maths. |

## Findings

| # | Severity | Finding | Status |
| --- | --- | --- | --- |
| 1 | 🔴 High | **No database-migration safety.** Users have data in DB v1. Any entity change without a migration crashes the app on launch for everyone; the usual "fix" (`fallbackToDestructiveMigration`) wipes all data. | **Fixed:** `MIGRATIONS` hook, explicit no-destructive-fallback, CI *Database schema guard*, `docs/DATABASE.md`. **Action for you:** commit the `room-schema` artifact's `1.json` once. |
| 2 | 🟠 Medium | **Slow recomputation at scale.** With 3 years of heavy use (13k entries) gamification took ~194 ms on desktop (≈1–2 s on a mid-range phone) after every save. | **Fixed:** cached entry dates; month-sliced badge checks. Now 44 ms (4.4× faster), identical results. |
| 3 | 🟠 Medium | **Android UI is untested.** Every screen bug so far was found by hand. | Open: add a few Compose UI smoke tests (add entry, import review, hide/unhide) on a CI emulator. |
| 4 | 🟠 Medium | **`AppViewModel` is a god object** (~550 lines): entries, wealth, dues, bills, backup, hidden entries, guide. | Open: split by feature (`EntriesViewModel`, `WealthViewModel`, `ToolsViewModel`, `SettingsViewModel`) before adding more. |
| 5 | 🟡 Low | No static analysis in CI. | **Fixed:** Android Lint runs on every build, HTML report uploaded as `lint-report` (doesn't block yet; fix the worst, then make it blocking). |
| 6 | 🟡 Low | Hidden entries are stored as an id set in settings, not on the entry. Works (ids are cleaned on delete), but a DB column is the long-term home. | Open: move to a `hidden` column in the first planned DB migration. |
| 7 | 🟡 Low | Backups (`.kcbak`) didn't include attachments (statements, receipts, profile photo): lost on a new phone. | **Fixed:** backup format v3 (KCB3) = ZIP streamed through chunked AES-256-GCM (1 MiB chunks, numbered + final-flag authenticated, so truncation / reordering / tampering are detected). Never loads a whole backup into memory. Restore stages files and swaps them in only after everything succeeded. Old KCB2 backups still restore. Toggle: Settings → *Include statements & receipts in backups* (on by default). |
| 8 | 🟡 Low | English only, for an Indian audience. | Open: Hindi first (strings → `strings.xml`), then Bengali / Tamil / Marathi based on installs. |
| 9 | 🟡 Low | Cold start not optimised. | Open: Baseline Profile module (typically 20–30% faster startup). |

## Deliberately deferred (and why)

- **#4 ViewModel split** and **#8 Hindi** touch every screen. Without a compiler in the loop they risk breaking a green build; do them together in Android Studio, one feature at a time.
- **#3 UI tests** need an emulator in CI; flaky unverified tests are worse than none. Add with #4.

## Added in this review

- **Budget alerts:** saving an entry that crosses 80% / 100% of a category or monthly budget shows a warning; the daily check catches crossings from statement imports. Each alert at most once a month.

## Recommended order before and after launch

1. **Now:** push this build; commit `1.json` from the `room-schema` artifact; open the `lint-report` and fix anything red.
2. **Internal testing (1–2 weeks):** you + 5–10 friends; import real statements from different banks; send failing pages (masked) for parser tuning.
3. **Closed testing (required 14 days, 12+ testers for new personal accounts).** Watch crash emails.
4. **Before scaling:** UI smoke tests (#3), ViewModel split (#4).
5. **After launch, driven by feedback:** Hindi (#8), attachments in backups (#7), Baseline Profile (#9).

## Don't build yet

Multi-device sync, family sharing, a server, AI chat, more gamification. Each is a big surface area.
Wait until real users ask for one repeatedly.
