package com.mashkov.ticketswap.data.auth

import com.apollographql.apollo.ApolloClient
import com.mashkov.ticketswap.core.AppError
import com.mashkov.ticketswap.core.AppResult
import com.mashkov.ticketswap.core.executeCatching
import com.mashkov.ticketswap.core.serverError
import com.mashkov.ticketswap.graphql.AuthenticatedUserMutation
import com.mashkov.ticketswap.graphql.LoginMutation
import com.mashkov.ticketswap.graphql.LogoutMutation
import kotlinx.coroutines.flow.first

class AuthRepository(
    private val apollo: ApolloClient,
    private val tokenStorage: TokenStorage,
) {

    suspend fun login(username: String, password: String): AppResult<Unit> {
        val result = apollo.mutation(LoginMutation(username, password)).executeCatching()
        if (result is AppResult.Failure) return result

        val payload = (result as AppResult.Success).value.login
        val errorText = payload.error?.text
        val token = payload.accessToken
        return when {
            errorText != null -> AppResult.Failure(serverError(errorText))
            token == null -> AppResult.Failure(AppError.Unknown)
            else -> {
                tokenStorage.save(token)
                AppResult.Success(Unit)
            }
        }
    }

    /**
     * Called at startup. Sessions live in the server's memory, so a stored token can go stale
     * (e.g. after a backend restart). We drop the token ONLY when the server says it is invalid;
     * on any other failure (offline, simulated error) we keep it and let the next screen retry.
     */
    suspend fun isSessionValid(): Boolean {
        val token = tokenStorage.token.first() ?: return false
        val error = when (val result = apollo.mutation(AuthenticatedUserMutation(token)).executeCatching()) {
            is AppResult.Success -> result.value.authenticatedUser.error?.let { serverError(it.text) }
            is AppResult.Failure -> result.error
        }
        if (error == AppError.Unauthorized) {
            tokenStorage.clear()
            return false
        }
        return true
    }

    /** Local logout always succeeds, even if the server call fails. */
    suspend fun logout() {
        tokenStorage.token.first()?.let { apollo.mutation(LogoutMutation(it)).executeCatching() }
        tokenStorage.clear()
    }
}
