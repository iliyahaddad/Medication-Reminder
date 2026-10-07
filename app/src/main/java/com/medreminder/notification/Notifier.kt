package com.medreminder.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.medreminder.R
import com.medreminder.domain.model.DoseLog
import com.medreminder.domain.model.Medication
import com.medreminder.notification.NotificationIds.Companion.actionRequestCode
import com.medreminder.notification.NotificationIds.Companion.doseNotificationId
import com.medreminder.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable notification ids & PendingIntent request codes.
 *
 * Determinism is what makes actions idempotent: the same dose always maps to
 * the same notification id, so re-posting replaces instead of duplicating, and
 * cancelling by id always finds the right row. Action request codes are derived
 * from (doseId, action) so "Taken" can never be delivered as "Skip".
 */
class NotificationIds private constructor() {
    companion object {
        const val CHANNEL_DOSES = "doses" // sound + vibration
        const val CHANNEL_DOSES_SOUND = "doses_sound"
        const val CHANNEL_DOSES_VIBRATE = "doses_vibrate"
        const val CHANNEL_DOSES_SILENT = "doses_silent"

        fun doseChannelId(sound: Boolean, vibration: Boolean): String = when {
            sound && vibration -> CHANNEL_DOSES
            sound -> CHANNEL_DOSES_SOUND
            vibration -> CHANNEL_DOSES_VIBRATE
            else -> CHANNEL_DOSES_SILENT
        }
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_ALERTS = "alerts"

        /** Truncates a 32-bit dose id (CRC32 range) into a positive Int requestCode. */
        fun doseRequestCode(doseId: Long): Int = (doseId and 0x7FFFFFFFL).toInt()

        /** Mixes the action ordinal into the id with XOR-shift so the result
         *  never overflows Int (a plain `* 4` would for CRC32-range ids). */
        fun actionRequestCode(doseId: Long, action: DoseAction): Int {
            val base = doseRequestCode(doseId)
            return ((base xor (base ushr 13)) and 0x3FFFFFFF) * 4 + action.ordinal
        }

        fun doseNotificationId(doseId: Long): Int = doseRequestCode(doseId)

        const val LOW_STOCK_ID_OFFSET = 1_000_000_000
        fun lowStockNotificationId(medicationId: Long): Int =
            LOW_STOCK_ID_OFFSET + (medicationId and 0x7FFFFFFF).toInt()
    }
}

/** Notification action buttons; ordinals participate in request-code derivation. */
enum class DoseAction { TAKEN, SNOOZE, SKIP }

