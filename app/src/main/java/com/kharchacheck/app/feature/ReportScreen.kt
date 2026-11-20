package com.kharchacheck.app.feature

import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.Insight
import com.kharchacheck.app.domain.MonthReport
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.components.CategoryIcon
import com.kharchacheck.app.ui.components.EmptyState
import com.kharchacheck.app.ui.components.Hero
import com.kharchacheck.app.ui.components.HeroTile
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.CalendarHeat
import com.kharchacheck.app.ui.components.Segmented
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.ProgressBar
import com.kharchacheck.app.ui.components.SplitBar
import com.kharchacheck.app.ui.components.StatBlock
import com.kharchacheck.app.ui.nav.CategoryInsightsRoute
import com.kharchacheck.app.ui.nav.GrowRoute
import com.kharchacheck.app.ui.nav.WrappedRoute
import com.kharchacheck.app.ui.nav.StatementsRoute
import com.kharchacheck.app.ui.openTab
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.InkLine
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import com.kharchacheck.app.ui.theme.MutedOnInk
import com.kharchacheck.app.ui.theme.NeedBlue
import com.kharchacheck.app.ui.theme.Violet
import com.kharchacheck.app.ui.theme.color
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Report tab: "Month" (one month in depth) and "Trends" (3/6/12-month analytics). */
@Composable
fun ReportScreen(vm: AppViewModel, nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val switcher: @Composable () -> Unit = { Segmented(listOf("Month", "Trends"), tab, onDark = true) { tab = it } }
    if (tab == 0) MonthReportView(vm, nav, switcher) else TrendsView(vm, nav, switcher)
}

