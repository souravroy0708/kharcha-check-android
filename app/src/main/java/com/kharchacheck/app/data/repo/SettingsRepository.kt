package com.kharchacheck.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val onboarded: Boolean = false,
    val name: String = "",
    val monthlyIncomePaise: Long = 0,
    val monthlyBudgetPaise: Long = 0,
    val age: Int = 0,
    val expectedReturnPct: Int = 12,
    val smallTicketRupees: Int = 200,
    val appLock: Boolean = false,
    val secureScreen: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val reminder: Boolean = true,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    val noSpendDays: Set<Long> = emptySet(),
    val activeChallenge: String? = null,
    val challengeStartDay: Long = 0,
    val challengesWon: Int = 0,
    val celebratedBadges: Set<String> = emptySet(),
    /** Random per install, so each person sees the 100 daily cards in their own order. */
    val wisdomSeed: Long = 0,
    /** Offline reading of screenshots and statements. On by default; nothing leaves the phone either way. */
    val autoRead: Boolean = true,
    val autoBackup: Boolean = false,
    /** Persisted tree URI of the folder the user chose for automatic backups. */
    val backupFolder: String = "",
    val lastBackupAt: Long = 0,
    // Daily money tip + salary plan
    val morningTip: Boolean = true,
    val tipHour: Int = 8,
    val tipMinute: Int = 30,
    val practicedTipDays: Set<Long> = emptySet(),
    val planDone: Set<String> = emptySet(),
    val dependents: Boolean = false,
    val risk: String = "BALANCED",
    val emergencySavedPaise: Long = 0,
    /** "sha256|epochDay" of statement files already imported, to tell the user they've seen this file. */
    val importedFiles: Set<String> = emptySet(),
    /** Entries hidden from everyday view ("private"); shown only after the user unlocks with biometrics/screen lock. */
    val hiddenIds: Set<Long> = emptySet(),
    /** Mask the big amounts on Home (for showing the app to someone). */
    val maskAmounts: Boolean = false,
    /** App-wide text size in percent: 100 normal, 115 large, 130 extra large (for parents, or anyone who prefers it). */
    val textScale: Int = 100,
    /** The user hid the "Getting started" checklist on Home. */
    val startDismissed: Boolean = false,
    /** "yyyy-MM|categoryId|level" budget warnings already sent, so each one is sent once a month. */
    val budgetAlertsSent: Set<String> = emptySet(),
    /** Put statements, receipts and the profile photo inside backups (bigger files, nothing lost on a new phone). */
    val backupIncludeFiles: Boolean = true,
    /** Goal plans (template, today's cost, timeline, reminder day), keyed by goal id. */
    val goalPlans: Map<Long, com.kharchacheck.app.domain.GoalPlan> = emptyMap(),
    // Tax-saving tracker
    val taxRegime: String = "UNKNOWN",
    val taxSlabPct: Int = 20,
    val selfSenior: Boolean = false,
    val parentsSenior: Boolean = false,
    /** Entry id → tax bucket chosen by the user (null = "not for tax"). Entries not listed use the suggestion. */
    val taxAssign: Map<Long, com.kharchacheck.app.domain.TaxBucket?> = emptyMap(),
    /** "fyStart|BUCKET" → amount paid outside the app (EPF from the salary slip, home-loan principal…). */
    val taxManual: Map<String, Long> = emptyMap(),
    // Subscription manager
    val subsCancelled: Map<String, java.time.LocalDate> = emptyMap(),
    val subsKept: Set<String> = emptySet(),
    // Wishlist (cooling-off before buying)
    val wishlist: List<com.kharchacheck.app.domain.WishItem> = emptyList(),
    /** Working hours a month, for "price in hours of your work". */
    val workHoursPerMonth: Int = 176,
    // Daily nudges: `morningTip` is the master switch and tipHour/tipMinute the morning time.
    val noonHour: Int = 13,
    val noonMinute: Int = 0,
    val eveHour: Int = 19,
    val eveMinute: Int = 30,
    val nudgeSlotsOff: Set<String> = emptySet(),
    /** Personal nudge family → day it was last shown (for cooldowns). */
    val nudgeLog: Map<String, java.time.LocalDate> = emptyMap(),
) {
    fun nudgeTimes(): Map<com.kharchacheck.app.domain.NudgeSlot, Pair<Int, Int>> = mapOf(
        com.kharchacheck.app.domain.NudgeSlot.MORNING to (tipHour to tipMinute),
        com.kharchacheck.app.domain.NudgeSlot.AFTERNOON to (noonHour to noonMinute),
        com.kharchacheck.app.domain.NudgeSlot.EVENING to (eveHour to eveMinute),
    )
}

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small preferences, kept on device only (excluded from cloud backup in data_extraction_rules.xml). */
class SettingsRepository(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val income = longPreferencesKey("income")
        val budget = longPreferencesKey("budget")
        val age = intPreferencesKey("age")
        val ret = intPreferencesKey("ret")
        val small = intPreferencesKey("small")
        val lock = booleanPreferencesKey("lock")
        val secure = booleanPreferencesKey("secure")
        val theme = stringPreferencesKey("theme")
        val sms = booleanPreferencesKey("sms")
        val reminder = booleanPreferencesKey("reminder")
        val rh = intPreferencesKey("rh")
        val rm = intPreferencesKey("rm")
        val noSpend = stringSetPreferencesKey("nospend")
        val challenge = stringPreferencesKey("challenge")
        val challengeStart = longPreferencesKey("challenge_start")
        val won = intPreferencesKey("won")
        val celebrated = stringSetPreferencesKey("celebrated")
        val seed = longPreferencesKey("wisdom_seed")
        val autoRead = booleanPreferencesKey("auto_read")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val backupFolder = stringPreferencesKey("backup_folder")
        val lastBackup = longPreferencesKey("last_backup")
        val morningTip = booleanPreferencesKey("morning_tip")
        val tipH = intPreferencesKey("tip_h")
        val tipM = intPreferencesKey("tip_m")
        val practiced = stringSetPreferencesKey("practiced_tips")
        val planDone = stringSetPreferencesKey("plan_done")
        val dependents = booleanPreferencesKey("dependents")
        val risk = stringPreferencesKey("risk")
        val emergencySaved = longPreferencesKey("emergency_saved")
        val importedFiles = stringSetPreferencesKey("imported_files")
        val hidden = stringSetPreferencesKey("hidden_ids")
        val mask = booleanPreferencesKey("mask_amounts")
        val textScale = intPreferencesKey("text_scale")
        val startDismissed = booleanPreferencesKey("start_dismissed")
        val budgetAlerts = stringSetPreferencesKey("budget_alerts")
        val backupFiles = booleanPreferencesKey("backup_files")
        val goalPlans = stringPreferencesKey("goal_plans")
        val taxRegime = stringPreferencesKey("tax_regime")
        val taxSlab = intPreferencesKey("tax_slab")
        val selfSenior = booleanPreferencesKey("self_senior")
        val parentsSenior = booleanPreferencesKey("parents_senior")
        val taxAssign = stringPreferencesKey("tax_assign")
        val taxManual = stringPreferencesKey("tax_manual")
        val subsCancelled = stringSetPreferencesKey("subs_cancelled")
        val subsKept = stringSetPreferencesKey("subs_kept")
        val wishlist = stringPreferencesKey("wishlist")
        val workHours = intPreferencesKey("work_hours")
        val noonH = intPreferencesKey("noon_h")
        val noonM = intPreferencesKey("noon_m")
        val eveH = intPreferencesKey("eve_h")
        val eveM = intPreferencesKey("eve_m")
        val nudgeOff = stringSetPreferencesKey("nudge_off")
        val nudgeLog = stringSetPreferencesKey("nudge_log")
    }

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            onboarded = p[K.onboarded] ?: false,
            name = p[K.name] ?: "",
            monthlyIncomePaise = p[K.income] ?: 0,
            monthlyBudgetPaise = p[K.budget] ?: 0,
            age = p[K.age] ?: 0,
            expectedReturnPct = p[K.ret] ?: 12,
            smallTicketRupees = p[K.small] ?: 200,
            appLock = p[K.lock] ?: false,
            secureScreen = p[K.secure] ?: true,
            theme = runCatching { ThemeMode.valueOf(p[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            reminder = p[K.reminder] ?: true,
            reminderHour = p[K.rh] ?: 21,
            reminderMinute = p[K.rm] ?: 0,
            noSpendDays = p[K.noSpend].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            activeChallenge = p[K.challenge],
            challengeStartDay = p[K.challengeStart] ?: 0,
            challengesWon = p[K.won] ?: 0,
            celebratedBadges = p[K.celebrated].orEmpty(),
            wisdomSeed = p[K.seed] ?: 0,
            autoRead = p[K.autoRead] ?: true,
            autoBackup = p[K.autoBackup] ?: false,
            backupFolder = p[K.backupFolder] ?: "",
            lastBackupAt = p[K.lastBackup] ?: 0,
            morningTip = p[K.morningTip] ?: true,
            tipHour = p[K.tipH] ?: 8,
            tipMinute = p[K.tipM] ?: 30,
            practicedTipDays = p[K.practiced].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            planDone = p[K.planDone].orEmpty(),
            dependents = p[K.dependents] ?: false,
            risk = p[K.risk] ?: "BALANCED",
            emergencySavedPaise = p[K.emergencySaved] ?: 0,
            importedFiles = p[K.importedFiles].orEmpty(),
            hiddenIds = p[K.hidden].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            maskAmounts = p[K.mask] ?: false,
            budgetAlertsSent = p[K.budgetAlerts].orEmpty(),
            backupIncludeFiles = p[K.backupFiles] ?: true,
            goalPlans = GoalPlansJson.decode(p[K.goalPlans]),
            taxRegime = p[K.taxRegime] ?: "UNKNOWN",
            taxSlabPct = p[K.taxSlab] ?: 20,
            selfSenior = p[K.selfSenior] ?: false,
            parentsSenior = p[K.parentsSenior] ?: false,
            taxAssign = TaxJson.decodeAssign(p[K.taxAssign]),
            taxManual = TaxJson.decodeManual(p[K.taxManual]),
            subsCancelled = p[K.subsCancelled].orEmpty().mapNotNull { e ->
                val i = e.lastIndexOf('|'); if (i < 0) null else e.substring(0, i) to java.time.LocalDate.ofEpochDay(e.substring(i + 1).toLongOrNull() ?: return@mapNotNull null)
            }.toMap(),
            subsKept = p[K.subsKept].orEmpty(),
            textScale = (p[K.textScale] ?: 100).coerceIn(85, 150),
            startDismissed = p[K.startDismissed] ?: false,
            wishlist = WishJson.decode(p[K.wishlist]),
            workHoursPerMonth = p[K.workHours] ?: 176,
            noonHour = p[K.noonH] ?: 13, noonMinute = p[K.noonM] ?: 0, eveHour = p[K.eveH] ?: 19, eveMinute = p[K.eveM] ?: 30,
            nudgeSlotsOff = p[K.nudgeOff].orEmpty(),
            nudgeLog = p[K.nudgeLog].orEmpty().mapNotNull { e ->
                val i = e.lastIndexOf('|'); if (i < 0) null else e.substring(0, i) to java.time.LocalDate.ofEpochDay(e.substring(i + 1).toLongOrNull() ?: return@mapNotNull null)
            }.toMap(),
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun completeOnboarding(name: String, incomePaise: Long, budgetPaise: Long) = context.store.edit {
        it[K.onboarded] = true; it[K.name] = name.trim(); it[K.income] = incomePaise; it[K.budget] = budgetPaise
    }
    suspend fun setName(v: String) = context.store.edit { it[K.name] = v.trim() }
    suspend fun setIncome(v: Long) = context.store.edit { it[K.income] = v }
    suspend fun setBudget(v: Long) = context.store.edit { it[K.budget] = v }
    suspend fun setAge(v: Int) = context.store.edit { it[K.age] = v }
    suspend fun setExpectedReturn(v: Int) = context.store.edit { it[K.ret] = v.coerceIn(1, 30) }
    suspend fun setSmallTicket(v: Int) = context.store.edit { it[K.small] = v.coerceIn(10, 5000) }
    suspend fun setAppLock(v: Boolean) = context.store.edit { it[K.lock] = v }
    suspend fun setSecureScreen(v: Boolean) = context.store.edit { it[K.secure] = v }
    suspend fun setTheme(v: ThemeMode) = context.store.edit { it[K.theme] = v.name }
    suspend fun setAutoRead(v: Boolean) = context.store.edit { it[K.autoRead] = v }
    suspend fun setAutoBackup(on: Boolean, folder: String?) = context.store.edit { it[K.autoBackup] = on; if (folder != null) it[K.backupFolder] = folder }
    suspend fun setLastBackup(at: Long) = context.store.edit { it[K.lastBackup] = at }
    suspend fun setMorningTip(on: Boolean, h: Int, m: Int) = context.store.edit { it[K.morningTip] = on; it[K.tipH] = h; it[K.tipM] = m }
    suspend fun togglePracticed(day: Long) = context.store.edit {
        val cur = it[K.practiced].orEmpty(); val d = day.toString()
        it[K.practiced] = if (d in cur) cur - d else cur + d
    }
    suspend fun togglePlanStep(id: String) = context.store.edit {
        val cur = it[K.planDone].orEmpty(); it[K.planDone] = if (id in cur) cur - id else cur + id
    }
    suspend fun setPlanProfile(dependents: Boolean, risk: String, emergencySaved: Long) = context.store.edit {
        it[K.dependents] = dependents; it[K.risk] = risk; it[K.emergencySaved] = emergencySaved
    }
    suspend fun rememberImportedFile(sha: String) = context.store.edit {
        val cur = it[K.importedFiles].orEmpty().filterNot { e -> e.startsWith("$sha|") }.toSet()
        it[K.importedFiles] = (cur + "$sha|${java.time.LocalDate.now().toEpochDay()}").toList().takeLast(200).toSet()
    }
    suspend fun setHidden(ids: Collection<Long>, hidden: Boolean) = context.store.edit {
        val cur = it[K.hidden].orEmpty(); val add = ids.map(Long::toString)
        it[K.hidden] = if (hidden) cur + add else cur - add.toSet()
    }
    /** Remembers sent budget alerts; keeps only the current month's so the set never grows. */
    suspend fun markBudgetAlerts(keys: Set<String>, month: String) = context.store.edit {
        it[K.budgetAlerts] = (it[K.budgetAlerts].orEmpty() + keys).filter { k -> k.startsWith("$month|") }.toSet()
    }
    suspend fun saveGoalPlan(plan: com.kharchacheck.app.domain.GoalPlan) = context.store.edit {
        it[K.goalPlans] = GoalPlansJson.encode(GoalPlansJson.decode(it[K.goalPlans]) + (plan.goalId to plan))
    }
    suspend fun deleteGoalPlan(goalId: Long) = context.store.edit { it[K.goalPlans] = GoalPlansJson.encode(GoalPlansJson.decode(it[K.goalPlans]) - goalId) }
    suspend fun setGoalPlans(all: Map<Long, com.kharchacheck.app.domain.GoalPlan>) = context.store.edit { it[K.goalPlans] = GoalPlansJson.encode(all) }
    suspend fun setTaxProfile(regime: String, slab: Int, selfSenior: Boolean, parentsSenior: Boolean) = context.store.edit {
        it[K.taxRegime] = regime; it[K.taxSlab] = slab; it[K.selfSenior] = selfSenior; it[K.parentsSenior] = parentsSenior
    }
    suspend fun assignTax(id: Long, bucket: com.kharchacheck.app.domain.TaxBucket?) = context.store.edit {
        it[K.taxAssign] = TaxJson.encodeAssign(TaxJson.decodeAssign(it[K.taxAssign]) + (id to bucket))
    }
    suspend fun setTaxManual(key: String, paise: Long) = context.store.edit {
        it[K.taxManual] = TaxJson.encodeManual(TaxJson.decodeManual(it[K.taxManual]) + (key to paise))
    }
    suspend fun setSubscription(key: String, cancelledOn: java.time.LocalDate?, kept: Boolean) = context.store.edit {
        it[K.subsCancelled] = it[K.subsCancelled].orEmpty().filterNot { e -> e.startsWith("$key|") }.toSet() +
            (cancelledOn?.let { d -> setOf("$key|${d.toEpochDay()}") } ?: emptySet())
        it[K.subsKept] = if (kept) it[K.subsKept].orEmpty() + key else it[K.subsKept].orEmpty() - key
    }
    suspend fun updateWishlist(f: (List<com.kharchacheck.app.domain.WishItem>) -> List<com.kharchacheck.app.domain.WishItem>) = context.store.edit {
        it[K.wishlist] = WishJson.encode(f(WishJson.decode(it[K.wishlist])).takeLast(300))
    }
    suspend fun setNudgeTime(slot: com.kharchacheck.app.domain.NudgeSlot, h: Int, m: Int) = context.store.edit {
        when (slot) {
            com.kharchacheck.app.domain.NudgeSlot.MORNING -> { it[K.tipH] = h; it[K.tipM] = m }
            com.kharchacheck.app.domain.NudgeSlot.AFTERNOON -> { it[K.noonH] = h; it[K.noonM] = m }
            com.kharchacheck.app.domain.NudgeSlot.EVENING -> { it[K.eveH] = h; it[K.eveM] = m }
        }
    }
    suspend fun setNudgeSlot(slot: com.kharchacheck.app.domain.NudgeSlot, on: Boolean) = context.store.edit {
        it[K.nudgeOff] = if (on) it[K.nudgeOff].orEmpty() - slot.name else it[K.nudgeOff].orEmpty() + slot.name
    }
    /** Remembers when a personal nudge family was shown; keeps only the last 60 days. */
    suspend fun logNudge(family: String, day: java.time.LocalDate) = context.store.edit {
        val cutoff = day.minusDays(60).toEpochDay()
        it[K.nudgeLog] = (it[K.nudgeLog].orEmpty().filterNot { e -> e.startsWith("$family|") || (e.substringAfterLast('|').toLongOrNull() ?: 0) < cutoff } +
            "$family|${day.toEpochDay()}").toSet()
    }
    suspend fun setWorkHours(h: Int) = context.store.edit { it[K.workHours] = h }
    suspend fun setBackupIncludeFiles(v: Boolean) = context.store.edit { it[K.backupFiles] = v }
    suspend fun setMaskAmounts(v: Boolean) = context.store.edit { it[K.mask] = v }
    suspend fun setTextScale(v: Int) = context.store.edit { it[K.textScale] = v.coerceIn(85, 150) }
    suspend fun setStartDismissed(v: Boolean) = context.store.edit { it[K.startDismissed] = v }
    suspend fun setReminder(on: Boolean, hour: Int, minute: Int) = context.store.edit { it[K.reminder] = on; it[K.rh] = hour; it[K.rm] = minute }

    suspend fun toggleNoSpend(epochDay: Long) = context.store.edit {
        val cur = it[K.noSpend].orEmpty()
        it[K.noSpend] = if (epochDay.toString() in cur) cur - epochDay.toString() else cur + epochDay.toString()
    }

    suspend fun startChallenge(id: String, startDay: Long) = context.store.edit { it[K.challenge] = id; it[K.challengeStart] = startDay }
    suspend fun endChallenge(won: Boolean) = context.store.edit {
        it.remove(K.challenge)
        if (won) it[K.won] = (it[K.won] ?: 0) + 1
    }
    suspend fun markCelebrated(ids: Set<String>) = context.store.edit { it[K.celebrated] = it[K.celebrated].orEmpty() + ids }

    /** Used by backup restore. */
    suspend fun restore(s: Settings) = context.store.edit {
        it[K.name] = s.name; it[K.income] = s.monthlyIncomePaise; it[K.budget] = s.monthlyBudgetPaise; it[K.age] = s.age
        it[K.ret] = s.expectedReturnPct; it[K.small] = s.smallTicketRupees; it[K.noSpend] = s.noSpendDays.map(Long::toString).toSet()
        it[K.won] = s.challengesWon; it[K.celebrated] = s.celebratedBadges
        it[K.practiced] = s.practicedTipDays.map(Long::toString).toSet(); it[K.planDone] = s.planDone
        it[K.dependents] = s.dependents; it[K.risk] = s.risk; it[K.emergencySaved] = s.emergencySavedPaise
        it[K.hidden] = s.hiddenIds.map(Long::toString).toSet()
        it[K.goalPlans] = GoalPlansJson.encode(s.goalPlans)
        it[K.taxRegime] = s.taxRegime; it[K.taxSlab] = s.taxSlabPct; it[K.selfSenior] = s.selfSenior; it[K.parentsSenior] = s.parentsSenior
        it[K.taxAssign] = TaxJson.encodeAssign(s.taxAssign); it[K.taxManual] = TaxJson.encodeManual(s.taxManual)
        it[K.subsCancelled] = s.subsCancelled.map { (k, d) -> "$k|${d.toEpochDay()}" }.toSet(); it[K.subsKept] = s.subsKept
        it[K.wishlist] = WishJson.encode(s.wishlist); it[K.workHours] = s.workHoursPerMonth
    }

    suspend fun ensureWisdomSeed() = context.store.edit {
        if ((it[K.seed] ?: 0L) == 0L) it[K.seed] = java.security.SecureRandom().nextLong().let { v -> if (v == 0L) 1L else v }
    }

    suspend fun clearAll() = context.store.edit { it.clear() }
}

/** Goal plans as compact JSON inside one preference (no database migration needed). */
object GoalPlansJson {
    fun encode(m: Map<Long, com.kharchacheck.app.domain.GoalPlan>): String = org.json.JSONArray(m.values.map { p ->
        org.json.JSONObject().put("id", p.goalId).put("kind", p.kind.name).put("cost", p.todayCostPaise).put("years", p.years)
            .put("infl", p.inflationPct).put("step", p.stepUp).put("day", p.sipDay).put("age", p.childAge ?: -1)
    }).toString()

    fun decode(s: String?): Map<Long, com.kharchacheck.app.domain.GoalPlan> = runCatching {
        val a = org.json.JSONArray(s ?: return emptyMap())
        (0 until a.length()).map { a.getJSONObject(it) }.associate { o ->
            val kind = runCatching { com.kharchacheck.app.domain.GoalKind.valueOf(o.getString("kind")) }.getOrDefault(com.kharchacheck.app.domain.GoalKind.CUSTOM)
            o.getLong("id") to com.kharchacheck.app.domain.GoalPlan(o.getLong("id"), kind, o.getLong("cost"), o.getInt("years"), o.optDouble("infl", kind.inflationPct),
                o.optBoolean("step", true), o.optInt("day", 5), o.optInt("age", -1).takeIf { it >= 0 })
        }
    }.getOrDefault(emptyMap())
}

object TaxJson {
    fun encodeAssign(m: Map<Long, com.kharchacheck.app.domain.TaxBucket?>): String =
        org.json.JSONObject().apply { m.forEach { (id, b) -> put(id.toString(), b?.name ?: "NONE") } }.toString()
    fun decodeAssign(s: String?): Map<Long, com.kharchacheck.app.domain.TaxBucket?> = runCatching {
        val o = org.json.JSONObject(s ?: return emptyMap())
        o.keys().asSequence().associate { k -> k.toLong() to o.getString(k).let { v -> com.kharchacheck.app.domain.TaxBucket.entries.firstOrNull { it.name == v } } }
    }.getOrDefault(emptyMap())
    fun encodeManual(m: Map<String, Long>): String = org.json.JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }.toString()
    fun decodeManual(s: String?): Map<String, Long> = runCatching {
        val o = org.json.JSONObject(s ?: return emptyMap()); o.keys().asSequence().associateWith { o.getLong(it) }
    }.getOrDefault(emptyMap())
}

object WishJson {
    fun encode(l: List<com.kharchacheck.app.domain.WishItem>): String = org.json.JSONArray(l.map { w ->
        org.json.JSONObject().put("id", w.id).put("name", w.name).put("price", w.pricePaise).put("added", w.addedOn.toEpochDay()).put("wait", w.waitDays)
            .put("uses", w.usesPerMonth).put("want", w.wantSignals).put("decision", w.decision.name).put("decided", w.decidedOn?.toEpochDay() ?: -1)
    }).toString()
    fun decode(s: String?): List<com.kharchacheck.app.domain.WishItem> = runCatching {
        val a = org.json.JSONArray(s ?: return emptyList())
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            com.kharchacheck.app.domain.WishItem(o.getLong("id"), o.getString("name"), o.getLong("price"), java.time.LocalDate.ofEpochDay(o.getLong("added")),
                o.getInt("wait"), o.optInt("uses"), o.optInt("want"),
                runCatching { com.kharchacheck.app.domain.WishDecision.valueOf(o.getString("decision")) }.getOrDefault(com.kharchacheck.app.domain.WishDecision.WAITING),
                o.optLong("decided", -1).takeIf { it >= 0 }?.let(java.time.LocalDate::ofEpochDay))
        }
    }.getOrDefault(emptyList())
}
