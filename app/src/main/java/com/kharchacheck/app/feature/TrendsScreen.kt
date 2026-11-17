package com.kharchacheck.app.feature

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.Analytics
import com.kharchacheck.app.domain.AnalyticsEngine
import com.kharchacheck.app.domain.InvestMath
import com.kharchacheck.app.ui.AppData
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.BucketStackChart
import com.kharchacheck.app.ui.components.CategoryIcon
import com.kharchacheck.app.ui.components.ChartLegend
import com.kharchacheck.app.ui.components.Donut
import com.kharchacheck.app.ui.components.EmptyState
import com.kharchacheck.app.ui.components.Hero
import com.kharchacheck.app.ui.components.HeroTile
import com.kharchacheck.app.ui.components.IncomeSpendChart
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.MonthBars
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.ProgressBar
import com.kharchacheck.app.ui.components.SavingsRateChart
import com.kharchacheck.app.ui.components.Segmented
import com.kharchacheck.app.ui.components.Slice
import com.kharchacheck.app.ui.components.SpendHeatmap
import com.kharchacheck.app.ui.components.StatBlock
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.components.TxnRow
import com.kharchacheck.app.ui.nav.CategoryInsightsRoute
import com.kharchacheck.app.ui.nav.EditRoute
import com.kharchacheck.app.ui.nav.WrappedRoute
import com.kharchacheck.app.ui.theme.Amber
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import com.kharchacheck.app.ui.theme.NeedBlue
import com.kharchacheck.app.ui.theme.Violet
import com.kharchacheck.app.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val PERIODS = listOf(3, 6, 12)

/** Computed off the main thread; recomputed only when entries or the period change. */
@Composable
private fun rememberAnalytics(d: AppData, months: Int): Analytics? {
    val a by produceState<Analytics?>(null, d.txns, d.catMap, months) {
        value = withContext(Dispatchers.Default) { AnalyticsEngine.build(d.txns, d.catMap, YearMonth.now(), months) }
    }
    return a
}

