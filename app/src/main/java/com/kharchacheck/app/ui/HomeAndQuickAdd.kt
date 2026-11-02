package com.kharchacheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharchacheck.app.AppController
import com.kharchacheck.app.Money
import com.kharchacheck.app.PayMode
import com.kharchacheck.app.SmsParser
import com.kharchacheck.app.Tab
import com.kharchacheck.app.TxType
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeTab(app: AppController, actions: PhoneActions) {
    val lang = app.language
    val monthLabel = remember(app.selectedMonth) {
        app.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    }
    val spent = app.monthSpentPaise
    val income = app.monthIncomePaise
    val saved = app.monthSavedPaise
    val avg3m = app.trailing3MonthsAvgSpentPaise
    val reviewTrayCount = app.reviewTrayItems.size
    val smallLeaks = app.smallLeaksList
    val smallLeaksTotal = smallLeaks.sumOf { it.amountPaise }
    val flags = app.overspendFlaggedCategories

    if (app.shareSmsDialogOpen) {
        PasteBankSmsDialog(
            initialText = app.sharedSmsPrefill,
            onParse = { raw ->
                app.shareSmsDialogOpen = false
                app.parseSharedOrPastedSms(raw, "AD-HDFCBK")
            },
            onDismiss = { app.shareSmsDialogOpen = false },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState()),
    ) {
        // 1. Dark Slate Financial Hero (Month Selector + Spent + Income + Saved + 3-Month Avg Comparison)
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Navy, Navy2)))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Month switcher bar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Navy3)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "Previous month",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { app.selectedMonth = app.selectedMonth.minusMonths(1) },
                    )
                    Text(
                        monthLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Next month",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { app.selectedMonth = app.selectedMonth.plusMonths(1) },
                    )
                }

                SmallPill("📋 Paste / Test SMS", emerald = true) {
                    app.sharedSmsPrefill = SmsParser.GOLDEN_SAMPLES.first().body
                    app.shareSmsDialogOpen = true
                }
            }

            // Main Spent Headline
            Column {
                Text(t("Spent this month"), color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                Text(Money.format(spent), color = Color.White, style = MaterialTheme.typography.displayLarge)
                if (avg3m > 0L) {
                    val diff = spent - avg3m
                    val diffText = if (diff <= 0L) {
                        "${Money.format(-diff)} below your 3-month average (${Money.format(avg3m)})"
                    } else {
                        "${Money.format(diff)} above your 3-month average (${Money.format(avg3m)})"
                    }
                    Text(
                        diffText,
                        color = if (diff <= 0L) Emerald else Amber,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Income, Saved, and Small Leaks Tiles
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HeroStatTile(
                    label = t("Income"),
                    value = Money.format(income),
                    sub = "Salary & credits",
                    dotColor = Emerald,
                    modifier = Modifier.weight(1f),
                )
                HeroStatTile(
                    label = t("Saved"),
                    value = Money.format(saved),
                    sub = "Savings rate: ${app.monthSavingsRatePct}%",
                    dotColor = if (saved >= 0) Emerald else Coral,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 2. Main Sheet Content
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Core Feature Banner: Guided 3-Minute Month-End Review (Spec Page 1 & 7)
            val completedReview = app.monthReviews[app.selectedMonth.toString()]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.horizontalGradient(listOf(Navy, Color(0xFF0F766E))))
                    .clickable {
                        app.monthReviewStep = 0
                        app.monthReviewOpen = true
                    }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Emerald),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Navy, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (completedReview != null) "Month-End Review Done ✓ (Re-open)" else t("Start 3-Min Month-End Review"),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "9 steps: where it went, small leaks, swipe big spends (Needed / Fine / Avoidable), and pick 1 change.",
                        color = MutedOnDark,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("→", color = Emerald, style = MaterialTheme.typography.headlineMedium)
            }

            // Review Tray Banner (Doubtful auto-detected entries needing a 1-tap check — Spec Page 4 & 8)
            if (reviewTrayCount > 0) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(AmberSoft)
                        .border(1.dp, Amber, RoundedCornerShape(18.dp))
                        .clickable { app.reviewTrayOpen = true }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlaylistAddCheck, contentDescription = null, tint = AmberInk, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "$reviewTrayCount auto-captured spend${if (reviewTrayCount == 1) "" else "s"} in Review Tray",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberInk,
                        )
                        Text(
                            "One tap to confirm category or teach a permanent merchant rule.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberInk,
                        )
                    }
                    SmallPill("Review →", amber = true) { app.reviewTrayOpen = true }
                }
            }

            // Overspend Flags (At most 3 categories — Spec Page 2 & 7)
            if (flags.isNotEmpty()) {
                AppCard(
                    title = t("Overspend Flags (Max 3)"),
                    subtitle = "Over budget or > 1.2× your 3-month median",
                ) {
                    flags.forEach { f ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CoralSoft.copy(alpha = 0.55f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(f.category.emoji, fontSize = 22.sp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(f.category.localizedName(lang), style = MaterialTheme.typography.titleMedium, color = Color(0xFF881337))
                                    val detail = if (f.budgetPaise != null && f.currentPaise > f.budgetPaise) {
                                        "Budget ${Money.format(f.budgetPaise)} · Over by ${Money.format(f.currentPaise - f.budgetPaise)}"
                                    } else {
                                        "3-mo median ${Money.format(f.trailingMedianPaise)} · +${Money.format(f.currentPaise - f.trailingMedianPaise)}"
                                    }
                                    Text(detail, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9F1239))
                                }
                            }
                            Text(Money.format(f.currentPaise), style = MaterialTheme.typography.titleLarge, color = Color(0xFF881337))
                        }
                    }
                }
            }

            // "Small Leaks" Card (Payments under ₹200 — Spec Page 2 & 7)
            AppCard(
                title = "☕ ${t("Small Leaks (< ₹200)")}",
                subtitle = "${smallLeaks.size} small payments under ₹${app.prefs.smallLeakThresholdRupees} add up quietly",
                trailing = {
                    Text(Money.format(smallLeaksTotal), style = MaterialTheme.typography.headlineMedium, color = Amber)
                }
            ) {
                app.smallLeaksByMerchant.take(4).forEach { group ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${group.merchantName} (${group.count}×)", style = MaterialTheme.typography.bodyMedium)
                        Text(Money.format(group.totalPaise), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Recent / Today's Transactions
            AppCard(
                title = t("Today & Recent Spends"),
                trailing = {
                    SmallPill("All (${app.monthTransactions.size}) →") { app.tab = Tab.Transactions }
                }
            ) {
                app.monthTransactions.take(8).forEach { tx ->
                    TransactionRowItem(
                        tx = tx,
                        category = app.categoryMap[tx.categoryId],
                        account = app.accountMap[tx.accountId],
                        lang = lang,
                        onClick = { app.tab = Tab.Transactions },
                    )
                }
            }

            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun HeroStatTile(
    label: String,
    value: String,
    sub: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Navy2)
            .border(1.dp, NavyLine, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(6.dp))
            Text(label, color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        }
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 2.dp))
        Text(sub, color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Under-5-Second Numpad Quick-Add Screen (Spec Page 1, 4 & 8:
 * "Numpad, category chip, done; defaults to now and the last used account").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddScreen(app: AppController, onClose: () -> Unit) {
    val lang = app.language
    var amountDigits by rememberSaveable { mutableStateOf("") }
    var txType by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    var selectedCategoryId by rememberSaveable { mutableLongStateOf(1L) }
    var selectedAccountId by rememberSaveable { mutableLongStateOf(app.prefs.lastUsedAccountId) }
    var selectedMode by rememberSaveable {
        mutableStateOf(runCatching { PayMode.valueOf(app.prefs.lastUsedMode) }.getOrDefault(PayMode.UPI))
    }
    var merchantInput by rememberSaveable { mutableStateOf("") }
    var noteInput by rememberSaveable { mutableStateOf("") }

    val amountRupees = amountDigits.toDoubleOrNull() ?: 0.0
    val amountPaise = Money.fromRupees(amountRupees)

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CardBg)
                    .border(1.dp, PaperLine, RoundedCornerShape(50))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Cancel"), style = MaterialTheme.typography.labelLarge)
            }

            // Expense / Income / Transfer toggle
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Paper2).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER).forEach { t ->
                    val on = txType == t
                    Text(
                        t.labelEn,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) Color.White else TextMain,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) Navy else Color.Transparent)
                            .clickable {
                                txType = t
                                if (t == TxType.INCOME) selectedCategoryId = 12L
                                else if (t == TxType.TRANSFER) selectedCategoryId = 13L
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Giant Amount Display
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Navy)
                .padding(vertical = 18.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "₹" + amountDigits.ifBlank { "0" },
                color = Emerald,
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = "Defaults to Now · ${app.accountMap[selectedAccountId]?.displayTitle ?: "HDFC Bank"} · ${selectedMode.labelEn}",
                color = MutedOnDark,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // 1-Tap Daily Presets for Instant Logging
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val presets = listOf(
                Triple("☕ Chai ₹20", "20", 1L to "Chai / Tea"),
                Triple("🛵 Auto ₹50", "50", 3L to "Auto Rickshaw"),
                Triple("🥛 Milk ₹66", "66", 2L to "Mother Dairy"),
                Triple("🛒 Blinkit ₹250", "250", 2L to "Blinkit"),
                Triple("🍔 Swiggy ₹300", "300", 1L to "Swiggy"),
                Triple("⛽ Petrol ₹500", "500", 3L to "Petrol Pump"),
            )
            presets.forEach { (label, amt, catInfo) ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(1.dp, Emerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable {
                            amountDigits = amt
                            selectedCategoryId = catInfo.first
                            merchantInput = catInfo.second
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Emerald, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Paste Payment Text Button
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardBg)
                .border(1.dp, PaperLine, RoundedCornerShape(14.dp))
                .clickable { app.shareSmsDialogOpen = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📋", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text("Paste Copied Payment / UPI Text", style = MaterialTheme.typography.bodyMedium, color = TextMain)
            }
            Text("Auto-fills in 1 tap →", style = MaterialTheme.typography.labelSmall, color = Emerald)
        }

        // Category Chips (1 tap)
        Text("Tap Category:", style = MaterialTheme.typography.labelMedium, color = Muted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            app.categories.filter { it.id <= 12L }.forEach { cat ->
                val on = selectedCategoryId == cat.id
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Emerald else CardBg)
                        .border(1.dp, if (on) Emerald else PaperLine, RoundedCornerShape(50))
                        .clickable {
                            selectedCategoryId = cat.id
                            if (amountPaise > 0L && merchantInput.isBlank()) {
                                // 1-tap instant save when amount is already typed!
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cat.emoji, fontSize = 14.sp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        cat.localizedName(lang),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) Navy else TextMain,
                    )
                }
            }
        }

        // Payment Mode + Account row
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PayMode.entries.forEach { m ->
                val on = selectedMode == m
                SmallPill(m.labelEn, emerald = on) { selectedMode = m }
            }
            app.accounts.forEach { a ->
                val on = selectedAccountId == a.id
                SmallPill(a.displayTitle, amber = on) { selectedAccountId = a.id }
            }
        }

        // Optional Merchant & Note row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(
                label = "Merchant (Optional)",
                value = merchantInput,
                onChange = { merchantInput = it.take(40) },
                placeholder = "e.g., Chai / Auto",
                modifier = Modifier.weight(1f),
            )
            AppField(
                label = "Note (Optional)",
                value = noteInput,
                onChange = { noteInput = it.take(60) },
                placeholder = "With friends",
                modifier = Modifier.weight(1f),
            )
        }

        // Fast Custom Numpad
        val padRows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "⌫"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            padRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardBg)
                                .border(1.dp, PaperLine, RoundedCornerShape(14.dp))
                                .clickable {
                                    when (key) {
                                        "⌫" -> amountDigits = amountDigits.dropLast(1)
                                        "." -> if (!amountDigits.contains(".") && amountDigits.length < 8) {
                                            amountDigits = if (amountDigits.isEmpty()) "0." else "$amountDigits."
                                        }
                                        else -> if (amountDigits.length < 8) amountDigits += key
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(key, style = MaterialTheme.typography.headlineMedium, color = TextMain)
                        }
                    }
                }
            }
        }

        BigButton(
            text = "✓ Save ${Money.format(amountPaise)}",
            emerald = true,
            enabled = amountPaise > 0L,
            onClick = {
                app.addManualEntry(
                    amountPaise = amountPaise,
                    type = txType,
                    categoryId = selectedCategoryId,
                    accountId = selectedAccountId,
                    mode = selectedMode,
                    merchantName = merchantInput,
                    note = noteInput,
                )
            },
        )
    }
}

@Composable
fun PasteBankSmsDialog(
    initialText: String,
    onParse: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var smsText by remember(initialText) { mutableStateOf(initialText) }
    var sampleIdx by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share-to-App / Test Bank SMS Parser", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Paste any Indian bank debit/credit SMS (or cycle through the bundled Golden Set samples) to test the 9-step on-device parser:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                SmallPill("Cycle Golden Sample (${SmsParser.GOLDEN_SAMPLES[sampleIdx].bank})", amber = true) {
                    sampleIdx = (sampleIdx + 1) % SmsParser.GOLDEN_SAMPLES.size
                    smsText = SmsParser.GOLDEN_SAMPLES[sampleIdx].body
                }
                AppField(
                    label = "Bank SMS Text",
                    value = smsText,
                    onChange = { smsText = it },
                    singleLine = false,
                )
            }
        },
        confirmButton = {
            BigButton("Parse On-Device & Add", emerald = true) {
                onParse(smsText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) }
        },
    )
}
