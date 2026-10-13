package com.mashkov.ticketswap.core

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.api.Operation

/**
 * Runs the call and folds the three ways it can fail into [AppResult]:
 * fetch errors (-> Network), GraphQL errors (-> Server) and missing data (-> Unknown).
 *
 * Business errors that the backend puts INSIDE the payload (`error { text }`) are not
 * handled here, because they differ per operation; repositories map them with [serverError].
 */
suspend fun <D : Operation.Data> ApolloCall<D>.executeCatching(): AppResult<D> {
    val response = execute()
    val data = response.data
    return when {
        data != null -> AppResult.Success(data)
        response.exception != null -> AppResult.Failure(AppError.Network)
        else -> {
            val message = response.errors?.firstOrNull()?.message
            AppResult.Failure(if (message != null) AppError.Server(message) else AppError.Unknown)
        }
    }
}
