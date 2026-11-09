package com.kharchacheck.app.sms

import android.content.Context

/** Standard edition: no SMS permission exists in the manifest at all. Share/paste still works. */
object SmsSupport {
    const val available = false
    val permissions: Array<String> = emptyArray()
    data class ImportResult(val scanned: Int, val added: Int)
    fun hasPermission(context: Context) = false
    suspend fun importInbox(context: Context, days: Int) = ImportResult(0, 0)
}
