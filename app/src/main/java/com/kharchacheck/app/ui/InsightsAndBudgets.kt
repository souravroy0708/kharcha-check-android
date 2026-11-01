package com.kharchacheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharchacheck.app.AppController
import com.kharchacheck.app.CategoryEntity
import com.kharchacheck.app.CategorySpendSummary
import com.kharchacheck.app.Money
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsTab(app: AppController, actions: PhoneActions) {
    val lang = app.language
    val summaries = app.categorySummaries
    val fixedSummaries = summaries.filter { it.category.isFixed }
    val flexSummaries = summaries.filter { !it.category.isFixed }
    val fixedTotal = fixedSummaries.sumOf { it.currentPaise }
    val flexTotal = flexSummaries.sumOf { it.currentPaise }
    val maxCatSpend = (summaries.maxOfOrNull { it.currentPaise } ?: 1L).coerceAtLeast(1L)

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("Insights"), style = MaterialTheme.typography.headlineLarge)
            SmallPill("📄 Export PDF", emerald = true) { actions.onExportMonthPdf() }
        }

        // Launch 3-Minute Guided Month-End Review button
        BigButton(
            text = "✨ ${t("Start 3-Min Month-End Review")}",
            emerald = true,
            onClick = {
                app.monthReviewStep = 0
                app.monthReviewOpen = true
            },
        )

        // 1. Fixed vs Flexible Spend Split (Spec Page 6 & 7)
        AppCard(
            title = t("Fixed vs Flexible Spend"),
            subtitle = "Fixed costs (Rent, EMIs, Bills) vs day-to-day flexible choices",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Paper)
                        .padding(12.dp)
                ) {
                    Text("Fixed Spend", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(Money.format(fixedTotal), style = MaterialTheme.typography.titleLarge)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Paper)
                        .padding(12.dp)
                ) {
                    Text("Flexible Spend", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(Money.format(flexTotal), style = MaterialTheme.typography.titleLarge, color = PrimaryAccent)
                }
            }

            Text("Flexible Categories (vs 3-month average):", style = MaterialTheme.typography.labelMedium, color = Muted)
            flexSummaries.forEach { s ->
                CategoryBarRow(s = s, maxSpend = maxCatSpend, lang = lang)
            }

            if (fixedSummaries.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Fixed Categories:", style = MaterialTheme.typography.labelMedium, color = Muted)
                fixedSummaries.forEach { s ->
                    CategoryBarRow(s = s, maxSpend = maxCatSpend, lang = lang)
                }
            }
        }

        // 2. Recurring Fixed Costs (Spotted after >= 2 repeats — Spec Page 4 & 6)
        val recurring = app.detectedRecurringCosts
        if (recurring.isNotEmpty()) {
            AppCard(
                title = t("Recurring Fixed Costs"),
                subtitle = "Spotted automatically after 2+ monthly repeats",
            ) {
                recurring.forEach { r ->
                    val cat = app.categoryMap[r.categoryId]
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(cat?.emoji ?: "🔁", fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(r.merchantName, style = MaterialTheme.typography.titleMedium)
                                Text("${r.cadence} · Next around ${r.nextDueDate}", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                        Text(Money.format(r.amountPaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Weekday & Time-of-Day Strip + Payment Mode Split (Spec Page 7 Step 7)
        AppCard(
            title = "When & How You Spend",
            subtitle = "Weekday strip, time-of-day strip, and UPI vs Card vs Cash split",
        ) {
            Text("By Day of Week:", style = MaterialTheme.typography.labelMedium, color = Muted)
            val wd = app.weekdaySpendPaise
            val maxWd = (wd.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
            Row(
                Modifier.fillMaxWidth().height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                wd.forEach { (dow, paise) ->
                    val frac = (paise.toFloat() / maxWd.toFloat()).coerceIn(0.05f, 1f)
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.65f)
                                .fillMaxHeight(frac * 0.72f)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (frac > 0.85f) Amber else Emerald)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                            color = Muted,
                        )
                    }
                }
            }

            HorizontalDivider(color = PaperLine)
            Text("By Time of Day:", style = MaterialTheme.typography.labelMedium, color = Muted)
            app.timeOfDaySpendPaise.forEach { (slot, paise) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(slot, style = MaterialTheme.typography.bodyMedium)
                    Text(Money.format(paise), style = MaterialTheme.typography.titleMedium)
                }
            }

            HorizontalDivider(color = PaperLine)
            Text("Payment Mode Split (UPI vs Card vs Cash/ATM):", style = MaterialTheme.typography.labelMedium, color = Muted)
            val totalSpent = app.monthSpentPaise.coerceAtLeast(1L)
            app.modeSplitPaise.forEach { (mode, paise) ->
                val pct = (paise * 100L) / totalSpent
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${mode.labelEn} ($pct%)", style = MaterialTheme.typography.bodyMedium)
                    Text(Money.format(paise), style = MaterialTheme.typography.titleMedium, color = PrimaryAccent)
                }
            }
        }

        // 4. On-Device Success Metrics (Spec Pages 11-12)
        AppCard(
            title = "On-Device Pilot Metrics",
            subtitle = "Computed locally on your phone; nothing is ever uploaded",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricBox("Auto-Captured Share", "${app.autoCapturedSharePct}%", "SMS + Share vs Manual", Modifier.weight(1f))
                MetricBox("Avoidable Found", Money.format(app.monthAvoidableTotalPaise), "Marked Avoidable", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricBox("Last Quick-Add Speed", app.lastManualLogSeconds?.let { "${it}s" } ?: "< 4.0s", "Target: under 5s", Modifier.weight(1f))
                MetricBox("Reviews Finished", "${app.monthReviews.size}", "Completed months", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(72.dp))
    }
}

@Composable
fun CategoryBarRow(s: CategorySpendSummary, maxSpend: Long, lang: String) {
    val fraction = (s.currentPaise.toFloat() / maxSpend.toFloat()).coerceIn(0.03f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.category.emoji, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(s.category.localizedName(lang), style = MaterialTheme.typography.titleMedium)
                if (s.isOverspendFlagged) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CoralSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Flagged", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = Coral)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(s.currentPaise), style = MaterialTheme.typography.titleMedium)
                if (s.trailingAvgPaise > 0L) {
                    val d = s.deltaVsAvgPaise
                    Text(
                        text = (if (d >= 0) "+" else "") + Money.format(d) + " vs 3m avg",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (d > 0) Coral else EmeraldDark,
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Paper2)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (s.isOverspendFlagged) Coral else Color(s.category.colorHex))
            )
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Paper)
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = PrimaryAccent)
        Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = Muted)
    }
}

