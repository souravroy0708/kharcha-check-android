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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.ChallengeStatus
import com.kharchacheck.app.domain.DayMark
import com.kharchacheck.app.domain.Bills
import com.kharchacheck.app.domain.DailyWisdom
import com.kharchacheck.app.domain.Dues
import com.kharchacheck.app.domain.Wisdom
import com.kharchacheck.app.ui.AppData
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.components.CategoryIcon
import com.kharchacheck.app.ui.components.Hero
import com.kharchacheck.app.ui.components.HeroTile
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.ProfileAvatar
import com.kharchacheck.app.ui.components.ProgressBar
import com.kharchacheck.app.ui.components.SectionHeader
import com.kharchacheck.app.ui.components.SpendRing
import com.kharchacheck.app.ui.components.SplitBar
import com.kharchacheck.app.ui.components.TxnRow
import com.kharchacheck.app.ui.nav.ActivityRoute
import com.kharchacheck.app.ui.nav.BillsRoute
import com.kharchacheck.app.ui.nav.DuesRoute
import com.kharchacheck.app.ui.nav.EditRoute
import com.kharchacheck.app.ui.nav.GuideRoute
import com.kharchacheck.app.ui.nav.GrowRoute
import com.kharchacheck.app.ui.nav.InboxRoute
import com.kharchacheck.app.ui.nav.SettingsRoute
import com.kharchacheck.app.ui.nav.StatementsRoute
import com.kharchacheck.app.ui.openTab
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import com.kharchacheck.app.ui.theme.MutedOnInk
import com.kharchacheck.app.ui.theme.Violet
import java.time.LocalDate
import java.time.YearMonth
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val platform = LocalPlatform.current
    val quick = remember(data.txns, data.categories) { quickCategories(data) }
    val files by vm.attachments.collectAsStateWithLifecycle()
    val billsList by vm.bills.collectAsStateWithLifecycle()
    val duesList by vm.dues.collectAsStateWithLifecycle()
    val attached = remember(files) { files.mapNotNull { it.txnId }.toSet() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { HomeHero(vm, data, onSettings = { nav.navigate(SettingsRoute) }) }

        // Today's money thought: one of 100, unique each day, in this install's own order.
        if (data.settings.wisdomSeed != 0L) item {
            val today = LocalDate.now()
            val card = remember(today, data.settings.wisdomSeed) { DailyWisdom.forDate(today, data.settings.wisdomSeed) }
            val practisedToday = today.toEpochDay() in data.settings.practicedTipDays
            val tipStreak = com.kharchacheck.app.domain.Gamification.currentStreak(today) { it.toEpochDay() in data.settings.practicedTipDays }
            Box(Modifier.padding(horizontal = 16.dp)) { WisdomCard(card, practisedToday, tipStreak, onDone = vm::markTipPracticed, onShare = { platform.shareText("${card.emoji} ${card.line}\n\nTry today: ${card.doToday}\n\n— via Kharcha Check") }) }
        }

        if (data.inbox.isNotEmpty()) item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                KcCard(onClick = { nav.navigate(InboxRoute) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📥", fontSize = 28.sp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${data.inbox.size} to review", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                            Text("Captured from bank messages. Check the category, then confirm.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                        }
                        Pill("Review", Haldi, Ink)
                    }
                }
            }
        }

        // Quick add: one tap on a frequent category opens the keypad with it preselected.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quick add", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text, modifier = Modifier.padding(horizontal = 20.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        QuickChip("📋", "Paste SMS") {
                            val text = platform.pasteFromClipboard()
                            if (text.isNullOrBlank()) vm.say("Copy a bank SMS or UPI message first, then tap Paste.")
                            else vm.draftFromText(text) { nav.navigate(EditRoute(fromDraft = true)) }
                        }
                    }
                    item {
                        QuickChip("📸", "Screenshot") {
                            platform.pickImage { uri -> vm.draftFromImage(uri) { nav.navigate(EditRoute(fromDraft = true)) } }
                        }
                    }
                    item { QuickChip("📄", "Statement") { nav.navigate(StatementsRoute(java.time.YearMonth.now().toString())) } }
                    items(quick, key = { it.id }) { c -> QuickChip(c.emoji, c.name.substringBefore(" (").substringBefore(" &")) { nav.navigate(EditRoute(type = c.bucket.type.name, categoryId = c.id)) } }
                }
            }
        }

        item {
            val plan = remember(data) { com.kharchacheck.app.feature.salaryPlanFor(data) }
            Box(Modifier.padding(horizontal = 16.dp)) {
                KcCard(onClick = { nav.navigate(GuideRoute()) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📘", fontSize = 28.sp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            if (plan == null) {
                                Text("Your salary plan", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                                Text("Add your salary: get your needs/wants/savings split, the right order of money steps and a SIP plan.",
                                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                            } else {
                                val next = plan.steps.firstOrNull { !it.done }
                                Text("Save ${Money.compact(plan.savePaise)}/month · ${plan.steps.count { it.done }}/${plan.steps.size} steps",
                                    style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                                Text(next?.let { "Next: ${it.emoji} ${it.title}" } ?: "All steps done. Review once a year 🎉",
                                    style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                            }
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = Kc.colors.muted)
                    }
                }
            }
        }

        // Money tools: bills due within a week and money people owe you.
        val upcomingBills = Bills.upcoming(billsList, LocalDate.now(), 7)
        if (upcomingBills.isNotEmpty()) item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                KcCard(title = "🧾 Bills coming up", onClick = { nav.navigate(BillsRoute) }) {
                    upcomingBills.take(3).forEach { st ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(st.bill.emoji, fontSize = 22.sp); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(st.bill.name, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                                Text(billStatusText(st, false), style = MaterialTheme.typography.bodySmall, color = if (st.overdue || st.daysLeft <= 1) Coral else Kc.colors.muted)
                            }
                            Pill("Paid", Haldi, Ink) { vm.markBillPaid(st.bill, YearMonth.from(st.dueDate)) }
                        }
                    }
                }
            }
        }
        val collect = Dues.toCollect(duesList)
        val pay = Dues.toPay(duesList)
        if (collect > 0 || pay > 0) item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                KcCard(onClick = { nav.navigate(DuesRoute) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤝", fontSize = 26.sp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Split & dues", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                            Text(listOfNotNull(collect.takeIf { it > 0 }?.let { "${Money.format(it)} to collect" }, pay.takeIf { it > 0 }?.let { "${Money.format(it)} to pay" }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = Kc.colors.muted)
                    }
                }
            }
        }

        item { Box(Modifier.padding(horizontal = 16.dp)) { StreakCard(data, vm, onGrow = { nav.openTab(GrowRoute) }) } }

        if (!data.report.isEmpty) item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                KcCard(title = "Needs · Wants · Savings", trailing = { if (data.report.split.followsRule) Pill("50/30/20 ✓", Kc.colors.goodBg, Kc.colors.good) }) {
                    SplitBar(data.report.split)
                    if (!data.report.split.basedOnIncome) Text("Add your income to see this as a share of what you earn.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                }
            }
        }

        if (data.report.leaksPaise > 0) item { Box(Modifier.padding(horizontal = 16.dp)) { LeakCard(data, onGrow = { nav.openTab(GrowRoute) }) } }

        item { Box(Modifier.padding(horizontal = 12.dp)) { SectionHeader("Recent", "See all") { nav.openTab(ActivityRoute) } } }
        val recent = data.txns.take(6)
        if (recent.isEmpty()) item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                KcCard {
                    Text("Your first entry takes 5 seconds", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                    Text("Tap the yellow + button, type the amount, pick a category. That's it. Or share any bank SMS to Kharcha Check.",
                        style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                }
            }
        } else item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                KcCard(padding = 10.dp) { recent.forEach { t -> TxnRow(t, data.catMap[t.categoryId], attached = t.id in attached) { nav.navigate(EditRoute(id = t.id)) } } }
            }
        }

    }
}

