package com.example.ghostespcompanion.ui.screens.dashboard

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.utils.censorMac
import com.example.ghostespcompanion.ui.utils.censorSsid
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

/**
 * Dashboard Screen - Main overview screen
 * 
 * Shows connection status, device info, and quick stats for all modules.
 * Acts as the landing page when opening the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToWifi: () -> Unit,
    onNavigateToBle: () -> Unit,
    onNavigateToIr: () -> Unit,
    onNavigateToMore: () -> Unit,
    onNavigateToNfc: () -> Unit,
    onNavigateToGps: () -> Unit,
    onNavigateToBadUsb: () -> Unit,
    onNavigateToSd: () -> Unit,
    onScanWifiAndNavigate: () -> Unit,
    onScanBleAndNavigate: () -> Unit,
    onScanNfcAndNavigate: () -> Unit
) {
val connectionState by viewModel.connectionState.collectAsState()
    val connectionTransport by viewModel.connectionTransport.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val accessPoints by viewModel.accessPoints.collectAsState()
    val bleDevices by viewModel.bleDevices.collectAsState()
    val nfcTags by viewModel.nfcTags.collectAsState()
    val irRemotes by viewModel.irRemotes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    val privacyMode = appSettings.privacyMode
    
    var showDeviceDialog by remember { mutableStateOf(false) }
    val availableDevices by viewModel.availableUsbDevices.collectAsState()
    val availableBleDevices by viewModel.availableBleDevices.collectAsState()
    val isBleScanning by viewModel.isBleScanning.collectAsState()
    val allUsbDevices by viewModel.allUsbDevices.collectAsState()
    val usbDebugLog by viewModel.usbDebugLog.collectAsState()
    val context = LocalContext.current
    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            viewModel.startBleBridgeScan()
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

    val openConnectionDialog = {
        if (connectionState == SerialManager.ConnectionState.ERROR) {
            viewModel.forceDisconnect()
        }
        viewModel.refreshAvailableDevices()
        viewModel.refreshAllUsbDevices()
        showDeviceDialog = true
    }
    
    MainScreen(title = stringResource(R.string.title_dashboard)) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
// Connection Status Card
                item {
                    ConnectionStatusCard(
                        isConnected = isConnected,
                        connectionState = connectionState,
                        connectionTransport = connectionTransport,
                        isLoading = isLoading,
                        deviceInfo = deviceInfo,
                        onConnectClick = openConnectionDialog,
                        onDisconnectClick = { viewModel.disconnect() },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                
                // Quick Stats Section - Links to More menu items
            item {
                BrutalistSectionHeader(
                    title = stringResource(R.string.header_quick_links),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    accentColor = primaryColor()
                )
            }

            // Quick Links Grid - Navigate to More menu items
            item {
                QuickLinksGrid(
                    onWifiClick = onNavigateToWifi,
                    onBleClick = onNavigateToBle,
                    onIrClick = onNavigateToIr,
                    onSdClick = onNavigateToSd,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            // Quick Actions Section - Navigate AND trigger action
            item {
                BrutalistSectionHeader(
                    title = stringResource(R.string.header_quick_actions),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    accentColor = primaryColor()
                )
            }

            item {
                QuickActionsCard(
                    isConnected = isConnected,
                    onScanWifi = onScanWifiAndNavigate,
                    onScanBle = onScanBleAndNavigate,
                    onScanNfc = onScanNfcAndNavigate,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            // Channel Congestion (always show)
            item {
                BrutalistSectionHeader(
                    title = stringResource(R.string.header_channel_congestion),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    accentColor = primaryColor()
                )
            }

            item {
                ChannelCongestionChart(
                    accessPoints = accessPoints,
                    hasScanData = accessPoints.isNotEmpty(),
                    onScanClick = onScanWifiAndNavigate,
                    isConnected = isConnected,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Recent WiFi Networks (always show)
            item {
                BrutalistSectionHeader(
                    title = stringResource(R.string.header_recent_wifi),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    accentColor = primaryColor()
                )
            }

            if (!isConnected) {
                item {
                    ScanPlaceholderCard(
                        message = stringResource(R.string.msg_connect_scan_networks),
                        actionText = stringResource(R.string.action_connect),
                        onClick = openConnectionDialog,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else if (accessPoints.isNotEmpty()) {
                itemsIndexed(
                    items = accessPoints.take(5),
                    key = { _, ap -> ap.index },
                    contentType = { _, _ -> "access_point" }
                ) { index, ap ->
                    StaggeredAnimatedItem(
                        index = index,
                        staggerDelayMs = 50
                    ) {
                        RecentNetworkItem(
                            ap = ap,
                            privacyMode = privacyMode,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                }
            } else {
                item {
                    ScanPlaceholderCard(
                        message = stringResource(R.string.msg_no_networks_scanned),
                        actionText = stringResource(R.string.action_scan_networks),
                        onClick = onScanWifiAndNavigate,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Spacer for bottom navigation
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
            
            // Device Selection Dialog
            if (showDeviceDialog) {
                ConnectionSelectionDialog(
                    usbDevices = availableDevices,
                    bleDevices = availableBleDevices,
                    allUsbDevices = allUsbDevices,
                    usbDebugLog = usbDebugLog,
                    bluetoothEnabled = viewModel.isBluetoothEnabled(),
                    bluetoothSupported = viewModel.isBluetoothSupported(),
                    isBleScanning = isBleScanning,
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
                    onRefreshBle = { scanBleBridges() },
                    onDismiss = {
                        viewModel.stopBleBridgeScan()
                        showDeviceDialog = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    isConnected: Boolean,
    connectionState: SerialManager.ConnectionState,
    connectionTransport: SerialManager.ConnectionTransport,
    isLoading: Boolean,
    deviceInfo: GhostResponse.DeviceInfo?,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BrutalistCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (isConnected) successColor() else MaterialTheme.colorScheme.outline,
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderWidth = if (isConnected) 2.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when {
                        isLoading -> warningColor().copy(alpha = 0.2f)
                        isConnected -> successColor().copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = warningColor(),
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
                                tint = if (isConnected) successColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isConnected) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) successColor() else MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (isConnected && deviceInfo != null) {
                        Text(
                            text = "${deviceInfo.model} • ${connectionTransportLabel(connectionTransport)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (!isConnected) {
                        Text(
                            text = stringResource(R.string.prompt_connect_device),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Connect/Disconnect button
                Button(
                    onClick = if (isConnected) onDisconnectClick else onConnectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) errorColor() else primaryColor()
                    ),
                    enabled = !isLoading
                ) {
                    Text(
                        text = if (isConnected) stringResource(R.string.action_disconnect) else stringResource(R.string.action_connect)
                    )
                }
            }
            
            // Device info details
            if (isConnected && deviceInfo != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    deviceInfo.firmwareVersion?.let { fw ->
                        DeviceInfoItem(
                            label = stringResource(R.string.label_firmware),
                            value = fw
                        )
                    }
                    DeviceInfoItem(
                        label = stringResource(R.string.label_build),
                        value = deviceInfo.buildConfig ?: "v${deviceInfo.revision}"
                    )
                }
            }
        }
    }
}

@Composable
private fun connectionTransportLabel(transport: SerialManager.ConnectionTransport): String = when (transport) {
    SerialManager.ConnectionTransport.USB -> stringResource(R.string.label_usb)
    SerialManager.ConnectionTransport.BLE -> stringResource(R.string.label_wireless)
    SerialManager.ConnectionTransport.NONE -> stringResource(R.string.label_unknown)
}

@Composable
private fun DeviceInfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Quick Links Grid - Links to More menu items
 */
