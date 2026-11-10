@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kharchacheck.app.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.export.ExportSpec
import com.kharchacheck.app.domain.export.Exporter
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.components.BigButton
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.Segmented
import com.kharchacheck.app.ui.components.StatBlock
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.components.SwitchRow
import com.kharchacheck.app.ui.nav.SettingsRoute
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Range(val label: String) { ALL("All time"), THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_YEAR("This year"), LAST_12("Last 12 months"), CUSTOM("Custom") }

/** Export entries as Excel (.xlsx, with summary sheets) or CSV: for taxes, a CA, another app, or before uninstalling. */
@Composable
fun ExportScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val p = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var range by rememberSaveable { mutableStateOf(Range.ALL) }
    var from by rememberSaveable { mutableStateOf(LocalDate.now().minusMonths(1).toEpochDay()) }
    var to by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var types by remember { mutableStateOf(TxType.entries.map { it.name }.toSet()) }
    var excel by rememberSaveable { mutableStateOf(true) }
    var summary by rememberSaveable { mutableStateOf(true) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val spec = ExportSpec(
        from = when (range) {
            Range.ALL -> null; Range.THIS_MONTH -> YearMonth.now().atDay(1); Range.LAST_MONTH -> YearMonth.now().minusMonths(1).atDay(1)
            Range.THIS_YEAR -> LocalDate.of(today.year, 1, 1); Range.LAST_12 -> today.minusMonths(12).plusDays(1); Range.CUSTOM -> LocalDate.ofEpochDay(from)
        },
        to = when (range) {
            Range.LAST_MONTH -> YearMonth.now().minusMonths(1).atEndOfMonth(); Range.CUSTOM -> LocalDate.ofEpochDay(to); else -> null
        },
        types = types.map { TxType.valueOf(it) }.toSet(),
        includeSummary = summary,
    )
    val selected = remember(data.txns, spec) { Exporter.select(data.txns, spec) }
    val out = selected.filter { it.type != TxType.INCOME }.sumOf { it.amountPaise }
    val inc = selected.filter { it.type == TxType.INCOME }.sumOf { it.amountPaise }
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun build(action: (ByteArray, String, String) -> Unit) = scope.launch {
        busy = true
        val (bytes, mime, ext) = withContext(Dispatchers.Default) {
            if (excel) Triple(Exporter.xlsx(data.txns, data.catMap, spec), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
            else Triple(Exporter.csv(data.txns, data.catMap, spec), "text/csv", "csv")
        }
        busy = false
        action(bytes, mime, Exporter.fileName(spec, ext))
    }

    SubScreen("Export entries", onBack = { nav.popBackStack() }) {
        item {
            KcCard(title = "Period") {
                Range.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { r -> Pill(r.label, if (r == range) Haldi else Kc.colors.chip, if (r == range) Ink else Kc.colors.text) { range = r } }
                    }
                }
                if (range == Range.CUSTOM) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = "from" }, modifier = Modifier.weight(1f)) { Text("From " + LocalDate.ofEpochDay(from).format(fmt)) }
                    OutlinedButton(onClick = { picking = "to" }, modifier = Modifier.weight(1f)) { Text("To " + LocalDate.ofEpochDay(to).format(fmt)) }
                }
            }
        }
        item {
            KcCard(title = "Include") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TxType.entries.forEach { t ->
                        val on = t.name in types
                        Pill((if (on) "✓ " else "") + t.label.replace("Save & Invest", "Invest"), if (on) Haldi else Kc.colors.chip, if (on) Ink else Kc.colors.muted) {
                            types = if (on && types.size > 1) types - t.name else types + t.name
                        }
                    }
                }
            }
        }
        item {
            KcCard(title = "Format") {
                Segmented(listOf("Excel (.xlsx)", "CSV"), if (excel) 0 else 1) { excel = it == 0 }
                Text(if (excel) "Opens in Excel, Google Sheets or WPS with real dates and numbers, so totals and filters just work."
                    else "Plain text that opens anywhere: Excel, Sheets, Tally import tools, other apps.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                if (excel) SwitchRow("Add summary sheets", "\"Monthly summary\" (income, spent, needs, wants, leaks, saved) and \"By category\".", summary) { summary = it }
                Text("Columns: date, time, type, category, bucket, payee, debit, credit, amount, payment mode, note, tags, worth, source and bank reference.",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
        }
        item {
            KcCard(title = "Preview") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBlock("Entries", "${selected.size}", Modifier.weight(1f))
                    StatBlock("Money out", Money.compact(out), Modifier.weight(1f))
                    StatBlock("Money in", Money.compact(inc), Modifier.weight(1f), Kc.colors.good)
                }
                if (selected.isNotEmpty()) Text("${selected.first().date.format(fmt)} to ${selected.last().date.format(fmt)}", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                BigButton(if (busy) "Preparing…" else "💾 Save to phone", enabled = selected.isNotEmpty() && !busy) { build { b, m, n -> p.saveCopy(b, m, n) } }
                OutlinedButton(onClick = { build { b, m, n -> p.shareFile(b, m, n) } }, enabled = selected.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("📤 Share (email, WhatsApp, Drive…)")
                }
            }
        }
        item {
            KcCard(title = "Switching phones or uninstalling?") {
                Text("An export is great for reading and sharing, but it can't restore goals, budgets, bills or settings. " +
                    "To move everything to a new phone, make a password backup (.kcbak) too, and restore it after installing.",
                    style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                OutlinedButton(onClick = { nav.navigate(SettingsRoute) }, modifier = Modifier.fillMaxWidth()) { Text("Go to backup in Settings") }
            }
        }
    }

    picking?.let { which ->
        val start = LocalDate.ofEpochDay(if (which == "from") from else to)
        val ds = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = { TextButton(onClick = {
                ds.selectedDateMillis?.let { ms ->
                    val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    if (which == "from") from = day else to = day
                    if (from > to) { val t = from; from = to; to = t }
                }
                picking = null
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
        ) { DatePicker(state = ds) }
    }
}
