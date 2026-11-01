package com.kharchacheck.app

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

/**
 * Settings store (Spec Page 7: "Settings (language, app lock, thresholds) live in DataStore / SharedPreferences").
 */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("kharcha_check_prefs", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = p.getBoolean("onboarding_done", false)
        set(v) = p.edit().putBoolean("onboarding_done", v).apply()

    /** "en" or "hi" (English / Hindi) */
    var language: String
        get() = p.getString("language", "en") ?: "en"
        set(v) = p.edit().putString("language", v).apply()

    /** "system", "light", or "dark" */
    var theme: String
        get() = p.getString("theme", "system") ?: "system"
        set(v) = p.edit().putString("theme", v).apply()

    /** App lock enabled (4-digit PIN) */
    var appLock: Boolean
        get() = p.getBoolean("app_lock", false)
        set(v) = p.edit().putBoolean("app_lock", v).apply()

    /** FLAG_SECURE: blocks screenshots and hides screen in Android recent apps switcher (Spec Page 9 & 10) */
    var hideInRecents: Boolean
        get() = p.getBoolean("hide_in_recents", false)
        set(v) = p.edit().putBoolean("hide_in_recents", v).apply()

    var pinHash: String
        get() = p.getString("pin_hash", "") ?: ""
        set(v) = p.edit().putString("pin_hash", v).apply()

    private val salt: String
        get() = p.getString("pin_salt", null) ?: UUID.randomUUID().toString().also {
            p.edit().putString("pin_salt", it).apply()
        }

    fun hashPin(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun checkPin(pin: String): Boolean = pinHash.isNotBlank() && hashPin(pin) == pinHash

    /** Small leaks threshold in Rupees (Spec Page 2 & 7: default ₹200, tunable in Settings) */
    var smallLeakThresholdRupees: Int
        get() = p.getInt("small_leak_rupees", 200)
        set(v) = p.edit().putInt("small_leak_rupees", v.coerceIn(50, 2000)).apply()

    /** Overspend buffer in Rupees over 1.2x 3-month average (Spec Page 7: default ₹500, tunable) */
    var overspendMinDeltaRupees: Int
        get() = p.getInt("overspend_delta_rupees", 500)
        set(v) = p.edit().putInt("overspend_delta_rupees", v.coerceIn(100, 10000)).apply()

    /** Last used account ID for < 5 second Quick Add (Spec Page 4) */
    var lastUsedAccountId: Long
        get() = p.getLong("last_account_id", 1L)
        set(v) = p.edit().putLong("last_account_id", v).apply()

    var lastUsedMode: String
        get() = p.getString("last_mode", "UPI") ?: "UPI"
        set(v) = p.edit().putString("last_mode", v).apply()

    var weeklyReminderEnabled: Boolean
        get() = p.getBoolean("weekly_reminder", true)
        set(v) = p.edit().putBoolean("weekly_reminder", v).apply()

    var monthEndReminderEnabled: Boolean
        get() = p.getBoolean("month_end_reminder", true)
        set(v) = p.edit().putBoolean("month_end_reminder", v).apply()

    var demoLoaded: Boolean
        get() = p.getBoolean("demo_loaded", false)
        set(v) = p.edit().putBoolean("demo_loaded", v).apply()

    var lastBackfillDays: Int
        get() = p.getInt("last_backfill_days", 0)
        set(v) = p.edit().putInt("last_backfill_days", v).apply()

    fun wipeAll() {
        p.edit().clear().apply()
    }
}
