package com.mashkov.ticketswap.ui.details

import androidx.lifecycle.SavedStateHandle
import com.mashkov.ticketswap.MainDispatcherRule
import com.mashkov.ticketswap.cartItem
import com.mashkov.ticketswap.core.AppResult
import com.mashkov.ticketswap.data.cart.Cart
import com.mashkov.ticketswap.data.cart.CartRepository
import com.mashkov.ticketswap.data.events.EventDetails
import com.mashkov.ticketswap.data.events.EventsRepository
import com.mashkov.ticketswap.data.events.TicketType
import com.mashkov.ticketswap.ui.navigation.Routes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cartFlow = MutableStateFlow(Cart.EMPTY)
    private val eventsRepository = mockk<EventsRepository>()
    private val cartRepository = mockk<CartRepository>()

    @Before
    fun setUp() {
        every { cartRepository.cart } returns cartFlow
        coEvery { cartRepository.refresh() } returns AppResult.Success(Unit)
    }

    private fun event(isPast: Boolean = false) = EventDetails(
        id = "evt_01",
        title = "Jazz Night",
        description = null,
        venueName = "Brickyard",
        imageUrl = "http://localhost:4000/images/event.jpg",
        startsAt = Instant.parse("2026-12-01T18:00:00Z"),
        isPast = isPast,
        tickets = listOf(TicketType("t1", "GA", 2500, "USD", quantityAvailable = 3)),
    )

    private fun viewModel(event: EventDetails): EventDetailsViewModel {
        coEvery { eventsRepository.getEvent("evt_01") } returns AppResult.Success(event)
        val args = SavedStateHandle(mapOf(Routes.EVENT_ID_ARG to "evt_01"))
        return EventDetailsViewModel(args, eventsRepository, cartRepository)
    }

    /** uiState only updates while someone collects it, so keep a collector alive for the test. */
    private fun TestScope.keepCollecting(vm: EventDetailsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    private fun EventDetailsViewModel.availableOf(ticketId: String): Int {
        val content = uiState.value.content as EventDetailsContent.Success
        return content.event.tickets.first { it.id == ticketId }.quantityAvailable
    }

    @Test
    fun `adding and removing a ticket moves the available stock the same way`() = runTest {
        coEvery { cartRepository.addTicket("t1") } coAnswers {
            cartFlow.value = Cart(listOf(cartItem("t1")))
            AppResult.Success(Unit)
        }
        coEvery { cartRepository.removeTicket("t1") } coAnswers {
            cartFlow.value = Cart.EMPTY
            AppResult.Success(Unit)
        }
        val vm = viewModel(event())
        keepCollecting(vm)
        assertEquals(3, vm.availableOf("t1"))

        vm.onAddTicket("t1")
        assertEquals(2, vm.availableOf("t1"))
        assertEquals(1, vm.uiState.value.cart.quantityOf("t1"))

        vm.onRemoveTicket("t1")
        assertEquals(3, vm.availableOf("t1"))
        assertEquals(0, vm.uiState.value.cart.quantityOf("t1"))
    }

    @Test
    fun `a past event never touches the cart`() = runTest {
        val vm = viewModel(event(isPast = true))
        keepCollecting(vm)

        vm.onAddTicket("t1")

        coVerify(exactly = 0) { cartRepository.addTicket(any()) }
    }
}
