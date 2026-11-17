package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.sms.SmsParser
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Turns one spoken sentence into a draft entry, e.g.
 * "spent 450 on lunch with team at Paradise yesterday" → ₹450, Dining Out, payee Paradise, note "Lunch with team", yesterday.
 *
 * Pure Kotlin, no Android. The text comes from Android's own speech-to-text screen (the app never records audio);
 * nothing here is stored except the fields the user saves.
 */
object VoiceEntry {

    data class Result(
        val amountPaise: Long?,
        /** Only set when the words clearly say income / invest / spend. */
        val type: TxType?,
        /** Null when nothing matched; the user picks. */
        val categoryKey: String?,
        val payee: String,
        val note: String,
        val mode: PayMode?,
        /** 0 = today, 1 = yesterday, 2 = day before yesterday. */
        val daysAgo: Int,
    ) {
        val foundSomething: Boolean get() = amountPaise != null || categoryKey != null || payee.isNotBlank()
    }

    fun parse(spoken: String): Result {
        val raw = spoken.replace(Regex("""\s+"""), " ").trim()
        if (raw.isEmpty()) return Result(null, null, null, "", "", null, 0)
        var work = wordsToDigits(raw)
        // Indian grouping from speech: "2,500" / "1,20,000" → plain digits.
        while (true) { val next = work.replace(Regex("""(\d),(\d)"""), "$1$2"); if (next == work) break; work = next }

        // 1. Amount
        val amount = findAmount(work)
        var rem = if (amount != null) work.removeRange(amount.start, amount.end) else work
        rem = squash(rem)
        val lower = rem.lowercase(Locale.ENGLISH)
        val full = work.lowercase(Locale.ENGLISH)

        // 2. When
        val daysAgo = when {
            Regex("""\b(day before yesterday|parso|parson)\b""").containsMatchIn(lower) -> 2
            Regex("""\b(yesterday|kal)\b""").containsMatchIn(lower) -> 1
            else -> 0
        }
        rem = squash(rem.replace(Regex("""\b(day before yesterday|yesterday|today|parso|parson|kal|aaj)\b""", RegexOption.IGNORE_CASE), " "))

        // 3. How it was paid
        var mode: PayMode? = null
        for ((re, m) in MODES) {
            val hit = re.find(rem) ?: continue
            mode = m
            rem = squash(rem.removeRange(hit.range))
            break
        }

        // 4. Type
        val type = when {
            Regex("""\b(received|salary|credited|income|earned|refund|cashback|mila|mile|aaya|aaye)\b""").containsMatchIn(full) -> TxType.INCOME
            Regex("""\b(sip|invested|invest|investment|mutual funds?|ppf|nps|epf|fd|rd|fixed deposit|recurring deposit|sgb|stocks?|shares?|emergency fund)\b""").containsMatchIn(full) -> TxType.INVEST
            Regex("""\b(lent|udhaar|udhar|borrowed|self transfer|own account|withdrew|atm)\b""").containsMatchIn(full) -> TxType.TRANSFER
            Regex("""\b(spent|spend|paid|pay|bought|buy|kharcha|kharch|diya|diye|gave|sent|ordered)\b""").containsMatchIn(full) -> TxType.EXPENSE
            else -> null
        }

        // 5. Payee: "at Paradise", "to Ramesh", "from Infosys"
        var payee = ""
        val payeeHit = PAYEE.find(rem)
        if (payeeHit != null) {
            val p = payeeHit.groupValues[2].trim().replace(Regex("""^(the|my|our)\s+""", RegexOption.IGNORE_CASE), "")
            if (p.length >= 2 && !FILLER_ONLY.matches(p.lowercase(Locale.ENGLISH))) payee = p
            rem = squash(rem.removeRange(payeeHit.range))
        }

        // 6. Category
        val (smsKey, brand) = SmsParser.categorize("$payee $rem")
        val key = when (type) {
            TxType.INCOME -> incomeKey(full)
            TxType.INVEST -> if (brand != null && smsKey in SmsParser.INVEST_KEYS) smsKey else investKey(full)
            TxType.TRANSFER -> transferKey(full)
            else -> {
                val said = WORDS.firstOrNull { it.second.containsMatchIn("$payee $rem".lowercase(Locale.ENGLISH)) }?.first
                when {
                    // A marketplace says where, the words say what: "jeans on Amazon" → Clothes, not general shopping.
                    brand != null && smsKey == "shopping" && said != null -> said
                    brand != null -> smsKey
                    else -> said ?: smsKey.takeIf { it != "uncategorised" }
                }
            }
        }
        if (payee.isBlank() && brand != null && brand !in GENERIC_BRANDS) payee = brand
        payee = tidyName(payee).take(40)

        // 7. Note: what it was for, in the user's own words
        val forHit = Regex("""\bfor\s+(.+)$""", RegexOption.IGNORE_CASE).find(rem)
        var note = forHit?.groupValues?.get(1) ?: rem
        note = squash(note.replace(FILLERS, " ")).trim(' ', ',', '.', '-')
        if (payee.isNotBlank()) note = note.replace(Regex("""\b(?:on|at|from|via|to)?\s*""" + Regex.escape(payee) + """\b""", RegexOption.IGNORE_CASE), " ")
        if (brand != null) note = note.replace(Regex("""\b(?:on|at|from|via|to)?\s*""" + Regex.escape(brand) + """\b""", RegexOption.IGNORE_CASE), " ")
        note = squash(note)
        repeat(2) {
            note = note.replace(Regex("""^(on|for|of|to|at|in|with|and)\s+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+(on|for|of|to|at|in|with|and|ka|ke|ki)$""", RegexOption.IGNORE_CASE), "").trim()
        }
        if (note.length < 2 || note.all { it.isDigit() || it == ' ' }) note = ""
        // One word that only names the category ("chai", "petrol", "SIP") adds nothing the category doesn't already say.
        if (key != null && !note.contains(' ')) note = ""
        if (note.isNotEmpty()) note = note.replaceFirstChar { it.titlecase(Locale.ENGLISH) }.take(200)

        return Result(amount?.paise, type, key, payee, note, mode, daysAgo)
    }