/**
 * Creates channels and posts/cancels all notifications owned by the app.
 * Channel config honours settings (sound/vibration) at post time.
 */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Ensures the three channels exist. Safe to call on every process start. */
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val doseChannels = listOf(
            Triple(NotificationIds.CHANNEL_DOSES, true, true),
            Triple(NotificationIds.CHANNEL_DOSES_SOUND, true, false),
            Triple(NotificationIds.CHANNEL_DOSES_VIBRATE, false, true),
            Triple(NotificationIds.CHANNEL_DOSES_SILENT, false, false),
        ).map { (id, sound, vibrate) ->
            val suffix = when {
                sound && vibrate -> ""
                sound -> " " + context.getString(R.string.channel_variant_sound_only)
                vibrate -> " " + context.getString(R.string.channel_variant_vibrate_only)
                else -> " " + context.getString(R.string.channel_variant_silent)
            }
            NotificationChannel(
                id,
                context.getString(R.string.channel_doses_name) + suffix,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_doses_description)
                setBypassDnd(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                if (sound) {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                } else {
                    setSound(null, null)
                }
                enableVibration(vibrate)
                if (vibrate) vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
            }
        }
        val reminders = NotificationChannel(
            NotificationIds.CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_reminders_description) }
        val alerts = NotificationChannel(
            NotificationIds.CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_alerts_description) }

        manager.createNotificationChannels(doseChannels + listOf(reminders, alerts))
    }

    /** Posts (or replaces) the dose reminder for [dose] with Taken/Snooze/Skip actions. */
    fun showDoseReminder(
        dose: DoseLog,
        medication: Medication,
        snoozeMinutes: Int,
        repeatCount: Int,
        fullScreenEnabled: Boolean,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
    ) {
        ensureChannels()
        val title = context.getString(R.string.notification_dose_title, medication.name)
        val text = context.getString(
            R.string.notification_dose_body,
            formatDosage(medication),
        )

        val contentIntent = launchMainActivity(context, dose.id)
        val builder = NotificationCompat.Builder(
            context,
            NotificationIds.doseChannelId(soundEnabled, vibrationEnabled),
        )
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title — $text"))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(action(dose.id, DoseAction.TAKEN, R.string.action_taken))
            .addAction(action(dose.id, DoseAction.SNOOZE, R.string.action_snooze_n, snoozeMinutes))
            .addAction(action(dose.id, DoseAction.SKIP, R.string.action_skip))
            .setColor(medication.colorArgb)

        // Sound/vibration are channel properties on API 26+ (minSdk): see ensureChannels().
        if (repeatCount > 0) {
            builder.setSubText(context.getString(R.string.notification_repeat_subtext, repeatCount))
        }

        // Full-screen alarm style: only when both the global setting and the
        // per-medication flag are on; requires USE_FULL_SCREEN_INTENT (declared).
        if (fullScreenEnabled && medication.alarmStyle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.fullScreenIntent = launchFullScreen(context, dose.id)
                ?: contentIntent
            builder.priority = NotificationCompat.PRIORITY_MAX
        } else {
            builder.priority = NotificationCompat.PRIORITY_HIGH
        }

        safeNotify(doseNotificationId(dose.id), builder.build())
    }

    /** Low-stock warning (channel: alerts). */
    fun showLowStock(medication: Medication) {
        ensureChannels()
        val notification = NotificationCompat.Builder(context, NotificationIds.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(context.getString(R.string.notification_low_stock_title, medication.name))
            .setContentText(
                context.getString(
                    R.string.notification_low_stock_body,
                    medication.stockCount ?: 0,
                    medication.refillThreshold,
                ),
            )
            .setContentIntent(launchMainActivity(context, null))
            .setAutoCancel(true)
            .setColor(medication.colorArgb)
            .build()
        safeNotify(NotificationIds.lowStockNotificationId(medication.id), notification)
    }

    /** Exact-alarm permission problem banner (reliability safety net). */
    fun showExactAlarmWarning() {
        ensureChannels()
        val notification = NotificationCompat.Builder(context, NotificationIds.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(context.getString(R.string.notification_exact_alarm_title))
            .setContentText(context.getString(R.string.notification_exact_alarm_body))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    9999,
                    context.requestExactAlarmSettingsIntent(),
                    PendingIntentFlags.forNotificationAction(),
                ),
            )
            .build()
        safeNotify(NotificationIds.LOW_STOCK_ID_OFFSET - 1, notification)
    }

    fun cancelDose(doseId: Long) =
        NotificationManagerCompat.from(context).cancel(doseNotificationId(doseId))

    fun cancelAll() = NotificationManagerCompat.from(context).cancelAll()

    private fun action(doseId: Long, action: DoseAction, titleRes: Int, vararg args: Any): NotificationCompat.Action {
        val intent = Intent(context, com.medreminder.notification.NotificationActionReceiver::class.java).apply {
            this.action = action.name
            putExtra(EXTRA_DOSE_ID, doseId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            actionRequestCode(doseId, action),
            intent,
            PendingIntentFlags.forNotificationAction(),
        )
        return NotificationCompat.Action(0, context.getString(titleRes, *args), pi)
    }

    private fun launchMainActivity(context: Context, doseId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            doseId?.let { putExtra(EXTRA_DOSE_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            doseId?.let { doseRequestCode(it) } ?: 0,
            intent,
            PendingIntentFlags.forNotificationAction(),
        )
    }

    private fun launchFullScreen(context: Context, doseId: Long): PendingIntent? {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DOSE_ID, doseId)
            putExtra(EXTRA_FULLSCREEN, true)
        }
        return PendingIntent.getActivity(
            context,
            doseRequestCode(doseId),
            intent,
            PendingIntentFlags.forNotificationAction(),
        )
    }

    /** POST_NOTIFICATIONS-safe notify: drops silently when the runtime permission is missing. */
    private fun safeNotify(id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission revoked by the user; the Today screen still shows the dose.
        }
    }

    companion object {
        const val EXTRA_DOSE_ID = "com.medreminder.extra.DOSE_ID"
        const val EXTRA_FULLSCREEN = "com.medreminder.extra.FULLSCREEN"
    }
}

private fun formatDosage(m: Medication): String =
    "${com.medreminder.util.formatDecimal(m.dosageValue)} ${m.dosageUnit.key}"

private fun Context.requestExactAlarmSettingsIntent(): Intent =
    com.medreminder.util.requestExactAlarmPermissionIntent(this)
