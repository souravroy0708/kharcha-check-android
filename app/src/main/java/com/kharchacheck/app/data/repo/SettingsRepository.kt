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
    val smsCapture: Boolean = false,
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
            smsCapture = p[K.sms] ?: false,
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
    suspend fun setSmsCapture(v: Boolean) = context.store.edit { it[K.sms] = v }
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
    }

    suspend fun ensureWisdomSeed() = context.store.edit {
        if ((it[K.seed] ?: 0L) == 0L) it[K.seed] = java.security.SecureRandom().nextLong().let { v -> if (v == 0L) 1L else v }
    }

    suspend fun clearAll() = context.store.edit { it.clear() }
}
