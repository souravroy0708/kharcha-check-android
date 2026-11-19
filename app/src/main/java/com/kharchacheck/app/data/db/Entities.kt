package com.kharchacheck.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Goal
import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.ReviewState
import com.kharchacheck.app.core.model.Source
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.core.model.Worth
import com.kharchacheck.app.domain.RecurringRule
import java.time.LocalDate
import java.time.YearMonth

@Entity(tableName = "categories", indices = [Index(value = ["cat_key"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "cat_key") val key: String,
    val name: String,
    val emoji: String,
    val colorArgb: Long,
    val bucket: String,
    val sortOrder: Int,
    val archived: Boolean = false,
    val custom: Boolean = false,
)

/**
 * One money entry. Kept deliberately small, because this is the table that grows every day:
 * money is whole paise (an integer, never a float), the five small enums are 1-byte integer [Codes],
 * and the time zone is stored as "" for India (the usual case) instead of "Asia/Kolkata" on every row.
 */
@Entity(
    tableName = "transactions",
    indices = [Index("epochMillis"), Index("categoryId"), Index("review"), Index("refNo")],
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val type: Int,
    val categoryId: Long,
    val epochMillis: Long,
    val zoneId: String,
    val mode: Int,
    val payee: String,
    val note: String,
    val tags: String,
    val worth: Int,
    val source: Int,
    val review: Int,
    val refNo: String,
    val goalId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "budgets")
data class BudgetEntity(@PrimaryKey val categoryId: Long, val limitPaise: Long)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val targetPaise: Long,
    val savedPaise: Long,
    val targetEpochDay: Long?,
    val archived: Boolean,
)

@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val type: String,
    val categoryId: Long,
    val mode: String,
    val payee: String,
    val note: String,
    val dayOfMonth: Int,
    val lastMonth: String,
    val active: Boolean,
)

@Entity(tableName = "attachments", indices = [Index("txnId"), Index("month")])
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val txnId: Long?,
    val month: String?,
    val label: String,
    val mime: String,
    val fileName: String,
    val originalName: String,
    val sizeBytes: Long,
    val createdAt: Long,
)

@Entity(tableName = "dues", indices = [Index("person"), Index("settled")])
data class DueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val amountPaise: Long,
    val direction: String,
    val note: String,
    val epochDay: Long,
    val settled: Boolean,
    val txnId: Long?,
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val dueDay: Int,
    val amountPaise: Long,
    val remindDaysBefore: Int,
    val paidMonth: String?,
    val active: Boolean,
)

@Entity(tableName = "holdings")
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    val schemeCode: String?,
    val units: String,
    val investedPaise: Long,
    val manualValuePaise: Long,
)

@Entity(tableName = "net_items")
data class NetItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val side: String,
    val name: String,
    val emoji: String,
    val valuePaise: Long,
)

/** Latest net worth per month, for the trend chart. */
@Entity(tableName = "net_snapshots")
data class NetSnapshotEntity(@PrimaryKey val month: String, val netPaise: Long)

/** "Always put payee X in category Y". Learned when the user re-categorises and ticks "always". */
@Entity(tableName = "payee_rules", indices = [Index(value = ["pattern"], unique = true)])
data class PayeeRuleEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val pattern: String, val categoryId: Long)

/**
 * Integer codes for the small enums in [TxnEntity] (1 byte each on disk instead of a 6–12 byte name).
 * NEVER reorder or remove an entry: stored rows depend on these positions. New values go at the END.
 * Backups still write readable names (see BackupManager), so every app version can restore any backup.
 */
object Codes {
    val TYPE = listOf(TxType.EXPENSE, TxType.INCOME, TxType.INVEST, TxType.TRANSFER)
    val MODE = listOf(PayMode.UPI, PayMode.CARD, PayMode.CASH, PayMode.NET_BANKING, PayMode.WALLET, PayMode.OTHER)
    val WORTH = listOf(Worth.NONE, Worth.NEEDED, Worth.WORTH_IT, Worth.REGRET)
    val SOURCE = listOf(Source.MANUAL, Source.SMS, Source.SHARED, Source.RECURRING, Source.STATEMENT)
    val REVIEW = listOf(ReviewState.CONFIRMED, ReviewState.NEEDS_REVIEW)

    fun <E> code(list: List<E>, e: E): Int = list.indexOf(e).coerceAtLeast(0)
    fun <E> of(list: List<E>, code: Int, fallback: E): E = list.getOrElse(code) { fallback }
    fun <E : Enum<E>> codeOfName(list: List<E>, name: String, fallback: E): Int = code(list, list.firstOrNull { it.name == name } ?: fallback)

    fun type(t: TxType) = code(TYPE, t)

