package com.mashkov.ticketswap.core

/** Everything that can go wrong, in the terms the UI cares about. */
sealed interface AppError {
    /** Business error returned by the backend in `error { text }`. */
    data class Server(val text: String) : AppError

    /** The access token is missing, unknown or expired -> user must log in again. */
    data object Unauthorized : AppError

    /** The requested thing does not exist (e.g. `event(id)` returned null without an error). */
    data object NotFound : AppError

    /** No response at all: offline, timeout, bad HTTP status, unparsable body. */
    data object Network : AppError

    data object Unknown : AppError
}

/**
 * The backend reports failures as plain text and has no error codes, so detecting an
 * invalid session means matching the text. Kept in ONE place on purpose.
 */
fun serverError(text: String): AppError =
    if (text.contains("access token", ignoreCase = true)) AppError.Unauthorized
    else AppError.Server(text)
