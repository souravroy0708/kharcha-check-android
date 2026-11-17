package com.kharchacheck.app.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.AmfiNav
import com.kharchacheck.app.domain.Holding
import com.kharchacheck.app.domain.HoldingKind
import com.kharchacheck.app.domain.HoldingValue
import com.kharchacheck.app.domain.NetItem
import com.kharchacheck.app.domain.NetSide
import com.kharchacheck.app.domain.Scheme
import com.kharchacheck.app.domain.Wealth
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.Segmented
import com.kharchacheck.app.ui.components.StatBlock
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

@Composable
fun WealthScreen(vm: AppViewModel, nav: NavHostController) {
    val holdings by vm.holdings.collectAsStateWithLifecycle()
    val items by vm.netItems.collectAsStateWithLifecycle()
    val history by vm.netHistory.collectAsStateWithLifecycle()
    val schemes by vm.schemes.collectAsStateWithLifecycle()
    val busy by vm.navBusy.collectAsStateWithLifecycle()
    var editHolding by remember { mutableStateOf<Holding?>(null) }
    var newHolding by rememberSaveable { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<NetItem?>(null) }
    var newItem by remember { mutableStateOf<NetSide?>(null) }

    LaunchedEffect(Unit) { vm.openWealth() }
    DisposableEffect(Unit) { onDispose { vm.closeWealth() } }

    val navs = remember(schemes) { schemes.associateBy { it.code } }
    val nw = remember(holdings, items, navs) { Wealth.netWorth(holdings, items, navs) }

    SubScreen("Wealth & net worth", onBack = { nav.popBackStack() }) {
        item {
            KcCard {
                Text("Net worth", style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted)
                Text(Money.format(nw.netPaise), style = MaterialTheme.typography.displaySmall, color = if (nw.netPaise >= 0) Kc.colors.text else Coral)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBlock("Investments", Money.compact(nw.investmentsPaise), Modifier.weight(1f))
                    StatBlock("Gain", (if (nw.gainPaise >= 0) "+" else "") + Money.compact(nw.gainPaise) + (nw.gainPct?.let { " · %.1f%%".format(it) } ?: ""),
                        Modifier.weight(1.3f), if (nw.gainPaise >= 0) Kc.colors.good else Coral)
                    StatBlock("Owe", Money.compact(nw.liabilitiesPaise), Modifier.weight(1f), if (nw.liabilitiesPaise > 0) Coral else Kc.colors.text)
                }
                if (history.size >= 2) NetWorthLine(history.values.toList())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val at = vm.navUpdatedAt
                    Text(if (at > 0) "Fund prices updated ${Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().format(shortDate)}" else "Fund prices not downloaded yet",
                        style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, modifier = Modifier.weight(1f))
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Haldi)
                    else Pill("↻ Update", Kc.colors.chip, Kc.colors.text) { vm.openWealth(force = true) }
                }
            }
        }

        item {
            KcCard(title = "📈 Investments", trailing = { TextButton(onClick = { newHolding = true }) { Text("+ Add") } }) {
                if (nw.holdings.isEmpty()) Text("Add your mutual funds (values update every day), and FDs, PPF/EPF/NPS, stocks or gold.",
                    style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                nw.holdings.forEach { hv -> HoldingRow(hv) { editHolding = hv.holding } }
                if (nw.holdings.size >= 2) Allocation(nw.holdings)
            }
        }
        listOf(NetSide.ASSET to "🏦 Bank, cash & other assets", NetSide.LIABILITY to "📉 Loans & dues").forEach { (side, title) ->
            item {
                val list = if (side == NetSide.ASSET) nw.assets else nw.liabilities
                KcCard(title = title, trailing = { TextButton(onClick = { newItem = side }) { Text("+ Add") } }) {
                    if (list.isEmpty()) Text(if (side == NetSide.ASSET) "Savings accounts, cash, property, PF balance…" else "Home, car or personal loans, credit card outstanding…",
                        style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                    list.forEach { i ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editItem = i }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(i.emoji, fontSize = 22.sp); Spacer(Modifier.width(10.dp))
                            Text(i.name, style = MaterialTheme.typography.bodyLarge, color = Kc.colors.text, modifier = Modifier.weight(1f))
                            Text(Money.format(i.valuePaise), style = MaterialTheme.typography.titleSmall, color = if (side == NetSide.LIABILITY) Coral else Kc.colors.text)
                        }
                    }
                }
            }
        }
        item {
            Text("Fund prices come from AMFI's public daily file of all ~30,000 schemes. The whole file is downloaded and searched on your phone, so no one learns which funds you hold. Values are indicative, not advice.",
                style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        }
    }

    if (newHolding || editHolding != null) HoldingDialog(vm, editHolding, schemes, busy, onDismiss = { newHolding = false; editHolding = null },
        onDelete = { h -> vm.deleteHolding(h); editHolding = null }) { h -> vm.saveHolding(h); newHolding = false; editHolding = null }
    if (newItem != null || editItem != null) NetItemDialog(editItem, newItem ?: editItem!!.side, onDismiss = { newItem = null; editItem = null },
        onDelete = { i -> vm.deleteNetItem(i); editItem = null }) { i -> vm.saveNetItem(i); newItem = null; editItem = null }
}

