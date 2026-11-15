# Kharcha Check vs. typical premium money apps

An honest self-assessment for the Play listing and roadmap. It compares against **capabilities commonly found in
paid / premium expense and budgeting apps**, not against specific products' current feature lists (those change often).

## Where Kharcha Check is ahead

| Area | Kharcha Check | Common in premium apps |
| --- | --- | --- |
| Privacy | No account, no server holding entries, encrypted DB, no ads/trackers, no SMS access (enforced by CI), hidden entries behind biometrics | Account + cloud sync; many read SMS or link bank accounts |
| Statement accuracy | Column-aware PDF reading, every amount checked against the running balance; **unsure → blank, never a guess**; dedupe on re-import | Often SMS-based; statement import less common or less careful |
| India-specific planning | Salary plan, money ladder, goal SIPs with goal-specific inflation, Sukanya Samriddhi note, 80C/80D/NPS tracker, dark-pattern rights (CCPA, NCH 1915) | Usually generic budgeting; tax help is a separate product |
| Behaviour change | Wishlist cooling-off, price in hours of work, cost per use, leak swap → goals, money rescued, sourced research library, gamification | Mostly charts and budgets |
| Price | Free, no ads | Subscription |

## At parity

Budgets with alerts · bill reminders · subscriptions · recurring entries · split & dues · net worth with live fund NAVs ·
Excel/CSV export · encrypted backups (with files) · widget · dark mode · app lock · yearly "Wrapped" · health score.

## Where premium apps are still ahead (roadmap)

| Gap | Why it matters | Effort / risk |
| --- | --- | --- |
| **Accounts & wallets** (bank, cash, card balances that move with each entry) | Many users think in accounts | High: needs a DB migration (see DATABASE.md) |
| **Automatic bank data** (Account Aggregator) | No manual import | High: regulated (RBI AA framework), needs a licensed partner and a server |
| **Multi-device sync / family sharing** | Couples manage money together | High: server, auth, encryption design |
| **Polish from real-device testing** (animations, edge cases, accessibility) | First impressions and ratings | Medium: needs Android Studio + testers |
| **Languages** (Hindi first) | Reach | Medium: string extraction across screens |
| **Budget rollover / envelope budgeting** | Power users | Low–medium |

## Recommendation

Feature-wise the app is complete for launch. The fastest path to a 4.5★ rating is **reliability and polish**:
internal test → fix what testers hit → closed test (14 days, 12+ testers) → production. Then add *one* roadmap item
at a time, chosen by what users ask for most.
