package com.kharchacheck.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.BuildConfig
import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.data.repo.ThemeMode
import com.kharchacheck.app.sms.SmsSupport
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.components.CategoryIcon
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.MoneyDialog
import com.kharchacheck.app.ui.components.PasswordDialog
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.ProfileAvatar
import com.kharchacheck.app.ui.components.Segmented
import com.kharchacheck.app.ui.components.SettingRow
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.components.SwitchRow
import com.kharchacheck.app.ui.components.TextDialog
import com.kharchacheck.app.ui.nav.BudgetsRoute
import com.kharchacheck.app.ui.nav.BillsRoute
import com.kharchacheck.app.ui.nav.DuesRoute
import com.kharchacheck.app.ui.nav.WealthRoute
import com.kharchacheck.app.ui.nav.WrappedRoute
import com.kharchacheck.app.ui.nav.CategoriesRoute
import com.kharchacheck.app.ui.nav.InboxRoute
import com.kharchacheck.app.ui.nav.PrivacyRoute
import com.kharchacheck.app.ui.nav.RecurringRoute
import com.kharchacheck.app.ui.nav.StatementsRoute
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.color
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Dlg { AUTO_BACKUP, LICENCES, NONE, NAME, INCOME, BUDGET, AGE, RETURN, SMALL, TIME, BACKUP, RESTORE, WIPE, SMS_INFO }

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val s = data.settings
    val p = LocalPlatform.current
    var dlg by rememberSaveable { mutableStateOf(Dlg.NONE) }
    val recurringCount = vm.recurring.collectAsStateWithLifecycle().value.size
    val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    var photoMenu by rememberSaveable { mutableStateOf(false) }
    SubScreen("Settings", onBack = { nav.popBackStack() }) {
        item {
            KcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(vm, s.name, 64.dp) { photoMenu = true }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name.ifBlank { "Add your name" }, style = MaterialTheme.typography.titleLarge, color = Kc.colors.text,
                            modifier = Modifier.clickable { dlg = Dlg.NAME })
                        Text("Level ${data.game.level.number} · ${data.game.level.name}", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                        Text("Change photo", style = MaterialTheme.typography.labelLarge, color = if (Kc.colors.dark) Haldi else Ink,
                            modifier = Modifier.clickable { photoMenu = true }.padding(top = 4.dp))
                    }
                }
            }
        }
        item {
            KcCard(onClick = { nav.navigate(PrivacyRoute) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔒", fontSize = 30.sp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Private by design", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                        Text("Encrypted on this phone · no account · no ads · no tracking", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = Kc.colors.muted)
                }
            }
        }
        item {
            KcCard(title = "You & your money") {
                SettingRow("Name", "Used only for greetings", s.name.ifBlank { "Not set" }) { dlg = Dlg.NAME }
                SettingRow("Monthly income", "Powers savings rate and 50/30/20", if (s.monthlyIncomePaise > 0) Money.format(s.monthlyIncomePaise) else "Not set") { dlg = Dlg.INCOME }
                SettingRow("Monthly spending budget", "The ring on Home", if (s.monthlyBudgetPaise > 0) Money.format(s.monthlyBudgetPaise) else "Not set") { dlg = Dlg.BUDGET }
                SettingRow("Category budgets", "Limits for the categories you want to watch", "${data.budgets.size} set") { nav.navigate(BudgetsRoute) }
                SettingRow("Categories", "Rename, recolour, hide, or create your own", "${data.activeCategories.size}") { nav.navigate(CategoriesRoute) }
                SettingRow("Your age", "Optional, for the 100-minus-age tip", if (s.age > 0) "${s.age}" else "Not set") { dlg = Dlg.AGE }
                SettingRow("Expected yearly return", "Used in projections only", "${s.expectedReturnPct}%") { dlg = Dlg.RETURN }
                SettingRow("Small-payment threshold", "Payments below this count as small tickets", "₹${s.smallTicketRupees}") { dlg = Dlg.SMALL }
            }
        }
        item {
            KcCard(title = "Adding entries") {
                Text("Fastest: tap the yellow +. Or share any bank SMS / UPI receipt to Kharcha Check from Messages, GPay or PhonePe, or copy it and tap “Paste SMS” on Home.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                SwitchRow("✨ Auto-read screenshots & statements",
                    "Fills entries from GPay / PhonePe / Paytm screenshots and reads bank, card and UPI statements. A built-in offline engine does it on this phone; nothing is uploaded.",
                    s.autoRead) { vm.setAutoRead(it) }
                if (SmsSupport.available) {
                    SwitchRow("Read bank SMS automatically", "Optional. Only bank alerts are read; messages are never stored or sent. Everything lands in Review first.",
                        s.smsCapture && SmsSupport.hasPermission(androidx.compose.ui.platform.LocalContext.current)) { on ->
                        if (on) dlg = Dlg.SMS_INFO else vm.setSmsCapture(false)
                    }
                    if (s.smsCapture) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.importSms(30) }) { Text("Import last 30 days") }
                        OutlinedButton(onClick = { vm.importSms(90) }) { Text("90 days") }
                    }
                }
                SettingRow("Repeating entries", "Rent, EMIs, SIPs and subscriptions added for you each month", "${recurringCount}") { nav.navigate(RecurringRoute) }
                SettingRow("Review inbox", "Entries waiting for a quick check", "${data.inbox.size}") { nav.navigate(InboxRoute) }
            }
        }
        item {
            KcCard(title = "Money tools") {
                val dues = vm.dues.collectAsStateWithLifecycle().value
                val bills = vm.bills.collectAsStateWithLifecycle().value
                SettingRow("💼 Wealth & net worth", "Mutual funds with daily prices, FDs, PPF, bank balances, loans") { nav.navigate(WealthRoute) }
                SettingRow("🤝 Split & dues", "Who owes you, who you owe, one-tap reminders", com.kharchacheck.app.domain.Dues.toCollect(dues).takeIf { it > 0 }?.let { Money.compact(it) }) { nav.navigate(DuesRoute) }
                SettingRow("🧾 Bills & due dates", "Card, rent, electricity, insurance reminders", "${bills.size}") { nav.navigate(BillsRoute) }
                SettingRow("🎁 Kharcha Wrapped ${java.time.Year.now()}", "Your year in money, as a shareable story") { nav.navigate(WrappedRoute(java.time.Year.now().toString())) }
            }
        }
        item {
            KcCard(title = "Reminders") {
                SwitchRow("Daily nudge", "Only if you haven't logged anything that day. Plus a month-end report.", s.reminder) { on ->
                    if (on) p.requestNotifications(); vm.setReminder(on, s.reminderHour, s.reminderMinute)
                }
                if (s.reminder) SettingRow("Remind me at", null, LocalTime.of(s.reminderHour, s.reminderMinute).format(fmt)) { dlg = Dlg.TIME }
            }
        }
        item {
            KcCard(title = "Security") {
                SwitchRow("App lock", "Fingerprint, face or your phone's screen lock", s.appLock) { on ->
                    if (!on) { if (p.canUseDeviceLock()) p.authenticate("Turn off app lock") { vm.setAppLock(false) } else vm.setAppLock(false) }
                    else if (p.canUseDeviceLock()) p.authenticate("Turn on app lock") { vm.setAppLock(true) }
                    else vm.say("Set a screen lock on your phone first (Settings → Security).")
                }
                SwitchRow("Hide in recent apps & block screenshots", "Recommended for a money app", s.secureScreen) { vm.setSecureScreen(it) }
            }
        }
        item {
            KcCard(title = "Appearance") {
                Segmented(listOf("System", "Light", "Dark"), ThemeMode.entries.indexOf(s.theme)) { vm.setTheme(ThemeMode.entries[it]) }
            }
        }
        item {
            KcCard(title = "Your data") {
                SwitchRow("Automatic backup", if (s.autoBackup) "Daily, encrypted, into the folder you chose" +
                    (if (s.lastBackupAt > 0) " · last ${java.time.Instant.ofEpochMilli(s.lastBackupAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}" else "")
                    else "Pick a folder once (choose Google Drive to back up to the cloud). Encrypted before it leaves the phone.", s.autoBackup) { on ->
                    if (on) dlg = Dlg.AUTO_BACKUP else vm.disableAutoBackup()
                }
                if (s.autoBackup) SettingRow("Back up now", null) { vm.backupNow() }
                SettingRow("Backup with password", "Save an encrypted .kcbak file anywhere you choose") { dlg = Dlg.BACKUP }
                SettingRow("Restore from backup", "Replaces everything on this phone") { dlg = Dlg.RESTORE }
                SettingRow("Statements & receipts", "Bank, credit card and UPI statements by month") { nav.navigate(StatementsRoute(java.time.YearMonth.now().toString())) }
                SettingRow("Export all entries (CSV)", "Open in Excel or Google Sheets") { p.exportCsv() }
                HorizontalDivider(color = Kc.colors.line)
                SettingRow("Delete everything", "Erase all entries, goals and settings from this phone") { dlg = Dlg.WIPE }
            }
        }
        item {
            KcCard(title = "About") {
                SettingRow("Version", null, BuildConfig.VERSION_NAME + if (SmsSupport.available) " · Smart" else "")
                SettingRow("Send feedback", "Opens your email app. Nothing is sent automatically.") { p.emailSupport() }
                SettingRow("Open-source licences", "The free software that makes this app possible") { dlg = Dlg.LICENCES }
                Text("Kharcha Check is educational and not financial advice.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
        }
    }

    if (photoMenu) AlertDialog(
        onDismissRequest = { photoMenu = false },
        title = { Text("Profile photo") },
        text = { Text("Your photo stays encrypted on this phone. It's only shown inside the app.") },
        confirmButton = { TextButton(onClick = { photoMenu = false; p.pickImage { vm.setProfilePhoto(it) } }) { Text("Choose photo") } },
        dismissButton = { TextButton(onClick = { photoMenu = false; vm.removeProfilePhoto() }) { Text("Remove") } },
    )

    when (dlg) {
        Dlg.NAME -> TextDialog("Your name", s.name, "Name", { dlg = Dlg.NONE }) { vm.setName(it); dlg = Dlg.NONE }
        Dlg.INCOME -> MoneyDialog("Monthly income", s.monthlyIncomePaise, "Take-home pay. You can also log income entries each month.", { dlg = Dlg.NONE }) { vm.setIncome(it); dlg = Dlg.NONE }
        Dlg.BUDGET -> MoneyDialog("Monthly spending budget", s.monthlyBudgetPaise,
            if (s.monthlyIncomePaise > 0) "50/30/20 suggests about ${Money.format(s.monthlyIncomePaise * 80 / 100)} for needs + wants." else null,
            { dlg = Dlg.NONE }) { vm.setBudget(it); dlg = Dlg.NONE }
        Dlg.AGE -> TextDialog("Your age", if (s.age > 0) "${s.age}" else "", "Age", { dlg = Dlg.NONE }) { vm.setAge(it.filter(Char::isDigit).toIntOrNull()?.coerceIn(0, 100) ?: 0); dlg = Dlg.NONE }
        Dlg.RETURN -> TextDialog("Expected yearly return %", "${s.expectedReturnPct}", "Percent (e.g. 12)", { dlg = Dlg.NONE }) { vm.setExpectedReturn(it.filter(Char::isDigit).toIntOrNull() ?: 12); dlg = Dlg.NONE }
        Dlg.SMALL -> TextDialog("Small-payment threshold (₹)", "${s.smallTicketRupees}", "Rupees", { dlg = Dlg.NONE }) { vm.setSmallTicket(it.filter(Char::isDigit).toIntOrNull() ?: 200); dlg = Dlg.NONE }
        Dlg.TIME -> {
            val ts = rememberTimePickerState(s.reminderHour, s.reminderMinute, is24Hour = false)
            AlertDialog(onDismissRequest = { dlg = Dlg.NONE }, text = { TimePicker(ts) },
                confirmButton = { TextButton(onClick = { vm.setReminder(true, ts.hour, ts.minute); dlg = Dlg.NONE }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { dlg = Dlg.NONE }) { Text("Cancel") } })
        }
        Dlg.BACKUP -> PasswordDialog(creating = true, onDismiss = { dlg = Dlg.NONE }) { p.exportBackup(it); dlg = Dlg.NONE }
        Dlg.RESTORE -> PasswordDialog(creating = false, onDismiss = { dlg = Dlg.NONE }) { p.importBackup(it); dlg = Dlg.NONE }
        Dlg.WIPE -> {
            var typed by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dlg = Dlg.NONE },
                title = { Text("Delete everything?") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This permanently erases every entry, goal, budget and setting on this phone. There is no copy anywhere else unless you made a backup.")
                    OutlinedTextField(typed, { typed = it }, label = { Text("Type DELETE to confirm") }, singleLine = true)
                } },
                confirmButton = { TextButton(enabled = typed.trim() == "DELETE", onClick = {
                    if (p.canUseDeviceLock()) p.authenticate("Confirm delete") { vm.wipeEverything { } } else vm.wipeEverything { }
                    dlg = Dlg.NONE
                }) { Text("Delete", color = Coral) } },
                dismissButton = { TextButton(onClick = { dlg = Dlg.NONE }) { Text("Cancel") } },
            )
        }
        Dlg.SMS_INFO -> AlertDialog(
            onDismissRequest = { dlg = Dlg.NONE },
            title = { Text("Read bank SMS?") },
            text = { Text("Kharcha Check will read only messages from bank and card senders to pre-fill entries. It skips OTPs and personal messages, never stores message text, and never sends message content anywhere. You can turn this off any time.") },
            confirmButton = { TextButton(onClick = {
                dlg = Dlg.NONE
                p.requestSms { granted -> if (granted) { vm.setSmsCapture(true); vm.importSms(30) } else vm.say("SMS reading stays off. Share or paste still works.") }
            }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { dlg = Dlg.NONE }) { Text("Not now") } },
        )
        Dlg.AUTO_BACKUP -> PasswordDialog(creating = true, onDismiss = { dlg = Dlg.NONE }) { pw ->
            dlg = Dlg.NONE
            p.pickFolder { folder -> vm.enableAutoBackup(folder, pw) }
        }
        Dlg.LICENCES -> AlertDialog(
            onDismissRequest = { dlg = Dlg.NONE },
            title = { Text("Open-source licences") },
            text = {
                Text(
                    "Tesseract OCR and the English model (tessdata_fast), via Tesseract4Android: Apache License 2.0\n\n" +
                        "PdfBox-Android (port of Apache PDFBox): Apache License 2.0. Bouncy Castle: MIT licence\n\n" +
                        "Leptonica: BSD 2-Clause. libjpeg: IJG licence. libpng: libpng licence\n\n" +
                        "SQLCipher for Android (Community Edition): BSD-style licence, © Zetetic LLC\n\n" +
                        "AndroidX, Jetpack Compose, Kotlin and kotlinx: Apache License 2.0\n\n" +
                        "Anek and Mukta fonts: SIL Open Font License 1.1\n\n" +
                        "Apache 2.0 text: apache.org/licenses/LICENSE-2.0",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { dlg = Dlg.NONE }) { Text("Close") } },
        )
        Dlg.NONE -> Unit
    }
}

