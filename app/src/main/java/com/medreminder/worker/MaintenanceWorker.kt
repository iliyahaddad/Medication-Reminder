package com.medreminder.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.medreminder.domain.usecase.CheckLowStockUseCase
import com.medreminder.domain.usecase.MarkMissedDosesUseCase
import com.medreminder.domain.usecase.RescheduleAllUseCase
import com.medreminder.notification.Notifier
import com.medreminder.util.canScheduleExactAlarms
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Daily reliability pass. It repairs schedules after process death, marks old
 * unacknowledged doses as missed, and raises low-stock/exact-alarm warnings.
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val markMissed: MarkMissedDosesUseCase,
    private val rescheduleAll: RescheduleAllUseCase,
    private val checkLowStock: CheckLowStockUseCase,
    private val notifier: Notifier,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = runCatching {
        markMissed()
        rescheduleAll()
        checkLowStock().forEach(notifier::showLowStock)
        if (!applicationContext.canScheduleExactAlarms()) {
            notifier.showExactAlarmWarning()
        }
        Result.success()
    }.getOrElse { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
        Result.retry()
    }

    companion object {
        const val PERIODIC_NAME = "medreminder-maintenance"
        const val BOOT_NAME = "medreminder-boot-maintenance"
    }
}
