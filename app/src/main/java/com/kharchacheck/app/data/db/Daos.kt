package com.kharchacheck.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name") fun observe(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories") suspend fun all(): List<CategoryEntity>
    @Query("SELECT COUNT(*) FROM categories") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(list: List<CategoryEntity>)
    @Upsert suspend fun upsert(c: CategoryEntity): Long
    @Query("SELECT MAX(sortOrder) FROM categories") suspend fun maxOrder(): Int?
    @Query("DELETE FROM categories") suspend fun clear()
}

@Dao
interface TxnDao {
    @Query("SELECT * FROM transactions ORDER BY epochMillis DESC") fun observe(): Flow<List<TxnEntity>>
    @Query("SELECT * FROM transactions") suspend fun all(): List<TxnEntity>
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun get(id: Long): TxnEntity?
    @Upsert suspend fun upsert(t: TxnEntity): Long
    @Insert suspend fun insertAll(list: List<TxnEntity>)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM transactions WHERE id IN (:ids)") suspend fun deleteIds(ids: List<Long>)
    @Query("UPDATE transactions SET review = 0 WHERE id IN (:ids)") /* 0 = Codes.REVIEW CONFIRMED */ suspend fun confirm(ids: List<Long>)
    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from") suspend fun moveCategory(from: Long, to: Long)
    @Query("SELECT COUNT(*) FROM transactions WHERE refNo = :ref AND refNo != ''") suspend fun countRef(ref: String): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE amountPaise = :amount AND type = :type AND epochMillis BETWEEN :from AND :to")
    suspend fun countSimilar(amount: Long, type: Int, from: Long, to: Long): Int
    @Query("SELECT categoryId FROM transactions WHERE type = :type GROUP BY categoryId ORDER BY COUNT(*) DESC, MAX(epochMillis) DESC LIMIT :limit")
    suspend fun topCategories(type: Int, limit: Int): List<Long>
    @Query("DELETE FROM transactions") suspend fun clear()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets") fun observe(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets") suspend fun all(): List<BudgetEntity>
    @Upsert suspend fun upsert(b: BudgetEntity)
    @Query("DELETE FROM budgets WHERE categoryId = :id") suspend fun delete(id: Long)
    @Query("UPDATE budgets SET rolloverFrom = :from WHERE categoryId = :id") suspend fun setRollover(id: Long, from: Int)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<BudgetEntity>)
    @Query("DELETE FROM budgets") suspend fun clear()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY id") fun observe(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM goals") suspend fun all(): List<GoalEntity>
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun get(id: Long): GoalEntity?
    @Upsert suspend fun upsert(g: GoalEntity): Long
    @Insert suspend fun insertAll(list: List<GoalEntity>)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM goals") suspend fun clear()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM payee_rules") suspend fun all(): List<PayeeRuleEntity>
    @Query("SELECT * FROM payee_rules") fun observe(): Flow<List<PayeeRuleEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(r: PayeeRuleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<PayeeRuleEntity>)
    @Query("DELETE FROM payee_rules WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM payee_rules") suspend fun clear()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY dayOfMonth") fun observe(): Flow<List<RecurringEntity>>
    @Query("SELECT * FROM recurring") suspend fun all(): List<RecurringEntity>
    @Upsert suspend fun upsert(r: RecurringEntity): Long
    @Insert suspend fun insertAll(list: List<RecurringEntity>)
    @Query("DELETE FROM recurring WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM recurring") suspend fun clear()
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments ORDER BY createdAt DESC") fun observe(): Flow<List<AttachmentEntity>>
    @Query("SELECT * FROM attachments") suspend fun all(): List<AttachmentEntity>
    @Insert suspend fun insert(a: AttachmentEntity): Long
    @Insert suspend fun insertAll(list: List<AttachmentEntity>)
    @Query("UPDATE attachments SET txnId = :txnId WHERE id IN (:ids)") suspend fun link(ids: List<Long>, txnId: Long)
    @Query("DELETE FROM attachments WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE attachments SET kind = :kind, month = :month WHERE id = :id") suspend fun setKind(id: Long, kind: String, month: String?)
    @Query("UPDATE attachments SET mime = :mime, sizeBytes = :size WHERE id = :id") suspend fun setFile(id: Long, mime: String, size: Long)
    /** Receipts picked for an entry that was never saved, or whose entry was deleted, after a grace period. */
    @Query("SELECT * FROM attachments WHERE kind = 'RECEIPT' AND createdAt < :before AND (txnId IS NULL OR txnId NOT IN (SELECT id FROM transactions))")
    suspend fun orphans(before: Long): List<AttachmentEntity>
    @Query("DELETE FROM attachments") suspend fun clear()
}

