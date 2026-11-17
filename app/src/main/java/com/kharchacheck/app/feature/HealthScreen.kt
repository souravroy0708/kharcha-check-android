package com.kharchacheck.app.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.GoalKind
import com.kharchacheck.app.domain.HealthInputs
import com.kharchacheck.app.domain.HealthScore
import com.kharchacheck.app.domain.HealthScorer
import com.kharchacheck.app.domain.ReportEngine
import com.kharchacheck.app.ui.AppData
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.ProgressBar
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.nav.BillsRoute
import com.kharchacheck.app.ui.nav.GoalsRoute
import com.kharchacheck.app.ui.nav.GuideRoute
import com.kharchacheck.app.ui.nav.SubscriptionsRoute
import com.kharchacheck.app.ui.nav.WishlistRoute
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import java.time.LocalDate
import java.time.YearMonth

private val HEALTH_INS = Regex("""health|medi ?claim|star health|care health|niva|bupa|ergo|lombard|family floater""", RegexOption.IGNORE_CASE)
private val TERM_INS = Regex("""term|click ?2 ?protect|iprotect|e-?shield|saral jeevan""", RegexOption.IGNORE_CASE)

/** Prepares the score inputs from the user's last three full months of entries plus settings. */
fun healthInputsFor(d: AppData): HealthInputs {
    val now = YearMonth.now()
    val months = (1..3).map { now.minusMonths(it.toLong()) }
    val inMonths = d.txns.filter { it.month in months.toSet() }
    fun bucket(t: com.kharchacheck.app.core.model.Txn) = d.catMap[t.categoryId]?.bucket
    val activeMonths = inMonths.map { it.month }.toSet().size.coerceAtLeast(1)
    val incomeAvg = inMonths.filter { it.type == TxType.INCOME }.sumOf { it.amountPaise } / activeMonths
    val spend = inMonths.filter { it.type == TxType.EXPENSE && bucket(it)?.isSpend == true }
    val last = ReportEngine.build(now.minusMonths(1), d.txns, d.catMap)
    val year = d.txns.filter { it.date.isAfter(LocalDate.now().minusMonths(13)) && d.catMap[it.categoryId]?.key == "insurance" }
    val emergencyGoal = d.goals.filter { g -> g.name.contains("emergency", true) || d.settings.goalPlans[g.id]?.kind == GoalKind.EMERGENCY }.maxOfOrNull { it.savedPaise } ?: 0
    return HealthInputs(
        avgIncomePaise = incomeAvg.takeIf { it > 0 } ?: d.settings.monthlyIncomePaise.takeIf { it > 0 },
        avgSpendPaise = spend.sumOf { it.amountPaise } / activeMonths,
        avgNeedsPaise = spend.filter { bucket(it) == Bucket.NEED }.sumOf { it.amountPaise } / activeMonths,
        leaksSharePct = if (last.spendPaise > 0) (last.leaksPaise * 100 / last.spendPaise).toInt() else null,
        emiPaise = d.txns.filter { it.month == now.minusMonths(1) && d.catMap[it.categoryId]?.key == "emi" }.sumOf { it.amountPaise },
        costlyDebt = d.txns.any { it.date.isAfter(LocalDate.now().minusDays(90)) && d.catMap[it.categoryId]?.key in setOf("cc_interest", "fees_penalties") },
        emergencySavedPaise = maxOf(d.settings.emergencySavedPaise, emergencyGoal),
        healthInsured = "health" in d.settings.planDone || year.any { HEALTH_INS.containsMatchIn("${it.payee} ${it.note}") },
        termInsured = "term" in d.settings.planDone || year.any { TERM_INS.containsMatchIn("${it.payee} ${it.note}") },
        dependents = d.settings.dependents,
        investMonths = months.count { m -> d.txns.any { it.month == m && it.type == TxType.INVEST } },
    )
}

fun healthRoute(nav: NavHostController, key: String) = when (key) {
    "goals" -> nav.navigate(GoalsRoute); "bills" -> nav.navigate(BillsRoute); "wishlist" -> nav.navigate(WishlistRoute)
    "subs" -> nav.navigate(SubscriptionsRoute); else -> nav.navigate(GuideRoute())
}

fun scoreColor(score: Int?) = when { score == null -> null; score >= 80 -> Mint; score >= 60 -> Haldi; score >= 40 -> Coral.copy(alpha = 0.8f); else -> Coral }

@Composable
fun HealthScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val hs: HealthScore = remember(data) { HealthScorer.score(healthInputsFor(data)) }
    SubScreen("💯 Money health", onBack = { nav.popBackStack() }) {
        item {
            KcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(hs.score?.toString() ?: "–", fontSize = 64.sp, color = scoreColor(hs.score) ?: Kc.colors.muted, style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(hs.grade, style = MaterialTheme.typography.headlineSmall, color = Kc.colors.text)
                        Text("out of 100, from six yardsticks used widely in personal finance", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                }
                ProgressBar((hs.score ?: 0) / 100f, scoreColor(hs.score) ?: Haldi, height = 10.dp)
                hs.nextStep?.let { n ->
                    Text("Your best next step", style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted)
                    Text("${n.pillar.emoji} ${n.action}", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                    Pill("Do it now ›", Haldi, Ink) { healthRoute(nav, n.route) }
                }
            }
        }
        hs.pillars.forEach { p ->
            item(key = p.pillar.name) {
                KcCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.pillar.emoji} ${p.pillar.label}", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, modifier = Modifier.weight(1f))
                        Text(p.score?.toString() ?: "?", style = MaterialTheme.typography.titleLarge, color = scoreColor(p.score) ?: Kc.colors.muted)
                    }
                    if (p.score != null) ProgressBar(p.score / 100f, scoreColor(p.score) ?: Haldi, height = 6.dp)
                    Text(p.detail, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.action, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text, modifier = Modifier.weight(1f))
                        if ((p.score ?: 0) < 80) Pill("Fix ›", Kc.colors.chip, Kc.colors.text) { healthRoute(nav, p.route) }
                    }
                }
            }
        }
        item {
            Text("Based on your last three full months in the app, your salary plan and goals. Missing data is left out rather than counted against you. Educational, not financial advice.",
                style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        }
    }
}