/**
 * Budgets Screen (Spec Page 8: "Per-category limits and targets from past reviews").
 */
@Composable
fun BudgetsTab(app: AppController) {
    val lang = app.language
    var editingCat by remember { mutableStateOf<CategoryEntity?>(null) }
    val budgetMap = app.budgets.associate { it.categoryId to it.monthlyLimitPaise }

    editingCat?.let { cat ->
        val existingPaise = budgetMap[cat.id] ?: 0L
        var limitRupeesInput by remember(cat.id) {
            mutableStateOf(if (existingPaise > 0) (existingPaise / 100L).toString() else "")
        }
        AlertDialog(
            onDismissRequest = { editingCat = null },
            title = { Text("${cat.emoji} ${cat.localizedName(lang)} Monthly Target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Set a monthly budget limit in Rupees (or 0 to remove). We'll check mid-month and in your Month-End Review.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    AppField(
                        label = "Monthly Limit (₹)",
                        value = limitRupeesInput,
                        onChange = { limitRupeesInput = it.filter { c -> c.isDigit() }.take(7) },
                        keyboard = KeyboardType.Number,
                        placeholder = "e.g., 5000",
                    )
                }
            },
            confirmButton = {
                BigButton("Save Target", emerald = true) {
                    val r = limitRupeesInput.toLongOrNull() ?: 0L
                    app.db.setBudget(cat.id, r * 100L)
                    app.reload()
                    editingCat = null
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCat = null }) { Text("Cancel", color = Muted) }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(t("Budgets"), style = MaterialTheme.typography.headlineLarge)
        Text(
            "Focus on 1 to 3 flexible categories rather than budgeting every rupee. Targets set during your Month-End Review appear here automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )

        AppCard(title = "Active Category Limits") {
            app.categories.filter { it.id <= 11L }.forEach { cat ->
                val spent = app.monthExpenses.filter { it.categoryId == cat.id }.sumOf { it.amountPaise }
                val limit = budgetMap[cat.id]
                val over = limit != null && spent > limit

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Paper)
                        .clickable { editingCat = cat }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cat.emoji, fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(cat.localizedName(lang), style = MaterialTheme.typography.titleMedium)
                        }
                        if (limit != null) {
                            Text(
                                "${Money.format(spent)} / ${Money.format(limit)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (over) Coral else EmeraldDark,
                            )
                        } else {
                            SmallPill("+ Set Target") { editingCat = cat }
                        }
                    }
                    if (limit != null && limit > 0L) {
                        val frac = (spent.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Paper2)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(frac)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(if (over) Coral else Emerald)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(72.dp))
    }
}
