package com.example.ghostespcompanion

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ghostespcompanion.data.repository.AppSettings
import com.example.ghostespcompanion.data.repository.PreferencesRepository
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.ui.components.ConnectionSelectionDialog
import com.example.ghostespcompanion.ui.navigation.GhostESPNavGraph
import com.example.ghostespcompanion.ui.navigation.Screen
import com.example.ghostespcompanion.ui.navigation.bottomNavItems
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main Activity for GhostESP Companion
 * 
 * Single activity that hosts all Compose screens with bottom navigation.
 * Minimalist dark theme with white accents.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferencesRepository: PreferencesRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContent {
            // Collect settings to apply dark mode
            val appSettings by preferencesRepository.appSettings.collectAsState(AppSettings())
            
            GhostESPTheme(darkTheme = appSettings.darkMode) {
                GhostESPApp(
                    autoConnect = appSettings.autoConnect
                )
            }
        }
    }
}

/**
 * Main app composable with modern minimalist bottom navigation
 */
@Composable
fun GhostESPApp(
    autoConnect: Boolean = false,
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    
    // Handle auto-connect on startup
    val connectionState by viewModel.connectionState.collectAsState()
    val hasAutoConnected = remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    val availableDevices by viewModel.availableUsbDevices.collectAsState()
    val allUsbDevices by viewModel.allUsbDevices.collectAsState()
    val usbDebugLog by viewModel.usbDebugLog.collectAsState()
    val availableBleDevices by viewModel.availableBleDevices.collectAsState()
    val isBleScanning by viewModel.isBleScanning.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            viewModel.startBleBridgeScan()
        }
    }

    val scanBleBridges: () -> Unit = {
        if (!viewModel.isBluetoothSupported() || !viewModel.isBluetoothEnabled()) {
        } else {
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

    LaunchedEffect(autoConnect, connectionState) {
        if (autoConnect && !hasAutoConnected.value && connectionState == SerialManager.ConnectionState.DISCONNECTED) {
            hasAutoConnected.value = true
            val devices = viewModel.fetchAvailableDevices()
            when (devices.size) {
                0 -> { /* no devices, nothing to show */ }
                1 -> viewModel.connectWithAutoBaud(devices.first())
                else -> showDeviceDialog = true
            }
        }
    }

    // Reconnect to the last device once Bluetooth/USB is ready
    LaunchedEffect(Unit) {
        if (autoConnect) {
            viewModel.connectSavedDevice()
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val currentRoute = currentDestination?.route
            
            // Show bottom bar on all screens, but determine which tab to highlight
            // For sub-screens, highlight the parent tab
            val highlightedRoute = when {
                currentRoute == null -> Screen.Dashboard.route
                currentRoute.startsWith("more/") && currentRoute != "more" -> Screen.More.route
                currentRoute.startsWith("wifi/") && currentRoute != "wifi" -> Screen.Wifi.route
                currentRoute.startsWith("ble/") && currentRoute != "ble" -> Screen.Ble.route
                currentRoute.startsWith("ir/") && currentRoute != "ir" -> Screen.Ir.route
                currentRoute.startsWith("nfc/") && currentRoute != "nfc" -> Screen.More.route // NFC is under More
                else -> currentRoute
            }
            
            ModernNavigationBar(
                items = bottomNavItems,
                currentRoute = highlightedRoute,
                onItemClick = { screen ->
                    val startDestId = navController.graph.findStartDestination().id
                    
                    // Check if we're in a sub-screen of the tapped tab
                    // If so, pop back to the parent screen instead of navigating
                    val isInSubmenuOfTappedTab = when {
                        screen.route == Screen.More.route && currentRoute != null -> 
                            currentRoute.startsWith("more/") && currentRoute != "more"
                        screen.route == Screen.Wifi.route && currentRoute != null -> 
                            currentRoute.startsWith("wifi/") && currentRoute != "wifi"
                        screen.route == Screen.Ble.route && currentRoute != null -> 
                            currentRoute.startsWith("ble/") && currentRoute != "ble"
                        screen.route == Screen.Ir.route && currentRoute != null -> 
                            currentRoute.startsWith("ir/") && currentRoute != "ir"
                        else -> false
                    }
                    
                    when {
                        screen.route == Screen.Dashboard.route -> {
                            // For Dashboard (start destination), just pop back to it
                            navController.popBackStack(startDestId, inclusive = false)
                        }
                        isInSubmenuOfTappedTab -> {
                            // Pop back to the parent screen (e.g., from more/settings to more)
                            navController.popBackStack(screen.route, inclusive = false)
                        }
                        else -> {
                            // For other tabs, use standard save/restore state pattern
                            navController.navigate(screen.route) {
                                popUpTo(startDestId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            GhostESPNavGraph(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                sharedViewModel = viewModel
            )
        }
        
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
                onRefreshBle = {
                    scanBleBridges()
                },
                onDismiss = {
                    viewModel.stopBleBridgeScan()
                    showDeviceDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSelectionDialog(
    devices: List<UsbDevice>,
    usbDebugLog: List<String> = emptyList(),
    onDeviceSelected: (UsbDevice, Int) -> Unit,
    onRefresh: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var showDebugLog by remember { mutableStateOf(false) }
    val baudRates = remember { listOf(9600, 57600, 115200, 230400, 420600, 460800, 921600) }
    var selectedBaud by remember { mutableStateOf(115200) }
    var baudExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Check USB connection and tap Refresh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onRefresh) { Text("Refresh") }
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
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                            color = MaterialTheme.colorScheme.primary
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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${device.interfaceCount} if",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Modern minimalist navigation bar
 */
@Composable
fun ModernNavigationBar(
    items: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { screen ->
                val selected = currentRoute == screen.route
                
                ModernNavItem(
                    icon = screen.icon.toImageVector(),
                    label = screen.title,
                    selected = selected,
                    onClick = { onItemClick(screen) }
                )
            }
        }
    }
}

/**
 * Modern nav item with minimalist styling
 */
@Composable
fun ModernNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    
    val iconColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp)),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = textColor
            )
        }
    }
}

/**
 * Extension function to convert icon string to ImageVector
 */
private fun String.toImageVector(): ImageVector = when (this) {
    "dashboard" -> Icons.Default.Dashboard
    "wifi" -> Icons.Default.Wifi
    "bluetooth" -> Icons.Default.Bluetooth
    "nfc" -> Icons.Default.Nfc
    "remote" -> Icons.Default.SettingsRemote
    "more_horiz" -> Icons.Default.MoreHoriz
    else -> Icons.Default.Help
}
