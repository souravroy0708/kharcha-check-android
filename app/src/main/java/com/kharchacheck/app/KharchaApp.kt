package com.kharchacheck.app

import android.app.Application
import android.content.Context
import com.kharchacheck.app.data.db.KharchaDatabase
import com.kharchacheck.app.data.files.FileVault
import com.kharchacheck.app.data.repo.AttachmentRepository
import com.kharchacheck.app.data.repo.FinanceRepository
import com.kharchacheck.app.data.repo.SettingsRepository
import com.kharchacheck.app.data.repo.ToolsRepository
import com.kharchacheck.app.data.repo.WealthRepository
import com.kharchacheck.app.export.BackupManager
import com.kharchacheck.app.read.AutoReader
import com.kharchacheck.app.read.TesseractOcr
import com.kharchacheck.app.work.CrashReporter
import com.kharchacheck.app.work.NavRefreshWorker
import com.kharchacheck.app.work.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container. Small app, no DI framework needed; everything is created lazily once. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(context)
    val database: KharchaDatabase by lazy { KharchaDatabase.build(context) }
    val finance: FinanceRepository by lazy { FinanceRepository(database) }
    val backup: BackupManager by lazy { BackupManager(context, database, settings) }
    val files: AttachmentRepository by lazy { AttachmentRepository(database, FileVault(context)) }
    val tools: ToolsRepository by lazy { ToolsRepository(database) }
    val wealth: WealthRepository by lazy { WealthRepository(context, database) }
    val reader: AutoReader by lazy { AutoReader(context, TesseractOcr(context)) }
    val storage: com.kharchacheck.app.data.StorageManager by lazy { com.kharchacheck.app.data.StorageManager(context, database, files, wealth) }
}

class KharchaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        container = AppContainer(this)
        NavRefreshWorker.schedule(this)
        com.kharchacheck.app.work.UpkeepWorker.schedule(this)
        Notifier.createChannels(this)
        container.appScope.launch(Dispatchers.IO) {
            container.finance.ensureSeeded()
            runCatching { container.files.cleanupOrphans() }
            // Temporary decrypted copies made for "Open in another app" are removed on every launch.
            java.io.File(cacheDir, "share").deleteRecursively()
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as KharchaApp).container
