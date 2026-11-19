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
import com.kharchacheck.app.data.db.Codes
import com.kharchacheck.app.data.db.ACCOUNT_KINDS
import com.kharchacheck.app.data.db.AccountEntity
import com.kharchacheck.app.data.db.TripEntity
import com.kharchacheck.app.domain.AccountKind
import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.ReviewState
import com.kharchacheck.app.core.model.Source
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.core.model.Worth
import com.kharchacheck.app.data.repo.SettingsRepository
import com.kharchacheck.app.data.db.AttachmentEntity
import com.kharchacheck.app.data.files.FileVault
import com.kharchacheck.app.domain.BackupCrypto
import com.kharchacheck.app.domain.StreamCrypto
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Password-protected backup to a file the user picks (Drive, USB, anywhere via the system picker).
 * The app never uploads backups itself; it writes them where the user chooses (Drive, USB, a folder…).
 */
class BackupManager(private val context: Context, private val db: KharchaDatabase, private val settings: SettingsRepository) {

    private val vault = FileVault(context)

    suspend fun export(uri: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { exportTo(it, password) } ?: error("Couldn't write the file")
    }

    /** Writes an encrypted backup to any stream (a picked file, or a file in the auto-backup folder). */
    suspend fun exportTo(out: java.io.OutputStream, password: CharArray, includeFiles: Boolean? = null): Int = withContext(Dispatchers.IO) {
        val withFiles = includeFiles ?: settings.current().backupIncludeFiles
        val txns = db.txns().all()
        val json = JSONObject()
            .put("format", "kharcha-check-backup").put("version", 2).put("exportedAt", System.currentTimeMillis())
            .put("categories", JSONArray(db.categories().all().map { c ->
                JSONObject().put("id", c.id).put("key", c.key).put("name", c.name).put("emoji", c.emoji).put("color", c.colorArgb)
                    .put("bucket", c.bucket).put("order", c.sortOrder).put("archived", c.archived).put("custom", c.custom)
            }))
            .put("txns", JSONArray(txns.map { t ->
                // Readable names, not the compact codes, so any app version can restore this file.
                JSONObject().put("id", t.id).put("amt", t.amountPaise).put("type", Codes.of(Codes.TYPE, t.type, TxType.EXPENSE).name)
                    .put("cat", t.categoryId).put("at", t.epochMillis).put("zone", Codes.unpackZone(t.zoneId))
                    .put("mode", Codes.of(Codes.MODE, t.mode, PayMode.OTHER).name).put("payee", t.payee).put("note", t.note).put("tags", t.tags)
                    .put("worth", Codes.of(Codes.WORTH, t.worth, Worth.NONE).name).put("source", Codes.of(Codes.SOURCE, t.source, Source.MANUAL).name)
                    .put("review", Codes.of(Codes.REVIEW, t.review, ReviewState.CONFIRMED).name).put("ref", t.refNo)
                    .put("acc", t.accountId ?: JSONObject.NULL).put("toAcc", t.toAccountId ?: JSONObject.NULL)
                    .put("goal", t.goalId ?: JSONObject.NULL).put("created", t.createdAt).put("updated", t.updatedAt)
            }))
            .put("budgets", JSONArray(db.budgets().all().map { JSONObject().put("cat", it.categoryId).put("limit", it.limitPaise).put("roll", it.rolloverFrom) }))
            .put("accounts", JSONArray(db.accounts().all().map { a ->
                JSONObject().put("id", a.id).put("name", a.name).put("emoji", a.emoji).put("kind", Codes.of(ACCOUNT_KINDS, a.kind, AccountKind.BANK).name)
                    .put("opening", a.openingPaise).put("archived", a.archived).put("order", a.sortOrder)
            }))
            .put("trips", JSONArray(db.trips().all().map { t ->
                JSONObject().put("id", t.id).put("name", t.name).put("emoji", t.emoji).put("tag", t.tag).put("budget", t.budgetPaise)
                    .put("start", t.startDay).put("end", t.endDay).put("cur", t.currency).put("rate", t.ratePaise)
            }))
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
                    .put("goalPlans", com.kharchacheck.app.data.repo.GoalPlansJson.encode(s.goalPlans))
                    .put("taxRegime", s.taxRegime).put("taxSlab", s.taxSlabPct).put("selfSenior", s.selfSenior).put("parentsSenior", s.parentsSenior)
                    .put("taxAssign", com.kharchacheck.app.data.repo.TaxJson.encodeAssign(s.taxAssign))
                    .put("taxManual", com.kharchacheck.app.data.repo.TaxJson.encodeManual(s.taxManual))
                    .put("subsCancelled", JSONArray(s.subsCancelled.map { (k, d) -> "$k|${d.toEpochDay()}" })).put("subsKept", JSONArray(s.subsKept.toList()))
                    .put("wishlist", com.kharchacheck.app.data.repo.WishJson.encode(s.wishlist)).put("workHours", s.workHoursPerMonth)
                    .put("dependents", s.dependents).put("risk", s.risk).put("emergencySaved", s.emergencySavedPaise)
            })
        // KCB3: a ZIP streamed through chunked encryption. Files first (one at a time, never all in memory),
        // then data.json listing only the attachments whose files actually made it into the backup.
        val included = JSONArray()
        try {
            ZipOutputStream(StreamCrypto.encrypting(out, password)).use { z ->
                if (withFiles) {
                    for (a in db.attachments().all()) {
                        val bytes = runCatching { vault.read(a.fileName) }.getOrNull() ?: continue
                        z.putNextEntry(ZipEntry("files/${a.fileName}")); z.write(bytes); z.closeEntry()
                        included.put(JSONObject().put("id", a.id).put("kind", a.kind).put("txn", a.txnId ?: JSONObject.NULL).put("month", a.month ?: JSONObject.NULL)
                            .put("label", a.label).put("mime", a.mime).put("file", a.fileName).put("orig", a.originalName).put("size", a.sizeBytes).put("created", a.createdAt))
                    }
                    if (vault.exists(FileVault.PROFILE)) runCatching { vault.read(FileVault.PROFILE) }.getOrNull()?.let {
                        z.putNextEntry(ZipEntry("files/${FileVault.PROFILE}")); z.write(it); z.closeEntry()
                    }
                }
                json.put("attachments", included).put("includesFiles", withFiles)
                z.putNextEntry(ZipEntry("data.json")); z.write(json.toString().toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
        } finally { password.fill('\u0000') }
        txns.size
    }

    /** Replaces everything on this phone with the backup's contents. Returns number of entries restored. */
    suspend fun import(uri: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val head = cr.openInputStream(uri)?.use { s -> val b = ByteArray(4); var n = 0; while (n < 4) { val r = s.read(b, n, 4 - n); if (r < 0) break; n += r }; b.copyOf(n) }
            ?: error("Couldn't read the file")
        if (!StreamCrypto.isKcb3(head)) {
            // Older backups (KCB2): one encrypted JSON blob, no files.
            val blob = cr.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't read the file")
            val plain = try { BackupCrypto.decrypt(blob, password) } finally { password.fill('\u0000') }
            return@withContext apply(JSONObject(String(plain, Charsets.UTF_8)), withFiles = false)
        }
        var jsonText: String? = null
        vault.beginRestore()
        try {
            cr.openInputStream(uri)?.use { raw ->
                ZipInputStream(StreamCrypto.decrypting(raw.buffered(), password)).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        when {
                            e.name == "data.json" -> jsonText = String(z.readBytes(), Charsets.UTF_8)
                            e.name.startsWith("files/") -> vault.stage(e.name.removePrefix("files/"), z.readBytes())
                        }
                    }
                }
            } ?: error("Couldn't read the file")
            val j = JSONObject(jsonText ?: throw StreamCrypto.TruncatedException())
            val n = apply(j, withFiles = j.optBoolean("includesFiles"))
            if (j.optBoolean("includesFiles")) vault.commitRestore() else vault.abortRestore()
            n
        } catch (e: Exception) {
            vault.abortRestore()
            throw e
        } finally { password.fill('\u0000') }
    }

    /** Replaces everything on this phone with the backup's data. Returns the number of entries restored. */
    private suspend fun apply(j: JSONObject, withFiles: Boolean): Int {
        require(j.optString("format") == "kharcha-check-backup") { "This isn't a Kharcha Check backup" }

        fun arr(name: String) = j.optJSONArray(name) ?: JSONArray()
        fun <T> JSONArray.mapObj(f: (JSONObject) -> T) = (0 until length()).map { f(getJSONObject(it)) }

        val cats = arr("categories").mapObj { CategoryEntity(it.getLong("id"), it.getString("key"), it.getString("name"), it.getString("emoji"),
            it.getLong("color"), it.getString("bucket"), it.getInt("order"), it.optBoolean("archived"), it.optBoolean("custom")) }
        val txns = arr("txns").mapObj { TxnEntity(it.getLong("id"), it.getLong("amt"), Codes.codeOfName(Codes.TYPE, it.getString("type"), TxType.EXPENSE),
            it.getLong("cat"), it.getLong("at"), Codes.packZone(it.getString("zone")), Codes.codeOfName(Codes.MODE, it.getString("mode"), PayMode.OTHER),
            it.optString("payee"), it.optString("note"), it.optString("tags"), Codes.codeOfName(Codes.WORTH, it.optString("worth", "NONE"), Worth.NONE),
            Codes.codeOfName(Codes.SOURCE, it.optString("source", "MANUAL"), Source.MANUAL),
            Codes.codeOfName(Codes.REVIEW, it.optString("review", "CONFIRMED"), ReviewState.CONFIRMED), it.optString("ref"),
            if (it.isNull("goal")) null else it.getLong("goal"), it.optLong("created"), it.optLong("updated"),
            if (it.isNull("acc")) null else it.optLong("acc").takeIf { v -> v > 0 },
            if (it.isNull("toAcc")) null else it.optLong("toAcc").takeIf { v -> v > 0 }) }
        val budgets = arr("budgets").mapObj { BudgetEntity(it.getLong("cat"), it.getLong("limit"), it.optInt("roll", 0)) }
        // Older backups have no accounts or trips: these are simply empty.
        val accounts = arr("accounts").mapObj { AccountEntity(it.getLong("id"), it.getString("name"), it.optString("emoji", "🏦"),
            Codes.codeOfName(ACCOUNT_KINDS, it.optString("kind", "BANK"), AccountKind.BANK), it.optLong("opening"), it.optBoolean("archived"), it.optInt("order")) }
        val trips = arr("trips").mapObj { TripEntity(it.getLong("id"), it.getString("name"), it.optString("emoji", "✈️"), it.getString("tag"),
            it.optLong("budget"), it.getLong("start"), it.getLong("end"), it.optString("cur", "INR"), it.optLong("rate", 100)) }
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

        val attachments = if (withFiles) arr("attachments").mapObj {
            AttachmentEntity(it.getLong("id"), it.getString("kind"), if (it.isNull("txn")) null else it.getLong("txn"), if (it.isNull("month")) null else it.getString("month"),
                it.optString("label"), it.getString("mime"), it.getString("file"), it.optString("orig"), it.optLong("size"), it.optLong("created"))
        } else null
        db.withTransaction {
            if (attachments != null) { db.attachments().clear(); db.attachments().insertAll(attachments) }
            db.txns().clear(); db.budgets().clear(); db.goals().clear(); db.rules().clear(); db.recurring().clear(); db.dues().clear(); db.bills().clear(); db.categories().clear()
            db.categories().insertAll(cats); db.txns().insertAll(txns); db.budgets().insertAll(budgets)
            db.goals().insertAll(goals); db.rules().insertAll(rules); db.recurring().insertAll(recurring); db.dues().insertAll(dues); db.bills().insertAll(bills)
            db.wealth().clearHoldings(); db.wealth().clearItems(); db.wealth().clearSnapshots()
            db.wealth().insertHoldings(holdings); db.wealth().insertItems(netItems); db.wealth().insertSnapshots(snaps)
            db.accounts().clear(); db.accounts().insertAll(accounts)
            db.trips().clear(); db.trips().insertAll(trips)
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
                goalPlans = com.kharchacheck.app.data.repo.GoalPlansJson.decode(s.optString("goalPlans").ifBlank { null }),
                taxRegime = s.optString("taxRegime", "UNKNOWN"), taxSlabPct = s.optInt("taxSlab", 20),
                selfSenior = s.optBoolean("selfSenior"), parentsSenior = s.optBoolean("parentsSenior"),
                taxAssign = com.kharchacheck.app.data.repo.TaxJson.decodeAssign(s.optString("taxAssign").ifBlank { null }),
                taxManual = com.kharchacheck.app.data.repo.TaxJson.decodeManual(s.optString("taxManual").ifBlank { null }),
                subsCancelled = strs(s.optJSONArray("subsCancelled")).mapNotNull { e ->
                    val i = e.lastIndexOf('|'); if (i < 0) null else e.substring(0, i) to java.time.LocalDate.ofEpochDay(e.substring(i + 1).toLongOrNull() ?: return@mapNotNull null)
                }.toMap(),
                subsKept = strs(s.optJSONArray("subsKept")),
                wishlist = com.kharchacheck.app.data.repo.WishJson.decode(s.optString("wishlist").ifBlank { null }),
                workHoursPerMonth = s.optInt("workHours", 176),
                dependents = s.optBoolean("dependents"), risk = s.optString("risk", "BALANCED"), emergencySavedPaise = s.optLong("emergencySaved"),
            ))
        }
        return txns.size
    }
}
