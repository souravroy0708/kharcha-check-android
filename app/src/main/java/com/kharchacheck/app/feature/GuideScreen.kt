package com.kharchacheck.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.GoldenRules
import com.kharchacheck.app.domain.OnlineSpending
import com.kharchacheck.app.domain.PlanInput
import com.kharchacheck.app.domain.RiskComfort
import com.kharchacheck.app.domain.SalaryPlan
import com.kharchacheck.app.domain.SalaryPlanner
import com.kharchacheck.app.ui.AppData
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.BigButton
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.MoneyDialog
import com.kharchacheck.app.ui.components.ProgressBar
import com.kharchacheck.app.ui.components.Segmented
import com.kharchacheck.app.ui.components.StatBlock
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.components.SwitchRow
import com.kharchacheck.app.ui.components.TextDialog
import com.kharchacheck.app.ui.nav.GuideRoute
import com.kharchacheck.app.ui.theme.Amber
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import com.kharchacheck.app.ui.theme.NeedBlue
import java.time.YearMonth

/** Builds the user's plan from their settings and their own entries (EMIs, essentials, costly debt). */
fun salaryPlanFor(d: AppData): SalaryPlan? {
    val s = d.settings
    if (s.monthlyIncomePaise <= 0) return null
    val now = YearMonth.now()
    val last3 = (1..3).map { now.minusMonths(it.toLong()) }.toSet()
    val recent = d.txns.filter { it.month in last3 && it.type == TxType.EXPENSE }
    fun cat(id: Long) = d.catMap[id]
    val monthsWithNeeds = recent.filter { cat(it.categoryId)?.bucket == Bucket.NEED }.map { it.month }.toSet().size
    val avgNeeds = if (monthsWithNeeds == 0) 0 else recent.filter { cat(it.categoryId)?.bucket == Bucket.NEED }.sumOf { it.amountPaise } / monthsWithNeeds
    val emi = d.txns.filter { it.month == now.minusMonths(1) && cat(it.categoryId)?.key == "emi" }.sumOf { it.amountPaise }
    val costly = recent.any { cat(it.categoryId)?.key in setOf("cc_interest", "fees_penalties") }
    val emergencyGoal = d.goals.filter { it.name.contains("emergency", true) }.maxOfOrNull { it.savedPaise } ?: 0
    return SalaryPlanner.plan(
        PlanInput(
            monthlyIncomePaise = s.monthlyIncomePaise, age = if (s.age > 0) s.age else 30, dependents = s.dependents,
            risk = runCatching { RiskComfort.valueOf(s.risk) }.getOrDefault(RiskComfort.BALANCED),
            emiPaise = emi, avgNeedsPaise = avgNeeds, emergencySavedPaise = maxOf(s.emergencySavedPaise, emergencyGoal), hasCostlyDebt = costly,
        ),
        doneSteps = s.planDone, equityReturnPct = s.expectedReturnPct.toDouble(),
    )
}

private enum class GDlg { NONE, INCOME, AGE, EMERGENCY }

