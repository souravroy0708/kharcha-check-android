package com.kharchacheck.app.domain.sms

import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.TxType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Reads Indian bank / card / UPI alert text and turns it into a draft entry. Pure Kotlin, no Android.
 *
 * Used only for text the user chooses to share or paste into the app (a bank message, a UPI receipt).
 * The app has no SMS permission and never reads messages itself.
 *
 * Privacy: the message body is never stored. Only the parsed fields are kept, and every field stays editable.
 */
object SmsParser {

    data class Parsed(
        val amountPaise: Long,
        val type: TxType,
        val mode: PayMode,
        val last4: String,
        val payee: String,
        val categoryKey: String,
        val refNo: String,
        val confident: Boolean,
    )

    sealed interface Outcome {
        data class Matched(val parsed: Parsed) : Outcome
        data class Ignored(val reason: String) : Outcome
    }

    private val BANK_TOKENS = setOf(
        "HDFCBK", "SBIINB", "SBIPSG", "SBIUPI", "CBSSBI", "ICICIB", "ICICIT", "AXISBK", "KOTAKB", "PNBSMS", "BOBTXN",
        "BOIIND", "CANBNK", "UNIONB", "IDFCFB", "INDUSB", "YESBNK", "FEDBNK", "RBLBNK", "RBLCRD", "AUFBNK", "SCBANK",
        "CITIBK", "HSBCIN", "AMEXIN", "ONECRD", "SLCEIT", "JUPITR", "FIBANK", "PAYTMB", "AIRBNK", "JIOPBK", "IPPBSM",
        "MAHABK", "UCOBNK", "CENTBK", "IOBCHN", "KVBANK", "KARBNK", "SIBSMS", "DBSBNK", "EQUTAS", "UJJIVN", "BANDHN",
        "PHONPE", "GPAY", "AMAZON", "CRED",
    )

