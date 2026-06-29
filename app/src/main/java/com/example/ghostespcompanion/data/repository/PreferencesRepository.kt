package com.example.ghostespcompanion.data.repository

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")
private val Context.deviceStore: DataStore<Preferences> by preferencesDataStore(name = "saved_device")

private suspend fun readSavedDevice(context: Context): SavedDevice? {
    return try {
        val prefs = context.deviceStore.data.first()
        when (prefs[stringPreferencesKey("kind")]) {
            "usb" -> {
                val vid = prefs[intPreferencesKey("vid")] ?: return null
                val pid = prefs[intPreferencesKey("pid")] ?: return null
                val name = prefs[stringPreferencesKey("name")] ?: return null
                val baud = prefs[intPreferencesKey("baud")] ?: 115200
                SavedDevice.Usb(vid, pid, name, baud)
            }
            "ble" -> {
                val addr = prefs[stringPreferencesKey("address")] ?: return null
                val name = prefs[stringPreferencesKey("name")] ?: addr
                SavedDevice.Ble(addr, name)
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

private suspend fun writeSavedDevice(context: Context, device: SavedDevice?) {
    try {
        context.deviceStore.edit { prefs ->
            if (device == null) {
                prefs.clear()
            } else when (device) {
                is SavedDevice.Usb -> {
                    prefs[stringPreferencesKey("kind")] = "usb"
                    prefs[intPreferencesKey("vid")] = device.vendorId
                    prefs[intPreferencesKey("pid")] = device.productId
                    prefs[stringPreferencesKey("name")] = device.deviceName
                    prefs[intPreferencesKey("baud")] = device.baudRate
                }
                is SavedDevice.Ble -> {
                    prefs[stringPreferencesKey("kind")] = "ble"
                    prefs[stringPreferencesKey("address")] = device.address
                    prefs[stringPreferencesKey("name")] = device.name
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("SavedDevice", "Failed to write saved device: ${e.message}")
    }
}

/**
 * Data class representing app settings
 */
@Immutable
data class AppSettings(
    val darkMode: Boolean = true,
    val hapticFeedback: Boolean = true,
    val autoConnect: Boolean = true,
    val showNotifications: Boolean = true,
    val privacyMode: Boolean = false
)

/**
 * Repository for managing app settings using DataStore
 */
@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val SHOW_NOTIFICATIONS = booleanPreferencesKey("show_notifications")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
    }

    /**
     * Flow of app settings
     */
    val appSettings: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppSettings(
                darkMode = preferences[PreferencesKeys.DARK_MODE] ?: true,
                hapticFeedback = preferences[PreferencesKeys.HAPTIC_FEEDBACK] ?: true,
                autoConnect = preferences[PreferencesKeys.AUTO_CONNECT] ?: true,
                showNotifications = preferences[PreferencesKeys.SHOW_NOTIFICATIONS] ?: true,
                privacyMode = preferences[PreferencesKeys.PRIVACY_MODE] ?: false
            )
        }

    /**
     * Update dark mode setting
     */
    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE] = enabled
        }
    }

    /**
     * Update haptic feedback setting
     */
    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_FEEDBACK] = enabled
        }
    }

    /**
     * Update auto connect setting
     */
    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CONNECT] = enabled
        }
    }

    /**
     * Update show notifications setting
     */
    suspend fun setShowNotifications(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_NOTIFICATIONS] = enabled
        }
    }

    /**
     * Update privacy mode setting
     */
    suspend fun setPrivacyMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRIVACY_MODE] = enabled
        }
    }

    /**
     * Update all settings at once
     */
    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE] = settings.darkMode
            preferences[PreferencesKeys.HAPTIC_FEEDBACK] = settings.hapticFeedback
            preferences[PreferencesKeys.AUTO_CONNECT] = settings.autoConnect
            preferences[PreferencesKeys.SHOW_NOTIFICATIONS] = settings.showNotifications
            preferences[PreferencesKeys.PRIVACY_MODE] = settings.privacyMode
        }
    }

    suspend fun getSavedDevice(): SavedDevice? = readSavedDevice(context)
    suspend fun setSavedDevice(device: SavedDevice?) = writeSavedDevice(context, device)
    suspend fun clearSavedDevice() = writeSavedDevice(context, null)
}

/**
 * Persisted description of the last device we successfully connected to.
 * Used for auto-reconnect on startup.
 */
sealed class SavedDevice {
    data class Usb(
        val vendorId: Int,
        val productId: Int,
        val deviceName: String,
        val baudRate: Int
    ) : SavedDevice()

    data class Ble(val address: String, val name: String) : SavedDevice()
}
