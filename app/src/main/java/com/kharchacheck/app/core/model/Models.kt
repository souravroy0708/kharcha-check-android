package com.kharchacheck.app.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/** What kind of money movement this is. Decides which categories make sense and how reports count it. */
enum class TxType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    INVEST("Save & Invest"),
    TRANSFER("Transfer"),
}

/**
 * Every category sits in one bucket. Buckets power the 50/30/20 view and the "what can I cut" story.
 * NEED = essentials, WANT = nice to have, LEAK = easiest to cut, INVEST = paying your future self.
 */
enum class Bucket(val label: String, val blurb: String, val type: TxType) {
    NEED("Needs", "Essentials you can't skip", TxType.EXPENSE),
    WANT("Wants", "Nice to have, worth planning", TxType.EXPENSE),
    LEAK("Leaks", "Easiest money to cut", TxType.EXPENSE),
    INVEST("Save & Invest", "Paying your future self", TxType.INVEST),
    INCOME("Income", "Money coming in", TxType.INCOME),
    TRANSFER("Transfers", "Moving your own money, not spending", TxType.TRANSFER);

    val isSpend: Boolean get() = this == NEED || this == WANT || this == LEAK

    companion object {
        fun forType(type: TxType): List<Bucket> = entries.filter { it.type == type }
    }
}

enum class PayMode(val label: String) { UPI("UPI"), CARD("Card"), CASH("Cash"), NET_BANKING("Bank"), WALLET("Wallet"), OTHER("Other") }

enum class Source(val label: String) { MANUAL("Added by you"), SMS("From bank SMS"), SHARED("Shared / pasted"), RECURRING("Repeating entry"), STATEMENT("Read from statement") }

/** How the user felt about a spend, after the fact. "Regret" is the strongest signal for what to cut. */
enum class Worth(val label: String, val emoji: String) { NONE("Not rated", ""), NEEDED("Needed", "✅"), WORTH_IT("Worth it", "🙂"), REGRET("Regret", "😬") }

enum class ReviewState { CONFIRMED, NEEDS_REVIEW }

data class Category(
    val id: Long,
    val key: String,
    val name: String,
    val emoji: String,
    val colorArgb: Long,
    val bucket: Bucket,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val custom: Boolean = false,
)

data class Txn(
    val id: Long = 0L,
    val amountPaise: Long,
    val type: TxType,
    val categoryId: Long,
    val epochMillis: Long,
    val zoneId: String = ZoneId.systemDefault().id,
    val mode: PayMode = PayMode.UPI,
    val payee: String = "",
    val note: String = "",
    val tags: String = "",
    val worth: Worth = Worth.NONE,
    val source: Source = Source.MANUAL,
    val review: ReviewState = ReviewState.CONFIRMED,
    val refNo: String = "",
    val goalId: Long? = null,
    val createdAt: Long = epochMillis,
    val updatedAt: Long = epochMillis,
    /** Which account it was paid from / received into (null = not linked). */
    val accountId: Long? = null,
    /** For transfers: the account the money went to. */
    val toAccountId: Long? = null,
) {
    // Computed once per entry, not on every read: reports and analytics read these millions of times.
    val zone: ZoneId by lazy(LazyThreadSafetyMode.PUBLICATION) { runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault()) }
    private val local by lazy(LazyThreadSafetyMode.PUBLICATION) { Instant.ofEpochMilli(epochMillis).atZone(zone) }
    val date: LocalDate by lazy(LazyThreadSafetyMode.PUBLICATION) { local.toLocalDate() }
    val month: YearMonth by lazy(LazyThreadSafetyMode.PUBLICATION) { YearMonth.from(date) }
    val hour: Int get() = local.hour
}

data class Goal(
    val id: Long = 0L,
    val name: String,
    val emoji: String,
    val targetPaise: Long,
    val savedPaise: Long = 0L,
    val targetDate: LocalDate? = null,
    val archived: Boolean = false,
) {
    val progress: Float get() = if (targetPaise <= 0) 0f else (savedPaise.toFloat() / targetPaise).coerceIn(0f, 1f)
    val done: Boolean get() = targetPaise in 1..savedPaise
}

/** Money is always integer paise. Never floating point rupees in storage or maths. */
object Money {
    /** Indian digit grouping (1,24,500) done by hand so it is identical on every device and JVM. */
    fun groupIndian(n: Long): String {
        val d = abs(n).toString()
        if (d.length <= 3) return d
        val last3 = d.takeLast(3)
        val head = d.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$head,$last3"
    }

    fun format(paise: Long, withPaise: Boolean = false, sign: Boolean = false): String {
        val s = when { paise < 0 -> "−"; sign && paise > 0 -> "+"; else -> "" }
        val a = abs(paise)
        val rupees = groupIndian(a / 100)
        val rem = a % 100
        return if (withPaise && rem != 0L) "$s₹$rupees.${"%02d".format(rem)}" else "$s₹$rupees"
    }

    /** ₹1.2L / ₹3.4Cr style for big projections. */
    fun compact(paise: Long): String {
        val r = abs(paise) / 100.0
        val s = if (paise < 0) "−" else ""
        return when {
            r >= 1_00_00_000 -> s + "₹%.2fCr".format(Locale.ENGLISH, r / 1_00_00_000)
            r >= 1_00_000 -> s + "₹%.2fL".format(Locale.ENGLISH, r / 1_00_000)
            r >= 1_000 -> s + "₹%.1fK".format(Locale.ENGLISH, r / 1_000)
            else -> format(paise)
        }.replace(".00", "").replace(".0K", "K")
    }

    /** Parses "1,250.5" or "₹ 999" to paise; null if not a positive number. */
    fun parse(text: String): Long? {
        val clean = text.replace("₹", "").replace(",", "").trim()
        val bd = clean.toBigDecimalOrNull() ?: return null
        if (bd.signum() <= 0) return null
        return bd.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
    }
}

/** Files the user attaches. Stored encrypted inside the app; never in a shared folder. */
enum class AttachmentKind(val label: String, val emoji: String) {
    RECEIPT("Receipt or screenshot", "🧾"),
    BANK_STATEMENT("Bank statement", "🏦"),
    CARD_STATEMENT("Credit card statement", "💳"),
    UPI_STATEMENT("UPI app statement", "📱"),
    OTHER("Other document", "📄");

    val isStatement: Boolean get() = this != RECEIPT
}

data class Attachment(
    val id: Long = 0,
    val kind: AttachmentKind,
    val txnId: Long? = null,
    val month: YearMonth? = null,
    val label: String = "",
    val mime: String,
    val fileName: String,
    val originalName: String = "",
    val sizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isPdf: Boolean get() = mime == "application/pdf"
    val isSheet: Boolean get() = !isPdf && !isImage
    val isImage: Boolean get() = mime.startsWith("image/")
    val sizeLabel: String get() = when {
        sizeBytes >= 1_048_576 -> "%.1f MB".format(java.util.Locale.ENGLISH, sizeBytes / 1_048_576.0)
        else -> "${(sizeBytes / 1024).coerceAtLeast(1)} KB"
    }
}
