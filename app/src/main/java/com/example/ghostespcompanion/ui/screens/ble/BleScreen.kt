package com.example.ghostespcompanion.ui.screens.ble

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import com.example.ghostespcompanion.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ghostespcompanion.data.ble.BleBridgeDevice
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostCommand
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.components.ConnectionSelectionDialog
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
    onNavigateToTrackFlipper: (Int) -> Unit,
    viewModel: MainViewModel
) {
    var isScanning by remember { mutableStateOf(false) }
    var isSpamming by remember { mutableStateOf(false) }
    var selectedScanMode by remember { mutableStateOf(GhostCommand.BleScanMode.FLIPPER) }
    var selectedSpamMode by remember { mutableStateOf(GhostCommand.BleSpamMode.APPLE) }
    var showScanModeMenu by remember { mutableStateOf(false) }
    var showSpamModeMenu by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    val availableUsbDevices by viewModel.availableUsbDevices.collectAsState()
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
    val appSettings by viewModel.appSettings.collectAsState()
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

    val displayDevices = remember(bleDevices, flipperDevices, airTagDevices, gattDevices) {
        buildList {
            bleDevices.map {
                BleDevicePreview(
                    name = it.name ?: "Unknown",
                    mac = it.mac ?: it.getUniqueId(),
                    rssi = it.rssi,
                    deviceType = it.deviceType.name,
                    deviceCategory = BleDeviceCategory.GENERIC
                )
            }.also { addAll(it) }

            flipperDevices.map {
                BleDevicePreview(
                    name = it.name ?: "Flipper Zero",
                    mac = it.mac,
                    rssi = it.rssi,
                    deviceType = it.flipperType,
                    deviceCategory = BleDeviceCategory.FLIPPER,
                    index = it.index
                )
            }.also { addAll(it) }

            airTagDevices.map {
                BleDevicePreview(
                    name = "AirTag",
                    mac = it.mac,
                    rssi = it.rssi,
                    deviceType = "AirTag",
                    deviceCategory = BleDeviceCategory.AIRTAG
                )
            }.also { addAll(it) }

            gattDevices.map {
                BleDevicePreview(
                    name = it.name ?: "Unknown",
                    mac = it.mac,
                    rssi = it.rssi,
                    deviceType = it.type ?: "GATT",
                    deviceCategory = BleDeviceCategory.GATT,
                    index = it.index
                )
            }.also { addAll(it) }
        }.sortedByDescending { it.rssi }
    }
    
    MainScreen(
        title = "BLE",
        actions = {
            IconButton(onClick = onNavigateToFlipper) {
                Icon(
                    painter = painterResource(R.drawable.ic_dolphin),
                    contentDescription = "Flipper Detect",
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
                    bleDevices = availableBridgeDevices,
                    allUsbDevices = allUsbDevices,
                    usbDebugLog = usbDebugLog,
                    bluetoothEnabled = viewModel.isBluetoothEnabled(),
                    bluetoothSupported = viewModel.isBluetoothSupported(),
                    isBleScanning = isBleScanning,
                    startOnWirelessTab = true,
                    onUsbSelected = { device, baud ->
                        showDeviceDialog = false
                        viewModel.connectWithBaud(device, baud)
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
            
            // Scan Mode Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Scan Mode Dropdown
                ExposedDropdownMenuBox(
                    expanded = showScanModeMenu,
                    onExpandedChange = { showScanModeMenu = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedButton(
                        onClick = { showScanModeMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = isConnected && !isScanning
                    ) {
                        Icon(
                            imageVector = when (selectedScanMode) {
                                GhostCommand.BleScanMode.FLIPPER -> Icons.Default.DeveloperBoard
                                GhostCommand.BleScanMode.AIR_TAG -> Icons.Default.LocationOn
                                GhostCommand.BleScanMode.GATT -> Icons.Default.SettingsBluetooth
                                GhostCommand.BleScanMode.RAW -> Icons.Default.Radar
                                GhostCommand.BleScanMode.SPAM_DETECTOR -> Icons.Default.Shield
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = scanModeToString(selectedScanMode),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    
                    ExposedDropdownMenu(
                        expanded = showScanModeMenu,
                        onDismissRequest = { showScanModeMenu = false }
                    ) {
                        GhostCommand.BleScanMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(scanModeToString(mode)) },
                                onClick = {
                                    selectedScanMode = mode
                                    showScanModeMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (mode) {
                                            GhostCommand.BleScanMode.FLIPPER -> Icons.Default.DeveloperBoard
                                            GhostCommand.BleScanMode.AIR_TAG -> Icons.Default.LocationOn
                                            GhostCommand.BleScanMode.GATT -> Icons.Default.SettingsBluetooth
                                            GhostCommand.BleScanMode.RAW -> Icons.Default.Radar
                                            GhostCommand.BleScanMode.SPAM_DETECTOR -> Icons.Default.Shield
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
            
            // Scan Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Button(
                    onClick = { 
                        if (isConnected) {
                            isScanning = !isScanning
                            if (isScanning) {
                                when (selectedScanMode) {
                                    GhostCommand.BleScanMode.FLIPPER -> viewModel.clearFlipperDevices()
                                    GhostCommand.BleScanMode.AIR_TAG -> viewModel.clearAirTagDevices()
                                    GhostCommand.BleScanMode.GATT -> viewModel.clearGattDevices()
                                    else -> viewModel.clearBleDevices()
                                }
                                viewModel.scanBle(selectedScanMode)
                            } else {
                                viewModel.stopBleScan()
                            }
                        }
                    },
                    enabled = isConnected,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) errorColor() else primaryColor()
                    )
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop ${scanModeToString(selectedScanMode)} Scan")
                    } else {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start ${scanModeToString(selectedScanMode)} Scan")
                    }
                }
            }
            
            // BLE Spam Section
            Spacer(modifier = Modifier.height(16.dp))
            
            // Spam Mode Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BLE Spam Attack",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isSpamming) errorColor() else MaterialTheme.colorScheme.onSurface
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Spam Mode Dropdown
                ExposedDropdownMenuBox(
                    expanded = showSpamModeMenu,
                    onExpandedChange = { showSpamModeMenu = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedButton(
                        onClick = { showSpamModeMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = isConnected && !isSpamming,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isSpamming) errorColor() else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(spamModeToString(selectedSpamMode), maxLines = 1)
                        Spacer(modifier = Modifier.width(4.dp))
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
            
            // Spam Toggle Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Button(
                    onClick = {
                        if (isConnected) {
                            isSpamming = !isSpamming
                            if (isSpamming) {
                                viewModel.startBleSpam(selectedSpamMode)
                            } else {
                                viewModel.stopBleSpam()
                            }
                        }
                    },
                    enabled = isConnected,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSpamming) errorColor() else warningColor()
                    )
                ) {
                    Icon(
                        if (isSpamming) Icons.Default.Stop else Icons.Default.Warning,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSpamming) "Stop Spam Attack" else "Start Spam Attack")
                }
            }
            
            // Device list header
            Spacer(modifier = Modifier.height(8.dp))

            if (displayDevices.isNotEmpty()) {
                Text(
                    text = "Found ${displayDevices.size} devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = displayDevices,
                        key = { it.mac },
                        contentType = { it.deviceCategory.name }
                    ) { device ->
                        BleDeviceCard(
                            device = device,
                            privacyMode = privacyMode,
                            onClick = when (device.deviceCategory) {
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
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                            text = "No devices found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Select a scan mode and tap scan to discover BLE devices",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
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
                onNavigateToGattDetail(selectedGattDevice!!.index)
            },
            onTrack = {
                showGattDetailSheet = false
                onNavigateToTrackGatt(selectedGattDevice!!.index)
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
                onNavigateToTrackFlipper(selectedFlipperDevice!!.index)
            }
        )
    }
}

/**
 * Convert scan mode to display string
 */
private fun scanModeToString(mode: GhostCommand.BleScanMode): String = when (mode) {
    GhostCommand.BleScanMode.FLIPPER -> "Flipper Detect"
    GhostCommand.BleScanMode.SPAM_DETECTOR -> "Spam Detector"
    GhostCommand.BleScanMode.AIR_TAG -> "AirTag Scan"
    GhostCommand.BleScanMode.RAW -> "Raw Scan"
    GhostCommand.BleScanMode.GATT -> "GATT Scan"
}

/**
 * Convert spam mode to display string
 */
private fun spamModeToString(mode: GhostCommand.BleSpamMode): String = when (mode) {
    GhostCommand.BleSpamMode.APPLE -> "Apple (iOS Popup)"
    GhostCommand.BleSpamMode.MICROSOFT -> "Microsoft (Swift Pair)"
    GhostCommand.BleSpamMode.SAMSUNG -> "Samsung (Easy Setup)"
    GhostCommand.BleSpamMode.GOOGLE -> "Google (Fast Pair)"
    GhostCommand.BleSpamMode.RANDOM -> "Random (All Types)"
    GhostCommand.BleSpamMode.STOP -> "Stop"
}

/**
 * Device category for display
 */
enum class BleDeviceCategory {
    GENERIC, FLIPPER, AIRTAG, GATT
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
        SerialManager.ConnectionState.CONNECTED -> "Device Connected"
        SerialManager.ConnectionState.CONNECTING -> "Connecting..."
        SerialManager.ConnectionState.ERROR -> "Connection Error"
        SerialManager.ConnectionState.DISCONNECTED -> "Not Connected"
    }
    
    val subtitleText = when (connectionState) {
        SerialManager.ConnectionState.CONNECTED -> when (connectionTransport) {
            SerialManager.ConnectionTransport.USB -> "Ready via USB"
            SerialManager.ConnectionTransport.BLE -> "Ready via wireless bridge"
            SerialManager.ConnectionTransport.NONE -> "Ready for BLE operations"
        }
        SerialManager.ConnectionState.CONNECTING -> "Please wait..."
        SerialManager.ConnectionState.ERROR -> "Tap to retry connection"
        SerialManager.ConnectionState.DISCONNECTED -> "Connect GhostESP to continue"
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
        title = { Text("Select BLE Bridge") },
        text = {
            if (devices.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (isScanning) "Scanning for GhostESP BLE bridges..." else "No BLE bridges found yet.")
                    OutlinedButton(onClick = onRefresh) {
                        Text(if (isScanning) "Scanning..." else "Scan Again")
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
                                    text = "${device.address} • RSSI ${device.rssi} dBm",
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
                Text("Cancel")
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
                            Text(
                                text = " • $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = when (device.deviceCategory) {
                                    BleDeviceCategory.FLIPPER -> primaryColor()
                                    BleDeviceCategory.AIRTAG -> warningColor()
                                    BleDeviceCategory.GATT -> primaryColor()
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                // Signal strength
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${device.rssi} dBm",
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
                        contentDescription = "View details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Preview data class for BLE device
 */
data class BleDevicePreview(
    val name: String,
    val mac: String,
    val rssi: Int,
    val deviceType: String?,
    val deviceCategory: BleDeviceCategory = BleDeviceCategory.GENERIC,
    val index: Int = -1
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
        device.rssi >= -60 -> "Excellent"
        device.rssi >= -80 -> "Good"
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
                        text = (device.name ?: "Unknown").censorDevice(privacyMode),
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
                        text = "Signal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${device.rssi} dBm ($signalText)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = signalColor
                    )
                }
                device.type?.let { type ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Type",
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
                    Text("View Services")
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
                    Text("Track")
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
        device.rssi >= -60 -> "Excellent"
        device.rssi >= -80 -> "Good"
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
                        text = (device.name ?: "Flipper Zero").censorDevice(privacyMode),
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
                        text = "Signal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${device.rssi} dBm ($signalText)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = signalColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Type",
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
                Text("Track Flipper")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
