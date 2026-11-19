package com.kharchacheck.app.work

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kharchacheck.app.container
import com.kharchacheck.app.data.security.SecretBox
import com.kharchacheck.app.domain.BackupRotation
import com.kharchacheck.app.domain.HoldingKind
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Daily: download today's mutual fund NAVs (only when online) and record this month's net worth. */
class NavRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val w = applicationContext.container.wealth
        return try {
            // Only download when the user actually holds a mutual fund; otherwise just record net worth.
            if (w.holdings.first().any { it.kind == HoldingKind.MUTUAL_FUND }) w.refresh(maxAgeHours = 12)
            w.snapshotNow()
            w.trimMemory()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<NavRefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("nav", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

/**
 * Daily, fully offline: records this month's net worth (from the last known prices) and keeps storage compact
 * (folds the database log, rebuilds it only when 10%+ is free space, clears share copies). No network needed.
 */
class UpkeepWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        runCatching { c.wealth.snapshotNow(); c.wealth.trimMemory() }
        c.storage.dailyUpkeep()
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpkeepWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("upkeep", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

/**
 * Daily encrypted backup into the folder the user picked (local, SD card, or a cloud provider such as
 * Google Drive through the system picker). Keeps the newest 7 automatic backups.
 */
class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val s = c.settings.current()
        if (!s.autoBackup || s.backupFolder.isBlank()) return Result.success()
        val password = SecretBox(applicationContext, "backup").get() ?: return Result.success()
        return try {
            val dir = DocumentFile.fromTreeUri(applicationContext, Uri.parse(s.backupFolder)) ?: error("Folder unavailable")
            if (!dir.canWrite()) error("No permission for the backup folder")
            val name = BackupRotation.fileName(LocalDate.now())
            dir.findFile(name)?.delete()
            val file = dir.createFile("application/octet-stream", name) ?: error("Couldn't create the backup file")
            applicationContext.contentResolver.openOutputStream(file.uri, "wt")?.use { c.backup.exportTo(it, password) } ?: error("Couldn't write")
            BackupRotation.toDelete(dir.listFiles().mapNotNull { it.name }).forEach { n -> dir.findFile(n)?.delete() }
            c.settings.setLastBackup(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else {
                Notifier.post(applicationContext, 2200, Notifier.CH_REMINDERS, "Automatic backup didn't run",
                    "Open Settings → Automatic backup to check the folder. Your data on this phone is safe.", "settings")
                Result.success()
            }
        } finally { password.fill('\u0000') }
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("auto-backup", ExistingPeriodicWorkPolicy.KEEP, req)
        }
        fun runNow(ctx: Context) = WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
        fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork("auto-backup")
    }
}
