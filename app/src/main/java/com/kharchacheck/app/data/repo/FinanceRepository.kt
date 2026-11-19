package com.kharchacheck.app.data.repo

import androidx.room.withTransaction
import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.DefaultCategories
import com.kharchacheck.app.core.model.Goal
import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.ReviewState
import com.kharchacheck.app.core.model.Source
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.data.db.BudgetEntity
import com.kharchacheck.app.data.db.KharchaDatabase
import com.kharchacheck.app.data.db.PayeeRuleEntity
import com.kharchacheck.app.data.db.toEntity
import com.kharchacheck.app.data.db.toModel
import com.kharchacheck.app.domain.sms.SmsParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.kharchacheck.app.domain.RecurringPlanner
import com.kharchacheck.app.domain.RecurringRule
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** Single source of truth for money data. All writes go through here; UI observes the Flows. */
class FinanceRepository(private val db: KharchaDatabase) {

    val categories: Flow<List<Category>> = db.categories().observe().map { l -> l.map { it.toModel() } }
    val txns: Flow<List<Txn>> = db.txns().observe().map { l -> l.map { it.toModel() } }
    val budgets: Flow<Map<Long, Long>> = db.budgets().observe().map { l -> l.associate { it.categoryId to it.limitPaise } }
    val goals: Flow<List<Goal>> = db.goals().observe().map { l -> l.map { it.toModel() } }
    val rules: Flow<List<PayeeRuleEntity>> = db.rules().observe()
    val recurring: Flow<List<RecurringRule>> = db.recurring().observe().map { l -> l.map { it.toModel() } }

    /** Seeds the category catalogue once. No fake transactions, no fake accounts. */
    suspend fun ensureSeeded() {
        if (db.categories().count() == 0) db.categories().insertAll(DefaultCategories.all.map { it.toEntity() })
        else {
            // App updates can add new default categories; insert any missing keys without touching user edits.
            val have = db.categories().all().map { it.key }.toSet()
            val missing = DefaultCategories.all.filter { it.key !in have }
            if (missing.isNotEmpty()) {
                val base = (db.categories().maxOrder() ?: 0) + 1
                db.categories().insertAll(missing.mapIndexed { i, c -> c.copy(sortOrder = base + i).toEntity() })
            }
        }
    }

    suspend fun txn(id: Long): Txn? = db.txns().get(id)?.toModel()

    suspend fun saveTxn(t: Txn): Long {
        val now = System.currentTimeMillis()
        val e = t.copy(updatedAt = now, createdAt = if (t.id == 0L) now else t.createdAt).toEntity()
        return db.withTransaction {
            val old = if (t.id != 0L) db.txns().get(t.id)?.toModel() else null
            val id = db.txns().upsert(e).let { if (e.id != 0L) e.id else it }
            // Keep goal balances in step with goal-linked savings entries.
            adjustGoal(old?.goalId, -(old?.amountPaise ?: 0))
            adjustGoal(t.goalId, t.amountPaise)
            id
        }
    }

    suspend fun deleteTxn(t: Txn) = db.withTransaction {
        db.txns().delete(t.id)
        adjustGoal(t.goalId, -t.amountPaise)
    }

    private suspend fun adjustGoal(goalId: Long?, delta: Long) {
        if (goalId == null || delta == 0L) return
        val g = db.goals().get(goalId) ?: return
        db.goals().upsert(g.copy(savedPaise = (g.savedPaise + delta).coerceAtLeast(0)))
    }

    suspend fun confirm(ids: List<Long>) = db.txns().confirm(ids)

    suspend fun topCategoryIds(type: TxType, limit: Int = 8): List<Long> = db.txns().topCategories(com.kharchacheck.app.data.db.Codes.type(type), limit)

    // ---- Categories ----
    suspend fun saveCategory(c: Category): Long {
        val key = c.key.ifBlank { "custom_" + UUID.randomUUID().toString().take(8) }
        val order = if (c.id == 0L) (db.categories().maxOrder() ?: 0) + 1 else c.sortOrder
        val e = c.copy(key = key, sortOrder = order, custom = c.custom || c.id == 0L).toEntity()
        return db.categories().upsert(e).let { if (e.id != 0L) e.id else it }
    }

    /** Archive hides a category from pickers; history keeps it. Optionally move its entries elsewhere. */
    suspend fun archiveCategory(c: Category, moveTo: Long?) = db.withTransaction {
        if (moveTo != null) db.txns().moveCategory(c.id, moveTo)
        db.categories().upsert(c.copy(archived = true).toEntity())
    }

    // ---- Budgets & goals ----
    suspend fun setCategoryBudget(categoryId: Long, paise: Long) =
        if (paise <= 0) db.budgets().delete(categoryId) else db.budgets().upsert(BudgetEntity(categoryId, paise))

    suspend fun saveGoal(g: Goal): Long { val e = g.toEntity(); return db.goals().upsert(e).let { if (e.id != 0L) e.id else it } }
    suspend fun deleteGoal(id: Long) = db.goals().delete(id)

    // ---- Learning rules ----
    suspend fun rememberPayee(payee: String, categoryId: Long) {
        val p = payee.trim().lowercase(Locale.ENGLISH)
        if (p.length >= 3) db.rules().put(PayeeRuleEntity(pattern = p, categoryId = categoryId))
    }
    suspend fun deleteRule(id: Long) = db.rules().delete(id)

