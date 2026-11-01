package com.kharchacheck.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    @Test
    fun goldenSetMeets95PercentAccuracyBar() {
        var passed = 0
        for (sample in SmsParser.GOLDEN_SAMPLES) {
            val outcome = SmsParser.parse(
                sender = sample.sender,
                body = sample.body,
                epochMillis = 1793550000000L,
            )
            if (sample.expectedMatched) {
                assertTrue("Expected match for ${sample.bank}", outcome is SmsParser.ParseOutcome.Matched)
                val p = (outcome as SmsParser.ParseOutcome.Matched).parsed
                assertEquals("Amount mismatch for ${sample.bank}", sample.expectedPaise, p.amountPaise)
                assertEquals("Type mismatch for ${sample.bank}", sample.expectedType, p.type)
                assertEquals("Mode mismatch for ${sample.bank}", sample.expectedMode, p.mode)
                if (sample.expectedMerchant.isNotBlank()) {
                    assertEquals("Merchant mismatch for ${sample.bank}", sample.expectedMerchant, p.normalizedMerchant)
                }
                passed++
            } else {
                assertTrue("Expected ignore for ${sample.bank}", outcome is SmsParser.ParseOutcome.Ignored)
                passed++
            }
        }
        val accuracy = (passed * 100) / SmsParser.GOLDEN_SAMPLES.size
        assertTrue("Golden set accuracy ($accuracy%) must be >= 95%", accuracy >= 95)
    }

    @Test
    fun deduplicatesSameReferenceOrWithinTwoMinutes() {
        val existing = listOf(
            TransactionEntry(
                id = 1L,
                amountPaise = 34000L,
                type = TxType.EXPENSE,
                epochMillis = 1793550000000L,
                refNo = "630518920112",
            )
        )
        val duplicateOutcome = SmsParser.parse(
            sender = "AD-HDFCBK",
            body = "Rs. 340.00 debited from a/c **4821 on 01-11-26 to VPA bundl@axisbank. UPI Ref No 630518920112.",
            epochMillis = 1793550030000L,
            existingRecent = existing,
        )
        assertTrue(duplicateOutcome is SmsParser.ParseOutcome.Ignored)
    }
}
