package com.mashkov.ticketswap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mashkov.ticketswap.ui.navigation.AppNavHost
import com.mashkov.ticketswap.ui.theme.TicketMarketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TicketMarketTheme {
                AppNavHost()
            }
        }
    }
}