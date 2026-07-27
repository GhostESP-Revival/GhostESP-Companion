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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostCommand
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.utils.censorMac
import com.example.ghostespcompanion.ui.utils.censorSsid
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel
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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
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

    // Attacks tab row states (row-based attack launcher list)
    // Shared target AP for every attack that operates on the firmware's `select -a` selection
    var targetApIndex by remember { mutableStateOf<Int?>(null) }
    var beaconSpamRickroll by remember { mutableStateOf(false) }
    var saePassword by remember { mutableStateOf("") }
    var isSaeFloodRunning by remember { mutableStateOf(false) }
    var dhcpThreads by remember { mutableStateOf("") }
    var isDhcpStarveRunning by remember { mutableStateOf(false) }
    var isListenProbesRunning by remember { mutableStateOf(false) }
    var isPineApRunning by remember { mutableStateOf(false) }
    var isFlockScanRunning by remember { mutableStateOf(false) }
    var openPortsTarget by remember { mutableStateOf("") }
    var sshScanTarget by remember { mutableStateOf("") }
    var netBiosScanTarget by remember { mutableStateOf("") }
    var httpBannerScanTarget by remember { mutableStateOf("") }
    var snmpTarget by remember { mutableStateOf("") }
    var snmpWalkMode by remember { mutableStateOf(false) }
    var enumScanTarget by remember { mutableStateOf("") }
    var isChannelSwitchRunning by remember { mutableStateOf(false) }
    var gtkAbusePassword by remember { mutableStateOf("") }
    var isGtkAbuseRunning by remember { mutableStateOf(false) }
    
    // Station detail sheet states
    var selectedStation by remember { mutableStateOf<GhostResponse.Station?>(null) }
    var showStationDetailSheet by remember { mutableStateOf(false) }

    // AP multi-select mode - firmware's `select -a i,j,k` genuinely deauths every selected AP at once
    var apMultiSelectMode by remember { mutableStateOf(false) }
    var selectedApIndices by remember { mutableStateOf(setOf<Int>()) }
    
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
    val pineapDetections by viewModel.pineapDetections.collectAsState()
    val flockDetections by viewModel.flockDetections.collectAsState()
    val flockScanComplete by viewModel.flockScanComplete.collectAsState()
    val netBiosResults by viewModel.netBiosResults.collectAsState()
    val httpBannerHits by viewModel.httpBannerHits.collectAsState()
    val httpBannerSummary by viewModel.httpBannerSummary.collectAsState()
    val snmpHits by viewModel.snmpHits.collectAsState()
    val snmpSummary by viewModel.snmpSummary.collectAsState()
    val enumHits by viewModel.enumHits.collectAsState()
    val enumSummary by viewModel.enumSummary.collectAsState()
    val wpa3Compliance by viewModel.wpa3Compliance.collectAsState()
    val wpa3ReportSummary by viewModel.wpa3ReportSummary.collectAsState()
    val csaAttackStatus by viewModel.csaAttackStatus.collectAsState()
    val gtkAbuseLog by viewModel.gtkAbuseLog.collectAsState()
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
            if (activeDeauthIndex != null || isBeaconSpamming || isRickRolling || isKarmaRunning ||
                isSaeFloodRunning || isDhcpStarveRunning || isListenProbesRunning ||
                isPineApRunning || isFlockScanRunning || isChannelSwitchRunning || isGtkAbuseRunning
            ) {
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

    // Pre-fill recon scan targets with the connected network's IP once known, without clobbering user edits
    LaunchedEffect(wifiConnection?.ip) {
        val ip = wifiConnection?.ip ?: return@LaunchedEffect
        if (netBiosScanTarget.isBlank()) netBiosScanTarget = ip
        if (httpBannerScanTarget.isBlank()) httpBannerScanTarget = ip
        if (snmpTarget.isBlank()) snmpTarget = ip
        if (enumScanTarget.isBlank()) enumScanTarget = ip
    }
    
    val tabTitles = listOf(
        stringResource(R.string.tab_wifi_access_points),
        stringResource(R.string.tab_wifi_attacks)
    )

    MainScreen(
        title = stringResource(R.string.title_wifi),
        actions = {
            IconButton(onClick = onNavigateToPortal) {
                Icon(
                    painter = painterResource(R.drawable.ic_evil_portal),
                    contentDescription = stringResource(R.string.label_evil_portal),
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
                    contentDescription = stringResource(R.string.label_device_info),
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
                deviceName = stringResource(R.string.app_name_short),
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
            
            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // Access Points + Stations tab
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BrutalistButton(
                                    text = if (isScanning) stringResource(R.string.action_stop_scan) else stringResource(R.string.action_scan_networks),
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

                                BrutalistButton(
                                    text = if (isScanningStations) stringResource(R.string.action_stop_scan) else stringResource(R.string.action_scan_stations),
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
                        }

                        item {
                            if (displayAccessPoints.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BrutalistSectionHeader(
                                        title = stringResource(R.string.msg_found_networks_count, displayAccessPoints.size),
                                        accentColor = primaryColor()
                                    )
                                    TextButton(onClick = {
                                        apMultiSelectMode = !apMultiSelectMode
                                        selectedApIndices = emptySet()
                                    }) {
                                        Text(
                                            text = if (apMultiSelectMode) stringResource(R.string.action_cancel) else stringResource(R.string.action_select),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            } else if (isConnected && !isScanning) {
                                BrutalistSectionHeader(
                                    title = stringResource(R.string.msg_no_networks_found_hint),
                                    accentColor = OnSurfaceVariantDark
                                )
                            }
                        }

                        if (apMultiSelectMode && selectedApIndices.isNotEmpty()) {
                            item {
                                ApBulkActionBar(
                                    selectedCount = selectedApIndices.size,
                                    enabled = isConnected,
                                    onDeauthSelected = {
                                        viewModel.selectAp(selectedApIndices.sorted().joinToString(","))
                                        viewModel.startDeauth()
                                        activeDeauthIndex = selectedApIndices.first()
                                        apMultiSelectMode = false
                                        selectedApIndices = emptySet()
                                    },
                                    onClear = { selectedApIndices = emptySet() }
                                )
                            }
                        }

                        if (isScanning && displayAccessPoints.isEmpty()) {
                            items(5) {
                                SkeletonWifiApCard()
                            }
                        } else {
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
                                        multiSelectMode = apMultiSelectMode,
                                        isSelected = ap.index in selectedApIndices,
                                        onClick = {
                                            if (apMultiSelectMode) {
                                                selectedApIndices = if (ap.index in selectedApIndices) {
                                                    selectedApIndices - ap.index
                                                } else {
                                                    selectedApIndices + ap.index
                                                }
                                            } else {
                                                selectedAp = ap
                                                viewModel.selectAp(ap.index.toString())
                                                showApDetailSheet = true
                                            }
                                        },
                                        onStationClick = { station ->
                                            selectedStation = station
                                            viewModel.selectStation(station.index.toString())
                                            showStationDetailSheet = true
                                        }
                                    )
                                }
                            }
                        }

                        if (unassociatedStations.isNotEmpty()) {
                            item {
                                BrutalistSectionHeader(
                                    title = stringResource(R.string.label_unassociated_stations, unassociatedStations.size),
                                    accentColor = warningColor()
                                )
                            }

                            itemsIndexed(
                                items = unassociatedStations,
                                key = { _, station -> "unassoc_${station.index}" },
                                contentType = { _, _ -> "station" }
                            ) { index, station ->
                                StaggeredAnimatedItem(
                                    index = index,
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

                    1 -> {
                        // Attacks tab - row-based attack launcher list
                        val gtkAbuseTargetSsid = displayAccessPoints.find { it.index == targetApIndex }?.ssid
                        val anyAttackRunning = activeDeauthIndex != null || isBeaconSpamming || isRickRolling ||
                            isKarmaRunning || activePacketCaptureMode != null || isSaeFloodRunning ||
                            isDhcpStarveRunning || isListenProbesRunning || isScanningStations ||
                            isPineApRunning || isFlockScanRunning || isChannelSwitchRunning || isGtkAbuseRunning

                        val stopAllRowState: () -> Unit = {
                            viewModel.stopAll()
                            viewModel.stopWifiScanAndReset()
                            isScanningStations = false
                            activeDeauthIndex = null
                            isBeaconSpamming = false
                            isRickRolling = false
                            isKarmaRunning = false
                            activePacketCaptureMode = null
                            isSaeFloodRunning = false
                            isDhcpStarveRunning = false
                            isListenProbesRunning = false
                            isPineApRunning = false
                            isFlockScanRunning = false
                            isChannelSwitchRunning = false
                            isGtkAbuseRunning = false
                        }

                        item {
                            BrutalistButton(
                                text = stringResource(R.string.action_stop_all),
                                onClick = stopAllRowState,
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = errorColor(),
                                borderColor = errorColor(),
                                enabled = isConnected && anyAttackRunning,
                                leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) }
                            )
                        }

                        item {
                            TargetApSelectorCard(
                                accessPoints = displayAccessPoints,
                                privacyMode = privacyMode,
                                selectedIndex = targetApIndex,
                                enabled = isConnected,
                                onSelect = { targetApIndex = it }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_deauth_attack),
                                description = stringResource(R.string.desc_deauth_attack),
                                isRunning = activeDeauthIndex != null,
                                startEnabled = isConnected && targetApIndex != null,
                                icon = Icons.Default.WifiOff,
                                onStart = {
                                    val index = targetApIndex ?: return@AttackLauncherRow
                                    viewModel.selectAp(index.toString())
                                    viewModel.startDeauth()
                                    activeDeauthIndex = index
                                },
                                onStop = {
                                    viewModel.stopDeauth()
                                    activeDeauthIndex = null
                                },
                                expandedContent = if (targetApIndex == null) {
                                    { TargetApHint() }
                                } else null
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_eapol_capture),
                                description = stringResource(R.string.desc_eapol_capture),
                                isRunning = activePacketCaptureMode == GhostCommand.CaptureMode.EAPOL,
                                startEnabled = isConnected && targetApIndex != null,
                                icon = Icons.Default.Key,
                                onStart = {
                                    val index = targetApIndex ?: return@AttackLauncherRow
                                    viewModel.selectAp(index.toString())
                                    viewModel.startEapolCapture()
                                    activePacketCaptureMode = GhostCommand.CaptureMode.EAPOL
                                },
                                onStop = {
                                    viewModel.stopPacketCapture()
                                    activePacketCaptureMode = null
                                },
                                expandedContent = if (targetApIndex == null) {
                                    { TargetApHint() }
                                } else null
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_beacon_spam),
                                description = if (beaconSpamRickroll) stringResource(R.string.desc_rick_roll) else stringResource(R.string.desc_beacon_spam),
                                isRunning = isBeaconSpamming || isRickRolling,
                                startEnabled = isConnected,
                                icon = Icons.Default.Router,
                                onStart = {
                                    if (beaconSpamRickroll) {
                                        viewModel.startBeaconSpam(GhostCommand.BeaconSpamMode.RICKROLL)
                                        isRickRolling = true
                                    } else {
                                        viewModel.startBeaconSpam()
                                        isBeaconSpamming = true
                                    }
                                },
                                onStop = {
                                    viewModel.stopBeaconSpam()
                                    isBeaconSpamming = false
                                    isRickRolling = false
                                },
                                expandedContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = beaconSpamRickroll,
                                            onCheckedChange = { beaconSpamRickroll = it },
                                            enabled = isConnected && !isBeaconSpamming && !isRickRolling
                                        )
                                        Text(
                                            text = stringResource(R.string.label_rick_roll),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_karma),
                                description = stringResource(R.string.desc_karma_attack),
                                isRunning = isKarmaRunning,
                                startEnabled = isConnected,
                                icon = Icons.Default.Phishing,
                                onStart = {
                                    viewModel.startKarma()
                                    isKarmaRunning = true
                                },
                                onStop = {
                                    viewModel.stopKarma()
                                    isKarmaRunning = false
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_sae_flood),
                                description = stringResource(R.string.desc_sae_flood),
                                isRunning = isSaeFloodRunning,
                                startEnabled = isConnected && targetApIndex != null && saePassword.isNotBlank(),
                                icon = Icons.Default.Lock,
                                onStart = {
                                    val index = targetApIndex ?: return@AttackLauncherRow
                                    viewModel.selectAp(index.toString())
                                    viewModel.startSaeFlood(saePassword)
                                    isSaeFloodRunning = true
                                },
                                onStop = {
                                    viewModel.stopSaeFlood()
                                    isSaeFloodRunning = false
                                },
                                expandedContent = {
                                    if (targetApIndex == null) {
                                        TargetApHint()
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    OutlinedTextField(
                                        value = saePassword,
                                        onValueChange = { saePassword = it },
                                        label = { Text(stringResource(R.string.label_sae_password)) },
                                        singleLine = true,
                                        enabled = isConnected && !isSaeFloodRunning,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_dhcp_starvation),
                                description = stringResource(R.string.desc_dhcp_starvation),
                                isRunning = isDhcpStarveRunning,
                                startEnabled = isConnected,
                                icon = Icons.Default.SettingsEthernet,
                                onStart = {
                                    viewModel.startDhcpStarve(dhcpThreads.toIntOrNull())
                                    isDhcpStarveRunning = true
                                },
                                onStop = {
                                    viewModel.stopDhcpStarve()
                                    isDhcpStarveRunning = false
                                },
                                expandedContent = {
                                    OutlinedTextField(
                                        value = dhcpThreads,
                                        onValueChange = { dhcpThreads = it.filter(Char::isDigit).take(3) },
                                        label = { Text(stringResource(R.string.label_dhcp_threads)) },
                                        singleLine = true,
                                        enabled = isConnected && !isDhcpStarveRunning,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_congestion_scan),
                                description = stringResource(R.string.desc_congestion_scan),
                                isRunning = false,
                                startEnabled = isConnected,
                                startLabel = stringResource(R.string.action_run_scan),
                                icon = Icons.Default.Radar,
                                onStart = { viewModel.runCongestionScan() },
                                onStop = { }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_listen_probes),
                                description = stringResource(R.string.desc_listen_probes),
                                isRunning = isListenProbesRunning,
                                startEnabled = isConnected,
                                icon = Icons.Default.Hearing,
                                onStart = {
                                    viewModel.startListenProbes()
                                    isListenProbesRunning = true
                                },
                                onStop = {
                                    viewModel.stopListenProbes()
                                    isListenProbesRunning = false
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_pineap_detection),
                                description = stringResource(R.string.desc_pineap_detection),
                                isRunning = isPineApRunning,
                                startEnabled = isConnected,
                                icon = Icons.Default.Radar,
                                onStart = {
                                    viewModel.startPineApDetection()
                                    isPineApRunning = true
                                },
                                onStop = {
                                    viewModel.stopPineApDetection()
                                    isPineApRunning = false
                                },
                                resultsContent = {
                                    ScanFindingsList(
                                        count = pineapDetections.size,
                                        lines = pineapDetections.map { d ->
                                            "${d.heading} ${d.bssid} Ch:${d.channel} ${d.rssi}dBm SSIDs(${d.ssidCount}): ${d.ssids}"
                                        }
                                    )
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_flock_detection),
                                description = stringResource(R.string.desc_flock_detection),
                                isRunning = isFlockScanRunning,
                                startEnabled = isConnected,
                                icon = Icons.Default.TravelExplore,
                                onStart = {
                                    viewModel.startFlockScan()
                                    isFlockScanRunning = true
                                },
                                onStop = {
                                    viewModel.stopFlockScan()
                                    isFlockScanRunning = false
                                },
                                resultsContent = {
                                    ScanFindingsList(
                                        count = flockScanComplete?.count ?: flockDetections.size,
                                        lines = flockDetections.map { d ->
                                            "${d.method} ${d.mac} ${d.signalLabel}(${d.rssi}dBm) Ch:${d.channel}${d.ssid?.let { " SSID:$it" } ?: ""} Hits:${d.hits}"
                                        }
                                    )
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_open_ports_scan),
                                description = stringResource(R.string.desc_open_ports_scan),
                                isRunning = false,
                                startEnabled = isConnected && openPortsTarget.isNotBlank(),
                                startLabel = stringResource(R.string.action_run_scan),
                                icon = Icons.Default.Radar,
                                onStart = { viewModel.runScanPorts(openPortsTarget) },
                                onStop = { },
                                expandedContent = {
                                    OutlinedTextField(
                                        value = openPortsTarget,
                                        onValueChange = { openPortsTarget = it },
                                        label = { Text(stringResource(R.string.label_scan_target)) },
                                        singleLine = true,
                                        enabled = isConnected,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_ssh_scan),
                                description = stringResource(R.string.desc_ssh_scan),
                                isRunning = false,
                                startEnabled = isConnected && sshScanTarget.isNotBlank(),
                                startLabel = stringResource(R.string.action_run_scan),
                                icon = Icons.Default.Radar,
                                onStart = { viewModel.runScanSsh(sshScanTarget) },
                                onStop = { },
                                expandedContent = {
                                    OutlinedTextField(
                                        value = sshScanTarget,
                                        onValueChange = { sshScanTarget = it },
                                        label = { Text(stringResource(R.string.label_scan_target)) },
                                        singleLine = true,
                                        enabled = isConnected,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_netbios_scan),
                                description = stringResource(R.string.desc_netbios_scan),
                                isRunning = false,
                                startEnabled = isConnected,
                                startLabel = stringResource(R.string.action_run_scan),
                                icon = Icons.Default.Radar,
                                onStart = { viewModel.runNetBiosScan(netBiosScanTarget) },
                                onStop = { },
                                expandedContent = {
                                    OutlinedTextField(
                                        value = netBiosScanTarget,
                                        onValueChange = { netBiosScanTarget = it },
                                        label = { Text(stringResource(R.string.label_scan_target_optional)) },
                                        singleLine = true,
                                        enabled = isConnected,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                resultsContent = {
                                    ScanFindingsList(
                                        count = netBiosResults.size,
                                        lines = netBiosResults.map { r ->
                                            if (r.remoteIp != null) "${r.host}  IP: ${r.remoteIp}  Flags: 0x${r.flags?.toString(16) ?: "?"}"
                                            else "${r.host}  Names: ${r.names ?: "none"}"
                                        }
                                    )
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_http_banner_scan),
                                description = stringResource(R.string.desc_http_banner_scan),
                                isRunning = false,
                                startEnabled = isConnected,
                                startLabel = stringResource(R.string.action_run_scan),
                                icon = Icons.Default.Radar,
                                onStart = { viewModel.runHttpBannerScan(httpBannerScanTarget) },
                                onStop = { },
                                expandedContent = {
                                    OutlinedTextField(
                                        value = httpBannerScanTarget,
                                        onValueChange = { httpBannerScanTarget = it },
                                        label = { Text(stringResource(R.string.label_scan_target_optional)) },
                                        singleLine = true,
                                        enabled = isConnected,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                resultsContent = {
                                    ScanFindingsList(
                                        count = httpBannerSummary?.hostsFound ?: httpBannerHits.size,
                                        lines = httpBannerHits.map { h ->
                                            "${h.ip}:${h.port} (${h.scheme}) ${h.server?.let { "Server: $it" } ?: "OPEN, no banner"}"
                                        }
                                    )
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_snmp_probe),
                                description = if (snmpWalkMode) stringResource(R.string.desc_snmp_walk) else stringResource(R.string.desc_snmp_probe),
                                isRunning = false,
                                startEnabled = isConnected,
                                startLabel = stringResource(R.string.action_run_scan),
                                icon = Icons.Default.Radar,
                                onStart = { viewModel.runSnmpProbe(snmpTarget, snmpWalkMode) },
                                onStop = { },
                                expandedContent = {
                                    OutlinedTextField(
                                        value = snmpTarget,
                                        onValueChange = { snmpTarget = it },
                                        label = { Text(stringResource(R.string.label_scan_target_optional)) },
                                        singleLine = true,
                                        enabled = isConnected,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = snmpWalkMode,
                                            onCheckedChange = { snmpWalkMode = it },
                                            enabled = isConnected
                                        )
                                        Text(
                                            text = stringResource(R.string.label_snmp_walk_mode),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                resultsContent = {
                                    ScanFindingsList(
                                        count = snmpSummary?.hostsFound ?: snmpHits.size,
                                        lines = snmpHits.map { h ->
                                            if (h.oid != null) "${h.oid} = ${h.value} (${h.type})"
                                            else "${h.ip} (community: ${h.community}) sysDescr: ${h.sysDescr}"
                                        }
                                    )
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_enum_scan),
                                description = stringResource(R.string.desc_enum_scan),
                                isRunning = false,
                                startEnabled = isConnected,
                                startLabel = stringResource(R.string.action_run_scan),
                                icon = Icons.Default.Radar,
                                onStart = { viewModel.runEnumScan(enumScanTarget) },
                                onStop = { },
                                expandedContent = {
                                    OutlinedTextField(
                                        value = enumScanTarget,
                                        onValueChange = { enumScanTarget = it },
                                        label = { Text(stringResource(R.string.label_scan_target_optional)) },
                                        singleLine = true,
                                        enabled = isConnected,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                resultsContent = {
                                    ScanFindingsList(
                                        count = enumSummary?.hostsFound ?: enumHits.size,
                                        lines = enumHits.map { it.raw }
                                    )
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_wpa3_check),
                                description = stringResource(R.string.desc_wpa3_check),
                                isRunning = false,
                                startEnabled = isConnected && targetApIndex != null,
                                startLabel = stringResource(R.string.action_run_scan),
                                icon = Icons.Default.VerifiedUser,
                                onStart = {
                                    val index = targetApIndex ?: return@AttackLauncherRow
                                    viewModel.selectAp(index.toString())
                                    viewModel.runWpa3Check()
                                },
                                onStop = { },
                                expandedContent = if (targetApIndex == null) {
                                    { TargetApHint() }
                                } else null,
                                resultsContent = {
                                    val compliance = wpa3Compliance
                                    val report = wpa3ReportSummary
                                    if (compliance != null) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            DetailRow(label = stringResource(R.string.label_scan_target), value = compliance.ssid)
                                            DetailRow(label = "BSSID", value = compliance.bssid)
                                            DetailRow(label = "Auth", value = compliance.auth)
                                            DetailRow(label = "PMF", value = compliance.pmf)
                                            DetailRow(
                                                label = "WPA3",
                                                value = if (compliance.wpa3Present) "Present${if (compliance.transitionMode) " (transition mode)" else ""}" else "Not present",
                                                valueColor = if (compliance.wpa3Present && !compliance.transitionMode) successColor() else warningColor()
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = stringResource(R.string.label_wpa3_finding, compliance.finding),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    } else if (report != null) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            DetailRow(label = "APs scanned", value = report.apCount.toString())
                                            DetailRow(label = "Compliant", value = report.compliant.toString(), valueColor = successColor())
                                            DetailRow(label = "Downgradable", value = report.downgradable.toString(), valueColor = warningColor())
                                            DetailRow(label = "Legacy", value = report.legacy.toString(), valueColor = warningColor())
                                            DetailRow(label = "Open", value = report.open.toString(), valueColor = errorColor())
                                            DetailRow(label = "Other", value = report.other.toString())
                                        }
                                    }
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_channel_switch_attack),
                                description = stringResource(R.string.desc_channel_switch_attack),
                                isRunning = isChannelSwitchRunning,
                                startEnabled = isConnected && targetApIndex != null,
                                icon = Icons.Default.SwapHoriz,
                                onStart = {
                                    val index = targetApIndex ?: return@AttackLauncherRow
                                    viewModel.selectAp(index.toString())
                                    viewModel.startChannelSwitchAttack()
                                    isChannelSwitchRunning = true
                                },
                                onStop = {
                                    viewModel.stopAll()
                                    isChannelSwitchRunning = false
                                },
                                expandedContent = if (targetApIndex == null) {
                                    { TargetApHint() }
                                } else null,
                                resultsContent = {
                                    if (csaAttackStatus.targetCount > 0) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = stringResource(R.string.label_csa_targeting, csaAttackStatus.targetCount),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            csaAttackStatus.targets.forEach { target ->
                                                Text(
                                                    text = target,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            csaAttackStatus.packetsPerSecond?.let { rate ->
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = stringResource(R.string.label_csa_rate, rate),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = errorColor()
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_gtk_abuse),
                                description = stringResource(R.string.desc_gtk_abuse),
                                isRunning = isGtkAbuseRunning,
                                startEnabled = isConnected && gtkAbuseTargetSsid != null && gtkAbusePassword.isNotBlank(),
                                icon = Icons.Default.Lock,
                                onStart = {
                                    val ssid = gtkAbuseTargetSsid ?: return@AttackLauncherRow
                                    viewModel.startGtkAbuse(ssid, gtkAbusePassword)
                                    isGtkAbuseRunning = true
                                },
                                onStop = {
                                    viewModel.stopAll()
                                    isGtkAbuseRunning = false
                                },
                                expandedContent = {
                                    if (targetApIndex == null) {
                                        TargetApHint()
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    OutlinedTextField(
                                        value = gtkAbusePassword,
                                        onValueChange = { gtkAbusePassword = it },
                                        label = { Text(stringResource(R.string.label_gtk_password)) },
                                        singleLine = true,
                                        enabled = isConnected && !isGtkAbuseRunning,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                resultsContent = {
                                    if (gtkAbuseLog.isNotEmpty()) {
                                        val isolationBroken = gtkAbuseLog.any { it.message.contains("isolation is BROKEN", ignoreCase = true) }
                                        val isolationOk = gtkAbuseLog.any { it.message.contains("No echo reply received", ignoreCase = true) }
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            if (isolationBroken || isolationOk) {
                                                Text(
                                                    text = if (isolationBroken) stringResource(R.string.label_gtk_isolation_broken) else stringResource(R.string.label_gtk_isolation_ok),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isolationBroken) errorColor() else successColor()
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 160.dp)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                gtkAbuseLog.forEach { status ->
                                                    Text(
                                                        text = status.message,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        item {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_packet_capture),
                                description = stringResource(R.string.msg_packet_capture_hint),
                                isRunning = activePacketCaptureMode != null,
                                startEnabled = isConnected,
                                icon = Icons.Default.Podcasts,
                                onStart = { showPacketCaptureDialog = true },
                                onStop = {
                                    viewModel.stopPacketCapture()
                                    activePacketCaptureMode = null
                                }
                            )
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
                isSaeFloodRunning = false
                isDhcpStarveRunning = false
                isListenProbesRunning = false
            }
        )
    }

    if (showPacketCaptureDialog) {
        PacketCaptureDialog(
            bleCapability = deviceInfo.resolve(GhostResponse.DeviceFeature.BLE),
            ieee802154Capability = deviceInfo.resolve(GhostResponse.DeviceFeature.IEEE802154),
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
            Text(stringResource(R.string.msg_connect_for_info))
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
        modifier = Modifier.fillMaxWidth(),
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
                    text = if (isScanningStations) stringResource(R.string.label_station_scan_active) else stringResource(R.string.label_active_attacks),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isScanningStations) warningColor() else errorColor(),
                    fontWeight = FontWeight.Bold
                )
                val activities = mutableListOf<String>()
                if (deauthIndex != null) activities.add(stringResource(R.string.label_deauth_ap, deauthIndex))
                if (isBeaconSpamming) activities.add(stringResource(R.string.label_beacon_spam))
                if (isKarmaRunning) activities.add(stringResource(R.string.label_karma))
                if (isRickRolling) activities.add(stringResource(R.string.label_rick_roll))
                if (isScanningStations) activities.add(stringResource(R.string.msg_scanning_stations))
                Text(
                    text = activities.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onStopAll) {
                Text(stringResource(R.string.action_stop_all), color = if (isScanningStations) warningColor() else errorColor(), style = MaterialTheme.typography.labelMedium)
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
        SerialManager.ConnectionState.CONNECTED -> stringResource(R.string.wifi_status_connected_to, deviceName ?: "")
        SerialManager.ConnectionState.CONNECTING -> stringResource(R.string.status_connecting)
        SerialManager.ConnectionState.ERROR -> stringResource(R.string.status_error)
        SerialManager.ConnectionState.DISCONNECTED -> stringResource(R.string.status_disconnected)
    }
    
    val subtitleText = when (connectionState) {
        SerialManager.ConnectionState.CONNECTED -> when (connectionTransport) {
            SerialManager.ConnectionTransport.USB -> stringResource(R.string.wifi_status_ready_usb)
            SerialManager.ConnectionTransport.BLE -> stringResource(R.string.wifi_status_ready_wireless)
            SerialManager.ConnectionTransport.NONE -> stringResource(R.string.status_connected)
        }
        SerialManager.ConnectionState.CONNECTING -> stringResource(R.string.msg_please_wait)
        SerialManager.ConnectionState.ERROR -> stringResource(R.string.msg_retry_connection)
        SerialManager.ConnectionState.DISCONNECTED -> stringResource(R.string.msg_tap_to_connect)
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
                    Text(stringResource(R.string.action_connect), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
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
                contentDescription = stringResource(R.string.label_signal_quality),
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
                            contentDescription = stringResource(R.string.label_active_attacks),
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
                            text = stringResource(R.string.label_open_network),
                            backgroundColor = tertiaryColor().copy(alpha = 0.1f),
                            borderColor = tertiaryColor().copy(alpha = 0.3f),
                            textColor = tertiaryColor()
                        )
                    } else {
                        val securityLabel = when (accessPoint.security) {
                            "WPA3" -> stringResource(R.string.label_wpa3)
                            "WPA2" -> stringResource(R.string.label_wpa2)
                            else -> accessPoint.security
                        }
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.desc_secured),
                            modifier = Modifier.size(12.dp),
                            tint = securityColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = securityLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = securityColor
                        )
                    }
                    
                    Text(
                        text = "  ${stringResource(R.string.label_ch_prefix, accessPoint.channel)}",
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
                contentDescription = stringResource(R.string.desc_view_details),
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
        accessPoint.rssi >= -50 -> stringResource(R.string.label_excellent)
        accessPoint.rssi >= -60 -> stringResource(R.string.label_good)
        accessPoint.rssi >= -70 -> stringResource(R.string.label_fair)
        else -> stringResource(R.string.label_weak)
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
                                contentDescription = null,
                                tint = successColor(),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.status_connected),
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
                    text = stringResource(R.string.label_ip_prefix, connectedIp),
                    style = MaterialTheme.typography.bodySmall,
                    color = successColor()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Details Grid
            DetailRow(stringResource(R.string.label_bssid), accessPoint.bssid.censorMac(privacyMode))
            DetailRow(stringResource(R.string.label_channel), accessPoint.channel.toString())
            DetailRow(stringResource(R.string.label_security), accessPoint.security)
            DetailRow(stringResource(R.string.label_signal), "${accessPoint.rssi} dBm ($signalText)", signalColor)
            accessPoint.vendor?.let { DetailRow(stringResource(R.string.label_vendor), it) }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BrutalistOutlinedButton(
                    text = stringResource(R.string.action_view_options),
                    onClick = onSelect,
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) }
                )
                BrutalistOutlinedButton(
                    text = stringResource(R.string.action_track),
                    onClick = onTrack,
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Deauth toggle button
            BrutalistButton(
                text = if (isAttacking) stringResource(R.string.action_stop_deauth) else stringResource(R.string.action_start_deauth),
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
                text = stringResource(R.string.action_more_attack_options),
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
                    text = if (isActive) stringResource(R.string.label_stop) else stringResource(R.string.label_start),
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
 * Shared target-AP selector shown once above the Attacks tab row list. Every attack row that
 * operates on the firmware's `select -a` selection (deauth, EAPOL, SAE flood, WPA3 check,
 * channel switch, GTK abuse) reads this single target instead of picking its own.
 */
@Composable
private fun TargetApSelectorCard(
    accessPoints: List<AccessPointPreview>,
    privacyMode: Boolean,
    selectedIndex: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    val selectedAp = accessPoints.find { it.index == selectedIndex }
    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.label_select_target_ap),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (selectedAp != null) {
                stringResource(R.string.msg_target_ap_selected, selectedAp.ssid.censorSsid(privacyMode), selectedAp.rssi)
            } else {
                stringResource(R.string.msg_no_ap_selected)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        ApTargetPicker(
            accessPoints = accessPoints,
            privacyMode = privacyMode,
            selectedIndex = selectedIndex,
            enabled = enabled,
            onSelect = onSelect
        )
    }
}

/**
 * Hint shown inside an attack row's expanded content when no shared target AP is selected yet.
 */
@Composable
private fun TargetApHint() {
    Text(
        text = stringResource(R.string.msg_select_target_ap_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Compact target-AP dropdown used inside attack launcher rows (deauth, EAPOL capture).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApTargetPicker(
    accessPoints: List<AccessPointPreview>,
    privacyMode: Boolean,
    selectedIndex: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAp = accessPoints.find { it.index == selectedIndex }
    val label = selectedAp?.ssid?.censorSsid(privacyMode) ?: stringResource(R.string.msg_no_ap_selected)

    ExposedDropdownMenuBox(
        expanded = expanded && accessPoints.isNotEmpty(),
        onExpandedChange = { if (accessPoints.isNotEmpty()) expanded = it }
    ) {
        OutlinedButton(
            onClick = { if (accessPoints.isNotEmpty()) expanded = true },
            enabled = enabled && accessPoints.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        ) {
            Text(label, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        ExposedDropdownMenu(
            expanded = expanded && accessPoints.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            accessPoints.forEach { ap ->
                DropdownMenuItem(
                    text = { Text(ap.ssid.censorSsid(privacyMode)) },
                    onClick = {
                        onSelect(ap.index)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Compact "N found" count plus a scrollable list of raw hit lines, used for the
 * scan-type attack launcher rows (Flock, NetBIOS, HTTP banner, SNMP, Enum, PineAp).
 */
@Composable
private fun ScanFindingsList(
    count: Int,
    lines: List<String>,
    modifier: Modifier = Modifier
) {
    if (count == 0 && lines.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.label_scan_findings_count, count),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (lines.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
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
    multiSelectMode: Boolean = false,
    isSelected: Boolean = false,
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
        isSelected -> primaryColor()
        isCurrentConnection -> successColor()
        isAttacking -> errorColor()
        else -> MaterialTheme.colorScheme.outline
    }
    val backgroundColor = when {
        isSelected -> primaryColor().copy(alpha = 0.1f)
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
            borderWidth = if (isAttacking || isSelected) 2.dp else 1.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (multiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // WiFi Icon
                Icon(
                    imageVector = Icons.Default.SignalWifi4Bar,
                    contentDescription = stringResource(R.string.desc_signal_strength),
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
                                contentDescription = stringResource(R.string.desc_attacking),
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
                                text = stringResource(R.string.label_open_network),
                                backgroundColor = tertiaryColor().copy(alpha = 0.1f),
                                borderColor = tertiaryColor().copy(alpha = 0.3f),
                                textColor = tertiaryColor()
                            )
                        } else {
                            val securityLabel = when (accessPoint.security) {
                                "WPA3" -> stringResource(R.string.label_wpa3)
                                "WPA2" -> stringResource(R.string.label_wpa2)
                                else -> accessPoint.security
                            }
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.desc_secured),
                                modifier = Modifier.size(12.dp),
                                tint = securityColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = securityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = securityColor
                            )
                        }
                        
                        Text(
                            text = "  ${stringResource(R.string.label_ch_prefix, accessPoint.channel)}",
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
                    contentDescription = stringResource(R.string.desc_view_details),
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
 * Bulk action bar shown while APs are multi-selected.
 *
 * Firmware's `select -a i,j,k` populates a genuine multi-target list that deauth
 * loops over (see deauth_attack.c), so this is the one bulk attack worth exposing here.
 */
@Composable
private fun ApBulkActionBar(
    selectedCount: Int,
    enabled: Boolean,
    onDeauthSelected: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = errorColor().copy(alpha = 0.1f),
        border = BorderStroke(1.dp, errorColor())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.label_selected_count, selectedCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
            }
            BrutalistButton(
                text = stringResource(R.string.action_deauth_selected),
                onClick = onDeauthSelected,
                containerColor = errorColor(),
                borderColor = errorColor(),
                enabled = enabled,
                leadingIcon = { Icon(Icons.Default.WifiOff, contentDescription = null) }
            )
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
                        contentDescription = stringResource(R.string.desc_station),
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
                contentDescription = stringResource(R.string.desc_station),
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
                text = stringResource(R.string.title_attack_options),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = stringResource(R.string.label_target, accessPoint.ssid.censorSsid(privacyMode)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Deauth Attack
            AttackOptionItem(
                title = stringResource(R.string.label_deauth_attack),
                description = stringResource(R.string.desc_deauth_attack),
                isActive = activeDeauthIndex == accessPoint.index,
                icon = Icons.Default.WifiOff,
                onClick = { onDeauth(accessPoint.index) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Beacon Spam
            AttackOptionItem(
                title = stringResource(R.string.label_beacon_spam),
                description = stringResource(R.string.desc_beacon_spam),
                isActive = isBeaconSpamming,
                icon = Icons.Default.Router,
                onClick = onBeaconSpam
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Karma Attack
            AttackOptionItem(
                title = stringResource(R.string.label_karma),
                description = stringResource(R.string.desc_karma_attack),
                isActive = isKarmaRunning,
                icon = Icons.Default.Phishing,
                onClick = onKarma
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Rick Roll
            AttackOptionItem(
                title = stringResource(R.string.label_rick_roll),
                description = stringResource(R.string.desc_rick_roll),
                isActive = isRickRolling,
                icon = Icons.Default.MusicNote,
                onClick = onRickRoll
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Stop All Button
            BrutalistOutlinedButton(
                text = stringResource(R.string.action_stop_all_attacks),
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
 * Packet capture mode picker backed by firmware capture commands.
 */
@Composable
private fun PacketCaptureDialog(
    bleCapability: GhostResponse.CapabilityResolution,
    ieee802154Capability: GhostResponse.CapabilityResolution,
    onDismiss: () -> Unit,
    onStart: (GhostCommand.CaptureMode, Int?) -> Unit,
    onStop: () -> Unit
) {
    val modes = listOf(
        GhostCommand.CaptureMode.EAPOL to stringResource(R.string.capture_mode_eapol),
        GhostCommand.CaptureMode.PROBE to stringResource(R.string.capture_mode_probe),
        GhostCommand.CaptureMode.DEAUTH to stringResource(R.string.capture_mode_deauth),
        GhostCommand.CaptureMode.BEACON to stringResource(R.string.capture_mode_beacon),
        GhostCommand.CaptureMode.RAW to stringResource(R.string.capture_mode_raw),
        GhostCommand.CaptureMode.WPS to stringResource(R.string.capture_mode_wps),
        GhostCommand.CaptureMode.PWN to stringResource(R.string.capture_mode_pwn),
        GhostCommand.CaptureMode.BLE to stringResource(R.string.capture_mode_ble),
        GhostCommand.CaptureMode.SKIMMER to stringResource(R.string.capture_mode_skimmer),
        GhostCommand.CaptureMode.IEEE802154 to stringResource(R.string.capture_mode_802154)
    )
    var selectedMode by remember { mutableStateOf(GhostCommand.CaptureMode.EAPOL) }
    var modeExpanded by remember { mutableStateOf(false) }
    var selectedQuickChannel by remember { mutableStateOf<Int?>(null) }
    var customChannel by remember { mutableStateOf("") }
    val selectedModeLabel = modes.first { it.first == selectedMode }.second
    val customChannelValue = customChannel.trim().toIntOrNull()
    val selectedChannel = customChannelValue ?: selectedQuickChannel
    val channelValid = customChannel.isBlank() || (customChannelValue != null && customChannelValue > 0)
    fun isModeUsable(mode: GhostCommand.CaptureMode): Boolean = when (mode) {
        GhostCommand.CaptureMode.BLE -> bleCapability.isUsable
        GhostCommand.CaptureMode.IEEE802154 -> ieee802154Capability.isUsable
        else -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_packet_capture)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.msg_packet_capture_hint),
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
                                text = { Text(if (isModeUsable(mode)) label else "$label (unsupported)") },
                                enabled = isModeUsable(mode),
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
                            label = { Text(channel?.let { "${stringResource(R.string.label_channel)} $it" } ?: stringResource(R.string.label_auto)) }
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
                    label = { Text(stringResource(R.string.label_custom_channel)) },
                    placeholder = { Text(stringResource(R.string.placeholder_custom_channel)) },
                    isError = !channelValid,
                    supportingText = {
                        Text(if (channelValid) stringResource(R.string.msg_custom_channel_hint) else stringResource(R.string.msg_custom_channel_error))
                    }
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onStop) {
                    Text(stringResource(R.string.action_stop))
                }
                Button(
                    enabled = channelValid && isModeUsable(selectedMode),
                    onClick = { onStart(selectedMode, selectedChannel) }
                ) {
                    Text(if (selectedChannel == null) stringResource(R.string.status_connecting).replace("…", "") else stringResource(R.string.action_start_capture_channel, selectedChannel))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
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
                    contentDescription = stringResource(R.string.desc_station),
                    tint = warningColor(),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.title_station_details),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Details Grid
            DetailRow(stringResource(R.string.label_mac_address), station.mac.censorMac(privacyMode))
            station.vendor?.let { DetailRow(stringResource(R.string.label_vendor), it) }
            station.associatedApSsid?.let { DetailRow(stringResource(R.string.label_connected_ap), it) }
            station.apBssid?.let { DetailRow(stringResource(R.string.label_ap_bssid), it.censorMac(privacyMode)) }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Deauth button
            BrutalistButton(
                text = stringResource(R.string.action_send_deauth_attack),
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

