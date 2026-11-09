package com.kharchacheck.app.domain.read

import com.kharchacheck.app.core.model.TxType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * Reads the OCR text of a payment screenshot (Google Pay, PhonePe, Paytm, BHIM, bank apps) or a bill.
 * Everything it finds is a suggestion: the entry screen shows it for the user to check before saving.
 */
object ReceiptParser {

    enum class Status { SUCCESS, FAILED, PENDING, UNKNOWN }

    data class Result(
        val amountPaise: Long?,
        val amountSure: Boolean,
        val type: TxType,
        val payee: String,
        val categoryKey: String,
        val dateTime: LocalDateTime?,
        val refNo: String,
        val status: Status,
    ) {
        val foundSomething: Boolean get() = amountPaise != null || payee.isNotBlank() || dateTime != null
    }

    // Tesseract often reads ₹ as %, F, & or z at the start of an amount.
    private val CURRENCY_AMOUNT = Regex("""(?:₹|rs\.?|inr|(?<![A-Za-z0-9])[%&Fz])\s*([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]{1,7}(?:\.[0-9]{1,2})?)\b""", RegexOption.IGNORE_CASE)
    private val BARE_AMOUNT = Regex("""^\s*([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{2})?|[0-9]{1,6}(?:\.[0-9]{2})?)\s*$""")
    private val REF = Regex("""(?:upi\s*(?:transaction\s*)?(?:id|ref(?:erence)?\.?\s*(?:no|number)?)|utr|rrn|ref(?:erence)?\s*(?:no|number))\s*[:.#]?\s*([0-9]{10,16})""", RegexOption.IGNORE_CASE)
    private val TWELVE = Regex("""\b([0-9]{12})\b""")