@Composable
private fun QuickLinksGrid(
    onWifiClick: () -> Unit,
    onBleClick: () -> Unit,
    onIrClick: () -> Unit,
    onSdClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickLinkCard(
            icon = Icons.Default.Wifi,
            label = stringResource(R.string.title_wifi),
            color = primaryColor(),
            onClick = onWifiClick,
            modifier = Modifier.weight(1f)
        )
        QuickLinkCard(
            icon = Icons.Default.Bluetooth,
            label = stringResource(R.string.title_ble),
            color = secondaryColor(),
            onClick = onBleClick,
            modifier = Modifier.weight(1f)
        )
        QuickLinkCard(
            icon = Icons.Default.SettingsRemote,
            label = stringResource(R.string.title_ir),
            color = tertiaryColor(),
            onClick = onIrClick,
            modifier = Modifier.weight(1f)
        )
        QuickLinkCard(
            icon = Icons.Default.SdCard,
            label = stringResource(R.string.label_sd),
            color = errorColor(),
            onClick = onSdClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickLinkCard(
    icon: ImageVector,
    label: String,
    count: Int = 0,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BrutalistCard(
        onClick = onClick,
        modifier = modifier,
        borderColor = color.copy(alpha = 0.5f),
        backgroundColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (count > 0) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuickActionsCard(
    isConnected: Boolean,
    onScanWifi: () -> Unit,
    onScanBle: () -> Unit,
    onScanNfc: () -> Unit,
    modifier: Modifier = Modifier
) {
    BrutalistCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.outline,
        backgroundColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionButton(
                icon = Icons.Default.Wifi,
                label = stringResource(R.string.label_scan_wifi),
                enabled = isConnected,
                onClick = onScanWifi,
                color = primaryColor()
            )
            QuickActionButton(
                painter = painterResource(R.drawable.ic_dolphin),
                label = stringResource(R.string.label_scan_flippers),
                enabled = isConnected,
                onClick = onScanBle,
                color = secondaryColor()
            )
            QuickActionButton(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.label_settings),
                enabled = true,
                onClick = onScanNfc,
                color = tertiaryColor()
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector? = null,
    painter: Painter? = null,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun RecentNetworkItem(
    ap: GhostResponse.AccessPoint,
    privacyMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    BrutalistCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderWidth = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = primaryColor(),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ap.ssid.ifEmpty { stringResource(R.string.label_hidden) }.censorSsid(privacyMode),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = ap.bssid.censorMac(privacyMode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${ap.rssi} ${stringResource(R.string.label_dbm)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (ap.rssi > -50) successColor() else if (ap.rssi > -70) warningColor() else errorColor()
            )
        }
    }
}

/**
 * Channel Congestion Chart - Bar chart showing AP count per WiFi channel
 * Shows placeholder overlay when no scan data is available
 */
@Composable
private fun ChannelCongestionChart(
    accessPoints: List<GhostResponse.AccessPoint>,
    hasScanData: Boolean,
    onScanClick: () -> Unit,
    isConnected: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Group APs by channel - memoized to avoid recomputation on every frame
    val channelCounts = remember(accessPoints) {
        accessPoints.groupBy { it.channel }.mapValues { it.value.size }
    }
    val maxCount = remember(channelCounts) { channelCounts.values.maxOrNull() ?: 1 }

    // All 2.4GHz channels (1-14) + any 5GHz channels present in scan
    val channels2g = remember { (1..14).toList() }
    val channels5g = remember(channelCounts) { channelCounts.keys.filter { it > 14 }.sorted() }
    val has5g = remember(channels5g) { channels5g.isNotEmpty() }

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    BrutalistCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.outline,
        backgroundColor = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasScanData) stringResource(R.string.msg_networks_channels_count, accessPoints.size, channelCounts.size) else stringResource(R.string.msg_no_scan_data),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CongestionLegendDot(color = successColor(), label = stringResource(R.string.label_congestion_low))
                        CongestionLegendDot(color = warningColor(), label = stringResource(R.string.label_congestion_med))
                        CongestionLegendDot(color = errorColor(), label = stringResource(R.string.label_congestion_high))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2.4GHz section
                Text(
                    text = stringResource(R.string.label_2_4ghz),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                ChannelBarRow(
                    channels = channels2g,
                    channelCounts = channelCounts,
                    maxCount = maxCount,
                    labelColor = labelColor,
                    emptyColor = surfaceVariant
                )

                // 5GHz section (only if present)
                if (has5g) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.label_5ghz),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    ChannelBarRow(
                        channels = channels5g,
                        channelCounts = channelCounts,
                        maxCount = maxCount,
                        labelColor = labelColor,
                        emptyColor = surfaceVariant
                    )
                }
            }
            
            if (!hasScanData) {
                ScanOverlay(
                    message = if (!isConnected) stringResource(R.string.msg_connect_scan_networks) else stringResource(R.string.msg_scan_networks_view_congestion),
                    actionText = if (!isConnected) stringResource(R.string.action_connect_scan) else stringResource(R.string.action_scan_networks),
                    onClick = onScanClick
                )
            }
        }
    }
}

