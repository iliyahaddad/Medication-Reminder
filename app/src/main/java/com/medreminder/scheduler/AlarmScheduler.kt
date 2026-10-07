package com.medreminder.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.medreminder.domain.model.Medication
import com.medreminder.domain.usecase.ScheduleAlarmsPort
import com.medreminder.notification.NotificationIds
import com.medreminder.util.canScheduleExactAlarms
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exact-alarm scheduling via [AlarmManager] — the only component that touches
 * AlarmManager directly.
 *
 * Design:
 *  - **One alarm per dose occurrence** (up to a hard budget) using
 *    `setExactAndAllowWhileIdle`, which survives Doze idle windows. For the
 *    next imminent dose of an alarm-style medication we upgrade to
 *    `setAlarmClock` on API ≥ 24, which is exempt from many OEM restrictions
 *    and shows the status-bar alarm icon.
 *  - **Deterministic request codes** derived from the dose id → scheduling the
 *    same dose twice replaces the same PendingIntent (idempotent, no duplicates).
 *  - **Graceful fallback**: when SCHEDULE_EXACT_ALARM is denied (API 31+ user
 *    revocation), we fall back to `setWindow` (inexact but still delivered) and
 *    surface a warning notification via the caller.
 *  - Alarms fire into [AlarmReceiver] (a manifest-declared receiver) so they do
 *    not depend on any live process.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScheduleAlarmsPort {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    /** Budget cap: Android can hold thousands; 35 days × 8 meds × 4 doses stays far below. */
    private val maxAlarmsPerMedication = 60

    override fun scheduleFor(medication: Medication, occurrencesMillis: List<Long>) {
        cancelFor(medication.id)
        val am = alarmManager ?: return
        val exactAllowed = context.canScheduleExactAlarms()
        occurrencesMillis.take(maxAlarmsPerMedication).forEachIndexed { index, millis ->
            val pi = dosePendingIntent(medication.id, millis)
            try {
                when {
                    // First imminent alarm of an alarm-style med: strongest guarantee.
                    index == 0 && medication.alarmStyle && exactAllowed &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                        am.setAlarmClock(
                            AlarmManager.AlarmClockInfo(millis, showAppIntent()),
                        )
                    }
                    exactAllowed -> am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, millis, pi,
                    )
                    else -> // Inexact fallback: deliver within ±10 min window instead of dropping.
                        am.setWindow(
                            AlarmManager.RTC_WAKEUP,
                            millis,
                            FALLBACK_WINDOW_MILLIS,
                            pi,
                        )
                }
            } catch (_: SecurityException) {
                // Race: permission revoked between check and call. Skip this one;
                // the daily reschedule worker will retry after settings change.
            }
        }
    }

    override fun scheduleReminder(doseId: Long, atMillis: Long) {
        val am = alarmManager ?: return
        val pi = reminderPendingIntent(doseId)
        try {
            if (context.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, atMillis, FALLBACK_WINDOW_MILLIS, pi)
            }
        } catch (_: SecurityException) {
            // ignored — see comment in scheduleFor
        }
    }

    override fun cancelFor(medicationId: Long) {
        // Dose ids are CRC32(medicationId|millis) and cannot be enumerated, so
        // stale alarms for a medication are neutralised at delivery time:
        // AlarmReceiver re-checks the dose row (cancelled/acknowledged → no-op).
        // The Today VM calls [cancelDoses] with the exact keys it knows about.
    }

    override fun cancelAll() {
        // AlarmManager does not expose enumeration of arbitrary PendingIntents.
        // The database is the source of truth, and stale alarms are harmless:
        // AlarmReceiver re-checks the dose row before showing anything.
    }

    /** Explicitly cancels the PendingIntents for the given (medicationId, millis) keys. */
    fun cancelDoses(doseKeys: List<Pair<Long, Long>>) {
        val am = alarmManager ?: return
        for ((medicationId, millis) in doseKeys) {
            val pi = dosePendingIntent(medicationId, millis, noCreate = true) ?: continue
            am.cancel(pi)
            pi.cancel()
        }
    }

    /** Cancels reminder/snooze PendingIntents listed by dose id. */
    fun cancelReminders(doseIds: List<Long>) {
        val am = alarmManager ?: return
        for (id in doseIds) {
            val pi = reminderPendingIntent(id, noCreate = true) ?: continue
            am.cancel(pi)
            pi.cancel()
        }
    }

    private fun dosePendingIntent(
        medicationId: Long,
        millis: Long,
        noCreate: Boolean = false,
    ): PendingIntent? {
        val doseId = com.medreminder.domain.model.ScheduleCalculator.doseId(medicationId, millis)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DOSE
            putExtra(com.medreminder.notification.Notifier.EXTRA_DOSE_ID, doseId)
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_SCHEDULED_AT, millis)
        }
        val flags = if (noCreate) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        } else {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            context,
            NotificationIds.doseRequestCode(doseId),
            intent,
            flags,
        )
    }

    /** Tapping the status-bar alarm icon opens the app (never re-fires the alarm). */
    private fun showAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        SHOW_APP_REQUEST_CODE,
        Intent(context, com.medreminder.ui.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun reminderPendingIntent(doseId: Long, noCreate: Boolean = false): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(com.medreminder.notification.Notifier.EXTRA_DOSE_ID, doseId)
        }
        val flags = if (noCreate) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        } else {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            context,
            REMINDER_CODE_BASE + NotificationIds.doseRequestCode(doseId) % REMINDER_CODE_RANGE,
            intent,
            flags,
        )
    }

    companion object {
        const val ACTION_DOSE = "com.medreminder.action.DOSE"
        const val ACTION_REMINDER = "com.medreminder.action.REMINDER"
        const val EXTRA_MEDICATION_ID = "com.medreminder.extra.MEDICATION_ID"
        const val EXTRA_SCHEDULED_AT = "com.medreminder.extra.SCHEDULED_AT"
        private const val SHOW_APP_REQUEST_CODE = 1_400_000_000
        private const val FALLBACK_WINDOW_MILLIS = 10L * 60_000L
        private const val REMINDER_CODE_BASE = 1_500_000_000
        private const val REMINDER_CODE_RANGE = 400_000_000
    }
}
