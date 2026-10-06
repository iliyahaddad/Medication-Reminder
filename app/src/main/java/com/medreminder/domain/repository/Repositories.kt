package com.medreminder.domain.repository

import com.medreminder.domain.model.AdherenceStats
import com.medreminder.domain.model.AppSettings
import com.medreminder.domain.model.DoseLog
import com.medreminder.domain.model.Medication
import kotlinx.coroutines.flow.Flow

/** Persistence contract for medications (Room-backed in the data layer). */
interface MedicationRepository {
    /** Stream all medications ordered by name. */
    fun observeAll(): Flow<List<Medication>>

    /** Stream only medications that currently produce alarms. */
    fun observeActive(): Flow<List<Medication>>

    suspend fun getById(id: Long): Medication?

    /** Insert or replace; returns the medication id. */
    suspend fun upsert(medication: Medication): Long

    suspend fun delete(id: Long)

    suspend fun setStatus(id: Long, active: Boolean)

    /** Atomically decrement tracked stock (no-op when stock is null). Returns new count or null. */
    suspend fun decrementStock(medicationId: Long, amount: Int = 1): Int?

    suspend fun setStock(medicationId: Long, count: Int)

    /** Replace the entire contents of the table — used by backup restore. Runs in a transaction. */
    suspend fun replaceAll(medications: List<Medication>)

    /** All medications including inactive — used by backup export & rescheduling. */
    suspend fun getAllOnce(): List<Medication>
}

/** Persistence contract for dose occurrences and their acknowledgement state. */
interface DoseLogRepository {
    /** Idempotent batch insert: rows whose deterministic id exists are ignored. */
    suspend fun insertScheduledIgnoreConflict(doses: List<DoseLog>)

    /** Stream doses scheduled within `[fromMillis, untilMillis)`. */
    fun observeBetween(fromMillis: Long, untilMillis: Long): Flow<List<DoseLog>>

    suspend fun getById(id: Long): DoseLog?

    suspend fun markTaken(id: Long, atMillis: Long)

    suspend fun markSkipped(id: Long, atMillis: Long)

    /** Snooze: keep SCHEDULED but move the reminder time forward. */
    suspend fun snooze(id: Long, remindAtMillis: Long)

    suspend fun setRemindUntil(id: Long, remindUntilMillis: Long?)

    /** Flip unacknowledged doses older than [graceMillis] to MISSED. Returns affected count. */
    suspend fun markMissed(olderThanMillis: Long): Int

    /** Delete every pending (SCHEDULED) occurrence strictly after [fromMillis]. */
    suspend fun deletePendingAfter(fromMillis: Long)

    suspend fun deleteForMedication(medicationId: Long)

    suspend fun getStats(fromMillis: Long, untilMillis: Long): AdherenceStats

    suspend fun replaceAll(doses: List<DoseLog>)

    suspend fun getAllOnce(): List<DoseLog>

    /** Oldest unresolved (SCHEDULED) doses — drives missed-dose handling & reminders. */
    suspend fun getUnresolvedOlderThan(millis: Long): List<DoseLog>
}

/** Settings contract (DataStore-backed). */
interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