@Composable
fun PrivacyScreen(nav: NavHostController) {
    val p = LocalPlatform.current
    SubScreen("Privacy", onBack = { nav.popBackStack() }) {
        val points = listOf(
            "🌐" to ("Internet only where it helps" to "Used only to download AMFI's public mutual fund prices (the whole list, so nobody learns what you hold) and to save backups to a folder you choose. Your entries are never sent to any server."),
            "🔐" to ("Encrypted on your phone" to "Your entries live in an AES-256 encrypted database. The key is held by your phone's secure hardware (Android Keystore)."),
            "🙅" to ("No account, no tracking" to "No sign-up, no analytics, no ads, no third-party SDKs that phone home. If the app crashes, you choose whether to email the report."),
            "☁️" to ("Backups you control" to "Android's automatic cloud backup is off. Turn on Automatic backup to save encrypted copies to a folder you pick, such as Google Drive."),
            "✉️" to ("SMS stays optional" to "Reading bank SMS is off unless you turn it on (Smart edition). Message text is never stored; only amount, payee and date."),
            "🧹" to ("You can erase it all" to "Settings → Delete everything wipes your data instantly. Uninstalling the app also removes it."),
        )
        items(points) { (emoji, tt) ->
            KcCard {
                Row(verticalAlignment = Alignment.Top) {
                    Text(emoji, fontSize = 26.sp); Spacer(Modifier.width(12.dp))
                    Column { Text(tt.first, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                        Text(tt.second, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted) }
                }
            }
        }
        item { OutlinedButton(onClick = p.openAppInfo, modifier = Modifier.fillMaxWidth()) { Text("Verify in App info") } }
    }
}

@Composable
fun CategoriesScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by rememberSaveable { mutableStateOf<Bucket?>(null) }
    var hiding by remember { mutableStateOf<Category?>(null) }
    SubScreen("Categories", onBack = { nav.popBackStack() }) {
        item { Text("Every category belongs to a bucket. Leaks are the ones Kharcha Check helps you cut. Tap to edit; hidden categories keep their history.",
            style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted) }
        Bucket.entries.forEach { b ->
            val list = data.categories.filter { it.bucket == b && !it.archived }
            item(key = "h-$b") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Box(Modifier.width(10.dp).aspectRatio(1f).clip(CircleShape).background(b.color())); Spacer(Modifier.width(8.dp))
                    Text("${b.label} · ${list.size}", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text, modifier = Modifier.weight(1f))
                    Pill("+ Add", Kc.colors.chip, Kc.colors.text) { creating = b }
                }
            }
            items(list, key = { it.id }) { c ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Kc.colors.card).clickable { editing = c }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(c, 36.dp); Spacer(Modifier.width(12.dp))
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, color = Kc.colors.text, modifier = Modifier.weight(1f))
                    if (c.custom) Pill("Yours", Kc.colors.chip, Kc.colors.muted)
                }
            }
        }
        val hidden = data.categories.filter { it.archived }
        if (hidden.isNotEmpty()) item {
            KcCard(title = "Hidden") { hidden.forEach { c -> SettingRow("${c.emoji} ${c.name}", "Tap to show again") { vm.saveCategory(c.copy(archived = false)) } } }
        }
    }
    editing?.let { c ->
        CategoryEditorDialog(c, c.bucket, Bucket.entries.filter { it.type == c.bucket.type || (c.bucket.isSpend && it.isSpend) },
            onDismiss = { editing = null }, onHide = { hiding = c; editing = null }) { vm.saveCategory(it); editing = null }
    }
    creating?.let { b -> CategoryEditorDialog(null, b, listOf(b), onDismiss = { creating = null }) { vm.saveCategory(it); creating = null } }
    hiding?.let { c ->
        val others = data.activeCategories.filter { it.bucket.type == c.bucket.type && it.id != c.id }
        AlertDialog(
            onDismissRequest = { hiding = null },
            title = { Text("Hide ${c.name}?") },
            text = { Text("It disappears from pickers. Past entries keep it, so reports stay accurate.") },
            confirmButton = { TextButton(onClick = { vm.archiveCategory(c, null); hiding = null }) { Text("Hide") } },
            dismissButton = { TextButton(onClick = {
                others.firstOrNull { it.key == "uncategorised" }?.let { vm.archiveCategory(c, it.id) } ?: vm.archiveCategory(c, null); hiding = null
            }) { Text("Hide & move entries to Uncategorised") } },
        )
    }
}

