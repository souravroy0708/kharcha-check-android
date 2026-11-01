package com.kharchacheck.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

enum class Tab { Home, Transactions, Insights, Budgets, Settings }

data class CategorySpendSummary(
    val category: CategoryEntity,
    val currentPaise: Long,
    val trailingAvgPaise: Long,
    val trailingMedianPaise: Long,
    val budgetPaise: Long?,
    val isOverspendFlagged: Boolean,
    val deltaVsAvgPaise: Long,
)

data class SmallLeakMerchantGroup(
    val merchantName: String,
    val count: Int,
    val totalPaise: Long,
)

/**
 * Reactive state holder and analytics engine for Kharcha Check.
 * Computes all Spec metrics 100% locally on the phone:
 * - Headline: Spent, earned, saved (`income - expenses`, transfers excluded), savings rate vs 3-month average
 * - Fixed vs Flexible category bars with change vs 3-month average
 * - Overspend flags: at most 3 categories (over budget OR > 1.2x trailing 3-month median and > ₹500 over it)
 * - Small leaks: count and total of payments under ₹200 (tunable in Settings), grouped by merchant
 * - Top 10 merchants & top 10 single payments
 * - Recurring costs: Rent, EMIs, and subscriptions spotted after >= 2 repeats
 * - Weekday and Time-of-Day strip + UPI vs Card vs Cash split
 * - Pilot metrics (Spec Page 11-12): Auto-captured share %, Average time-to-log manual entry, Avoidable spend found
 */
class AppController(private val ctx: Context, private val scope: CoroutineScope) {
    val prefs = Prefs(ctx)
    val db = LocalDb.get(ctx)

    var onboardingDone by mutableStateOf(prefs.onboardingDone)
    var unlocked by mutableStateOf(!prefs.appLock)
    var tab by mutableStateOf(Tab.Home)
    var language by mutableStateOf(prefs.language)
    var theme by mutableStateOf(prefs.theme)
    var hideInRecents by mutableStateOf(prefs.hideInRecents)

    // Selected month (defaults to current month)
    var selectedMonth by mutableStateOf(YearMonth.now())

    // Modal / full-screen overlays
    var quickAddOpen by mutableStateOf(false)
    var quickAddOpenedAtMillis by mutableStateOf(0L)
    var lastManualLogSeconds by mutableStateOf<Double?>(null)
    var reviewTrayOpen by mutableStateOf(false)
    var monthReviewOpen by mutableStateOf(false)
    var monthReviewStep by mutableIntStateOf(0) // 0..8 (Steps 1..9 of Spec Page 7-8)
    var shareSmsDialogOpen by mutableStateOf(false)
    var sharedSmsPrefill by mutableStateOf("")

    // Rule learning prompt ("Always do this for this merchant" - Spec Page 5)
    var rulePromptMerchant by mutableStateOf<String?>(null)
    var rulePromptCategoryId by mutableStateOf<Long?>(null)

    // Reactive DB state
    var categories by mutableStateOf<List<CategoryEntity>>(db.categories())
    var accounts by mutableStateOf<List<AccountEntity>>(db.accounts())
    var transactions by mutableStateOf<List<TransactionEntry>>(emptyList())
    var rules by mutableStateOf<List<RuleEntity>>(emptyList())
    var budgets by mutableStateOf<List<BudgetEntity>>(emptyList())
    var monthReviews by mutableStateOf<Map<String, MonthReviewEntity>>(emptyMap())
    var unrecognisedSms by mutableStateOf<List<Pair<String, String>>>(emptyList())

    // Filters on Transactions tab
    var searchQuery by mutableStateOf("")
    var filterCategoryId by mutableStateOf<Long?>(null)
    var filterMode by mutableStateOf<PayMode?>(null)
    var filterAccountId by mutableStateOf<Long?>(null)

    // Status & Toast
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var smsGranted by mutableStateOf(hasSmsPermission())

    init {
        if (!prefs.demoLoaded) {
            db.seedRealisticSampleData()
            prefs.demoLoaded = true
        }
        reload()
    }

    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    fun reload() {
        smsGranted = hasSmsPermission()
        categories = db.categories()
        accounts = db.accounts()
        transactions = db.allTransactions()
        rules = db.rules()
        budgets = db.budgets()
        monthReviews = db.monthReviews()
        unrecognisedSms = db.unrecognisedSmsList()
    }

    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    // ---------- Derived Analytics for Selected Month ----------

