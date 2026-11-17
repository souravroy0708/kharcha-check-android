package com.kharchacheck.app.domain.read

import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.sms.SmsParser
import java.util.Locale

/** Turns a narration or payee into (type, category key, clean payee). Shared by screenshots and statements. */
object Classifier {
    data class Result(val type: TxType, val categoryKey: String, val payee: String)

    fun classify(description: String, isCredit: Boolean, creditCard: Boolean = false): Result {
        val d = description.lowercase(Locale.ENGLISH)
        val payee = Narration.payee(description)
        val (brandKey, brandName) = SmsParser.categorize("$payee $description")
        val name = brandName ?: payee
        return when {
            Regex("""\b(atm|cash wdl|cash withdrawal|nwd|awd)\b""").containsMatchIn(d) && !isCredit -> Result(TxType.TRANSFER, "atm_cash", "ATM withdrawal")
            Regex("""(credit card|cc payment|card payment|cc ?bill ?pay|cc bill|billpay.{0,12}cc|bbps.*card|cred club|payment received|thank you for your payment)""").containsMatchIn(d) &&
                (creditCard == isCredit) -> Result(TxType.TRANSFER, "cc_bill", if (creditCard) "Card bill payment" else name)
            Regex("""(self transfer|own a/?c|to self\b|sweep)""").containsMatchIn(d) -> Result(TxType.TRANSFER, "self_transfer", name)
            isCredit && Regex("""(refund|reversal|reversed|cashback|chargeback)""").containsMatchIn(d) -> Result(TxType.INCOME, "refund", name)
            isCredit && Regex("""\b(salary|sal\b|payroll)""").containsMatchIn(d) -> Result(TxType.INCOME, "salary", name)
            isCredit && Regex("""(interest|int\.?pd|dividend|int cr)""").containsMatchIn(d) -> Result(TxType.INCOME, "interest_dividend", name)
            isCredit -> Result(TxType.INCOME, "other_income", name)
            brandKey in SmsParser.INVEST_KEYS -> Result(TxType.INVEST, brandKey, name)
            Regex("""(late fee|late payment|penalty|overlimit|min bal|non maint)""").containsMatchIn(d) -> Result(TxType.EXPENSE, "fees_penalties", name)
            Regex("""(finance charge|interest charged|int on)""").containsMatchIn(d) && creditCard -> Result(TxType.EXPENSE, "cc_interest", name)
            Regex("""(gst|annual fee|sms charge|chrg|charges|conv fee|convenience)""").containsMatchIn(d) -> Result(TxType.EXPENSE, "bank_charges", name)
            else -> Result(TxType.EXPENSE, brandKey, name)
        }
    }
}

/** Pulls a human payee out of bank narrations like "UPI/630518920112/SWIGGY/swiggy@icici/Payment". */
object Narration {
    private val NOISE = setOf("upi", "dr", "cr", "imps", "neft", "rtgs", "nach", "ach", "mmt", "pos", "ecom", "payment", "pay", "paid",
        "transfer", "trf", "to", "from", "by", "txn", "ref", "inb", "mob", "ib", "bil", "billpay", "p2a", "p2m", "collect", "upiint",
        "sent", "using", "via", "na", "null", "others", "other", "purchase", "debit", "credit", "card", "visa", "rupay", "mastercard")

    fun payee(description: String): String {
        val tokens = description.split('/', '-', '|', ':', '*', '\\', ',')
            .map { it.trim() }
            .filter { t ->
                t.length >= 3 && t.any(Char::isLetter) && !t.contains('@') &&
                    t.lowercase(Locale.ENGLISH) !in NOISE &&
                    !Regex("""^[A-Za-z]{4}0[A-Za-z0-9]{6}$""").matches(t) && // IFSC
                    t.count(Char::isDigit) < t.length / 2
            }
        val pick = tokens.firstOrNull() ?: description.substringBefore('@').trim()
        return title(pick.replace(Regex("""\s+"""), " ").take(32))
    }

    /** "BHUKYA DEV" → "Bhukya Dev"; in mixed text keeps short acronyms ("SBI Card", "ATM"); keeps "B2". */
    fun title(s: String): String {
        val allCaps = s.none { it.isLowerCase() }
        return s.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
            when {
                w.any(Char::isDigit) -> w
                !allCaps && w.length <= 3 && w.all { it.isUpperCase() } -> w
                else -> w.lowercase(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) }
            }
        }
    }
}
