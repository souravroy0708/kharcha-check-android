# Release 2.0 — checklist

## Play Store "What's new" (under 500 characters)

```
Kharcha Check is free, with no ads and no premium plan.
• Say it: "450 for lunch at Paradise yesterday" fills the whole entry
• Long-press the app icon: Add expense, Speak it, Add income
• Works fully offline; only fund prices need internet
• NEW: Accounts & balances, Trips & events budgets, carry-over budgets
• Settings → Storage and Settings → Voice entry setup
• No SMS reading. Everything stays encrypted on your phone.
```

## Before you upload

1. GitHub Actions build is green (unit tests + privacy gate + lint).
2. Commit `app/schemas/` from the **room-schema** artifact (database is now version 2).
3. **Upgrade test**: install the previous build, add ~10 entries (mixed types, a screenshot, a statement),
   install the new build. Check entries, amounts, categories and attachments are unchanged.
4. **Backup round trip**: make a backup on the new build, Delete everything, restore. Check counts match.
5. **Voice** on 2 phones if possible (one Samsung/Xiaomi, one Pixel/Motorola):
   - First tap → explainer → Allow → listening works.
   - Deny twice → "Microphone is off" guide → Settings → back → listens automatically.
   - Google app → Permissions → Microphone → Don't allow → tap 🎤 → "Allow the microphone for Google" guide.
   - Settings → Voice entry setup shows ✅ for everything after fixing.
6. **Offline**: airplane mode, use the app for 5 minutes (add, edit, report, read a screenshot). Open Wealth → "You're offline" only if there are no saved prices.
7. Launcher shortcuts work on the **release** build (they target the release package id, not `.debug`).
8. Play Console: Data safety updated (Audio: collected, ephemeral, optional; see PLAY_CONSOLE_GUIDE.md), no SMS/Call log declaration, the old v1 bundle replaced in every track.

## New in this release (premium features, free)

- **Accounts & balances**: bank, cash, credit cards (shows what you owe), wallets; transfers move money between them; old entries can be linked in one tap.
- **Trips & events**: their own budget, entries tagged automatically while the trip is on, "₹X a day left", amounts typed in a foreign currency at your own rate.
- **Carry-over budgets** and a **weekly pace** line per category.

Extra checks for these (add to step 3 above): create a bank account and a card, add an expense on the card, pay the card bill as a Transfer from the bank, check both balances. Create a trip covering today, add an expense, confirm it's tagged and the trip card on Home updates.

## Still not included, on purpose

| Feature | Why |
| --- | --- |
| Hindi / other Indian-language UI | ~1,500 strings are written directly in the screens; translating them properly means moving every one into resources and having a native speaker check money terms. A machine-translated finance app can mislead users. |
| Family / shared sync | Needs a server, which breaks the "nothing leaves your phone" promise. |
| Automatic bank sync | Needs SMS reading or Account Aggregator access; both conflict with the privacy design. Statement import covers it. |
