package com.mashkov.ticketswap.data.auth

import kotlinx.coroutines.flow.Flow

/**
 * Where the access token lives. An interface so the implementation can be swapped
 * (e.g. for an encrypted one) without touching the rest of the app.
 */
interface TokenStorage {
    val token: Flow<String?>
    suspend fun save(token: String)
    suspend fun clear()
}
