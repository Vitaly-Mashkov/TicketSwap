package com.mashkov.ticketswap.data.cart

import com.apollographql.apollo.ApolloClient
import com.mashkov.ticketswap.core.AppError
import com.mashkov.ticketswap.core.AppResult
import com.mashkov.ticketswap.core.executeCatching
import com.mashkov.ticketswap.core.serverError
import com.mashkov.ticketswap.data.auth.SessionManager
import com.mashkov.ticketswap.data.auth.TokenStorage
import com.mashkov.ticketswap.graphql.AddTicketToCartMutation
import com.mashkov.ticketswap.graphql.CheckoutMutation
import com.mashkov.ticketswap.graphql.LoadCartMutation
import com.mashkov.ticketswap.graphql.RemoveTicketFromCartMutation
import com.mashkov.ticketswap.graphql.fragment.CartLineFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single source of truth for the cart on the client. The real cart lives on the server;
 * after every operation we replace our copy with the cart the server returned.
 */
class CartRepository(
    private val apollo: ApolloClient,
    private val tokenStorage: TokenStorage,
    private val sessionManager: SessionManager,
) {
    private val _cart = MutableStateFlow(Cart.EMPTY)
    val cart: StateFlow<Cart> = _cart.asStateFlow()

    // Operations run one at a time, so an older response can never overwrite a newer one.
    private val mutex = Mutex()

    suspend fun refresh(): AppResult<Unit> = withToken { token ->
        when (val result = apollo.mutation(LoadCartMutation(token)).executeCatching()) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val payload = result.value.authenticatedUser
                commit(payload.error?.text, payload.user?.cart?.map { it.cartLineFields })
            }
        }
    }

    suspend fun addTicket(ticketId: String): AppResult<Unit> = withToken { token ->
        when (val result = apollo.mutation(AddTicketToCartMutation(token, ticketId)).executeCatching()) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val payload = result.value.addTicketToCart
                commit(payload.error?.text, payload.cart?.lines?.map { it.cartLineFields })
            }
        }
    }

    /** Removes ONE unit of the ticket (that is how the backend works). */
    suspend fun removeTicket(ticketId: String): AppResult<Unit> = withToken { token ->
        when (val result = apollo.mutation(RemoveTicketFromCartMutation(token, ticketId)).executeCatching()) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val payload = result.value.removeTicketFromCart
                commit(payload.error?.text, payload.cart?.lines?.map { it.cartLineFields })
            }
        }
    }

    /** Pays for everything in the cart. On success the cart is empty. */
    suspend fun checkout(cardNumber: String): AppResult<List<PurchasedTicket>> = withToken { token ->
        when (val result = apollo.mutation(CheckoutMutation(token, cardNumber)).executeCatching()) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val payload = result.value.checkout
                val errorText = payload.error?.text
                val tickets = payload.purchasedTickets
                when {
                    errorText != null -> AppResult.Failure(serverError(errorText))
                    tickets == null -> AppResult.Failure(AppError.Unknown)
                    else -> {
                        _cart.value = Cart.EMPTY
                        AppResult.Success(
                            tickets.map {
                                PurchasedTicket(it.id, it.label, it.priceCents, it.currency, it.barcode)
                            },
                        )
                    }
                }
            }
        }
    }

    /** Forget the local copy (on logout). */
    fun clear() {
        _cart.value = Cart.EMPTY
    }

    private suspend fun <T> withToken(block: suspend (String) -> AppResult<T>): AppResult<T> =
        mutex.withLock {
            val token = tokenStorage.token.first()
            val result = if (token == null) AppResult.Failure(AppError.Unauthorized) else block(token)
            if (result is AppResult.Failure && result.error == AppError.Unauthorized) {
                // The server no longer knows this token: drop the session, UI goes to login.
                _cart.value = Cart.EMPTY
                sessionManager.expire()
            }
            result
        }

    private fun commit(errorText: String?, lines: List<CartLineFields>?): AppResult<Unit> = when {
        errorText != null -> AppResult.Failure(serverError(errorText))
        lines == null -> AppResult.Failure(AppError.Unknown)
        else -> {
            _cart.value = Cart(lines.map { it.toDomain() })
            AppResult.Success(Unit)
        }
    }
}

private fun CartLineFields.toDomain() = CartItem(
    ticketId = ticketId,
    eventId = eventId,
    eventTitle = eventTitle,
    ticketLabel = ticketLabel,
    priceCents = priceCents,
    currency = currency,
)
