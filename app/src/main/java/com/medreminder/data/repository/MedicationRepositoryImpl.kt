package com.medreminder.data.repository

import com.medreminder.data.local.db.MedicationDao
import com.medreminder.data.mapper.MedicationMapper
import com.medreminder.domain.model.Medication
import com.medreminder.domain.model.MedicationStatus
import com.medreminder.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [MedicationRepository]. All work happens in the caller's dispatcher
 *  (Room suspend functions dispatch onto the transaction executor internally). */
@Singleton
class MedicationRepositoryImpl @Inject constructor(
    private val dao: MedicationDao,
) : MedicationRepository {

    override fun observeAll(): Flow<List<Medication>> =
        dao.observeAll().map { list -> list.map(MedicationMapper::toDomain) }

    override fun observeActive(): Flow<List<Medication>> =
        dao.observeActive().map { list -> list.map(MedicationMapper::toDomain) }

    override suspend fun getById(id: Long): Medication? =
        dao.getById(id)?.let(MedicationMapper::toDomain)

    override suspend fun upsert(medication: Medication): Long {
        require(medication.name.isNotBlank()) { "medication name must not be blank" }
        val entity = MedicationMapper.toEntity(medication)
        return if (entity.id == 0L) dao.upsert(entity) else { dao.upsert(entity); entity.id }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun setStatus(id: Long, active: Boolean) =
        dao.setStatus(id, active)

    override suspend fun decrementStock(medicationId: Long, amount: Int): Int? {
        require(amount >= 0) { "amount must not be negative" }
        val updated = dao.decrementStock(medicationId, amount)
        if (updated == 0) return null // stock tracking off or row missing
        return dao.getById(medicationId)?.stockCount
    }

    override suspend fun setStock(medicationId: Long, count: Int) {
        require(count >= 0) { "stock count must not be negative" }
        dao.setStock(medicationId, count)
    }

    override suspend fun replaceAll(medications: List<Medication>) =
        dao.replaceAll(medications.map(MedicationMapper::toEntity))

    override suspend fun getAllOnce(): List<Medication> =
        dao.getAllOnce().map(MedicationMapper::toDomain)

    /** Convenience for callers that want the raw status enum of one medication. */
    suspend fun statusOf(id: Long): MedicationStatus? =
        dao.getById(id)?.let { MedicationStatus.fromKey(it.status) }
}
