package com.example.ghostespcompanion.ui.screens.ble

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.ble.BleBridgeDevice
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostCommand
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.utils.censorDevice
import com.example.ghostespcompanion.ui.utils.censorMac
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

/**
 * BLE Screen
 * 
 * Main Bluetooth Low Energy feature screen with:
 * - Device scanning (multiple modes: Default, Flipper, AirTag, GATT, Raw, Spam Detector)
 * - Flipper Zero detection
 * - AirTag tools
 * - BLE spam attacks
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScreen(
    onNavigateToFlipper: () -> Unit,
    onNavigateToGattDetail: (Int) -> Unit,
    onNavigateToTrackGatt: (Int) -> Unit,
    onNavigateToGps: () -> Unit,
    onNavigateToTrackFlipper: (Int) -> Unit,
    viewModel: MainViewModel
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isScanning by remember { mutableStateOf(false) }
    var isSpamming by remember { mutableStateOf(false) }
    var selectedScanUiMode by remember { mutableStateOf(BleScanUiMode.FLIPPER) }
    var selectedSpamMode by remember { mutableStateOf(GhostCommand.BleSpamMode.APPLE) }
    var showScanModeMenu by remember { mutableStateOf(false) }
    var showSpamModeMenu by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var advFilterOui by remember { mutableStateOf("") }
    var advFilterVendor by remember { mutableStateOf("") }
    var isAdvScanning by remember { mutableStateOf(false) }
    var isBleWardriving by remember { mutableStateOf(false) }
    var isSpoofingAirTag by remember { mutableStateOf(false) }
    val availableUsbDevices by viewModel.availableUsbDevices.collectAsState()
    val usbPortCounts by viewModel.usbPortCounts.collectAsState()
    val allUsbDevices by viewModel.allUsbDevices.collectAsState()
    val usbDebugLog by viewModel.usbDebugLog.collectAsState()
    val availableBridgeDevices by viewModel.availableBleDevices.collectAsState()
    val isBleScanning by viewModel.isBleScanning.collectAsState()
    var showGattDetailSheet by remember { mutableStateOf(false) }
    var selectedGattDevice by remember { mutableStateOf<GhostResponse.GattDevice?>(null) }
    var showFlipperDetailSheet by remember { mutableStateOf(false) }
    var selectedFlipperDevice by remember { mutableStateOf<GhostResponse.FlipperDevice?>(null) }

    // Collect state from ViewModel
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionTransport by viewModel.connectionTransport.collectAsState()
    val bleDevices by viewModel.bleDevices.collectAsState()
    val flipperDevices by viewModel.flipperDevices.collectAsState()
    val airTagDevices by viewModel.airTagDevices.collectAsState()
    val gattDevices by viewModel.gattDevices.collectAsState()
    val advertiserDevices by viewModel.advertiserDevices.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    val bleCapability = deviceInfo.resolve(GhostResponse.DeviceFeature.BLE)
    val bleUsable = bleCapability.isUsable
    val privacyMode = appSettings.privacyMode
    val context = LocalContext.current
    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            viewModel.startBleBridgeScan()
        }
    }

    // Stop BLE scan and spam when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopBleScan()
            if (isSpamming) {
                viewModel.stopBleSpam()
            }
            if (isAdvScanning) {
                viewModel.stopBleScan()
            }
            if (isBleWardriving) {
                viewModel.stopBleWardrive()
            }
            if (isSpoofingAirTag) {
                viewModel.spoofAirTag(false)
            }
        }
    }

    val requestBleConnect: () -> Unit = {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!viewModel.isBluetoothSupported() || !viewModel.isBluetoothEnabled()) {
            showDeviceDialog = true
        } else if (allGranted) {
            viewModel.startBleBridgeScan()
            showDeviceDialog = true
        } else {
            showDeviceDialog = true
            blePermissionLauncher.launch(permissions)
        }
    }

    val displayDevices = remember(bleDevices, flipperDevices, airTagDevices, gattDevices, advertiserDevices) {
        val unknownStr = context.getString(R.string.label_unknown)
        val flipperStr = context.getString(R.string.label_flipper_zero)
        val airtagStr = context.getString(R.string.label_airtag)
        val gattStr = context.getString(R.string.label_gatt)

        buildList {
            bleDevices.map {
                BleDevicePreview(
                    name = it.name ?: unknownStr,
                    mac = it.mac ?: it.getUniqueId(),
                    rssi = it.rssi,
                    deviceType = it.deviceType.name,
                    deviceCategory = BleDeviceCategory.GENERIC
                )
            }.also { addAll(it) }

            flipperDevices.map {
                BleDevicePreview(
                    name = it.name ?: flipperStr,
                    mac = it.mac,
                    rssi = it.rssi,
                    deviceType = it.flipperType,
                    deviceCategory = BleDeviceCategory.FLIPPER,
                    index = it.index
                )
            }.also { addAll(it) }

            airTagDevices.map {
                BleDevicePreview(
                    name = airtagStr,
                    mac = it.mac,
                    rssi = it.rssi,
                    deviceType = airtagStr,
                    deviceCategory = BleDeviceCategory.AIRTAG
                )
            }.also { addAll(it) }

            gattDevices.map {
                BleDevicePreview(
                    name = it.name ?: unknownStr,
                    mac = it.mac,
                    rssi = it.rssi,
                    deviceType = it.type ?: gattStr,
                    deviceCategory = BleDeviceCategory.GATT,
                    index = it.index
                )
            }.also { addAll(it) }

            advertiserDevices.map { it.toPreview() }.also { addAll(it) }
        }.sortedByDescending { it.rssi }
    }

    val tabTitles = listOf(
        stringResource(R.string.tab_ble_scan),
        stringResource(R.string.tab_ble_spam)
    )

    MainScreen(
        title = stringResource(R.string.title_ble),
        actions = {
            IconButton(onClick = onNavigateToFlipper, enabled = bleUsable) {
                Icon(
                    painter = painterResource(R.drawable.ic_dolphin),
                    contentDescription = stringResource(R.string.ble_scan_mode_flipper),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CapabilityNotice(bleCapability, "Firmware BLE", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            // Connection Status Banner
            BleConnectionBanner(
                isConnected = isConnected,
                connectionState = connectionState,
                connectionTransport = connectionTransport,
                onConnect = {
                    requestBleConnect()
                }
            )
            
            // Device Selection Dialog
            if (showDeviceDialog) {
                ConnectionSelectionDialog(
                    usbDevices = availableUsbDevices,
                    usbPortCounts = usbPortCounts,
                    bleDevices = availableBridgeDevices,
                    allUsbDevices = allUsbDevices,
                    usbDebugLog = usbDebugLog,
                    bluetoothEnabled = viewModel.isBluetoothEnabled(),
                    bluetoothSupported = viewModel.isBluetoothSupported(),
                    isBleScanning = isBleScanning,
                    startOnWirelessTab = true,
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
                    onRefreshBle = { requestBleConnect() },
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

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
            when (selectedTab) {
                0 -> {
            item {
            // Scan card: unified mode selector (standard modes + Advertisers) + single start/stop
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                    BrutalistSectionHeader(title = stringResource(R.string.title_standard_scan))

                    val scanBusy = isScanning || isAdvScanning
                    val isAdvertiserMode = selectedScanUiMode == BleScanUiMode.ADVERTISER

                    ExposedDropdownMenuBox(
                        expanded = showScanModeMenu,
                        onExpandedChange = { showScanModeMenu = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { showScanModeMenu = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            enabled = isConnected && bleUsable && !scanBusy
                        ) {
                            Icon(
                                imageVector = scanUiModeIcon(selectedScanUiMode),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = scanUiModeToString(selectedScanUiMode),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        ExposedDropdownMenu(
                            expanded = showScanModeMenu,
                            onDismissRequest = { showScanModeMenu = false }
                        ) {
                            BleScanUiMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(scanUiModeToString(mode)) },
                                    onClick = {
                                        selectedScanUiMode = mode
                                        showScanModeMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = scanUiModeIcon(mode),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (isAdvertiserMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = advFilterOui,
                                onValueChange = { advFilterOui = it; advFilterVendor = "" },
                                label = { Text(stringResource(R.string.label_oui_prefix)) },
                                singleLine = true,
                                enabled = isConnected && bleUsable && !scanBusy,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = advFilterVendor,
                                onValueChange = { advFilterVendor = it; advFilterOui = "" },
                                label = { Text(stringResource(R.string.label_advertiser_vendor)) },
                                singleLine = true,
                                enabled = isConnected && bleUsable && !scanBusy,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (!isConnected || !bleUsable) return@Button
                                if (isAdvertiserMode) {
                                    isAdvScanning = !isAdvScanning
                                    if (isAdvScanning) {
                                        val filter = when {
                                            advFilterOui.isNotBlank() -> GhostCommand.BleAdvertiserFilter.Oui(advFilterOui.trim())
                                            advFilterVendor.isNotBlank() -> GhostCommand.BleAdvertiserFilter.Vendor(advFilterVendor.trim())
                                            else -> GhostCommand.BleAdvertiserFilter.All
                                        }
                                        viewModel.scanBleAdvertisers(filter)
                                    } else {
                                        viewModel.stopBleScan()
                                    }
                                } else {
                                    isScanning = !isScanning
                                    if (isScanning) {
                                        when (selectedScanUiMode) {
                                            BleScanUiMode.FLIPPER -> viewModel.clearFlipperDevices()
                                            BleScanUiMode.AIR_TAG -> viewModel.clearAirTagDevices()
                                            BleScanUiMode.GATT -> viewModel.clearGattDevices()
                                            else -> viewModel.clearBleDevices()
                                        }
                                        viewModel.scanBle(selectedScanUiMode.toBleScanMode()!!)
                                    } else {
                                        viewModel.stopBleScan()
                                    }
                                }
                            },
                            enabled = isConnected && bleUsable,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (scanBusy) errorColor() else primaryColor()
                            )
                        ) {
                            if (scanBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_stop_scan_mode, scanUiModeToString(selectedScanUiMode)))
                            } else {
                                Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_start_scan_mode, scanUiModeToString(selectedScanUiMode)))
                            }
                        }

                        if (isAdvertiserMode) {
                            IconButton(
                                onClick = { if (isConnected && bleUsable) viewModel.listAdvertisers() },
                                enabled = isConnected && bleUsable
                            ) {
                                Icon(Icons.Default.List, contentDescription = stringResource(R.string.action_list_results))
                            }
                        }
                    }
                }
            }
            }

            item {
                // Combined device list header
                Spacer(modifier = Modifier.height(8.dp))
                if (displayDevices.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.msg_found_devices_count, displayDevices.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (displayDevices.isNotEmpty()) {
                items(
                    items = displayDevices,
                    key = { "${it.deviceCategory}_${it.mac}" },
                    contentType = { it.deviceCategory.name }
                ) { device ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        BleDeviceCard(
                            device = device,
                            privacyMode = privacyMode,
                            onClick = if (!bleUsable) null else when (device.deviceCategory) {
                                BleDeviceCategory.GATT -> {
                                    val gattDevice = gattDevices.find { it.mac == device.mac }
                                    if (gattDevice != null) {
                                        { selectedGattDevice = gattDevice; showGattDetailSheet = true }
                                    } else null
                                }
                                BleDeviceCategory.FLIPPER -> {
                                    val flipperDevice = flipperDevices.find { it.mac == device.mac }
                                    if (flipperDevice != null) {
                                        { selectedFlipperDevice = flipperDevice; showFlipperDetailSheet = true }
                                    } else null
                                }
                                else -> null
                            }
                        )
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Icon(
                                Icons.Default.BluetoothDisabled,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.msg_no_ble_devices),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.msg_ble_scan_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
                }

                1 -> {
                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_ble_spam_attack),
                                description = stringResource(R.string.desc_ble_spam),
                                isRunning = isSpamming,
                                startEnabled = isConnected && bleUsable,
                                icon = Icons.Default.Warning,
                                onStart = {
                                    viewModel.startBleSpam(selectedSpamMode)
                                    isSpamming = true
                                },
                                onStop = {
                                    viewModel.stopBleSpam()
                                    isSpamming = false
                                },
                                expandedContent = {
                                    ExposedDropdownMenuBox(
                                        expanded = showSpamModeMenu,
                                        onExpandedChange = { showSpamModeMenu = it }
                                    ) {
                                        OutlinedButton(
                                            onClick = { showSpamModeMenu = true },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(),
                                            enabled = isConnected && bleUsable && !isSpamming
                                        ) {
                                            Text(spamModeToString(selectedSpamMode), modifier = Modifier.weight(1f), maxLines = 1)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }

                                        ExposedDropdownMenu(
                                            expanded = showSpamModeMenu,
                                            onDismissRequest = { showSpamModeMenu = false }
                                        ) {
                                            listOf(
                                                GhostCommand.BleSpamMode.APPLE,
                                                GhostCommand.BleSpamMode.MICROSOFT,
                                                GhostCommand.BleSpamMode.SAMSUNG,
                                                GhostCommand.BleSpamMode.GOOGLE,
                                                GhostCommand.BleSpamMode.RANDOM
                                            ).forEach { mode ->
                                                DropdownMenuItem(
                                                    text = { Text(spamModeToString(mode)) },
                                                    onClick = {
                                                        selectedSpamMode = mode
                                                        showSpamModeMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            BrutalistCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onNavigateToGps
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Radar, contentDescription = null, tint = primaryColor())
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.label_ble_wardrive),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stringResource(R.string.desc_ble_wardrive_gps_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            AttackLauncherRow(
                                title = stringResource(R.string.label_spoof_airtag),
                                description = stringResource(R.string.desc_spoof_airtag),
                                isRunning = isSpoofingAirTag,
                                startEnabled = isConnected && bleUsable,
                                icon = Icons.Default.LocationOn,
                                onStart = {
                                    viewModel.spoofAirTag(true)
                                    isSpoofingAirTag = true
                                },
                                onStop = {
                                    viewModel.spoofAirTag(false)
                                    isSpoofingAirTag = false
                                }
                            )
                        }
                    }
                }
            }
            }
        }
    }

    // GATT Device Detail Bottom Sheet
    if (showGattDetailSheet && selectedGattDevice != null) {
        GattDeviceDetailSheet(
            device = selectedGattDevice!!,
            privacyMode = privacyMode,
            onDismiss = { showGattDetailSheet = false },
            onViewServices = {
                showGattDetailSheet = false
                if (bleUsable) onNavigateToGattDetail(selectedGattDevice!!.index)
            },
            onTrack = {
                showGattDetailSheet = false
                if (bleUsable) onNavigateToTrackGatt(selectedGattDevice!!.index)
            }
        )
    }

    // Flipper Device Detail Bottom Sheet
    if (showFlipperDetailSheet && selectedFlipperDevice != null) {
        FlipperDeviceDetailSheet(
            device = selectedFlipperDevice!!,
            privacyMode = privacyMode,
            onDismiss = { showFlipperDetailSheet = false },
            onTrack = {
                showFlipperDetailSheet = false
                if (bleUsable) onNavigateToTrackFlipper(selectedFlipperDevice!!.index)
            }
        )
    }
}

/**
 * Unified scan mode covering both standard blescan modes and BLE advertiser scanning,
 * so both can share one mode selector, one start/stop button, and one results list.
 */
