package com.kharchacheck.app.feature

import androidx.compose.foundation.layout.Arrangement
import com.kharchacheck.app.ui.nav.GrowRoute
import com.kharchacheck.app.ui.nav.ReportRoute
import com.kharchacheck.app.ui.theme.Haldi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.Bills
import com.kharchacheck.app.domain.Dues
import com.kharchacheck.app.domain.GoalPlanner
import com.kharchacheck.app.domain.HealthScorer
import com.kharchacheck.app.domain.Subscriptions
import com.kharchacheck.app.domain.TaxSaver
import com.kharchacheck.app.domain.Wishlist
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.nav.BillsRoute
import com.kharchacheck.app.ui.nav.BudgetsRoute
import com.kharchacheck.app.ui.nav.DuesRoute
import com.kharchacheck.app.ui.nav.ExportRoute
import com.kharchacheck.app.ui.nav.GoalsRoute
import com.kharchacheck.app.ui.nav.GuideRoute
import com.kharchacheck.app.ui.nav.HealthRoute
import com.kharchacheck.app.ui.nav.HiddenRoute
import com.kharchacheck.app.ui.nav.StatementsRoute
import com.kharchacheck.app.ui.nav.SubscriptionsRoute
import com.kharchacheck.app.ui.nav.TaxRoute
import com.kharchacheck.app.ui.nav.WealthRoute
import com.kharchacheck.app.ui.nav.WishlistRoute
import com.kharchacheck.app.ui.nav.WrappedRoute
import com.kharchacheck.app.ui.theme.Kc
import java.time.LocalDate
import java.time.YearMonth

/** A money tool with a live status line. [id] is stable so Home can pick its highlights. */
data class MoneyTool(val id: String, val emoji: String, val title: String, val status: String, val go: () -> Unit)

