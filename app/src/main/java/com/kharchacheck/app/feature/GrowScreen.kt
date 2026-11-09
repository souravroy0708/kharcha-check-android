package com.kharchacheck.app.feature

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Goal
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.ChallengeProgress
import com.kharchacheck.app.domain.ChallengeStatus
import com.kharchacheck.app.domain.DayMark
import com.kharchacheck.app.domain.Gamification
import com.kharchacheck.app.domain.GoldenRules
import com.kharchacheck.app.domain.InvestMath
import com.kharchacheck.app.domain.TipTopic
import com.kharchacheck.app.ui.AppData
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.components.HabitGrid
import com.kharchacheck.app.ui.components.Hero
import com.kharchacheck.app.ui.components.HeroTile
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.MoneyDialog
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.ProgressBar
import com.kharchacheck.app.ui.components.Segmented
import com.kharchacheck.app.ui.components.StatBlock
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import com.kharchacheck.app.ui.theme.MutedOnInk
import com.kharchacheck.app.ui.theme.Violet
import java.time.YearMonth

@Composable
fun GrowScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val g = data.game
    var goalEdit by remember { mutableStateOf<Goal?>(null) }
    var newGoal by rememberSaveable { mutableStateOf(false) }
    var fundGoal by remember { mutableStateOf<Goal?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Hero {
                Text("Grow", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(64.dp).aspectRatio(1f).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) {
                        Text("${g.level.number}", style = MaterialTheme.typography.headlineLarge, color = Ink)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(g.level.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        ProgressBar(g.levelProgress, Haldi, track = com.kharchacheck.app.ui.theme.Ink3, height = 8.dp)
                        Text(g.nextLevel?.let { "${g.xp} XP · ${it.minXp - g.xp} to ${it.name}" } ?: "${g.xp} XP · top level!", style = MaterialTheme.typography.bodySmall, color = MutedOnInk)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroTile("Streak", "${g.logStreak}🔥", "best ${g.bestLogStreak}", Haldi, Modifier.weight(1f))
                    HeroTile("Leak-free", "${g.leakFreeStreak} days", "best ${g.bestLeakFreeStreak}", Mint, Modifier.weight(1f))
                    HeroTile("Badges", "${g.earnedCount}/${g.badges.size}", null, Violet, Modifier.weight(1f))
                }
            }
        }

        item { Pad { KcCard(title = "Your 21 days") { HabitGrid(g.last21) } } }

        item { Pad { ChallengeCard(data, vm) } }

        item {
            Pad {
                KcCard(onClick = { nav.navigate(com.kharchacheck.app.ui.nav.WealthRoute) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💼", fontSize = 28.sp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Wealth & net worth", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                            Text("Your mutual funds at today's prices, FDs, PPF, bank balances and loans in one number.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = Kc.colors.muted)
                    }
                }
            }
        }

        item { Pad { LeakToSipCard(data) } }

        item {
            Pad {
                KcCard(title = "🎯 Savings goals", trailing = { TextButton(onClick = { newGoal = true }) { Text("+ New") } }) {
                    if (data.goals.isEmpty()) Text("Give your money a job: emergency fund, a trip, a bike, a gift. Every rupee you add is logged as Save & Invest.",
                        style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                    data.goals.forEach { goal ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { goalEdit = goal }.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(goal.emoji, fontSize = 24.sp); Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(goal.name, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                                    Text("${Money.format(goal.savedPaise)} of ${Money.format(goal.targetPaise)}" + if (goal.done) " · done 🏁" else "",
                                        style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                                }
                                Pill("+ Add", Haldi, Ink) { fundGoal = goal }
                            }
                            ProgressBar(goal.progress, if (goal.done) Mint else Haldi)
                        }
                    }
                    EmergencyHint(data)
                }
            }
        }

        item {
            Pad {
                KcCard(title = "Badges") {
                    g.badges.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { b ->
                                Column(Modifier.weight(1f).alpha(if (b.earned) 1f else 0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(CircleShape).background(if (b.earned) Haldi.copy(alpha = 0.3f) else Kc.colors.chip),
                                        contentAlignment = Alignment.Center) { Text(if (b.earned) b.badge.emoji else "🔒", fontSize = 24.sp) }
                                    Text(b.badge.name, style = MaterialTheme.typography.labelSmall, color = Kc.colors.text, textAlign = TextAlign.Center, maxLines = 2)
                                    if (!b.earned) Text(b.badge.how, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Kc.colors.muted, textAlign = TextAlign.Center, maxLines = 3)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        item { Text("Golden rules of money", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text, modifier = Modifier.padding(horizontal = 20.dp)) }
        item { RulesList(data) }
        item {
            Text(GoldenRules.DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        }
    }

    if (newGoal || goalEdit != null) GoalDialog(goalEdit, onDismiss = { newGoal = false; goalEdit = null }, onDelete = { id -> vm.deleteGoal(id); goalEdit = null }) { gl ->
        vm.saveGoal(gl); newGoal = false; goalEdit = null
    }
    fundGoal?.let { goal ->
        MoneyDialog("Add to ${goal.name}", 0, "This logs a Save & Invest entry, so your report counts it as saved.", onDismiss = { fundGoal = null }) { p ->
            if (p > 0) vm.addToGoal(goal, p); fundGoal = null
        }
    }
}

@Composable
private fun Pad(content: @Composable () -> Unit) = Box(Modifier.padding(horizontal = 16.dp)) { content() }

@Composable
private fun ChallengeCard(d: AppData, vm: AppViewModel) {
    val p = d.challenge
    if (p != null && p.status != ChallengeStatus.WON) {
        ActiveChallenge(p, vm)
        return
    }
    KcCard(title = "⚔️ Pick a challenge") {
        Text("Small, time-boxed wins build the habit. Winning gives +100 XP and a badge.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        val leakKeys = d.report.leakCategories.map { it.category.key }.toSet()
        // Challenges that target your own top leaks come first.
        Gamification.challenges.sortedByDescending { c -> c.categoryKeys.any { it in leakKeys } }.forEach { c ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Kc.colors.chip).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c.emoji, fontSize = 26.sp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                    Text(c.description, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                }
                Spacer(Modifier.width(8.dp))
                Pill("Start", Haldi, Ink) { vm.startChallenge(c.id) }
            }
        }
    }
}

@Composable
private fun ActiveChallenge(p: ChallengeProgress, vm: AppViewModel) {
    var confirmQuit by rememberSaveable { mutableStateOf(false) }
    KcCard(title = "${p.challenge.emoji} ${p.challenge.title}") {
        Text(p.challenge.description, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        p.marks.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { m ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(
                        when (m) { DayMark.OK -> Haldi; DayMark.FAIL -> Coral; DayMark.TODAY -> Violet.copy(alpha = 0.6f); DayMark.NEUTRAL -> Kc.colors.chip; DayMark.FUTURE -> Kc.colors.line.copy(alpha = 0.5f) },
                    ), contentAlignment = Alignment.Center) {
                        Text(when (m) { DayMark.OK -> "✓"; DayMark.FAIL -> "✗"; DayMark.TODAY -> "•"; else -> "" }, color = Ink, style = MaterialTheme.typography.labelMedium)
                    }
                }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        when (p.status) {
            ChallengeStatus.FAILED -> {
                Text("This one slipped. That's how habits are built: notice, reset, go again.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Restart", Haldi, Ink) { vm.startChallenge(p.challenge.id) }
                    Pill("Choose another", Kc.colors.chip, Kc.colors.text) { vm.endChallenge() }
                }
            }
            else -> {
                val target = if (p.challenge.target > 0) " · ${p.count}/${p.challenge.target} zero days" else ""
                Text("Ends ${p.end.dayOfMonth} ${p.end.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}$target",
                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                TextButton(onClick = { confirmQuit = true }) { Text("Give up") }
            }
        }
    }
    if (confirmQuit) AlertDialog(
        onDismissRequest = { confirmQuit = false },
        title = { Text("Give up this challenge?") }, text = { Text("No XP lost. You can start any challenge again later.") },
        confirmButton = { TextButton(onClick = { vm.endChallenge(); confirmQuit = false }) { Text("Give up") } },
        dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Keep going") } },
    )
}

@Composable
private fun LeakToSipCard(d: AppData) {
    val avgLeaks = remember(d.txns) {
        val months = (1..3).map { YearMonth.now().minusMonths(it.toLong()) }
        val sums = months.map { m -> d.txns.filter { it.month == m && it.type == TxType.EXPENSE && d.catMap[it.categoryId]?.bucket == Bucket.LEAK }.sumOf { it.amountPaise } }.filter { it > 0 }
        if (sums.isEmpty()) d.report.leaksPaise else sums.sum() / sums.size
    }
    var monthly by remember { mutableFloatStateOf(((avgLeaks / 100).coerceIn(500, 20_000) / 2).toFloat().coerceAtLeast(500f)) }
    var yearsIdx by rememberSaveable { mutableStateOf(1) }
    val years = listOf(5, 10, 15, 20)[yearsIdx]
    val rate = d.settings.expectedReturnPct.toDouble()
    val paise = (monthly.toLong() / 100 * 100) * 100
    val fv = InvestMath.sipFutureValue(paise, rate, years)
    val invested = paise * 12 * years
    KcCard(title = "🌱 Cut a leak, grow a SIP") {
        Text(if (avgLeaks > 0) "Your leaks average ${Money.format(avgLeaks)} a month. What if you cut part of it and invested monthly?"
            else "What if you cut a small habit and invested it every month?", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        Text("Cut ${Money.format(paise)} / month", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
        Slider(value = monthly, onValueChange = { monthly = it }, valueRange = 500f..20_000f,
            colors = SliderDefaults.colors(thumbColor = Haldi, activeTrackColor = Haldi))
        Segmented(listOf("5 yrs", "10 yrs", "15 yrs", "20 yrs"), yearsIdx) { yearsIdx = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBlock("You put in", Money.compact(invested), Modifier.weight(1f))
            StatBlock("Could become", Money.compact(fv), Modifier.weight(1f), Mint)
        }
        Text("At ${rate.toInt()}% a year (change in Settings). Money roughly doubles every ${"%.0f".format(InvestMath.yearsToDouble(rate))} years by the Rule of 72. Returns are not guaranteed.",
            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
    }
}

@Composable
private fun EmergencyHint(d: AppData) {
    val avgNeeds = remember(d.txns) {
        val sums = (1..3).map { YearMonth.now().minusMonths(it.toLong()) }.map { m ->
            d.txns.filter { it.month == m && it.type == TxType.EXPENSE && d.catMap[it.categoryId]?.bucket == Bucket.NEED }.sumOf { it.amountPaise }
        }.filter { it > 0 }
        if (sums.isEmpty()) 0L else sums.sum() / sums.size
    }
    if (avgNeeds <= 0) return
    val target = InvestMath.emergencyFundTarget(avgNeeds)
    val hasGoal = d.goals.any { it.name.contains("emergency", true) }
    Text("🛟 Emergency fund target: about ${Money.compact(target)} (6 × your average monthly needs of ${Money.format(avgNeeds)})." +
        if (!hasGoal) " Create an “Emergency fund” goal to track it." else "",
        style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
}

@Composable
private fun RulesList(d: AppData) {
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("All", if (topic == null) Haldi else Kc.colors.chip, if (topic == null) Ink else Kc.colors.text) { topic = null }
            listOf(TipTopic.BUDGET, TipTopic.CUT, TipTopic.SAVE, TipTopic.INVEST).forEach { t ->
                Pill(t.label, if (topic == t.name) Haldi else Kc.colors.chip, if (topic == t.name) Ink else Kc.colors.text) { topic = t.name }
            }
        }
        GoldenRules.tips.filter { topic == null || it.topic.name == topic || (topic == "INVEST" && it.topic in setOf(TipTopic.PROTECT, TipTopic.DEBT)) }.forEach { tip ->
            var open by rememberSaveable(tip.id) { mutableStateOf(false) }
            KcCard(Modifier.animateContentSize(), onClick = { open = !open }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tip.emoji, fontSize = 24.sp); Spacer(Modifier.width(12.dp))
                    Text(tip.title, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, modifier = Modifier.weight(1f))
                    Text(if (open) "−" else "+", style = MaterialTheme.typography.titleLarge, color = Kc.colors.muted)
                }
                if (open) {
                    Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                    if (tip.id == "age_rule" && d.settings.age > 0) Text("For you (age ${d.settings.age}): about ${InvestMath.equityShareByAge(d.settings.age)}% in equity as a starting point.",
                        style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                    if (tip.id == "503020" && !d.report.isEmpty) Text("This month: ${d.report.split.needsPct}/${d.report.split.wantsPct}/${d.report.split.savePct}.",
                        style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                }
            }
        }
    }
}

@Composable
private fun GoalDialog(existing: Goal?, onDismiss: () -> Unit, onDelete: (Long) -> Unit, onSave: (Goal) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(existing?.emoji ?: "🎯") }
    var target by rememberSaveable { mutableStateOf(existing?.targetPaise?.let { (it / 100).toString() } ?: "") }
    val emojis = listOf("🎯", "🛟", "✈️", "🏍️", "🏠", "💍", "🎓", "📱", "🚗", "🎁", "👶", "🧓")
    val ok = name.isNotBlank() && (Money.parse(target) ?: 0) > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Goal name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(target, { v -> target = v.filter { it.isDigit() }.take(10) }, label = { Text("Target amount") }, prefix = { Text("₹ ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                emojis.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { e -> Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(if (e == emoji) Haldi else Kc.colors.chip).clickable { emoji = e },
                            contentAlignment = Alignment.Center) { Text(e, fontSize = 20.sp) } }
                    }
                }
                if (existing != null) OutlinedButton(onClick = { onDelete(existing.id) }) { Text("Delete goal", color = Coral) }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = {
            onSave((existing ?: Goal(name = name, emoji = emoji, targetPaise = 0)).copy(name = name.trim(), emoji = emoji, targetPaise = Money.parse(target) ?: 0))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

