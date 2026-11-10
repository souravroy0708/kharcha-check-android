package com.kharchacheck.app.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.kharchacheck.app.data.db.BudgetEntity
import com.kharchacheck.app.data.db.BillEntity
import com.kharchacheck.app.data.db.CategoryEntity
import com.kharchacheck.app.data.db.DueEntity
import com.kharchacheck.app.data.db.GoalEntity
import com.kharchacheck.app.data.db.HoldingEntity
import com.kharchacheck.app.data.db.KharchaDatabase
import com.kharchacheck.app.data.db.NetItemEntity
import com.kharchacheck.app.data.db.NetSnapshotEntity
import com.kharchacheck.app.data.db.PayeeRuleEntity
import com.kharchacheck.app.data.db.RecurringEntity
import com.kharchacheck.app.data.db.TxnEntity
import com.kharchacheck.app.data.repo.SettingsRepository
import com.kharchacheck.app.domain.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Password-protected backup to a file the user picks (Drive, USB, anywhere via the system picker).
 * The app never uploads backups itself; it writes them where the user chooses (Drive, USB, a folder…).
 */
class BackupManager(private val context: Context, private val db: KharchaDatabase, private val settings: SettingsRepository) {

    suspend fun export(uri: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { exportTo(it, password) } ?: error("Couldn't write the file")
    }

    /** Writes an encrypted backup to any stream (a picked file, or a file in the auto-backup folder). */
    suspend fun exportTo(out: java.io.OutputStream, password: CharArray): Int = withContext(Dispatchers.IO) {
        val txns = db.txns().all()
        val json = JSONObject()
            .put("format", "kharcha-check-backup").put("version", 2).put("exportedAt", System.currentTimeMillis())
            .put("categories", JSONArray(db.categories().all().map { c ->
                JSONObject().put("id", c.id).put("key", c.key).put("name", c.name).put("emoji", c.emoji).put("color", c.colorArgb)
                    .put("bucket", c.bucket).put("order", c.sortOrder).put("archived", c.archived).put("custom", c.custom)
            }))
            .put("txns", JSONArray(txns.map { t ->
                JSONObject().put("id", t.id).put("amt", t.amountPaise).put("type", t.type).put("cat", t.categoryId).put("at", t.epochMillis)
                    .put("zone", t.zoneId).put("mode", t.mode).put("payee", t.payee).put("note", t.note).put("tags", t.tags)
                    .put("worth", t.worth).put("source", t.source).put("review", t.review).put("ref", t.refNo)
                    .put("goal", t.goalId ?: JSONObject.NULL).put("created", t.createdAt).put("updated", t.updatedAt)
            }))
            .put("budgets", JSONArray(db.budgets().all().map { JSONObject().put("cat", it.categoryId).put("limit", it.limitPaise) }))
            .put("goals", JSONArray(db.goals().all().map { g ->
                JSONObject().put("id", g.id).put("name", g.name).put("emoji", g.emoji).put("target", g.targetPaise).put("saved", g.savedPaise)
                    .put("day", g.targetEpochDay ?: JSONObject.NULL).put("archived", g.archived)
            }))
            .put("recurring", JSONArray(db.recurring().all().map { r ->
                JSONObject().put("id", r.id).put("amt", r.amountPaise).put("type", r.type).put("cat", r.categoryId).put("mode", r.mode)
                    .put("payee", r.payee).put("note", r.note).put("day", r.dayOfMonth).put("last", r.lastMonth).put("active", r.active)
            }))
            .put("dues", JSONArray(db.dues().all().map { d ->
                JSONObject().put("id", d.id).put("person", d.person).put("amt", d.amountPaise).put("dir", d.direction).put("note", d.note)
                    .put("day", d.epochDay).put("settled", d.settled).put("txn", d.txnId ?: JSONObject.NULL)
            }))
            .put("bills", JSONArray(db.bills().all().map { b ->
                JSONObject().put("id", b.id).put("name", b.name).put("emoji", b.emoji).put("due", b.dueDay).put("amt", b.amountPaise)
                    .put("remind", b.remindDaysBefore).put("paid", b.paidMonth ?: JSONObject.NULL).put("active", b.active)
            }))
            .put("holdings", JSONArray(db.wealth().allHoldings().map { h ->
                JSONObject().put("id", h.id).put("kind", h.kind).put("name", h.name).put("code", h.schemeCode ?: JSONObject.NULL).put("units", h.units)
                    .put("invested", h.investedPaise).put("value", h.manualValuePaise)
            }))
            .put("netItems", JSONArray(db.wealth().allItems().map { i ->
                JSONObject().put("id", i.id).put("side", i.side).put("name", i.name).put("emoji", i.emoji).put("value", i.valuePaise)
            }))
            .put("netSnapshots", JSONArray(db.wealth().allSnapshots().map { JSONObject().put("m", it.month).put("v", it.netPaise) }))
            .put("rules", JSONArray(db.rules().all().map { JSONObject().put("id", it.id).put("pattern", it.pattern).put("cat", it.categoryId) }))
            .put("settings", settings.current().let { s ->
                JSONObject().put("name", s.name).put("income", s.monthlyIncomePaise).put("budget", s.monthlyBudgetPaise).put("age", s.age)
                    .put("ret", s.expectedReturnPct).put("small", s.smallTicketRupees).put("won", s.challengesWon)
                    .put("nospend", JSONArray(s.noSpendDays.toList())).put("celebrated", JSONArray(s.celebratedBadges.toList()))
                    .put("practiced", JSONArray(s.practicedTipDays.toList())).put("planDone", JSONArray(s.planDone.toList()))
                    .put("hidden", JSONArray(s.hiddenIds.toList()))
                    .put("dependents", s.dependents).put("risk", s.risk).put("emergencySaved", s.emergencySavedPaise)
            })
        val blob = BackupCrypto.encrypt(json.toString().toByteArray(Charsets.UTF_8), password)
        password.fill('\u0000')
        out.write(blob)
        txns.size
    }

