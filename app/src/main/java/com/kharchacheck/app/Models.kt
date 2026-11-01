package com.kharchacheck.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class TxType(val labelEn: String, val labelHi: String) {
    EXPENSE("Expense", "खर्च"),
    INCOME("Income", "आय"),
    TRANSFER("Transfer", "ट्रांसफ़र"),
    REFUND("Refund", "रिफ़ंड"),
}

enum class PayMode(val labelEn: String, val labelHi: String) {
    UPI("UPI", "UPI"),
    CARD("Card", "कार्ड"),
    CASH("Cash", "नकद"),
    NET_BANKING("NetBanking", "नेटबैंकिंग"),
    ATM("ATM", "ATM"),
    OTHER("Other", "अन्य"),
}

enum class TxSource(val labelEn: String) {
    SMS("Auto SMS"),
    MANUAL("Manual"),
    SHARE("Shared SMS"),
}

enum class ConfidenceLevel(val score: Int) {
    HIGH(90),
    MEDIUM(65),
    LOW(35),
}

enum class ReviewState(val labelEn: String, val labelHi: String) {
    AUTO("Auto", "ऑटो"),
    NEEDS_REVIEW("Needs Review", "जाँच ज़रूरी"),
    CONFIRMED("Confirmed", "पक्का"),
}

enum class WorthTag(val labelEn: String, val labelHi: String) {
    NONE("Unrated", "बिना टैग"),
    NEEDED("Needed", "ज़रूरी था"),
    FINE("Fine", "ठीक था"),
    AVOIDABLE("Avoidable", "टाला जा सकता था"),
}

enum class AccountType(val labelEn: String) {
    BANK("Bank Account"),
    CARD("Credit / Debit Card"),
    WALLET("Wallet"),
    CASH("Cash"),
}

/**
 * Money formatting helper: Spec Page 6 states "Money is stored as integer paise."
 */
object Money {
    private val inrInt = NumberFormat.getIntegerInstance(Locale("en", "IN"))

    fun fromRupees(rupees: Double): Long = (rupees * 100.0).roundToLong()

    fun toRupees(paise: Long): Double = paise / 100.0

    fun format(paise: Long, showSign: Boolean = false, showPaiseIfNonZero: Boolean = false): String {
        val sign = when {
            paise < 0 -> "-"
            showSign && paise > 0 -> "+"
            else -> ""
        }
        val absPaise = abs(paise)
        val rupees = absPaise / 100
        val rem = absPaise % 100
        return if (showPaiseIfNonZero && rem > 0) {
            "$sign₹${inrInt.format(rupees)}.${"%02d".format(rem)}"
        } else {
            "$sign₹${inrInt.format(rupees)}"
        }
    }
}

/** Entity 1: Transaction (Spec Page 6) */
data class TransactionEntry(
    val id: Long = 0L,
    val amountPaise: Long,
    val type: TxType = TxType.EXPENSE,
    val epochMillis: Long = System.currentTimeMillis(),
    val timeZoneId: String = ZoneId.systemDefault().id,
    val accountId: Long = 1L,
    val mode: PayMode = PayMode.UPI,
    val merchantId: Long = 0L,
    val merchantName: String = "",
    val categoryId: Long = 1L,
    val note: String = "",
    val tags: String = "",
    val source: TxSource = TxSource.MANUAL,
    val smsId: String = "",
    val refNo: String = "",
    val confidence: Int = 100,
    val reviewState: ReviewState = ReviewState.CONFIRMED,
    val worth: WorthTag = WorthTag.NONE,
) {
    val zonedDateTime: ZonedDateTime
        get() = Instant.ofEpochMilli(epochMillis).atZone(runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault()))

    val localDate: LocalDate
        get() = zonedDateTime.toLocalDate()

    val yearMonth: YearMonth
        get() = YearMonth.from(localDate)

    val prettyTime: String
        get() = zonedDateTime.format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH))

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("amountPaise", amountPaise)
        .put("type", type.name)
        .put("epochMillis", epochMillis)
        .put("timeZoneId", timeZoneId)
        .put("accountId", accountId)
        .put("mode", mode.name)
        .put("merchantId", merchantId)
        .put("merchantName", merchantName)
        .put("categoryId", categoryId)
        .put("note", note)
        .put("tags", tags)
        .put("source", source.name)
        .put("smsId", smsId)
        .put("refNo", refNo)
        .put("confidence", confidence)
        .put("reviewState", reviewState.name)
        .put("worth", worth.name)

    companion object {
        fun fromJson(j: JSONObject) = TransactionEntry(
            id = j.optLong("id"),
            amountPaise = j.optLong("amountPaise"),
            type = runCatching { TxType.valueOf(j.optString("type", "EXPENSE")) }.getOrDefault(TxType.EXPENSE),
            epochMillis = j.optLong("epochMillis", System.currentTimeMillis()),
            timeZoneId = j.optString("timeZoneId", "Asia/Kolkata"),
            accountId = j.optLong("accountId", 1L),
            mode = runCatching { PayMode.valueOf(j.optString("mode", "UPI")) }.getOrDefault(PayMode.UPI),
            merchantId = j.optLong("merchantId", 0L),
            merchantName = j.optString("merchantName", ""),
            categoryId = j.optLong("categoryId", 1L),
            note = j.optString("note", ""),
            tags = j.optString("tags", ""),
            source = runCatching { TxSource.valueOf(j.optString("source", "MANUAL")) }.getOrDefault(TxSource.MANUAL),
            smsId = j.optString("smsId", ""),
            refNo = j.optString("refNo", ""),
            confidence = j.optInt("confidence", 100),
            reviewState = runCatching { ReviewState.valueOf(j.optString("reviewState", "CONFIRMED")) }.getOrDefault(ReviewState.CONFIRMED),
            worth = runCatching { WorthTag.valueOf(j.optString("worth", "NONE")) }.getOrDefault(WorthTag.NONE),
        )
    }
}

