package com.mashkov.ticketswap.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerErrorTest {

    @Test
    fun `access token messages mean the session is gone, anything else is a plain server error`() {
        // Both texts are what the backend really sends (see getSessionWithError in index.js).
        assertEquals(AppError.Unauthorized, serverError("Invalid or unknown access token."))
        assertEquals(AppError.Unauthorized, serverError("Missing or invalid access token."))

        assertEquals(AppError.Server("Cart is empty."), serverError("Cart is empty."))
    }
}
