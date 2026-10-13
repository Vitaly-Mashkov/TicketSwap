package com.mashkov.ticketswap.ui.cart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

/**
 * Draws a scannable EAN-13 barcode. ZXing only encodes the bars (a one-row BitMatrix, quiet
 * zones included); the drawing is ours. Always black on white, even in dark theme, or scanners fail.
 *
 * Draws nothing if [code] is not a valid EAN-13 (ZXing verifies the check digit).
 */
@Composable
fun Ean13Barcode(code: String, modifier: Modifier = Modifier, height: Dp = 96.dp) {
    val matrix = remember(code) {
        runCatching { MultiFormatWriter().encode(code, BarcodeFormat.EAN_13, 0, 0) }.getOrNull()
    }
    Canvas(modifier.fillMaxWidth().height(height).background(Color.White)) {
        val bits = matrix ?: return@Canvas
        // Whole pixels per module keep the bars sharp; the barcode is centred.
        val module = (size.width / bits.width).toInt().coerceAtLeast(1)
        val startX = (size.width - module * bits.width) / 2f
        for (x in 0 until bits.width) {
            if (bits.get(x, 0)) {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(startX + x * module, 0f),
                    size = Size(module.toFloat(), size.height),
                )
            }
        }
    }
}