@Composable
private fun HoldingRow(hv: HoldingValue, onClick: () -> Unit) {
    val h = hv.holding
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(h.kind.emoji, fontSize = 22.sp); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(h.name, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text, maxLines = 2)
            Text(if (hv.live) "${h.units} units · NAV ${hv.navDate?.format(shortDate) ?: ""}" else "${h.kind.label} · value you entered",
                style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(hv.valuePaise), style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
            if (h.investedPaise > 0) Text((if (hv.gainPaise >= 0) "+" else "") + Money.compact(hv.gainPaise) + (hv.gainPct?.let { " (%.1f%%)".format(it) } ?: ""),
                style = MaterialTheme.typography.labelSmall, color = if (hv.gainPaise >= 0) Kc.colors.good else Coral)
        }
    }
}

@Composable
private fun Allocation(list: List<HoldingValue>) {
    val total = list.sumOf { it.valuePaise }.coerceAtLeast(1)
    Text("Allocation", style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted, modifier = Modifier.padding(top = 6.dp))
    list.groupBy { it.holding.kind }.mapValues { e -> e.value.sumOf { it.valuePaise } }.entries.sortedByDescending { it.value }.forEach { (k, v) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${k.emoji} ${k.label}", style = MaterialTheme.typography.bodySmall, color = Kc.colors.text, modifier = Modifier.width(130.dp))
            com.kharchacheck.app.ui.components.ProgressBar(v.toFloat() / total, Mint, Modifier.weight(1f), height = 8.dp)
            Spacer(Modifier.width(8.dp))
            Text("${v * 100 / total}%", style = MaterialTheme.typography.labelMedium, color = Kc.colors.muted)
        }
    }
}

@Composable
private fun NetWorthLine(values: List<Long>) {
    val min = values.min().toFloat(); val max = values.max().toFloat()
    val span = (max - min).coerceAtLeast(1f)
    val good = Kc.colors.good
    Canvas(Modifier.fillMaxWidth().height(70.dp).padding(vertical = 6.dp)) {
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val p = Offset(i * step, size.height - (v - min) / span * size.height)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, if (values.last() >= values.first()) good else Coral, style = Stroke(5f, cap = StrokeCap.Round))
    }
}

