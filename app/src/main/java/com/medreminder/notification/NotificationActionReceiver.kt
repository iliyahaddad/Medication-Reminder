package com.medreminder.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medreminder.domain.usecase.MarkDoseSkippedUseCase
import com.medreminder.domain.usecase.MarkDoseTakenUseCase
import com.medreminder.domain.usecase.ObserveSettingsUseCase
import com.medreminder.domain.usecase.SnoozeDoseUseCase
import com.medreminder.scheduler.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @javax.inject.Inject lateinit var markTaken: MarkDoseTakenUseCase
    @javax.inject.Inject lateinit var markSkipped: MarkDoseSkippedUseCase
    @javax.inject.Inject lateinit var snooze: SnoozeDoseUseCase
    @javax.inject.Inject lateinit var settingsUseCase: ObserveSettingsUseCase
    @javax.inject.Inject lateinit var notifier: Notifier
    @javax.inject.Inject lateinit var alarms: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra(Notifier.EXTRA_DOSE_ID, -1L)
        if (doseId < 0) return

        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    DoseAction.TAKEN.name -> {
                        alarms.cancelReminders(listOf(doseId))
                        markTaken(doseId)
                    }
                    DoseAction.SKIP.name -> {
                        alarms.cancelReminders(listOf(doseId))
                        markSkipped(doseId)
                    }
                    DoseAction.SNOOZE.name -> {
                        // Remove the old reminder before scheduling the replacement.
                        alarms.cancelReminders(listOf(doseId))
                        val minutes = settingsUseCase().first().snoozeMinutes
                        snooze(doseId, minutes)
                    }
                }
                notifier.cancelDose(doseId)
            } catch (_: Exception) {
                // Receivers must never crash; the Today screen remains the source of truth.
            } finally {
                result.finish()
            }
        }
    }
}
