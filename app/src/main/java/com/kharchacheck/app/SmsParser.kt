package com.kharchacheck.app

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Pure Kotlin Indian Bank & UPI SMS Parsing Pipeline (Spec Page 5).
 * Implements all 9 stages:
 * 1. Sender filter (alphanumeric Indian bank/card/wallet headers like AD-HDFCBK, VM-SBIINB, AX-ICICIB; ignores 10-digit personal numbers)
 * 2. Exclude (OTPs, promotions, statements, balance-only alerts, reminders/due dates, mandate set-ups, failed/declined payments)
 * 3. Classify (EXPENSE, INCOME, REFUND, TRANSFER, plus PayMode: UPI, CARD, ATM, NET_BANKING, CASH)
 * 4. Extract (amountPaise, direction, account last4, payment mode, merchant/payee, reference/UTR number, optional balance)
 * 5. Normalise merchants (lowercase, strip VPA handles & legal suffixes like "Bundl Technologies" -> "Swiggy", "Zomato Ltd" -> "Zomato")
 * 6. De-duplicate (same refNo, or same amountPaise + accountLast4 within 2 minutes)
 * 7. Categorise (User/Learned rules first, then bundled India merchant keyword map, else Uncategorised)
 * 8. Score confidence (High: amount + direction + mode + merchant -> AUTO; Medium -> AUTO with check badge; Low -> NEEDS_REVIEW tray)
 * 9. Handle transfers (Own-account transfers and credit card bill payments marked TRANSFER and excluded from spend)
 */
object SmsParser {

    data class ParsedSms(
        val amountPaise: Long,
        val type: TxType,
        val mode: PayMode,
        val accountLast4: String,
        val bankHeader: String,
        val rawMerchant: String,
        val normalizedMerchant: String,
        val categoryId: Long,
        val refNo: String,
        val balancePaise: Long?,
        val confidence: ConfidenceLevel,
        val reviewState: ReviewState,
        val epochMillis: Long,
        val smsId: String,
    )

    sealed class ParseOutcome {
        data class Matched(val parsed: ParsedSms) : ParseOutcome()
        data class Ignored(val reason: String) : ParseOutcome()
        data class UnrecognisedBankSms(val sender: String, val maskedTemplate: String) : ParseOutcome()
    }

    // Step 1: Bundled allow-list of Indian bank, card, and UPI sender headers
    private val KNOWN_BANK_TOKENS = setOf(
        "HDFCBK", "SBIINB", "SBIPSG", "SBIUPI", "CBSSBI", "ICICIB", "ICICIT", "AXISBK", "KOTAKB",
        "PNBSMS", "BOBTXN", "BOIIND", "CANBNK", "UNIONB", "IDFCFB", "INDUSB", "YESBNK", "FEDBNK",
        "RBLBNK", "RBLCRD", "AUFBNK", "SCBANK", "CITIBK", "HSBCIN", "AMEXIN", "ONECARD", "SLICEIT",
        "JUPITR", "FIJBNK", "PAYTMB", "AIRBNK", "JIOPAY", "IPPBSM", "MAHABK", "UCOBNK", "CENTBK",
        "IOBCHN", "KVBANK", "KARBNK", "SIBSMS", "DBSBNK", "EQUITB", "UJJIVN", "BANDHN"
    )

    // Step 2: Exclusion patterns (OTPs, promos, statements, failed/declined, mandates, balance-only)
    private val EXCLUDE_REGEX = Regex(
        "\\b(otp|one time password|verification code|auth code|cvv|pin is|" +
            "statement for|e-statement|min amount due|minimum amount due|total amount due|bill is generated|" +
            "autopay mandate|mandate successfully|e-mandate|standing instruction|" +
            "failed|declined|unsuccessful|could not be processed|reversed|" +
            "pre-approved|apply now|avail loan|personal loan|credit limit increase|offer valid|" +
            "available balance is rs|avl bal is rs)\\b",
        RegexOption.IGNORE_CASE
    )

    // Amount regex supporting Rs., INR, ₹ with Indian commas (e.g. Rs. 1,24,500.50 or INR 185)
    private val AMOUNT_REGEX = Regex(
        "(?:rs\\.?|inr|₹)\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    private val BALANCE_REGEX = Regex(
        "(?:avl\\s*bal|avail(?:able)?\\s*bal(?:ance)?|bal(?:ance)?\\s*(?:is|:))\\s*(?:rs\\.?|inr|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    private val ACCOUNT_LAST4_REGEX = Regex(
        "(?:a/c|acct|account|card|ending|xx+|\\*+)\\s*(?:no\\.?|ending|with)?\\s*[xX*#\\-]*([0-9]{3,4})\\b",
        RegexOption.IGNORE_CASE
    )

