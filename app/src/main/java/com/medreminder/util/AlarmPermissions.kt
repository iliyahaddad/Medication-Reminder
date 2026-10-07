package com.medreminder.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Android platform helpers with per-API-level behaviour. Kept tiny and
 * side-effect-light so the scheduling use cases stay testable.
 */

/** Standard PendingIntent flags: immutable everywhere + one-shot for broadcasts. */
object PendingIntentFlags {
    /** FLAG_IMMUTABLE is mandatory on API 23+; we target 26+, always immutable. */
    const val BASE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE

    fun forAlarm(updateExisting: Boolean): Int =
        if (updateExisting) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT

    fun forNotificationAction(): Int = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
}

/** True when the device allows this app to schedule exact alarms (API 31+ policy). */
fun Context.canScheduleExactAlarms(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = getSystemService(AlarmManager::class.java) ?: return false
    return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
}

/** Opens the system "Alarms & reminders" permission page (API 31+), or app details below it. */
fun Context.requestExactAlarmPermissionIntent(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        openAppDetailsIntent()
    }
}

/** App-system-settings deep link for this package (used by many fallbacks). */
fun Context.openAppDetailsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** Battery-optimization exemption request (onboarding). */
@Suppress("BatteryLife") // This IS the legitimate battery-exemption request flow.
fun Context.requestIgnoreBatteryOptimizationsIntent(): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** Direct access to the global battery-optimization list (fallback if the above is denied). */
fun Context.batteryOptimizationListIntent(): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** Notification settings for this app (deep-links to per-channel settings on API 26+). */
fun Context.notificationSettingsIntent(): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** True when notifications are enabled at the OS level for this app. */
fun Context.areNotificationsEnabled(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled()

/** True when the app currently ignores battery optimizations. */
fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val pm = getSystemService(PowerManager::class.java) ?: return false
    return runCatching { pm.isIgnoringBatteryOptimizations(packageName) }.getOrDefault(false)
}

/** Best-effort OEM detection for the onboarding guidance screen (Xiaomi/Huawei/…). */
fun Context.detectOem(): String {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> "Xiaomi/Redmi (MIUI)"
        manufacturer.contains("huawei") || manufacturer.contains("honor") -> "Huawei/Honor (EMUI)"
        manufacturer.contains("samsung") -> "Samsung (One UI)"
        manufacturer.contains("oppo") || manufacturer.contains("oneplus") ||
            manufacturer.contains("realme") -> "OPPO/OnePlus/realme"
        manufacturer.contains("vivo") -> "Vivo"
        manufacturer.contains("asus") -> "ASUS (ZenUI)"
        else -> ""
    }
}
