package com.medreminder.domain.usecase

import com.medreminder.domain.model.DoseLog
import com.medreminder.domain.model.Medication
import com.medreminder.domain.model.MedicationStatus
import com.medreminder.domain.repository.DoseLogRepository
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.domain.repository.SettingsRepository
import com.medreminder.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** A dose on the Today screen: its log row plus the medication it belongs to. */
data class TodayItem(
    val dose: DoseLog,
    val medication: Medication,
) {
    /** True when the dose time has passed and nobody acknowledged it yet. */
    fun isPastDue(nowMillis: Long): Boolean =
        dose.status == DoseStatus.SCHEDULED && dose.scheduledAtMillis < nowMillis
}

/**
 * Resolves the "Today" list from raw dose rows: joins medications and orders by
 * time of day. Pure — easy to unit test with fake repositories.
 */
class GetTodayUseCase @Inject constructor(
    private val doseLogRepository: DoseLogRepository,
    private val medicationRepository: MedicationRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) {
    /** Emits today's items whenever either underlying flow changes. */
    fun observe(dayStartMillis: Long, dayEndMillis: Long): Flow<List<TodayItem>> =
        combine(
            doseLogRepository.observeBetween(dayStartMillis, dayEndMillis),
            medicationRepository.observeAll(),
        ) { doses, meds ->
            val byId = meds.associateBy { it.id }
            doses.mapNotNull { d -> byId[d.medicationId]?.let { TodayItem(d, it) } }
                .sortedBy { it.dose.scheduledAtMillis }
        }

    /** Unacknowledged past doses of active medications (missed-dose handling). */
    suspend fun unresolvedDosesFor(medications: List<Medication>): List<DoseLog> {
        val now = clock.nowMillis()
        return doseLogRepository.getUnresolvedOlderThan(now)
            .filter { d -> medications.any { it.id == d.medicationId && it.status == MedicationStatus.ACTIVE } }
    }

    companion object {
        /** Grace period after which an unacknowledged dose is flipped to MISSED. */
        const val MISSED_GRACE_MILLIS = 2L * 3_600_000L // 2 hours
    }
}

/** Stream that re-evaluates "today" whenever the app settings change. */
class ObserveSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<com.medreminder.domain.model.AppSettings> =
        settingsRepository.observeSettings()

    suspend fun update(transform: (com.medreminder.domain.model.AppSettings) -> com.medreminder.domain.model.AppSettings) =
        settingsRepository.update(transform)
}


/** Flips stale SCHEDULED rows to MISSED (run from the daily WorkManager job). */
class MarkMissedDosesUseCase @Inject constructor(
    private val doseLogRepository: DoseLogRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Int =
        doseLogRepository.markMissed(clock.nowMillis() - GetTodayUseCase.MISSED_GRACE_MILLIS)
}

/** Computes adherence stats for an arbitrary window (used by Stats screen & widgets). */
class GetAdherenceStatsUseCase @Inject constructor(
    private val doseLogRepository: DoseLogRepository,
) {
    suspend operator fun invoke(fromMillis: Long, untilMillis: Long) =
        doseLogRepository.getStats(fromMillis, untilMillis)
}

/** Low-stock check used by the daily safety-net worker; emits alert notifications. */
class CheckLowStockUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository,
) {
    suspend operator fun invoke(): List<Medication> =
        medicationRepository.getAllOnce().filter { it.isLowStock() }
}