    private val REF_REGEX = Regex(
        "(?:upi\\s*ref(?:\\s*no)?|ref(?:\\s*no|\\.|#)?|utr|rrn|txn(?:\\s*id)?)[:\\s\\-]*([A-Za-z0-9]{6,22})",
        RegexOption.IGNORE_CASE
    )

    // Step 5: Merchant Legal Name -> Consumer Display Name + Default Category ID
    // Categories: 1=Food & Dining, 2=Groceries, 3=Commute & Fuel, 4=Shopping, 5=Bills & Utilities,
    // 6=Rent & Housing (Fixed), 7=EMI & Loans (Fixed), 8=Subscriptions (Fixed), 9=Health & Medical,
    // 10=Entertainment, 11=Small Chai & Snacks, 12=Salary & Income, 13=Transfer, 14=Uncategorised
    data class MerchantRule(val canonicalName: String, val categoryId: Long, val keywords: List<String>)

    val BUNDLED_MERCHANTS = listOf(
        MerchantRule("Swiggy", 1L, listOf("bundl technologies", "swiggy", "instamart")),
        MerchantRule("Zomato", 1L, listOf("zomato", "eternal ltd")),
        MerchantRule("EatClub / Box8", 1L, listOf("eatclub", "box8", "poncho hospitality")),
        MerchantRule("Starbucks / Third Wave", 11L, listOf("tata starbucks", "starbucks", "third wave coffee", "chaayos", "chai point")),
        MerchantRule("Blinkit", 2L, listOf("blink commerce", "blinkit", "grofers")),
        MerchantRule("Zepto", 2L, listOf("kiranakart", "zepto")),
        MerchantRule("BigBasket", 2L, listOf("supermarket grocery", "bigbasket", "innovative retail")),
        MerchantRule("DMart", 2L, listOf("avenue supermarkets", "dmart", "d-mart")),
        MerchantRule("Reliance Smart", 2L, listOf("reliance retail", "smart bazaar", "jiomart")),
        MerchantRule("Uber", 3L, listOf("uber india", "uber", "uberrides")),
        MerchantRule("Ola Cabs", 3L, listOf("ani technologies", "olacabs", "ola money", "ola electric")),
        MerchantRule("Rapido", 3L, listOf("roppen transportation", "rapido")),
        MerchantRule("Namma Yatri / Metro", 3L, listOf("namma yatri", "bmrc", "dmrc", "hmrl", "irctc", "indian railways", "makemytrip", "indigo")),
        MerchantRule("IndianOil / Shell / HPCL", 3L, listOf("indian oil", "iocl", "hpcl", "bpcl", "bharat petroleum", "shell india", "petrol")),
        MerchantRule("Amazon", 4L, listOf("amazon pay india", "amazon seller", "amazon")),
        MerchantRule("Flipkart / Myntra", 4L, listOf("flipkart", "myntra", "ekart")),
        MerchantRule("Nykaa / Decathlon", 4L, listOf("fsn ecommerce", "nykaa", "decathlon", "zudio", "trent ltd")),
        MerchantRule("Airtel / Jio / Vi", 5L, listOf("bharti airtel", "airtel", "reliance jio", "jio prepay", "vodafone idea", "act fibernet")),
        MerchantRule("BESCOM / Electricity / Gas", 5L, listOf("bescom", "tsspdcl", "mahadiscom", "tata power", "adani electricity", "indane", "bharatgas")),
        MerchantRule("House Rent", 6L, listOf("house rent", "landlord", "nobroker", "nestaway", "society maintenance")),
        MerchantRule("Loan EMI / Bajaj / HDFC Loan", 7L, listOf("ach d-", "ecs-", "nach-", "bajaj finance", "home loan emi", "car loan emi")),
        MerchantRule("Netflix / Spotify / Prime", 8L, listOf("netflix", "spotify", "youtube premium", "apple services", "google play", "hotstar")),
        MerchantRule("Apollo / 1mg / PharmEasy", 9L, listOf("apollo pharmacy", "tata 1mg", "pharmeasy", "medplus", "practo", "cultfit")),
        MerchantRule("BookMyShow / PVR", 10L, listOf("bigtree entertainment", "bookmyshow", "pvr inox", "pvr ltd")),
    )

