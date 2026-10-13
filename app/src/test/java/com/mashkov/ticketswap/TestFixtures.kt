package com.mashkov.ticketswap

import com.mashkov.ticketswap.data.cart.CartItem

/** One unit of a ticket in the cart, with sensible defaults. */
fun cartItem(
    ticketId: String,
    eventTitle: String = "Jazz Night",
    label: String = "GA",
    priceCents: Int = 2500,
) = CartItem(
    ticketId = ticketId,
    eventId = "evt_01",
    eventTitle = eventTitle,
    ticketLabel = label,
    priceCents = priceCents,
    currency = "USD",
)