    /** Most entries are made in India: store "" instead of repeating the zone name on every row. */
    const val HOME_ZONE = "Asia/Kolkata"
    fun packZone(zone: String) = if (zone == HOME_ZONE) "" else zone
    fun unpackZone(stored: String) = stored.ifEmpty { HOME_ZONE }
}

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E = runCatching { enumValueOf<E>(name) }.getOrDefault(fallback)

fun CategoryEntity.toModel() = Category(id, key, name, emoji, colorArgb, enumOr(bucket, Bucket.WANT), sortOrder, archived, custom)
fun Category.toEntity() = CategoryEntity(id, key, name, emoji, colorArgb, bucket.name, sortOrder, archived, custom)

fun TxnEntity.toModel() = Txn(
    id = id, amountPaise = amountPaise, type = Codes.of(Codes.TYPE, type, TxType.EXPENSE), categoryId = categoryId, epochMillis = epochMillis,
    zoneId = Codes.unpackZone(zoneId), mode = Codes.of(Codes.MODE, mode, PayMode.OTHER), payee = payee, note = note, tags = tags,
    worth = Codes.of(Codes.WORTH, worth, Worth.NONE), source = Codes.of(Codes.SOURCE, source, Source.MANUAL),
    review = Codes.of(Codes.REVIEW, review, ReviewState.CONFIRMED), refNo = refNo, goalId = goalId,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun Txn.toEntity() = TxnEntity(
    id, amountPaise, Codes.code(Codes.TYPE, type), categoryId, epochMillis, Codes.packZone(zoneId), Codes.code(Codes.MODE, mode),
    payee.trim(), note.trim(), tags.trim(), Codes.code(Codes.WORTH, worth), Codes.code(Codes.SOURCE, source),
    Codes.code(Codes.REVIEW, review), refNo.trim(), goalId, createdAt, updatedAt,
)

fun GoalEntity.toModel() = Goal(id, name, emoji, targetPaise, savedPaise, targetEpochDay?.let(LocalDate::ofEpochDay), archived)
fun Goal.toEntity() = GoalEntity(id, name, emoji, targetPaise, savedPaise, targetDate?.toEpochDay(), archived)

fun RecurringEntity.toModel() = RecurringRule(id, amountPaise, enumOr(type, TxType.EXPENSE), categoryId, enumOr(mode, PayMode.OTHER), payee, note,
    dayOfMonth, runCatching { YearMonth.parse(lastMonth) }.getOrDefault(YearMonth.now()), active)
fun RecurringRule.toEntity() = RecurringEntity(id, amountPaise, type.name, categoryId, mode.name, payee, note, dayOfMonth, lastMonth.toString(), active)

fun AttachmentEntity.toModel() = com.kharchacheck.app.core.model.Attachment(
    id, enumOr(kind, com.kharchacheck.app.core.model.AttachmentKind.OTHER), txnId, month?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
    label, mime, fileName, originalName, sizeBytes, createdAt,
)
fun com.kharchacheck.app.core.model.Attachment.toEntity() =
    AttachmentEntity(id, kind.name, txnId, month?.toString(), label, mime, fileName, originalName, sizeBytes, createdAt)

fun DueEntity.toModel() = com.kharchacheck.app.domain.Due(id, person, amountPaise, enumOr(direction, com.kharchacheck.app.domain.DueDirection.THEY_OWE),
    note, LocalDate.ofEpochDay(epochDay), settled, txnId)
fun com.kharchacheck.app.domain.Due.toEntity() = DueEntity(id, person.trim(), amountPaise, direction.name, note, date.toEpochDay(), settled, txnId)
fun BillEntity.toModel() = com.kharchacheck.app.domain.Bill(id, name, emoji, dueDay, amountPaise, remindDaysBefore,
    paidMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }, active)
fun com.kharchacheck.app.domain.Bill.toEntity() = BillEntity(id, name.trim(), emoji, dueDay, amountPaise, remindDaysBefore, paidMonth?.toString(), active)

fun HoldingEntity.toModel() = com.kharchacheck.app.domain.Holding(id, enumOr(kind, com.kharchacheck.app.domain.HoldingKind.OTHER), name, schemeCode, units, investedPaise, manualValuePaise)
fun com.kharchacheck.app.domain.Holding.toEntity() = HoldingEntity(id, kind.name, name.trim(), schemeCode, units.trim(), investedPaise, manualValuePaise)
fun NetItemEntity.toModel() = com.kharchacheck.app.domain.NetItem(id, enumOr(side, com.kharchacheck.app.domain.NetSide.ASSET), name, emoji, valuePaise)
fun com.kharchacheck.app.domain.NetItem.toEntity() = NetItemEntity(id, side.name, name.trim(), emoji, valuePaise)
