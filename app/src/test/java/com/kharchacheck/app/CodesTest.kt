package com.kharchacheck.app

import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.ReviewState
import com.kharchacheck.app.core.model.Source
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.core.model.Worth
import com.kharchacheck.app.data.db.Codes
import org.junit.Assert.assertEquals
import org.junit.Test

/** Stored rows depend on these numbers. If this test fails, a code was reordered: put it back and append instead. */
class CodesTest {
    @Test fun codesNeverMove() {
        assertEquals(listOf(0, 1, 2, 3), listOf(TxType.EXPENSE, TxType.INCOME, TxType.INVEST, TxType.TRANSFER).map { Codes.code(Codes.TYPE, it) })
        assertEquals(listOf(0, 1, 2, 3, 4, 5), listOf(PayMode.UPI, PayMode.CARD, PayMode.CASH, PayMode.NET_BANKING, PayMode.WALLET, PayMode.OTHER).map { Codes.code(Codes.MODE, it) })
        assertEquals(listOf(0, 1, 2, 3), listOf(Worth.NONE, Worth.NEEDED, Worth.WORTH_IT, Worth.REGRET).map { Codes.code(Codes.WORTH, it) })
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(Source.MANUAL, Source.SMS, Source.SHARED, Source.RECURRING, Source.STATEMENT).map { Codes.code(Codes.SOURCE, it) })
        assertEquals(listOf(0, 1), listOf(ReviewState.CONFIRMED, ReviewState.NEEDS_REVIEW).map { Codes.code(Codes.REVIEW, it) })
    }

    @Test fun everyEnumValueHasACode() {
        assertEquals(TxType.entries.toSet(), Codes.TYPE.toSet())
        assertEquals(PayMode.entries.toSet(), Codes.MODE.toSet())
        assertEquals(Worth.entries.toSet(), Codes.WORTH.toSet())
        assertEquals(Source.entries.toSet(), Codes.SOURCE.toSet())
        assertEquals(ReviewState.entries.toSet(), Codes.REVIEW.toSet())
    }

    @Test fun unknownCodesFallBackSafely() {
        assertEquals(PayMode.OTHER, Codes.of(Codes.MODE, 99, PayMode.OTHER))
        assertEquals(Codes.code(Codes.MODE, PayMode.OTHER), Codes.codeOfName(Codes.MODE, "SOMETHING_NEW", PayMode.OTHER))
    }

    @Test fun homeZoneIsStoredEmpty() {
        assertEquals("", Codes.packZone("Asia/Kolkata"))
        assertEquals("Asia/Kolkata", Codes.unpackZone(""))
        assertEquals("Asia/Dubai", Codes.unpackZone(Codes.packZone("Asia/Dubai")))
    }
}