    /** Turns parsed bank text into a draft, applying the user's own rules first. Does not save. */
    suspend fun draftFromParsed(p: SmsParser.Parsed, epochMillis: Long, source: Source): Txn {
        val cats = db.categories().all().map { it.toModel() }
        val rule = db.rules().all().firstOrNull { p.payee.lowercase(Locale.ENGLISH).contains(it.pattern) }
        val ruled = rule?.let { r -> cats.firstOrNull { it.id == r.categoryId && !it.archived } }
        val fallback = cats.first { it.key == DefaultCategories.UNCATEGORISED }
        val cat = ruled ?: cats.firstOrNull { it.key == p.categoryKey } ?: fallback
        val type = when (cat.bucket) { Bucket.INCOME -> TxType.INCOME; Bucket.INVEST -> TxType.INVEST; Bucket.TRANSFER -> TxType.TRANSFER; else -> p.type.takeIf { it == TxType.EXPENSE } ?: TxType.EXPENSE }
        return Txn(
            amountPaise = p.amountPaise, type = type, categoryId = cat.id, epochMillis = epochMillis,
            mode = p.mode, payee = p.payee, source = source, refNo = p.refNo,
            review = if (p.confident || ruled != null) ReviewState.CONFIRMED else ReviewState.NEEDS_REVIEW,
        )
    }

    /** True if this looks like an entry we already have (same bank ref, or same amount within 2 minutes). */
    suspend fun isDuplicate(t: Txn): Boolean {
        if (t.refNo.isNotBlank() && db.txns().countRef(t.refNo) > 0) return true
        return db.txns().countSimilar(t.amountPaise, com.kharchacheck.app.data.db.Codes.type(t.type), t.epochMillis - 120_000, t.epochMillis + 120_000) > 0
    }


    /** Saves rows from a statement in one transaction, either confirmed or as drafts in the Review inbox. */
    /** Returns the new ids, so the import can be undone in one tap. */
    suspend fun importAll(list: List<Txn>, asDrafts: Boolean = false): List<Long> = db.withTransaction {
        val now = System.currentTimeMillis()
        val review = if (asDrafts) ReviewState.NEEDS_REVIEW else ReviewState.CONFIRMED
        list.map { db.txns().upsert(it.copy(id = 0, review = review, createdAt = now, updatedAt = now).toEntity()) }
    }

    /** Deletes many entries (chunked: SQLite allows ~999 variables per statement). */
    suspend fun deleteIds(ids: List<Long>) = db.withTransaction { ids.chunked(500).forEach { db.txns().deleteIds(it) } }

    /** Bulk delete that keeps goal balances right; returns what was deleted so it can be restored. */
    suspend fun deleteMany(list: List<Txn>) = db.withTransaction { list.forEach { t -> db.txns().delete(t.id); adjustGoal(t.goalId, -t.amountPaise) } }

    suspend fun rulesList() = db.rules().all()

    // ---- Repeating entries ----
    /** Turns a saved entry into a monthly rule. Its own month counts as done, so nothing is duplicated. */
    suspend fun repeatMonthly(t: Txn) {
        val date = t.date
        db.recurring().upsert(RecurringRule(
            amountPaise = t.amountPaise, type = t.type, categoryId = t.categoryId, mode = t.mode, payee = t.payee, note = t.note,
            dayOfMonth = date.dayOfMonth, lastMonth = YearMonth.from(date),
        ).toEntity())
    }

    suspend fun setRecurringActive(r: RecurringRule, active: Boolean) { db.recurring().upsert(r.copy(active = active).toEntity()) }
    suspend fun deleteRecurring(id: Long) = db.recurring().delete(id)
    suspend fun saveRecurring(r: RecurringRule) { db.recurring().upsert(r.toEntity()) }

    /** Creates any due monthly entries (into the Review inbox). Returns how many were added. */
    suspend fun generateRecurring(today: LocalDate = LocalDate.now()): Int = db.withTransaction {
        var added = 0
        val zone = ZoneId.systemDefault()
        db.recurring().all().map { it.toModel() }.forEach { rule ->
            val months = RecurringPlanner.dueMonths(rule, today)
            months.forEach { m ->
                val at = RecurringPlanner.dateIn(rule, m).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                db.txns().upsert(Txn(
                    amountPaise = rule.amountPaise, type = rule.type, categoryId = rule.categoryId, epochMillis = at, zoneId = zone.id,
                    mode = rule.mode, payee = rule.payee, note = rule.note, source = Source.RECURRING, review = ReviewState.NEEDS_REVIEW,
                ).toEntity())
                added++
            }
            if (months.isNotEmpty()) db.recurring().upsert(rule.copy(lastMonth = months.last()).toEntity())
        }
        added
    }

    // ---- Danger zone ----
    suspend fun wipeEverything() = db.withTransaction {
        db.txns().clear(); db.budgets().clear(); db.goals().clear(); db.rules().clear(); db.recurring().clear(); db.dues().clear(); db.bills().clear()
        db.wealth().clearHoldings(); db.wealth().clearItems(); db.wealth().clearSnapshots(); db.categories().clear()
    }

    internal val database: KharchaDatabase get() = db

    companion object {
        val MODES = PayMode.entries
    }
}
