package com.medreminder.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.medreminder.worker.MaintenanceWorker

/**
 * Restores the app's scheduling safety net after reboot, app update, clock
 * changes, or timezone changes. WorkManager performs the database work after
 * Android has restored the application process.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val supported = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_SET,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
        if (intent.action !in supported) return

        val request = OneTimeWorkRequestBuilder<MaintenanceWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MaintenanceWorker.BOOT_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
