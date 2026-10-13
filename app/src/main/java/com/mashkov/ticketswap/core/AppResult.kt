package com.mashkov.ticketswap.core

/** Typed success/failure. Used instead of kotlin.Result so the error type is known. */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
