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

@Entity(
    tableName = "transactions",
    indices = [Index("epochMillis"), Index("categoryId"), Index("review"), Index("refNo")],
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val type: String,
    val categoryId: Long,
    val epochMillis: Long,
    val zoneId: String,
    val mode: String,
    val payee: String,
    val note: String,
    val tags: String,
    val worth: String,
    val source: String,
    val review: String,
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

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E = runCatching { enumValueOf<E>(name) }.getOrDefault(fallback)

fun CategoryEntity.toModel() = Category(id, key, name, emoji, colorArgb, enumOr(bucket, Bucket.WANT), sortOrder, archived, custom)
fun Category.toEntity() = CategoryEntity(id, key, name, emoji, colorArgb, bucket.name, sortOrder, archived, custom)

fun TxnEntity.toModel() = Txn(
    id = id, amountPaise = amountPaise, type = enumOr(type, TxType.EXPENSE), categoryId = categoryId, epochMillis = epochMillis,
    zoneId = zoneId, mode = enumOr(mode, PayMode.OTHER), payee = payee, note = note, tags = tags, worth = enumOr(worth, Worth.NONE),
    source = enumOr(source, Source.MANUAL), review = enumOr(review, ReviewState.CONFIRMED), refNo = refNo, goalId = goalId,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun Txn.toEntity() = TxnEntity(
    id, amountPaise, type.name, categoryId, epochMillis, zoneId, mode.name, payee.trim(), note.trim(), tags.trim(),
    worth.name, source.name, review.name, refNo, goalId, createdAt, updatedAt,
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