private enum class BleScanUiMode {
    FLIPPER, AIR_TAG, GATT, RAW, SPAM_DETECTOR, ADVERTISER
}

private fun BleScanUiMode.toBleScanMode(): GhostCommand.BleScanMode? = when (this) {
    BleScanUiMode.FLIPPER -> GhostCommand.BleScanMode.FLIPPER
    BleScanUiMode.AIR_TAG -> GhostCommand.BleScanMode.AIR_TAG
    BleScanUiMode.GATT -> GhostCommand.BleScanMode.GATT
    BleScanUiMode.RAW -> GhostCommand.BleScanMode.RAW
    BleScanUiMode.SPAM_DETECTOR -> GhostCommand.BleScanMode.SPAM_DETECTOR
    BleScanUiMode.ADVERTISER -> null
}

private fun scanUiModeIcon(mode: BleScanUiMode): ImageVector = when (mode) {
    BleScanUiMode.FLIPPER -> Icons.Default.DeveloperBoard
    BleScanUiMode.AIR_TAG -> Icons.Default.LocationOn
    BleScanUiMode.GATT -> Icons.Default.SettingsBluetooth
    BleScanUiMode.RAW -> Icons.Default.Radar
    BleScanUiMode.SPAM_DETECTOR -> Icons.Default.Shield
    BleScanUiMode.ADVERTISER -> Icons.Default.Podcasts
}