    // ---------- amount ----------

    private class Amount(val paise: Long, val start: Int, val end: Int, val marked: Boolean)

    private val NUM = Regex("""(?<![\d.])(\d+(?:\.\d+)?)""")
    private val PRE = Regex("""(₹|\brs\.?|\binr|\brupees?)\s*$""", RegexOption.IGNORE_CASE)
    private val MULT = Regex("""^\s*(k|thousand|hazaa?r|hajaa?r|lakhs?|lacs?|lakh|crores?|cr|hundred|sau)\b""", RegexOption.IGNORE_CASE)
    private val SUF = Regex("""^\s*(rupees?\b|rupaye\b|rupaiye\b|rupaiya\b|rs\b\.?|bucks\b|₹|/-)""", RegexOption.IGNORE_CASE)
    private val QTY = Regex(
        """^\s*(am|pm|baje|o'?clock|st|nd|rd|th|kgs?|gms?|g|grams?|km|l|ltrs?|litres?|liters?|pcs|pieces?|plates?|cups?|people|persons?|days?|months?|years?|hours?|hrs?|minutes?|mins?|times?|x)\b|^\s*%""",
        RegexOption.IGNORE_CASE,
    )

    private fun findAmount(text: String): Amount? {
        val found = mutableListOf<Amount>()
        for (m in NUM.findAll(text)) {
            val before = text.substring(0, m.range.first)
            val pre = PRE.find(before)
            var end = m.range.last + 1
            val mult = MULT.find(text.substring(end))
            val factor = when (mult?.groupValues?.get(1)?.lowercase(Locale.ENGLISH)) {
                null -> 1L
                "k", "thousand", "hazar", "hazaar", "hajar", "hajaar" -> 1_000L
                "lakh", "lakhs", "lac", "lacs" -> 100_000L
                "crore", "crores", "cr" -> 10_000_000L
                "hundred", "sau" -> 100L
                else -> 1L
            }
            if (mult != null) end += mult.range.last + 1
            val suf = SUF.find(text.substring(end))
            if (suf != null) end += suf.range.last + 1
            val marked = pre != null || suf != null
            if (!marked && mult == null && QTY.containsMatchIn(text.substring(m.range.last + 1))) continue
            val value = m.groupValues[1].toBigDecimalOrNull() ?: continue
            val paise = value.multiply(BigDecimal(factor)).movePointRight(2).setScale(0, RoundingMode.HALF_UP)
            if (paise <= BigDecimal.ZERO || paise > BigDecimal("100000000000")) continue
            val start = pre?.range?.first ?: m.range.first
            found += Amount(paise.toLong(), start, end, marked)
        }
        // Prefer an amount said with "rupees" / ₹; otherwise the biggest number ("2 coffees, 300" → 300).
        return found.firstOrNull { it.marked } ?: found.maxByOrNull { it.paise }
    }