    private val MONTHS = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
    private val DATE_NAMED = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*,?\s+(\d{4}|\d{2})\b""", RegexOption.IGNORE_CASE)
    private val DATE_NAMED_US = Regex("""\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+(\d{1,2}),?\s+(\d{4})\b""", RegexOption.IGNORE_CASE)
    private val DATE_NUMERIC = Regex("""\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{4}|\d{2})\b""")
    private val DATE_ISO = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val TIME = Regex("""\b(\d{1,2})[:.](\d{2})(?::\d{2})?\s*([ap]\.?m\.?)?""", RegexOption.IGNORE_CASE)

    private val PAYEE_LABEL = Regex("""^(?:paid\s+(?:successfully\s+)?to|payment\s+to|sent\s+to|to|transferred\s+to|money\s+sent\s+to|paying)\s*:?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val FROM_LABEL = Regex("""^(?:received\s+from|from|money\s+received\s+from)\s*:?\s*(.*)$""", RegexOption.IGNORE_CASE)

    fun parse(ocr: String, today: LocalDate = LocalDate.now()): Result {
        val lines = ocr.lines().map { it.replace('|', ' ').replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
        val all = lines.joinToString("\n")
        val lower = all.lowercase(Locale.ENGLISH)

        val status = when {
            Regex("""\b(failed|failure|declined|unsuccessful|cancelled)\b""").containsMatchIn(lower) -> Status.FAILED
            Regex("""\b(pending|processing|in progress)\b""").containsMatchIn(lower) -> Status.PENDING
            Regex("""(successful|success|completed|paid|received|sent|debited|credited)""").containsMatchIn(lower) -> Status.SUCCESS
            else -> Status.UNKNOWN
        }
        val received = Regex("""(received from|money received|credited to your|you received|received\b)""").containsMatchIn(lower) &&
            !Regex("""(paid to|sent to|payment to|debited from)""").containsMatchIn(lower)
        val refund = lower.contains("refund")

        // Amount: prefer one written with a currency sign; fall back to a big bare number on its own line.
        var amount: Long? = null
        var sure = false
        for (l in lines) {
            val m = CURRENCY_AMOUNT.find(l) ?: continue
            if (Regex("""(balance|bal\b|limit|cashback|reward|offer|saved)""", RegexOption.IGNORE_CASE).containsMatchIn(l)) continue
            amount = toPaise(m.groupValues[1]); sure = m.value.trim().first().let { it == '₹' || it.lowercaseChar() == 'r' || it.lowercaseChar() == 'i' }
            if (amount != null) break
        }
        if (amount == null) {
            lines.firstOrNull { BARE_AMOUNT.matches(it) && !looksLikeYear(it) }?.let { amount = toPaise(BARE_AMOUNT.find(it)!!.groupValues[1]); sure = false }
        }

        val payee = findName(lines, if (received) FROM_LABEL else PAYEE_LABEL)
        val ref = REF.find(all)?.groupValues?.get(1) ?: TWELVE.find(all)?.groupValues?.get(1).orEmpty()
        val dt = findDateTime(all, today)

        val cls = Classifier.classify("$payee $all".take(400), isCredit = received || refund)
        val type = if (received || refund) TxType.INCOME else if (cls.type == TxType.INVEST) TxType.INVEST else TxType.EXPENSE
        val key = when { refund -> "refund"; received -> cls.categoryKey.takeIf { cls.type == TxType.INCOME } ?: "other_income"; cls.type == TxType.EXPENSE || cls.type == TxType.INVEST -> cls.categoryKey; else -> "uncategorised" }
        val brand = com.kharchacheck.app.domain.sms.SmsParser.categorize("$payee $all").second
        return Result(amount, sure, type, brand ?: payee, key, dt, ref, status)
    }

    private fun findName(lines: List<String>, label: Regex): String {
        for ((i, l) in lines.withIndex()) {
            val m = label.find(l) ?: continue
            val sameLine = m.groupValues[1].trim()
            val candidates = listOfNotNull(sameLine.takeIf { it.isNotEmpty() }) + lines.drop(i + 1).take(3)
            for (c in candidates) {
                val clean = c.replace(Regex("""^[:\-\s]+"""), "")
                if (clean.contains('@')) return Narration.title(clean.substringBefore('@').replace('.', ' '))
                if (clean.length >= 2 && clean.any(Char::isLetter) && CURRENCY_AMOUNT.find(clean) == null &&
                    !Regex("""^(upi|bank|a/c|account|xx|\*|banking name)""", RegexOption.IGNORE_CASE).containsMatchIn(clean)) {
                    return Narration.title(clean.replace(Regex("""\(.*$"""), "").trim().take(32))
                }
            }
        }
        return ""
    }

    private fun findDateTime(text: String, today: LocalDate): LocalDateTime? {
        fun yr(s: String) = s.toInt().let { if (it < 100) 2000 + it else it }
        val date: LocalDate? = DATE_NAMED.find(text)?.let { m ->
            runCatching { LocalDate.of(yr(m.groupValues[3]), MONTHS.getValue(m.groupValues[2].lowercase().take(3)), m.groupValues[1].toInt()) }.getOrNull()
        } ?: DATE_NAMED_US.find(text)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[3].toInt(), MONTHS.getValue(m.groupValues[1].lowercase().take(3)), m.groupValues[2].toInt()) }.getOrNull()
        } ?: DATE_ISO.find(text)?.let { m -> runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull() }
        ?: DATE_NUMERIC.find(text)?.let { m -> runCatching { LocalDate.of(yr(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull() }
        if (date == null || date.isAfter(today) || date.isBefore(today.minusYears(2))) return null
        val time = TIME.findAll(text).mapNotNull { m ->
            var h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
            val ap = m.groupValues[3].lowercase().replace(".", "")
            if (min > 59 || h > 23) return@mapNotNull null
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            LocalTime.of(h, min)
        }.firstOrNull() ?: LocalTime.NOON
        return date.atTime(time)
    }

    private fun looksLikeYear(s: String) = s.trim().length == 4 && s.trim().toIntOrNull()?.let { it in 2000..2100 } == true

    internal fun toPaise(raw: String): Long? {
        val v = raw.replace(",", "").toBigDecimalOrNull() ?: return null
        if (v.signum() <= 0) return null
        return v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }
}
