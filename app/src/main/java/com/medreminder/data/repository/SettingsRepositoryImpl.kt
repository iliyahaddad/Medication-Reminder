package com.medreminder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.medreminder.domain.model.AppSettings
import com.medreminder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

/** DataStore-backed [SettingsRepository] with safe defaults for every key. */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            snoozeMinutes = prefs[KEY_SNOOZE] ?: 10,
            repeatIntervalMinutes = prefs[KEY_REPEAT] ?: 15,
            maxRepeats = prefs[KEY_MAX_REPEATS] ?: 3,
            themeMode = prefs[KEY_THEME] ?: 0,
            use24HourFormat = read24(prefs),
            firstDayOfWeek = prefs[KEY_FIRST_DAY]?.let { name ->
                DayOfWeek.entries.firstOrNull { it.name == name }
            },
            languageTag = prefs[KEY_LANGUAGE],
            jalaliDates = prefs[KEY_JALALI] ?: false,
            soundEnabled = prefs[KEY_SOUND] ?: true,
            vibrationEnabled = prefs[KEY_VIBRATION] ?: true,
            fullScreenAlarm = prefs[KEY_FULLSCREEN] ?: false,
            onboardingCompleted = prefs[KEY_ONBOARDING] ?: false,
        )
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = AppSettings(
                snoozeMinutes = prefs[KEY_SNOOZE] ?: 10,
                repeatIntervalMinutes = prefs[KEY_REPEAT] ?: 15,
                maxRepeats = prefs[KEY_MAX_REPEATS] ?: 3,
                themeMode = prefs[KEY_THEME] ?: 0,
                use24HourFormat = read24(prefs),
                firstDayOfWeek = prefs[KEY_FIRST_DAY]?.let { name ->
                    DayOfWeek.entries.firstOrNull { it.name == name }
                },
                languageTag = prefs[KEY_LANGUAGE],
                jalaliDates = prefs[KEY_JALALI] ?: false,
                soundEnabled = prefs[KEY_SOUND] ?: true,
                vibrationEnabled = prefs[KEY_VIBRATION] ?: true,
                fullScreenAlarm = prefs[KEY_FULLSCREEN] ?: false,
                onboardingCompleted = prefs[KEY_ONBOARDING] ?: false,
            )
            val next = transform(current)
            prefs[KEY_SNOOZE] = next.snoozeMinutes.coerceIn(1, 240)
            prefs[KEY_REPEAT] = next.repeatIntervalMinutes.coerceIn(0, 240)
            prefs[KEY_MAX_REPEATS] = next.maxRepeats.coerceIn(0, 10)
            prefs[KEY_THEME] = next.themeMode.coerceIn(0, 2)
            when (val h = next.use24HourFormat) {
                null -> { prefs.remove(KEY_24H); prefs.remove(KEY_24H_FALSE) }
                true -> { prefs[KEY_24H] = true; prefs.remove(KEY_24H_FALSE) }
                false -> { prefs[KEY_24H_FALSE] = false; prefs.remove(KEY_24H) }
            }
            next.firstDayOfWeek?.let { prefs[KEY_FIRST_DAY] = it.name } ?: prefs.remove(KEY_FIRST_DAY)
            next.languageTag?.let { prefs[KEY_LANGUAGE] = it } ?: prefs.remove(KEY_LANGUAGE)
            prefs[KEY_JALALI] = next.jalaliDates
            prefs[KEY_SOUND] = next.soundEnabled
            prefs[KEY_VIBRATION] = next.vibrationEnabled
            prefs[KEY_FULLSCREEN] = next.fullScreenAlarm
            prefs[KEY_ONBOARDING] = next.onboardingCompleted
        }
    }

    private fun read24(prefs: Preferences): Boolean? = when {
        prefs.contains(KEY_24H) -> true
        prefs.contains(KEY_24H_FALSE) -> false
        else -> null
    }

    private companion object {
        val KEY_SNOOZE = intPreferencesKey("snooze_minutes")
        val KEY_REPEAT = intPreferencesKey("repeat_interval_minutes")
        val KEY_MAX_REPEATS = intPreferencesKey("max_repeats")
        val KEY_THEME = intPreferencesKey("theme_mode")
        val KEY_24H = booleanPreferencesKey("use24_true")
        val KEY_24H_FALSE = booleanPreferencesKey("use24_false")
        val KEY_FIRST_DAY = stringPreferencesKey("first_day_of_week")
        val KEY_LANGUAGE = stringPreferencesKey("language_tag")
        val KEY_JALALI = booleanPreferencesKey("jalali_dates")
        val KEY_SOUND = booleanPreferencesKey("sound_enabled")
        val KEY_VIBRATION = booleanPreferencesKey("vibration_enabled")
        val KEY_FULLSCREEN = booleanPreferencesKey("fullscreen_alarm")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
    }
}

