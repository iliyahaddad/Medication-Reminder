package com.medreminder.domain.usecase

import com.medreminder.domain.model.DoseStatus
import com.medreminder.domain.repository.DoseLogRepository
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.Clock
import javax.inject.Inject

/**
 * Acknowledges a dose as TAKEN.
 *
 * Idempotent: the underlying SQL only updates rows whose status is not already
 * `taken`, so double-taps (or a notification action racing with an in-app tap)
 * decrement stock exactly once — the boolean return tells the caller whether
 * this invocation was the one that counted.
 */
class MarkDoseTakenUseCase @Inject constructor(
    private val doseLogRepository: DoseLogRepository,
    private val medicationRepository: MedicationRepository,
    private val clock: Clock,
) {
    /** Returns true when this call performed the transition (first acknowledgement). */
    suspend operator fun invoke(doseId: Long): Boolean {
        val dose = doseLogRepository.getById(doseId) ?: return false
        if (dose.status == DoseStatus.TAKEN) return false
        val changed = doseLogRepository.markTaken(doseId, clock.nowMillis())
        if (changed) {
            // Stock decrement happens only for the winning call above.
            medicationRepository.decrementStock(dose.medicationId, amount = 1)
        }
        return changed
    }
}

/** Acknowledges a dose as SKIPPED (no stock change). */
class MarkDoseSkippedUseCase @Inject constructor(
    private val doseLogRepository: DoseLogRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(doseId: Long): Boolean {
        val dose = doseLogRepository.getById(doseId) ?: return false
        if (dose.status == DoseStatus.TAKEN) return false // taken wins; never downgrade
        return doseLogRepository.markSkipped(doseId, clock.nowMillis())
    }
}

/**
 * Snoozes a dose by [snoozeMinutes]: clears any pending reminder and programs
 * a single new exact alarm via the scheduler port. Keeps status SCHEDULED.
 */
class SnoozeDoseUseCase @Inject constructor(
    private val doseLogRepository: DoseLogRepository,
    private val alarms: ScheduleAlarmsPort,
    private val clock: Clock,
) {
    suspend operator fun invoke(doseId: Long, snoozeMinutes: Int): Boolean {
        require(snoozeMinutes in 1..240) { "snoozeMinutes out of range" }
        val dose = doseLogRepository.getById(doseId) ?: return false
        if (dose.status != DoseStatus.SCHEDULED) return false
        val remindAt = clock.nowMillis() + snoozeMinutes * 60_000L
        doseLogRepository.snooze(doseId, remindAt)
        alarms.scheduleReminder(doseId, remindAt)
        return true
    }
}
