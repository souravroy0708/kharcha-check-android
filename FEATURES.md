# Kharcha Check v2: feature → file map

| Feature | Files |
| --- | --- |
| Privacy gate (no ad ID, no location/contacts, HTTPS-only) | `AndroidManifest.xml`, `.github/workflows/build-apk.yml` |
| Encrypted database, Keystore key | `data/db/KharchaDatabase.kt`, `data/security/DbKeyStore.kt` |
| Manual entry, calculator keypad, category picker, leak nudge | `feature/EditTxnScreen.kt`, `domain/AmountExpression.kt` |
| 73 categories in Needs / Wants / Leaks / Invest / Income / Transfers | `core/model/DefaultCategories.kt` |
| 100 daily money thoughts (unique per day, per-install order) | `domain/DailyWisdom.kt`, `feature/HomeScreen.kt` |
| Monthly report, 50/30/20, insights, PDF / CSV | `domain/ReportEngine.kt`, `feature/ReportScreen.kt`, `export/ReportPdf.kt`, `domain/CsvWriter.kt` |
| Streaks, XP, levels, badges, challenges | `domain/Gamification.kt`, `feature/GrowScreen.kt` |
| Golden rules, SIP / Rule of 72 maths | `domain/GoldenRules.kt`, `domain/InvestMath.kt` |
| Share / paste bank-message text (no SMS permission) | `domain/sms/SmsParser.kt`, `MainActivity.kt` |
| Review inbox, learning rules | `feature/ActivityScreen.kt`, `data/repo/FinanceRepository.kt` |
| Password backup / restore | `domain/BackupCrypto.kt`, `export/BackupManager.kt` |
| App lock, screenshot block, reminders | `MainActivity.kt`, `work/DailyWorker.kt`, `work/Notifier.kt` |
| Repeating monthly entries (rent, EMI, SIP, salary) | `domain/Recurring.kt`, `data/repo/FinanceRepository.kt`, `feature/SettingsScreens.kt` (RecurringScreen) |
| Home-screen widget (no amounts shown) | `widget/KharchaWidget.kt`, `res/layout/widget_kharcha.xml` |
| Profile photo, receipts / UPI screenshots, monthly statements (encrypted) | `data/files/FileVault.kt`, `data/repo/AttachmentRepository.kt`, `ui/components/Attachments.kt`, `feature/StatementsScreen.kt` |
| Savings rate falls back to Settings income when none is logged | `domain/ReportEngine.kt` (`fallbackIncomePaise`) |
| ✨ Auto-read screenshots (amount, payee, date, ref, category) | `domain/read/ReceiptParser.kt`, `read/AutoReader.kt`, `feature/EditTxnScreen.kt` (`applyReceipt`) |
| ✨ Statements → draft line items (digital PDF text, scanned PDF/photo OCR, password PDFs on all Android, .xlsx / HTML .xls / CSV, open from Gmail/Drive) with edit, drafts or confirm | `domain/read/StatementParser.kt`, `domain/read/SpreadsheetReader.kt`, `domain/read/Classifier.kt`, `read/AutoReader.kt`, `feature/ImportScreen.kt` |
| Offline OCR engine (swappable) | `read/OcrEngine.kt`, `assets/tessdata/eng.traineddata` |
| Trends: income vs spend, savings rate, category donut & run-rates, buckets over time, heatmap, regular payments, outliers, payees | `domain/AnalyticsEngine.kt`, `feature/TrendsScreen.kt`, `ui/components/Charts.kt` |
| Category drill-down | `feature/TrendsScreen.kt` (`CategoryInsightsScreen`) |
| Calendar heatmap in monthly report | `ui/components/Charts.kt` (`CalendarHeat`), `feature/ReportScreen.kt` |
| Monday "your week in money" notification | `domain/AnalyticsEngine.kt` (`lastWeek`), `work/DailyWorker.kt` |
| Split bills & dues, reminders via share sheet | `domain/MoneyTools.kt` (`Dues`), `data/repo/ToolsRepository.kt`, `feature/ToolsScreens.kt`, `feature/EditTxnScreen.kt` |
| Bills & due-date reminders | `domain/MoneyTools.kt` (`Bills`), `feature/ToolsScreens.kt`, `work/DailyWorker.kt` |
| Kharcha Wrapped (month / year story, personas, image share) | `domain/Wrapped.kt`, `feature/WrappedScreen.kt` |
| Quick Settings tile | `widget/QuickAddTile.kt` |
| 🎤 Voice entry and mic on text fields (no mic permission) | `domain/VoiceEntry.kt`, `ui/components/VoiceInput.kt`, `feature/EditTxnScreen.kt` (`applyVoice`), `feature/HomeScreen.kt` |
| Logo, themed icon, store graphics | `res/drawable/ic_launcher_*.xml`, `docs/store/` |
| Wealth & net worth with daily AMFI NAVs | `domain/Wealth.kt`, `data/repo/WealthRepository.kt`, `feature/WealthScreen.kt`, `work/BackgroundWork.kt` (`NavRefreshWorker`) |
| Automatic encrypted backup to a chosen folder (Drive) | `work/BackgroundWork.kt` (`AutoBackupWorker`), `data/security/SecretBox.kt`, `domain/Wealth.kt` (`BackupRotation`) |
| User-controlled crash reports | `work/CrashReporter.kt`, `ui/Root.kt` |
| Smart Money Guide: salary plan, money ladder, SIP split, online-spending traps | `domain/SalaryPlanner.kt`, `domain/OnlineSpending.kt`, `feature/GuideScreen.kt` |
| 3 daily nudges (invest / avoid spending / saving trick), personal + unique pools | `domain/Nudges.kt`, `work/NudgeWorker.kt`, `feature/SettingsScreens.kt` |
| Position-aware PDF statement table reader | `domain/read/PdfTable.kt`, `read/AutoReader.kt` (`GlyphStripper`) |
| Duplicate-proof imports | `domain/Dedupe.kt`, `feature/ImportScreen.kt` |
| Excel / CSV export screen | `domain/export/Exporter.kt`, `domain/export/XlsxWriter.kt`, `feature/ExportScreen.kt` |
| Bulk edit (multi-select), undo | `feature/ActivityScreen.kt`, `ui/AppViewModel.kt` |
| Hidden entries (biometric unlock), mask amounts | `ui/AppViewModel.kt` (`derive`), `feature/HiddenScreen.kt`, `feature/HomeScreen.kt`, `MainActivity.kt` (`onStop`) |
| Import history with batch delete | `feature/StatementsScreen.kt`, `ui/AppViewModel.kt` (`deleteImportBatch`) |
| Balance-verified statement amounts (unsure → 0, never guessed) | `domain/read/PdfTable.kt` (`verifyWithBalance`), `feature/ImportScreen.kt` |
| Goal planner: inflation-adjusted SIPs, leak swap, rescued money, SIP reminders | `domain/GoalPlanner.kt`, `feature/GoalsScreen.kt`, `work/DailyWorker.kt`, `data/repo/SettingsRepository.kt` (`GoalPlansJson`) |
| Tax saver (80C / 80CCD(1B) / 80D) | `domain/TaxSaver.kt`, `feature/TaxScreen.kt`, `work/DailyWorker.kt` |
| Subscription manager | `domain/Subscriptions.kt`, `feature/SubscriptionsScreen.kt` |
| Card statement summary → bill | `domain/read/StatementParser.kt` (`cardSummary`), `read/AutoReader.kt`, `feature/ImportScreen.kt` |
| Wishlist cooling-off, hours-of-work price, cost per use | `domain/Wishlist.kt`, `feature/WishlistScreen.kt`, `feature/EditTxnScreen.kt`, `work/DailyWorker.kt` |
| Books & research library (sourced, paraphrased) | `domain/WisdomLibrary.kt`, `feature/GuideScreen.kt` |
| Money Health Score + Tools hub | `domain/HealthScore.kt`, `feature/HealthScreen.kt`, `feature/ToolsScreen.kt`, `feature/HomeScreen.kt` |
