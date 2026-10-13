package com.mashkov.ticketswap

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.mashkov.ticketswap.data.auth.AuthRepository
import com.mashkov.ticketswap.data.auth.DataStoreTokenStorage
import com.mashkov.ticketswap.data.auth.SessionManager
import com.mashkov.ticketswap.data.auth.TokenStorage
import com.mashkov.ticketswap.data.cart.CartRepository
import com.mashkov.ticketswap.data.events.EventsRepository

/**
 * Hand-written dependency container (no Hilt: the app is small enough that
 * a DI framework would cost more than it saves).
 */
class AppContainer(context: Context) {

    val tokenStorage: TokenStorage = DataStoreTokenStorage(context.applicationContext)
    val sessionManager = SessionManager(tokenStorage)

    val apolloClient: ApolloClient = ApolloClient.Builder()
        .serverUrl(BuildConfig.GRAPHQL_URL)
        .build()

    val authRepository = AuthRepository(apolloClient, tokenStorage)
    val eventsRepository = EventsRepository(apolloClient)
    val cartRepository = CartRepository(apolloClient, tokenStorage, sessionManager)
}
