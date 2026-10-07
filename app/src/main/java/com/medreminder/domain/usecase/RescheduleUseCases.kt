package com.medreminder.domain.usecase

import com.medreminder.domain.model.DoseLog
import com.medreminder.domain.model.Medication
import com.medreminder.domain.model.ScheduleCalculator
import com.medreminder.domain.repository.DoseLogRepository
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.Clock
import com.medreminder.util.TimeZoneProvider
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Materializes upcoming dose occurrences for one medication into the dose log
 * table (idempotent) and hands the resulting instants to the alarm scheduler.
 *
 * Reliability rules implemented here:
 *  - Occurrences strictly in the future only (`fromMillis = now`) — never
 *    schedule in the past.
 *  - Expired / paused / as-needed medications produce nothing; their stale
 *    pending rows are deleted.
 *  - Deterministic ids make double-scheduling a no-op at the DB level.
 */
class RescheduleMedicationUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val doseLogRepository: DoseLogRepository,
    private val scheduleAlarms: ScheduleAlarmsPort,
    private val clock: Clock,
    private val timeZoneProvider: TimeZoneProvider,
) {

    /** Builds the future occurrence list for [medication] without persisting (pure). */
    fun computeOccurrences(medication: Medication, nowMillis: Long, horizonMillis: Long): List<Long> {
        if (medication.schedule is com.medreminder.domain.model.ScheduleType.AsNeeded) return emptyList()
        if (medication.status != com.medreminder.domain.model.MedicationStatus.ACTIVE) return emptyList()
        val tz = timeZoneProvider.current()
        val today = kotlinx.datetime.Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz).date
        if (medication.isExpired(today)) return emptyList()
        return ScheduleCalculator.occurrences(
            schedule = medication.schedule,
            validityStart = medication.startDate,
            // endDate is user-facing and inclusive; ScheduleCalculator expects an exclusive bound.
            validityEndExclusive = medication.endDate?.plus(kotlinx.datetime.DatePeriod(days = 1)),
            fromMillis = nowMillis,
            untilMillis = nowMillis + horizonMillis,
            timeZone = tz,
        )
    }

    /** Persists pending doses + schedules alarms for one medication. Returns scheduled count. */
    suspend operator fun invoke(medication: Medication, horizonMillis: Long): Int {
        val now = clock.nowMillis()
        val occurrences = computeOccurrences(medication, now, horizonMillis)
        if (occurrences.isEmpty()) {
            scheduleAlarms.cancelFor(medication.id)
            doseLogRepository.deletePendingAfterForMedication(medication.id, now)
            return 0
        }
        val doses = occurrences.map { millis ->
            DoseLog(
                id = ScheduleCalculator.doseId(medication.id, millis),
                medicationId = medication.id,
                scheduledAtMillis = millis,
            )
        }
        doseLogRepository.insertScheduledIgnoreConflict(doses)
        scheduleAlarms.scheduleFor(medication, occurrences)
        return doses.size
    }
}

/** Port implemented by the scheduler package (keeps domain free of Android). */
interface ScheduleAlarmsPort {
    /** (Re)program exact alarms for the given future occurrences of [medication]. */
    fun scheduleFor(medication: Medication, occurrencesMillis: List<Long>)

    /** Program a one-off reminder/snooze alarm for an existing dose. */
    fun scheduleReminder(doseId: Long, atMillis: Long)

    /** Remove every alarm belonging to [medicationId]. */
    fun cancelFor(medicationId: Long)

    /** Remove all alarms the app owns (used before full reschedules). */
    fun cancelAll()
}

/** Reschedules every medication currently stored. Used after boot/time change/restore. */
class RescheduleAllUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val rescheduleMedication: RescheduleMedicationUseCase,
    private val alarmPort: ScheduleAlarmsPort,
    private val doseLogRepository: DoseLogRepository,
    private val clock: Clock,
) {
    companion object {
        /** ~35 days ahead — comfortably below AlarmManager limits, above any weekly/cyclic gap. */
        const val DEFAULT_HORIZON_MILLIS = 35L * 24 * 3_600_000L
        private const val REARM_DELAY_MILLIS = 5_000L
    }

    suspend operator fun invoke() {
        alarmPort.cancelAll()
        val meds = medicationRepository.getAllOnce()
        for (m in meds) {
            rescheduleMedication(m, DEFAULT_HORIZON_MILLIS)
        }
        // Snoozed / repeat reminders live in their own alarms; re-arm them too.
        val now = clock.nowMillis()
        val activeIds = meds.filter { it.status == com.medreminder.domain.model.MedicationStatus.ACTIVE }
            .map { it.id }.toSet()
        for (dose in doseLogRepository.getPendingReminders()) {
            if (dose.medicationId !in activeIds) continue
            val at = (dose.remindUntilMillis ?: continue).coerceAtLeast(now + REARM_DELAY_MILLIS)
            alarmPort.scheduleReminder(dose.id, at)
        }
    }
}

/** Convenience date helper used by callers that think in calendar days. */
fun daysFromToday(today: LocalDate, offsetDays: Long): LocalDate =
    today.plus(kotlinx.datetime.DatePeriod(days = offsetDays.toInt()))
