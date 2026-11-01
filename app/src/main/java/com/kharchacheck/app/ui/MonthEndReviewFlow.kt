package com.kharchacheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.kharchacheck.app.Money
import com.kharchacheck.app.WorthTag
import java.time.format.DateTimeFormatter
import java.util.Locale

private val STEP_TITLES = listOf(
    "1. Headline: Spent, Earned & Saved",
    "2. Where It Went (Fixed vs Flexible)",
    "3. Overspend Flags (Top 1–3)",
    "4. Small Leaks (< ₹200)",
    "5. Top 10 Merchants & Single Spends",
    "6. Swipe Review: Needed, Fine or Avoidable",
    "7. Weekday, Time & Payment Patterns",
    "8. One Change: Pick 1–3 Targets",
    "9. Save Intent & Export PDF",
)

/**
 * The Core Feature of Kharcha Check (Spec Pages 7-8):
 * A guided 9-step walk-through taking ~3 minutes at month-end, with neutral non-judgmental facts.
 */
@Composable
fun MonthEndReviewFlow(
    app: AppController,
    actions: PhoneActions,
    onClose: () -> Unit,
) {
    val lang = app.language
    val step = app.monthReviewStep.coerceIn(0, 8)
    val monthLabel = app.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))

    var selectedTargetIds by rememberSaveable {
        mutableStateOf(app.overspendFlaggedCategories.map { it.category.id }.take(2).toSet())
    }
    var intendedSaveRupees by rememberSaveable(app.monthAvoidableTotalPaise) {
        mutableStateOf(((app.monthAvoidableTotalPaise / 100L).takeIf { it > 0 } ?: 1500L).toString())
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Top Progress Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Month-End Review · $monthLabel", style = MaterialTheme.typography.labelMedium, color = PrimaryAccent)
                Text("Step ${step + 1} of 9", style = MaterialTheme.typography.titleMedium)
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CardBg)
                    .border(1.dp, PaperLine, CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
            }
        }

        // 9-segment progress bar
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(9) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= step) Emerald else PaperLine)
                        .clickable { app.monthReviewStep = i }
                )
            }
        }

        Text(STEP_TITLES[step], style = MaterialTheme.typography.headlineMedium)

        when (step) {
            // STEP 1: Headline
            0 -> {
                AppCard(
                    title = "Your $monthLabel Snapshot",
                    subtitle = "Saved = Income − Expenses (Transfers & CC bill payments excluded)",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ReviewStatPill("Earned", Money.format(app.monthIncomePaise), EmeraldDark, Modifier.weight(1f))
                        ReviewStatPill("Spent", Money.format(app.monthSpentPaise), TextMain, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ReviewStatPill("Net Saved", Money.format(app.monthSavedPaise), PrimaryAccent, Modifier.weight(1f))
                        ReviewStatPill("Savings Rate", "${app.monthSavingsRatePct}%", AmberInk, Modifier.weight(1f))
                    }
                    val avg3m = app.trailing3MonthsAvgSpentPaise
                    if (avg3m > 0L) {
                        val diff = app.monthSpentPaise - avg3m
                        Text(
                            text = if (diff <= 0L) {
                                "You spent ${Money.format(-diff)} less than your trailing 3-month average (${Money.format(avg3m)})."
                            } else {
                                "You spent ${Money.format(diff)} more than your trailing 3-month average (${Money.format(avg3m)})."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                        )
                    }
                }
            }

            // STEP 2: Where it went
            1 -> {
                val maxSpend = (app.categorySummaries.maxOfOrNull { it.currentPaise } ?: 1L).coerceAtLeast(1L)
                AppCard(
                    title = "Flexible Spend by Size",
                    subtitle = "Compared against your trailing 3-month average",
                ) {
                    app.categorySummaries.filter { !it.category.isFixed }.forEach { s ->
                        CategoryBarRow(s = s, maxSpend = maxSpend, lang = lang)
                    }
                }
                AppCard(title = "Fixed Monthly Costs") {
                    app.categorySummaries.filter { it.category.isFixed }.forEach { s ->
                        CategoryBarRow(s = s, maxSpend = maxSpend, lang = lang)
                    }
                }
            }

            // STEP 3: Overspend flags (max 3)
            2 -> {
                val flags = app.overspendFlaggedCategories
                AppCard(
                    title = "Up to 3 Categories Flagged",
                    subtitle = "Rule: Over budget, or > 1.2× trailing 3-month median and > ₹${app.prefs.overspendMinDeltaRupees} over it",
                ) {
                    if (flags.isEmpty()) {
                        Text("No categories crossed your overspend threshold this month.", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        flags.forEach { f ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CoralSoft.copy(alpha = 0.55f))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("${f.category.emoji} ${f.category.localizedName(lang)} — ${Money.format(f.currentPaise)}", style = MaterialTheme.typography.titleLarge, color = Color(0xFF881337))
                                Text(
                                    "3-month median: ${Money.format(f.trailingMedianPaise)} · Change: +${Money.format(f.currentPaise - f.trailingMedianPaise)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF9F1239),
                                )
                            }
                        }
                    }
                }
            }

            // STEP 4: Small leaks
            3 -> {
                val leaks = app.smallLeaksList
                val totalLeaks = leaks.sumOf { it.amountPaise }
                AppCard(
                    title = "${leaks.size} Small Payments Under ₹${app.prefs.smallLeakThresholdRupees}",
                    subtitle = "Nobody remembers 90 chai, cab, and quick-delivery top-ups — together they added up to ${Money.format(totalLeaks)}",
                ) {
                    app.smallLeaksByMerchant.forEach { g ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${g.merchantName} (${g.count} payments)", style = MaterialTheme.typography.bodyMedium)
                            Text(Money.format(g.totalPaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // STEP 5: Top 10 merchants & top 10 single spends
            4 -> {
                AppCard(title = "Top 10 Merchants This Month") {
                    app.top10Merchants.forEachIndexed { idx, (m, paise) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${idx + 1}. $m", style = MaterialTheme.typography.bodyMedium)
                            Text(Money.format(paise), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                AppCard(title = "Top 10 Single Payments") {
                    app.top10SinglePayments.forEach { tx ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${tx.merchantName} (${tx.prettyTime})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(Money.format(tx.amountPaise), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            // STEP 6: Swipe / Tap Review Cards (Needed, Fine, or Avoidable)
            5 -> {
                AppCard(
                    title = "Only You Know What Was Worth It",
                    subtitle = "Tag your biggest flexible payments: Needed, Fine, or Avoidable",
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Avoidable Total", style = MaterialTheme.typography.bodySmall, color = Muted)
                            Text(Money.format(app.monthAvoidableTotalPaise), style = MaterialTheme.typography.titleLarge, color = Coral)
                        }
                    }
                ) {
                    app.swipeReviewCandidates.forEach { tx ->
                        val cat = app.categoryMap[tx.categoryId]
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Paper)
                                .border(1.dp, PaperLine, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("${cat?.emoji ?: "💸"} ${tx.merchantName}", style = MaterialTheme.typography.titleMedium)
                                    Text("${tx.prettyTime} · ${tx.mode.labelEn}" + (if (tx.note.isNotBlank()) " · ${tx.note}" else ""), style = MaterialTheme.typography.bodySmall, color = Muted)
                                }
                                Text(Money.format(tx.amountPaise), style = MaterialTheme.typography.titleLarge)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(
                                    WorthTag.NEEDED to "✓ Needed",
                                    WorthTag.FINE to "👌 Fine",
                                    WorthTag.AVOIDABLE to "⚠️ Avoidable",
                                ).forEach { (w, label) ->
                                    val on = tx.worth == w
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    on && w == WorthTag.AVOIDABLE -> Coral
                                                    on && w == WorthTag.NEEDED -> Emerald
                                                    on -> Amber
                                                    else -> CardBg
                                                }
                                            )
                                            .border(1.dp, PaperLine, RoundedCornerShape(10.dp))
                                            .clickable { app.setWorth(tx.id, w) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (on && w == WorthTag.AVOIDABLE) Color.White else if (on) Navy else TextMain,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STEP 7: Patterns
            6 -> {
                AppCard(title = "Spending Patterns") {
                    Text("Time-of-Day Breakdown:", style = MaterialTheme.typography.labelMedium, color = Muted)
                    app.timeOfDaySpendPaise.forEach { (slot, paise) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(slot, style = MaterialTheme.typography.bodyMedium)
                            Text(Money.format(paise), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    HorizontalDivider(color = PaperLine)
                    Text("Payment Mode Split:", style = MaterialTheme.typography.labelMedium, color = Muted)
                    app.modeSplitPaise.forEach { (mode, paise) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(mode.labelEn, style = MaterialTheme.typography.bodyMedium)
                            Text(Money.format(paise), style = MaterialTheme.typography.titleMedium, color = PrimaryAccent)
                        }
                    }
                }
            }

            // STEP 8: One Change (Pick 1 to 3 targets for next month)
            7 -> {
                AppCard(
                    title = "Pick 1 to 3 Focus Categories for Next Month",
                    subtitle = "Each selected category becomes an automatic budget with a 15% trim",
                ) {
                    app.categorySummaries.filter { !it.category.isFixed }.forEach { s ->
                        val isPicked = s.category.id in selectedTargetIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isPicked) EmeraldSoft else Paper)
                                .border(1.5.dp, if (isPicked) Emerald else PaperLine, RoundedCornerShape(14.dp))
                                .clickable {
                                    selectedTargetIds = if (isPicked) {
                                        selectedTargetIds - s.category.id
                                    } else if (selectedTargetIds.size < 3) {
                                        selectedTargetIds + s.category.id
                                    } else selectedTargetIds
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${s.category.emoji} ${s.category.localizedName(lang)}", style = MaterialTheme.typography.titleMedium, color = if (isPicked) Navy else TextMain)
                                Text("This month: ${Money.format(s.currentPaise)} → Target: ${Money.format((s.currentPaise * 85) / 100)}", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                            Text(if (isPicked) "✓ Picked" else "Select", style = MaterialTheme.typography.labelLarge, color = if (isPicked) EmeraldDark else Muted)
                        }
                    }
                }
            }

            // STEP 9: Save Intent & Export PDF
            else -> {
                AppCard(
                    title = "Move Avoidable Spend to Savings",
                    subtitle = "Records your intent on-device; the app never touches or moves your money",
                ) {
                    Text(
                        "You marked ${Money.format(app.monthAvoidableTotalPaise)} as Avoidable this month.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Coral,
                    )
                    AppField(
                        label = "How much will you move to savings next month? (₹)",
                        value = intendedSaveRupees,
                        onChange = { intendedSaveRupees = it.filter { c -> c.isDigit() }.take(7) },
                        keyboard = KeyboardType.Number,
                    )

                    GhostButton(
                        text = "📄 Save Month-End Review as PDF",
                        onClick = { actions.onExportMonthPdf() },
                    )

                    BigButton(
                        text = "✓ Complete Month-End Review",
                        emerald = true,
                        onClick = {
                            app.completeMonthReview(
                                selectedTargetCatIds = selectedTargetIds,
                                intendedSavingRupees = intendedSaveRupees.toDoubleOrNull() ?: 0.0,
                            )
                        },
                    )
                }
            }
        }

        // Bottom Prev / Next Step Bar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (step > 0) {
                GhostButton(
                    text = "← Previous",
                    modifier = Modifier.weight(1f),
                    onClick = { app.monthReviewStep = step - 1 },
                )
            }
            if (step < 8) {
                BigButton(
                    text = "Next Step (${step + 2}/9) →",
                    emerald = true,
                    modifier = Modifier.weight(1f),
                    onClick = { app.monthReviewStep = step + 1 },
                )
            }
        }
    }
}

@Composable
private fun ReviewStatPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Paper)
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
    }
}
