package com.mashkov.ticketswap.ui.cart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mashkov.ticketswap.BuildConfig
import com.mashkov.ticketswap.R
import com.mashkov.ticketswap.data.cart.CartEntry
import com.mashkov.ticketswap.ui.common.QuantityStepper
import com.mashkov.ticketswap.ui.common.formatPrice
import com.mashkov.ticketswap.ui.common.toUserMessage

/** Cart -> payment -> confirmation, all behind one ViewModel. */
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: CartViewModel = viewModel(factory = CartViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val purchase = state.purchase
    if (purchase != null) {
        // The cart is gone now; going "back" must not land on a stale screen.
        BackHandler(onBack = onDone)
        PurchaseConfirmation(purchase = purchase, onDone = onDone)
    } else {
        CartContent(
            state = state,
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onAddTicket = viewModel::onAddTicket,
            onRemoveTicket = viewModel::onRemoveTicket,
            onCardNumberChange = viewModel::onCardNumberChange,
            onPayClick = viewModel::onPayClick,
            onActionErrorShown = viewModel::onActionErrorShown,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartContent(
    state: CartUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddTicket: (String) -> Unit,
    onRemoveTicket: (String) -> Unit,
    onCardNumberChange: (String) -> Unit,
    onPayClick: () -> Unit,
    onActionErrorShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            snackbarHostState.showSnackbar(it.toUserMessage())
            onActionErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cart") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadError != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = state.loadError.toUserMessage(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onRetry) { Text("Try again") }
                }
                state.cart.isEmpty -> Text(
                    text = "Your cart is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> CartList(
                    state = state,
                    onAddTicket = onAddTicket,
                    onRemoveTicket = onRemoveTicket,
                    onCardNumberChange = onCardNumberChange,
                    onPayClick = onPayClick,
                )
            }
        }
    }
}

@Composable
private fun CartList(
    state: CartUiState,
    onAddTicket: (String) -> Unit,
    onRemoveTicket: (String) -> Unit,
    onCardNumberChange: (String) -> Unit,
    onPayClick: () -> Unit,
) {
    val entries = remember(state.cart) { state.cart.entries }
    // While paying (or while another request runs) the steppers are locked.
    val locked = state.isPaying

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries, key = { it.ticketId }) { entry ->
            val isBusy = entry.ticketId in state.busyTicketIds
            CartEntryCard(
                entry = entry,
                isBusy = isBusy,
                enabled = !isBusy && !locked,
                onAdd = { onAddTicket(entry.ticketId) },
                onRemove = { onRemoveTicket(entry.ticketId) },
            )
        }
        item {
            PaymentSection(state = state, onCardNumberChange = onCardNumberChange, onPayClick = onPayClick)
        }
    }
}

@Composable
private fun CartEntryCard(
    entry: CartEntry,
    isBusy: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = entry.eventTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.ticketLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${formatPrice(entry.unitPriceCents, entry.currency)} × ${entry.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = formatPrice(entry.subtotalCents, entry.currency),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            QuantityStepper(
                label = entry.ticketLabel,
                quantity = entry.quantity,
                isBusy = isBusy,
                canRemove = enabled,
                canAdd = enabled,
                onRemove = onRemove,
                onAdd = onAdd,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun PaymentSection(
    state: CartUiState,
    onCardNumberChange: (String) -> Unit,
    onPayClick: () -> Unit,
) {
    val currency = state.cart.currency ?: return
    val total = formatPrice(state.cart.totalCents, currency)
    val focusManager = LocalFocusManager.current
    val cardTransformation = remember { CardNumberVisualTransformation() }

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(total, style = MaterialTheme.typography.titleLarge)
        }

        OutlinedTextField(
            value = state.cardNumber,
            onValueChange = onCardNumberChange,
            label = { Text("Card number") },
            singleLine = true,
            enabled = !state.isPaying,
            isError = state.paymentError != null,
            visualTransformation = cardTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            supportingText = {
                val error = state.paymentError
                if (error != null) {
                    Text(error.toUserMessage())
                } else if (BuildConfig.DEBUG) {
                    Text("Demo backend: try 4242 4242 4242 4242")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onPayClick,
            enabled = state.canPay,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.isPaying) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Pay $total")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseConfirmation(purchase: Purchase, onDone: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Your tickets") }) },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Payment successful", style = MaterialTheme.typography.headlineSmall)
                    val count = purchase.items.size
                    val total = purchase.currency?.let { formatPrice(purchase.totalCents, it) }
                    Text(
                        text = listOfNotNull(if (count == 1) "1 ticket" else "$count tickets", total)
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(purchase.items, key = { it.ticket.id }) { item ->
                TicketCard(item)
            }
            item {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun TicketCard(item: PurchasedItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item.eventTitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(item.ticket.label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = formatPrice(item.ticket.priceCents, item.ticket.currency),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Ean13Barcode(item.ticket.barcode)
            Text(
                text = item.ticket.barcode,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
