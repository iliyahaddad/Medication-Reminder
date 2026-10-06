package com.medreminder.data.backup

import com.medreminder.domain.model.DoseLog
import com.medreminder.domain.model.Medication
import kotlinx.serialization.Serializable

/**
 * Versioned JSON backup schema (exported/imported via the Storage Access
 * Framework — no permissions required, the user picks the file).
 *
 * [BackupFile.VERSION] is bumped whenever the shape changes incompatibly.
 * Import validates the version and every record before touching the database.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int = VERSION,
    val exportedAtEpochMillis: Long = 0L,
    val medications: List<Medication> = emptyList(),
    val doseLogs: List<DoseLog> = emptyList(),
) {
    companion object {
        const val VERSION = 1

        /** Hard sanity limits used during import validation. */
        const val MAX_MEDICATIONS = 10_000
        const val MAX_DOSE_LOGS = 500_000
    }
}