    val categoryMap: Map<Long, CategoryEntity>
        get() = categories.associateBy { it.id }

    val accountMap: Map<Long, AccountEntity>
        get() = accounts.associateBy { it.id }

    val reviewTrayItems: List<TransactionEntry>
        get() = transactions.filter { it.reviewState == ReviewState.NEEDS_REVIEW }

    val monthTransactions: List<TransactionEntry>
        get() = transactions.filter { it.yearMonth == selectedMonth }

    val monthExpenses: List<TransactionEntry>
        get() = monthTransactions.filter { it.type == TxType.EXPENSE }

    val monthIncomePaise: Long
        get() = monthTransactions.filter { it.type == TxType.INCOME || it.type == TxType.REFUND }.sumOf { it.amountPaise }

    val monthSpentPaise: Long
        get() = monthExpenses.sumOf { it.amountPaise }

    val monthSavedPaise: Long
        get() = monthIncomePaise - monthSpentPaise

    val monthSavingsRatePct: Int
        get() = if (monthIncomePaise > 0L) ((monthSavedPaise * 100L) / monthIncomePaise).toInt().coerceIn(-100, 100) else 0

    val trailing3MonthsAvgSpentPaise: Long
        get() {
            val m1 = selectedMonth.minusMonths(1)
            val m2 = selectedMonth.minusMonths(2)
            val m3 = selectedMonth.minusMonths(3)
            val totals = listOf(m1, m2, m3).map { ym ->
                transactions.filter { it.yearMonth == ym && it.type == TxType.EXPENSE }.sumOf { it.amountPaise }
            }.filter { it > 0L }
            return if (totals.isEmpty()) 0L else totals.sum() / totals.size
        }

    /** Category breakdown sorted by size, with trailing 3-month average/median and Overspend flag (Spec Page 7). */
    val categorySummaries: List<CategorySpendSummary>
        get() {
            val m1 = selectedMonth.minusMonths(1)
            val m2 = selectedMonth.minusMonths(2)
            val m3 = selectedMonth.minusMonths(3)
            val budgetLookup = budgets.associate { it.categoryId to it.monthlyLimitPaise }
            val minOverPaise = prefs.overspendMinDeltaRupees * 100L

            val raw = categories.filter { it.id != 12L && it.id != 13L }.mapNotNull { cat ->
                val curr = monthExpenses.filter { it.categoryId == cat.id }.sumOf { it.amountPaise }
                val prevSpends = listOf(m1, m2, m3).map { ym ->
                    transactions.filter { it.yearMonth == ym && it.type == TxType.EXPENSE && it.categoryId == cat.id }.sumOf { it.amountPaise }
                }.filter { it > 0L }.sorted()

                val avg = if (prevSpends.isEmpty()) 0L else prevSpends.sum() / prevSpends.size
                val median = when {
                    prevSpends.isEmpty() -> 0L
                    prevSpends.size % 2 == 1 -> prevSpends[prevSpends.size / 2]
                    else -> (prevSpends[prevSpends.size / 2 - 1] + prevSpends[prevSpends.size / 2]) / 2L
                }
                val bLimit = budgetLookup[cat.id]
                if (curr == 0L && bLimit == null && avg == 0L) return@mapNotNull null

                val overBudget = bLimit != null && curr > bLimit
                val overMedianRule = !cat.isFixed && median > 0L && curr > (median * 1.2).roundToLong() && (curr - median) > minOverPaise
                CategorySpendSummary(
                    category = cat,
                    currentPaise = curr,
                    trailingAvgPaise = avg,
                    trailingMedianPaise = median,
                    budgetPaise = bLimit,
                    isOverspendFlagged = overBudget || overMedianRule,
                    deltaVsAvgPaise = curr - avg,
                )
            }.sortedByDescending { it.currentPaise }

            // Spec Page 2 & 7: "Overspend flags show at most three categories"
            var flagCount = 0
            return raw.map { item ->
                if (item.isOverspendFlagged && flagCount < 3) {
                    flagCount++
                    item
                } else {
                    item.copy(isOverspendFlagged = false)
                }
            }
        }

