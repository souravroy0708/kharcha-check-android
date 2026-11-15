package com.kharchacheck.app.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.TaxBucket
import com.kharchacheck.app.domain.TaxRegime
import com.kharchacheck.app.domain.TaxSaver
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.MoneyDialog
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.ProgressBar
import com.kharchacheck.app.ui.components.Segmented
import com.kharchacheck.app.ui.components.StatBlock
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.components.SwitchRow
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Old-regime tax-saving tracker (80C, NPS 80CCD(1B), 80D). Educational; not tax advice. */
@Composable
fun TaxScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val s = data.settings
    val regime = runCatching { TaxRegime.valueOf(s.taxRegime) }.getOrDefault(TaxRegime.UNKNOWN)
    val today = LocalDate.now()
    val fy = TaxSaver.fyStart(today)
    val manual = TaxBucket.entries.associateWith { s.taxManual["$fy|${it.name}"] ?: 0L }
    val sum = remember(data.txns, s) { TaxSaver.summary(data.txns, data.catMap, today, s.taxAssign, manual, s.taxSlabPct, s.selfSenior, s.parentsSenior) }
    val candidates = remember(data.txns) { TaxSaver.candidates(data.txns, data.catMap, today) }
    var manualFor by remember { mutableStateOf<TaxBucket?>(null) }
    val dFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    SubScreen("🧾 Tax saver · ${sum.fyLabel}", onBack = { nav.popBackStack() }) {
        item {
            KcCard(title = "Your tax regime") {
                Segmented(TaxRegime.entries.map { it.label }, TaxRegime.entries.indexOf(regime)) { vm.setTaxProfile(TaxRegime.entries[it], s.taxSlabPct, s.selfSenior, s.parentsSenior) }
                Text(when (regime) {
                    TaxRegime.NEW -> "Under the new regime (the default now), these deductions mostly don't apply. Your employer's NPS contribution (80CCD(2)) still does. If your deductions are large, compare both regimes with your employer's calculator or a CA before the year's declaration."
                    TaxRegime.OLD -> "Old regime: every rupee below reduces your taxable income, up to each section's limit."
                    TaxRegime.UNKNOWN -> "Not sure? Check your payslip or ask HR; you choose the regime when declaring investments. The new regime is the default unless you opt out."
                }, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                Text("Your tax slab (highest rate you pay)", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                Segmented(listOf("5%", "20%", "30%"), listOf(5, 20, 30).indexOf(s.taxSlabPct).coerceAtLeast(0)) {
                    vm.setTaxProfile(regime, listOf(5, 20, 30)[it], s.selfSenior, s.parentsSenior)
                }
                SwitchRow("I'm 60 or older", "Higher health-insurance limit", s.selfSenior) { vm.setTaxProfile(regime, s.taxSlabPct, it, s.parentsSenior) }
                SwitchRow("My parents are 60 or older", "Higher limit for their health insurance", s.parentsSenior) { vm.setTaxProfile(regime, s.taxSlabPct, s.selfSenior, it) }
            }
        }
        item {
            KcCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBlock("Deductions", Money.compact(sum.deductionPaise), Modifier.weight(1f))
                    StatBlock("Tax saved", Money.compact(sum.savedTaxPaise), Modifier.weight(1f), Kc.colors.good)
                    StatBlock("Days to 31 Mar", "${sum.daysLeft}", Modifier.weight(1f), if (sum.daysLeft < 45) Coral else Kc.colors.text)
                }
                if (regime != TaxRegime.NEW && sum.possibleMoreTaxPaise > 0) Text("You can still save about ${Money.format(sum.possibleMoreTaxPaise)} in tax by using the room left below before 31 March.",
                    style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                Text("Estimates at your ${s.taxSlabPct}% slab + 4% cess. Limits can change in a Budget. Not tax advice.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
        }
        sum.buckets.forEach { b ->
            item(key = b.bucket.name) {
                KcCard(title = b.bucket.label) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${Money.compact(b.usedPaise)} of ${Money.compact(b.limitPaise)}", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, modifier = Modifier.weight(1f))
                        Text(if (b.leftPaise == 0L) "✅ Full" else "${Money.compact(b.leftPaise)} left", style = MaterialTheme.typography.labelLarge,
                            color = if (b.leftPaise == 0L) Kc.colors.good else Kc.colors.muted)
                    }
                    ProgressBar(if (b.limitPaise > 0) b.usedPaise.toFloat() / b.limitPaise else 0f, if (b.leftPaise == 0L) Mint else Haldi, height = 8.dp)
                    Text(b.bucket.covers, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill(if (b.manualPaise > 0) "Outside the app: ${Money.compact(b.manualPaise)} ✎" else "+ Paid outside the app", Kc.colors.chip, Kc.colors.text) { manualFor = b.bucket }
                        Text("${b.entries.size} entries", style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
                    }
                }
            }
        }
        if (candidates.isNotEmpty()) item {
            Text("This year's entries: tap where each one counts", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text, modifier = Modifier.padding(top = 6.dp))
        }
        items(candidates.take(80), key = { "c-" + it.id }) { t ->
            val cat = data.catMap[t.categoryId]
            val current = if (t.id in s.taxAssign) s.taxAssign[t.id] else TaxSaver.suggest(t, cat)
            KcCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cat?.emoji ?: "•", fontSize = 22.sp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.payee.ifBlank { cat?.name ?: "Entry" }, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text, maxLines = 1)
                        Text("${t.date.format(dFmt)} · ${cat?.name ?: ""}", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                    Text(Money.format(t.amountPaise), style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (TaxBucket.entries.map { it as TaxBucket? } + null).forEach { b ->
                        val on = b == current
                        Pill(b?.short ?: "Not for tax", if (on) Haldi else Kc.colors.chip, if (on) Ink else Kc.colors.muted) { vm.assignTax(t, b) }
                    }
                }
            }
        }
    }
    manualFor?.let { b ->
        MoneyDialog("${b.short}: paid outside the app", manual[b] ?: 0L, "This year's total not logged here, e.g. EPF deducted from salary (see payslip) or home-loan principal (see the bank's certificate).",
            { manualFor = null }) { vm.setTaxManual(fy, b, it); manualFor = null }
    }
}