@Composable
private fun ChannelBarRow(
    channels: List<Int>,
    channelCounts: Map<Int, Int>,
    maxCount: Int,
    labelColor: Color,
    emptyColor: Color
) {
    val barMaxHeight = 80.dp
    val barMaxHeightPx = with(LocalDensity.current) { barMaxHeight.toPx() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        channels.forEach { channel ->
            val count = channelCounts[channel] ?: 0
            val barColor = when {
                count == 0 -> emptyColor
                count <= 2 -> successColor()
                count <= 5 -> warningColor()
                else -> errorColor()
            }
            val barFraction = if (count > 0) {
                (count.toFloat() / maxCount).coerceIn(0.1f, 1f)
            } else {
                0.05f
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(28.dp)
            ) {
                // Count label above bar
                if (count > 0) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = labelColor
                    )
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Bar
                Canvas(
                    modifier = Modifier
                        .width(20.dp)
                        .height(barMaxHeight)
                ) {
                    val barHeight = barMaxHeightPx * barFraction
                    val yOffset = barMaxHeightPx - barHeight
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(0f, yOffset),
                        size = Size(size.width, barHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Channel label
                Text(
                    text = channel.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = labelColor
                )
            }
        }
    }
}

@Composable
private fun CongestionLegendDot(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(6.dp)) {
            drawCircle(color = color)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Scan Overlay - Semi-transparent overlay with scan prompt
 */
@Composable
private fun ScanOverlay(
    message: String,
    actionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WifiFind,
                contentDescription = null,
                tint = primaryColor(),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor()
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = actionText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardDeviceSelectionDialog(
    devices: List<UsbDevice>,
    allUsbDevices: List<UsbDevice> = emptyList(),
    usbDebugLog: List<String> = emptyList(),
    onDeviceSelected: (UsbDevice) -> Unit,
    onDebugClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var showDebugLog by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (devices.isEmpty()) stringResource(R.string.title_no_devices_found) else stringResource(R.string.title_select_device, devices.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (usbDebugLog.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showDebugLog = !showDebugLog }
                    ) {
                        Text(
                            text = if (showDebugLog) stringResource(R.string.action_hide_debug_log) else stringResource(R.string.action_show_debug_log, usbDebugLog.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor()
                        )
                    }
                }
                
                if (showDebugLog && usbDebugLog.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            items(usbDebugLog) { logLine ->
                                Text(
                                    text = logLine,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (devices.isEmpty()) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.msg_no_serial_devices),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (allUsbDevices.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.label_raw_usb_devices, allUsbDevices.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryColor()
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onDebugClick) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_refresh))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 250.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = devices,
                            key = { "${it.vendorId}-${it.productId}-${it.deviceName}" }
                        ) { device ->
                            Card(
                                onClick = { onDeviceSelected(device) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = device.productName ?: device.deviceName.ifEmpty { stringResource(R.string.label_usb_device) },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    device.manufacturerName?.let { manufacturer ->
                                        Text(
                                            text = manufacturer,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = primaryColor()
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(R.string.label_usb_ids, device.vendorId.toString(16), device.productId.toString(16)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.label_usb_interfaces, device.interfaceCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}

/**
 * Scan Placeholder Card - Card shown when no scan data available
 */
@Composable
private fun ScanPlaceholderCard(
    message: String,
    actionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BrutalistCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderWidth = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WifiFind,
                contentDescription = null,
                tint = primaryColor().copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor()
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = actionText)
            }
        }
    }
}
