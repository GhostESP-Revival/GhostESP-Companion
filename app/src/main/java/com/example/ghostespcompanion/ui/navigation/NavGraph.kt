package com.example.ghostespcompanion.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ghostespcompanion.ui.screens.dashboard.DashboardScreen
import com.example.ghostespcompanion.ui.screens.wifi.WifiScreen
import com.example.ghostespcompanion.ui.screens.wifi.EvilPortalScreen
import com.example.ghostespcompanion.ui.screens.wifi.ApDetailScreen
import com.example.ghostespcompanion.ui.screens.wifi.TrackApScreen
import com.example.ghostespcompanion.ui.screens.wifi.HandshakeCaptureScreen
import com.example.ghostespcompanion.ui.screens.ble.BleScreen
import com.example.ghostespcompanion.ui.screens.ble.FlipperDetectScreen
import com.example.ghostespcompanion.ui.screens.ble.TrackGattScreen
import com.example.ghostespcompanion.ui.screens.ble.TrackFlipperScreen
import com.example.ghostespcompanion.ui.screens.ble.GattDetailScreen
import com.example.ghostespcompanion.ui.screens.nfc.NfcScreen
import com.example.ghostespcompanion.ui.screens.nfc.ChameleonScreen
import com.example.ghostespcompanion.ui.screens.nfc.SavedTagsScreen
import com.example.ghostespcompanion.ui.screens.ir.IrScreen
import com.example.ghostespcompanion.ui.screens.ir.IrLearnScreen
import com.example.ghostespcompanion.ui.screens.ir.IrRemoteDetailScreen
import com.example.ghostespcompanion.ui.screens.more.MoreMenuScreen
import com.example.ghostespcompanion.ui.screens.more.BadUsbScreen
import com.example.ghostespcompanion.ui.screens.more.GpsScreen
import com.example.ghostespcompanion.ui.screens.more.EthernetScreen
import com.example.ghostespcompanion.ui.screens.more.SdManagerScreen
import com.example.ghostespcompanion.ui.screens.more.SettingsScreen
import com.example.ghostespcompanion.ui.screens.terminal.TerminalScreen
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

// Fast fade for bottom nav tab switches — feels nearly instant
private val tabEnter = fadeIn(animationSpec = tween(100, easing = FastOutSlowInEasing))
private val tabExit = fadeOut(animationSpec = tween(100, easing = FastOutSlowInEasing))

// Slide transitions for sub-screen push/pop navigation with spring physics
private val subScreenEnter = slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth },
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
) + fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))

private val subScreenExit = slideOutHorizontally(
    targetOffsetX = { fullWidth -> -fullWidth / 4 },
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
) + fadeOut(animationSpec = tween(150))

private val subScreenPopEnter = slideInHorizontally(
    initialOffsetX = { fullWidth -> -fullWidth / 4 },
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
) + fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))

private val subScreenPopExit = slideOutHorizontally(
    targetOffsetX = { fullWidth -> fullWidth },
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
) + fadeOut(animationSpec = tween(150))

/**
 * Main navigation graph for GhostESP Companion
 *
 * Defines all navigation destinations and their composable screens.
 * Uses bottom navigation for main sections with nested navigation for sub-screens.
 *
 * IMPORTANT: All screens share the same MainViewModel instance by passing it
 * from the activity level. This ensures the serial connection is shared across
 * all screens and not recreated when navigating.
 *
 * @param navController The NavHostController for navigation
 * @param startDestination The initial screen to display
 * @param sharedViewModel The shared MainViewModel instance from the activity
 */
