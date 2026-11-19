package com.kharchacheck.app.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.kharchacheck.app.container
import com.kharchacheck.app.data.StorageManager
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.theme.Kc
import kotlinx.coroutines.launch
import java.util.Locale

/** Settings → Storage: what Kharcha Check keeps on this phone, and one-tap ways to make it smaller. */
@Composable
fun StorageScreen(vm: AppViewModel, nav: NavHostController) {
    val storage = LocalContext.current.container.storage
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<StorageManager.Usage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmStatements by remember { mutableStateOf(false) }
    LaunchedEffect(reload) { usage = storage.usage() }

    fun run(work: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            val msg = runCatching { work() }.getOrElse { "Couldn't finish that. Please try again." }
            busy = false; reload++; vm.say(msg)
        }
    }

    SubScreen("Storage", onBack = { nav.popBackStack() }) {
        item {
            KcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📴", fontSize = 28.sp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Works without internet", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                        Text(if (vm.isOnline()) "You're online right now." else "You're offline right now. That's fine.",
                            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                }
                Text("Adding and editing entries, reports, budgets, goals, reminders, reading screenshots and statements, backups to this phone, and voice entry on phones with offline speech all work offline. " +
                    "Only mutual fund prices need the internet; the app keeps the last prices and updates them when you're back online.",
                    style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
            }
        }

        val u = usage
        if (u == null) {
            item { Text("Measuring…", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted) }
            return@SubScreen
        }
        item {
            KcCard {
                Text("${size(u.totalBytes)} on this phone", style = MaterialTheme.typography.headlineSmall, color = Kc.colors.text)
                Line("📒", "Your entries", "${u.entries} entries, encrypted", u.databaseBytes)
                Line("🧾", "Screenshots & statements", "${u.receipts} screenshots, ${u.statements} statement files, encrypted", u.filesBytes)
                if (u.pricesBytes > 0) Line("📈", "Fund prices (offline copy)", "For search and values without internet", u.pricesBytes)
                Line("🔤", "Text reader", "Reads screenshots on the phone. Fixed size.", u.readerBytes)
                if (u.tempBytes > 0) Line("🗑️", "Temporary files", "Safe to clear", u.tempBytes)
                Text("Entries are tiny, about 100 bytes each, so years of daily use stay under a few MB. Pictures and PDFs are what grow.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
        }

        item { Text("Make it smaller", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text) }
        item {
            Action("Shrink older screenshots", "Re-saves older photos and screenshots in the compact WebP format. Usually 3 to 5 times smaller. Entries don't change.", busy) {
                run { val saved = storage.shrinkImages(); if (saved > 0) "Freed ${size(saved)} ✓" else "Screenshots are already compact ✓" }
            }
        }
        item {
            Action("Tidy the database", "Folds in pending changes and removes free space left by deleted entries.", busy) {
                run { val saved = storage.compact(force = true); if (saved > 0) "Freed ${size(saved)} ✓" else "Database is already compact ✓" }
            }
        }
        if (u.tempBytes > 0) item {
            Action("Clear temporary files", "Copies made when you opened a file in another app, previews and leftovers.", busy) {
                run { val saved = storage.clearTemp(); "Freed ${size(saved)} ✓" }
            }
        }
        if (u.statements > 0) item {
            Action("Remove old statement files", "Deletes statement PDFs and sheets added more than 12 months ago. The entries read from them stay.", busy) {
                confirmStatements = true
            }
        }
        if (u.pricesBytes > 0) item {
            Action("Delete offline fund prices", "Your holdings stay. Prices download again next time you open Wealth with internet.", busy) {
                run { storage.clearFundPrices(); "Fund price list removed ✓" }
            }
        }
    }

    if (confirmStatements) AlertDialog(
        onDismissRequest = { confirmStatements = false },
        title = { Text("Remove old statement files?") },
        text = { Text("Statement files added more than 12 months ago will be deleted from this phone. Every entry read from them stays. Your backups still contain them.") },
        confirmButton = { TextButton(onClick = { confirmStatements = false; run { val n = storage.removeStatementFiles(12); if (n > 0) "Removed $n statement files ✓" else "No statement files older than 12 months" } }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmStatements = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Line(emoji: String, title: String, detail: String, bytes: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(emoji, fontSize = 22.sp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        }
        Text(size(bytes), style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
    }
}

@Composable
private fun Action(title: String, detail: String, busy: Boolean, onClick: () -> Unit) {
    KcCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        OutlinedButton(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Working…" else title) }
    }
}

private fun size(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(Locale.ENGLISH, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