/**
 * Convert scan mode to display string
 */
@Composable
private fun scanUiModeToString(mode: BleScanUiMode): String = when (mode) {
    BleScanUiMode.FLIPPER -> stringResource(R.string.ble_scan_mode_flipper)
    BleScanUiMode.SPAM_DETECTOR -> stringResource(R.string.ble_scan_mode_spam)
    BleScanUiMode.AIR_TAG -> stringResource(R.string.ble_scan_mode_airtag)
    BleScanUiMode.RAW -> stringResource(R.string.ble_scan_mode_raw)
    BleScanUiMode.GATT -> stringResource(R.string.ble_scan_mode_gatt)
    BleScanUiMode.ADVERTISER -> stringResource(R.string.title_advertiser_scan)
}

/**
 * Convert spam mode to display string
 */
@Composable
private fun spamModeToString(mode: GhostCommand.BleSpamMode): String = when (mode) {
    GhostCommand.BleSpamMode.APPLE -> stringResource(R.string.ble_spam_mode_apple)
    GhostCommand.BleSpamMode.MICROSOFT -> stringResource(R.string.ble_spam_mode_microsoft)
    GhostCommand.BleSpamMode.SAMSUNG -> stringResource(R.string.ble_spam_mode_samsung)
    GhostCommand.BleSpamMode.GOOGLE -> stringResource(R.string.ble_spam_mode_google)
    GhostCommand.BleSpamMode.RANDOM -> stringResource(R.string.ble_spam_mode_random)
    GhostCommand.BleSpamMode.STOP -> stringResource(R.string.action_stop)
}

