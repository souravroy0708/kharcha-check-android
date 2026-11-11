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
    /** "yyyy-MM|categoryId|level" budget warnings already sent, so each one is sent once a month. */
    val budgetAlertsSent: Set<String> = emptySet(),
    /** Put statements, receipts and the profile photo inside backups (bigger files, nothing lost on a new phone). */
    val backupIncludeFiles: Boolean = true,
)

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
        val budgetAlerts = stringSetPreferencesKey("budget_alerts")
        val backupFiles = booleanPreferencesKey("backup_files")
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
    suspend fun setBackupIncludeFiles(v: Boolean) = context.store.edit { it[K.backupFiles] = v }
    suspend fun setMaskAmounts(v: Boolean) = context.store.edit { it[K.mask] = v }
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
    }

    suspend fun ensureWisdomSeed() = context.store.edit {
        if ((it[K.seed] ?: 0L) == 0L) it[K.seed] = java.security.SecureRandom().nextLong().let { v -> if (v == 0L) 1L else v }
    }

    suspend fun clearAll() = context.store.edit { it.clear() }
}
