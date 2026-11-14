package com.kharchacheck.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Goal
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.GoalKind
import com.kharchacheck.app.domain.GoalMath
import com.kharchacheck.app.domain.GoalPlan
import com.kharchacheck.app.domain.GoalPlanner
import com.kharchacheck.app.domain.GoldenRules
import com.kharchacheck.app.ui.AppData
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.BigButton
import com.kharchacheck.app.ui.components.EmptyState
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.MoneyDialog
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.ProgressBar
import com.kharchacheck.app.ui.components.StatBlock
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.components.SwitchRow
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import java.time.LocalDate
import java.time.YearMonth

/** Average monthly spend per leak category over the last three full months. */
private fun leakAverages(d: AppData): List<Pair<com.kharchacheck.app.core.model.Category, Long>> {
    val now = YearMonth.now()
    val months = (1..3).map { now.minusMonths(it.toLong()) }.toSet()
    return d.txns.filter { it.type == TxType.EXPENSE && it.month in months && d.catMap[it.categoryId]?.bucket == Bucket.LEAK }
        .groupBy { it.categoryId }.mapNotNull { (id, l) -> d.catMap[id]?.let { it to l.sumOf { t -> t.amountPaise } / 3 } }
        .sortedByDescending { it.second }
}

private fun sipOf(plan: GoalPlan, m: GoalMath) = if (plan.stepUp) m.sipStepUpStartPaise else m.sipFlatPaise

