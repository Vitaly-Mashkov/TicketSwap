package com.mashkov.ticketswap.data.events

import java.time.Instant

/** What the events list needs. Independent of Apollo's generated classes. */
data class EventSummary(
    val id: String,
    val title: String,
    val venueName: String,
    val imageUrl: String,
    val startsAt: Instant,
    val isPast: Boolean,
    /** Cheapest ticket that can still be bought; null when everything is sold out. */
    val fromPriceCents: Int?,
    val currency: String?,
)