/** Every tool, most useful first, with live status. Shared by the Tools tab and Home's highlight tiles. */
@Composable
fun rememberMoneyTools(vm: AppViewModel, nav: NavHostController): List<MoneyTool> {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return emptyList()
    val bills by vm.bills.collectAsStateWithLifecycle()
    val dues by vm.dues.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val diary by vm.tripDiary.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val s = data.settings
    fun m(text: String) = if (s.maskAmounts) "₹ ••••" else text
    return remember(data, bills, dues, accounts, trips, diary) {
        val net = com.kharchacheck.app.domain.Balances.let { b -> b.net(accounts, b.of(accounts, data.allTxns)) }
        val liveTrip = trips.firstOrNull { it.isOn(today) } ?: trips.filter { it.start.isAfter(today) }.minByOrNull { it.start }
        val score = HealthScorer.score(healthInputsFor(data)).score
        val subs = Subscriptions.detect(data.txns, data.catMap, today).filter { it.key !in s.subsCancelled }
        val goalSip = data.goals.filter { !it.archived && !it.done }.sumOf { g -> s.goalPlans[g.id]?.let { p -> GoalPlanner.plan(p, g.savedPaise).let { if (p.stepUp) it.sipStepUpStartPaise else it.sipFlatPaise } } ?: 0L }
        val ready = s.wishlist.count { Wishlist.ready(it, today) }
        val dueSoon = Bills.upcoming(bills, today, 7).size
        val g = data.game
        listOf(
            MoneyTool("report", "📊", "Monthly report", if (data.report.isEmpty) "Where your money went" else "${m(Money.compact(data.report.spendPaise))} spent this month") { nav.navigate(ReportRoute) },
            MoneyTool("budgets", "🎯", "Budgets", if (data.baseBudgets.isEmpty()) "Set limits, get alerts" else "${data.baseBudgets.size} limits · carry-over") { nav.navigate(BudgetsRoute) },
            MoneyTool("accounts", "🏦", "Accounts & balances", if (accounts.isEmpty()) "Bank, cash, cards, wallets" else "${m(Money.compact(net))} across ${accounts.count { !it.archived }}") { nav.navigate(com.kharchacheck.app.ui.nav.AccountsRoute) },
            MoneyTool("import", "📄", "Import statement", "Bank or card PDF, Excel, CSV") { nav.navigate(StatementsRoute(YearMonth.now().toString())) },
            MoneyTool("trips", "✈️", "Trips & events", liveTrip?.let { t -> if (t.isOn(today)) "${t.emoji} ${t.name} on now" else "${t.emoji} ${t.name} coming up" } ?: "Bookings, spends, diary") { nav.navigate(com.kharchacheck.app.ui.nav.TripsRoute) },
            MoneyTool("bills", "📅", "Bills & due dates", if (dueSoon > 0) "$dueSoon due this week" else if (bills.isEmpty()) "Card bills, rent, EMIs" else "${bills.size} tracked") { nav.navigate(BillsRoute) },
            MoneyTool("health", "💯", "Money health", score?.let { "Score $it / 100" } ?: "See your score") { nav.navigate(HealthRoute) },
            MoneyTool("grow", "🔥", "Grow", if (g.logStreak > 0) "Level ${g.level.number} · ${g.logStreak}-day streak" else "Level ${g.level.number} · challenges") { nav.navigate(GrowRoute) },
            MoneyTool("goals", "🏁", "Goals & plans", if (goalSip > 0) "${m(Money.compact(goalSip))}/month planned" else "Home, education, retirement") { nav.navigate(GoalsRoute) },
            MoneyTool("dues", "🤝", "Split & dues", Dues.toCollect(dues).takeIf { it > 0 }?.let { "${m(Money.compact(it))} to collect" } ?: "Split bills with friends") { nav.navigate(DuesRoute) },
            MoneyTool("subs", "🔁", "Subscriptions", if (subs.isEmpty()) "None found yet" else "${subs.size} · ${m(Money.compact(subs.sumOf { it.yearlyPaise }))}/year") { nav.navigate(SubscriptionsRoute) },
            MoneyTool("wealth", "💼", "Wealth & net worth", "Funds at daily prices") { nav.navigate(WealthRoute) },
            MoneyTool("wishlist", "🛒", "Wishlist", when { ready > 0 -> "$ready ready to decide"; else -> "Kept ${m(Money.compact(Wishlist.savedBySkipping(s.wishlist)))}" }) { nav.navigate(WishlistRoute) },
            MoneyTool("tax", "🧾", "Tax saver", "FY ${TaxSaver.fyStart(today)}-${(TaxSaver.fyStart(today) + 1) % 100}") { nav.navigate(TaxRoute) },
            MoneyTool("memories", "📸", "Memories", if (diary.isEmpty()) "Trip photos and moments" else "${diary.size} moments") { nav.navigate(com.kharchacheck.app.ui.nav.MemoriesRoute) },
            MoneyTool("guide", "📘", "Smart Money Guide", "Salary plan · research · tips") { nav.navigate(GuideRoute()) },
            MoneyTool("wrapped", "🎁", "Wrapped", "${today.year} so far, as a story") { nav.navigate(WrappedRoute(today.year.toString())) },
            MoneyTool("export", "⬇️", "Export", "Excel or CSV") { nav.navigate(ExportRoute) },
            MoneyTool("hidden", "🙈", "Hidden entries", "Locked") { nav.navigate(HiddenRoute) },
            MoneyTool("help", "❓", "Help & how-to", "Simple steps for everything") { nav.navigate(com.kharchacheck.app.ui.nav.HelpRoute) },
        )
    }
}

/** A tile: emoji on a soft square, title, live status. [highlight] gives the haldi treatment for the top pick. */
@Composable
fun ToolTile(t: MoneyTool, modifier: Modifier = Modifier, highlight: Boolean = false) {
    KcCard(modifier, onClick = t.go, padding = 14.dp) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (highlight) Haldi else Kc.colors.chip),
            contentAlignment = Alignment.Center,
        ) { Text(t.emoji, fontSize = 22.sp) }
        Text(t.title, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, maxLines = 1)
        Text(t.status, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, maxLines = 2, minLines = 2)
    }
}

/** Tools tab: every money tool in one place, each with a live status line. */
@Composable
fun ToolsScreen(vm: AppViewModel, nav: NavHostController) {
    val tools = rememberMoneyTools(vm, nav)
    SubScreen("Tools", onBack = null) {
        item {
            Text("Everything Kharcha Check can do, with what's happening in each. All free, all offline.",
                style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        }
        tools.chunked(2).forEachIndexed { i, pair ->
            item(key = "row$i") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { t -> ToolTile(t, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
