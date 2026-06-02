package com.yaku.geo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AuthRepository(private val context: Context) {
    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    private val NICKNAME_KEY = stringPreferencesKey("nickname")
    private val LAST_SYNC_KEY = longPreferencesKey("last_sync_timestamp")

    val authToken: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val nickname: Flow<String?> = context.dataStore.data.map { it[NICKNAME_KEY] }
    val lastSync: Flow<Long?> = context.dataStore.data.map { it[LAST_SYNC_KEY] }

    suspend fun saveAuth(token: String, nickname: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
            preferences[NICKNAME_KEY] = nickname
        }
    }

    suspend fun updateSyncTimestamp() {
        context.dataStore.edit { preferences ->
            preferences[LAST_SYNC_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(NICKNAME_KEY)
        }
    }
}
