package com.kharchacheck.app

import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.VoiceEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Sentences people actually say into the 🎤. Add every one that reads wrong here first. */
class VoiceEntryTest {

    @Test fun fullSentence() {
        val r = VoiceEntry.parse("Spent 450 on lunch with team at Paradise yesterday")
        assertEquals(45_000L, r.amountPaise)
        assertEquals(TxType.EXPENSE, r.type)
        assertEquals("dining_out", r.categoryKey)
        assertEquals("Paradise", r.payee)
        assertEquals("Lunch with team", r.note)
        assertEquals(1, r.daysAgo)
    }

    @Test fun rupeeSymbolAndBrand() {
        val r = VoiceEntry.parse("₹389 Swiggy")
        assertEquals(38_900L, r.amountPaise)
        assertEquals("food_delivery", r.categoryKey)
        assertEquals("Swiggy", r.payee)
    }

    @Test fun indianNumberWords() {
        assertEquals(25_000L, VoiceEntry.parse("paid two fifty to auto").amountPaise)
        assertEquals(150_000L, VoiceEntry.parse("fifteen hundred for electricity bill").amountPaise)
        assertEquals(200_000L, VoiceEntry.parse("gave 2k to Rahul").amountPaise)
        assertEquals(15_000_000L, VoiceEntry.parse("1.5 lakh for school fees").amountPaise)
        assertEquals(6_800_000L, VoiceEntry.parse("received salary 68,000 from Infosys").amountPaise)
    }

    @Test fun quantitiesAreNotAmounts() {
        val r = VoiceEntry.parse("2 kg tomatoes 80 rupees")
        assertEquals(8_000L, r.amountPaise)
        assertEquals("vegetables", r.categoryKey)
    }

    @Test fun paymentModeAndGenericBrandsNeverBecomePayee() {
        val r = VoiceEntry.parse("petrol 500 rupees by card")
        assertEquals(PayMode.CARD, r.mode)
        assertEquals("fuel", r.categoryKey)
        assertEquals("", r.payee)
        assertEquals(PayMode.CASH, VoiceEntry.parse("bought vegetables for 180 in cash").mode)
        assertEquals(PayMode.UPI, VoiceEntry.parse("doctor fees 600 via gpay").mode)
    }

    @Test fun typesFromWords() {
        val income = VoiceEntry.parse("received salary 68,000 from Infosys")
        assertEquals(TxType.INCOME, income.type); assertEquals("salary", income.categoryKey); assertEquals("Infosys", income.payee)
        val sip = VoiceEntry.parse("SIP 5000")
        assertEquals(TxType.INVEST, sip.type); assertEquals("sip", sip.categoryKey)
        val lent = VoiceEntry.parse("lent 1000 to Priya")
        assertEquals(TxType.TRANSFER, lent.type); assertEquals("lent_borrowed", lent.categoryKey); assertEquals("Priya", lent.payee)
    }

    @Test fun hinglish() {
        val r = VoiceEntry.parse("kal 300 ka petrol dala")
        assertEquals(30_000L, r.amountPaise)
        assertEquals("fuel", r.categoryKey)
        assertEquals(1, r.daysAgo)
    }

    @Test fun doctorFeesAreMedicalNotEducation() = assertEquals("medical", VoiceEntry.parse("doctor fees 600").categoryKey)

    @Test fun nothingUseful() {
        val r = VoiceEntry.parse("hello there")
        assertNull(r.amountPaise)
        assertFalse(r.foundSomething)
    }
}