@Dao
interface DueDao {
    @Query("SELECT * FROM dues ORDER BY settled, epochDay DESC, id DESC") fun observe(): Flow<List<DueEntity>>
    @Query("SELECT * FROM dues") suspend fun all(): List<DueEntity>
    @Upsert suspend fun upsert(d: DueEntity): Long
    @Insert suspend fun insertAll(list: List<DueEntity>)
    @Query("UPDATE dues SET settled = 1 WHERE LOWER(TRIM(person)) = LOWER(TRIM(:person)) AND settled = 0") suspend fun settlePerson(person: String)
    @Query("DELETE FROM dues WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM dues") suspend fun clear()
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY dueDay") fun observe(): Flow<List<BillEntity>>
    @Query("SELECT * FROM bills") suspend fun all(): List<BillEntity>
    @Upsert suspend fun upsert(b: BillEntity): Long
    @Insert suspend fun insertAll(list: List<BillEntity>)
    @Query("DELETE FROM bills WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM bills") suspend fun clear()
}

@Dao
interface WealthDao {
    @Query("SELECT * FROM holdings ORDER BY id") fun holdings(): Flow<List<HoldingEntity>>
    @Query("SELECT * FROM holdings") suspend fun allHoldings(): List<HoldingEntity>
    @Upsert suspend fun upsertHolding(h: HoldingEntity): Long
    @Insert suspend fun insertHoldings(list: List<HoldingEntity>)
    @Query("DELETE FROM holdings WHERE id = :id") suspend fun deleteHolding(id: Long)

    @Query("SELECT * FROM net_items ORDER BY side, valuePaise DESC") fun items(): Flow<List<NetItemEntity>>
    @Query("SELECT * FROM net_items") suspend fun allItems(): List<NetItemEntity>
    @Upsert suspend fun upsertItem(i: NetItemEntity): Long
    @Insert suspend fun insertItems(list: List<NetItemEntity>)
    @Query("DELETE FROM net_items WHERE id = :id") suspend fun deleteItem(id: Long)

    @Query("SELECT * FROM net_snapshots ORDER BY month") fun snapshots(): Flow<List<NetSnapshotEntity>>
    @Query("SELECT * FROM net_snapshots") suspend fun allSnapshots(): List<NetSnapshotEntity>
    @Upsert suspend fun upsertSnapshot(s: NetSnapshotEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSnapshots(list: List<NetSnapshotEntity>)

    @Query("DELETE FROM holdings") suspend fun clearHoldings()
    @Query("DELETE FROM net_items") suspend fun clearItems()
    @Query("DELETE FROM net_snapshots") suspend fun clearSnapshots()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, id") fun observe(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts") suspend fun all(): List<AccountEntity>
    @Upsert suspend fun upsert(a: AccountEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<AccountEntity>)
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE accounts SET sortOrder = CASE WHEN id = :id THEN 0 ELSE sortOrder + 1 END") suspend fun makeFirst(id: Long)
    /** Entries stay when an account is deleted; they just aren't linked any more. */
    @Query("UPDATE transactions SET accountId = NULL WHERE accountId = :id") suspend fun unlinkFrom(id: Long)
    @Query("UPDATE transactions SET toAccountId = NULL WHERE toAccountId = :id") suspend fun unlinkTo(id: Long)
    @Query("UPDATE transactions SET accountId = :id WHERE accountId IS NULL") suspend fun linkUnlinked(id: Long): Int
    @Query("DELETE FROM accounts") suspend fun clear()
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startDay DESC") fun observe(): Flow<List<TripEntity>>
    @Query("SELECT * FROM trips") suspend fun all(): List<TripEntity>
    @Upsert suspend fun upsert(t: TripEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<TripEntity>)
    @Query("DELETE FROM trips WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM trips") suspend fun clear()
}