private fun quickCategories(d: AppData): List<Category> {
    val since = System.currentTimeMillis() - 90L * 86_400_000
    val counts = d.txns.filter { it.epochMillis >= since }.groupingBy { it.categoryId }.eachCount()
    val top = counts.entries.sortedByDescending { it.value }.mapNotNull { d.catMap[it.key] }.filter { !it.archived && it.bucket.isSpend }.take(6)
    if (top.size >= 6) return top
    val defaults = listOf("groceries", "food_delivery", "chai_coffee", "commute", "dining_out", "junk_food")
        .mapNotNull { k -> d.activeCategories.firstOrNull { it.key == k } }
    return (top + defaults).distinctBy { it.id }.take(6)
}

@Composable
private fun QuickChip(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(78.dp).clip(RoundedCornerShape(18.dp)).background(Kc.colors.card).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 24.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Kc.colors.text, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HomeHero(vm: AppViewModel, d: AppData, onSettings: () -> Unit) {
    val r = d.report
    val s = d.settings
    val today = LocalDate.now()
    val greeting = when (LocalTime.now().hour) { in 4..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val budget = s.monthlyBudgetPaise
    val daysLeft = (today.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
    val left = budget - r.spendPaise
    Hero {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(vm, s.name, 42.dp, onClick = onSettings)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (s.name.isBlank()) "$greeting 👋" else "$greeting, ${s.name} 👋", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) + " · private to this phone 🔒",
                    style = MaterialTheme.typography.bodySmall, color = MutedOnInk)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpendRing(
                fraction = if (budget > 0) r.spendPaise.toFloat() / budget else 0f,
                leakFraction = if (r.spendPaise > 0) r.leaksPaise.toFloat() / r.spendPaise else 0f,
                over = budget in 1 until r.spendPaise,
                modifier = Modifier.size(150.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Spent", style = MaterialTheme.typography.labelSmall, color = MutedOnInk)
                    Text(Money.compact(r.spendPaise), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(if (budget > 0) "of ${Money.compact(budget)}" else "this month", style = MaterialTheme.typography.labelSmall, color = MutedOnInk)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroLine("Today", Money.format(d.todaySpendPaise))
                if (budget > 0) {
                    if (left >= 0) HeroLine("Safe to spend / day", Money.format(left / daysLeft), Haldi)
                    else HeroLine("Over budget by", Money.format(-left), Coral)
                } else HeroLine("Monthly budget", "Not set", MutedOnInk)
                if (r.projectedSpendPaise > 0 && today.dayOfMonth >= 5) HeroLine("Month-end pace", Money.compact(r.projectedSpendPaise))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroTile("Income", Money.compact(r.incomePaise), null, Mint, Modifier.weight(1f))
            HeroTile("Invested", Money.compact(r.investPaise), null, Violet, Modifier.weight(1f))
            HeroTile("Leaks", Money.compact(r.leaksPaise), null, Coral, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroLine(label: String, value: String, color: Color = Color.White) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MutedOnInk)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
    }
}

@Composable
private fun StreakCard(d: AppData, vm: AppViewModel, onGrow: () -> Unit) {
    val g = d.game
    val todayNoSpend = LocalDate.now().toEpochDay() in d.settings.noSpendDays
    val week = g.last21.takeLast(7)
    KcCard(onClick = onGrow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (g.logStreak > 0) "🔥 ${g.logStreak}-day streak" else "Start your streak today", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text)
                Text("Level ${g.level.number} · ${g.level.name} · ${g.xp} XP", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
            if (g.leakFreeStreak >= 2) Pill("🔧 ${g.leakFreeStreak} leak-free", Kc.colors.goodBg, Kc.colors.good)
        }
        ProgressBar(g.levelProgress, Haldi, height = 8.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.forEach { c ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(
                            when { c.noSpend -> Mint; c.active && !c.leak -> Haldi; c.active -> Violet.copy(alpha = 0.55f); else -> Kc.colors.chip },
                        ),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (c.active || c.noSpend) "✓" else "", color = Ink, style = MaterialTheme.typography.labelMedium) }
                    Text(c.date.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
                }
            }
        }
        d.challenge?.takeIf { it.status == ChallengeStatus.ACTIVE }?.let { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.challenge.emoji, fontSize = 20.sp); Spacer(Modifier.width(8.dp))
                Text("${p.challenge.title}: day ${p.marks.count { it != DayMark.FUTURE }} of ${p.challenge.days}", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text, modifier = Modifier.weight(1f))
            }
        }
        if (!g.loggedToday || todayNoSpend) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (todayNoSpend) "🌿 No-spend day ✓ (undo)" else "🌿 I spent nothing today", if (todayNoSpend) Kc.colors.goodBg else Kc.colors.chip,
                    if (todayNoSpend) Kc.colors.good else Kc.colors.text, onClick = vm::toggleNoSpendToday)
            }
        }
    }
}

