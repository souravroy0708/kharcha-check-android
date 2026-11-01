package com.kharchacheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kharchacheck.app.AccountType
import com.kharchacheck.app.AppController
import com.kharchacheck.app.SmsParser

@Composable
fun SettingsTab(app: AppController, actions: PhoneActions) {
    var backupPassphrase by rememberSaveable { mutableStateOf("") }
    var smallLeakInput by rememberSaveable { mutableStateOf(app.prefs.smallLeakThresholdRupees.toString()) }
    var overspendInput by rememberSaveable { mutableStateOf(app.prefs.overspendMinDeltaRupees.toString()) }
    var newPinInput by rememberSaveable { mutableStateOf("") }
    var addingAccount by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var goldenTestResult by remember { mutableStateOf<String?>(null) }

    if (addingAccount) {
        AddAccountDialog(
            app = app,
            onDismiss = { addingAccount = false },
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete all data on this phone?") },
            text = { Text("This permanently deletes all transactions, accounts, rules, budgets, and reviews stored on this phone. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWipe = false
                        app.deleteAllData()
                    }
                ) {
                    Text("Delete Everything", color = Coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancel", color = Muted) }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(t("Settings"), style = MaterialTheme.typography.headlineLarge)

        // 1. Provable Privacy Verification Card
        AppCard(
            title = "🔒 Provable Privacy Status",
            subtitle = "Zero cloud, zero analytics, zero INTERNET permission",
        ) {
            Text(
                "Kharcha Check ships without android.permission.INTERNET in its manifest. You don't have to take our word for it — verify it directly in Android Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            GhostButton("Open Android App Permissions to Verify →") {
                actions.onOpenAppInfoPermissions()
            }
        }

        // 2. SMS Capture, Backfill & Golden Test Set Verifier
        AppCard(
            title = "📩 Bank SMS Capture & Parser",
            subtitle = if (app.smsGranted) "SMS permission is ON" else "SMS permission is OFF (Using Manual & Share-to-App)",
        ) {
            Text("One-time Inbox Backfill (reads only bank headers, stores only parsed fields):", style = MaterialTheme.typography.bodySmall, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(30, 90, 180).forEach { days ->
                    SmallPill("Backfill ${days}d", emerald = app.prefs.lastBackfillDays == days) {
                        if (app.smsGranted) app.runSmsBackfill(days) else actions.onRequestSmsPermissions(days)
                    }
                }
            }

            HorizontalDivider(color = PaperLine)
            Text("Parser Quality Bar (Golden Set of ${SmsParser.GOLDEN_SAMPLES.size} Bank SMS):", style = MaterialTheme.typography.labelMedium)
            goldenTestResult?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = EmeraldDark, fontWeight = FontWeight.Bold)
            }
            SmallPill("▶ Run On-Device Golden Set Test (Target ≥ 95%)", amber = true) {
                var passed = 0
                SmsParser.GOLDEN_SAMPLES.forEach { s ->
                    val out = SmsParser.parse(s.sender, s.body)
                    val ok = if (s.expectedMatched) {
                        out is SmsParser.ParseOutcome.Matched && out.parsed.amountPaise == s.expectedPaise && out.parsed.type == s.expectedType
                    } else {
                        out is SmsParser.ParseOutcome.Ignored
                    }
                    if (ok) passed++
                }
                val pct = (passed * 100) / SmsParser.GOLDEN_SAMPLES.size
                goldenTestResult = "✓ Passed $passed / ${SmsParser.GOLDEN_SAMPLES.size} ($pct% accuracy across HDFC, SBI, ICICI, Axis, Kotak, OTP & Transfer rules)"
            }

            if (app.unrecognisedSms.isNotEmpty()) {
                HorizontalDivider(color = PaperLine)
                Text("Unrecognised Bank SMS Templates (Digits Masked):", style = MaterialTheme.typography.labelMedium, color = Muted)
                app.unrecognisedSms.take(3).forEach { (sender, tmpl) ->
                    Text("$sender: $tmpl", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                SmallPill("Copy Masked Templates") {
                    val joined = app.unrecognisedSms.joinToString("
") { "${it.first}: ${it.second}" }
                    actions.onCopyText("Masked SMS Templates", joined)
                }
            }
        }

        // 3. Passphrase-Encrypted Backup & Import (Storage Access Framework — Spec Page 9 & 10)
        AppCard(
            title = "🔐 Passphrase-Encrypted Backup (.kcbak)",
            subtitle = "AES-256-GCM + PBKDF2-HMAC-SHA256 to any folder you choose",
        ) {
            AppField(
                label = "Backup Passphrase (Required for Export / Restore)",
                value = backupPassphrase,
                onChange = { backupPassphrase = it.take(64) },
                placeholder = "Enter a memorable passphrase",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BigButton(
                    text = "Export Encrypted",
                    emerald = true,
                    enabled = backupPassphrase.length >= 4,
                    modifier = Modifier.weight(1f),
                    onClick = { actions.onExportEncryptedBackup(backupPassphrase) },
                )
                GhostButton(
                    text = "Import Backup",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (backupPassphrase.length < 4) {
                            app.message = "Enter your backup passphrase first."
                        } else {
                            actions.onImportEncryptedBackup(backupPassphrase)
                        }
                    },
                )
            }
        }

        // 4. Accounts & Learned Merchant Rules
        AppCard(
            title = "🏦 Accounts (${app.accounts.size})",
            trailing = { SmallPill("+ Add Account") { addingAccount = true } }
        ) {
            app.accounts.forEach { a ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(a.displayTitle, style = MaterialTheme.typography.titleMedium)
                    Text("${a.type.labelEn} · ${a.senderHeaders.ifBlank { "Manual" }}", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }

        AppCard(
            title = "🧠 Learned & User Merchant Rules (${app.rules.size})",
            subtitle = "User rules always override bundled keywords",
        ) {
            if (app.rules.isEmpty()) {
                Text("No custom rules yet. When you fix a category in the Review Tray, Kharcha Check offers to save a rule here.", style = MaterialTheme.typography.bodySmall, color = Muted)
            } else {
                app.rules.forEach { r ->
                    val cat = app.categoryMap[r.categoryId]
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("'${r.pattern}' → ${cat?.emoji ?: ""} ${cat?.nameEn ?: ""}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Remove",
                            style = MaterialTheme.typography.labelMedium,
                            color = Coral,
                            modifier = Modifier.clickable {
                                app.db.deleteRule(r.id)
                                app.reload()
                            },
                        )
                    }
                }
            }
        }

        // 5. Thresholds, Appearance & App Lock
        AppCard(title = "⚙️ Thresholds, Language & App Lock") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppField(
                    label = "Small Leaks (< ₹)",
                    value = smallLeakInput,
                    onChange = { v ->
                        smallLeakInput = v.filter { it.isDigit() }.take(4)
                        smallLeakInput.toIntOrNull()?.let { app.prefs.smallLeakThresholdRupees = it; app.reload() }
                    },
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                AppField(
                    label = "Overspend Delta (₹)",
                    value = overspendInput,
                    onChange = { v ->
                        overspendInput = v.filter { it.isDigit() }.take(5)
                        overspendInput.toIntOrNull()?.let { app.prefs.overspendMinDeltaRupees = it; app.reload() }
                    },
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }

            Text("Language (भाषा):", style = MaterialTheme.typography.labelMedium, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en" to "English", "hi" to "हिन्दी (Hindi)").forEach { (code, label) ->
                    SmallPill(label, emerald = app.language == code) {
                        app.prefs.language = code
                        app.language = code
                        Lang.current = code
                    }
                }
            }

            Text("Theme:", style = MaterialTheme.typography.labelMedium, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                    SmallPill(label, emerald = app.theme == mode) {
                        app.prefs.theme = mode
                        app.theme = mode
                    }
                }
            }

            HorizontalDivider(color = PaperLine)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hide in Recent Apps & Block Screenshots (FLAG_SECURE)", style = MaterialTheme.typography.titleMedium)
                    Text("Protects your screen in the Android app switcher", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Switch(
                    checked = app.hideInRecents,
                    colors = SwitchDefaults.colors(checkedTrackColor = Emerald, checkedThumbColor = Navy),
                    onCheckedChange = { on ->
                        app.prefs.hideInRecents = on
                        app.hideInRecents = on
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppField(
                    label = if (app.prefs.appLock) "Change 4-Digit PIN" else "Set 4-Digit App Lock PIN",
                    value = newPinInput,
                    onChange = { newPinInput = it.filter { c -> c.isDigit() }.take(4) },
                    keyboard = KeyboardType.NumberPassword,
                    modifier = Modifier.weight(1f),
                )
                if (newPinInput.length == 4) {
                    SmallPill("Save PIN", emerald = true) {
                        app.prefs.pinHash = app.prefs.hashPin(newPinInput)
                        app.prefs.appLock = true
                        newPinInput = ""
                        app.message = "App lock PIN updated ✓"
                    }
                }
            }
            if (app.prefs.appLock) {
                SmallPill("Turn Off PIN Lock", coral = true) {
                    app.prefs.appLock = false
                    app.message = "App lock disabled."
                }
            }
        }

        // 6. Danger Zone
        GhostButton(
            text = "Delete All Data From This Phone",
            danger = true,
            onClick = { confirmWipe = true },
        )

        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun AddAccountDialog(app: AppController, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.BANK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Bank Account / Card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(label = "Account / Card Name", value = name, onChange = { name = it.take(30) }, placeholder = "Axis Bank / OneCard")
                AppField(label = "Last 4 Digits", value = last4, onChange = { last4 = it.filter { c -> c.isDigit() }.take(4) }, keyboard = KeyboardType.Number, placeholder = "3319")
                AppField(label = "SMS Sender Header Token", value = headers, onChange = { headers = it.take(30) }, placeholder = "AXISBK")
            }
        },
        confirmButton = {
            BigButton("Add Account", emerald = true, enabled = name.isNotBlank()) {
                app.db.saveAccount(name, type, last4, headers)
                app.reload()
                onDismiss()
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) }
        },
    )
}
