package com.example.ghostespcompanion.ui.screens.wifi

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostCommand
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.ui.utils.censorMac
import com.example.ghostespcompanion.ui.utils.censorSsid
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

/**
 * WiFi Screen - Minimalist Neo-Brutalist Design
 * 
 * Clean white accents on deep black background.
 * Professional and modern aesthetic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(
    onNavigateToApDetail: (Int) -> Unit,
    onNavigateToPortal: () -> Unit,
    onNavigateToTrack: (Int) -> Unit,
    viewModel: MainViewModel
) {
    var isScanningStations by remember { mutableStateOf(false) }
    var showApDetailSheet by remember { mutableStateOf(false) }
    var showAttackOptionsSheet by remember { mutableStateOf(false) }
    var selectedAp by remember { mutableStateOf<AccessPointPreview?>(null) }
    var showDeviceInfoDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    val availableDevices by viewModel.availableUsbDevices.collectAsState()
    val usbPortCounts by viewModel.usbPortCounts.collectAsState()
    
    // Attack states
    var activeDeauthIndex by remember { mutableStateOf<Int?>(null) }
    var isBeaconSpamming by remember { mutableStateOf(false) }
    var isRickRolling by remember { mutableStateOf(false) }
    var isKarmaRunning by remember { mutableStateOf(false) }
    var showPacketCaptureDialog by remember { mutableStateOf(false) }
    var activePacketCaptureMode by remember { mutableStateOf<GhostCommand.CaptureMode?>(null) }
    var showNotConnectedSnackbar by remember { mutableStateOf(false) }
    
    // Station detail sheet states
    var selectedStation by remember { mutableStateOf<GhostResponse.Station?>(null) }
    var showStationDetailSheet by remember { mutableStateOf(false) }
    
    // Collect state from ViewModel
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionTransport by viewModel.connectionTransport.collectAsState()
    val availableBleDevices by viewModel.availableBleDevices.collectAsState()
    val isBleScanning by viewModel.isBleScanning.collectAsState()
    val accessPoints by viewModel.accessPoints.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val chipInfoRaw by viewModel.chipInfoRaw.collectAsState()
    val chipInfoParseStatus by viewModel.chipInfoParseStatus.collectAsState()
    val chipInfoDebugLog by viewModel.chipInfoDebugLog.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val isScanning by viewModel.isWifiScanning.collectAsState()
    val wifiConnection by viewModel.wifiConnection.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    val privacyMode = appSettings.privacyMode
    val context = LocalContext.current
    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            viewModel.startBleBridgeScan()
        }
    }

    // Stop all WiFi operations when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopWifiScanAndReset()
            if (activeDeauthIndex != null || isBeaconSpamming || isRickRolling || isKarmaRunning) {
                viewModel.stopAll()
            }
            if (activePacketCaptureMode != null) {
                viewModel.stopPacketCapture()
            }
        }
    }

    val scanBleBridges: () -> Unit = {
        if (viewModel.isBluetoothSupported() && viewModel.isBluetoothEnabled()) {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            val allGranted = permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                viewModel.startBleBridgeScan()
            } else {
                blePermissionLauncher.launch(permissions)
            }
        }
    }
    
    // Find the SSID of the connected AP (match by SSID since that's what firmware reports)
    val connectedSsid = wifiConnection?.takeIf { it.isConnected }?.ssid
    
    // Use real data from ViewModel or empty list
    // Wrap in remember to avoid recomputing on every recomposition
    val displayAccessPoints = remember(accessPoints) {
        if (accessPoints.isNotEmpty()) {
            accessPoints.map { ap ->
                AccessPointPreview(
                    index = ap.index,
                    ssid = ap.ssid,
                    bssid = ap.bssid,
                    rssi = ap.rssi,
                    channel = ap.channel,
                    security = ap.security,
                    vendor = ap.vendor
                )
            }
        } else {
            emptyList()
        }
    }
    
    // Handle scan state
    // Pre-fetch chip info when connected so the info button has data immediately
    LaunchedEffect(isConnected, isScanning) {
        if (isConnected && !isScanning) {
            viewModel.getChipInfo()
        }
    }
    
    MainScreen(
        title = "WiFi",
        actions = {
            IconButton(onClick = onNavigateToPortal) {
                Icon(
                    painter = painterResource(R.drawable.ic_evil_portal),
                    contentDescription = "Evil Portal",
                    tint = primaryColor()
                )
            }
            IconButton(onClick = {
                if (isConnected) {
                    viewModel.getChipInfo()
                    showDeviceInfoDialog = true
                } else {
                    showNotConnectedSnackbar = true
                }
            }) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Device Info",
                    tint = primaryColor()
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Connection Status Banner - Minimal style
            WifiStatusBanner(
                isConnected = isConnected,
                connectionState = connectionState,
                connectionTransport = connectionTransport,
                deviceName = "GhostESP",
                onConnect = {
                    if (connectionState == SerialManager.ConnectionState.ERROR) {
                        viewModel.forceDisconnect()
                    }
                    viewModel.refreshAvailableDevices()
                    viewModel.refreshAllUsbDevices()
                    showDeviceDialog = true
                }
            )
            
            // Device Selection Dialog
            if (showDeviceDialog) {
                val allUsbDevices by viewModel.allUsbDevices.collectAsState()
                val usbDebugLog by viewModel.usbDebugLog.collectAsState()
                ConnectionSelectionDialog(
                    usbDevices = availableDevices,
                    usbPortCounts = usbPortCounts,
                    bleDevices = availableBleDevices,
                    allUsbDevices = allUsbDevices,
                    usbDebugLog = usbDebugLog,
                    bluetoothEnabled = viewModel.isBluetoothEnabled(),
                    bluetoothSupported = viewModel.isBluetoothSupported(),
                    isBleScanning = isBleScanning,
                    onUsbSelected = { device, baud, portIndex ->
                        showDeviceDialog = false
                        viewModel.connectWithBaud(device, baud, portIndex)
                    },
                    onBleSelected = { device ->
                        showDeviceDialog = false
                        viewModel.stopBleBridgeScan()
                        viewModel.connectBle(device)
                    },
                    onRefreshUsb = {
                        viewModel.refreshAvailableDevices()
                        viewModel.refreshAllUsbDevices()
                    },
                    onRefreshBle = { scanBleBridges() },
                    onDismiss = {
                        viewModel.stopBleBridgeScan()
                        showDeviceDialog = false
                    }
                )
            }
            
            // Scan Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Scan Networks Button
                BrutalistButton(
                    text = if (isScanning) "Stop Scan" else "Scan Networks",
                    onClick = { 
                        if (isConnected) {
                            if (isScanning) {
                                viewModel.stopWifiScanAndReset()
                            } else {
                                viewModel.scanWifi()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    containerColor = if (isScanning) warningColor() else MaterialTheme.colorScheme.primary,
                    textColor = MaterialTheme.colorScheme.onPrimary,
                    enabled = isConnected,
                    isLoading = false,
                    leadingIcon = {
                        Icon(
                            if (isScanning) Icons.Default.Stop else Icons.Default.Search, 
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                
                // Scan Stations Button
                BrutalistButton(
                    text = if (isScanningStations) "Stop Scan" else "Scan Stations",
                    onClick = { 
                        if (isConnected) {
                            if (isScanningStations) {
                                viewModel.stopAll()
                                isScanningStations = false
                            } else {
                                viewModel.clearStations()
                                viewModel.scanSta()
                                isScanningStations = true
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    containerColor = if (isScanningStations) warningColor() else MaterialTheme.colorScheme.secondary,
                    textColor = MaterialTheme.colorScheme.onSecondary,
                    enabled = isConnected,
                    isLoading = false,
                    leadingIcon = {
                        Icon(
                            if (isScanningStations) Icons.Default.Stop else Icons.Default.Devices, 
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            
            // Quick action buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isConnected) {
                    // Stop All button
                    QuickActionChip(
                        text = "Stop All",
                        onClick = {
                            viewModel.stopAll()
                            viewModel.stopWifiScanAndReset()
                            isScanningStations = false
                            activeDeauthIndex = null
                            isBeaconSpamming = false
                            isRickRolling = false
                            isKarmaRunning = false
                            activePacketCaptureMode = null
                        },
                        isSelected = false,
                        selectedColor = errorColor()
                    )
                    QuickActionChip(
                        text = "Packet Capture",
                        onClick = { showPacketCaptureDialog = true },
                        isSelected = activePacketCaptureMode != null,
                        selectedColor = primaryColor()
                    )
                    if (activePacketCaptureMode != null) {
                        QuickActionChip(
                            text = "Stop Capture",
                            onClick = {
                                viewModel.stopPacketCapture()
                                activePacketCaptureMode = null
                            },
                            isSelected = true,
                            selectedColor = errorColor()
                        )
                    }
                }
            }
            
            // Active attack indicator
            if (activeDeauthIndex != null || isBeaconSpamming || isRickRolling || isKarmaRunning || isScanningStations) {
                ActiveAttackBanner(
                    deauthIndex = activeDeauthIndex,
                    isBeaconSpamming = isBeaconSpamming,
                    isRickRolling = isRickRolling,
                    isKarmaRunning = isKarmaRunning,
                    isScanningStations = isScanningStations,
                    onStopAll = {
                        viewModel.stopAll()
                        activeDeauthIndex = null
                        isBeaconSpamming = false
                        isRickRolling = false
                        isKarmaRunning = false
                        activePacketCaptureMode = null
                        isScanningStations = false
                    }
                )
            }
            
            // Status message
            statusMessage?.let { message ->
                if (message.isNotEmpty()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            
            // Results count
            if (displayAccessPoints.isNotEmpty()) {
                BrutalistSectionHeader(
                    title = "Found ${displayAccessPoints.size} networks",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    accentColor = primaryColor()
                )
            } else if (isConnected && !isScanning) {
                BrutalistSectionHeader(
                    title = "No networks found - Press Scan",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    accentColor = OnSurfaceVariantDark
                )
            }
            
            // Show skeleton loading while scanning
            if (isScanning && displayAccessPoints.isEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(5) {
                        SkeletonWifiApCard()
                    }
                }
            } else {
                // Group stations by their associated AP BSSID
                val stationsByApBssid = remember(stations) {
                    stations.groupBy { it.apBssid }
                }
                
                // Pre-compute unassociated stations with memoization to avoid O(n*m) filter on every recomposition
                val unassociatedStations = remember(stations, displayAccessPoints) {
                    val apBssids = displayAccessPoints.map { it.bssid }.toSet()
                    stations.filter { station ->
                        station.apBssid == null || station.apBssid !in apBssids
                    }
                }
                
                // Combined scrollable list for APs with their associated stations
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // AP List with stations as sub-items
                    itemsIndexed(
                        items = displayAccessPoints,
                        key = { _, ap -> ap.index },
                        contentType = { _, _ -> "access_point" }
                    ) { index, ap ->
                        // Only stagger first few items for initial load, rest appear instantly
                        StaggeredAnimatedItem(
                            index = index,
                            staggerDelayMs = 20
                        ) {
                            val associatedStations = stationsByApBssid[ap.bssid] ?: emptyList()
                            val hasStations = associatedStations.isNotEmpty()
                            
                            WifiApCardWithStations(
                                accessPoint = ap,
                                isAttacking = activeDeauthIndex == ap.index,
                                privacyMode = privacyMode,
                                hasStations = hasStations,
                                associatedStationsCount = associatedStations.size,
                                associatedStations = associatedStations,
                                isCurrentConnection = connectedSsid != null && ap.ssid == connectedSsid,
                                connectedIp = if (connectedSsid != null && ap.ssid == connectedSsid) wifiConnection?.ip else null,
                                onClick = {
                                    selectedAp = ap
                                    viewModel.selectAp(ap.index.toString())
                                    showApDetailSheet = true
                                },
                                onStationClick = { station ->
                                    selectedStation = station
                                    viewModel.selectStation(station.index.toString())
                                    showStationDetailSheet = true
                                }
                            )
                        }
                    }
                    
                    // Show unassociated stations (stations without a matching AP in the list)
                    
                    if (unassociatedStations.isNotEmpty()) {
                        item {
                            BrutalistSectionHeader(
                                title = "Unassociated Stations (${unassociatedStations.size})",
                                modifier = Modifier.padding(vertical = 4.dp),
                                accentColor = warningColor()
                            )
                        }
                        
                        itemsIndexed(
                            items = unassociatedStations,
                            key = { _, station -> "unassoc_${station.index}" },
                            contentType = { _, _ -> "station" }
                        ) { index, station ->
                            StaggeredAnimatedItem(
                                index = index + displayAccessPoints.size,
                                staggerDelayMs = 20
                            ) {
                                StationResultCard(
                                    station = station,
                                    privacyMode = privacyMode,
                                    onClick = {
                                        selectedStation = station
                                        viewModel.selectStation(station.index.toString())
                                        showStationDetailSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // AP Detail Bottom Sheet
    if (showApDetailSheet && selectedAp != null) {
        selectedAp?.let { ap ->
            ApDetailSheet(
                accessPoint = ap,
                isAttacking = activeDeauthIndex == ap.index,
                privacyMode = privacyMode,
                isCurrentConnection = connectedSsid != null && ap.ssid == connectedSsid,
                connectedIp = if (connectedSsid != null && ap.ssid == connectedSsid) wifiConnection?.ip else null,
                onDismiss = { showApDetailSheet = false },
                onSelect = {
                    showApDetailSheet = false
                    onNavigateToApDetail(ap.index)
                },
                onShowAttackOptions = {
                    showApDetailSheet = false
                    showAttackOptionsSheet = true
                },
                onDeauth = {
                    if (activeDeauthIndex == ap.index) {
                        viewModel.stopDeauth()
                        activeDeauthIndex = null
                    } else {
                        // Stop any existing deauth first
                        if (activeDeauthIndex != null) {
                            viewModel.stopDeauth()
                        }
                        viewModel.selectAp(ap.index.toString())
                        viewModel.startDeauth()
                        activeDeauthIndex = ap.index
                    }
                },
                onTrack = {
                    viewModel.trackAp()
                    showApDetailSheet = false
                    onNavigateToTrack(ap.index)
                }
            )
        }
    }
    
    // Attack Options Bottom Sheet
    if (showAttackOptionsSheet && selectedAp != null) {
AttackOptionsSheet(
            accessPoint = selectedAp!!,
            activeDeauthIndex = activeDeauthIndex,
            isBeaconSpamming = isBeaconSpamming,
            isRickRolling = isRickRolling,
            isKarmaRunning = isKarmaRunning,
            privacyMode = privacyMode,
            onDismiss = { showAttackOptionsSheet = false },
            onDeauth = { index ->
                if (activeDeauthIndex == index) {
                    viewModel.stopDeauth()
                    activeDeauthIndex = null
                } else {
                    if (activeDeauthIndex != null) {
                        viewModel.stopDeauth()
                    }
                    viewModel.selectAp(index.toString())
                    viewModel.startDeauth()
                    activeDeauthIndex = index
                }
            },
            onBeaconSpam = {
                if (isBeaconSpamming) {
                    viewModel.stopBeaconSpam()
                    isBeaconSpamming = false
                } else {
                    viewModel.startBeaconSpam()
                    isBeaconSpamming = true
                }
            },
            onRickRoll = {
                if (isRickRolling) {
                    viewModel.stopBeaconSpam()
                    isRickRolling = false
                } else {
                    viewModel.startBeaconSpam(GhostCommand.BeaconSpamMode.RICKROLL)
                    isRickRolling = true
                }
            },
            onKarma = {
                if (isKarmaRunning) {
                    viewModel.stopKarma()
                    isKarmaRunning = false
                } else {
                    viewModel.startKarma()
                    isKarmaRunning = true
                }
            },
            onStopAll = {
                viewModel.stopAll()
                activeDeauthIndex = null
                isBeaconSpamming = false
                isRickRolling = false
                isKarmaRunning = false
                activePacketCaptureMode = null
            }
        )
    }

    if (showPacketCaptureDialog) {
        PacketCaptureDialog(
            onDismiss = { showPacketCaptureDialog = false },
            onStart = { mode, channel ->
                viewModel.startPacketCapture(mode, channel)
                activePacketCaptureMode = mode
                showPacketCaptureDialog = false
            },
            onStop = {
                viewModel.stopPacketCapture()
                activePacketCaptureMode = null
                showPacketCaptureDialog = false
            }
        )
    }
    
    // Station Detail Bottom Sheet
    if (showStationDetailSheet && selectedStation != null) {
        StationDetailSheet(
            station = selectedStation!!,
            privacyMode = privacyMode,
            onDismiss = { showStationDetailSheet = false },
            onDeauth = {
                viewModel.selectStation(selectedStation!!.index.toString())
                viewModel.startDeauth()
                showStationDetailSheet = false
            }
        )
    }
    
    // Device Info Dialog
    if (showDeviceInfoDialog) {
        DeviceInfoDialog(
            deviceInfo = deviceInfo,
            onDismiss = { showDeviceInfoDialog = false },
            onRefresh = { viewModel.getChipInfo() },
            chipInfoRaw = chipInfoRaw,
            chipInfoParseStatus = chipInfoParseStatus,
            chipInfoDebugLog = chipInfoDebugLog
        )
    }
    
    // Not Connected Snackbar
    if (showNotConnectedSnackbar) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            showNotConnectedSnackbar = false
        }
        Snackbar(
            modifier = Modifier.padding(16.dp),
            containerColor = warningColor(),
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Text("Connect to device first to view device info")
        }
    }
}

/**
 * Active attack banner showing current attacks
 */