/** Create or edit a category. Spend categories can move between Needs / Wants / Leaks: that's the user's call. */
@Composable
fun CategoryEditorDialog(existing: Category?, defaultBucket: Bucket, allowedBuckets: List<Bucket>, onDismiss: () -> Unit, onHide: (() -> Unit)? = null, onSave: (Category) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(existing?.emoji ?: "🏷️") }
    var bucket by rememberSaveable { mutableStateOf(existing?.bucket ?: defaultBucket) }
    val palette = listOf(0xFF5B6CFF, 0xFF2BB673, 0xFFFFB020, 0xFFFF6B4A, 0xFFAB47BC, 0xFF00ACC1, 0xFFEC407A, 0xFF8D6E63)
    var color by rememberSaveable { mutableStateOf(existing?.colorArgb ?: palette.first()) }
    val emojis = listOf("🏷️", "🍔", "🍕", "🥤", "🧃", "🍿", "🛒", "🚌", "⛽", "💊", "🎮", "🎬", "👗", "💄", "📚", "🧒", "🐶", "🏏", "🎵", "💼", "🏦", "🎁", "🛠️", "💡")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(32) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (allowedBuckets.size > 1) Segmented(allowedBuckets.map { it.label }, allowedBuckets.indexOf(bucket).coerceAtLeast(0)) { bucket = allowedBuckets[it] }
                Text(bucket.blurb, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                emojis.chunked(8).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { e -> Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(if (e == emoji) Haldi else Kc.colors.chip).clickable { emoji = e },
                            contentAlignment = Alignment.Center) { Text(e, fontSize = 16.sp) } }
                    }
                }
                OutlinedTextField(emoji, { v -> if (v.isNotBlank()) emoji = v.trim().take(4) }, label = { Text("Or type any emoji") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.forEach { c -> Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(c))
                        .then(if (c == color) Modifier.padding(3.dp).clip(CircleShape).background(Kc.colors.card).padding(3.dp).clip(CircleShape).background(Color(c)) else Modifier)
                        .clickable { color = c }) }
                }
                if (existing != null && onHide != null && existing.key != "uncategorised") TextButton(onClick = onHide) { Text("Hide this category", color = Coral) }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            onSave((existing ?: Category(0, "", "", "", 0, bucket)).copy(name = name.trim(), emoji = emoji, colorArgb = color, bucket = bucket))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BudgetsScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    var editing by remember { mutableStateOf<Category?>(null) }
    val spentNow = data.report.categories.associate { it.category.id to it.paise }
    SubScreen("Category budgets", onBack = { nav.popBackStack() }) {
        item { Text("Set limits only for categories you want to watch. Leaks are a great place to start.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted) }
        listOf(Bucket.LEAK, Bucket.WANT, Bucket.NEED).forEach { b ->
            item(key = "h-$b") { Text(b.label, style = MaterialTheme.typography.titleLarge, color = Kc.colors.text, modifier = Modifier.padding(top = 8.dp)) }
            items(data.activeCategories.filter { it.bucket == b }, key = { it.id }) { c ->
                val limit = data.budgets[c.id]
                val spent = spentNow[c.id] ?: 0L
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Kc.colors.card).clickable { editing = c }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(c, 34.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, color = Kc.colors.text)
                        if (limit != null) Text("${Money.format(spent)} of ${Money.format(limit)} this month", style = MaterialTheme.typography.bodySmall,
                            color = if (spent > limit) Coral else Kc.colors.muted)
                    }
                    Pill(limit?.let { Money.compact(it) } ?: "Set", if (limit != null) Haldi else Kc.colors.chip, if (limit != null) Ink else Kc.colors.muted)
                }
            }
        }
    }
    editing?.let { c -> MoneyDialog("${c.emoji} ${c.name} budget", data.budgets[c.id] ?: 0, "Leave empty to remove the limit.", { editing = null }) { vm.setCategoryBudget(c.id, it); editing = null } }
}

