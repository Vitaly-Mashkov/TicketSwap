package com.mashkov.ticketswap.ui.cart

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class CardNumberVisualTransformationTest {

    @Test
    fun `digits are grouped by four and the cursor maps both ways`() {
        val result = CardNumberVisualTransformation().filter(AnnotatedString("4242424242"))
        val mapping = result.offsetMapping

        assertEquals("4242 4242 42", result.text.text)

        // Before the first space there is no shift yet...
        assertEquals(4, mapping.originalToTransformed(4))
        // ...after the 5th digit the cursor sits behind one inserted space.
        assertEquals(6, mapping.originalToTransformed(5))
        assertEquals(12, mapping.originalToTransformed(10))

        assertEquals(4, mapping.transformedToOriginal(5)) // right after the space
        assertEquals(5, mapping.transformedToOriginal(6))
        assertEquals(10, mapping.transformedToOriginal(12))
    }
}