    private val EXCLUDE = Regex(
        """\b(otp|one time password|verification code|auth(?:orisation)? code|cvv|do not share|""" +
            """statement (?:for|is)|e-statement|min(?:imum)? amount due|total amount due|bill (?:is )?generated|payment due|due (?:on|by)|""" +
            """mandate (?:is )?(?:created|registered|set)|autopay (?:is )?(?:set|registered)|e-mandate|standing instruction|""" +
            """failed|declined|unsuccessful|could not be processed|request (?:received|initiated)|will be debited|""" +
            """pre-approved|apply now|avail (?:a )?loan|loan offer|credit limit (?:increase|upgrade)|offer valid|cashback offer|t&c)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val AMOUNT = Regex("""(?:rs\.?|inr|₹)\s*([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val LAST4 = Regex("""(?:a/c|acct|account|card|ending|no\.)\s*(?:no\.?|ending|with)?\s*[xX*#.\-]*([0-9]{3,4})\b""", RegexOption.IGNORE_CASE)
    private val REF = Regex("""(?:upi\s*ref(?:\s*no)?|ref(?:erence)?(?:\s*no)?|utr|rrn|txn\s*id)[\s.:#\-]*([A-Za-z0-9]{6,22})""", RegexOption.IGNORE_CASE)
    private val PAYEE_PATTERNS = listOf(
        Regex("""\(([A-Za-z][A-Za-z0-9 .&'\-]{2,40})\)"""),
        Regex("""(?:\bto|\bat|towards|trf to|paid to|info:?)\s+(?:vpa\s+)?([A-Za-z0-9@._\-&' ]{2,40}?)(?=\s+(?:on|ref|upi|utr|via|avl|for|using)\b|[.;,(]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+(?:vpa\s+)?([A-Za-z0-9@._\-&' ]{2,40}?)(?=\s+(?:on|ref|upi|utr|via|avl)\b|[.;,(]|$)""", RegexOption.IGNORE_CASE),
    )

    private data class Brand(val name: String, val categoryKey: String, val keywords: List<String>)

    // Order matters: more specific brands before umbrella brands (Instamart before Swiggy).
    private val BRANDS = listOf(
        Brand("Swiggy Instamart", "groceries", listOf("instamart")),
        Brand("Swiggy", "food_delivery", listOf("bundl technologies", "swiggy")),
        Brand("Zomato", "food_delivery", listOf("zomato", "eternal ltd")),
        Brand("Blinkit", "groceries", listOf("blink commerce", "blinkit", "grofers")),
        Brand("Zepto", "groceries", listOf("kiranakart", "zepto")),
        Brand("BigBasket", "groceries", listOf("supermarket grocery", "bigbasket", "innovative retail")),
        Brand("DMart", "groceries", listOf("avenue supermarts", "dmart", "d-mart")),
        Brand("JioMart", "groceries", listOf("jiomart", "smart bazaar", "reliance retail")),
        Brand("Starbucks", "chai_coffee", listOf("tata starbucks", "starbucks")),
        Brand("Chai Point", "chai_coffee", listOf("chai point", "chaayos", "third wave coffee", "tea villa", "blue tokai")),
        Brand("Uber", "cab", listOf("uber india", "uber")),
        Brand("Ola", "cab", listOf("ani technologies", "olacabs")),
        Brand("Rapido", "cab", listOf("roppen transportation", "rapido")),
        Brand("Metro / Rail", "commute", listOf("metro rail", "bmrcl", "dmrc", "hmrl", "mmrcl", "irctc")),
        Brand("Fuel", "fuel", listOf("indian oil", "iocl", "hpcl", "bpcl", "bharat petroleum", "shell india", "petrol", "fuel")),
        Brand("Amazon", "shopping", listOf("amazon")),
        Brand("Flipkart", "shopping", listOf("flipkart", "ekart")),
        Brand("Myntra", "shopping_clothes", listOf("myntra")),
        Brand("Ajio", "shopping_clothes", listOf("ajio", "zudio", "trent ltd")),
        Brand("Nykaa", "personal_care", listOf("fsn ecommerce", "nykaa")),
        Brand("Airtel", "mobile_internet", listOf("bharti airtel", "airtel")),
        Brand("Jio", "mobile_internet", listOf("reliance jio", "jio prepaid")),
        Brand("Vi", "mobile_internet", listOf("vodafone idea")),
        Brand("Electricity", "electricity", listOf("bescom", "tsspdcl", "tgspdcl", "mahadiscom", "tata power", "adani electricity", "msedcl", "electricity")),
        Brand("Gas", "gas", listOf("indane", "bharatgas", "hp gas", "mahanagar gas", "igl")),
        Brand("Rent", "rent", listOf("house rent", "nobroker", "nestaway", "society maintenance")),
        Brand("Loan EMI", "emi", listOf("bajaj finance", "loan emi", "emi ", "nach-", "ach d-")),
        Brand("Netflix", "ott", listOf("netflix")),
        Brand("Spotify", "ott", listOf("spotify")),
        Brand("YouTube Premium", "ott", listOf("youtube")),
        Brand("JioHotstar", "ott", listOf("hotstar")),
        Brand("Pharmacy", "medical", listOf("apollo pharmacy", "tata 1mg", "pharmeasy", "medplus", "netmeds")),
        Brand("BookMyShow", "entertainment", listOf("bigtree entertainment", "bookmyshow", "pvr inox")),
        Brand("Dream11", "betting", listOf("dream11", "sporta technologies", "my11circle", "mpl")),
        Brand("Insurance", "insurance", listOf("lic of india", "life insurance", "general insurance", "health insurance")),
        Brand("Mutual Fund SIP", "sip", listOf("bse star mf", "mutual fund", "indian clearing corp", "iccl", "groww", "kuvera", "coin by zerodha")),
        Brand("Stocks", "stocks", listOf("zerodha broking", "upstox", "angel one")),
        Brand("PPF / NPS", "ppf_epf_nps", listOf("ppf", "nps trust", "protean")),
    )

    fun isBankSender(sender: String): Boolean {
        val s = sender.trim().uppercase(Locale.ENGLISH)
        if (s.isEmpty()) return false
        val digits = s.count { it.isDigit() }
        if (digits >= 10 && s.all { it.isDigit() || it in "+ -" }) return false // a person's phone number
        if (BANK_TOKENS.any { s.contains(it) }) return true
        return Regex("^[A-Z]{2}-[A-Z0-9]{5,8}(-[A-Z])?$").matches(s)
    }

    /**
     * @param trustSender true for text the user shared or pasted themselves; we skip the sender check.
     */
    fun parse(sender: String, body: String, trustSender: Boolean = false): Outcome {
        val text = body.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (text.length < 12) return Outcome.Ignored("too short")
        if (!trustSender && !isBankSender(sender)) return Outcome.Ignored("not a bank sender")
        if (EXCLUDE.containsMatchIn(text)) return Outcome.Ignored("otp, promo, reminder or failed payment")

        val lower = text.lowercase(Locale.ENGLISH)
        val refund = Regex("""\b(refund(?:ed)?|reversed|reversal)\b""").containsMatchIn(lower)
        val debit = Regex("""\b(debited|spent|paid|sent|withdrawn|purchase|dr\b|debit of|used for|transferred to)""").containsMatchIn(lower)
        val credit = Regex("""\b(credited|received|deposited|cr\b|added to)""").containsMatchIn(lower)
        if (!debit && !credit && !refund) return Outcome.Ignored("no money movement")

        val amount = AMOUNT.find(text)?.groupValues?.get(1)?.replace(",", "")?.let { toPaise(it) }
            ?: return Outcome.Ignored("no amount")
        if (amount <= 0) return Outcome.Ignored("zero amount")

        val last4 = LAST4.find(text)?.groupValues?.get(1).orEmpty()
        val ref = REF.find(text)?.groupValues?.get(1).orEmpty()
        val atm = Regex("""\b(atm|cash withdrawal|cash wdl|withdrawn at)\b""").containsMatchIn(lower)
        val ccBill = Regex("""(credit card bill|card bill payment|cc bill|payment (?:of .{0,20})?received (?:towards|on|for) (?:your )?(?:\w+ ){0,3}credit card|cred club|cheq)""").containsMatchIn(lower)
        val selfTx = Regex("""(self transfer|own account|to self\b|neft to self)""").containsMatchIn(lower)

        val mode = when {
            atm -> PayMode.CASH
            lower.contains("upi") || lower.contains("vpa") || Regex("""[a-z0-9.\-]+@[a-z]+""").containsMatchIn(lower) -> PayMode.UPI
            Regex("""(credit card|debit card|\bcard\b|pos\b)""").containsMatchIn(lower) -> PayMode.CARD
            Regex("""\b(imps|neft|rtgs|ach|ecs|nach|net ?banking)\b""").containsMatchIn(lower) -> PayMode.NET_BANKING
            lower.contains("wallet") -> PayMode.WALLET
            else -> PayMode.OTHER
        }

        val payeeRaw = when {
            atm -> "ATM withdrawal"
            else -> extractPayee(text)
        }
        val brand = BRANDS.firstOrNull { b -> b.keywords.any { lower.contains(it) } }

        val (type, key) = when {
            atm -> TxType.TRANSFER to "atm_cash"
            ccBill -> TxType.TRANSFER to "cc_bill"
            selfTx -> TxType.TRANSFER to "self_transfer"
            refund -> TxType.INCOME to "refund"
            credit && !debit -> TxType.INCOME to when {
                lower.contains("salary") || lower.contains("sal ") -> "salary"
                lower.contains("interest") || lower.contains("dividend") -> "interest_dividend"
                else -> "other_income"
            }
            brand != null && brand.categoryKey in INVEST_KEYS -> TxType.INVEST to brand.categoryKey
            brand != null -> TxType.EXPENSE to brand.categoryKey
            else -> TxType.EXPENSE to guessFromWords(payeeRaw.lowercase(Locale.ENGLISH), amount)
        }

        val payee = brand?.name ?: payeeRaw
        val confident = payee.isNotBlank() && key != "uncategorised"
        return Outcome.Matched(Parsed(amount, type, mode, last4, payee, key, ref, confident))
    }

    val INVEST_KEYS = setOf("sip", "stocks", "ppf_epf_nps", "fd_rd", "gold")

    /** Brand or word-based category for any free text (payee, narration). Returns (categoryKey, brand name or null). */
    fun categorize(text: String): Pair<String, String?> {
        val lower = text.lowercase(Locale.ENGLISH)
        val brand = BRANDS.firstOrNull { b -> b.keywords.any { lower.contains(it) } }
        return if (brand != null) brand.categoryKey to brand.name else guessFromWords(lower, 0) to null
    }

    private fun toPaise(s: String): Long? =
        s.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.takeIf { it > BigDecimal.ZERO }?.toLong()

    private fun extractPayee(text: String): String {
        for (p in PAYEE_PATTERNS) {
            for (m in p.findAll(text)) {
                val raw = m.groupValues[1].trim()
                val cleaned = clean(raw)
                if (cleaned.length >= 2 && !cleaned.equals("your account", true) && !cleaned.startsWith("a/c", true) &&
                    !Regex("""^(xx|\*)?\d+$""", RegexOption.IGNORE_CASE).matches(cleaned)) return cleaned
            }
        }
        return ""
    }

    private fun clean(raw: String): String {
        var s = raw
        if (s.contains('@')) s = s.substringBefore('@')
        s = s.replace(Regex("""\b(pvt|ltd|limited|private|technologies|llp|inc|vpa|upi|a/c)\b\.?""", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("""[^A-Za-z0-9 &'\-]"""), " ").replace(Regex("""\s+"""), " ").trim()
        return s.split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { w -> if (w.length <= 3 && w.all { it.isUpperCase() }) w else w.lowercase(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) } }
            .take(32)
    }

    private fun guessFromWords(p: String, amountPaise: Long): String = when {
        Regex("""chai|tea|coffee|cafe""").containsMatchIn(p) -> "chai_coffee"
        Regex("""hotel|restaurant|dhaba|biryani|kitchen|foods""").containsMatchIn(p) -> "dining_out"
        Regex("""bakery|sweets|mithai""").containsMatchIn(p) -> "sweets_desserts"
        Regex("""kirana|mart|stores|supermarket|dairy|milk""").containsMatchIn(p) -> "groceries"
        Regex("""medical|pharma|chemist|clinic|hospital|diagnostic""").containsMatchIn(p) -> "medical"
        Regex("""petrol|fuel|filling""").containsMatchIn(p) -> "fuel"
        Regex("""rent""").containsMatchIn(p) -> "rent"
        else -> "uncategorised"
    }

    /** Masks long numbers so an unrecognised template can be shared for support without leaking anything. */
    fun mask(body: String): String = body.replace(Regex("[0-9]{3,}"), "XXXX")
}
