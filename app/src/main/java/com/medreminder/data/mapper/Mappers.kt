package com.medreminder.data.mapper

import com.medreminder.data.local.entity.DoseLogEntity
import com.medreminder.data.local.entity.MedicationEntity
import com.medreminder.domain.model.DoseLog
import com.medreminder.domain.model.DoseStatus
import com.medreminder.domain.model.DosageUnit
import com.medreminder.domain.model.Medication
import com.medreminder.domain.model.MedicationForm
import com.medreminder.domain.model.MedicationStatus
import com.medreminder.domain.model.ScheduleType
import kotlinx.serialization.json.Json

/**
 * Bidirectional mapping between Room entities and domain models.
 *
 * Enums are persisted by their stable [key] strings (never by `name`/ordinal)
 * so that renaming enum entries cannot corrupt stored data. Schedules travel
 * as kotlinx-serialization JSON.
 */
object MedicationMapper {

    internal val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun toEntity(m: Medication): MedicationEntity = MedicationEntity(
        id = m.id,
        name = m.name,
        dosageValue = m.dosageValue,
        dosageUnit = m.dosageUnit.key,
        form = m.form.key,
        notes = m.notes,
        colorArgb = m.colorArgb,
        iconKey = m.iconKey,
        startDate = m.startDate.toString(),
        endDate = m.endDate?.toString(),
        status = m.status.key,
        stockCount = m.stockCount,
        refillThreshold = m.refillThreshold,
        scheduleJson = json.encodeToString(ScheduleType.serializer(), m.schedule),
        alarmStyle = m.alarmStyle,
    )

    fun toDomain(e: MedicationEntity): Medication = Medication(
        id = e.id,
        name = e.name,
        dosageValue = e.dosageValue,
        dosageUnit = DosageUnit.fromKey(e.dosageUnit) ?: DosageUnit.MG,
        form = MedicationForm.fromKey(e.form),
        notes = e.notes,
        colorArgb = e.colorArgb,
        iconKey = e.iconKey,
        startDate = kotlinx.datetime.LocalDate.parse(e.startDate),
        endDate = e.endDate?.let { kotlinx.datetime.LocalDate.parse(it) },
        status = MedicationStatus.fromKey(e.status),
        stockCount = e.stockCount,
        refillThreshold = e.refillThreshold,
        schedule = json.decodeFromString(ScheduleType.serializer(), e.scheduleJson),
        alarmStyle = e.alarmStyle,
    )
}

/** Mapping for dose log rows. */
object DoseLogMapper {

    fun toEntity(d: DoseLog): DoseLogEntity = DoseLogEntity(
        id = d.id,
        medicationId = d.medicationId,
        scheduledAtMillis = d.scheduledAtMillis,
        status = d.status.key,
        takenAtMillis = d.takenAtMillis,
        skippedAtMillis = d.skippedAtMillis,
        remindUntilMillis = d.remindUntilMillis,
    )

    fun toDomain(e: DoseLogEntity): DoseLog = DoseLog(
        id = e.id,
        medicationId = e.medicationId,
        scheduledAtMillis = e.scheduledAtMillis,
        status = DoseStatus.fromKey(e.status),
        takenAtMillis = e.takenAtMillis,
        skippedAtMillis = e.skippedAtMillis,
        remindUntilMillis = e.remindUntilMillis,
    )
}