@Composable
fun GoalsScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    var planning by remember { mutableStateOf<Pair<Goal?, GoalPlan?>?>(null) }
    var adding by remember { mutableStateOf<Goal?>(null) }
    val plans = data.settings.goalPlans
    val goals = data.goals.filter { !it.archived }
    val now = YearMonth.now()
    val leaks = remember(data.txns) { leakAverages(data) }
    val avgLeaks = leaks.sumOf { it.second }
    val rescued = GoalPlanner.rescued(data.report.leaksPaise, avgLeaks, LocalDate.now().dayOfMonth, now.lengthOfMonth())
    val capacity = salaryPlanFor(data)?.savePaise
    val totalSip = goals.sumOf { g -> plans[g.id]?.let { p -> sipOf(p, GoalPlanner.plan(p, g.savedPaise)) } ?: 0L }

    SubScreen("🎯 Goals & plans", onBack = { nav.popBackStack() }) {
        item {
            KcCard {
                Text("Your goals need", style = MaterialTheme.typography.labelLarge, color = Kc.colors.muted)
                Text("${Money.format(totalSip)} / month", style = MaterialTheme.typography.headlineMedium, color = Kc.colors.text)
                when {
                    capacity == null -> Text("Add your salary in the Smart Money Guide to see whether this fits your budget.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    totalSip <= capacity -> Text("✅ Fits inside the ${Money.format(capacity)} a month your salary plan saves. ${Money.format(capacity - totalSip)} left for new goals.",
                        style = MaterialTheme.typography.bodyMedium, color = Kc.colors.good)
                    else -> Text("⚠️ ${Money.format(totalSip - capacity)} a month more than your plan saves (${Money.format(capacity)}). Options: push a goal's date out, use step-up SIPs, or redirect a leak below.",
                        style = MaterialTheme.typography.bodyMedium, color = Coral)
                }
                BigButton("+ Plan a goal") { planning = null to null }
            }
        }
        if (rescued >= 100_00) item {
            KcCard(title = "💰 Money rescued this month") {
                Text("You've spent ${Money.format(rescued)} less on leaks than your usual by this point in the month. Move it to a goal before it leaks somewhere else?",
                    style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    goals.filter { !it.done }.take(3).forEach { g -> Pill("${g.emoji} ${g.name.take(14)}", Haldi, Ink) { vm.addToGoal(g, rescued); vm.say("${Money.format(rescued)} moved to ${g.name} ✓") } }
                }
            }
        }
        if (goals.isEmpty()) item {
            EmptyState("🎯", "No goals yet", "Plan a home, your child's education or wedding, retirement or a trip: see the monthly amount that gets you there.")
        }
        items(goals, key = { it.id }) { g -> GoalCard(g, plans[g.id], data, leaks, vm, onPlan = { planning = g to plans[g.id] }, onAdd = { adding = g }) }
        item { Text(GoldenRules.DISCLAIMER + " Inflation and return figures are long-run assumptions; real results vary.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted) }
    }

    planning?.let { (g, p) -> PlanGoalDialog(g, p, onDismiss = { planning = null }, onDelete = { id -> vm.deleteGoal(id); planning = null }) { goal, plan -> vm.savePlannedGoal(goal, plan); planning = null } }
    adding?.let { g ->
        val plan = plans[g.id]
        val suggest = plan?.let { sipOf(it, GoalPlanner.plan(it, g.savedPaise)) } ?: 0L
        MoneyDialog("Add money to ${g.name}", suggest, "Log what you invested or set aside for this goal (it's recorded as Save & Invest).", { adding = null }) { vm.addToGoal(g, it); adding = null }
    }
}

@Composable
private fun GoalCard(g: Goal, plan: GoalPlan?, d: AppData, leaks: List<Pair<com.kharchacheck.app.core.model.Category, Long>>, vm: AppViewModel, onPlan: () -> Unit, onAdd: () -> Unit) {
    val math = plan?.let { GoalPlanner.plan(it, g.savedPaise) }
    val sip = if (plan != null && math != null) sipOf(plan, math) else 0L
    val thisMonth = d.txns.filter { it.goalId == g.id && it.month == YearMonth.now() }.sumOf { it.amountPaise }
    KcCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.emoji, fontSize = 30.sp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                Text("${Money.compact(g.savedPaise)} of ${Money.compact(g.targetPaise)}" + (g.targetDate?.let { " · by ${it.year}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
            Text("${(g.progress * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text)
        }
        ProgressBar(g.progress, if (g.done) Mint else Haldi, height = 8.dp)
        if (g.done) { Text("🎉 Fully funded!", style = MaterialTheme.typography.titleSmall, color = Kc.colors.good); return@KcCard }
        if (plan == null || math == null) {
            Text("No plan yet. Plan it to see the monthly amount that reaches it on time, with inflation included.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pill("📋 Plan it", Haldi, Ink, onClick = onPlan); Pill("+ Add money", Kc.colors.chip, Kc.colors.text, onClick = onAdd) }
            return@KcCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBlock(if (plan.stepUp) "Monthly SIP (start)" else "Monthly SIP", Money.compact(sip), Modifier.weight(1f))
            StatBlock("This month", if (thisMonth >= sip && sip > 0) "✅ done" else Money.compact(thisMonth), Modifier.weight(1f),
                if (thisMonth >= sip && sip > 0) Kc.colors.good else Kc.colors.text)
        }
        Text("Needs ${Money.compact(math.futureCostPaise)} in ${math.years} years (${Money.compact(plan.todayCostPaise)} today, ${"%.0f".format(plan.inflationPct)}% inflation)" +
            (plan.childAge?.let { " · your child will be ${it + math.years}" } ?: "") + (if (plan.stepUp) ". SIP grows 10% a year." else "."),
            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        Text("Where: ${math.mix} (assumes ~${"%.0f".format(math.expectedReturnPct)}% a year).", style = MaterialTheme.typography.bodySmall, color = Kc.colors.text)
        if (plan.kind.childGoal) Text("👧 Saving for a daughter? Sukanya Samriddhi Yojana (post office / bank) is a government scheme for girls with tax benefits; it can be the safe part of this goal. Check the current rate before investing.",
            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        leaks.firstOrNull()?.let { (cat, avg) ->
            val half = avg / 2
            if (half >= 100_00 && sip > 0) Text("🔁 Halving ${cat.emoji} ${cat.name} (about ${Money.compact(avg)}/month) frees ${Money.compact(half)}, which covers ${GoalPlanner.leakCoverPct(half, sip)}% of this SIP.",
                style = MaterialTheme.typography.bodySmall, color = Kc.colors.good)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("+ Add money", Haldi, Ink, onClick = onAdd)
            Pill("✎ Plan", Kc.colors.chip, Kc.colors.text, onClick = onPlan)
            if (plan.sipDay > 0) Text("Reminder on the ${plan.sipDay}${ordinal(plan.sipDay)}", style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun ordinal(n: Int) = if (n in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

@Composable
private fun PlanGoalDialog(existing: Goal?, plan: GoalPlan?, onDismiss: () -> Unit, onDelete: (Long) -> Unit, onSave: (Goal, GoalPlan) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(plan?.kind ?: GoalKind.HOME) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var cost by rememberSaveable { mutableStateOf(((plan?.todayCostPaise ?: existing?.targetPaise ?: 0L) / 100).takeIf { it > 0 }?.toString() ?: "") }
    var years by rememberSaveable { mutableStateOf((plan?.years ?: 10).toString()) }
    var childAge by rememberSaveable { mutableStateOf(plan?.childAge?.toString() ?: "") }
    var goalAge by rememberSaveable { mutableStateOf("18") }
    var saved by rememberSaveable { mutableStateOf(((existing?.savedPaise ?: 0L) / 100).takeIf { it > 0 }?.toString() ?: "") }
    var stepUp by rememberSaveable { mutableStateOf(plan?.stepUp ?: true) }
    var day by rememberSaveable { mutableStateOf((plan?.sipDay ?: 5).toString()) }

    val y = if (kind.childGoal && childAge.toIntOrNull() != null && goalAge.toIntOrNull() != null)
        GoalPlanner.yearsForChild(childAge.toInt(), goalAge.toInt()) else years.toIntOrNull()?.coerceIn(1, 40) ?: 0
    val costPaise = (cost.toLongOrNull() ?: 0) * 100
    val savedPaise = (saved.toLongOrNull() ?: 0) * 100
    val draft = if (costPaise > 0 && y > 0) GoalPlan(existing?.id ?: 0, kind, costPaise, y, kind.inflationPct, stepUp, day.toIntOrNull()?.coerceIn(0, 28) ?: 5,
        childAge.toIntOrNull()?.takeIf { kind.childGoal }) else null
    val math = draft?.let { GoalPlanner.plan(it, savedPaise) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Plan a goal" else "Edit plan") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GoalKind.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { k -> Pill("${k.emoji} ${k.label.substringBefore(' ')}", if (k == kind) Haldi else Kc.colors.chip, if (k == kind) Ink else Kc.colors.text) {
                            kind = k; if (name.isBlank() || GoalKind.entries.any { name == it.label }) name = k.label
                            if (k == GoalKind.CHILD_WEDDING) goalAge = "25"
                        } }
                    }
                }
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name, e.g. \"Riya's college\"") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cost, { v -> cost = v.filter(Char::isDigit).take(10) }, label = { Text("What it costs today") }, prefix = { Text("₹ ") }, singleLine = true,
                    supportingText = { Text(kind.hint) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (kind.childGoal) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(childAge, { v -> childAge = v.filter(Char::isDigit).take(2) }, label = { Text("Child's age now") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(goalAge, { v -> goalAge = v.filter(Char::isDigit).take(2) }, label = { Text("Needed at age") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                } else OutlinedTextField(years, { v -> years = v.filter(Char::isDigit).take(2) }, label = { Text("In how many years?") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(saved, { v -> saved = v.filter(Char::isDigit).take(10) }, label = { Text("Already saved for it") }, prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                SwitchRow("Step-up SIP (+10% a year)", "Start smaller and raise it with your salary. Usually the easiest way to reach big goals.", stepUp) { stepUp = it }
                OutlinedTextField(day, { v -> day = v.filter(Char::isDigit).take(2) }, label = { Text("Remind me on day (0 = off)") }, singleLine = true,
                    supportingText = { Text("Pick the day after your salary arrives") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (math != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kc.colors.goodBg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("In $y years this will cost about ${Money.compact(math.futureCostPaise)}", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                    Text(if (math.sipFlatPaise == 0L) "Already covered by what you've saved 🎉"
                        else "Invest ${Money.format(if (stepUp) math.sipStepUpStartPaise else math.sipFlatPaise)} a month" + (if (stepUp) ", rising 10% each year" else ""),
                        style = MaterialTheme.typography.titleMedium, color = Kc.colors.good)
                    if (stepUp && math.sipFlatPaise > 0) Text("(or a flat ${Money.format(math.sipFlatPaise)} every month)", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    Text(math.mix, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                }
                if (existing != null) TextButton(onClick = { onDelete(existing.id) }) { Text("Delete goal", color = Coral) }
            }
        },
        confirmButton = { TextButton(enabled = draft != null && math != null && name.isNotBlank(), onClick = {
            val p = draft ?: return@TextButton; val m = math ?: return@TextButton
            val goal = (existing ?: Goal(name = "", emoji = kind.emoji, targetPaise = 0)).copy(
                name = name.trim(), emoji = kind.emoji, targetPaise = m.futureCostPaise, savedPaise = existing?.savedPaise ?: savedPaise,
                targetDate = LocalDate.now().plusYears(p.years.toLong()))
            onSave(goal, p)
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

