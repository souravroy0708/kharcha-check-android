# Product & Build Spec → Implementation Map (`Kharcha Check`)

| Spec Section | Source File(s) |
| :--- | :--- |
| **Data Model (8 Entities, Integer Paise, UTC + TimeZone)** | `Models.kt`, `LocalDb.kt` |
| **SMS Capture & 9-Step Parsing Pipeline + Golden Set** | `SmsParser.kt`, `SmsReceiver.kt`, `SmsParserTest.kt` |
| **Hardware Keystore Encryption & Passphrase Backup** | `CryptoVault.kt`, `MainActivity.kt` |
| **Screen 1: Onboarding (3 Screens) & PIN Lock** | `ui/OnboardingAndLock.kt` |
| **Screen 2 & 3: Home Dashboard & < 5s Numpad Quick Add** | `ui/HomeAndQuickAdd.kt` |
| **Screen 4 & 5: Review Tray (+ Rule Prompt) & Transactions** | `ui/ReviewTrayAndTransactions.kt` |
| **Screen 6 & 8: Live Insights & Per-Category Budgets** | `ui/InsightsAndBudgets.kt` |
| **Screen 7: 9-Step Guided Month-End Review + PDF Export** | `ui/MonthEndReviewFlow.kt`, `PdfReportExporter.kt` |
| **Screen 9: Settings, Golden Set Verifier, Rules, Backup** | `ui/SettingsTab.kt`, `Prefs.kt` |
| **Weekly Glance & Month-End Local Notifications** | `ReminderWorker.kt` |
| **Zero-INTERNET CI Verification & APK/AAB Release Pipeline** | `.github/workflows/build-apk.yml`, `AndroidManifest.xml` |
