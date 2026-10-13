package com.mashkov.ticketswap.data.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lets any repository say "the server rejected our token". The stored token is dropped
 * and the UI layer (navigation) listens to [expired] and sends the user to the login screen.
 */
class SessionManager(private val tokenStorage: TokenStorage) {

    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    suspend fun expire() {
        tokenStorage.clear()
        _expired.tryEmit(Unit)
    }
}