    /** Step 1: Verify if sender looks like an Indian bank/card/wallet header (or allow manual share-to-app). */
    fun isBankSender(sender: String, allowAnySenderForShare: Boolean = false): Boolean {
        if (allowAnySenderForShare) return true
        val clean = sender.trim().uppercase(Locale.ENGLISH)
        // Ignore standard 10–12 digit personal mobile numbers (+919876543210, 9876543210)
        val digitsOnly = clean.filter { it.isDigit() }
        if (digitsOnly.length >= 10 && clean.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
            return false
        }
        // Standard TRAI header format like AD-HDFCBK, VM-SBIINB-S, AX-ICICIB, JD-AXISBK
        if (KNOWN_BANK_TOKENS.any { clean.contains(it) }) return true
        if (Regex("^[A-Z]{2}-[A-Z0-9]{5,8}(-[A-Z])?$").matches(clean)) return true
        return false
    }

    /** Main entry point for parsing an incoming, backfilled, or shared SMS. */
    fun parse(
        sender: String,
        body: String,
        epochMillis: Long = System.currentTimeMillis(),
        smsId: String = "",
        userRules: List<RuleEntity> = emptyList(),
        existingRecent: List<TransactionEntry> = emptyList(),
        allowAnySenderForShare: Boolean = false,
    ): ParseOutcome {
        val trimmed = body.trim()
        if (trimmed.length < 12) return ParseOutcome.Ignored("Too short")

        // 1. Sender filter
        if (!isBankSender(sender, allowAnySenderForShare)) {
            return ParseOutcome.Ignored("Personal number or non-bank sender")
        }

        // 2. Exclude OTPs, promotions, statements, reminders, failed txns
        if (EXCLUDE_REGEX.containsMatchIn(trimmed)) {
            return ParseOutcome.Ignored("Excluded OTP / statement / promo / failed alert")
        }

        val lower = trimmed.lowercase(Locale.ENGLISH)

        // 3. Classify direction & mode
        val isRefund = lower.contains("refund") || lower.contains("reversed")
        val isTransferOrCardBill = lower.contains("credit card bill") ||
            lower.contains("cc bill payment") ||
            lower.contains("self transfer") ||
            lower.contains("own account") ||
            lower.contains("neft to self") ||
            lower.contains("cred club") ||
            lower.contains("cheq ")

        val isDebit = lower.contains("debited") ||
            lower.contains("spent") ||
            lower.contains("paid") ||
            lower.contains("sent rs") ||
            lower.contains("withdrawn") ||
            lower.contains("purchase of") ||
            lower.contains("dr from") ||
            lower.contains("debited by") ||
            lower.contains("used for rs")

        val isCredit = lower.contains("credited") ||
            lower.contains("received") ||
            lower.contains("deposited") ||
            lower.contains("salary") ||
            lower.contains("cr to")

        if (!isDebit && !isCredit && !isRefund) {
            return ParseOutcome.UnrecognisedBankSms(sender, maskSensitiveNumbers(trimmed))
        }

        // 4. Extract Amount
        val amountMatch = AMOUNT_REGEX.find(trimmed)
            ?: return ParseOutcome.UnrecognisedBankSms(sender, maskSensitiveNumbers(trimmed))
        val amountDouble = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull()
            ?: return ParseOutcome.UnrecognisedBankSms(sender, maskSensitiveNumbers(trimmed))
        if (amountDouble <= 0.0) return ParseOutcome.Ignored("Zero amount")
        val amountPaise = (amountDouble * 100.0).roundToLong()

        // Extract Balance if present
        val balPaise = BALANCE_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()?.let {
            (it * 100.0).roundToLong()
        }

        // Extract Account Last 4
        val accountLast4 = ACCOUNT_LAST4_REGEX.find(trimmed)?.groupValues?.getOrNull(1) ?: ""

        // Extract Reference / UTR
        val refNo = REF_REGEX.find(trimmed)?.groupValues?.getOrNull(1) ?: ""

        // Payment Mode classification
        val mode = when {
            lower.contains("atm") || lower.contains("withdrawn") || lower.contains("cash wdl") -> PayMode.ATM
            lower.contains("upi") || lower.contains("vpa") || lower.contains("@") -> PayMode.UPI
            (lower.contains("credit card") && !lower.contains("card bill")) || lower.contains("debit card") || lower.contains("pos ") || lower.contains("spent on card") || lower.contains("card xx") -> PayMode.CARD
            lower.contains("imps") || lower.contains("neft") || lower.contains("rtgs") || lower.contains("ach ") || lower.contains("ecs") -> PayMode.NET_BANKING
            else -> PayMode.UPI
        }

        // Transaction Type classification (Step 3 & Step 9)
        val txType = when {
            isTransferOrCardBill -> TxType.TRANSFER
            isRefund -> TxType.REFUND
            isCredit && !isDebit -> TxType.INCOME
            else -> TxType.EXPENSE
        }

        // Extract Raw Merchant / Payee
        val rawMerchant = extractMerchant(trimmed, mode, txType)

        // 5. Normalise Merchant & Map Aliases
        val (normalizedMerchant, matchedCategoryId, merchantKnown) = normalizeAndCategorize(
            rawMerchant = rawMerchant,
            fullSmsLower = lower,
            txType = txType,
            mode = mode,
            amountPaise = amountPaise,
            userRules = userRules,
        )

        // 6. De-duplicate (same refNo, or same amountPaise + accountLast4 within 2 minutes = 120,000 ms)
        val isDuplicate = existingRecent.any { ex ->
            (refNo.isNotBlank() && ex.refNo.equals(refNo, ignoreCase = true)) ||
                (ex.amountPaise == amountPaise && ex.type == txType && abs(ex.epochMillis - epochMillis) <= 120_000L)
        }
        if (isDuplicate) {
            return ParseOutcome.Ignored("Duplicate transaction within 2 minutes or matching refNo")
        }

        // 8. Score Confidence
        val hasMerchant = normalizedMerchant.isNotBlank() && normalizedMerchant != "Unknown Merchant"
        val confidence = when {
            hasMerchant && (merchantKnown || accountLast4.isNotBlank()) && matchedCategoryId != 14L -> ConfidenceLevel.HIGH
            hasMerchant -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

        val reviewState = when (confidence) {
            ConfidenceLevel.HIGH, ConfidenceLevel.MEDIUM -> ReviewState.AUTO
            ConfidenceLevel.LOW -> ReviewState.NEEDS_REVIEW
        }

        val bankHeader = sender.uppercase(Locale.ENGLISH).substringAfterLast("-").take(8)

        return ParseOutcome.Matched(
            ParsedSms(
                amountPaise = amountPaise,
                type = txType,
                mode = mode,
                accountLast4 = accountLast4,
                bankHeader = bankHeader,
                rawMerchant = rawMerchant,
                normalizedMerchant = normalizedMerchant.ifBlank { if (mode == PayMode.ATM) "ATM Cash Withdrawal" else "Unknown Merchant" },
                categoryId = matchedCategoryId,
                refNo = refNo,
                balancePaise = balPaise,
                confidence = confidence,
                reviewState = reviewState,
                epochMillis = epochMillis,
                smsId = smsId,
            )
        )
    }

    private fun extractMerchant(body: String, mode: PayMode, txType: TxType): String {
        if (mode == PayMode.ATM) return "ATM Cash Withdrawal"
        if (txType == TxType.INCOME && body.lowercase(Locale.ENGLISH).contains("salary")) return "Monthly Salary"

        // Try common Indian bank SMS patterns:
        // 1. "to VPA name@bank (Merchant Name)" or "to/at/towards <Merchant> on/ref/UPI"
        val patterns = listOf(
            Regex("(?:to|at|towards|from|info:|trf to|paid to)\\s+([A-Za-z0-9@._\\-&/ ]{2,40}?)(?:\\s+on\\b|\\s+ref\\b|\\s+upi\\b|\\s+utr\\b|\\s+avl\\b|\\.|$)", RegexOption.IGNORE_CASE),
            Regex("vpa\\s+([A-Za-z0-9._\\-]+@[A-Za-z0-9]+)", RegexOption.IGNORE_CASE),
            Regex("for\\s+([A-Za-z0-9 .&\\-]{3,35}?)(?:\\s+on\\b|\\s+at\\b|\\s+ref\\b|\\.|$)", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(body)?.groupValues?.getOrNull(1)?.trim()
            if (!m.isNullOrBlank() && !m.equals("your account", ignoreCase = true)) {
                return cleanRawMerchant(m)
            }
        }
        return "Unknown Merchant"
    }

    private fun cleanRawMerchant(raw: String): String {
        var s = raw.trim()
        // If VPA like swiggy@axisbank or bundl@icici, take prefix before @ unless there's a name
        if (s.contains("@")) {
            s = s.substringBefore("@")
        }
        // Strip legal suffixes and noise words
        s = s.replace(Regex("\\b(pvt|ltd|limited|private|india|technologies|retail|services|llp|inc|vpa|upi)\\b", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("[^A-Za-z0-9 &\\-]"), " ").replace(Regex("\\s+"), " ").trim()
        return s.take(32)
    }

    private fun normalizeAndCategorize(
        rawMerchant: String,
        fullSmsLower: String,
        txType: TxType,
        mode: PayMode,
        amountPaise: Long,
        userRules: List<RuleEntity>,
    ): Triple<String, Long, Boolean> {
        if (txType == TxType.TRANSFER) return Triple(rawMerchant.ifBlank { "Credit Card / Self Transfer" }, 13L, true)
        if (txType == TxType.INCOME) return Triple(rawMerchant.ifBlank { "Account Credit / Salary" }, 12L, true)
        if (mode == PayMode.ATM) return Triple("ATM Cash Withdrawal", 11L, true)

        val candidateLower = rawMerchant.lowercase(Locale.ENGLISH)

        // Step 7a: User & Learned Rules always win first
        for (rule in userRules) {
            val pat = rule.pattern.lowercase(Locale.ENGLISH).trim()
            if (pat.isNotBlank() && (candidateLower.contains(pat) || fullSmsLower.contains(pat))) {
                val display = rawMerchant.ifBlank { rule.pattern }.replaceFirstChar { it.uppercase() }
                return Triple(display, rule.categoryId, true)
            }
        }

        // Step 5 & 7b: Bundled Merchant Alias Table
        for (bm in BUNDLED_MERCHANTS) {
            if (bm.keywords.any { kw -> candidateLower.contains(kw) || fullSmsLower.contains(kw) }) {
                return Triple(bm.canonicalName, bm.categoryId, true)
            }
        }

        // Step 7c: Generic India keyword fallback
        val fallbackCategory = when {
            candidateLower.contains("chai") || candidateLower.contains("tea") || candidateLower.contains("bakery") || candidateLower.contains("sweet") -> 11L
            candidateLower.contains("hotel") || candidateLower.contains("restaurant") || candidateLower.contains("cafe") || candidateLower.contains("biryani") || candidateLower.contains("kitchen") -> 1L
            candidateLower.contains("mart") || candidateLower.contains("kirana") || candidateLower.contains("supermarket") || candidateLower.contains("dairy") || candidateLower.contains("milk") -> 2L
            candidateLower.contains("fuel") || candidateLower.contains("petrol") || candidateLower.contains("auto") || candidateLower.contains("cab") -> 3L
            candidateLower.contains("med") || candidateLower.contains("pharma") || candidateLower.contains("clinic") || candidateLower.contains("hospital") -> 9L
            candidateLower.contains("rent") -> 6L
            candidateLower.contains("emi") || candidateLower.contains("loan") -> 7L
            amountPaise <= 5000L && rawMerchant != "Unknown Merchant" -> 11L // <= ₹50 Tapri/Chai/Small spend
            else -> 14L // Uncategorised -> goes to Review Tray
        }

        val prettyName = if (rawMerchant.isBlank() || rawMerchant == "Unknown Merchant") {
            "Unknown Merchant"
        } else {
            rawMerchant.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
                word.lowercase(Locale.ENGLISH).replaceFirstChar { it.uppercase() }
            }
        }
        return Triple(prettyName, fallbackCategory, fallbackCategory != 14L)
    }

    /** Masks digits in unrecognised SMS templates so the user can inspect/copy safely without leaking account numbers. */
    fun maskSensitiveNumbers(body: String): String =
        body.replace(Regex("[0-9]{3,}"), "XXXX")

    // ---------- Golden Test Set of Anonymised Indian Bank SMS Messages (Spec Page 5 & 12) ----------

    data class GoldenSample(
        val bank: String,
        val sender: String,
        val body: String,
        val expectedMatched: Boolean,
        val expectedPaise: Long = 0L,
        val expectedType: TxType = TxType.EXPENSE,
        val expectedMode: PayMode = PayMode.UPI,
        val expectedMerchant: String = "",
    )

    val GOLDEN_SAMPLES = listOf(
        GoldenSample(
            bank = "HDFC Bank UPI",
            sender = "AD-HDFCBK",
            body = "Rs. 340.00 debited from a/c **4821 on 01-11-26 to VPA bundl@axisbank (Bundl Technologies Pvt Ltd). UPI Ref No 630518920112.",
            expectedMatched = true,
            expectedPaise = 34000L,
            expectedType = TxType.EXPENSE,
            expectedMode = PayMode.UPI,
            expectedMerchant = "Swiggy",
        ),
        GoldenSample(
            bank = "SBI UPI",
            sender = "VM-SBIUPI",
            body = "Dear UPI user A/C X9102 debited by Rs 145.0 on date 01Nov26 trf to ZOMATO LTD Refno 630511223344. If not u? call 1800111109.",
            expectedMatched = true,
            expectedPaise = 14500L,
            expectedType = TxType.EXPENSE,
            expectedMode = PayMode.UPI,
            expectedMerchant = "Zomato",
        ),
        GoldenSample(
            bank = "ICICI Bank Card",
            sender = "AX-ICICIB",
            body = "INR 1,280.00 spent on ICICI Bank Card XX7014 on 01-Nov-26 at Blink Commerce Pvt Ltd. Avl Limit: INR 1,42,000.",
            expectedMatched = true,
            expectedPaise = 128000L,
            expectedType = TxType.EXPENSE,
            expectedMode = PayMode.CARD,
            expectedMerchant = "Blinkit",
        ),
        GoldenSample(
            bank = "Axis Bank UPI",
            sender = "JD-AXISBK",
            body = "INR 65.00 debited from A/c no. XX3319 on 01-11-2026 to Tea Villa Chai Point via UPI Ref 630598877665.",
            expectedMatched = true,
            expectedPaise = 6500L,
            expectedType = TxType.EXPENSE,
            expectedMode = PayMode.UPI,
            expectedMerchant = "Starbucks / Third Wave",
        ),
        GoldenSample(
            bank = "Kotak Bank UPI",
            sender = "VM-KOTAKB",
            body = "Sent Rs.285.00 from Kotak Bank AC X4410 to Uber India Systems on 01-11-26. UPI Ref 630544556677.",
            expectedMatched = true,
            expectedPaise = 28500L,
            expectedType = TxType.EXPENSE,
            expectedMode = PayMode.UPI,
            expectedMerchant = "Uber",
        ),
        GoldenSample(
            bank = "HDFC Salary Credit",
            sender = "AD-HDFCBK",
            body = "INR 1,15,000.00 credited to A/c XX4821 on 01-11-2026 towards Monthly Salary via NEFT Ref HDFC0029182.",
            expectedMatched = true,
            expectedPaise = 11500000L,
            expectedType = TxType.INCOME,
            expectedMode = PayMode.NET_BANKING,
            expectedMerchant = "Monthly Salary",
        ),
        GoldenSample(
            bank = "SBI ATM Withdrawal",
            sender = "AD-SBIPSG",
            body = "Rs 2,500.00 withdrawn at SBI ATM Koramangala from A/c XX9102 on 01-11-26 Ref 401928. Avl Bal Rs 48,210.00.",
            expectedMatched = true,
            expectedPaise = 250000L,
            expectedType = TxType.EXPENSE,
            expectedMode = PayMode.ATM,
            expectedMerchant = "ATM Cash Withdrawal",
        ),
        GoldenSample(
            bank = "Credit Card Bill Transfer (No Double Count)",
            sender = "AD-HDFCBK",
            body = "Rs. 18,450.00 debited from a/c **4821 towards Credit Card Bill payment via CRED Club Ref 630599881122.",
            expectedMatched = true,
            expectedPaise = 1845000L,
            expectedType = TxType.TRANSFER,
            expectedMode = PayMode.UPI,
        ),
        GoldenSample(
            bank = "OTP Exclusion",
            sender = "AD-HDFCBK",
            body = "849201 is your OTP for transaction of Rs. 2,499.00 on Amazon. Do not share this OTP with anyone.",
            expectedMatched = false,
        ),
        GoldenSample(
            bank = "Personal Number Exclusion",
            sender = "+919876543210",
            body = "Bro I paid Rs. 500 to Swiggy for dinner tonight.",
            expectedMatched = false,
        ),
    )
}