/** Entity 2: Account (Spec Page 6) */
data class AccountEntity(
    val id: Long,
    val name: String,
    val type: AccountType,
    val last4: String,
    val senderHeaders: String,
) {
    val displayTitle: String
        get() = if (last4.isNotBlank()) "$name ••$last4" else name

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("type", type.name)
        .put("last4", last4)
        .put("senderHeaders", senderHeaders)

    companion object {
        fun fromJson(j: JSONObject) = AccountEntity(
            id = j.optLong("id"),
            name = j.optString("name"),
            type = runCatching { AccountType.valueOf(j.optString("type", "BANK")) }.getOrDefault(AccountType.BANK),
            last4 = j.optString("last4", ""),
            senderHeaders = j.optString("senderHeaders", ""),
        )
    }
}

/** Entity 3: Category (Spec Page 6) */
data class CategoryEntity(
    val id: Long,
    val nameEn: String,
    val nameHi: String,
    val emoji: String,
    val colorHex: Long,
    val parentId: Long? = null,
    val isFixed: Boolean = false,
) {
    fun localizedName(lang: String): String = if (lang == "hi" && nameHi.isNotBlank()) nameHi else nameEn

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("nameEn", nameEn)
        .put("nameHi", nameHi)
        .put("emoji", emoji)
        .put("colorHex", colorHex)
        .put("isFixed", isFixed)

    companion object {
        fun fromJson(j: JSONObject) = CategoryEntity(
            id = j.optLong("id"),
            nameEn = j.optString("nameEn"),
            nameHi = j.optString("nameHi"),
            emoji = j.optString("emoji", "💸"),
            colorHex = j.optLong("colorHex", 0xFF10B981),
            isFixed = j.optBoolean("isFixed", false),
        )
    }
}

/** Entity 4: Merchant (Spec Page 6) */
data class MerchantEntity(
    val id: Long,
    val displayName: String,
    val aliases: String,
    val defaultCategoryId: Long,
)

/** Entity 5: Rule (Spec Page 6) */
data class RuleEntity(
    val id: Long = 0L,
    val matchType: String = "CONTAINS", // "EXACT" or "CONTAINS"
    val pattern: String,
    val categoryId: Long,
    val origin: String = "USER",        // "USER" or "LEARNED"
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("matchType", matchType)
        .put("pattern", pattern)
        .put("categoryId", categoryId)
        .put("origin", origin)

    companion object {
        fun fromJson(j: JSONObject) = RuleEntity(
            id = j.optLong("id"),
            matchType = j.optString("matchType", "CONTAINS"),
            pattern = j.optString("pattern"),
            categoryId = j.optLong("categoryId"),
            origin = j.optString("origin", "USER"),
        )
    }
}

/** Entity 6: Budget (Spec Page 6) */
data class BudgetEntity(
    val id: Long = 0L,
    val categoryId: Long,
    val monthlyLimitPaise: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("categoryId", categoryId)
        .put("monthlyLimitPaise", monthlyLimitPaise)

    companion object {
        fun fromJson(j: JSONObject) = BudgetEntity(
            id = j.optLong("id"),
            categoryId = j.optLong("categoryId"),
            monthlyLimitPaise = j.optLong("monthlyLimitPaise"),
        )
    }
}

/** Entity 7: Recurring (Spec Page 6) */
data class RecurringEntity(
    val id: Long = 0L,
    val merchantName: String,
    val categoryId: Long,
    val amountPaise: Long,
    val cadence: String = "MONTHLY",
    val nextDueDate: String,
    val occurrences: Int = 2,
)

/** Entity 8: MonthReview (Spec Page 7) */
data class MonthReviewEntity(
    val monthKey: String,          // e.g. "2026-10"
    val spentPaise: Long,
    val incomePaise: Long,
    val savedPaise: Long,
    val avoidableTotalPaise: Long,
    val intendedSavingPaise: Long,
    val targetsJson: String,       // JSON array of categoryIds or target pairs
    val completedAtMillis: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("monthKey", monthKey)
        .put("spentPaise", spentPaise)
        .put("incomePaise", incomePaise)
        .put("savedPaise", savedPaise)
        .put("avoidableTotalPaise", avoidableTotalPaise)
        .put("intendedSavingPaise", intendedSavingPaise)
        .put("targetsJson", targetsJson)
        .put("completedAtMillis", completedAtMillis)

    companion object {
        fun fromJson(j: JSONObject) = MonthReviewEntity(
            monthKey = j.optString("monthKey"),
            spentPaise = j.optLong("spentPaise"),
            incomePaise = j.optLong("incomePaise"),
            savedPaise = j.optLong("savedPaise"),
            avoidableTotalPaise = j.optLong("avoidableTotalPaise"),
            intendedSavingPaise = j.optLong("intendedSavingPaise"),
            targetsJson = j.optString("targetsJson", "[]"),
            completedAtMillis = j.optLong("completedAtMillis"),
        )
    }
}