    // ---------- number words ("two fifty", "fifteen hundred", "one lakh twenty thousand") ----------

    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fourty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
    private val SCALES = mapOf(
        "hundred" to 100L, "thousand" to 1_000L, "lakh" to 100_000L, "lakhs" to 100_000L, "lac" to 100_000L, "lacs" to 100_000L,
        "crore" to 10_000_000L, "crores" to 10_000_000L,
    )

    private fun isNumWord(w: String) = w in UNITS || w in TENS || w in SCALES

    internal fun wordsToDigits(text: String): String {
        val tokens = text.split(' ')
        val out = StringBuilder()
        var i = 0
        while (i < tokens.size) {
            val word = tokens[i].lowercase(Locale.ENGLISH).trimEnd(',', '.', '!', '?')
            // A segment must start with a unit or tens word; "5 thousand" stays for the amount finder.
            if (word in UNITS || word in TENS) {
                var j = i
                val seg = mutableListOf<String>()
                while (j < tokens.size) {
                    val w = tokens[j].lowercase(Locale.ENGLISH).trimEnd(',', '.', '!', '?')
                    if (isNumWord(w)) { seg += w; j++; if (tokens[j - 1].last() in ",.!?") break }
                    else if (w == "and" && j + 1 < tokens.size && isNumWord(tokens[j + 1].lowercase(Locale.ENGLISH).trimEnd(',', '.', '!', '?')) && seg.isNotEmpty()) j++
                    else break
                }
                val trail = tokens[j - 1].takeLastWhile { it in ",.!?" }
                if (out.isNotEmpty()) out.append(' ')
                out.append(segmentValue(seg)).append(trail)
                i = j
            } else {
                if (out.isNotEmpty()) out.append(' ')
                out.append(tokens[i])
                i++
            }
        }
        return out.toString()
    }

    private fun segmentValue(seg: List<String>): Long {
        var total = 0L
        var current = 0L
        var unitOnly = false
        for (w in seg) {
            when {
                w in UNITS -> { val v = UNITS.getValue(w).toLong(); current += v; unitOnly = v in 1..9 && current == v }
                w in TENS -> { val v = TENS.getValue(w).toLong(); current = if (unitOnly) current * 100 + v else current + v; unitOnly = false }
                w == "hundred" -> { current = maxOf(current, 1) * 100; unitOnly = false }
                else -> { total += maxOf(current, 1) * SCALES.getValue(w); current = 0; unitOnly = false }
            }
        }
        return total + current
    }

    // ---------- words ----------

    private val MODES = listOf(
        Regex("""\b(?:by|in|via|using|through|with|on|from)?\s*(?:cash|nakad|nakdi)\b""", RegexOption.IGNORE_CASE) to PayMode.CASH,
        Regex("""\b(?:by|via|using|through|with|on|from)?\s*(?:my\s+)?(?:\w+\s+)?(?:credit|debit)\s+card\b""", RegexOption.IGNORE_CASE) to PayMode.CARD,
        Regex("""\b(?:by|via|using|through|with|on|from)\s+(?:my\s+)?card\b""", RegexOption.IGNORE_CASE) to PayMode.CARD,
        Regex("""\b(?:by|via|using|through|with|on|from|in)?\s*(?:upi|gpay|g pay|google pay|phonepe|phone pe|paytm|bhim|cred)\b""", RegexOption.IGNORE_CASE) to PayMode.UPI,
        Regex("""\b(?:by|via|using|through|with)?\s*(?:net ?banking|bank transfer|neft|imps|rtgs)\b""", RegexOption.IGNORE_CASE) to PayMode.NET_BANKING,
    )

