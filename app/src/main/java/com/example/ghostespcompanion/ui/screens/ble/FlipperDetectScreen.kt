package com.example.ghostespcompanion.ui.screens.ble

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostCommand
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.utils.censorDevice
import com.example.ghostespcompanion.ui.utils.censorMac
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

/**
 * Flipper Detection Screen - Detect nearby Flipper Zero devices
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlipperDetectScreen(
    viewModel: MainViewModel,
    onNavigateToTrackFlipper: (Int) -> Unit,
    onBack: () -> Unit
) {
    var isScanning by remember { mutableStateOf(false) }
    var selectedFlipperDevice by remember { mutableStateOf<GhostResponse.FlipperDevice?>(null) }
    var showFlipperDetailSheet by remember { mutableStateOf(false) }
    
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionTransport by viewModel.connectionTransport.collectAsState()
    val flipperDevices by viewModel.flipperDevices.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    val privacyMode = appSettings.privacyMode
    
    // Start scan on launch if connected
    LaunchedEffect(Unit) {
        if (isConnected) {
            viewModel.clearFlipperDevices()
            isScanning = true
            viewModel.scanBle(GhostCommand.BleScanMode.FLIPPER)
        }
    }
    
    MainScreen(
        onBack = onBack,
        title = stringResource(R.string.title_flipper_detection),
        actions = {
            IconButton(onClick = {
                if (isConnected) {
                    viewModel.clearFlipperDevices()  // Clear previous results
                    isScanning = true
                    viewModel.scanBle(GhostCommand.BleScanMode.FLIPPER)
                }
            }) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
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
            // Connection Status Banner
            BleConnectionBanner(
                isConnected = isConnected,
                connectionTransport = connectionTransport,
                deviceName = stringResource(R.string.app_name_short),
                onConnect = { viewModel.connectFirstAvailable() }
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Scanning Status
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isScanning) primaryColor().copy(alpha = 0.1f) else SurfaceVariantDark
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = primaryColor(),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.msg_scanning_flippers),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceDark
                            )
                        } else {
                            Icon(
                                Icons.Default.BluetoothDisabled,
                                contentDescription = null,
                                tint = OnSurfaceVariantDark
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.msg_scan_complete_idle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceVariantDark
                            )
                        }
                    }
                }
                
                // Start/Stop Scan Button
                BrutalistButton(
                    text = if (isScanning) stringResource(R.string.action_stop_scan) else stringResource(R.string.action_scan),
                    onClick = {
                        if (isConnected) {
                            if (isScanning) {
                                viewModel.stopBleScan()
                                isScanning = false
                            } else {
                                viewModel.clearFlipperDevices()  // Clear previous results
                                viewModel.scanBle(GhostCommand.BleScanMode.FLIPPER)
                                isScanning = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (isScanning) errorColor() else primaryColor(),
                    enabled = isConnected,
                    leadingIcon = {
                        Icon(
                            if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                            contentDescription = null
                        )
                    }
                )
                
                // Detected Flippers List
                if (flipperDevices.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.label_detected_flippers, flipperDevices.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Warning
                    )
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(flipperDevices, key = { it.mac }) { flipper ->
                            FlipperDeviceCard(
                                flipper = flipper,
                                privacyMode = privacyMode,
                                onAction = {
                                    selectedFlipperDevice = flipper
                                    showFlipperDetailSheet = true
                                }
                            )
                        }
                    }
                } else if (!isScanning) {
                    // Empty state
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = SurfaceVariantDark.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.BluetoothSearching,
                                contentDescription = null,
                                tint = OnSurfaceVariantDark,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.msg_no_flippers_detected),
                                style = MaterialTheme.typography.bodyLarge,
                                color = OnSurfaceVariantDark
                            )
                            Text(
                                text = stringResource(R.string.msg_flipper_scan_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantDark
                            )
                        }
                    }
                }
                
                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceVariantDark.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = OnSurfaceVariantDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.msg_flipper_bt_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantDark
                        )
                    }
                }
            }
        }
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
 * Card displaying a detected Flipper device
 */
@Composable
private fun FlipperDeviceCard(
    flipper: GhostResponse.FlipperDevice,
    privacyMode: Boolean = false,
    onAction: () -> Unit
) {
    BrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = Warning
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DeveloperBoard,
                    contentDescription = null,
                    tint = Warning,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text = flipper.name ?: "${stringResource(R.string.label_flipper_zero)} (${flipper.flipperType})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                    Text(
                        text = flipper.mac.censorMac(privacyMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )
                    Text(
                        text = "${stringResource(R.string.label_signal)}: ${flipper.rssi} ${stringResource(R.string.label_dbm)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            flipper.rssi >= -50 -> successColor()
                            flipper.rssi >= -70 -> warningColor()
                            else -> errorColor()
                        }
                    )
                }
            }
            
            IconButton(onClick = onAction) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.title_actions),
                    tint = OnSurfaceVariantDark
                )
            }
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

/**
 * BLE Connection Banner component
 */
@Composable
private fun BleConnectionBanner(
    isConnected: Boolean,
    connectionTransport: SerialManager.ConnectionTransport,
    deviceName: String,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) successColor().copy(alpha = 0.1f) else errorColor().copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when {
                        !isConnected -> Icons.Default.BluetoothDisabled
                        connectionTransport == SerialManager.ConnectionTransport.BLE -> Icons.Default.BluetoothConnected
                        else -> Icons.Default.Usb
                    },
                    contentDescription = null,
                    tint = if (isConnected) successColor() else errorColor()
                )
                Column {
                    Text(
                        text = if (isConnected) stringResource(R.string.status_connected_device, deviceName) else stringResource(R.string.status_disconnected),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) successColor() else errorColor()
                    )
                    if (isConnected) {
                        Text(
                            text = stringResource(R.string.label_ready_to_scan),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariantDark
                        )
                    }
                }
            }
            
            if (!isConnected) {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor(),
                        contentColor = onPrimaryColor()
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.action_connect), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