@Composable
fun GuideScreen(vm: AppViewModel, nav: NavHostController, route: GuideRoute) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    var tab by rememberSaveable { mutableIntStateOf(route.tab) }
    var dlg by rememberSaveable { mutableStateOf(GDlg.NONE) }
    val s = data.settings
    val plan = remember(data) { salaryPlanFor(data) }

    SubScreen("📘 Smart Money Guide", onBack = { nav.popBackStack() }) {
        item { Segmented(listOf("My salary plan", "Investing", "Online spend"), tab) { tab = it } }
        when (tab) {
            0 -> {
                item {
                    KcCard(title = "About you") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBlock("Monthly take-home", if (s.monthlyIncomePaise > 0) Money.compact(s.monthlyIncomePaise) else "Add", Modifier.weight(1f).clickable { dlg = GDlg.INCOME })
                            StatBlock("Age", if (s.age > 0) "${s.age}" else "Add", Modifier.weight(0.6f).clickable { dlg = GDlg.AGE })
                            StatBlock("Emergency saved", Money.compact(s.emergencySavedPaise), Modifier.weight(1f).clickable { dlg = GDlg.EMERGENCY })
                        }
                        SwitchRow("Someone depends on my income", "Parents, spouse or children. Adds term insurance to your plan.", s.dependents) {
                            vm.setPlanProfile(it, s.risk, s.emergencySavedPaise)
                        }
                        Text("How do you feel about market ups and downs?", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                        val risks = RiskComfort.entries
                        Segmented(risks.map { it.label }, risks.indexOfFirst { it.name == s.risk }.let { if (it < 0) 1 else it }) { vm.setPlanProfile(s.dependents, risks[it].name, s.emergencySavedPaise) }
                        Text(runCatching { RiskComfort.valueOf(s.risk) }.getOrDefault(RiskComfort.BALANCED).blurb, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                }
                if (plan == null) item { BigButton("Add your monthly salary to build your plan") { dlg = GDlg.INCOME } }
                else {
                    item { SplitCard(plan, data) }
                    item { LadderCard(plan, vm) }
                    item { InvestCard(plan) }
                }
                item { Text(GoldenRules.DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted) }
            }
            1 -> {
                item {
                    KcCard {
                        Text("The rules most money experts agree on. Tap any to read more.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                    }
                }
                item { Box(Modifier.padding(horizontal = 0.dp)) { RulesList(data) } }
            }
            else -> {
                val leak = OnlineSpending.biggestLeak(data.report.categories)
                if (leak != null) item {
                    KcCard(title = "Your biggest online leak") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(leak.category.emoji, fontSize = 30.sp); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(leak.category.name, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                                Text("${Money.format(leak.paise)} this month · ${leak.count} orders · about ${Money.compact(leak.paise * 12)} a year at this pace",
                                    style = MaterialTheme.typography.bodySmall, color = Coral)
                            }
                        }
                    }
                }
                val first = leak?.let { OnlineSpending.tipsFor(it.category.key) }.orEmpty()
                val ordered = first + OnlineSpending.tips.filter { it !in first }
                ordered.forEach { t ->
                    item(key = t.id) {
                        KcCard(padding = 14.dp) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(t.emoji, fontSize = 24.sp); Spacer(Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(t.title, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                                    Text(t.trick, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                                    Text("✅ ${t.fix}", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (dlg) {
        GDlg.INCOME -> MoneyDialog("Monthly take-home salary", s.monthlyIncomePaise, "After tax and PF, what lands in your account.", { dlg = GDlg.NONE }) { vm.setIncome(it); dlg = GDlg.NONE }
        GDlg.AGE -> TextDialog("Your age", if (s.age > 0) "${s.age}" else "", "Age", { dlg = GDlg.NONE }) { vm.setAge(it.filter(Char::isDigit).toIntOrNull()?.coerceIn(0, 100) ?: 0); dlg = GDlg.NONE }
        GDlg.EMERGENCY -> MoneyDialog("Emergency money already saved", s.emergencySavedPaise, "Cash you can reach in a day: savings account, sweep FD, liquid fund.", { dlg = GDlg.NONE }) {
            vm.setPlanProfile(s.dependents, s.risk, it); dlg = GDlg.NONE
        }
        GDlg.NONE -> Unit
    }
}

@Composable
private fun SplitCard(p: SalaryPlan, d: AppData) {
    val r = d.report
    KcCard(title = "Your monthly split") {
        Text("For ${Money.format(p.input.monthlyIncomePaise)} a month, aim for:", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        listOf(Triple("Needs (rent, food, bills, EMIs)", p.needsCapPaise to r.needsPaise, NeedBlue),
            Triple("Wants (fun, shopping, eating out)", p.wantsCapPaise to (r.wantsPaise + r.leaksPaise), Amber),
            Triple("Save & invest (${p.savePct}%)", p.savePaise to (r.incomePaise - r.spendPaise).coerceAtLeast(0), Mint)).forEach { (label, pair, color) ->
            val (target, actual) = pair
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text, modifier = Modifier.weight(1f))
                    Text(Money.format(target), style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                }
                ProgressBar(if (target > 0) actual.toFloat() / target else 0f, color, height = 8.dp)
                Text("This month so far: ${Money.format(actual)}", style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
            }
        }
        if (p.savePct < 20) Text("At your income, saving ${p.savePct}% is a strong start. Raise it by 5% every time your salary grows.",
            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        p.emiWarning?.let { Text("⚠️ $it", style = MaterialTheme.typography.bodyMedium, color = Coral) }
    }
}

@Composable
private fun LadderCard(p: SalaryPlan, vm: AppViewModel) {
    val done = p.steps.count { it.done }
    KcCard(title = "Your money ladder", trailing = { Text("$done/${p.steps.size}", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text) }) {
        ProgressBar(done.toFloat() / p.steps.size, Haldi, height = 8.dp)
        Text("Do these in order. Tick each one when done: +40 XP per step.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        p.steps.forEachIndexed { i, st ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (st.done) Kc.colors.goodBg else Kc.colors.chip)
                .clickable { vm.togglePlanStep(st.id) }.padding(end = 12.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
                Checkbox(st.done, { vm.togglePlanStep(st.id) })
                Column(Modifier.weight(1f).padding(top = 10.dp)) {
                    Text("${i + 1}. ${st.emoji} ${st.title}", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                    Text(st.detail, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun InvestCard(p: SalaryPlan) {
    KcCard(title = "Where your ${Money.compact(p.savePaise)}/month goes") {
        Text("A ${p.equityPct}% equity mix suits your age and comfort with risk. Until your emergency fund is full, send half of this there.",
            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        p.allocation.forEach { a ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row {
                    Text("${a.label} · ${a.sharePct}%", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text, modifier = Modifier.weight(1f))
                    Text(Money.format(a.monthlyPaise), style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                }
                Text(a.where, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
        }
        Text("What it can become", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, modifier = Modifier.padding(top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBlock("In 10 years", Money.compact(p.corpus10y), Modifier.weight(1f))
            StatBlock("In 20 years", Money.compact(p.corpus20y), Modifier.weight(1f))
            StatBlock("20y + 10% step-up", Money.compact(p.corpus20yStepUp), Modifier.weight(1.2f), Kc.colors.good)
        }
        Text("Assumes ${"%.0f".format(p.equityPct / 100.0 * 12 + (100 - p.equityPct) / 100.0 * 7)}% a year blended. Real returns vary and are never guaranteed.",
            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
    }
}
