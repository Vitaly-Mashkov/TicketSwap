package com.mashkov.ticketswap.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mashkov.ticketswap.container
import com.mashkov.ticketswap.core.AppError
import com.mashkov.ticketswap.core.AppResult
import com.mashkov.ticketswap.data.cart.CartRepository
import com.mashkov.ticketswap.data.events.EventSummary
import com.mashkov.ticketswap.data.events.EventsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EventsContent {
    data object Loading : EventsContent
    data class Error(val error: AppError) : EventsContent
    data class Success(val events: List<EventSummary>) : EventsContent
}

data class EventsUiState(
    val query: String = "",
    val content: EventsContent = EventsContent.Loading,
    val cartCount: Int = 0,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class EventsViewModel(
    private val repository: EventsRepository,
    private val cartRepository: CartRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val reloadTrigger = MutableStateFlow(0)

    private val content: Flow<EventsContent> = combine(
        // Typing is debounced; clearing the field applies instantly.
        query
            .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
            .map { it.trim() }
            .distinctUntilChanged(),
        reloadTrigger,
    ) { text, _ -> text }
        // A new query (or retry) cancels the request still in flight.
        .flatMapLatest { text ->
            flow {
                emit(EventsContent.Loading)
                emit(repository.getEvents(text).toContent())
            }
        }

    val uiState: StateFlow<EventsUiState> =
        combine(query, content, cartRepository.cart) { q, c, cart -> EventsUiState(q, c, cart.totalQuantity) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUiState())

    init {
        // The server keeps the cart between app launches; load it so the badge is right. Best effort.
        viewModelScope.launch { cartRepository.refresh() }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onRetry() {
        reloadTrigger.update { it + 1 }
    }

    private fun AppResult<List<EventSummary>>.toContent(): EventsContent = when (this) {
        is AppResult.Success -> EventsContent.Success(value)
        is AppResult.Failure -> EventsContent.Error(error)
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { EventsViewModel(container.eventsRepository, container.cartRepository) }
        }
    }
}