@Composable
private fun ActiveAttackBanner(
    deauthIndex: Int?,
    isBeaconSpamming: Boolean,
    isRickRolling: Boolean,
    isKarmaRunning: Boolean,
    isScanningStations: Boolean = false,
    onStopAll: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isScanningStations) warningColor().copy(alpha = 0.15f) else errorColor().copy(alpha = 0.15f),
        border = BorderStroke(1.dp, if (isScanningStations) warningColor() else errorColor())
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isScanningStations) Icons.Default.Devices else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isScanningStations) warningColor() else errorColor(),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isScanningStations) "Station Scan Active" else "Active Attacks",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isScanningStations) warningColor() else errorColor(),
                    fontWeight = FontWeight.Bold
                )
                val activities = mutableListOf<String>()
                if (deauthIndex != null) activities.add("Deauth AP[$deauthIndex]")
                if (isBeaconSpamming) activities.add("Beacon Spam")
                if (isKarmaRunning) activities.add("Karma")
                if (isRickRolling) activities.add("Rick Roll")
                if (isScanningStations) activities.add("Scanning for stations...")
                Text(
                    text = activities.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onStopAll) {
                Text("Stop All", color = if (isScanningStations) warningColor() else errorColor(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * WiFi status banner with connect button
 */
@Composable
private fun WifiStatusBanner(
    isConnected: Boolean,
    connectionState: SerialManager.ConnectionState,
    connectionTransport: SerialManager.ConnectionTransport,
    deviceName: String?,
    onConnect: () -> Unit
) {
    val borderColor = when {
        isConnected -> successColor()
        connectionState == SerialManager.ConnectionState.CONNECTING -> warningColor()
        connectionState == SerialManager.ConnectionState.ERROR -> errorColor()
        else -> MaterialTheme.colorScheme.outline
    }

    val backgroundColor = when {
        isConnected -> successColor().copy(alpha = 0.08f)
        connectionState == SerialManager.ConnectionState.CONNECTING -> warningColor().copy(alpha = 0.08f)
        connectionState == SerialManager.ConnectionState.ERROR -> errorColor().copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val statusText = when (connectionState) {
        SerialManager.ConnectionState.CONNECTED -> "Connected to $deviceName"
        SerialManager.ConnectionState.CONNECTING -> "Connecting..."
        SerialManager.ConnectionState.ERROR -> "Connection Error"
        SerialManager.ConnectionState.DISCONNECTED -> "Not Connected"
    }
    
    val subtitleText = when (connectionState) {
        SerialManager.ConnectionState.CONNECTED -> when (connectionTransport) {
            SerialManager.ConnectionTransport.USB -> "Connected over USB serial"
            SerialManager.ConnectionTransport.BLE -> "Connected over wireless bridge"
            SerialManager.ConnectionTransport.NONE -> "Connected"
        }
        SerialManager.ConnectionState.CONNECTING -> "Please wait..."
        SerialManager.ConnectionState.ERROR -> "Tap to retry connection"
        SerialManager.ConnectionState.DISCONNECTED -> "Tap to connect"
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (connectionState == SerialManager.ConnectionState.CONNECTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = borderColor,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = when {
                        !isConnected -> Icons.Default.UsbOff
                        connectionTransport == SerialManager.ConnectionTransport.BLE -> Icons.Default.BluetoothConnected
                        else -> Icons.Default.Usb
                    },
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isConnected && connectionState != SerialManager.ConnectionState.CONNECTING) {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Connect", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Device Selection Dialog
 */
@Composable
private fun DeviceSelectionDialog(
    devices: List<UsbDevice>,
    allUsbDevices: List<UsbDevice> = emptyList(),
    onDeviceSelected: (UsbDevice) -> Unit,
    onDebugClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text("Select Device") 
        },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text("No recognized USB serial devices found.")
                    Spacer(modifier = Modifier.height(8.dp))
                    if (allUsbDevices.isNotEmpty()) {
                        Text(
                            "Found ${allUsbDevices.size} USB device(s) but none are recognized as serial devices:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        allUsbDevices.forEach { device ->
                            Text(
                                "• ${device.deviceName} (VID:0x${device.vendorId.toString(16)}, PID:0x${device.productId.toString(16)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = Warning
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = devices,
                            key = { it.deviceName },
                            contentType = { "usb_device" }
                        ) { device ->
                            Card(
                                onClick = { onDeviceSelected(device) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = device.deviceName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "VID: 0x${device.vendorId.toString(16)} PID: 0x${device.productId.toString(16)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (device.manufacturerName != null) {
                                        Text(
                                            text = "Manufacturer: ${device.manufacturerName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDebugClick) {
                    Text("Debug Log")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

/**
 * Scan button with live scanning indicator
 */
@Composable
private fun WifiScanButton(
    isScanning: Boolean,
    isConnected: Boolean,
    onScanClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        BrutalistButton(
            text = if (isScanning) "Scanning..." else "Scan Networks",
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primary,
            textColor = MaterialTheme.colorScheme.onPrimary,
            enabled = isConnected,
            isLoading = isScanning,
            leadingIcon = {
                if (!isScanning) {
                    Icon(
                        Icons.Default.Search, 
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )
    }
}

/**
 * Packet capture mode picker backed by firmware capture commands.
 */
@Composable
private fun PacketCaptureDialog(
    onDismiss: () -> Unit,
    onStart: (GhostCommand.CaptureMode, Int?) -> Unit,
    onStop: () -> Unit
) {
    val modes = listOf(
        GhostCommand.CaptureMode.EAPOL to "EAPOL / Handshakes",
        GhostCommand.CaptureMode.PROBE to "Probe Requests",
        GhostCommand.CaptureMode.DEAUTH to "Deauth Frames",
        GhostCommand.CaptureMode.BEACON to "Beacon Frames",
        GhostCommand.CaptureMode.RAW to "Raw WiFi",
        GhostCommand.CaptureMode.WPS to "WPS",
        GhostCommand.CaptureMode.PWN to "Pwnagotchi",
        GhostCommand.CaptureMode.BLE to "BLE",
        GhostCommand.CaptureMode.SKIMMER to "Skimmer BLE",
        GhostCommand.CaptureMode.IEEE802154 to "802.15.4"
    )
    var selectedMode by remember { mutableStateOf(GhostCommand.CaptureMode.EAPOL) }
    var modeExpanded by remember { mutableStateOf(false) }
    var selectedQuickChannel by remember { mutableStateOf<Int?>(null) }
    var customChannel by remember { mutableStateOf("") }
    val selectedModeLabel = modes.first { it.first == selectedMode }.second
    val customChannelValue = customChannel.trim().toIntOrNull()
    val selectedChannel = customChannelValue ?: selectedQuickChannel
    val channelValid = customChannel.isBlank() || (customChannelValue != null && customChannelValue > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Packet Capture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Auto keeps firmware hopping. A channel lock sends -channel <n> and firmware validates support.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    OutlinedButton(
                        onClick = { modeExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedModeLabel, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ExpandMore, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false }
                    ) {
                        modes.forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedMode = mode
                                    modeExpanded = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Int?>(null, 1, 6, 11).forEach { channel ->
                        FilterChip(
                            selected = customChannel.isBlank() && selectedQuickChannel == channel,
                            onClick = {
                                selectedQuickChannel = channel
                                customChannel = ""
                            },
                            label = { Text(channel?.let { "Ch $it" } ?: "Auto") }
                        )
                    }
                }
                OutlinedTextField(
                    value = customChannel,
                    onValueChange = { value ->
                        customChannel = value.filter { it.isDigit() }.take(3)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Custom channel") },
                    placeholder = { Text("36, 149, 26...") },
                    isError = !channelValid,
                    supportingText = {
                        Text(if (channelValid) "Optional. Leave blank for Auto or quick chip." else "Enter a positive channel number.")
                    }
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onStop) {
                    Text("Stop")
                }
                Button(
                    enabled = channelValid,
                    onClick = { onStart(selectedMode, selectedChannel) }
                ) {
                    Text(if (selectedChannel == null) "Start" else "Start Ch $selectedChannel")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Quick action chip
 */
@Composable
private fun QuickActionChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    selectedColor: Color = errorColor()
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (isSelected) selectedColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
            labelColor = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = selectedColor.copy(alpha = 0.3f)
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = if (isSelected) selectedColor else MaterialTheme.colorScheme.outline,
            selectedBorderColor = selectedColor,
            enabled = enabled,
            selected = isSelected
        )
    )
}

/**
 * WiFi AP Card - Minimal style
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiApCard(
    accessPoint: AccessPointPreview,
    isAttacking: Boolean,
    privacyMode: Boolean,
    onClick: () -> Unit
) {
    val signalColor = when {
        accessPoint.rssi >= -50 -> SignalExcellent
        accessPoint.rssi >= -60 -> SignalGood
        accessPoint.rssi >= -70 -> SignalFair
        else -> SignalWeak
    }
    
    val securityColor = when (accessPoint.security) {
        "Open" -> MaterialTheme.colorScheme.tertiary
        "WPA3" -> MaterialTheme.colorScheme.primary
        "WPA2" -> successColor()
        else -> errorColor()
    }

    val borderColor = if (isAttacking) errorColor() else MaterialTheme.colorScheme.outline
    val backgroundColor = if (isAttacking) errorColor().copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
    
    BrutalistCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        borderColor = borderColor,
        backgroundColor = backgroundColor,
        borderWidth = if (isAttacking) 2.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // WiFi Icon
            Icon(
                imageVector = Icons.Default.SignalWifi4Bar,
                contentDescription = "Signal strength",
                tint = signalColor,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(14.dp))
            
            // Network info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = accessPoint.ssid.censorSsid(privacyMode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isAttacking) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Attacking",
                            tint = errorColor(),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Security indicator
                    if (accessPoint.security == "Open") {
                        BrutalistChip(
                            text = "Open",
                            backgroundColor = tertiaryColor().copy(alpha = 0.1f),
                            borderColor = tertiaryColor().copy(alpha = 0.3f),
                            textColor = tertiaryColor()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secured",
                            modifier = Modifier.size(12.dp),
                            tint = securityColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = accessPoint.security,
                            style = MaterialTheme.typography.labelSmall,
                            color = securityColor
                        )
                    }
                    
                    Text(
                        text = "  Ch ${accessPoint.channel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    accessPoint.vendor?.let {
                        Text(
                            text = "  $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * AP Detail Bottom Sheet - Shows AP details and actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApDetailSheet(
    accessPoint: AccessPointPreview,
    isAttacking: Boolean,
    privacyMode: Boolean,
    isCurrentConnection: Boolean = false,
    connectedIp: String? = null,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onShowAttackOptions: () -> Unit,
    onDeauth: () -> Unit,
    onTrack: () -> Unit
) {
    val signalColor = when {
        accessPoint.rssi >= -50 -> SignalExcellent
        accessPoint.rssi >= -60 -> SignalGood
        accessPoint.rssi >= -70 -> SignalFair
        else -> SignalWeak
    }
    
    val signalText = when {
        accessPoint.rssi >= -50 -> "Excellent"
        accessPoint.rssi >= -60 -> "Good"
        accessPoint.rssi >= -70 -> "Fair"
        else -> "Weak"
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with SSID and connection badge
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = accessPoint.ssid.censorSsid(privacyMode),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isCurrentConnection) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = successColor().copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, successColor().copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Connected",
                                tint = successColor(),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Connected",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = successColor()
                            )
                        }
                    }
                }
            }
            
            // Show IP if connected
            if (isCurrentConnection && connectedIp != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "IP: $connectedIp",
                    style = MaterialTheme.typography.bodySmall,
                    color = successColor()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Details Grid
            DetailRow("BSSID", accessPoint.bssid.censorMac(privacyMode))
            DetailRow("Channel", accessPoint.channel.toString())
            DetailRow("Security", accessPoint.security)
            DetailRow("Signal", "${accessPoint.rssi} dBm ($signalText)", signalColor)
            accessPoint.vendor?.let { DetailRow("Vendor", it) }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BrutalistOutlinedButton(
                    text = "View Options",
                    onClick = onSelect,
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) }
                )
                BrutalistOutlinedButton(
                    text = "Track",
                    onClick = onTrack,
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Deauth toggle button
            BrutalistButton(
                text = if (isAttacking) "Stop Deauth" else "Start Deauth",
                onClick = onDeauth,
                modifier = Modifier.fillMaxWidth(),
                containerColor = if (isAttacking) warningColor() else errorColor(),
                borderColor = if (isAttacking) warningColor() else errorColor(),
                leadingIcon = { 
                    Icon(
                        if (isAttacking) Icons.Default.Stop else Icons.Default.Warning, 
                        contentDescription = null 
                    ) 
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Attack Options button
            BrutalistButton(
                text = "More Attack Options",
                onClick = onShowAttackOptions,
                modifier = Modifier.fillMaxWidth(),
                containerColor = primaryColor(),
                borderColor = primaryColor(),
                leadingIcon = { Icon(Icons.Default.ArrowForward, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Device Selection Dialog
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DeviceSelectionDialog(
    devices: List<UsbDevice>,
    allUsbDevices: List<UsbDevice> = emptyList(),
    usbDebugLog: List<String> = emptyList(),
    onDeviceSelected: (UsbDevice, Int) -> Unit,
    onDebugClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var showDebugLog by remember { mutableStateOf(false) }
    val baudRates = remember { listOf(9600, 57600, 115200, 230400, 420600, 460800, 921600) }
    var selectedBaud by remember { mutableStateOf(115200) }
    var baudExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, OutlineDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .heightIn(max = 500.dp)
            ) {
                Text(
                    text = "Select Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (usbDebugLog.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showDebugLog = !showDebugLog },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (showDebugLog) "Hide Debug Log" else "Show Debug Log",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariantDark
                        )
                    }
                }

                if (showDebugLog && usbDebugLog.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = BackgroundDark,
                        border = BorderStroke(1.dp, OutlineVariantDark)
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            items(usbDebugLog) { logLine ->
                                Text(
                                    text = logLine,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = OnSurfaceVariantDark,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Baud rate selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Baud Rate",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    ExposedDropdownMenuBox(
                        expanded = baudExpanded,
                        onExpandedChange = { baudExpanded = it },
                        modifier = Modifier.width(140.dp)
                    ) {
                        OutlinedButton(
                            onClick = { baudExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        ) {
                            Text(
                                text = selectedBaud.toString(),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        ExposedDropdownMenu(
                            expanded = baudExpanded,
                            onDismissRequest = { baudExpanded = false }
                        ) {
                            baudRates.forEach { baud ->
                                DropdownMenuItem(
                                    text = { Text(baud.toString()) },
                                    onClick = {
                                        selectedBaud = baud
                                        baudExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (devices.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No serial devices found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariantDark
                        )
                        if (allUsbDevices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Raw USB detected: ${allUsbDevices.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryColor()
                            )
                            allUsbDevices.forEach { device ->
                                Text(
                                    text = "• ${device.productName ?: device.deviceName} (0x${device.vendorId.toString(16)}:0x${device.productId.toString(16)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariantDark
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "No USB devices detected",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantDark
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Supported: CH340, CH341, CP210x, FTDI, CDC/ACM",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onDebugClick) {
                            Text("Refresh Scan")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = devices,
                            key = { "${it.vendorId}-${it.productId}-${it.deviceName}" }
                        ) { device ->
                            Card(
                                onClick = { onDeviceSelected(device, selectedBaud) },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                                border = BorderStroke(1.dp, OutlineDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = device.productName ?: device.deviceName.ifEmpty { "USB Device" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    device.manufacturerName?.let { manufacturer ->
                                        Text(
                                            text = manufacturer,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = primaryColor()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "VID: 0x${device.vendorId.toString(16)}  PID: 0x${device.productId.toString(16)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnSurfaceVariantDark
                                        )
                                        Text(
                                            text = "${device.interfaceCount} if",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnSurfaceVariantDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = OnSurfaceVariantDark)
                    }
                }
            }
        }
    }
}

/**
 * Attack Options Bottom Sheet - Shows all available attacks
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttackOptionsSheet(
    accessPoint: AccessPointPreview,
    activeDeauthIndex: Int?,
    isBeaconSpamming: Boolean,
    isRickRolling: Boolean,
    isKarmaRunning: Boolean,
    privacyMode: Boolean = false,
    onDismiss: () -> Unit,
    onDeauth: (Int) -> Unit,
    onBeaconSpam: () -> Unit,
    onRickRoll: () -> Unit,
    onKarma: () -> Unit,
    onStopAll: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Attack Options",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
Text(
                text = "Target: ${accessPoint.ssid.censorSsid(privacyMode)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Deauth Attack
            AttackOptionItem(
                title = "Deauthentication Attack",
                description = "Disconnect clients from the target AP by sending deauth frames",
                isActive = activeDeauthIndex == accessPoint.index,
                icon = Icons.Default.WifiOff,
                onClick = { onDeauth(accessPoint.index) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Beacon Spam
            AttackOptionItem(
                title = "Beacon Spam",
                description = "Flood the area with fake AP beacons",
                isActive = isBeaconSpamming,
                icon = Icons.Default.Router,
                onClick = onBeaconSpam
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Karma Attack
            AttackOptionItem(
                title = "Karma Attack",
                description = "Respond to probe requests with matching AP beacons",
                isActive = isKarmaRunning,
                icon = Icons.Default.Phishing,
                onClick = onKarma
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Rick Roll
            AttackOptionItem(
                title = "Rick Roll",
                description = "Create a Rick Roll themed evil twin AP",
                isActive = isRickRolling,
                icon = Icons.Default.MusicNote,
                onClick = onRickRoll
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Stop All Button
            BrutalistOutlinedButton(
                text = "Stop All Attacks",
                onClick = onStopAll,
                modifier = Modifier.fillMaxWidth(),
                borderColor = errorColor(),
                textColor = errorColor(),
                leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Station Detail Bottom Sheet - Shows station details and deauth action
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationDetailSheet(
    station: GhostResponse.Station,
    privacyMode: Boolean,
    onDismiss: () -> Unit,
    onDeauth: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with station icon
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Devices,
                    contentDescription = "Station",
                    tint = warningColor(),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Station Details",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Details Grid
            DetailRow("MAC Address", station.mac.censorMac(privacyMode))
            station.vendor?.let { DetailRow("Vendor", it) }
            station.associatedApSsid?.let { DetailRow("Connected AP", it) }
            station.apBssid?.let { DetailRow("AP BSSID", it.censorMac(privacyMode)) }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Deauth button
            BrutalistButton(
                text = "Send Deauth Attack",
                onClick = onDeauth,
                modifier = Modifier.fillMaxWidth(),
                containerColor = errorColor(),
                borderColor = errorColor(),
                leadingIcon = { 
                    Icon(
                        Icons.Default.WifiOff, 
                        contentDescription = null 
                    ) 
                }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Attack option item with toggle
 */
@Composable
private fun AttackOptionItem(
    title: String,
    description: String,
    isActive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    BrutalistCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isActive) errorColor() else MaterialTheme.colorScheme.outline,
        backgroundColor = if (isActive) errorColor().copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) errorColor() else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) Error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Toggle indicator
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isActive) Error else MaterialTheme.colorScheme.outline
            ) {
                Text(
                    text = if (isActive) "STOP" else "START",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Detail row for displaying key-value information
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

/**
 * Preview data class for AP
 */
@Immutable
data class AccessPointPreview(
    val index: Int,
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val channel: Int,
    val security: String,
    val vendor: String?
)

/**
 * WiFi AP Card with always-visible stations list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiApCardWithStations(
    accessPoint: AccessPointPreview,
    isAttacking: Boolean,
    privacyMode: Boolean,
    hasStations: Boolean,
    associatedStationsCount: Int,
    associatedStations: List<GhostResponse.Station>,
    isCurrentConnection: Boolean = false,
    connectedIp: String? = null,
    onClick: () -> Unit,
    onStationClick: (GhostResponse.Station) -> Unit
) {
    val signalColor = when {
        accessPoint.rssi >= -50 -> SignalExcellent
        accessPoint.rssi >= -60 -> SignalGood
        accessPoint.rssi >= -70 -> SignalFair
        else -> SignalWeak
    }
    
    val securityColor = when (accessPoint.security) {
        "Open" -> MaterialTheme.colorScheme.tertiary
        "WPA3" -> MaterialTheme.colorScheme.primary
        "WPA2" -> successColor()
        else -> errorColor()
    }

    val borderColor = when {
        isCurrentConnection -> successColor()
        isAttacking -> errorColor()
        else -> MaterialTheme.colorScheme.outline
    }
    val backgroundColor = when {
        isCurrentConnection -> successColor().copy(alpha = 0.1f)
        isAttacking -> errorColor().copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Main AP Card
        BrutalistCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            borderColor = borderColor,
            backgroundColor = backgroundColor,
            borderWidth = if (isAttacking) 2.dp else 1.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // WiFi Icon
                Icon(
                    imageVector = Icons.Default.SignalWifi4Bar,
                    contentDescription = "Signal strength",
                    tint = signalColor,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(14.dp))
                
                // Network info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = accessPoint.ssid.censorSsid(privacyMode),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isAttacking) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Attacking",
                                tint = errorColor(),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        // Station count badge - smaller
                        if (hasStations) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = warningColor().copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, warningColor())
                            ) {
                                Text(
                                    text = "$associatedStationsCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f,
                                    color = warningColor(),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Security indicator
                        if (accessPoint.security == "Open") {
                            BrutalistChip(
                                text = "Open",
                                backgroundColor = tertiaryColor().copy(alpha = 0.1f),
                                borderColor = tertiaryColor().copy(alpha = 0.3f),
                                textColor = tertiaryColor()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secured",
                                modifier = Modifier.size(12.dp),
                                tint = securityColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = accessPoint.security,
                                style = MaterialTheme.typography.labelSmall,
                                color = securityColor
                            )
                        }
                        
                        Text(
                            text = "  Ch ${accessPoint.channel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        accessPoint.vendor?.let {
                            Text(
                                text = "  $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Always show stations list with tree connectors
        if (associatedStations.isNotEmpty()) {
            // Tree connector from AP card to first station
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .width(1.5.dp)
                    .height(4.dp)
                    .background(warningColor().copy(alpha = 0.6f))
            )
            
associatedStations.forEachIndexed { index, station ->
                val isLast = index == associatedStations.size - 1
                StationSubItem(
                    station = station,
                    isLast = isLast,
                    privacyMode = privacyMode,
                    onClick = { onStationClick(station) }
                )
            }
        }
    }
}

/**
 * Station sub-item displayed under an AP card - slim version with tree connector
 */
@Composable
private fun StationSubItem(
    station: GhostResponse.Station,
    isLast: Boolean = false,
    privacyMode: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = warningColor().copy(alpha = 0.5f)
    val backgroundColor = warningColor().copy(alpha = 0.05f)
    val connectorColor = warningColor().copy(alpha = 0.6f)
    val lineThickness = 1.5.dp
    val verticalLineX = 16.dp // X position of vertical line from left edge
    val horizontalLineLength = 8.dp // Length of horizontal connector
    
    // Use drawBehind to draw tree connector lines precisely
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val lineThicknessPx = lineThickness.toPx()
                val verticalLineXPx = verticalLineX.toPx()
                val horizontalLineLengthPx = horizontalLineLength.toPx()
                
                // Starting Y position for the vertical line (top of this item)
                val verticalStartY = 0f
                // Ending Y position - extends through to next item if not last, or stops at row center if last
                val verticalEndY = if (!isLast) size.height + 4.dp.toPx() else size.height / 2f
                
                // Draw vertical line
                drawRect(
                    color = connectorColor,
                    topLeft = Offset(verticalLineXPx, verticalStartY),
                    size = androidx.compose.ui.geometry.Size(lineThicknessPx, verticalEndY)
                )
                
                // Draw horizontal line at vertical center of this row
                val horizontalY = size.height / 2f
                drawRect(
                    color = connectorColor,
                    topLeft = Offset(verticalLineXPx, horizontalY),
                    size = androidx.compose.ui.geometry.Size(horizontalLineLengthPx, lineThicknessPx)
                )
            }
    ) {
        // Main content row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spacer to push content past the connector lines
            Spacer(modifier = Modifier.width(verticalLineX + horizontalLineLength + 4.dp))
            
            // Station card
            BrutalistCard(
                onClick = onClick,
                modifier = Modifier.weight(1f),
                borderColor = borderColor,
                backgroundColor = backgroundColor,
                borderWidth = 0.5.dp,
                shadowOffset = 0.dp,
                cornerRadius = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Station icon - smaller
                    Icon(
                        Icons.Default.Devices,
                        contentDescription = "Station",
                        tint = warningColor(),
                        modifier = Modifier.size(14.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = station.mac.censorMac(privacyMode),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        station.vendor?.let { vendor ->
                            Text(
                                text = vendor,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Station result card for displaying unassociated stations - slim version
 */
@Composable
private fun StationResultCard(
    station: GhostResponse.Station,
    privacyMode: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = warningColor().copy(alpha = 0.5f)
    val backgroundColor = warningColor().copy(alpha = 0.05f)
    
    BrutalistCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 2.dp),
        borderColor = borderColor,
        backgroundColor = backgroundColor,
        borderWidth = 0.5.dp,
        shadowOffset = 0.dp,
        cornerRadius = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Station icon - smaller
            Icon(
                Icons.Default.Devices,
                contentDescription = "Station",
                tint = warningColor(),
                modifier = Modifier.size(14.dp)
            )
            
            Spacer(modifier = Modifier.width(6.dp))
            
            Column(modifier = Modifier.weight(1f)) {
Text(
                    text = station.mac.censorMac(privacyMode),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                station.vendor?.let { vendor ->
                    Text(
                        text = vendor,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
