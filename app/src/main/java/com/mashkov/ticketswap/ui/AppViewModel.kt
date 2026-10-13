package com.mashkov.ticketswap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mashkov.ticketswap.container
import com.mashkov.ticketswap.data.auth.AuthRepository
import com.mashkov.ticketswap.data.auth.SessionManager
import com.mashkov.ticketswap.data.cart.CartRepository
import com.mashkov.ticketswap.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** App-level state: decides where to start (login vs. content), handles logout and expired sessions. */
class AppViewModel(
    private val authRepository: AuthRepository,
    private val cartRepository: CartRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    /** null while the stored session is being checked. */
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    /** Emits when the server rejected our token mid-session -> navigate to login. */
    val sessionExpired: SharedFlow<Unit> = sessionManager.expired

    init {
        viewModelScope.launch {
            _startDestination.value =
                if (authRepository.isSessionValid()) Routes.EVENTS else Routes.LOGIN
        }
    }

    fun logout() {
        // The previous user's cart must not leak into the next session.
        cartRepository.clear()
        viewModelScope.launch { authRepository.logout() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppViewModel(container.authRepository, container.cartRepository, container.sessionManager)
            }
        }
    }
}
