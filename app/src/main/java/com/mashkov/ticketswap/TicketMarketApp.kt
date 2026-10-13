package com.mashkov.ticketswap

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras

class TicketMarketApp : Application() {
    // Manual DI: a single container, created lazily, lives as long as the process.
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Lets ViewModel factories reach the container: `initializer { MyViewModel(container.xxx) }`. */
val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as TicketMarketApp).container
