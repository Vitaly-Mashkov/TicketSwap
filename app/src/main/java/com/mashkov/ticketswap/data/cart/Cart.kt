package com.mashkov.ticketswap.data.cart

/** One unit of a ticket in the cart (the backend keeps one line per unit). */
data class CartItem(
    val ticketId: String,
    val eventId: String,
    val eventTitle: String,
    val ticketLabel: String,
    val priceCents: Int,
    val currency: String,
)

/** Units of the same ticket grouped into one row, as the user sees them. */
data class CartEntry(
    val ticketId: String,
    val eventTitle: String,
    val ticketLabel: String,
    val unitPriceCents: Int,
    val currency: String,
    val quantity: Int,
) {
    val subtotalCents: Int get() = unitPriceCents * quantity
}

data class Cart(val items: List<CartItem>) {
    val isEmpty: Boolean get() = items.isEmpty()
    val totalQuantity: Int get() = items.size
    val totalCents: Int get() = items.sumOf { it.priceCents }

    /** The catalog is single-currency (USD), so the first item decides. */
    val currency: String? get() = items.firstOrNull()?.currency

    /** Grouped by ticket, in the order each ticket was first added. */
    val entries: List<CartEntry>
        get() = items.groupBy { it.ticketId }.map { (ticketId, units) ->
            val first = units.first()
            CartEntry(
                ticketId = ticketId,
                eventTitle = first.eventTitle,
                ticketLabel = first.ticketLabel,
                unitPriceCents = first.priceCents,
                currency = first.currency,
                quantity = units.size,
            )
        }

    fun quantityOf(ticketId: String): Int = items.count { it.ticketId == ticketId }

    companion object {
        val EMPTY = Cart(emptyList())
    }
}

/** A ticket in the wallet after checkout. [barcode] is a 13-digit EAN-13 string. */
data class PurchasedTicket(
    val id: String,
    val label: String,
    val priceCents: Int,
    val currency: String,
    val barcode: String,
)
