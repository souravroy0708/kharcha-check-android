package com.kharchacheck.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.kharchacheck.app.ui.KharchaCheckTheme
import com.kharchacheck.app.ui.Lang
import com.kharchacheck.app.ui.LockScreen
import com.kharchacheck.app.ui.MainShell
import com.kharchacheck.app.ui.OnboardingFlow
import com.kharchacheck.app.ui.PhoneActions

/**
 * Single-Activity entry point for Kharcha Check (Spec Page 9).
 * Handles:
 * - Zero INTERNET permission verification (opens Android App Info -> Permissions so user can verify it)
 * - Optional SMS permissions (`RECEIVE_SMS`, `READ_SMS`) + 30/90/180 day backfill
 * - `ACTION_SEND` share-to-app receiver (share any bank SMS to Kharcha Check when SMS permission is off)
 * - `FLAG_SECURE` toggle (hides app screen in recent apps switcher and blocks screenshots)
 * - Storage Access Framework (`CreateDocument` / `OpenDocument`) for PDF export and passphrase-encrypted `.kcbak` backups
 */
class MainActivity : ComponentActivity() {

    private lateinit var app: AppController
    private var pendingBackupPassphrase: String = ""

    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            app.reload()
            if (grants.values.any { it }) {
                app.runSmsBackfill(30)
            } else {
                app.message = "SMS permission skipped. You can use Quick Add or Share-to-App anytime."
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            ReminderWorker.schedule(this)
        }

    private val createPdfLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    val cats = app.categorySummaries.map {
                        Triple(it.category.localizedName(app.language), it.currentPaise, it.isOverspendFlagged)
                    }
                    PdfReportExporter.writeMonthReviewPdf(
                        outputStream = out,
                        month = app.selectedMonth,
                        spentPaise = app.monthSpentPaise,
                        incomePaise = app.monthIncomePaise,
                        savedPaise = app.monthSavedPaise,
                        avoidablePaise = app.monthAvoidableTotalPaise,
                        smallLeaksCount = app.smallLeaksList.size,
                        smallLeaksPaise = app.smallLeaksList.sumOf { it.amountPaise },
                        categoryRows = cats,
                        topMerchants = app.top10Merchants,
                        selectedTargetNames = app.overspendFlaggedCategories.map { it.category.nameEn },
                        intendedSavingPaise = app.monthAvoidableTotalPaise,
                    )
                }
                app.message = "Month-End Review PDF saved to your chosen folder ✓"
            } catch (e: Exception) {
                app.message = "Could not save PDF: ${e.message}"
            }
        }

    private val createBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
            if (uri == null || pendingBackupPassphrase.isBlank()) return@registerForActivityResult
            contentResolver.openOutputStream(uri)?.let { out ->
                app.exportEncryptedBackup(out, pendingBackupPassphrase)
            }
        }

    private val openBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null || pendingBackupPassphrase.isBlank()) return@registerForActivityResult
            contentResolver.openInputStream(uri)?.let { input ->
                app.importEncryptedBackup(input, pendingBackupPassphrase)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        app = AppController(applicationContext, lifecycleScope)
        ReminderWorker.schedule(this)
        handleIncomingShareIntent(intent)

        val actions = PhoneActions(
            onRequestSmsPermissions = { daysBack ->
                app.prefs.lastBackfillDays = daysBack
                smsPermissionLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
            },
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    ReminderWorker.schedule(this)
                }
            },
            onOpenAppInfoPermissions = {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                    )
                } catch (_: Exception) {
                }
            },
            onExportMonthPdf = {
                val filename = "KharchaCheck-Review-${app.selectedMonth}.pdf"
                createPdfLauncher.launch(filename)
            },
            onExportEncryptedBackup = { passphrase ->
                pendingBackupPassphrase = passphrase
                createBackupLauncher.launch("KharchaCheck-${app.selectedMonth}.kcbak")
            },
            onImportEncryptedBackup = { passphrase ->
                pendingBackupPassphrase = passphrase
                openBackupLauncher.launch(arrayOf("*/*"))
            },
            onCopyText = { label, text ->
                val cb = getSystemService(ClipboardManager::class.java)
                cb?.setPrimaryClip(ClipData.newPlainText(label, text))
                app.message = "$label copied to clipboard ✓"
            },
        )

        setContent {
            val systemDark = isSystemInDarkTheme()
            Lang.current = app.language

            // Apply or clear FLAG_SECURE whenever hideInRecents changes (Spec Page 9 & 10)
            LaunchedEffect(app.hideInRecents) {
                if (app.hideInRecents) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            KharchaCheckTheme(
                dark = when (app.theme) {
                    "dark" -> true
                    "light" -> false
                    else -> systemDark
                }
            ) {
                when {
                    !app.onboardingDone -> OnboardingFlow(app = app, actions = actions)
                    !app.unlocked -> LockScreen(app = app)
                    else -> MainShell(app = app, actions = actions)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        app.reload()
    }

    /**
     * Spec Page 4 & 6: "Share to app: Share a bank message to the app when SMS permission is off; it is parsed the same way."
     */
    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!sharedText.isNullOrBlank()) {
                intent.removeExtra(Intent.EXTRA_TEXT)
                app.sharedSmsPrefill = sharedText
                app.parseSharedOrPastedSms(sharedText, "SHARED-SMS")
            }
        }
    }
}