    val overspendFlaggedCategories: List<CategorySpendSummary>
        get() = categorySummaries.filter { it.isOverspendFlagged }.take(3)

    /** Step 4 of Month-End Review: Small Leaks (< ₹200 threshold, grouped by merchant — Spec Page 2 & 7). */
    val smallLeaksList: List<TransactionEntry>
        get() {
            val thresholdPaise = prefs.smallLeakThresholdRupees * 100L
            return monthExpenses.filter { it.amountPaise <= thresholdPaise }
        }

    val smallLeaksByMerchant: List<SmallLeakMerchantGroup>
        get() = smallLeaksList
            .groupBy { it.merchantName }
            .map { (m, list) -> SmallLeakMerchantGroup(m, list.size, list.sumOf { it.amountPaise }) }
            .sortedByDescending { it.totalPaise }

    /** Step 5 of Month-End Review: Top 10 merchants & top 10 single payments (Spec Page 7). */
    val top10Merchants: List<Pair<String, Long>>
        get() = monthExpenses
            .groupBy { it.merchantName }
            .map { (m, list) -> m to list.sumOf { it.amountPaise } }
            .sortedByDescending { it.second }
            .take(10)

    val top10SinglePayments: List<TransactionEntry>
        get() = monthExpenses.sortedByDescending { it.amountPaise }.take(10)

    /** Step 6 of Month-End Review: Biggest flexible payments for Swipe/Tap cards (Needed, Fine, Avoidable). */
    val swipeReviewCandidates: List<TransactionEntry>
        get() {
            val fixedCatIds = categories.filter { it.isFixed }.map { it.id }.toSet()
            return monthExpenses
                .filter { it.categoryId !in fixedCatIds }
                .sortedByDescending { it.amountPaise }
                .take(12)
        }

    val monthAvoidableTotalPaise: Long
        get() = monthExpenses.filter { it.worth == WorthTag.AVOIDABLE }.sumOf { it.amountPaise }

    /** Step 7 of Month-End Review: Weekday strip (Mon..Sun), Time-of-Day strip, and UPI vs Card vs Cash split. */
    val weekdaySpendPaise: List<Pair<DayOfWeek, Long>>
        get() = DayOfWeek.entries.map { dow ->
            dow to monthExpenses.filter { it.localDate.dayOfWeek == dow }.sumOf { it.amountPaise }
        }

    val timeOfDaySpendPaise: List<Pair<String, Long>>
        get() {
            // Morning (6-11), Afternoon (12-16), Evening (17-20), Late Night (21-5)
            var morning = 0L
            var afternoon = 0L
            var evening = 0L
            var night = 0L
            monthExpenses.forEach { tx ->
                when (tx.zonedDateTime.hour) {
                    in 6..11 -> morning += tx.amountPaise
                    in 12..16 -> afternoon += tx.amountPaise
                    in 17..20 -> evening += tx.amountPaise
                    else -> night += tx.amountPaise
                }
            }
            return listOf(
                "Morning (6a–12p)" to morning,
                "Afternoon (12p–5p)" to afternoon,
                "Evening (5p–9p)" to evening,
                "Night (9p–6a)" to night,
            )
        }

    val modeSplitPaise: List<Pair<PayMode, Long>>
        get() = PayMode.entries.mapNotNull { m ->
            val sum = monthExpenses.filter { it.mode == m }.sumOf { it.amountPaise }
            if (sum > 0L) m to sum else null
        }.sortedByDescending { it.second }

    /** Recurring fixed costs spotted after >= 2 repeats across history (Spec Page 4 & 6). */
    val detectedRecurringCosts: List<RecurringEntity>
        get() {
            val allExp = transactions.filter { it.type == TxType.EXPENSE }
            return allExp
                .groupBy { it.merchantName }
                .mapNotNull { (merchant, list) ->
                    val distinctMonths = list.map { it.yearMonth }.distinct()
                    val cat = categoryMap[list.first().categoryId]
                    if (distinctMonths.size >= 2 && (cat?.isFixed == true || list.first().amountPaise >= 40000L)) {
                        val latest = list.maxBy { it.epochMillis }
                        val nextDate = latest.localDate.plusMonths(1).toString()
                        RecurringEntity(
                            id = latest.id,
                            merchantName = merchant,
                            categoryId = latest.categoryId,
                            amountPaise = latest.amountPaise,
                            cadence = "MONTHLY",
                            nextDueDate = nextDate,
                            occurrences = distinctMonths.size,
                        )
                    } else null
                }.sortedByDescending { it.amountPaise }
        }

