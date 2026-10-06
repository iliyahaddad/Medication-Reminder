package com.medreminder.scheduler

package com.medreminder.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medreminder.domain.model.AppSettings
import com.medreminder.domain.model.DoseStatus
import com.medreminder.domain.model.MedicationStatus
import com.medreminder.domain.repository.DoseLogRepository
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.domain.repository.SettingsRepository
import com.medreminder.notification.Notifier
import com.medreminder.util.Clock
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Target of every exact alarm (dose + repeat reminder). Runs with Hilt injection
 * so it can talk to the repositories directly — no live app process required.
 *
 * Contract:
 *  - Work happens inside goAsync() + a time-boxed coroutine ([HANDOFF_TIMEOUT_MILLIS]);
 *    a slow DB never ANRs the receiver.
 *  - Every branch re-checks current state: acknowledged or deleted doses are
 *    silently ignored, which makes duplicate/stale alarms harmless.
 *  - Repeat reminders are capped by [AppSettings.maxRepeats] (checked against the
 *    stored repeat_count before scheduling the next one).
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var doseLogRepository: DoseLogRepository
    @Inject lateinit var medicationRepository: MedicationRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var clock: Clock
    @Inject lateinit var alarms: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra(Notifier.EXTRA_DOSE_ID, -1L)
        if (doseId < 0) return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                withTimeoutOrNull(HANDOFF_TIMEOUT_MILLIS) { handle(doseId) }
            } catch (_: Exception) {
                // Receivers must never crash; missed edge cases are covered by
                // the daily reschedule worker and the in-app Today screen.
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private suspend fun handle(doseId: Long) = runBlocking(Dispatchers.IO) {
        val dose = doseLogRepository.getById(doseId) ?: return@runBlocking
        if (dose.status != DoseStatus.SCHEDULED) {
            // Already taken/skipped/missed → cancel any lingering notification.
            notifier.cancelDose(doseId)
            return@runBlocking
        }
        val medication = medicationRepository.getById(dose.medicationId) ?: return@runBlocking
        if (medication.status != MedicationStatus.ACTIVE) return@runBlocking

        val settings = withTimeoutOrNull(SETTINGS_TIMEOUT_MILLIS) {
            settingsRepository.observeSettings().first()
        } ?: AppSettings()

        notifier.showDoseReminder(
            dose = dose,
            medication = medication,
            snoozeMinutes = settings.snoozeMinutes,
            repeatCount = dose.repeatCount,
            fullScreenEnabled = settings.fullScreenAlarm,
            soundEnabled = settings.soundEnabled,
            vibrationEnabled = settings.vibrationEnabled,
        )
        scheduleNextRepeatIfNeeded(doseId, settings)
    }

    private suspend fun scheduleNextRepeatIfNeeded(doseId: Long, settings: AppSettings) {
        if (settings.repeatIntervalMinutes == 0 || settings.maxRepeats == 0) return
        val dose = doseLogRepository.getById(doseId) ?: return
        if (dose.repeatCount >= settings.maxRepeats) return // repeat budget exhausted
        val nextRemindAt = clock.nowMillis() + settings.repeatIntervalMinutes * 60_000L
        doseLogRepository.setRemindUntil(doseId, nextRemindAt)
        doseLogRepository.incrementRepeatCount(doseId)
        alarms.scheduleReminder(doseId, nextRemindAt)
    }

    private companion object {
        const val HANDOFF_TIMEOUT_MILLIS = 8_000L
        const val SETTINGS_TIMEOUT_MILLIS = 2_000L
    }
}
