package com.kharchacheck.app.work

import android.content.Context
import android.os.Build
import com.kharchacheck.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZonedDateTime

/**
 * Crash reports the user controls: a crash is saved on the phone, and on the next launch the app offers to
 * email the technical details to the developer. Nothing is sent automatically. The app never logs
 * amounts, payees or notes, so stack traces contain no personal money data.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val app = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter(); error.printStackTrace(PrintWriter(sw))
                File(app.filesDir, FILE).writeText(
                    "Kharcha Check ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Time: ${ZonedDateTime.now()}\nThread: ${thread.name}\n\n" + sw.toString().take(12_000)
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
    fun clear(context: Context) { File(context.filesDir, FILE).delete() }
}