    /** Replaces everything on this phone with the backup's contents. Returns number of entries restored. */
    suspend fun import(uri: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val blob = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't read the file")
        val plain = try { BackupCrypto.decrypt(blob, password) } finally { password.fill('\u0000') }
        val j = JSONObject(String(plain, Charsets.UTF_8))
        require(j.optString("format") == "kharcha-check-backup") { "This isn't a Kharcha Check backup" }

        fun arr(name: String) = j.optJSONArray(name) ?: JSONArray()
        fun <T> JSONArray.mapObj(f: (JSONObject) -> T) = (0 until length()).map { f(getJSONObject(it)) }

        val cats = arr("categories").mapObj { CategoryEntity(it.getLong("id"), it.getString("key"), it.getString("name"), it.getString("emoji"),
            it.getLong("color"), it.getString("bucket"), it.getInt("order"), it.optBoolean("archived"), it.optBoolean("custom")) }
        val txns = arr("txns").mapObj { TxnEntity(it.getLong("id"), it.getLong("amt"), it.getString("type"), it.getLong("cat"), it.getLong("at"),
            it.getString("zone"), it.getString("mode"), it.optString("payee"), it.optString("note"), it.optString("tags"), it.optString("worth", "NONE"),
            it.optString("source", "MANUAL"), it.optString("review", "CONFIRMED"), it.optString("ref"),
            if (it.isNull("goal")) null else it.getLong("goal"), it.optLong("created"), it.optLong("updated")) }
        val budgets = arr("budgets").mapObj { BudgetEntity(it.getLong("cat"), it.getLong("limit")) }
        val goals = arr("goals").mapObj { GoalEntity(it.getLong("id"), it.getString("name"), it.getString("emoji"), it.getLong("target"),
            it.getLong("saved"), if (it.isNull("day")) null else it.getLong("day"), it.optBoolean("archived")) }
        val recurring = arr("recurring").mapObj { RecurringEntity(it.getLong("id"), it.getLong("amt"), it.getString("type"), it.getLong("cat"),
            it.getString("mode"), it.optString("payee"), it.optString("note"), it.getInt("day"), it.getString("last"), it.optBoolean("active", true)) }
        val dues = arr("dues").mapObj { DueEntity(it.getLong("id"), it.getString("person"), it.getLong("amt"), it.getString("dir"), it.optString("note"),
            it.getLong("day"), it.optBoolean("settled"), if (it.isNull("txn")) null else it.getLong("txn")) }
        val bills = arr("bills").mapObj { BillEntity(it.getLong("id"), it.getString("name"), it.optString("emoji", "🧾"), it.getInt("due"), it.optLong("amt"),
            it.optInt("remind", 3), if (it.isNull("paid")) null else it.getString("paid"), it.optBoolean("active", true)) }
        val holdings = arr("holdings").mapObj { HoldingEntity(it.getLong("id"), it.getString("kind"), it.getString("name"),
            if (it.isNull("code")) null else it.getString("code"), it.optString("units"), it.optLong("invested"), it.optLong("value")) }
        val netItems = arr("netItems").mapObj { NetItemEntity(it.getLong("id"), it.getString("side"), it.getString("name"), it.optString("emoji", "💰"), it.getLong("value")) }
        val snaps = arr("netSnapshots").mapObj { NetSnapshotEntity(it.getString("m"), it.getLong("v")) }
        val rules = arr("rules").mapObj { PayeeRuleEntity(it.getLong("id"), it.getString("pattern"), it.getLong("cat")) }

        db.withTransaction {
            db.txns().clear(); db.budgets().clear(); db.goals().clear(); db.rules().clear(); db.recurring().clear(); db.dues().clear(); db.bills().clear(); db.categories().clear()
            db.categories().insertAll(cats); db.txns().insertAll(txns); db.budgets().insertAll(budgets)
            db.goals().insertAll(goals); db.rules().insertAll(rules); db.recurring().insertAll(recurring); db.dues().insertAll(dues); db.bills().insertAll(bills)
            db.wealth().clearHoldings(); db.wealth().clearItems(); db.wealth().clearSnapshots()
            db.wealth().insertHoldings(holdings); db.wealth().insertItems(netItems); db.wealth().insertSnapshots(snaps)
        }
        j.optJSONObject("settings")?.let { s ->
            val cur = settings.current()
            fun longs(a: JSONArray?) = a?.let { x -> (0 until x.length()).map { x.getLong(it) }.toSet() } ?: emptySet()
            fun strs(a: JSONArray?) = a?.let { x -> (0 until x.length()).map { x.getString(it) }.toSet() } ?: emptySet()
            settings.restore(cur.copy(
                name = s.optString("name", cur.name), monthlyIncomePaise = s.optLong("income"), monthlyBudgetPaise = s.optLong("budget"),
                age = s.optInt("age"), expectedReturnPct = s.optInt("ret", 12), smallTicketRupees = s.optInt("small", 200),
                challengesWon = s.optInt("won"), noSpendDays = longs(s.optJSONArray("nospend")), celebratedBadges = strs(s.optJSONArray("celebrated")),
                practicedTipDays = longs(s.optJSONArray("practiced")), planDone = strs(s.optJSONArray("planDone")),
                hiddenIds = longs(s.optJSONArray("hidden")),
                dependents = s.optBoolean("dependents"), risk = s.optString("risk", "BALANCED"), emergencySavedPaise = s.optLong("emergencySaved"),
            ))
        }
        txns.size
    }
}
