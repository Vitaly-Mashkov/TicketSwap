package com.mashkov.ticketswap.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mashkov.ticketswap.container
import com.mashkov.ticketswap.core.AppError
import com.mashkov.ticketswap.core.AppResult
import com.mashkov.ticketswap.data.cart.Cart
import com.mashkov.ticketswap.data.cart.CartRepository
import com.mashkov.ticketswap.data.cart.PurchasedTicket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A bought ticket plus the event it belongs to (the wallet itself does not carry the event). */
data class PurchasedItem(val eventTitle: String?, val ticket: PurchasedTicket)

data class Purchase(val items: List<PurchasedItem>, val totalCents: Int, val currency: String?)

data class CartUiState(
    val cart: Cart = Cart.EMPTY,
    /** True until the first refresh finishes. */
    val isLoading: Boolean = true,
    val loadError: AppError? = null,
    val busyTicketIds: Set<String> = emptySet(),
    /** Digits only; the field formats them for display. */
    val cardNumber: String = "",
    val isPaying: Boolean = false,
    val paymentError: AppError? = null,
    /** One-shot error from a stepper action, shown as a snackbar. */
    val actionError: AppError? = null,
    /** Set after a successful payment; the screen then shows the tickets. */
    val purchase: Purchase? = null,
) {
    // The backend accepts 12-19 digits and checks Luhn itself; its error text is shown if that fails.
    val canPay: Boolean
        get() = !cart.isEmpty && cardNumber.length in 12..19 && !isPaying && busyTicketIds.isEmpty()
}

class CartViewModel(private val cartRepository: CartRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState(cart = cartRepository.cart.value))
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            cartRepository.cart.collect { cart -> _uiState.update { it.copy(cart = cart) } }
        }
        load()
    }

    fun onRetry() = load()

    fun onAddTicket(ticketId: String) = changeQuantity(ticketId, add = true)

    fun onRemoveTicket(ticketId: String) = changeQuantity(ticketId, add = false)

    fun onCardNumberChange(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(MAX_CARD_DIGITS)
        _uiState.update { it.copy(cardNumber = digits, paymentError = null) }
    }

    fun onActionErrorShown() = _uiState.update { it.copy(actionError = null) }

    fun onPayClick() {
        val state = _uiState.value
        if (!state.canPay) return // also blocks double taps: isPaying is set synchronously below

        val snapshot = state.cart
        _uiState.update { it.copy(isPaying = true, paymentError = null) }
        viewModelScope.launch {
            when (val result = cartRepository.checkout(state.cardNumber)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isPaying = false, cardNumber = "", purchase = buildPurchase(snapshot, result.value))
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isPaying = false, paymentError = result.error)
                }
            }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val result = cartRepository.refresh()
            _uiState.update {
                it.copy(isLoading = false, loadError = (result as? AppResult.Failure)?.error)
            }
        }
    }

    private fun changeQuantity(ticketId: String, add: Boolean) {
        val state = _uiState.value
        if (ticketId in state.busyTicketIds || state.isPaying || state.purchase != null) return

        _uiState.update {
            it.copy(busyTicketIds = it.busyTicketIds + ticketId, actionError = null, paymentError = null)
        }
        viewModelScope.launch {
            val result = if (add) cartRepository.addTicket(ticketId) else cartRepository.removeTicket(ticketId)
            _uiState.update {
                it.copy(
                    busyTicketIds = it.busyTicketIds - ticketId,
                    actionError = (result as? AppResult.Failure)?.error,
                )
            }
        }
    }

    /**
     * The backend creates the purchased tickets by walking the cart in order, so the n-th ticket
     * belongs to the n-th cart item. If the counts ever differ we simply omit the event titles.
     */
    private fun buildPurchase(cart: Cart, tickets: List<PurchasedTicket>): Purchase {
        val aligned = tickets.size == cart.items.size
        return Purchase(
            items = tickets.mapIndexed { index, ticket ->
                PurchasedItem(eventTitle = if (aligned) cart.items[index].eventTitle else null, ticket = ticket)
            },
            totalCents = tickets.sumOf { it.priceCents },
            currency = tickets.firstOrNull()?.currency,
        )
    }

    companion object {
        private const val MAX_CARD_DIGITS = 19

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { CartViewModel(container.cartRepository) }
        }
    }
}
