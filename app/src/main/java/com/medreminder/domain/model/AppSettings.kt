package com.medreminder.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * Domain-level settings (persisted in DataStore). Every field has a safe
 * default so the app works before the user touches Settings.
 */
data class AppSettings(
    /** Snooze duration applied by the notification "Snooze" action, minutes. */
    val snoozeMinutes: Int = 10,
    /** Repeat-reminder interval while a dose stays unacknowledged, minutes. 0 disables repeats. */
    val repeatIntervalMinutes: Int = 15,
    /** Maximum number of repeat reminders per dose. */
    val maxRepeats: Int = 3,
    /** Theme mode: 0 = system, 1 = light, 2 = dark. */
    val themeMode: Int = 0,
    /** Use 24-hour clock; null = follow system preference. */
    val use24HourFormat: Boolean? = null,
    /** First day of week for the history calendar; null = follow locale. */
    val firstDayOfWeek: DayOfWeek? = null,
    /** BCP-47 language tag override; null = follow system. */
    val languageTag: String? = null,
    /** Show Jalali (Persian) calendar dates alongside Gregorian. */
    val jalaliDates: Boolean = false,
    /** Play the device default alarm sound with dose notifications. */
    val soundEnabled: Boolean = true,
    /** Vibrate on dose notifications. */
    val vibrationEnabled: Boolean = true,
    /** Raise full-screen alarms (requires USE_FULL_SCREEN_INTENT + alarmStyle per medication). */
    val fullScreenAlarm: Boolean = false,
    /** Whether the one-time onboarding flow was completed. */
    val onboardingCompleted: Boolean = false,
) {
    init {
        require(snoozeMinutes in 1..240) { "snoozeMinutes out of range" }
        require(repeatIntervalMinutes in 0..240) { "repeatIntervalMinutes out of range" }
        require(maxRepeats in 0..10) { "maxRepeats out of range" }
    }

    companion object {
        /** Weekday choices offered in the schedule editor. */
        val WEEKDAYS: List<DayOfWeek> = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
        )

        /** Hours offered for the "every N hours" schedule. */
        val HOUR_INTERVALS: List<Int> = listOf(2, 3, 4, 6, 8, 12)

        /** Time presets used by the multi-time picker. */
        val COMMON_TIMES: List<LocalTime> = listOf(
            LocalTime(8, 0), LocalTime(13, 0), LocalTime(20, 0), LocalTime(22, 0),
        )
    }
}
