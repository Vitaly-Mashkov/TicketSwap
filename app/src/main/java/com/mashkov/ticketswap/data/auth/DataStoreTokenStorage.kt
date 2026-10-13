package com.mashkov.ticketswap.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/**
 * Stores the token in Preferences DataStore. NOTE: not encrypted. For production I would
 * encrypt it with a key from the Android Keystore; the [TokenStorage] interface makes that a local change.
 */
class DataStoreTokenStorage(private val context: Context) : TokenStorage {

    private val tokenKey = stringPreferencesKey("access_token")

    override val token: Flow<String?> = context.sessionDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[tokenKey] }

    override suspend fun save(token: String) {
        context.sessionDataStore.edit { it[tokenKey] = token }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.remove(tokenKey) }
    }
}
