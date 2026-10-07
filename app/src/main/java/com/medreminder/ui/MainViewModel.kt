package com.medreminder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.data.backup.BackupManager
import com.medreminder.domain.model.AdherenceStats
import com.medreminder.domain.model.DoseLog
import com.medreminder.domain.model.DosageUnit
import com.medreminder.domain.model.Medication
import com.medreminder.domain.model.MedicationForm
import com.medreminder.domain.model.MedicationStatus
import com.medreminder.domain.model.ScheduleType
import com.medreminder.domain.repository.DoseLogRepository
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.domain.usecase.MarkDoseSkippedUseCase
import com.medreminder.domain.usecase.MarkDoseTakenUseCase
import com.medreminder.domain.usecase.ObserveSettingsUseCase
import com.medreminder.domain.usecase.RescheduleMedicationUseCase
import com.medreminder.domain.usecase.SaveMedicationUseCase
import com.medreminder.domain.usecase.SnoozeDoseUseCase
import com.medreminder.util.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import javax.inject.Inject

data class TodayRow(val dose: DoseLog, val medication: Medication)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val doseLogRepository: DoseLogRepository,
    private val saveMedication: SaveMedicationUseCase,
    private val rescheduleMedication: RescheduleMedicationUseCase,
    private val markTaken: MarkDoseTakenUseCase,
    private val markSkipped: MarkDoseSkippedUseCase,
    private val snoozeDose: SnoozeDoseUseCase,
    private val settingsUseCase: ObserveSettingsUseCase,
    private val clock: Clock,
    private val backupManager: BackupManager,
    private val rescheduleAll: com.medreminder.domain.usecase.RescheduleAllUseCase,
    private val notifier: com.medreminder.notification.Notifier,
    private val alarms: com.medreminder.scheduler.AlarmScheduler,
) : ViewModel() {

    val medications: StateFlow<List<Medication>> = medicationRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = settingsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.medreminder.domain.model.AppSettings())

    val today: StateFlow<List<TodayRow>> = combine(
        medications,
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(todayRange())
                kotlinx.coroutines.delay(60_000)
            }
        },
    ) { meds, range -> meds to range }
        .flatMapLatest { (meds, range) ->
            val byId = meds.associateBy { it.id }
            doseLogRepository.observeBetween(range.first, range.second).map { doses ->
                doses.mapNotNull { dose -> byId[dose.medicationId]?.let { TodayRow(dose, it) } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<AdherenceStats> = kotlinx.coroutines.flow.flow {
        while (true) {
            val now = java.time.Instant.ofEpochMilli(clock.nowMillis())
            val zone = ZoneId.systemDefault()
            val date = now.atZone(zone).toLocalDate()
            val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val until = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            emit(doseLogRepository.getStats(from, until))
            kotlinx.coroutines.delay(60_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdherenceStats(0, 0, 0, 0))

    fun addMedication(
        name: String,
        dosage: String,
        unit: DosageUnit,
        time: LocalTime,
    ) {
        val value = dosage.toDoubleOrNull() ?: return
        val medication = Medication(
            name = name.trim(),
            dosageValue = value,
            dosageUnit = unit,
            form = MedicationForm.TABLET,
            startDate = kotlinx.datetime.LocalDate(
                LocalDate.now().year, LocalDate.now().monthValue, LocalDate.now().dayOfMonth,
            ),
            schedule = ScheduleType.Daily(listOf(time)),
        )
        viewModelScope.launch {
            saveMedication(medication).onSuccess { id ->
                rescheduleMedication(medication.copy(id = id), RescheduleMedicationUseCase.DEFAULT_HORIZON_MILLIS)
            }
        }
    }

    fun setActive(medication: Medication, active: Boolean) {
        viewModelScope.launch {
            // Saving the updated model keeps validation and persistence in one path.
            val updated = medication.copy(
                status = if (active) MedicationStatus.ACTIVE else MedicationStatus.PAUSED,
            )
            saveMedication(updated).onSuccess {
                rescheduleMedication(updated, RescheduleMedicationUseCase.DEFAULT_HORIZON_MILLIS)
            }
        }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            // Cancel every scheduler entry for this medication first. The database
            // foreign key then removes its dose history atomically with the delete.
            alarms.cancelFor(medication.id)
            medicationRepository.delete(medication.id)
            doseLogRepository.deleteForMedication(medication.id)
        }
    }

    fun take(doseId: Long) = viewModelScope.launch {
        alarms.cancelReminders(listOf(doseId))
        markTaken(doseId)
        notifier.cancelDose(doseId)
    }

    fun skip(doseId: Long) = viewModelScope.launch {
        alarms.cancelReminders(listOf(doseId))
        markSkipped(doseId)
        notifier.cancelDose(doseId)
    }

    fun snooze(doseId: Long) = viewModelScope.launch {
        alarms.cancelReminders(listOf(doseId))
        snoozeDose(doseId, settings.value.snoozeMinutes)
        notifier.cancelDose(doseId)
    }

    fun exportBackup(uri: android.net.Uri) {
        viewModelScope.launch { backupManager.exportTo(uri) }
    }

    fun importBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            backupManager.importFrom(uri).onSuccess {
                rescheduleAll()
            }
        }
    }

    fun updateSettings(
        transform: (com.medreminder.domain.model.AppSettings) ->
            com.medreminder.domain.model.AppSettings,
    ) {
        viewModelScope.launch { settingsUseCase.update(transform) }
    }

    private fun todayRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val date = java.time.Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
        return date.atStartOfDay(zone).toInstant().toEpochMilli() to
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    companion object {
        const val DEFAULT_TIME = "08:00"
    }
}
