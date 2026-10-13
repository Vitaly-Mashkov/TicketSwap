package com.mashkov.ticketswap.ui.common

import com.mashkov.ticketswap.core.AppError

/** Single place that turns an [AppError] into user-facing text. */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.Server -> text
    AppError.Unauthorized -> "Your session has expired. Please sign in again."
    AppError.NotFound -> "We couldn't find what you were looking for."
    AppError.Network -> "Can't reach the server. Check your connection and try again."
    AppError.Unknown -> "Something went wrong. Please try again."
}
