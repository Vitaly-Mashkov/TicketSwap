package com.mashkov.ticketswap.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mashkov.ticketswap.R
import com.mashkov.ticketswap.data.events.EventSummary
import com.mashkov.ticketswap.ui.common.formatEventDate
import com.mashkov.ticketswap.ui.common.formatPrice
import com.mashkov.ticketswap.ui.common.toUserMessage

@Composable
fun EventsScreen(
    onEventClick: (eventId: String) -> Unit,
    onOpenCart: () -> Unit,
    onLogout: () -> Unit,
    viewModel: EventsViewModel = viewModel(factory = EventsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EventsScreenContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onRetry = viewModel::onRetry,
        onEventClick = onEventClick,
        onOpenCart = onOpenCart,
        onLogout = onLogout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventsScreenContent(
    state: EventsUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onEventClick: (String) -> Unit,
    onOpenCart: () -> Unit,
    onLogout: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Events") },
                actions = {
                    TextButton(onClick = onOpenCart) {
                        Text(if (state.cartCount > 0) "Cart (${state.cartCount})" else "Cart")
                    }
                    TextButton(onClick = onLogout) { Text("Log out") }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier
                   .fillMaxSize()
                   .padding(innerPadding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search events") },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_search),
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Box(Modifier
                    .weight(1f)
                    .fillMaxWidth()) {
                when (val content = state.content) {
                    EventsContent.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is EventsContent.Error -> ErrorState(
                        message = content.error.toUserMessage(),
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is EventsContent.Success -> if (content.events.isEmpty()) {
                        Text(
                            text = "No events found",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        EventList(content.events, onEventClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventList(events: List<EventSummary>, onEventClick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(events, key = { it.id }) { event ->
            EventCard(event, onClick = { onEventClick(event.id) })
        }
    }
}

@Composable
private fun EventCard(event: EventSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        // Past events stay visible (the catalog is mostly dated in the past) but are dimmed.
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (event.isPast) 0.6f else 1f),
    ) {
        AsyncImage(
            model = event.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
        Column(Modifier.padding(16.dp)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${event.venueName} • ${formatEventDate(event.startsAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = priceLabel(event),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun priceLabel(event: EventSummary): String = when {
    event.isPast -> "Past event"
    event.fromPriceCents == null || event.currency == null -> "Sold out"
    else -> "From ${formatPrice(event.fromPriceCents, event.currency)}"
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text("Try again") }
    }
}
