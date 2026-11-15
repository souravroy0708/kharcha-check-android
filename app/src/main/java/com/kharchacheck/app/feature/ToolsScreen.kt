package com.kharchacheck.app.feature

import androidx.compose.foundation.layout.Arrangement
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

private data class Tool(val emoji: String, val title: String, val status: String, val go: () -> Unit)

/** Every money tool in one place, each with a live status line. */
@Composable
fun ToolsScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val bills by vm.bills.collectAsStateWithLifecycle()
    val dues by vm.dues.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val s = data.settings
    val tools = remember(data, bills, dues) {
        val score = HealthScorer.score(healthInputsFor(data)).score
        val subs = Subscriptions.detect(data.txns, data.catMap, today).filter { it.key !in s.subsCancelled }
        val goalSip = data.goals.filter { !it.archived && !it.done }.sumOf { g -> s.goalPlans[g.id]?.let { p -> GoalPlanner.plan(p, g.savedPaise).let { if (p.stepUp) it.sipStepUpStartPaise else it.sipFlatPaise } } ?: 0L }
        val ready = s.wishlist.count { Wishlist.ready(it, today) }
        val dueSoon = Bills.upcoming(bills, today, 7).size
        listOf(
            Tool("💯", "Money health", score?.let { "Score $it / 100" } ?: "See your score") { nav.navigate(HealthRoute) },
            Tool("📘", "Smart Money Guide", "Salary plan · research · tips") { nav.navigate(GuideRoute()) },
            Tool("🎯", "Goals & plans", if (goalSip > 0) "${Money.compact(goalSip)}/month planned" else "Home, education, retirement") { nav.navigate(GoalsRoute) },
            Tool("🛒", "Wishlist", when { ready > 0 -> "$ready ready to decide"; else -> "Kept ${Money.compact(Wishlist.savedBySkipping(s.wishlist))}" }) { nav.navigate(WishlistRoute) },
            Tool("🔁", "Subscriptions", if (subs.isEmpty()) "None found yet" else "${subs.size} · ${Money.compact(subs.sumOf { it.yearlyPaise })}/year") { nav.navigate(SubscriptionsRoute) },
            Tool("🧾", "Tax saver", "FY ${TaxSaver.fyStart(today)}-${(TaxSaver.fyStart(today) + 1) % 100}") { nav.navigate(TaxRoute) },
            Tool("🎯", "Budgets", "Per category, with alerts") { nav.navigate(BudgetsRoute) },
            Tool("📅", "Bills & due dates", if (dueSoon > 0) "$dueSoon due this week" else "${bills.size} tracked") { nav.navigate(BillsRoute) },
            Tool("💼", "Wealth & net worth", "Funds at daily prices") { nav.navigate(WealthRoute) },
            Tool("🤝", "Split & dues", Dues.toCollect(dues).takeIf { it > 0 }?.let { "${Money.compact(it)} to collect" } ?: "Split bills with friends") { nav.navigate(DuesRoute) },
            Tool("📄", "Import statement", "PDF, Excel, CSV") { nav.navigate(StatementsRoute(YearMonth.now().toString())) },
            Tool("⬇️", "Export", "Excel or CSV") { nav.navigate(ExportRoute) },
            Tool("🎁", "Wrapped", "${today.year} so far, as a story") { nav.navigate(WrappedRoute(today.year.toString())) },
            Tool("🙈", "Hidden entries", "Locked") { nav.navigate(HiddenRoute) },
        )
    }
    SubScreen("🧰 Tools", onBack = { nav.popBackStack() }) {
        tools.chunked(2).forEachIndexed { i, pair ->
            item(key = "row$i") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { t ->
                        KcCard(Modifier.weight(1f), onClick = t.go, padding = 14.dp) {
                            Text(t.emoji, fontSize = 26.sp)
                            Text(t.title, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, maxLines = 1)
                            Text(t.status, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, maxLines = 2)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