/**
 * Device category for display
 */
enum class BleDeviceCategory {
    GENERIC, FLIPPER, AIRTAG, GATT, ADVERTISER
}

/**
 * Map an [GhostResponse.AdvertiserDevice] to the shared [BleDevicePreview] shape so it
 * can reuse [BleDeviceCard]. Detail-only fields from the listadv block are carried
 * through so the card can render them.
 */
private fun GhostResponse.AdvertiserDevice.toPreview(): BleDevicePreview {
    val details = buildList {
        ouiVendor?.let { add("OUI $it") }
        manufacturer?.let { add("MFG $it") }
        services?.let { add("SVC $it") }
        proximity?.let { add("Proximity $it") }
        seenCount?.let { add("Seen $it") }
        addressType?.let { add("Addr ${it}") }
        txPower?.let { add("TX $it dBm") }
        ibeaconUuid?.let { add("UUID $it") }
        ibeaconMajor?.let { add("Major $it") }
        ibeaconMinor?.let { add("Minor $it") }
    }
    return BleDevicePreview(
        name = name ?: if (isIBeacon) "iBeacon" else "Advertiser",
        mac = mac,
        rssi = rssi,
        deviceType = if (isIBeacon) "iBeacon" else advType,
        deviceCategory = BleDeviceCategory.ADVERTISER,
        index = index,
        detailLines = details
    )
}

