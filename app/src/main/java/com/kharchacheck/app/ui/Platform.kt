package com.kharchacheck.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Things only the Activity can do (system pickers, permissions, biometrics). Screens call these. */
class PlatformActions(
    val requestNotifications: () -> Unit,
    val requestSms: (onResult: (Boolean) -> Unit) -> Unit,
    val canUseDeviceLock: () -> Boolean,
    val authenticate: (title: String, onSuccess: () -> Unit) -> Unit,
    val exportCsv: () -> Unit,
    val exportPdf: (fileName: String) -> Unit,
    val exportBackup: (password: CharArray) -> Unit,
    val importBackup: (password: CharArray) -> Unit,
    val openAppInfo: () -> Unit,
    val pasteFromClipboard: () -> String?,
    val emailSupport: () -> Unit,
    val shareText: (String) -> Unit,
    /** Writes a file to private cache and opens the share sheet (email, WhatsApp, Drive…). */
    val shareFile: (bytes: ByteArray, mime: String, name: String) -> Unit,
    /** System folder picker (local, SD card, Google Drive…); permission is kept across restarts. */
    val pickFolder: (onPicked: (android.net.Uri) -> Unit) -> Unit,
    /** Opens the email app with a pre-filled crash report; the user decides whether to send. */
    val emailCrash: (String) -> Unit,
    /** Saves a picture to private cache and opens the share sheet (WhatsApp, Instagram…). */
    val shareImage: (android.graphics.Bitmap, String) -> Unit,
    /** System Photo Picker: no storage permission, user picks exactly one image. */
    val pickImage: (onPicked: (android.net.Uri) -> Unit) -> Unit,
    /** System file picker for statements: PDF or image. */
    val pickDocument: (onPicked: (android.net.Uri) -> Unit) -> Unit,
    /** Decrypts to a temporary private copy and hands it to the user's PDF or gallery app (read-only, one time). */
    val openExternally: (bytes: ByteArray, mime: String, name: String) -> Unit,
    /** Saves a decrypted copy wherever the user chooses. */
    val saveCopy: (bytes: ByteArray, mime: String, name: String) -> Unit,
)

val LocalPlatform = staticCompositionLocalOf<PlatformActions> { error("PlatformActions not provided") }
