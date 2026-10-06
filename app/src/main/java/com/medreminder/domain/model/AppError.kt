package com.medreminder.domain.model

/**
 * Domain-level error type. The UI layer maps each variant to a localized
 * string resource — never show raw exception messages to the user.
 */
sealed interface AppError {
    /** A persisted entity referenced by id does not exist (e.g. deleted meanwhile). */
    data object NotFound : AppError

    /** User input failed validation; carries a string-resource key name. */
    data class Validation(val messageResName: String) : AppError

    /** Backup file could not be parsed / validated. */
    data class InvalidBackup(val reason: String) : AppError

    /** An I/O operation (file stream, DB write) failed unexpectedly. */
    data class Io(val cause: Throwable) : AppError
}

/** Runs [block], normalizing unexpected exceptions into [AppError.Io]. */
inline fun <T> domainCall(block: () -> T): Result<T> =
    runCatching(block).recoverCatching { t ->
        if (t is AppError) throw t else throw AppError.Io(t)
    }
