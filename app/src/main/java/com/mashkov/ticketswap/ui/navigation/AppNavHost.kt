package com.mashkov.ticketswap.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mashkov.ticketswap.ui.AppViewModel
import com.mashkov.ticketswap.ui.cart.CartScreen
import com.mashkov.ticketswap.ui.details.EventDetailsScreen
import com.mashkov.ticketswap.ui.events.EventsScreen
import com.mashkov.ticketswap.ui.login.LoginScreen

object Routes {
    const val LOGIN = "login"
    const val EVENTS = "events"
    const val CART = "cart"

    const val EVENT_ID_ARG = "eventId"
    const val EVENT_DETAILS = "event/{$EVENT_ID_ARG}"
    fun eventDetails(eventId: String) = "event/$eventId"
}

@Composable
fun AppNavHost(appViewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val startDestination by appViewModel.startDestination.collectAsStateWithLifecycle()

    val start = startDestination
    if (start == null) {
        // Checking the stored token against the server.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.EVENTS) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.EVENTS) {
            EventsScreen(
                onEventClick = { eventId -> navController.navigate(Routes.eventDetails(eventId)) },
                onOpenCart = { navController.navigate(Routes.CART) },
                onLogout = {
                    appViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.EVENTS) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.EVENT_DETAILS,
            arguments = listOf(navArgument(Routes.EVENT_ID_ARG) { type = NavType.StringType }),
        ) {
            EventDetailsScreen(
                onBack = { navController.popBackStack() },
                onOpenCart = { navController.navigate(Routes.CART) },
            )
        }
        composable(Routes.CART) {
            CartScreen(
                onBack = { navController.popBackStack() },
                // After a purchase: back to the events list, dropping details and cart.
                onDone = { navController.popBackStack(Routes.EVENTS, inclusive = false) },
            )
        }
    }

    // The server rejected our token (e.g. it restarted): wipe the back stack and ask for a login.
    LaunchedEffect(Unit) {
        appViewModel.sessionExpired.collect {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }
}
