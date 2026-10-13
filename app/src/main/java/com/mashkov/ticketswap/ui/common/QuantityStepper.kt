package com.mashkov.ticketswap.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** "− N +" control. While [isBusy] the number is replaced by a spinner. */
@Composable
fun QuantityStepper(
    label: String,
    quantity: Int,
    isBusy: Boolean,
    canRemove: Boolean,
    canAdd: Boolean,
    onRemove: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = onRemove,
            enabled = canRemove,
            modifier = Modifier.semantics { contentDescription = "Remove one $label ticket" },
        ) { Text("−") }

        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            if (isBusy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(quantity.toString(), style = MaterialTheme.typography.titleMedium)
            }
        }

        FilledTonalIconButton(
            onClick = onAdd,
            enabled = canAdd,
            modifier = Modifier.semantics { contentDescription = "Add one $label ticket" },
        ) { Text("+") }
    }
}
