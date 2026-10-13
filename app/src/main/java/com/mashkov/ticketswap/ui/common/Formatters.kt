package com.mashkov.ticketswap.ui.common

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

private val eventDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

fun formatEventDate(instant: Instant): String = eventDateFormatter.format(instant)

/** Money stays in integer cents everywhere; it only becomes a decimal here, for display. */
fun formatPrice(cents: Int, currencyCode: String): String =
    NumberFormat.getCurrencyInstance().apply {
        currency = Currency.getInstance(currencyCode)
    }.format(BigDecimal(cents).movePointLeft(2))
