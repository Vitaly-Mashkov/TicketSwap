package com.mashkov.ticketswap.data.events

import com.apollographql.apollo.ApolloClient
import com.mashkov.ticketswap.core.AppError
import com.mashkov.ticketswap.core.AppResult
import com.mashkov.ticketswap.core.executeCatching
import com.mashkov.ticketswap.core.serverError
import com.mashkov.ticketswap.graphql.EventDetailsQuery
import com.mashkov.ticketswap.graphql.EventsQuery
import com.mashkov.ticketswap.graphql.SearchEventsQuery
import com.mashkov.ticketswap.graphql.fragment.EventDetailsFields
import com.mashkov.ticketswap.graphql.fragment.EventSummaryFields
import java.time.Instant

class EventsRepository(private val apollo: ApolloClient) {

    /** Blank [query] -> the whole catalog. (The backend's search returns nothing for a blank query.) */
    suspend fun getEvents(query: String): AppResult<List<EventSummary>> {
        val text = query.trim()
        return if (text.isEmpty()) loadAll() else search(text)
    }

    suspend fun getEvent(id: String): AppResult<EventDetails> =
        when (val result = apollo.query(EventDetailsQuery(id)).executeCatching()) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val payload = result.value.event
                val errorText = payload.error?.text
                val event = payload.event?.eventDetailsFields
                when {
                    errorText != null -> AppResult.Failure(serverError(errorText))
                    event == null -> AppResult.Failure(AppError.NotFound)
                    else -> AppResult.Success(event.toDomain(Instant.now()))
                }
            }
        }

    private suspend fun loadAll(): AppResult<List<EventSummary>> =
        when (val result = apollo.query(EventsQuery()).executeCatching()) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val payload = result.value.events
                toResult(payload.error?.text, payload.events.map { it.eventSummaryFields })
            }
        }

    private suspend fun search(query: String): AppResult<List<EventSummary>> =
        when (val result = apollo.query(SearchEventsQuery(query)).executeCatching()) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val payload = result.value.searchEvents
                toResult(payload.error?.text, payload.events.map { it.eventSummaryFields })
            }
        }

    private fun toResult(
        errorText: String?,
        events: List<EventSummaryFields>,
    ): AppResult<List<EventSummary>> {
        if (errorText != null) return AppResult.Failure(serverError(errorText))
        val now = Instant.now()
        val mapped = events.map { it.toDomain(now) }
        // Upcoming first (soonest on top), then past events (most recent first).
        val (past, upcoming) = mapped.partition { it.isPast }
        return AppResult.Success(upcoming.sortedBy { it.startsAt } + past.sortedByDescending { it.startsAt })
    }
}

private fun EventSummaryFields.toDomain(now: Instant): EventSummary {
    val start = Instant.parse(startsAt)
    val cheapest = tickets.filter { it.quantityAvailable > 0 }.minByOrNull { it.priceCents }
    return EventSummary(
        id = id,
        title = title,
        venueName = venue.name,
        imageUrl = imageUrl,
        startsAt = start,
        isPast = start.isBefore(now),
        fromPriceCents = cheapest?.priceCents,
        currency = cheapest?.currency,
    )
}

private fun EventDetailsFields.toDomain(now: Instant): EventDetails {
    val start = Instant.parse(startsAt)
    return EventDetails(
        id = id,
        title = title,
        description = description?.takeIf { it.isNotBlank() },
        venueName = venue.name,
        imageUrl = imageUrl,
        startsAt = start,
        isPast = start.isBefore(now),
        tickets = tickets.map {
            TicketType(
                id = it.id,
                label = it.label,
                priceCents = it.priceCents,
                currency = it.currency,
                quantityAvailable = it.quantityAvailable,
            )
        },
    )
}
