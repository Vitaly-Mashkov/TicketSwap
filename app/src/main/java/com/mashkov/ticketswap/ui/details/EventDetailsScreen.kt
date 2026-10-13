package com.mashkov.ticketswap.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mashkov.ticketswap.R
import com.mashkov.ticketswap.data.cart.Cart
import com.mashkov.ticketswap.data.events.EventDetails
import com.mashkov.ticketswap.data.events.TicketType
import com.mashkov.ticketswap.ui.common.formatEventDate
import com.mashkov.ticketswap.ui.common.formatPrice
import com.mashkov.ticketswap.ui.common.QuantityStepper
import com.mashkov.ticketswap.ui.common.toUserMessage

@Composable
fun EventDetailsScreen(
    onBack: () -> Unit,
    onOpenCart: () -> Unit,
    viewModel: EventDetailsViewModel = viewModel(factory = EventDetailsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EventDetailsContent(
        state = state,
        onBack = onBack,
        onOpenCart = onOpenCart,
        onRetry = viewModel::onRetry,
        onAddTicket = viewModel::onAddTicket,
        onRemoveTicket = viewModel::onRemoveTicket,
        onCartErrorShown = viewModel::onCartErrorShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailsContent(
    state: EventDetailsUiState,
    onBack: () -> Unit,
    onOpenCart: () -> Unit,
    onRetry: () -> Unit,
    onAddTicket: (String) -> Unit,
    onRemoveTicket: (String) -> Unit,
    onCartErrorShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.cartError) {
        state.cartError?.let {
            snackbarHostState.showSnackbar(it.toUserMessage())
            onCartErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.content is EventDetailsContent.Success && !state.cart.isEmpty) {
                CartBar(cart = state.cart, onOpenCart = onOpenCart)
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (val content = state.content) {
                EventDetailsContent.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is EventDetailsContent.Error -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = content.error.toUserMessage(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onRetry) { Text("Try again") }
                }
                is EventDetailsContent.Success -> EventBody(
                    event = content.event,
                    cart = state.cart,
                    busyTicketIds = state.busyTicketIds,
                    onAddTicket = onAddTicket,
                    onRemoveTicket = onRemoveTicket,
                )
            }
        }
    }
}

@Composable
private fun EventBody(
    event: EventDetails,
    cart: Cart,
    busyTicketIds: Set<String>,
    onAddTicket: (String) -> Unit,
    onRemoveTicket: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            AsyncImage(
                model = event.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Text(event.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${event.venueName} • ${formatEventDate(event.startsAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                event.description?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(24.dp))
                Text("Tickets", style = MaterialTheme.typography.titleLarge)
                if (event.isPast) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "This event has passed. Tickets are no longer available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(event.tickets, key = { it.id }) { ticket ->
            val inCart = cart.quantityOf(ticket.id)
            val isBusy = ticket.id in busyTicketIds
            TicketRow(
                ticket = ticket,
                quantityInCart = inCart,
                isBusy = isBusy,
                isPast = event.isPast,
                onAdd = { onAddTicket(ticket.id) },
                onRemove = { onRemoveTicket(ticket.id) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TicketRow(
    ticket: TicketType,
    quantityInCart: Int,
    isBusy: Boolean,
    isPast: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().alpha(if (isPast) 0.6f else 1f)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ticket.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatPrice(ticket.priceCents, ticket.currency),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!isPast) {
                    Text(
                        text = availabilityLabel(ticket.quantityAvailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            QuantityStepper(
                label = ticket.label,
                quantity = quantityInCart,
                isBusy = isBusy,
                canRemove = !isPast && !isBusy && quantityInCart > 0,
                canAdd = !isPast && !isBusy && ticket.quantityAvailable > 0,
                onRemove = onRemove,
                onAdd = onAdd,
            )
        }
    }
}

private fun availabilityLabel(available: Int): String = when {
    available <= 0 -> "No more available"
    available <= 5 -> "Only $available left"
    else -> "$available available"
}

@Composable
private fun CartBar(cart: Cart, onOpenCart: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val count = cart.totalQuantity
                Text(
                    text = if (count == 1) "1 ticket" else "$count tickets",
                    style = MaterialTheme.typography.bodyMedium,
                )
                cart.currency?.let {
                    Text(formatPrice(cart.totalCents, it), style = MaterialTheme.typography.titleMedium)
                }
            }
            Button(onClick = onOpenCart) { Text("View cart") }
        }
    }
}
