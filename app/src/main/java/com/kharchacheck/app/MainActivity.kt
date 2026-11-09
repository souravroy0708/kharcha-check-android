package com.kharchacheck.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharchacheck.app.data.repo.ThemeMode
import com.kharchacheck.app.sms.SmsSupport
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.KharchaRoot
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.PlatformActions
import com.kharchacheck.app.ui.theme.KharchaTheme
import com.kharchacheck.app.work.Notifier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import java.time.YearMonth

/**
 * Single activity. FragmentActivity (a ComponentActivity) because BiometricPrompt needs it.
 * Owns: system pickers, permissions, the app lock, FLAG_SECURE and incoming share / notification intents.
 */
class MainActivity : FragmentActivity() {

    private val vm: AppViewModel by viewModels()
    /** Buffered so a share/notification that arrives before the UI is ready is not lost. */
    private val navRequests = Channel<String>(Channel.BUFFERED)

    private var pendingPassword: CharArray? = null
    private var smsCallback: ((Boolean) -> Unit)? = null
    private var stoppedAt = 0L
    private var imageCallback: ((Uri) -> Unit)? = null
    private var docCallback: ((Uri) -> Unit)? = null
    private var pendingCopy: ByteArray? = null
    private var folderCallback: ((Uri) -> Unit)? = null
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            folderCallback?.invoke(uri)
        }
        folderCallback = null
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.say("No problem. You can turn reminders on later in Settings.")
    }
    private val smsPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        smsCallback?.invoke(res.values.all { it }); smsCallback = null
    }
    private val csvDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::exportCsv) }
    private val pdfDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let(vm::exportPdf) }
    private val backupDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = pendingPassword; pendingPassword = null
        if (uri != null && pw != null) vm.exportBackup(uri, pw) else pw?.fill('\u0000')
    }
    private val restoreDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val pw = pendingPassword; pendingPassword = null
        if (uri != null && pw != null) vm.importBackup(uri, pw) else pw?.fill('\u0000')
    }

    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { imageCallback?.invoke(it) }; imageCallback = null
    }
    private val docPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { docCallback?.invoke(it) }; docCallback = null
    }
    private val copyDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = pendingCopy; pendingCopy = null
        if (uri != null && bytes != null) {
            runCatching { contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } }
                .onSuccess { vm.say("Copy saved ✓") }.onFailure { vm.say("Couldn't save the copy") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        // Secure by default until settings load, so nothing sensitive is ever visible in Recents.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        publishShortcut()

        val actions = PlatformActions(
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(this)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            requestSms = { cb ->
                if (!SmsSupport.available) cb(false)
                else if (SmsSupport.hasPermission(this)) cb(true)
                else { smsCallback = cb; smsPermission.launch(SmsSupport.permissions) }
            },
            canUseDeviceLock = {
                BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
            },
            authenticate = { title, ok -> authenticate(title, ok) },
            exportCsv = { csvDoc.launch("KharchaCheck-${java.time.LocalDate.now()}.csv") },
            exportPdf = { name -> pdfDoc.launch(name) },
            exportBackup = { pw -> pendingPassword = pw; backupDoc.launch("KharchaCheck-${java.time.LocalDate.now()}.kcbak") },
            importBackup = { pw -> pendingPassword = pw; restoreDoc.launch(arrayOf("*/*")) },
            openAppInfo = {
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) }
            },
            pasteFromClipboard = {
                getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            },
            emailSupport = {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.SUPPORT_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, "Kharcha Check ${BuildConfig.VERSION_NAME} feedback")
                runCatching { startActivity(i) }
            },
            pickImage = { cb ->
                imageCallback = cb
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            pickDocument = { cb -> docCallback = cb; docPicker.launch(arrayOf("application/pdf", "image/*", "text/csv", "text/comma-separated-values", "text/plain", "text/tab-separated-values", "application/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
            openExternally = { bytes, mime, name -> openExternally(bytes, mime, name) },
            saveCopy = { bytes, _, name -> pendingCopy = bytes; copyDoc.launch(name) },
            shareImage = { bmp, text ->
                runCatching {
                    val dir = File(cacheDir, "share").apply { mkdirs() }
                    val file = File(dir, "kharcha-wrapped.png")
                    file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
                    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(send, "Share"))
                }.onFailure { vm.say("Couldn't share the image") }
            },
            pickFolder = { cb -> folderCallback = cb; folderPicker.launch(null) },
            emailCrash = { report ->
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.SUPPORT_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, "Kharcha Check crash report ${BuildConfig.VERSION_NAME}").putExtra(Intent.EXTRA_TEXT, report)
                runCatching { startActivity(i) }
            },
            shareText = { text ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                runCatching { startActivity(Intent.createChooser(send, "Share")) }
            },
        )

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings?.theme) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; else -> systemDark }
            LaunchedEffect(settings?.secureScreen) {
                if (settings?.secureScreen == false) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            }
            KharchaTheme(dark = dark) {
                CompositionLocalProvider(LocalPlatform provides actions) {
                    KharchaRoot(vm, navRequests.receiveAsFlow())
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after 30 seconds in the background.
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > 30_000) vm.lock()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            intent.removeExtra(Intent.EXTRA_TEXT)
            if (text.isNotEmpty()) vm.draftFromText(text) { navRequests.trySend("draft") }
        }
        val statementTypes = setOf("application/pdf", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        if ((intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW) && intent.type in statementTypes) {
            val uri = if (intent.action == Intent.ACTION_SEND) IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) else intent.data
            intent.removeExtra(Intent.EXTRA_STREAM); intent.data = null
            if (uri != null) vm.importStatementFile(uri) { id -> navRequests.trySend("import:$id") }
        }
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            intent.removeExtra(Intent.EXTRA_STREAM)
            if (uri != null) vm.draftFromImage(uri) { navRequests.trySend("draft") }
        }
        intent.getStringExtra(Notifier.EXTRA_OPEN)?.let { target ->
            intent.removeExtra(Notifier.EXTRA_OPEN)
            if (target == "report") vm.setReportMonth(YearMonth.now().minusMonths(1))
            navRequests.trySend(target)
        }
    }

    /** Writes a temporary decrypted copy into app cache (wiped on every launch) and opens it read-only. */
    private fun openExternally(bytes: ByteArray, mime: String, name: String) {
        runCatching {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val safe = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "file" }
            val file = File(dir, safe).apply { writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(view, "Open with"))
        }.onFailure { vm.say(if (it is ActivityNotFoundException) "No app found to open this file" else "Couldn't open the file") }
    }

    private fun authenticate(title: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Use your fingerprint, face or screen lock")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    private fun publishShortcut() {
        val add = ShortcutInfoCompat.Builder(this, "add")
            .setShortLabel(getString(R.string.shortcut_add))
            .setLongLabel(getString(R.string.shortcut_add_long))
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(Notifier.EXTRA_OPEN, "add"))
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(this, add) }
    }
}
