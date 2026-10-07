package com.medreminder.domain.model

/**
 * Domain-level error type. It is throwable so Result.failure(...) can preserve
 * structured errors across repository/use-case boundaries without exposing
 * raw exception text to the UI.
 */
sealed class AppError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    data object NotFound : AppError("entity_not_found")

    data class Validation(val messageResName: String) :
        AppError("validation:$messageResName")

    data class InvalidBackup(val reason: String) :
        AppError("invalid_backup:$reason")

    data class Io(val error: Throwable) :
        AppError("io_error", error)
}

inline fun <T> domainCall(block: () -> T): Result<T> =
    runCatching(block).recoverCatching { t ->
        if (t is AppError) throw t else throw AppError.Io(t)
    }
