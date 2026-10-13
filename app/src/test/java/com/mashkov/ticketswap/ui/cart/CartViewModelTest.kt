package com.mashkov.ticketswap.ui.cart

import com.mashkov.ticketswap.MainDispatcherRule
import com.mashkov.ticketswap.cartItem
import com.mashkov.ticketswap.core.AppError
import com.mashkov.ticketswap.core.AppResult
import com.mashkov.ticketswap.data.cart.Cart
import com.mashkov.ticketswap.data.cart.CartRepository
import com.mashkov.ticketswap.data.cart.PurchasedTicket
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CartViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cartFlow = MutableStateFlow(Cart.EMPTY)
    private val repository = mockk<CartRepository>()

    @Before
    fun setUp() {
        every { repository.cart } returns cartFlow
        coEvery { repository.refresh() } returns AppResult.Success(Unit)
    }

    private fun viewModel() = CartViewModel(repository)

    @Test
    fun `successful payment shows the tickets labelled with the event titles from the cart`() {
        cartFlow.value = Cart(
            listOf(
                cartItem("t1", eventTitle = "Jazz Night", label = "GA", priceCents = 2500),
                cartItem("t2", eventTitle = "Techno Night", label = "VIP", priceCents = 4000),
            ),
        )
        coEvery { repository.checkout("4242424242424242") } coAnswers {
            cartFlow.value = Cart.EMPTY // the real repository empties the cart on success
            AppResult.Success(
                listOf(
                    PurchasedTicket("w1", "GA", 2500, "USD", "4006381333931"),
                    PurchasedTicket("w2", "VIP", 4000, "USD", "5901234123457"),
                ),
            )
        }

        val vm = viewModel()
        vm.onCardNumberChange("4242 4242 4242 4242") // spaces are stripped
        vm.onPayClick()

        val state = vm.uiState.value
        val purchase = requireNotNull(state.purchase)
        assertEquals(listOf("Jazz Night", "Techno Night"), purchase.items.map { it.eventTitle })
        assertEquals(6500, purchase.totalCents)
        assertEquals("", state.cardNumber) // card digits are not kept after paying
    }

    @Test
    fun `failed payment keeps the cart and reports the error`() {
        val cart = Cart(listOf(cartItem("t1")))
        cartFlow.value = cart
        val error = AppError.Server("Invalid or unsupported credit card number.")
        coEvery { repository.checkout(any()) } returns AppResult.Failure(error)

        val vm = viewModel()
        vm.onCardNumberChange("1234567890123456")
        vm.onPayClick()

        val state = vm.uiState.value
        assertNull(state.purchase)
        assertEquals(error, state.paymentError)
        assertEquals(cart, state.cart)
        assertFalse(state.isPaying)
        assertTrue(state.canPay) // the user can fix the number and try again
    }

    @Test
    fun `tapping Pay twice sends only one checkout`() {
        cartFlow.value = Cart(listOf(cartItem("t1")))
        val gate = CompletableDeferred<AppResult<List<PurchasedTicket>>>()
        coEvery { repository.checkout(any()) } coAnswers { gate.await() }

        val vm = viewModel()
        vm.onCardNumberChange("4242424242424242")
        vm.onPayClick()
        vm.onPayClick() // the first request is still in flight

        assertTrue(vm.uiState.value.isPaying)
        gate.complete(AppResult.Failure(AppError.Network))
        coVerify(exactly = 1) { repository.checkout(any()) }
    }
}
