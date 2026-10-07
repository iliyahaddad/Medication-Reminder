package com.medreminder.domain.usecase

import com.medreminder.domain.model.Medication
import com.medreminder.domain.repository.DoseLogRepository
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.Clock
import javax.inject.Inject

/**
 * Validates and persists a medication (create or update).
 *
 * All user input passes through here — the UI layer must not talk to the
 * repositories directly. After a successful save the caller is expected to ask
 * [RescheduleMedicationUseCase] to refresh alarms, which keeps this use case
 * free of Android dependencies and trivially testable.
 */
class SaveMedicationUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val doseLogRepository: DoseLogRepository,
    private val clock: Clock,
) {

    /** Returns stable validation-error keys; empty list means the model is valid. */
    fun validate(medication: Medication): List<String> = buildList {
        if (medication.name.isBlank()) add("name_blank")
        if (medication.name.length > 200) add("name_too_long")
        if (!medication.dosageValue.isFinite() || medication.dosageValue <= 0.0) add("dosage_invalid")
        if (medication.refillThreshold < 0) add("threshold_negative")
        medication.stockCount?.let { if (it < 0) add("stock_negative") }
        if (medication.endDate != null && medication.endDate < medication.startDate) add("end_before_start")
        when (val s = medication.schedule) {
            is com.medreminder.domain.model.ScheduleType.Daily ->
                if (s.times.isEmpty()) add("times_empty")
            is com.medreminder.domain.model.ScheduleType.Weekly ->
                if (s.days.isEmpty() || s.times.isEmpty()) add("weekly_empty")
            is com.medreminder.domain.model.ScheduleType.EveryNDays ->
                if (s.intervalDays < 1) add("interval_days")
            is com.medreminder.domain.model.ScheduleType.EveryNHours ->
                if (s.intervalHours < 1) add("interval_hours")
            is com.medreminder.domain.model.ScheduleType.Cyclic ->
                if (s.daysOn < 1 || s.daysOff < 0 || s.times.isEmpty()) add("cyclic_invalid")
            com.medreminder.domain.model.ScheduleType.AsNeeded -> Unit
        }
    }

    /**
     * Saves [medication]. On success returns the row id; on validation failure
     * returns [IllegalArgumentException] whose message is the comma-joined
     * error keys (the UI maps them to string resources).
     */
    suspend operator fun invoke(medication: Medication): Result<Long> {
        val problems = validate(medication)
        if (problems.isNotEmpty()) {
            return Result.failure(IllegalArgumentException(problems.joinToString(",")))
        }
        val normalized = medication.copy(name = medication.name.trim())
        val id = medicationRepository.upsert(normalized)
        // Editing an existing medication invalidates its pending occurrences:
        // drop future SCHEDULED rows so the rescheduler rebuilds them idempotently.
        if (medication.id != 0L) {
            doseLogRepository.deletePendingAfterForMedication(medication.id, clock.nowMillis())
        }
        return Result.success(id)
    }
}