@Composable
private fun MonthReportView(vm: AppViewModel, nav: NavHostController, switcher: @Composable () -> Unit) {
    val rep by vm.selectedReport.collectAsStateWithLifecycle()
    val month by vm.reportMonth.collectAsStateWithLifecycle()
    val d by vm.data.collectAsStateWithLifecycle()
    val r = rep ?: return
    val platform = LocalPlatform.current
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val isNow = month == YearMonth.now()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Hero {
                // Report now opens from Home / Tools, so it gets a way back.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                    Text("Report", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
                switcher()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.setReportMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", tint = Color.White) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text(if (isNow) "Month so far" else "Monthly report", style = MaterialTheme.typography.bodySmall, color = MutedOnInk)
                    }
                    IconButton(onClick = { vm.setReportMonth(month.plusMonths(1)) }, enabled = !isNow) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", tint = if (isNow) InkLine else Color.White)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Savings rate", style = MaterialTheme.typography.bodySmall, color = MutedOnInk)
                    Text(if (r.incomePaise > 0) "${r.savingsRatePct}%" else "—", style = MaterialTheme.typography.displayLarge,
                        color = when { r.incomePaise == 0L -> Color.White; r.savingsRatePct >= 20 -> Haldi; r.savingsRatePct >= 0 -> Color.White; else -> Coral })
                    Text(if (r.incomePaise > 0) "Saved ${Money.format(r.savedPaise)} of ${Money.format(r.incomePaise)}" + (if (r.incomeEstimated) " (your monthly income from Settings)" else "")
                        else "Log income or set it in Settings to see your savings rate",
                        style = MaterialTheme.typography.bodyMedium, color = MutedOnInk)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroTile("Spent", Money.compact(r.spendPaise), r.spendDeltaPct?.let { (if (it > 0) "▲ " else "▼ ") + "${kotlin.math.abs(it)}% vs last" }, Haldi, Modifier.weight(1f))
                    HeroTile("Invested", Money.compact(r.investPaise), null, Violet, Modifier.weight(1f))
                    HeroTile("Leaks", Money.compact(r.leaksPaise), null, Coral, Modifier.weight(1f))
                }
            }
        }

        if (r.isEmpty) {
            item { EmptyState("📊", "No entries for this month", "Your report builds itself as you log. Nothing is ever sent anywhere.") }
            return@LazyColumn
        }

        item {
            Pad {
                KcCard(onClick = { nav.navigate(WrappedRoute(month.toString())) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎁", fontSize = 28.sp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} Wrapped", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                            Text("Your month as a story, with your money persona. Shareable.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = Kc.colors.muted)
                    }
                }
            }
        }

        if (r.insights.isNotEmpty()) item {
            Pad { KcCard(title = "Takeaways") { r.insights.forEach { InsightRow(it) } } }
        }

        item {
            Pad {
                KcCard(title = "50/30/20 check", trailing = { if (r.split.followsRule) Pill("On track ✓", Kc.colors.goodBg, Kc.colors.good) }) {
                    SplitBar(r.split)
                    Text(if (r.split.basedOnIncome) "Share of your income. Ideal: ≤50% needs, ≤30% wants (leaks count as wants), ≥20% saved."
                        else "No income logged, so this shows your outflow split instead.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                }
            }
        }

        item {
            Pad {
                KcCard(title = "Where it went") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBlock("Needs", Money.compact(r.needsPaise), Modifier.weight(1f), NeedBlue)
                        StatBlock("Wants", Money.compact(r.wantsPaise), Modifier.weight(1f), com.kharchacheck.app.ui.theme.Amber)
                        StatBlock("Leaks", Money.compact(r.leaksPaise), Modifier.weight(1f), Coral)
                    }
                    val max = r.categories.maxOfOrNull { it.paise }?.coerceAtLeast(1) ?: 1
                    r.categories.take(10).forEach { line ->
                        Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { nav.navigate(CategoryInsightsRoute(line.category.id, 6)) }.padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryIcon(line.category, 32.dp); Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(line.category.name, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text, maxLines = 1)
                                    val sub = buildList {
                                        add("${line.sharePct}%"); add("${line.count}×")
                                        line.deltaPct?.let { add((if (it >= 0) "▲" else "▼") + "${kotlin.math.abs(it)}%") }
                                        line.budgetPaise?.let { add("budget ${Money.compact(it)}") }
                                    }.joinToString(" · ")
                                    Text(sub, style = MaterialTheme.typography.bodySmall, color = if (line.overBudget) Coral else Kc.colors.muted)
                                }
                                Text(Money.format(line.paise), style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                            }
                            ProgressBar(line.paise.toFloat() / max, line.category.bucket.color(), height = 6.dp)
                        }
                    }
                }
            }
        }

        if (r.leaksPaise > 0) item {
            Pad {
                KcCard(title = "🕳️ What you could cut") {
                    Text("Leaks this month: ${Money.format(r.leaksPaise)}" + (if (r.prevLeaksPaise > 0) " (last month ${Money.format(r.prevLeaksPaise)})" else "") +
                        ". Moved into a monthly SIP instead, that's about ${Money.compact(r.leakTenYearValuePaise)} in 10 years at ${d?.settings?.expectedReturnPct ?: 12}%.",
                        style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                    if (r.regretPaise > 0) Text("😬 Marked as regret: ${Money.format(r.regretPaise)}", style = MaterialTheme.typography.bodyMedium, color = Coral)
                    if (r.smallTicketCount > 0) Text("☕ ${r.smallTicketCount} small payments under ₹${d?.settings?.smallTicketRupees ?: 200}: ${Money.format(r.smallTicketPaise)}",
                        style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                    OutlinedButton(onClick = { nav.navigate(GrowRoute) }) { Text("Pick a challenge to cut one") }
                }
            }
        }

        item {
            Pad {
                KcCard(title = "Daily spending") {
                    var day by rememberSaveable(month) { mutableStateOf<Int?>(null) }
                    CalendarHeat(month, r.dailySpend, day) { day = it }
                    day?.let { i -> Text("${month.atDay(i + 1).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))}: ${Money.format(r.dailySpend[i])} spent",
                        style = MaterialTheme.typography.titleSmall, color = Kc.colors.text) }
                    val peak = r.dailySpend.withIndex().maxByOrNull { it.value }
                    if (peak != null && peak.value > 0) Text("Biggest day: ${month.atDay(peak.index + 1).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} · ${Money.format(peak.value)}" +
                        " · ${r.noSpendDays} no-spend days", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    WeekdayStrip(r)
                }
            }
        }

        item {
            Pad {
                KcCard(title = "How you paid") {
                    val total = r.modeSplit.values.sum().coerceAtLeast(1)
                    r.modeSplit.entries.sortedByDescending { it.value }.forEach { (mode, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mode.label, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text, modifier = Modifier.width(70.dp))
                            ProgressBar(v.toFloat() / total, Violet, Modifier.weight(1f), height = 8.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("${(v * 100 / total)}%", style = MaterialTheme.typography.labelMedium, color = Kc.colors.muted)
                        }
                    }
                    if (r.topPayees.isNotEmpty()) {
                        Text("Top payees", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, modifier = Modifier.padding(top = 6.dp))
                        r.topPayees.forEach { p ->
                            Row { Text(p.payee, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("${p.count}× · ${Money.format(p.paise)}", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted) }
                        }
                    }
                }
            }
        }

        item {
            val stmts = attachments.count { it.kind.isStatement && it.month == month }
            Pad {
                KcCard(title = "📎 Statements", onClick = { nav.navigate(StatementsRoute(month.toString())) }) {
                    Text(if (stmts == 0) "Attach this month's bank, credit card or UPI statement to cross-check your entries. Encrypted on this phone."
                        else "$stmts attached for this month. Tap to view or add more.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                }
            }
        }

        item {
            Pad {
                KcCard(title = "Take it with you") {
                    Text("Files are made on your phone and saved where you choose. Nothing is uploaded.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { platform.exportPdf("KharchaCheck-${month}.pdf") }, modifier = Modifier.weight(1f)) { Text("PDF report") }
                        OutlinedButton(onClick = { nav.navigate(com.kharchacheck.app.ui.nav.ExportRoute) }, modifier = Modifier.weight(1f)) { Text("Excel / CSV") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pad(content: @Composable () -> Unit) = Box(Modifier.padding(horizontal = 16.dp)) { content() }

@Composable
private fun InsightRow(i: Insight) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(
            when (i.tone) { Insight.Tone.GOOD -> Kc.colors.goodBg; Insight.Tone.WARN -> Kc.colors.warnBg; Insight.Tone.INFO -> Kc.colors.chip },
        ).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(i.emoji, fontSize = 20.sp); Spacer(Modifier.width(10.dp))
        Text(i.text, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
    }
}

@Composable
private fun WeekdayStrip(r: MonthReport) {
    val max = r.weekdaySpend.values.maxOrNull()?.coerceAtLeast(1) ?: return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { dow ->
            val v = r.weekdaySpend[dow] ?: 0L
            val a = (v.toFloat() / max).coerceIn(0.08f, 1f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Mint.copy(alpha = a)))
                Text(dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2), style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
            }
        }
    }
}