    /** On-device Pilot Metrics (Spec Pages 11-12: Auto-captured share %, Avoidable spend found, Reviews completed). */
    val autoCapturedSharePct: Int
        get() {
            if (transactions.isEmpty()) return 0
            val autoCount = transactions.count { it.source == TxSource.SMS || it.source == TxSource.SHARE }
            return (autoCount * 100) / transactions.size
        }

    // ---------- Mutating Actions ----------

    fun openQuickAdd() {
        quickAddOpenedAtMillis = System.currentTimeMillis()
        quickAddOpen = true
    }

    fun addManualEntry(
        amountPaise: Long,
        type: TxType,
        categoryId: Long,
        accountId: Long,
        mode: PayMode,
        merchantName: String,
        note: String,
    ) {
        val elapsedSec = if (quickAddOpenedAtMillis > 0L) {
            ((System.currentTimeMillis() - quickAddOpenedAtMillis) / 100.0).roundToLong() / 10.0
        } else null
        lastManualLogSeconds = elapsedSec
        prefs.lastUsedAccountId = accountId
        prefs.lastUsedMode = mode.name

        val cat = categoryMap[categoryId]
        val finalMerchant = merchantName.trim().ifBlank { cat?.nameEn ?: "Quick Spend" }
        db.insertOrUpdateTransaction(
            TransactionEntry(
                amountPaise = amountPaise,
                type = type,
                epochMillis = System.currentTimeMillis(),
                accountId = accountId,
                mode = mode,
                merchantName = finalMerchant,
                categoryId = categoryId,
                note = note,
                source = TxSource.MANUAL,
                confidence = 100,
                reviewState = ReviewState.CONFIRMED,
            )
        )
        quickAddOpen = false
        reload()
        message = "Saved ${Money.format(amountPaise)}" + (elapsedSec?.let { " in ${it}s ✓" } ?: " ✓")
    }

    fun confirmReviewItem(tx: TransactionEntry, newCategoryId: Long, newMerchantName: String) {
        val changedCategory = tx.categoryId != newCategoryId
        db.updateTransactionReview(tx.id, newCategoryId, newMerchantName, ReviewState.CONFIRMED)
        reload()
        if (changedCategory && newMerchantName.isNotBlank() && newMerchantName != "Unknown Merchant") {
            rulePromptMerchant = newMerchantName
            rulePromptCategoryId = newCategoryId
        } else {
            message = "Confirmed ${newMerchantName} ✓"
        }
    }

    fun acceptMerchantRulePrompt() {
        val m = rulePromptMerchant
        val c = rulePromptCategoryId
        if (!m.isNullOrBlank() && c != null) {
            db.saveRule(m, c, "LEARNED")
            reload()
            message = "Rule saved: Always categorise '$m' under ${categoryMap[c]?.nameEn ?: "selected category"} ✓"
        }
        rulePromptMerchant = null
        rulePromptCategoryId = null
    }

    fun dismissMerchantRulePrompt() {
        rulePromptMerchant = null
        rulePromptCategoryId = null
    }