@Composable
private fun HoldingDialog(vm: AppViewModel, existing: Holding?, schemes: List<Scheme>, busy: Boolean, onDismiss: () -> Unit, onDelete: (Holding) -> Unit, onSave: (Holding) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: HoldingKind.MUTUAL_FUND) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var code by rememberSaveable { mutableStateOf(existing?.schemeCode) }
    var query by rememberSaveable { mutableStateOf("") }
    var units by rememberSaveable { mutableStateOf(existing?.units ?: "") }
    var invested by rememberSaveable { mutableStateOf(existing?.investedPaise?.takeIf { it > 0 }?.let { (it / 100).toString() } ?: "") }
    var value by rememberSaveable { mutableStateOf(existing?.manualValuePaise?.takeIf { it > 0 }?.let { (it / 100).toString() } ?: "") }
    val results = remember(query, schemes) { if (query.length >= 3) AmfiNav.search(schemes, query, 8) else emptyList() }
    val chosen = code?.let { c -> schemes.firstOrNull { it.code == c } }
    val isMf = kind == HoldingKind.MUTUAL_FUND
    val ok = name.isNotBlank() && (if (isMf) code != null && units.toBigDecimalOrNull() != null else (Money.parse(value) ?: Money.parse(invested)) != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add investment" else "Edit investment") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HoldingKind.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { k -> Pill("${k.emoji} ${k.label}", if (k == kind) Haldi else Kc.colors.chip, if (k == kind) Ink else Kc.colors.text) { kind = k } }
                    }
                }
                if (isMf) {
                    if (chosen != null || (code != null && name.isNotBlank())) {
                        Text(name, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                        chosen?.nav?.let { Text("NAV ₹$it on ${chosen?.date?.format(shortDate) ?: ""}", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted) }
                        Text("Change fund", style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted, modifier = Modifier.clickable { code = null; name = "" })
                    } else {
                        OutlinedTextField(query, { query = it }, label = { Text("Search fund, e.g. parag flexi direct") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (schemes.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                            if (busy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                            Text(if (busy) "Downloading the fund list…" else "Fund list not available. Check your connection and tap ↻ Update.",
                                style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                        }
                        results.forEach { s ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kc.colors.chip).clickable { code = s.code; name = s.name }.padding(10.dp)) {
                                Text(s.name, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text, maxLines = 2)
                                Text("${s.amc} · NAV ${s.nav?.let { "₹$it" } ?: "n/a"}", style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted, maxLines = 1)
                            }
                        }
                    }
                    OutlinedTextField(units, { v -> units = v.filter { it.isDigit() || it == '.' }.take(14) }, label = { Text("Units held (from your statement or app)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name, e.g. SBI FD 2027") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value, { v -> value = v.filter(Char::isDigit).take(11) }, label = { Text("Current value") }, prefix = { Text("₹ ") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(invested, { v -> invested = v.filter(Char::isDigit).take(11) }, label = { Text("Amount invested (for gain)") }, prefix = { Text("₹ ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (existing != null) TextButton(onClick = { onDelete(existing) }) { Text("Delete", color = Coral) }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = {
            onSave(Holding(id = existing?.id ?: 0, kind = kind, name = name.trim(), schemeCode = if (isMf) code else null, units = if (isMf) units.trim() else "",
                investedPaise = Money.parse(invested) ?: 0, manualValuePaise = if (isMf) 0 else Money.parse(value) ?: 0))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NetItemDialog(existing: NetItem?, side0: NetSide, onDismiss: () -> Unit, onDelete: (NetItem) -> Unit, onSave: (NetItem) -> Unit) {
    var side by rememberSaveable { mutableStateOf(existing?.side ?: side0) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(existing?.emoji ?: if (side0 == NetSide.ASSET) "🏦" else "🏠") }
    var value by rememberSaveable { mutableStateOf(existing?.valuePaise?.let { (it / 100).toString() } ?: "") }
    val paise = Money.parse(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add" else "Edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(listOf("I own (asset)", "I owe (loan)"), if (side == NetSide.ASSET) 0 else 1) { side = if (it == 0) NetSide.ASSET else NetSide.LIABILITY }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("🏦", "💵", "🏠", "🚗", "💳", "🎓", "🪙", "💼").forEach { e ->
                        Text(e, fontSize = 20.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(if (e == emoji) Haldi else Kc.colors.chip).clickable { emoji = e }.padding(6.dp))
                    }
                }
                OutlinedTextField(name, { name = it.take(30) }, label = { Text(if (side == NetSide.ASSET) "e.g. HDFC savings" else "e.g. Home loan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value, { v -> value = v.filter { it.isDigit() || it == '.' }.take(12) }, label = { Text(if (side == NetSide.ASSET) "Balance / value" else "Outstanding") },
                    prefix = { Text("₹ ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                if (existing != null) TextButton(onClick = { onDelete(existing) }) { Text("Delete", color = Coral) }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && paise != null, onClick = {
            onSave(NetItem(existing?.id ?: 0, side, name.trim(), emoji, paise ?: 0))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

