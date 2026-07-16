package com.example.ghostespcompanion.ui.navigation

import androidx.annotation.StringRes
import com.example.ghostespcompanion.R

/**
 * Navigation destinations for GhostESP Companion
 * 
 * Defines all screens and their routes for type-safe navigation.
 */
sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int = 0,
    val icon: String
) {
    // Main bottom navigation screens
    data object Dashboard : Screen(
        route = "dashboard",
        titleRes = R.string.title_dashboard,
        icon = "dashboard"
    )
    
    data object Wifi : Screen(
        route = "wifi",
        titleRes = R.string.title_wifi,
        icon = "wifi"
    )
    
    data object Ble : Screen(
        route = "ble",
        titleRes = R.string.title_ble,
        icon = "bluetooth"
    )
    
    data object Ir : Screen(
        route = "ir",
        titleRes = R.string.title_ir,
        icon = "remote"
    )
    
    data object More : Screen(
        route = "more",
        titleRes = R.string.title_more,
        icon = "more_horiz"
    )
    
    // NFC - now in More section
    data object Nfc : Screen(
        route = "more/nfc",
        titleRes = R.string.title_nfc,
        icon = "nfc"
    )
    
    // WiFi sub-screens
    data object WifiScan : Screen(
        route = "wifi/scan",
        titleRes = R.string.action_scan_networks,
        icon = "wifi"
    )
    
    data object WifiApDetail : Screen(
        route = "wifi/ap/{apIndex}",
        titleRes = R.string.title_network_details,
        icon = "wifi"
    ) {
        fun createRoute(apIndex: Int) = "wifi/ap/$apIndex"
    }
    
    data object TrackAp : Screen(
        route = "wifi/track/{apIndex}",
        titleRes = R.string.title_track_ap,
        icon = "location_on"
    ) {
        fun createRoute(apIndex: Int) = "wifi/track/$apIndex"
    }
    
    data object HandshakeCapture : Screen(
        route = "wifi/handshake/{apIndex}",
        titleRes = R.string.title_handshake_capture,
        icon = "key"
    ) {
        fun createRoute(apIndex: Int) = "wifi/handshake/$apIndex"
    }
    
    data object EvilPortal : Screen(
        route = "wifi/portal",
        titleRes = R.string.title_evil_portal,
        icon = "router"
    )
    
    // BLE sub-screens
    data object BleScan : Screen(
        route = "ble/scan",
        titleRes = R.string.title_scan_devices,
        icon = "bluetooth"
    )
    
    data object FlipperDetect : Screen(
        route = "ble/flipper",
        titleRes = R.string.title_flipper_detection,
        icon = "search"
    )
    
    data object TrackGatt : Screen(
        route = "ble/track/{gattIndex}",
        titleRes = R.string.title_track_gatt_device,
        icon = "location_on"
    ) {
        fun createRoute(gattIndex: Int) = "ble/track/$gattIndex"
    }

    data object TrackFlipper : Screen(
        route = "ble/trackflipper/{flipperIndex}",
        titleRes = R.string.action_track_flipper,
        icon = "location_on"
    ) {
        fun createRoute(flipperIndex: Int) = "ble/trackflipper/$flipperIndex"
    }
    
    data object GattDetail : Screen(
        route = "ble/gatt/{gattIndex}",
        titleRes = R.string.title_gatt_services,
        icon = "settings_bluetooth"
    ) {
        fun createRoute(gattIndex: Int) = "ble/gatt/$gattIndex"
    }
    
    // NFC sub-screens
    data object NfcScan : Screen(
        route = "nfc/scan",
        titleRes = R.string.title_scan_tag,
        icon = "nfc"
    )
    
    data object Chameleon : Screen(
        route = "nfc/chameleon",
        titleRes = R.string.title_chameleon_ultra,
        icon = "credit_card"
    )
    
    data object SavedTags : Screen(
        route = "nfc/saved",
        titleRes = R.string.title_saved_tags,
        icon = "folder"
    )
    
    // IR sub-screens
    data object IrRemote : Screen(
        route = "ir/remote",
        titleRes = R.string.title_remotes,
        icon = "remote"
    )
    
    data object IrRemoteDetail : Screen(
        route = "ir/remote/{remoteIndex}",
        titleRes = R.string.title_remote_details,
        icon = "remote"
    ) {
        fun createRoute(remoteIndex: Int) = "ir/remote/$remoteIndex"
    }
    
    data object IrLearn : Screen(
        route = "ir/learn",
        titleRes = R.string.title_learn_signal,
        icon = "settings_remote"
    )
    
    // More menu screens
    data object BadUsb : Screen(
        route = "more/badusb",
        titleRes = R.string.title_bad_usb,
        icon = "usb"
    )
    
    data object Gps : Screen(
        route = "more/gps",
        titleRes = R.string.title_gps_wardrive,
        icon = "gps_fixed"
    )
    
    data object Ethernet : Screen(
        route = "more/ethernet",
        titleRes = R.string.title_ethernet,
        icon = "lan"
    )
    
    data object SdManager : Screen(
        route = "more/sd",
        titleRes = R.string.title_sd_manager,
        icon = "sd_card"
    )
    
    data object Settings : Screen(
        route = "more/settings",
        titleRes = R.string.title_settings,
        icon = "settings"
    )
    
    data object Terminal : Screen(
        route = "more/terminal",
        titleRes = R.string.title_terminal,
        icon = "terminal"
    )
}

/**
 * Bottom navigation items
 */
val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Wifi,
    Screen.Ble,
    Screen.Ir,
    Screen.More
)