@Composable
fun RecurringScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val rules by vm.recurring.collectAsStateWithLifecycle()
    val data = d ?: return
    var stopping by remember { mutableStateOf<com.kharchacheck.app.domain.RecurringRule?>(null) }
    SubScreen("Repeating entries", onBack = { nav.popBackStack() }) {
        item {
            Text("Each one is added to your Review inbox on its day, so you can confirm, change the amount, or skip it. " +
                "To add one, tick “Repeat every month” when you save an entry.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        }
        if (rules.isEmpty()) item { com.kharchacheck.app.ui.components.EmptyState("🔁", "Nothing repeating yet", "Rent, EMI, SIP, salary and subscriptions are good candidates.") }
        items(rules, key = { it.id }) { r ->
            val c = data.catMap[r.categoryId]
            KcCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(c, 38.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.payee.ifBlank { c?.name ?: "Entry" }, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                        Text("${Money.format(r.amountPaise)} · every month on the ${ordinal(r.dayOfMonth)}" + if (!r.active) " · paused" else "",
                            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (r.active) "Pause" else "Resume", Kc.colors.chip, Kc.colors.text) { vm.setRecurringActive(r, !r.active) }
                    Pill("Stop", Kc.colors.warnBg, Coral) { stopping = r }
                }
            }
        }
    }
    stopping?.let { r ->
        AlertDialog(
            onDismissRequest = { stopping = null },
            title = { Text("Stop repeating?") },
            text = { Text("Entries already added stay. No new ones will be created.") },
            confirmButton = { TextButton(onClick = { vm.deleteRecurring(r); stopping = null }) { Text("Stop", color = Coral) } },
            dismissButton = { TextButton(onClick = { stopping = null }) { Text("Keep") } },
        )
    }
}