@Composable
fun GhostESPNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Dashboard.route,
    sharedViewModel: MainViewModel
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { tabEnter },
        exitTransition = { tabExit },
        popEnterTransition = { tabEnter },
        popExitTransition = { tabExit }
    ) {
        // Main bottom navigation screens — use default (fast fade) transitions
        composable(route = Screen.Dashboard.route) {
            val onNavigateToWifi = remember { { navController.navigate(Screen.Wifi.route) } }
            val onNavigateToBle = remember { { navController.navigate(Screen.Ble.route) } }
            val onNavigateToIr = remember { { navController.navigate(Screen.Ir.route) } }
            val onNavigateToMore = remember { { navController.navigate(Screen.More.route) } }
            val onNavigateToNfc = remember { { navController.navigate(Screen.Nfc.route) } }
            val onNavigateToGps = remember { { navController.navigate(Screen.Gps.route) } }
            val onNavigateToBadUsb = remember { { navController.navigate(Screen.BadUsb.route) } }
            val onNavigateToSd = remember { { navController.navigate(Screen.SdManager.route) } }
            val onScanWifiAndNavigate = remember { {
                sharedViewModel.scanWifi()
                navController.navigate(Screen.Wifi.route)
            } }
            val onScanBleAndNavigate = remember { { navController.navigate(Screen.FlipperDetect.route) } }
            val onScanNfcAndNavigate = remember { { navController.navigate(Screen.Nfc.route) } }

            DashboardScreen(
                viewModel = sharedViewModel,
                onNavigateToWifi = onNavigateToWifi,
                onNavigateToBle = onNavigateToBle,
                onNavigateToIr = onNavigateToIr,
                onNavigateToMore = onNavigateToMore,
                onNavigateToNfc = onNavigateToNfc,
                onNavigateToGps = onNavigateToGps,
                onNavigateToBadUsb = onNavigateToBadUsb,
                onNavigateToSd = onNavigateToSd,
                onScanWifiAndNavigate = onScanWifiAndNavigate,
                onScanBleAndNavigate = onScanBleAndNavigate,
                onScanNfcAndNavigate = onScanNfcAndNavigate
            )
        }

        composable(route = Screen.Wifi.route) {
            val onNavigateToApDetail = remember { { apIndex: Int -> navController.navigate(Screen.WifiApDetail.createRoute(apIndex)) } }
            val onNavigateToPortal = remember { { navController.navigate(Screen.EvilPortal.route) } }
            val onNavigateToTrack = remember { { apIndex: Int -> navController.navigate(Screen.TrackAp.createRoute(apIndex)) } }

            WifiScreen(
                viewModel = sharedViewModel,
                onNavigateToApDetail = onNavigateToApDetail,
                onNavigateToPortal = onNavigateToPortal,
                onNavigateToTrack = onNavigateToTrack
            )
        }

        composable(route = Screen.Ble.route) {
            val onNavigateToFlipper = remember { { navController.navigate(Screen.FlipperDetect.route) } }
            val onNavigateToGattDetail = remember { { gattIndex: Int -> navController.navigate(Screen.GattDetail.createRoute(gattIndex)) } }
            val onNavigateToTrackGatt = remember { { gattIndex: Int -> navController.navigate(Screen.TrackGatt.createRoute(gattIndex)) } }
            val onNavigateToTrackFlipper = remember { { flipperIndex: Int -> navController.navigate(Screen.TrackFlipper.createRoute(flipperIndex)) } }

            BleScreen(
                viewModel = sharedViewModel,
                onNavigateToFlipper = onNavigateToFlipper,
                onNavigateToGattDetail = onNavigateToGattDetail,
                onNavigateToTrackGatt = onNavigateToTrackGatt,
                onNavigateToTrackFlipper = onNavigateToTrackFlipper
            )
        }

        composable(route = Screen.Ir.route) {
            val onNavigateToLearn = remember { { navController.navigate(Screen.IrLearn.route) } }
            val onNavigateToRemoteDetail = remember { { remoteIndex: Int -> navController.navigate(Screen.IrRemoteDetail.createRoute(remoteIndex)) } }

            IrScreen(
                viewModel = sharedViewModel,
                onNavigateToLearn = onNavigateToLearn,
                onNavigateToRemoteDetail = onNavigateToRemoteDetail
            )
        }

        composable(route = Screen.More.route) {
            val onNavigateToNfc = remember { { navController.navigate(Screen.Nfc.route) } }
            val onNavigateToBadUsb = remember { { navController.navigate(Screen.BadUsb.route) } }
            val onNavigateToGps = remember { { navController.navigate(Screen.Gps.route) } }
            val onNavigateToEthernet = remember { { navController.navigate(Screen.Ethernet.route) } }
            val onNavigateToSd = remember { { navController.navigate(Screen.SdManager.route) } }
            val onNavigateToSettings = remember { { navController.navigate(Screen.Settings.route) } }
            val onNavigateToTerminal = remember { { navController.navigate(Screen.Terminal.route) } }

            MoreMenuScreen(
                viewModel = sharedViewModel,
                onNavigateToNfc = onNavigateToNfc,
                onNavigateToBadUsb = onNavigateToBadUsb,
                onNavigateToGps = onNavigateToGps,
                onNavigateToEthernet = onNavigateToEthernet,
                onNavigateToSd = onNavigateToSd,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToTerminal = onNavigateToTerminal
            )
        }

        // NFC screen (now in More section)
        composable(
            route = Screen.Nfc.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) {
            NfcScreen(
                viewModel = sharedViewModel,
                onNavigateToChameleon = { navController.navigate(Screen.Chameleon.route) },
                onNavigateToSaved = { navController.navigate(Screen.SavedTags.route) },
                onBack = { navController.navigateUp() }
            )
        }

        // WiFi sub-screens — slide transitions
        composable(
            route = Screen.EvilPortal.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) {
            EvilPortalScreen(viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        composable(
            route = Screen.WifiApDetail.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) { backStackEntry ->
            val apIndex = backStackEntry.arguments?.getString("apIndex")?.toIntOrNull() ?: 0
            ApDetailScreen(
                apIndex = apIndex,
                viewModel = sharedViewModel,
                onNavigateToTrack = { idx -> navController.navigate(Screen.TrackAp.createRoute(idx)) },
                onNavigateToHandshake = { idx -> navController.navigate(Screen.HandshakeCapture.createRoute(idx)) },
                onBack = { navController.navigateUp() }
            )
        }

        composable(
            route = Screen.TrackAp.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) { backStackEntry ->
            val apIndex = backStackEntry.arguments?.getString("apIndex")?.toIntOrNull() ?: 0
            TrackApScreen(apIndex = apIndex, viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        composable(
            route = Screen.HandshakeCapture.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) { backStackEntry ->
            val apIndex = backStackEntry.arguments?.getString("apIndex")?.toIntOrNull() ?: 0
            HandshakeCaptureScreen(apIndex = apIndex, viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        // BLE sub-screens
        composable(
            route = Screen.FlipperDetect.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) {
            FlipperDetectScreen(
                viewModel = sharedViewModel,
                onNavigateToTrackFlipper = { flipperIndex ->
                    navController.navigate(Screen.TrackFlipper.createRoute(flipperIndex))
                },
                onBack = { navController.navigateUp() }
            )
        }

        composable(
            route = Screen.TrackGatt.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) { backStackEntry ->
            val gattIndex = backStackEntry.arguments?.getString("gattIndex")?.toIntOrNull() ?: 0
            TrackGattScreen(gattIndex = gattIndex, viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        composable(
            route = Screen.TrackFlipper.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) { backStackEntry ->
            val flipperIndex = backStackEntry.arguments?.getString("flipperIndex")?.toIntOrNull() ?: 0
            TrackFlipperScreen(flipperIndex = flipperIndex, viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        composable(
            route = Screen.GattDetail.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) { backStackEntry ->
            val gattIndex = backStackEntry.arguments?.getString("gattIndex")?.toIntOrNull() ?: 0
            GattDetailScreen(
                gattIndex = gattIndex,
                viewModel = sharedViewModel,
                onNavigateToTrack = { idx -> navController.navigate(Screen.TrackGatt.createRoute(idx)) },
                onBack = { navController.navigateUp() }
            )
        }

        // NFC sub-screens
        composable(
            route = Screen.Chameleon.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) {
            ChameleonScreen(viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        composable(
            route = Screen.SavedTags.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) {
            SavedTagsScreen(viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        // IR sub-screens
        composable(
            route = Screen.IrLearn.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) {
            IrLearnScreen(viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        composable(
            route = Screen.IrRemoteDetail.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) { backStackEntry ->
            val remoteIndex = backStackEntry.arguments?.getString("remoteIndex")?.toIntOrNull() ?: 0
            IrRemoteDetailScreen(remoteIndex = remoteIndex, viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        // More menu sub-screens
        composable(
            route = Screen.BadUsb.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) {
            BadUsbScreen(viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        composable(
            route = Screen.Gps.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) {
            GpsScreen(viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        composable(
            route = Screen.Ethernet.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) {
            EthernetScreen(viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        composable(
            route = Screen.SdManager.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) {
            SdManagerScreen(viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        composable(
            route = Screen.Settings.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) {
            SettingsScreen(viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }

        composable(
            route = Screen.Terminal.route,
            enterTransition = { subScreenEnter },
            exitTransition = { subScreenExit },
            popEnterTransition = { subScreenPopEnter },
            popExitTransition = { subScreenPopExit }
        ) {
            TerminalScreen(viewModel = sharedViewModel, onBack = { navController.navigateUp() })
        }
    }
}