/**
 * BLE Connection Banner
 */
@Composable
private fun BleConnectionBanner(
    isConnected: Boolean,
    connectionState: SerialManager.ConnectionState,
    connectionTransport: SerialManager.ConnectionTransport,
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
        SerialManager.ConnectionState.CONNECTED -> stringResource(R.string.ble_status_connected_device)
        SerialManager.ConnectionState.CONNECTING -> stringResource(R.string.status_connecting)
        SerialManager.ConnectionState.ERROR -> stringResource(R.string.status_error)
        SerialManager.ConnectionState.DISCONNECTED -> stringResource(R.string.status_disconnected)
    }
    
    val subtitleText = when (connectionState) {
        SerialManager.ConnectionState.CONNECTED -> when (connectionTransport) {
            SerialManager.ConnectionTransport.USB -> stringResource(R.string.ble_ready_usb)
            SerialManager.ConnectionTransport.BLE -> stringResource(R.string.ble_ready_wireless)
            SerialManager.ConnectionTransport.NONE -> stringResource(R.string.ble_ready_operations)
        }
        SerialManager.ConnectionState.CONNECTING -> stringResource(R.string.msg_please_wait)
        SerialManager.ConnectionState.ERROR -> stringResource(R.string.msg_retry_connection)
        SerialManager.ConnectionState.DISCONNECTED -> stringResource(R.string.msg_connect_to_continue)
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

@Composable
private fun BleBridgeDialog(
    devices: List<BleBridgeDevice>,
    isScanning: Boolean,
    onRefresh: () -> Unit,
    onDeviceSelected: (BleBridgeDevice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_select_ble_bridge)) },
        text = {
            if (devices.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (isScanning) stringResource(R.string.msg_scanning_bridges) else stringResource(R.string.msg_no_bridges_found))
                    OutlinedButton(onClick = onRefresh) {
                        Text(if (isScanning) stringResource(R.string.status_connecting) else stringResource(R.string.action_scan))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = devices,
                        key = { it.address },
                        contentType = { "ble_bridge" }
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
                                    text = device.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${device.address} • ${stringResource(R.string.label_signal)} ${device.rssi} ${stringResource(R.string.label_dbm)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * BLE Device Card
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BleDeviceCard(
    device: BleDevicePreview,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val borderColor = when (device.deviceCategory) {
        BleDeviceCategory.FLIPPER -> primaryColor()
        BleDeviceCategory.AIRTAG -> warningColor()
        BleDeviceCategory.GATT -> primaryColor()
        BleDeviceCategory.ADVERTISER -> primaryColor()
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        enabled = onClick != null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(width = 1.dp, color = borderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device type icon
                if (device.deviceCategory == BleDeviceCategory.FLIPPER) {
                    Icon(
                        painter = painterResource(R.drawable.ic_dolphin),
                        contentDescription = null,
                        tint = primaryColor(),
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = when (device.deviceCategory) {
                            BleDeviceCategory.AIRTAG -> Icons.Default.LocationOn
                            BleDeviceCategory.GATT -> Icons.Default.SettingsBluetooth
                            BleDeviceCategory.ADVERTISER -> Icons.Default.Radar
                            else -> when (device.deviceType) {
                                "IPHONE" -> Icons.Default.PhoneIphone
                                "SAMSUNG" -> Icons.Default.Watch
                                "GOOGLE" -> Icons.Default.PhoneAndroid
                                else -> Icons.Default.Bluetooth
                            }
                        },
                        contentDescription = null,
                        tint = when (device.deviceCategory) {
                            BleDeviceCategory.AIRTAG -> warningColor()
                            BleDeviceCategory.GATT -> primaryColor()
                            BleDeviceCategory.ADVERTISER -> primaryColor()
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name.censorDevice(privacyMode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.mac.censorMac(privacyMode),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        device.deviceType?.let {
                            val typeLabel = when (it) {
                                "IPHONE" -> stringResource(R.string.label_iphone)
                                "SAMSUNG" -> stringResource(R.string.label_samsung)
                                "GOOGLE" -> stringResource(R.string.label_google)
                                else -> it
                            }
                            Text(
                                text = " • $typeLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = when (device.deviceCategory) {
                                    BleDeviceCategory.FLIPPER -> primaryColor()
                                    BleDeviceCategory.AIRTAG -> warningColor()
                                    BleDeviceCategory.GATT -> primaryColor()
                                    BleDeviceCategory.ADVERTISER -> primaryColor()
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                // Signal strength
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${device.rssi} ${stringResource(R.string.label_dbm)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            device.rssi >= -60 -> SignalExcellent
                            device.rssi >= -80 -> SignalGood
                            else -> SignalFair
                        }
                    )
                    // Signal strength indicator
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        repeat(4) { index ->
                            val barHeight = (6 + index * 3).dp
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(barHeight)
                                    .background(
                                        when {
                                            device.rssi >= -50 -> SignalExcellent
                                            device.rssi >= -60 && index < 3 -> SignalExcellent
                                            device.rssi >= -70 && index < 2 -> SignalGood
                                            device.rssi >= -80 && index < 1 -> SignalGood
                                            index == 0 -> SignalFair
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        }
                                    )
                            )
                        }
                    }
                }

                if (onClick != null && (device.deviceCategory == BleDeviceCategory.GATT || device.deviceCategory == BleDeviceCategory.FLIPPER)) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.action_view_details),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (device.detailLines.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    device.detailLines.forEach { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Preview data class for BLE device
 */
@Immutable
data class BleDevicePreview(
    val name: String,
    val mac: String,
    val rssi: Int,
    val deviceType: String?,
    val deviceCategory: BleDeviceCategory = BleDeviceCategory.GENERIC,
    val index: Int = -1,
    val detailLines: List<String> = emptyList()
)

/**
 * GATT Device Detail Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GattDeviceDetailSheet(
    device: GhostResponse.GattDevice,
    privacyMode: Boolean,
    onDismiss: () -> Unit,
    onViewServices: () -> Unit,
    onTrack: () -> Unit
) {
    val signalColor = when {
        device.rssi >= -60 -> SignalExcellent
        device.rssi >= -80 -> SignalGood
        else -> SignalFair
    }
    
    val signalText = when {
        device.rssi >= -60 -> stringResource(R.string.signal_excellent)
        device.rssi >= -80 -> stringResource(R.string.signal_good)
        else -> stringResource(R.string.signal_weak)
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
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.SettingsBluetooth,
                    contentDescription = null,
                    tint = primaryColor(),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = (device.name ?: stringResource(R.string.label_unknown)).censorDevice(privacyMode),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = device.mac.censorMac(privacyMode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.label_signal),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${device.rssi} ${stringResource(R.string.label_dbm)} ($signalText)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = signalColor
                    )
                }
                device.type?.let { type ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.label_type),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = type,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = primaryColor()
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onViewServices,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_view_services))
                }
                Button(
                    onClick = onTrack,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor()
                    )
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_track))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Flipper Device Detail Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlipperDeviceDetailSheet(
    device: GhostResponse.FlipperDevice,
    privacyMode: Boolean,
    onDismiss: () -> Unit,
    onTrack: () -> Unit
) {
    val signalColor = when {
        device.rssi >= -60 -> SignalExcellent
        device.rssi >= -80 -> SignalGood
        else -> SignalFair
    }

    val signalText = when {
        device.rssi >= -60 -> stringResource(R.string.signal_excellent)
        device.rssi >= -80 -> stringResource(R.string.signal_good)
        else -> stringResource(R.string.signal_weak)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DeveloperBoard,
                    contentDescription = null,
                    tint = primaryColor(),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = (device.name ?: stringResource(R.string.label_flipper_zero)).censorDevice(privacyMode),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = device.mac.censorMac(privacyMode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.label_signal),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${device.rssi} ${stringResource(R.string.label_dbm)} ($signalText)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = signalColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.label_type),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = device.flipperType,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = primaryColor()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onTrack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor())
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_track_flipper))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
