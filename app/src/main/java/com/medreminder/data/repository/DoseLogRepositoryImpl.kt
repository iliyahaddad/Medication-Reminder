package com.medreminder.data.repository

import com.medreminder.data.local.db.DoseLogDao
import com.medreminder.data.local.db.DoseStatsRow
import com.medreminder.data.mapper.DoseLogMapper
import com.medreminder.domain.model.AdherenceStats
import com.medreminder.domain.model.DoseLog
import com.medreminder.domain.repository.DoseLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [DoseLogRepository]. */
@Singleton
class DoseLogRepositoryImpl @Inject constructor(
    private val dao: DoseLogDao,
) : DoseLogRepository {

    override suspend fun insertScheduledIgnoreConflict(doses: List<DoseLog>) =
        dao.insertIgnoreConflict(doses.map(DoseLogMapper::toEntity))

    override fun observeBetween(fromMillis: Long, untilMillis: Long): Flow<List<DoseLog>> =
        dao.observeBetween(fromMillis, untilMillis).map { list -> list.map(DoseLogMapper::toDomain) }

    override suspend fun getById(id: Long): DoseLog? =
        dao.getById(id)?.let(DoseLogMapper::toDomain)

    override suspend fun markTaken(id: Long, atMillis: Long): Boolean =
        dao.markTaken(id, atMillis) > 0

    override suspend fun markSkipped(id: Long, atMillis: Long): Boolean =
        dao.markSkipped(id, atMillis) > 0

    override suspend fun snooze(id: Long, remindAtMillis: Long) = dao.snooze(id, remindAtMillis)

    override suspend fun setRemindUntil(id: Long, remindUntilMillis: Long?) =
        dao.setRemindUntil(id, remindUntilMillis)

    override suspend fun incrementRepeatCount(id: Long) = dao.incrementRepeatCount(id)

    override suspend fun markMissed(olderThanMillis: Long): Int =
        dao.markMissed(olderThanMillis)

    override suspend fun deletePendingAfter(fromMillis: Long) = dao.deletePendingAfter(fromMillis)

    override suspend fun deletePendingAfterForMedication(medicationId: Long, fromMillis: Long) =
        dao.deletePendingAfterForMedication(medicationId, fromMillis)

    override suspend fun deleteForMedication(medicationId: Long) =
        dao.deleteForMedication(medicationId)

    override suspend fun getStats(fromMillis: Long, untilMillis: Long): AdherenceStats {
        val row: DoseStatsRow? = dao.getStats(fromMillis, untilMillis)
        return AdherenceStats(
            totalScheduled = row?.total ?: 0,
            taken = row?.taken ?: 0,
            skipped = row?.skipped ?: 0,
            missed = row?.missed ?: 0,
        )
    }

    override suspend fun replaceAll(doses: List<DoseLog>) =
        dao.replaceAll(doses.map(DoseLogMapper::toEntity))

    override suspend fun getAllOnce(): List<DoseLog> =
        dao.getAllOnce().map(DoseLogMapper::toDomain)

    override suspend fun getPendingReminders(): List<DoseLog> =
        dao.getPendingReminders().map(DoseLogMapper::toDomain)

    override suspend fun getUnresolvedOlderThan(millis: Long): List<DoseLog> =
        dao.getUnresolvedOlderThan(millis).map(DoseLogMapper::toDomain)
}
