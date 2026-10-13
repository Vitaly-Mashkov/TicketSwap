package com.mashkov.ticketswap.data.cart

import com.mashkov.ticketswap.cartItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CartTest {

    @Test
    fun `totals and quantities count every unit`() {
        val cart = Cart(
            listOf(
                cartItem("a", priceCents = 1000),
                cartItem("a", priceCents = 1000),
                cartItem("b", priceCents = 2500),
            ),
        )

        assertEquals(3, cart.totalQuantity)
        assertEquals(4500, cart.totalCents)
        assertEquals(2, cart.quantityOf("a"))
        assertEquals(0, cart.quantityOf("unknown"))
    }

    @Test
    fun `entries group units by ticket in the order they were first added`() {
        val cart = Cart(
            listOf(
                cartItem("vip", label = "VIP", priceCents = 4000),
                cartItem("ga", label = "GA", priceCents = 2500),
                cartItem("vip", label = "VIP", priceCents = 4000),
            ),
        )

        assertEquals(listOf("vip", "ga"), cart.entries.map { it.ticketId })
        val vip = cart.entries.first()
        assertEquals(2, vip.quantity)
        assertEquals(8000, vip.subtotalCents)
    }
}
