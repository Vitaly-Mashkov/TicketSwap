package com.mashkov.ticketswap.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mashkov.ticketswap.container
import com.mashkov.ticketswap.core.AppError
import com.mashkov.ticketswap.core.AppResult
import com.mashkov.ticketswap.data.cart.Cart
import com.mashkov.ticketswap.data.cart.CartRepository
import com.mashkov.ticketswap.data.events.EventDetails
import com.mashkov.ticketswap.data.events.EventsRepository
import com.mashkov.ticketswap.ui.navigation.Routes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EventDetailsContent {
    data object Loading : EventDetailsContent
    data class Error(val error: AppError) : EventDetailsContent
    data class Success(val event: EventDetails) : EventDetailsContent
}

data class EventDetailsUiState(
    val content: EventDetailsContent = EventDetailsContent.Loading,
    val cart: Cart = Cart.EMPTY,
    /** Tickets with a cart request in flight; their stepper is locked. */
    val busyTicketIds: Set<String> = emptySet(),
    /** One-shot error from the last cart action, shown as a snackbar. */
    val cartError: AppError? = null,
)

class EventDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val eventsRepository: EventsRepository,
    private val cartRepository: CartRepository,
) : ViewModel() {

    private val eventId: String = checkNotNull(savedStateHandle.get<String>(Routes.EVENT_ID_ARG))

    private data class Local(
        val content: EventDetailsContent = EventDetailsContent.Loading,
        val busyTicketIds: Set<String> = emptySet(),
        val cartError: AppError? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<EventDetailsUiState> = combine(local, cartRepository.cart) { l, cart ->
        EventDetailsUiState(l.content, cart, l.busyTicketIds, l.cartError)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventDetailsUiState())

    init {
        load()
    }

    fun onRetry() = load()

    fun onAddTicket(ticketId: String) = changeQuantity(ticketId, delta = +1)

    fun onRemoveTicket(ticketId: String) = changeQuantity(ticketId, delta = -1)

    fun onCartErrorShown() = local.update { it.copy(cartError = null) }

    private fun load() {
        local.update { it.copy(content = EventDetailsContent.Loading) }
        viewModelScope.launch {
            // The steppers need BOTH the event and what is already in the cart.
            val (event, cart) = coroutineScope {
                val eventCall = async { eventsRepository.getEvent(eventId) }
                val cartCall = async { cartRepository.refresh() }
                eventCall.await() to cartCall.await()
            }
            val content = when {
                event is AppResult.Failure -> EventDetailsContent.Error(event.error)
                cart is AppResult.Failure -> EventDetailsContent.Error(cart.error)
                event is AppResult.Success -> EventDetailsContent.Success(event.value)
                else -> EventDetailsContent.Error(AppError.Unknown)
            }
            local.update { it.copy(content = content) }
        }
    }

    private fun changeQuantity(ticketId: String, delta: Int) {
        val state = local.value
        val event = (state.content as? EventDetailsContent.Success)?.event ?: return
        val ticket = event.tickets.firstOrNull { it.id == ticketId } ?: return
        if (event.isPast || ticketId in state.busyTicketIds) return
        if (delta > 0 && ticket.quantityAvailable <= 0) return
        if (delta < 0 && cartRepository.cart.value.quantityOf(ticketId) <= 0) return

        local.update { it.copy(busyTicketIds = it.busyTicketIds + ticketId, cartError = null) }
        viewModelScope.launch {
            val result = if (delta > 0) cartRepository.addTicket(ticketId) else cartRepository.removeTicket(ticketId)
            local.update { s ->
                s.copy(
                    // The backend takes a unit out of stock on add and gives it back on remove,
                    // so we mirror that locally instead of re-fetching the event.
                    content = if (result is AppResult.Success) s.content.withStockChange(ticketId, -delta) else s.content,
                    busyTicketIds = s.busyTicketIds - ticketId,
                    cartError = (result as? AppResult.Failure)?.error,
                )
            }
        }
    }

    private fun EventDetailsContent.withStockChange(ticketId: String, delta: Int): EventDetailsContent =
        if (this is EventDetailsContent.Success) {
            copy(
                event = event.copy(
                    tickets = event.tickets.map {
                        if (it.id == ticketId) it.copy(quantityAvailable = it.quantityAvailable + delta) else it
                    },
                ),
            )
        } else {
            this
        }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EventDetailsViewModel(createSavedStateHandle(), container.eventsRepository, container.cartRepository)
            }
        }
    }
}