@Composable
fun TrendsView(vm: AppViewModel, nav: NavHostController, switcher: @Composable () -> Unit) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    var periodIdx by rememberSaveable { mutableIntStateOf(1) }
    val months = PERIODS[periodIdx]
    val a = rememberAnalytics(data, months)
    var selected by rememberSaveable(months) { mutableIntStateOf(months - 1) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Hero {
                switcher()
                Segmented(listOf("3 months", "6 months", "12 months"), periodIdx, onDark = true) { periodIdx = it }
                if (a != null && !a.isEmpty) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroTile("Avg spend / month", Money.compact(a.avgMonthlySpendPaise), a.spendTrendPct?.let { arrow(it) + " last month" }, Haldi, Modifier.weight(1f))
                        HeroTile("Avg income / month", Money.compact(a.avgMonthlyIncomePaise), null, Mint, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroTile("Per day", Money.format(a.avgDailySpendPaise), null, Coral, Modifier.weight(1f))
                        HeroTile("Invested", Money.compact(a.totalInvestPaise), "in $months months", Violet, Modifier.weight(1f))
                    }
                }
            }
        }
        if (a == null) { item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Haldi) } }; return@LazyColumn }
        if (a.isEmpty) { item { EmptyState("📈", "Trends need a little history", "Keep logging for a few weeks, or read a past statement from Statements, and your trends appear here.") }; return@LazyColumn }

        item {
            Pad {
                KcCard(onClick = { nav.navigate(WrappedRoute(YearMonth.now().year.toString())) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎁", fontSize = 28.sp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${YearMonth.now().year} Wrapped (so far)", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                            Text("Your year in money as a story, with your persona.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = Kc.colors.muted)
                    }
                }
            }
        }

        item {
            Pad {
                KcCard(title = "Income vs spending") {
                    IncomeSpendChart(a.months, selected.coerceIn(0, a.months.lastIndex)) { selected = it }
                    ChartLegend(listOf(Mint to "Income", Haldi to "Spent", Violet to "Invested"))
                    val m = a.months[selected.coerceIn(0, a.months.lastIndex)]
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBlock(m.month.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)) + " in", Money.compact(m.incomePaise), Modifier.weight(1f), Kc.colors.good)
                        StatBlock("Out", Money.compact(m.spendPaise), Modifier.weight(1f))
                        StatBlock("Left", Money.compact(m.netPaise), Modifier.weight(1f), if (m.netPaise >= 0) Kc.colors.good else Coral)
                    }
                }
            }
        }

        item {
            Pad {
                KcCard(title = "Savings rate") {
                    SavingsRateChart(a.months)
                    val best = a.bestMonth
                    Text(if (best != null) "Dashed line = 20% goal. Best month: ${best.month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))} at ${best.savingsRatePct}%."
                        else "Log income (or set it in Settings) to see your savings rate.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                }
            }
        }

        item {
            Pad {
                KcCard(title = "Where it goes") {
                    val top = a.categories.take(6)
                    val rest = a.categories.drop(6).sumOf { it.totalPaise }
                    val slices = top.map { Slice(Color(it.category.colorArgb), it.totalPaise.toFloat(), it.category.name) } +
                        listOfNotNull(if (rest > 0) Slice(Kc.colors.line, rest.toFloat(), "Others") else null)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Donut(slices, 150.dp, "Spending by category: " + top.joinToString { "${it.category.name} ${it.sharePct.toInt()}%" }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Spent", style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
                                Text(Money.compact(a.totalSpendPaise), style = MaterialTheme.typography.titleLarge, color = Kc.colors.text)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Bucket.NEED, Bucket.WANT, Bucket.LEAK).forEach { b ->
                                val v = a.buckets[b] ?: 0
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(b.color())); Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text(b.label, style = MaterialTheme.typography.labelMedium, color = Kc.colors.text)
                                        Text(Money.compact(v) + if (a.totalSpendPaise > 0) " · ${v * 100 / a.totalSpendPaise}%" else "", style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
                                    }
                                }
                            }
                        }
                    }
                    Text("Tap a category for its full story", style = MaterialTheme.typography.labelMedium, color = Kc.colors.muted)
                    a.categories.take(12).forEach { c ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { nav.navigate(CategoryInsightsRoute(c.category.id, months)) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CategoryIcon(c.category, 34.dp); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.category.name, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text, maxLines = 1)
                                Text("${"%.0f".format(c.sharePct)}% · ${Money.compact(c.monthlyAvgPaise)}/month · ${Money.compact(c.yearlyPaise)}/year",
                                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Money.format(c.totalPaise), style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                                c.trendPct?.takeIf { abs(it) >= 10 }?.let { t ->
                                    Text(arrow(t), style = MaterialTheme.typography.labelSmall, color = if (t > 0) Coral else Kc.colors.good)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Pad {
                KcCard(title = "Needs · Wants · Leaks") {
                    BucketStackChart(a.months)
                    ChartLegend(listOf(NeedBlue to "Needs", Amber to "Wants", Coral to "Leaks"))
                    val leakTrend = AnalyticsEngine.trend(a.months.map { it.leaksPaise })
                    if (leakTrend != null) Text(if (leakTrend <= 0) "Leaks are ${-leakTrend}% below your average. Keep going 💪" else "Leaks are $leakTrend% above your average this month.",
                        style = MaterialTheme.typography.bodyMedium, color = if (leakTrend <= 0) Kc.colors.good else Coral)
                }
            }
        }

        item {
            Pad {
                KcCard(title = "When you spend") {
                    SpendHeatmap(a.heat, a.peakHeat)
                    a.busiest?.let { (day, part) ->
                        Text("You spend the most on ${day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${part.label.lowercase()}s. That's where a small rule (no orders after 10 pm?) saves the most.",
                            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                }
            }
        }

        if (a.regulars.isNotEmpty()) item {
            Pad {
                val monthly = a.regulars.sumOf { it.typicalPaise }
                KcCard(title = "🔁 Regular payments", trailing = { Text("${Money.compact(monthly)}/mo", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text) }) {
                    Text("Found automatically: same payee, similar amount, most months. That's ${Money.compact(monthly * 12)} a year already committed. Cancel anything you don't use.",
                        style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    a.regulars.take(8).forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryIcon(data.catMap[r.categoryId], 32.dp); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.payee, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text, maxLines = 1)
                                Text("${r.monthsSeen} of last 4 months · ${Money.compact(r.yearlyPaise)}/year", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                            }
                            Text(Money.format(r.typicalPaise), style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                        }
                    }
                }
            }
        }

        if (a.outliers.isNotEmpty()) item {
            Pad {
                KcCard(title = "⚡ Unusual spends", padding = 12.dp) {
                    Text("Much bigger than your usual in that category. Planned, or worth a second look?", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    a.outliers.forEach { t -> TxnRow(t, data.catMap[t.categoryId]) { nav.navigate(EditRoute(id = t.id)) } }
                }
            }
        }

        if (a.payees.isNotEmpty()) item {
            Pad {
                KcCard(title = "Top payees") {
                    val max = a.payees.first().totalPaise.coerceAtLeast(1)
                    a.payees.forEach { p ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row { Text(p.name, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("${p.count}× · ${Money.format(p.totalPaise)}", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted) }
                            ProgressBar(p.totalPaise.toFloat() / max, data.catMap[p.categoryId]?.let { Color(it.colorArgb) } ?: Violet, height = 6.dp)
                        }
                    }
                }
            }
        }

        item {
            Pad {
                KcCard(title = "How you pay") {
                    val total = a.modeTotals.values.sum().coerceAtLeast(1)
                    a.modeTotals.entries.sortedByDescending { it.value }.forEach { (mode, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mode.label, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text, modifier = Modifier.width(64.dp))
                            ProgressBar(v.toFloat() / total, Violet, Modifier.weight(1f), height = 8.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("${v * 100 / total}%", style = MaterialTheme.typography.labelMedium, color = Kc.colors.muted)
                        }
                    }
                    Text("${a.txnCount} entries in this period.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                }
            }
        }
    }
}

private fun arrow(pct: Int) = (if (pct > 0) "▲ " else if (pct < 0) "▼ " else "") + "${abs(pct)}%"

@Composable
private fun Pad(content: @Composable () -> Unit) = Box(Modifier.padding(horizontal = 16.dp)) { content() }

/** Everything about one category: monthly bars vs its average, run-rate, payees, entries. */
@Composable
fun CategoryInsightsScreen(vm: AppViewModel, nav: NavHostController, route: CategoryInsightsRoute) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val cat = data.catMap[route.categoryId] ?: return
    var periodIdx by rememberSaveable { mutableIntStateOf(PERIODS.indexOf(route.months).coerceAtLeast(0)) }
    val months = PERIODS[periodIdx]
    val detail by produceState<AnalyticsEngine.CategoryDetail?>(null, data.txns, months, route.categoryId) {
        value = withContext(Dispatchers.Default) { AnalyticsEngine.category(data.txns, data.catMap, route.categoryId, YearMonth.now(), months) }
    }
    var showAll by rememberSaveable { mutableStateOf(false) }

    SubScreen("${cat.emoji} ${cat.name}", onBack = { nav.popBackStack() }) {
        item { Segmented(listOf("3 months", "6 months", "12 months"), periodIdx) { periodIdx = it } }
        val dt = detail
        if (dt == null) { item { CircularProgressIndicator(color = Haldi) }; return@SubScreen }
        val s = dt.share
        if (s == null || dt.txns.isEmpty()) { item { EmptyState(cat.emoji, "Nothing here yet", "No entries in ${cat.name} in the last $months months.") }; return@SubScreen }
        item {
            KcCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBlock("Total", Money.compact(s.totalPaise), Modifier.weight(1f))
                    StatBlock("Per month", Money.compact(s.monthlyAvgPaise), Modifier.weight(1f))
                    StatBlock("Per year", Money.compact(s.yearlyPaise), Modifier.weight(1f), if (cat.bucket == Bucket.LEAK) Coral else Kc.colors.text)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBlock("Entries", "${s.count}", Modifier.weight(1f))
                    StatBlock("Avg entry", Money.compact(dt.avgTicketPaise), Modifier.weight(1f))
                    StatBlock("Trend", s.trendPct?.let { arrow(it) } ?: "—", Modifier.weight(1f),
                        when { s.trendPct == null -> Kc.colors.text; (s.trendPct > 0) == cat.bucket.isSpend -> Coral; else -> Kc.colors.good })
                }
            }
        }
        item {
            KcCard(title = "Month by month") {
                MonthBars(dt.months, s.perMonth, Color(cat.colorArgb))
                Text("Dashed line = your monthly average.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                data.budgets[cat.id]?.let { b ->
                    val now = s.perMonth.lastOrNull() ?: 0
                    Text("Budget ${Money.format(b)} · this month ${Money.format(now)}" + if (now > b) " (over by ${Money.format(now - b)})" else "",
                        style = MaterialTheme.typography.bodyMedium, color = if (now > b) Coral else Kc.colors.text)
                }
            }
        }
        if (cat.bucket == Bucket.LEAK || cat.bucket == Bucket.WANT) item {
            val half = s.monthlyAvgPaise / 2
            val fv = InvestMath.sipFutureValue(half, data.settings.expectedReturnPct.toDouble(), 10)
            KcCard {
                Row(verticalAlignment = Alignment.Top) {
                    Text("🌱", fontSize = 26.sp); Spacer(Modifier.width(10.dp))
                    Text("Halve ${cat.name} and invest the other ${Money.format(half)} every month: about ${Money.compact(fv)} in 10 years at ${data.settings.expectedReturnPct}%.",
                        style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                }
            }
        }
        if (dt.payees.isNotEmpty()) item {
            KcCard(title = "Who gets it") {
                dt.payees.forEach { p ->
                    Row { Text(p.name, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${p.count}× · ${Money.format(p.totalPaise)}", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted) }
                }
            }
        }
        item {
            KcCard(title = "Entries", padding = 10.dp) {
                val list = if (showAll) dt.txns else dt.txns.take(10)
                list.forEach { t -> TxnRow(t, cat) { nav.navigate(EditRoute(id = t.id)) } }
                if (dt.txns.size > 10 && !showAll) Pill("Show all ${dt.txns.size}", Kc.colors.chip, Kc.colors.text, Modifier.padding(6.dp)) { showAll = true }
            }
        }
    }
}