    fun parseSharedOrPastedSms(rawText: String, senderHeader: String = "SHARED-SMS") {
        val outcome = SmsParser.parse(
            sender = senderHeader,
            body = rawText,
            epochMillis = System.currentTimeMillis(),
            smsId = "share_${System.currentTimeMillis()}",
            userRules = rules,
            existingRecent = transactions.take(15),
            allowAnySenderForShare = true,
        )
        when (outcome) {
            is SmsParser.ParseOutcome.Matched -> {
                val p = outcome.parsed
                val acctId = db.resolveAccountForSms(p.accountLast4, p.bankHeader)
                db.insertOrUpdateTransaction(
                    TransactionEntry(
                        amountPaise = p.amountPaise,
                        type = p.type,
                        epochMillis = p.epochMillis,
                        accountId = acctId,
                        mode = p.mode,
                        merchantName = p.normalizedMerchant,
                        categoryId = p.categoryId,
                        source = TxSource.SHARE,
                        smsId = p.smsId,
                        refNo = p.refNo,
                        confidence = p.confidence.score,
                        reviewState = p.reviewState,
                    )
                )
                reload()
                message = "Parsed ${p.type.labelEn}: ${Money.format(p.amountPaise)} at ${p.normalizedMerchant} ✓"
            }
            is SmsParser.ParseOutcome.Ignored -> {
                message = "Ignored message: ${outcome.reason}"
            }
            is SmsParser.ParseOutcome.UnrecognisedBankSms -> {
                db.logUnrecognisedSms(outcome.sender, outcome.maskedTemplate)
                reload()
                message = "Could not extract amount/direction. Logged masked template in Settings."
            }
        }
    }

    fun runSmsBackfill(days: Int) = launch {
        busy = true
        val res = SmsBackfill.run(ctx, days)
        prefs.lastBackfillDays = days
        reload()
        busy = false
        message = "Scanned ${res.scanned} SMS (${days}d): ${res.imported} transactions added (${res.reviewTrayCount} in Review tray)."
    }

    fun setWorth(txId: Long, worth: WorthTag) {
        db.updateTransactionWorth(txId, worth)
        reload()
    }

    fun completeMonthReview(selectedTargetCatIds: Set<Long>, intendedSavingRupees: Double) {
        val targetsArr = JSONArray()
        // Each selected target becomes a budget for next month (10% trim from current spend or trailing avg)
        selectedTargetCatIds.take(3).forEach { catId ->
            targetsArr.put(catId)
            val summary = categorySummaries.firstOrNull { it.category.id == catId }
            val currentOrAvg = summary?.currentPaise?.takeIf { it > 0 } ?: summary?.trailingAvgPaise ?: 300000L
            val suggestedLimit = ((currentOrAvg * 0.85).roundToLong() / 10000L) * 10000L // rounded to nearest ₹100
            db.setBudget(catId, suggestedLimit.coerceAtLeast(50000L))
        }
        val review = MonthReviewEntity(
            monthKey = selectedMonth.toString(),
            spentPaise = monthSpentPaise,
            incomePaise = monthIncomePaise,
            savedPaise = monthSavedPaise,
            avoidableTotalPaise = monthAvoidableTotalPaise,
            intendedSavingPaise = Money.fromRupees(intendedSavingRupees),
            targetsJson = targetsArr.toString(),
            completedAtMillis = System.currentTimeMillis(),
        )
        db.saveMonthReview(review)
        reload()
        monthReviewOpen = false
        monthReviewStep = 0
        message = "Month-end review completed! Next month's budget targets are set ✓"
    }

    fun exportEncryptedBackup(outputStream: OutputStream, passphrase: String) = launch {
        try {
            val bytes = withContext(Dispatchers.IO) {
                val json = db.exportFullJson()
                CryptoVault.encryptBackupWithPassphrase(json, passphrase)
            }
            withContext(Dispatchers.IO) {
                outputStream.use { it.write(bytes) }
            }
            message = "Encrypted backup (.kcbak) saved to your chosen folder ✓"
        } catch (e: Exception) {
            message = "Backup failed: ${e.message}"
        }
    }

    fun importEncryptedBackup(inputStream: InputStream, passphrase: String) = launch {
        try {
            val count = withContext(Dispatchers.IO) {
                val raw = inputStream.use { it.readBytes() }
                val json = CryptoVault.decryptBackupWithPassphrase(raw, passphrase)
                db.importFullJson(json)
            }
            reload()
            message = "Restored $count transactions from encrypted backup ✓"
        } catch (_: Exception) {
            message = "Could not decrypt backup. Check your passphrase and try again."
        }
    }

    fun deleteAllData() {
        db.wipeAll()
        prefs.wipeAll()
        onboardingDone = false
        unlocked = true
        reload()
        message = "All data wiped from this phone."
    }
}
