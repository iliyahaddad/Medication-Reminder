package com.medreminder.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.medreminder.data.local.entity.DoseLogEntity
import kotlinx.coroutines.flow.Flow

/** Room DAO for the `dose_logs` table. */
@Dao
interface DoseLogDao {

    /** Idempotent batch insert — deterministic ids make conflicts a no-op. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreConflict(doses: List<DoseLogEntity>)

    @Query(
        "SELECT * FROM dose_logs WHERE scheduled_at >= :fromMillis " +
            "AND scheduled_at < :untilMillis ORDER BY scheduled_at ASC",
    )
    fun observeBetween(fromMillis: Long, untilMillis: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE id = :id")
    suspend fun getById(id: Long): DoseLogEntity?

    @Query("SELECT * FROM dose_logs WHERE medication_id = :medicationId AND scheduled_at = :scheduledAtMillis")
    suspend fun findByKey(medicationId: Long, scheduledAtMillis: Long): DoseLogEntity?

    @Query(
        "UPDATE dose_logs SET status = 'taken', taken_at = :atMillis, remind_until = NULL " +
            "WHERE id = :id AND status != 'taken'",
    )
    suspend fun markTaken(id: Long, atMillis: Long): Int

    @Query(
        "UPDATE dose_logs SET status = 'skipped', skipped_at = :atMillis, remind_until = NULL " +
            "WHERE id = :id AND status != 'taken'",
    )
    suspend fun markSkipped(id: Long, atMillis: Long): Int

    @Query("UPDATE dose_logs SET remind_until = :remindAtMillis WHERE id = :id")
    suspend fun snooze(id: Long, remindAtMillis: Long)

    @Query("UPDATE dose_logs SET remind_until = :remindUntilMillis WHERE id = :id")
    suspend fun setRemindUntil(id: Long, remindUntilMillis: Long?)

    @Query("UPDATE dose_logs SET repeat_count = repeat_count + 1 WHERE id = :id")
    suspend fun incrementRepeatCount(id: Long)

    @Query(
        "UPDATE dose_logs SET status = 'missed', remind_until = NULL " +
            "WHERE status = 'scheduled' AND scheduled_at < :olderThanMillis " +
            "AND (remind_until IS NULL OR remind_until < :olderThanMillis)",
    )
    suspend fun markMissed(olderThanMillis: Long): Int

    @Query("DELETE FROM dose_logs WHERE status = 'scheduled' AND scheduled_at > :fromMillis")
    suspend fun deletePendingAfter(fromMillis: Long)

    @Query(
        "DELETE FROM dose_logs WHERE status = 'scheduled' " +
            "AND scheduled_at > :fromMillis AND medication_id = :medicationId",
    )
    suspend fun deletePendingAfterForMedication(medicationId: Long, fromMillis: Long)

    @Query("DELETE FROM dose_logs WHERE medication_id = :medicationId")
    suspend fun deleteForMedication(medicationId: Long)

    @Query(
        "SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN status = 'taken' THEN 1 ELSE 0 END) AS taken, " +
            "SUM(CASE WHEN status = 'skipped' THEN 1 ELSE 0 END) AS skipped, " +
            "SUM(CASE WHEN status = 'missed' THEN 1 ELSE 0 END) AS missed " +
            "FROM dose_logs WHERE scheduled_at >= :fromMillis AND scheduled_at < :untilMillis",
    )
    @Transaction
    suspend fun getStats(fromMillis: Long, untilMillis: Long): DoseStatsRow?

    @Query("SELECT * FROM dose_logs")
    suspend fun getAllOnce(): List<DoseLogEntity>

    @Query(
        "SELECT * FROM dose_logs WHERE status = 'scheduled' AND scheduled_at < :millis " +
            "ORDER BY scheduled_at ASC LIMIT 500",
    )
    suspend fun getUnresolvedOlderThan(millis: Long): List<DoseLogEntity>

    /** Unacknowledged doses with a pending snooze/repeat reminder (re-armed after reboot). */
    @Query("SELECT * FROM dose_logs WHERE status = 'scheduled' AND remind_until IS NOT NULL")
    suspend fun getPendingReminders(): List<DoseLogEntity>

    @Transaction
    suspend fun replaceAll(doses: List<DoseLogEntity>) {
        deleteAll()
        insertAll(doses)
    }

    @Query("DELETE FROM dose_logs")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(doses: List<DoseLogEntity>)
}

/** Projection row for the adherence aggregate query (null when no rows in range). */
data class DoseStatsRow(
    val total: Int,
    val taken: Int,
    val skipped: Int,
    val missed: Int,
)