@Composable
private fun LeakCard(d: AppData, onGrow: () -> Unit) {
    val r = d.report
    KcCard(title = "🕳️ Leaks this month", trailing = { Text(Money.format(r.leaksPaise), style = MaterialTheme.typography.titleLarge, color = Coral) }) {
        Text("Small, frequent spends that are easiest to cut. Invested every month instead, this could be about " +
            "${Money.compact(r.leakTenYearValuePaise)} in 10 years at ${d.settings.expectedReturnPct}%.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        r.leakCategories.take(3).forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(line.category, 34.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(line.category.name, style = MaterialTheme.typography.titleSmall, color = Kc.colors.text, maxLines = 1)
                    Text("${line.count} times", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                }
                Text(Money.format(line.paise), style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
            }
        }
        Text("Take a challenge to cut one →", style = MaterialTheme.typography.labelLarge, color = if (Kc.colors.dark) Haldi else Ink,
            modifier = Modifier.clickable(onClick = onGrow).padding(vertical = 4.dp))
    }
}

@Composable
private fun WisdomCard(w: Wisdom, done: Boolean, streak: Int, onDone: () -> Unit, onShare: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Haldi.copy(alpha = if (Kc.colors.dark) 0.16f else 0.22f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today · ${w.kind.label}", style = MaterialTheme.typography.labelMedium, color = Kc.colors.muted, modifier = Modifier.weight(1f))
            Text("${w.id}/${DailyWisdom.all.size}", style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
        }
        Row(verticalAlignment = Alignment.Top) {
            Text(w.emoji, fontSize = 34.sp)
            Spacer(Modifier.width(12.dp))
            Text(w.line, style = MaterialTheme.typography.titleLarge, color = Kc.colors.text)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Kc.colors.card).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✅", fontSize = 18.sp); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Try today", style = MaterialTheme.typography.labelMedium, color = Kc.colors.muted)
                Text(w.doToday, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (done) Pill("✅ Done today" + if (streak > 1) " · 🔥 $streak days" else "", Kc.colors.goodBg, Kc.colors.good, onClick = onDone)
            else Pill("✅ I did it · +15 XP", Haldi, Ink, onClick = onDone)
            Spacer(Modifier.weight(1f))
            Pill("Share", Ink, Color.White, onClick = onShare)
        }
        Text("A new one tomorrow. Education, not advice.", style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
    }
}