    private val PAYEE = Regex(
        """\b(paid to|gave to|given to|sent to|at|to|from)\s+([A-Za-z][A-Za-z0-9&'.\- ]{1,30}?)(?=\s+(?:for|on|in|with|by|via|using|and|of|kal|today|yesterday)\b|[,.!?]|$)""",
        RegexOption.IGNORE_CASE,
    )
    /** Category-style "brands" in SmsParser that aren't a real merchant name, so they never become the payee. */
    private val GENERIC_BRANDS = setOf("Fuel", "Electricity", "Gas", "Rent", "Loan EMI", "Pharmacy", "Insurance", "Mutual Fund SIP", "Stocks", "PPF / NPS", "Metro / Rail")

    private val FILLER_ONLY = Regex("""^(home|office|work|the|my|me|him|her|them|it|shop|market|store|online|someone|friend)$""")
    private val FILLERS = Regex(
        """\b(i|i've|i have|have|has|just|spent|spend|paid|pay|bought|buy|ordered|kharcha|kharch|kiya|kiye|diya|diye|gave|give|got|sent|received|credited|invested|lent|dala|dalwaya|liya|worth|rupees?|rupaye|rupaiye|rupaiya|rs|bucks|ke|ki|ka|liye|lie|mein|me|the|a|an|today|aaj)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Spoken words → category keys (must exist in DefaultCategories). Order matters: specific before general. */
    private val WORDS: List<Pair<String, Regex>> = listOf(
        "mobile_internet" to """\b(recharge|wifi|wi-fi|broadband|internet|data pack|mobile bill|phone bill|postpaid|prepaid)\b""",
        "electricity" to """\b(electricity|current bill|light bill|bijli|water bill)\b""",
        "gas" to """\b(gas cylinder|cylinder|lpg|gas bill)\b""",
        "rent" to """\b(rent|kiraya|maintenance)\b""",
        "emi" to """\b(emi|loan)\b""",
        "medical" to """\b(medicines?|tablets?|doctor|hospital|pharmacy|chemist|clinic|dentist|lab test|blood test)\b""",
        "education" to """\b(school|college|tuition|fees|fee)\b""",
        "vegetables" to """\b(sabzi|sabji|subzi|vegetables?|tomato(?:es)?|tamatar|onions?|pyaaz|pyaz|potato(?:es)?|aloo|veggies|fruits?|milk|doodh|dairy|paneer|curd|dahi|eggs?)\b""",
        "groceries" to """\b(groceries|grocery|ration|kirana|atta|rice|dal|oil|sugar)\b""",
        "chai_coffee" to """\b(chai|tea|coffee|cafe)\b""",
        "food_delivery" to """\b(food delivery|ordered food|online food)\b""",
        "junk_food" to """\b(snacks?|samosa|pani ?puri|golgappa|vada ?pav|pav bhaji|chaat|pizza|burger|momos|chips|maggi|fries)\b""",
        "dining_out" to """\b(lunch|dinner|breakfast|restaurant|dhaba|biryani|eating out|meal|thali|canteen|mess)\b""",
        "cold_drinks" to """\b(cold ?drinks?|coke|pepsi|juice|soft drinks?|soda)\b""",
        "sweets_desserts" to """\b(sweets?|mithai|ice ?cream|cake|dessert|chocolates?)\b""",
        "travel" to """\b(flight|trip|holiday|vacation|train ticket|irctc|hotel booking|bus ticket)\b""",
        "cab" to """\b(cab|taxi)\b""",
        "commute" to """\b(auto|rickshaw|bus|metro|local train|train)\b""",
        "fuel" to """\b(petrol|diesel|fuel|cng)\b""",
        "vehicle" to """\b(parking|toll|fastag|puncture|car wash|bike service|car service|servicing)\b""",
        "insurance" to """\b(insurance|premium|policy)\b""",
        "childcare" to """\b(diapers?|baby food|baby|kids?|toys?)\b""",
        "house_help" to """\b(maid|bai|cook|house help|plumber|electrician|carpenter|driver salary|watchman)\b""",
        "household" to """\b(detergent|soap|shampoo|cleaning|household|toiletries)\b""",
        "shopping_clothes" to """\b(clothes|shirts?|t-?shirts?|jeans|saree|sari|kurta|kurti|shoes|footwear|dress|chappals?)\b""",
        "gadgets" to """\b(laptop|earphones|earbuds|headphones|charger|gadgets?|smartwatch|new phone)\b""",
        "entertainment" to """\b(movies?|cinema|film|concert|show tickets?)\b""",
        "ott" to """\b(subscription|ott)\b""",
        "gifts" to """\b(gifts?|shagun|present)\b""",
        "festivals" to """\b(pooja|puja|diwali|festival|rakhi|temple|holi|eid|christmas)\b""",
        "fitness" to """\b(gym|yoga|fitness|protein)\b""",
        "personal_care" to """\b(haircut|hair cut|salon|parlou?r|barber|grooming|spa)\b""",
        "books_courses" to """\b(books?|course|classes|class|stationery)\b""",
        "pets" to """\b(dog|cat|pet|vet)\b""",
        "donation" to """\b(donation|charity|daan)\b""",
        "smoking" to """\b(cigarettes?|paan|gutka|tobacco|smoke)\b""",
        "alcohol" to """\b(alcohol|beer|whisky|whiskey|wine|vodka|rum|daru|party)\b""",
        "gaming" to """\b(game|gaming)\b""",
        "betting" to """\b(betting|bet)\b""",
        "lottery" to """\b(lottery)\b""",
        "home_decor" to """\b(furniture|curtains|decor|plants?)\b""",
        "shopping" to """\b(shopping|amazon|flipkart|online order)\b""",
    ).map { it.first to Regex(it.second) }

    private fun incomeKey(t: String) = when {
        Regex("""\b(salary|payslip)\b""").containsMatchIn(t) -> "salary"
        Regex("""\b(refund|cashback|reversed)\b""").containsMatchIn(t) -> "refund"
        Regex("""\b(freelance|side income|project|client)\b""").containsMatchIn(t) -> "freelance"
        Regex("""\b(business|shop sale|sales)\b""").containsMatchIn(t) -> "business"
        Regex("""\b(interest|dividend)\b""").containsMatchIn(t) -> "interest_dividend"
        Regex("""\b(rent)\b""").containsMatchIn(t) -> "rent_received"
        Regex("""\b(gift|shagun)\b""").containsMatchIn(t) -> "gift_received"
        else -> "other_income"
    }

    private fun investKey(t: String) = when {
        Regex("""\b(ppf|nps|epf)\b""").containsMatchIn(t) -> "ppf_epf_nps"
        Regex("""\b(fd|rd|fixed deposit|recurring deposit)\b""").containsMatchIn(t) -> "fd_rd"
        Regex("""\b(gold|sgb)\b""").containsMatchIn(t) -> "gold"
        Regex("""\b(stocks?|shares?)\b""").containsMatchIn(t) -> "stocks"
        Regex("""\b(emergency fund)\b""").containsMatchIn(t) -> "emergency_fund"
        else -> "sip"
    }

    private fun transferKey(t: String) = when {
        Regex("""\b(atm|withdrew)\b""").containsMatchIn(t) -> "atm_cash"
        Regex("""\b(self transfer|own account)\b""").containsMatchIn(t) -> "self_transfer"
        else -> "lent_borrowed"
    }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").replace(Regex("""\s+([,.!?])"""), "$1").trim()

    /** Speech often comes back in lower case: "ramesh tea stall" → "Ramesh Tea Stall". Keeps "DMart", "KFC". */
    private fun tidyName(s: String): String {
        val t = s.trim(' ', ',', '.', '-')
        if (t.any { it.isUpperCase() }) return t
        return t.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ENGLISH) } }
    }
}
