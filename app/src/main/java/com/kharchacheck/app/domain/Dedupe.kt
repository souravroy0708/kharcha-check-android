package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import java.util.Locale
import kotlin.math.abs

/**
 * Decides which incoming entries (from a statement, screenshot or SMS) are already in the app.
 *
 * Rules, strongest first:
 *  1. Same bank reference / UTR number and amount → duplicate.
 *  2. Same day + same amount + same direction (in/out) + same merchant → duplicate.
 *  3. Same amount + direction within ±1 day, merchant unknown or different → *possible* duplicate
 *     (e.g. "Chai ₹40" typed by hand vs "UPI-RAMESH TEA ₹40" in the statement): unticked, but the user can add it.
 *
 * Matching *counts* occurrences: two genuine ₹40 chais on the same day stay two entries, but importing the
 * same statement twice adds nothing the second time.
 */
object Dedupe {
    enum class Verdict { NEW, DUPLICATE, POSSIBLE }

    data class Match(val verdict: Verdict, val existing: Txn?)

    fun merchantKey(s: String): String = s.lowercase(Locale.ENGLISH).filter { it.isLetterOrDigit() }.take(10)

    private fun isIn(t: Txn) = t.type == TxType.INCOME

    fun check(existing: List<Txn>, incoming: List<Txn>): List<Match> {
        val used = HashSet<Long>()   // existing ids already matched to an earlier incoming row
        val byRef = existing.filter { it.refNo.length >= 6 }.groupBy { it.refNo.trimStart('0') }
        val byDay = existing.groupBy { it.date to it.amountPaise }
        val byAmount = existing.groupBy { it.amountPaise }

        return incoming.map { t ->
            fun take(candidates: List<Txn>?, ok: (Txn) -> Boolean): Txn? =
                candidates?.firstOrNull { it.id !in used && ok(it) }?.also { used += it.id }

            val ref = t.refNo.trimStart('0')
            if (ref.length >= 6) take(byRef[ref]) { it.amountPaise == t.amountPaise }?.let { return@map Match(Verdict.DUPLICATE, it) }

            val mk = merchantKey(t.payee)
            take(byDay[t.date to t.amountPaise]) { isIn(it) == isIn(t) && mk.isNotEmpty() && merchantKey(it.payee) == mk }
                ?.let { return@map Match(Verdict.DUPLICATE, it) }

            take(byAmount[t.amountPaise]) { isIn(it) == isIn(t) && abs(it.date.toEpochDay() - t.date.toEpochDay()) <= 1 }
                ?.let { return@map Match(Verdict.POSSIBLE, it) }

            Match(Verdict.NEW, null)
        }
    }
}
