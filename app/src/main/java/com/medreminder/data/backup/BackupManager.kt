package com.medreminder.data.backup

import android.content.Context
import android.net.Uri
import com.medreminder.domain.model.AppError
import com.medreminder.domain.model.Medication
import androidx.room.withTransaction
import com.medreminder.data.local.db.MedReminderDatabase
import com.medreminder.data.mapper.DoseLogMapper
import com.medreminder.data.mapper.MedicationMapper
import com.medreminder.domain.repository.DoseLogRepository
import com.medreminder.domain.repository.MedicationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export/import of all app data as a single versioned JSON document.
 *
 * Security & reliability decisions:
 *  - Streams go through SAF [Uri]s only — the app never requests storage permissions.
 *  - Import is fully validated *before* any write; the actual replacement runs in
 *    one Room transaction per table (via repository replaceAll), so a crash
 *    mid-import cannot leave half-restored rows behind.
 *  - Untrusted input is parsed with strict settings (no lenient mode, size caps).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val medicationRepository: MedicationRepository,
    private val doseLogRepository: DoseLogRepository,
    private val database: MedReminderDatabase,
) {
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        prettyPrint = true
    }

    /** Writes the full database to [target]; returns the number of medications exported. */
    suspend fun exportTo(target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val file = BackupFile(
                schemaVersion = BackupFile.VERSION,
                exportedAtEpochMillis = System.currentTimeMillis(),
                medications = medicationRepository.getAllOnce(),
                doseLogs = doseLogRepository.getAllOnce(),
            )
            context.contentResolver.openOutputStream(target, "wt")?.use { stream ->
                stream.writer().buffered().use { it.write(json.encodeToString(BackupFile.serializer(), file)) }
            } ?: error("Could not open output stream")
            file.medications.size
        }
    }

    /**
     * Reads and validates [source], then atomically replaces all app data.
     * Returns the imported medication count or an [AppError].
     */
    suspend fun importFrom(source: Uri): Result<Int> = withContext(Dispatchers.IO) {
        val parsed = runCatching {
            val text = context.contentResolver.openInputStream(source)?.use { stream ->
                val reader = BufferedReader(stream.reader())
                val builder = StringBuilder()
                val buffer = CharArray(8 * 1024)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (builder.length + count > MAX_JSON_CHARS) {
                        throw AppError.InvalidBackup("file too large")
                    }
                    builder.append(buffer, 0, count)
                }
                builder.toString()
            } ?: throw AppError.Io(IllegalStateException("Cannot open input stream"))
            json.decodeFromString(BackupFile.serializer(), text)
        }.getOrElse { t ->
            return@withContext when (t) {
                is AppError -> Result.failure(t)
                is SerializationException -> Result.failure(AppError.InvalidBackup(t.message ?: "malformed JSON"))
                else -> Result.failure(AppError.Io(t))
            }
        }

        val validation = validate(parsed)
        if (validation.isFailure) return@withContext validation

        runCatching {
            // Replace both tables inside one Room transaction. The foreign-key
            // parent table is restored before child dose rows.
            database.withTransaction {
                // Child rows must be removed before their foreign-key parents.
                // Both tables are then restored in the same transaction.
                database.doseLogDao().deleteAll()
                database.medicationDao().deleteAll()
                database.medicationDao().insertAll(parsed.medications.map(MedicationMapper::toEntity))
                database.doseLogDao().insertAll(parsed.doseLogs.map(DoseLogMapper::toEntity))
            }
            parsed.medications.size
        }
    }

    /** Pure validator — unit-tested without Android. */
    fun validate(file: BackupFile): Result<Unit> {
        if (file.schemaVersion != BackupFile.VERSION) {
            return Result.failure(
                AppError.InvalidBackup("unsupported schemaVersion ${file.schemaVersion}"),
            )
        }
        if (file.medications.size > BackupFile.MAX_MEDICATIONS) {
            return Result.failure(AppError.InvalidBackup("too many medications"))
        }
        if (file.doseLogs.size > BackupFile.MAX_DOSE_LOGS) {
            return Result.failure(AppError.InvalidBackup("too many dose logs"))
        }
        val ids = mutableSetOf<Long>()
        for (m in file.medications) {
            if (!isValidMedication(m)) {
                return Result.failure(AppError.InvalidBackup("bad medication '${m.name}'"))
            }
            if (!ids.add(m.id)) {
                return Result.failure(AppError.InvalidBackup("duplicate medication id ${m.id}"))
            }
        }
        val doseIds = mutableSetOf<Long>()
        val doseKeys = mutableSetOf<Pair<Long, Long>>()
        for (d in file.doseLogs) {
            if (d.id <= 0L) {
                return Result.failure(AppError.InvalidBackup("invalid dose id"))
            }
            if (d.medicationId !in ids) {
                return Result.failure(AppError.InvalidBackup("orphan dose log ${d.id}"))
            }
            if (d.repeatCount !in 0..100) {
                return Result.failure(AppError.InvalidBackup("invalid repeat count"))
            }
            if (d.scheduledAtMillis < 0) {
                return Result.failure(AppError.InvalidBackup("negative timestamp"))
            }
            if (!doseIds.add(d.id)) {
                return Result.failure(AppError.InvalidBackup("duplicate dose id ${d.id}"))
            }
            if (!doseKeys.add(d.medicationId to d.scheduledAtMillis)) {
                return Result.failure(AppError.InvalidBackup("duplicate dose occurrence"))
            }
        }
        return Result.success(Unit)
    }

    private fun isValidMedication(m: Medication): Boolean =
        m.name.isNotBlank() &&
            m.name.length <= 200 &&
            m.dosageValue.isFinite() && m.dosageValue > 0.0 &&
            m.startDate <= (m.endDate ?: m.startDate) &&
            (m.stockCount == null || (m.stockCount >= 0 && m.stockCount <= 1_000_000)) &&
            m.refillThreshold in 0..1_000_000

    private companion object {
        const val MAX_JSON_CHARS = 64 * 1024 * 1024
    }
}
