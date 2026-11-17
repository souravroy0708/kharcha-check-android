package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** A fun label for how someone spent. Shown on the last Wrapped card. */
data class Persona(val emoji: String, val title: String, val line: String)

/** "Kharcha Wrapped": a month or a year of money, told as a story. */
data class WrappedStory(
    val title: String,
    val spendPaise: Long,
    val incomePaise: Long,
    val investPaise: Long,
    val entries: Int,
    val activeDays: Int,
    val topCategory: Category?,
    val topCategoryPaise: Long,
    val topCategorySharePct: Int,
    val topPayee: String?,
    val topPayeeCount: Int,
    val biggestDay: LocalDate?,
    val biggestDayPaise: Long,
    val favouriteDay: DayOfWeek?,
    val leaksPaise: Long,
    val leakCount: Int,
    val noSpendDays: Int,
    val persona: Persona,
) {
    val savedPaise: Long get() = incomePaise - spendPaise
    val savingsRatePct: Int? get() = if (incomePaise > 0) ((savedPaise * 100) / incomePaise).toInt() else null
    val isEmpty: Boolean get() = entries == 0
}

object Wrapped {
    fun build(title: String, months: List<YearMonth>, all: List<Txn>, categories: Map<Long, Category>, noSpendDays: Set<Long>): WrappedStory {
        val set = months.toSet()
        val txns = all.filter { it.month in set }
        fun bucket(t: Txn) = categories[t.categoryId]?.bucket ?: Bucket.WANT
        val spend = txns.filter { it.type == TxType.EXPENSE && bucket(it).isSpend }
        val spendTotal = spend.sumOf { it.amountPaise }
        val income = txns.filter { it.type == TxType.INCOME }.sumOf { it.amountPaise }
        val invest = txns.filter { it.type == TxType.INVEST }.sumOf { it.amountPaise }
        val byCat = spend.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amountPaise } }.maxByOrNull { it.value }
        val topCat = byCat?.key?.let { categories[it] }
        val payee = spend.filter { it.payee.isNotBlank() }.groupBy { it.payee.trim().lowercase() }.maxByOrNull { it.value.size }
        val byDay = spend.groupBy { it.date }.mapValues { e -> e.value.sumOf { it.amountPaise } }
        val biggest = byDay.maxByOrNull { it.value }
        val favDay = spend.groupBy { it.date.dayOfWeek }.maxByOrNull { e -> e.value.sumOf { it.amountPaise } }?.key
        val leaks = spend.filter { bucket(it) == Bucket.LEAK }
        val noSpend = noSpendDays.count { YearMonth.from(LocalDate.ofEpochDay(it)) in set }
        val share = if (spendTotal > 0 && byCat != null) (byCat.value * 100 / spendTotal).toInt() else 0
        return WrappedStory(
            title = title, spendPaise = spendTotal, incomePaise = income, investPaise = invest, entries = txns.size,
            activeDays = txns.map { it.date }.toSet().size, topCategory = topCat, topCategoryPaise = byCat?.value ?: 0, topCategorySharePct = share,
            topPayee = payee?.value?.first()?.payee?.trim(), topPayeeCount = payee?.value?.size ?: 0,
            biggestDay = biggest?.key, biggestDayPaise = biggest?.value ?: 0, favouriteDay = favDay,
            leaksPaise = leaks.sumOf { it.amountPaise }, leakCount = leaks.size, noSpendDays = noSpend,
            persona = persona(income, spendTotal, invest, leaks.sumOf { it.amountPaise }, topCat),
        )
    }

    internal fun persona(income: Long, spend: Long, invest: Long, leaks: Long, top: Category?): Persona {
        val key = top?.key.orEmpty()
        return when {
            income > 0 && invest * 100 / income >= 20 -> Persona("📈", "The Investor", "You paid your future self first. Compounding is on your side.")
            income > 0 && (income - spend) * 100 / income >= 30 -> Persona("🐿️", "The Saver", "You kept a big slice of what you earned. That's rare and powerful.")
            spend > 0 && leaks * 100 / spend < 8 -> Persona("🔧", "The Leak Plugger", "Hardly any money slipped away on small stuff. Disciplined!")
            key in setOf("food_delivery", "dining_out", "junk_food", "chai_coffee", "sweets_desserts", "cold_drinks") ->
                Persona("🍜", "The Foodie", "Food was your love language. Maybe cook one extra meal a week?")
            key in setOf("travel", "cab", "fuel", "commute") -> Persona("✈️", "The Explorer", "You were always on the move. Plan trips ahead to save more.")
            key in setOf("shopping", "shopping_clothes", "gadgets", "impulse_shopping", "quick_commerce", "home_decor") ->
                Persona("🛍️", "The Shopper", "The cart was busy. Try the 30-day rule on the next big want.")
            top?.bucket == Bucket.NEED -> Persona("🏠", "The Provider", "Most of your money went to essentials. Steady and responsible.")
            else -> Persona("⚖️", "The Balanced One", "A bit of everything, nothing overboard. Nicely done.")
        }
    }
}
