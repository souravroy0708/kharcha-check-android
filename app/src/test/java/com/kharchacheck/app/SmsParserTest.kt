package com.kharchacheck.app

import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden set of anonymised Indian bank alerts. Add every new real-world template that fails here first. */
class SmsParserTest {

    private data class Case(val sender: String, val body: String, val paise: Long?, val type: TxType? = null, val key: String? = null, val mode: PayMode? = null, val payee: String? = null)

    private val golden = listOf(
        Case("AD-HDFCBK", "Rs. 340.00 debited from a/c **4821 on 01-11-26 to VPA bundl@axisbank (Bundl Technologies Pvt Ltd). UPI Ref No 630518920112.", 34000, TxType.EXPENSE, "food_delivery", PayMode.UPI, "Swiggy"),
        Case("VM-SBIUPI", "Dear UPI user A/C X9102 debited by Rs 145.0 on date 01Nov26 trf to ZOMATO LTD Refno 630511223344. If not u? call 1800111109.", 14500, TxType.EXPENSE, "food_delivery", PayMode.UPI, "Zomato"),
        Case("AX-ICICIB", "INR 1,280.00 spent on ICICI Bank Card XX7014 on 01-Nov-26 at Blink Commerce Pvt Ltd. Avl Limit: INR 1,42,000.", 128000, TxType.EXPENSE, "groceries", PayMode.CARD, "Blinkit"),
        Case("JD-AXISBK", "INR 65.00 debited from A/c no. XX3319 on 01-11-2026 to Tea Villa Chai Point via UPI Ref 630598877665.", 6500, TxType.EXPENSE, "chai_coffee", PayMode.UPI),
        Case("VM-KOTAKB", "Sent Rs.250.00 from Kotak Bank AC X1234 to sharmagenstore@ybl on 02-11-26.UPI Ref 630600112233. Not you, kotak.com/fraud", 25000, TxType.EXPENSE, null, PayMode.UPI, "Sharmagenstore"),
        Case("AD-HDFCBK", "Update! INR 52,000.00 deposited in HDFC Bank A/c XX4821 on 01-NOV-26 for SALARY OCT 2026. Avl bal INR 61,233.50", 5200000, TxType.INCOME, "salary"),
        Case("VK-SBIINB", "Rs 2,000 withdrawn at ATM S1AW00123 from A/c X9102 on 03Nov26. Avl Bal Rs 12,340.", 200000, TxType.TRANSFER, "atm_cash", PayMode.CASH),
        Case("AX-ICICIB", "Payment of INR 18,450.00 received towards your ICICI Bank Credit Card XX7014. Thank you.", 1845000, TxType.TRANSFER, "cc_bill"),
        Case("AD-HDFCBK", "Refund of Rs 499.00 for your Amazon order has been credited to your HDFC Bank Card XX4821.", 49900, TxType.INCOME, "refund"),
        Case("JM-AXISBK", "INR 5,000.00 debited from A/c XX3319 on 05-11-26 towards BSE STAR MF SIP via NACH. Ref 7766554433.", 500000, TxType.INVEST, "sip", PayMode.NET_BANKING),
        Case("BZ-HDFCBK", "Rs.199 debited from a/c XX4821 to NETFLIX via card on 06-11-26.", 19900, TxType.EXPENSE, "ott", PayMode.CARD, "Netflix"),
        // Must be ignored
        Case("AD-HDFCBK", "123456 is your OTP for transaction of Rs 4,500 at AMAZON. Do not share with anyone.", null),
        Case("AX-ICICIB", "Your ICICI Bank Credit Card statement is generated. Total amount due Rs 18,450. Min amount due Rs 920. Due on 20-Nov.", null),
        Case("VM-SBIUPI", "Your UPI transaction of Rs 300 to swiggy@icici has failed. Amount will be reversed if debited.", null),
        Case("+919876543210", "Bhai Rs 500 paid to you for chai, check kar", null),
        Case("AD-HDFCBK", "Get pre-approved personal loan up to Rs 5,00,000 instantly! Apply now.", null),
    )

    @Test fun goldenSet() {
        val failures = mutableListOf<String>()
        golden.forEachIndexed { i, c ->
            val out = SmsParser.parse(c.sender, c.body)
            if (c.paise == null) {
                if (out !is SmsParser.Outcome.Ignored) failures += "#$i should be ignored, got $out"
                return@forEachIndexed
            }
            if (out !is SmsParser.Outcome.Matched) { failures += "#$i not matched: $out"; return@forEachIndexed }
            val p = out.parsed
            if (p.amountPaise != c.paise) failures += "#$i amount ${p.amountPaise} != ${c.paise}"
            c.type?.let { if (p.type != it) failures += "#$i type ${p.type} != $it" }
            c.key?.let { if (p.categoryKey != it) failures += "#$i key ${p.categoryKey} != $it" }
            c.mode?.let { if (p.mode != it) failures += "#$i mode ${p.mode} != $it" }
            c.payee?.let { if (p.payee != it) failures += "#$i payee '${p.payee}' != '$it'" }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun sharedTextSkipsSenderCheck() {
        val out = SmsParser.parse("", "Paid ₹120 to Ramesh Tea Stall via UPI", trustSender = true)
        assertTrue(out is SmsParser.Outcome.Matched)
        assertEquals("chai_coffee", (out as SmsParser.Outcome.Matched).parsed.categoryKey)
    }

    @Test fun masksNumbers() = assertEquals("A/c XXXX ref XXXX", SmsParser.mask("A/c 4821 ref 630518920112"))
}
