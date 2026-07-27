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
                val portIndex = prefs[intPreferencesKey("port_index")] ?: 0
                SavedDevice.Usb(vid, pid, name, baud, portIndex)
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
                    prefs[intPreferencesKey("port_index")] = device.portIndex
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
    val privacyMode: Boolean = false,
    val dtrCompatibilityMode: Boolean = false
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
        val DTR_COMPATIBILITY_MODE = booleanPreferencesKey("dtr_compatibility_mode")
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
                privacyMode = preferences[PreferencesKeys.PRIVACY_MODE] ?: false,
                dtrCompatibilityMode = preferences[PreferencesKeys.DTR_COMPATIBILITY_MODE] ?: false
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

    suspend fun setDtrCompatibilityMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DTR_COMPATIBILITY_MODE] = enabled
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
            preferences[PreferencesKeys.DTR_COMPATIBILITY_MODE] = settings.dtrCompatibilityMode
        }
    }

    suspend fun getSavedDevice(): SavedDevice? = readSavedDevice(context)
    suspend fun setSavedDevice(device: SavedDevice?) = writeSavedDevice(context, device)
    suspend fun clearSavedDevice() = writeSavedDevice(context, null)

    private object BadUsbConfigKeys {
        val VID = stringPreferencesKey("badusb_vid")
        val PID = stringPreferencesKey("badusb_pid")
        val MFR = stringPreferencesKey("badusb_mfr")
        val PROD = stringPreferencesKey("badusb_prod")
        val LAYOUT = intPreferencesKey("badusb_layout")
    }

    /**
     * Flow of the last-used BadUSB config, so the screen can default to it instead of hardcoded values.
     */
    val lastBadUsbConfig: Flow<BadUsbConfig> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            BadUsbConfig(
                vid = preferences[BadUsbConfigKeys.VID] ?: BadUsbConfig().vid,
                pid = preferences[BadUsbConfigKeys.PID] ?: BadUsbConfig().pid,
                mfr = preferences[BadUsbConfigKeys.MFR] ?: "",
                prod = preferences[BadUsbConfigKeys.PROD] ?: "",
                layout = preferences[BadUsbConfigKeys.LAYOUT] ?: 0
            )
        }

    suspend fun setLastBadUsbConfig(config: BadUsbConfig) {
        context.dataStore.edit { preferences ->
            preferences[BadUsbConfigKeys.VID] = config.vid
            preferences[BadUsbConfigKeys.PID] = config.pid
            preferences[BadUsbConfigKeys.MFR] = config.mfr
            preferences[BadUsbConfigKeys.PROD] = config.prod
            preferences[BadUsbConfigKeys.LAYOUT] = config.layout
        }
    }

    private val LAST_NFC_BACKEND = stringPreferencesKey("last_nfc_backend")

    /**
     * Flow of the last-selected NFC backend, so the NFC screen can default to it instead of resetting to AUTO.
     */
    val lastNfcBackend: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[LAST_NFC_BACKEND] }

    suspend fun setLastNfcBackend(backend: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_NFC_BACKEND] = backend
        }
    }

    private val QUICK_LINKS = stringPreferencesKey("quick_links")

    /**
     * Flow of the user-selected Quick Links destination IDs, ordered, comma-separated.
     * Falls back to the original fixed grid (WIFI, BLE, IR, SD) when nothing is persisted yet.
     */
    val quickLinks: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[QUICK_LINKS] ?: DEFAULT_QUICK_LINKS }

    suspend fun setQuickLinks(destinationIds: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[QUICK_LINKS] = destinationIds.joinToString(",")
        }
    }

    companion object {
        const val DEFAULT_QUICK_LINKS = "WIFI,BLE,IR,SD"
    }
}

/**
 * Last-used BadUSB identity fields, persisted so the Config tab doesn't reset to Apple-mouse defaults every visit.
 */
@Immutable
data class BadUsbConfig(
    val vid: String = "0x05AC",
    val pid: String = "0x0210",
    val mfr: String = "",
    val prod: String = "",
    val layout: Int = 0
)

/**
 * Persisted description of the last device we successfully connected to.
 * Used for auto-reconnect on startup.
 */
sealed class SavedDevice {
    data class Usb(
        val vendorId: Int,
        val productId: Int,
        val deviceName: String,
        val baudRate: Int,
        val portIndex: Int = 0
    ) : SavedDevice()

    data class Ble(val address: String, val name: String) : SavedDevice()
}
