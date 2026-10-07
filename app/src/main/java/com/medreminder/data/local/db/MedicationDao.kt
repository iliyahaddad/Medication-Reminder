package com.medreminder.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.medreminder.data.local.entity.MedicationEntity
import kotlinx.coroutines.flow.Flow

/** Room DAO for the `medications` table. All suspend fns run off the main thread. */
@Dao
interface MedicationDao {

    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE status = 'active' ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<MedicationEntity>

    @Upsert
    suspend fun upsert(medication: MedicationEntity): Long

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE medications SET status = CASE WHEN :active THEN 'active' ELSE 'paused' END WHERE id = :id")
    suspend fun setStatus(id: Long, active: Boolean)

    /**
     * Decrements stock only when tracking is on ([stock_count] IS NOT NULL) and
     * never lets it go below zero. Returns the number of rows updated.
     */
    @Query(
        "UPDATE medications SET stock_count = MAX(stock_count - :amount, 0) " +
            "WHERE id = :id AND stock_count IS NOT NULL",
    )
    suspend fun decrementStock(id: Long, amount: Int): Int

    @Query("UPDATE medications SET stock_count = :count WHERE id = :id")
    suspend fun setStock(id: Long, count: Int)

    @Transaction
    suspend fun replaceAll(medications: List<MedicationEntity>) {
        deleteAll()
        insertAll(medications)
    }

    @Query("DELETE FROM medications")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(medications: List<MedicationEntity>)
}
