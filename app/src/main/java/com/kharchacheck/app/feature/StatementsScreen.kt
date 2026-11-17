package com.kharchacheck.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Attachment
import com.kharchacheck.app.core.model.AttachmentKind
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.components.AttachmentThumb
import com.kharchacheck.app.ui.components.AttachmentViewer
import com.kharchacheck.app.ui.components.BigButton
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.nav.ImportRoute
import com.kharchacheck.app.ui.nav.StatementsRoute
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val dFmtShort = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Monthly statements kept next to your entries: bank, credit card and UPI app. Reference only, never parsed or sent. */
@Composable
fun StatementsScreen(vm: AppViewModel, nav: NavHostController, route: StatementsRoute) {
    val all by vm.attachments.collectAsStateWithLifecycle()
    val dd by vm.data.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<List<com.kharchacheck.app.core.model.Txn>?>(null) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val autoRead = settings?.autoRead != false
    val p = LocalPlatform.current
    var month by rememberSaveable { mutableStateOf(runCatching { YearMonth.parse(route.month) }.getOrDefault(YearMonth.now()).toString()) }
    val ym = YearMonth.parse(month)
    var adding by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    val statements = all.filter { it.kind.isStatement && it.month == ym }
    val receipts = all.count { it.kind == AttachmentKind.RECEIPT && it.txnId != null && YearMonth.from(java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault())) == ym }
    val otherMonths = all.filter { it.kind.isStatement && it.month != null && it.month != ym }.groupingBy { it.month!! }.eachCount().toSortedMap(compareByDescending { it })

    SubScreen("Statements", onBack = { nav.popBackStack() }) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", tint = Kc.colors.text) }
                Text(ym.format(monthFmt), style = MaterialTheme.typography.headlineSmall, color = Kc.colors.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (ym < YearMonth.now()) month = ym.plusMonths(1).toString() }, enabled = ym < YearMonth.now()) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", tint = Kc.colors.text)
                }
            }
        }
        item {
            KcCard {
                Text(if (autoRead) "Attach a bank, credit card or UPI statement and ✨ Kharcha Check reads it into entries for you to review. PDF, photo or CSV."
                    else "Keep each month's bank, credit card and UPI statements next to your entries, so you can check everything was logged.",
                    style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                if (autoRead) Text("Best results: the CSV download from netbanking. Password-protected PDFs work on Android 15 and newer.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                Text("🔒 Encrypted on this phone, never uploaded. Not included in .kcbak backups, so keep your originals too.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                BigButton("📎 Attach a statement") { adding = true }
            }
        }
        if (statements.isEmpty()) item {
            Text("Nothing attached for ${ym.format(monthFmt)} yet." + if (receipts > 0) " $receipts receipts are attached to entries this month." else "",
                style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
        }
        items(statements, key = { it.id }) { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Kc.colors.card).clickable { viewing = a }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachmentThumb(vm, a, 56.dp) { viewing = a }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label.ifBlank { a.kind.label }, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, maxLines = 1)
                    Text("${a.kind.emoji} ${a.kind.label} · ${a.sizeLabel}", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, maxLines = 1)
                    if (a.originalName.isNotBlank()) Text(a.originalName, style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted, maxLines = 1)
                }
                if (autoRead) Pill("✨ Read", Haldi, Ink) { nav.navigate(ImportRoute(a.id)) }
                else Text("›", style = MaterialTheme.typography.titleLarge, color = Kc.colors.muted)
            }
        }
        // Import history: entries from one statement import share their creation time.
        val batches = dd?.allTxns.orEmpty().filter { it.source == com.kharchacheck.app.core.model.Source.STATEMENT }
            .groupBy { it.createdAt }.entries.sortedByDescending { it.key }.take(12)
        if (batches.isNotEmpty()) item {
            KcCard(title = "Import history") {
                Text("Imported something wrong? Delete a whole import here (you can undo). Single entries: long-press them in Activity.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                batches.forEach { (at, list) ->
                    val when_ = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${list.size} entries · imported ${when_.format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH))}",
                                style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                            Text("${list.minOf { it.date }.format(dFmtShort)} to ${list.maxOf { it.date }.format(dFmtShort)}",
                                style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                        }
                        Pill("🗑️ Delete", Kc.colors.warnBg, com.kharchacheck.app.ui.theme.Coral) { deleting = list }
                    }
                }
            }
        }
        if (otherMonths.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text("Other months", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(otherMonths.entries.toList(), key = { it.key.toString() }) { (m, n) ->
                        Pill("${m.format(DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH))} · $n", Kc.colors.chip, Kc.colors.text) { month = m.toString() }
                    }
                }
            }
        }
    }

    if (adding) StatementDialog(ym, onDismiss = { adding = false }) { kind, label ->
        adding = false
        p.pickDocument { uri -> vm.addStatement(uri, kind, ym, label) { a -> if (autoRead) nav.navigate(ImportRoute(a.id)) } }
    }
    viewing?.let { a -> AttachmentViewer(vm, a, onDismiss = { viewing = null }, onDelete = { vm.removeAttachment(a); viewing = null }) }
    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${list.size} imported entries?") },
            text = { Text("Removes every entry this import added, including ones you edited since. You can undo right after.") },
            confirmButton = { TextButton(onClick = { vm.deleteImportBatch(list.map { it.id }); deleting = null }) { Text("Delete", color = com.kharchacheck.app.ui.theme.Coral) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun StatementDialog(month: YearMonth, onDismiss: () -> Unit, onPick: (AttachmentKind, String) -> Unit) {
    val kinds = listOf(AttachmentKind.BANK_STATEMENT, AttachmentKind.CARD_STATEMENT, AttachmentKind.UPI_STATEMENT, AttachmentKind.OTHER)
    var kind by rememberSaveable { mutableStateOf(AttachmentKind.BANK_STATEMENT) }
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Statement for ${month.format(monthFmt)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                kinds.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k -> Pill("${k.emoji} ${k.label}", if (k == kind) Haldi else Kc.colors.chip, if (k == kind) Ink else Kc.colors.text) { kind = k } }
                    }
                }
                OutlinedTextField(label, { label = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name (optional)") },
                    placeholder = { Text(when (kind) { AttachmentKind.CARD_STATEMENT -> "e.g. HDFC Millennia card"; AttachmentKind.UPI_STATEMENT -> "e.g. Google Pay"; else -> "e.g. SBI savings" }) })
                Text("PDF, photo or CSV, up to 25 MB. Tip: don't type full account or card numbers here.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(kind, label) }) { Text("Choose file") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
