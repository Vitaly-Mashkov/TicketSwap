package com.mashkov.ticketswap.data.events

import java.time.Instant

data class EventDetails(
    val id: String,
    val title: String,
    val description: String?,
    val venueName: String,
    val imageUrl: String,
    val startsAt: Instant,
    val isPast: Boolean,
    val tickets: List<TicketType>,
)

data class TicketType(
    val id: String,
    val label: String,
    val priceCents: Int,
    val currency: String,
    val quantityAvailable: Int,
)
