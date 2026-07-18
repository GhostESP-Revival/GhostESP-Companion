package com.example.ghostespcompanion.ui.screens.more

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.utils.censorMac
import com.example.ghostespcompanion.ui.utils.censorIp
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

/**
 * Ethernet Screen - Network scanning and tools
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EthernetScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var isScanning by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf(EthernetTool.NETWORK_SCAN) }
    var targetIp by remember { mutableStateOf("") }
    var scanResults by remember { mutableStateOf<List<NetworkDevice>>(emptyList()) }
    var showOverlay by rememberSaveable { mutableStateOf(true) }
    
val connectionState by viewModel.connectionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    val privacyMode = appSettings.privacyMode
    
    val isEthernetSupported = deviceInfo?.hasFeature(GhostResponse.DeviceFeature.ETHERNET) ?: true
    val hasDeviceInfo = deviceInfo != null
    
    // Tool-specific state variables
    var dnsQuery by remember { mutableStateOf("") }
    var httpUrl by remember { mutableStateOf("") }
    var httpMethod by remember { mutableStateOf("GET") }
    
    // Mock network info
    var networkInfo by remember {
        mutableStateOf(
            NetworkInfo(
                ip = "192.168.1.100",
                subnet = "255.255.255.0",
                gateway = "192.168.1.1",
                dns = "8.8.8.8",
                mac = "AA:BB:CC:DD:EE:FF"
            )
        )
    }
    
    MainScreen(
        onBack = onBack,
        title = stringResource(R.string.title_ethernet),
        actions = {
            IconButton(onClick = {
                if (isConnected) {
                    viewModel.sendRaw("eth_info")
                }
            }) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = stringResource(R.string.label_network_information),
                    tint = primaryColor()
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Connection Status Banner
            EthernetConnectionBanner(
                isConnected = isConnected,
                deviceName = stringResource(R.string.app_name_short),
                onConnect = { viewModel.connectFirstAvailable() }
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Network Info Card
                item {
                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.title_network_information),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor()
                            )
                            
                            NetworkInfoRow(stringResource(R.string.label_ip_address), networkInfo.ip)
                            NetworkInfoRow(stringResource(R.string.label_subnet_mask), networkInfo.subnet)
                            NetworkInfoRow(stringResource(R.string.label_gateway), networkInfo.gateway)
                            NetworkInfoRow(stringResource(R.string.label_dns), networkInfo.dns)
                            NetworkInfoRow(stringResource(R.string.label_mac_address), networkInfo.mac)
                        }
                    }
                }
                
                // Tool Selection
                item {
                    Text(
                        text = stringResource(R.string.label_network_tools),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor()
                    )
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolButton(
                            text = stringResource(R.string.action_scan).lowercase().replaceFirstChar { it.uppercase() },
                            icon = Icons.Default.Search,
                            selected = selectedTool == EthernetTool.NETWORK_SCAN,
                            onClick = { selectedTool = EthernetTool.NETWORK_SCAN },
                            modifier = Modifier.weight(1f)
                        )
                        ToolButton(
                            text = stringResource(R.string.label_fingerprint),
                            icon = Icons.Default.Fingerprint,
                            selected = selectedTool == EthernetTool.FINGERPRINT,
                            onClick = { selectedTool = EthernetTool.FINGERPRINT },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolButton(
                            text = stringResource(R.string.label_dns),
                            icon = Icons.Default.Dns,
                            selected = selectedTool == EthernetTool.DNS,
                            onClick = { selectedTool = EthernetTool.DNS },
                            modifier = Modifier.weight(1f)
                        )
                        ToolButton(
                            text = stringResource(R.string.label_http),
                            icon = Icons.Default.Http,
                            selected = selectedTool == EthernetTool.HTTP,
                            onClick = { selectedTool = EthernetTool.HTTP },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Tool-specific content
                when (selectedTool) {
                    EthernetTool.NETWORK_SCAN -> {
                        item {
                            OutlinedTextField(
                                value = targetIp,
                                onValueChange = { targetIp = it },
                                label = { Text(stringResource(R.string.label_target_ip_range)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.placeholder_ip_range)) }
                            )
                        }
                        
                        item {
                            BrutalistButton(
                                text = if (isScanning) stringResource(R.string.action_stop_scan) else stringResource(R.string.action_start_network_scan),
                                onClick = {
                                    if (isConnected) {
                                        if (isScanning) {
                                            viewModel.stopAll()
                                            isScanning = false
                                        } else {
                                            viewModel.sendRaw("eth_scan ${targetIp.ifEmpty { "local" }}")
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
                        }
                        
                        // Scan Results
                        if (scanResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.label_discovered_devices, scanResults.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = successColor()
                                )
                            }
                            
scanResults.forEach { device ->
                                item {
                                    NetworkDeviceCard(device = device, privacyMode = privacyMode)
                                }
                            }
                        }
                    }
                    
                    EthernetTool.FINGERPRINT -> {
                        item {
                            OutlinedTextField(
                                value = targetIp,
                                onValueChange = { targetIp = it },
                                label = { Text(stringResource(R.string.label_target_ip)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.placeholder_target_ip)) }
                            )
                        }
                        
                        item {
                            BrutalistButton(
                                text = stringResource(R.string.action_fingerprint_device),
                                onClick = {
                                    if (isConnected && targetIp.isNotBlank()) {
                                        viewModel.sendRaw("eth_fingerprint $targetIp")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = isConnected && targetIp.isNotBlank(),
                                leadingIcon = {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                                }
                            )
                        }
                    }
                    
                    EthernetTool.DNS -> {
                        item {
                            OutlinedTextField(
                                value = dnsQuery,
                                onValueChange = { dnsQuery = it },
                                label = { Text(stringResource(R.string.label_domain_or_ip)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.placeholder_domain)) }
                            )
                        }
                        
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BrutalistButton(
                                    text = stringResource(R.string.action_lookup),
                                    onClick = {
                                        if (isConnected && dnsQuery.isNotBlank()) {
                                            viewModel.sendRaw("dns_lookup $dnsQuery")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = isConnected && dnsQuery.isNotBlank(),
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null)
                                    }
                                )
                                
                                BrutalistOutlinedButton(
                                    text = stringResource(R.string.action_reverse),
                                    onClick = {
                                        if (isConnected && dnsQuery.isNotBlank()) {
                                            viewModel.sendRaw("dns_reverse $dnsQuery")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = isConnected && dnsQuery.isNotBlank(),
                                    leadingIcon = {
                                        Icon(Icons.Default.SwapVert, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                    
                    EthernetTool.HTTP -> {
                        item {
                            OutlinedTextField(
                                value = httpUrl,
                                onValueChange = { httpUrl = it },
                                label = { Text(stringResource(R.string.label_url)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.placeholder_url)) }
                            )
                        }
                        
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("GET", "POST", "HEAD").forEach { method ->
                                    FilterChip(
                                        selected = httpMethod == method,
                                        onClick = { httpMethod = method },
                                        label = { Text(method) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = primaryColor().copy(alpha = 0.2f),
                                            selectedLabelColor = primaryColor()
                                        )
                                    )
                                }
                            }
                        }
                        
                        item {
                            BrutalistButton(
                                text = stringResource(R.string.action_send_request),
                                onClick = {
                                    if (isConnected && httpUrl.isNotBlank()) {
                                        viewModel.sendRaw("http_${httpMethod.lowercase()} $httpUrl")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = isConnected && httpUrl.isNotBlank(),
                                leadingIcon = {
                                    Icon(Icons.Default.Send, contentDescription = null)
                                }
                            )
                        }
                    }
                }
                
                // Status Message
                if (statusMessage != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = SurfaceVariantDark
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = statusMessage!!,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceDark
                            )
                        }
                    }
                }
                
                // Info Card
                item {
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
                                text = stringResource(R.string.msg_ethernet_adapter_required),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantDark
                            )
                        }
                    }
                }
            }
        }
        
        // Feature Not Supported or Coming Soon Overlay
        if (hasDeviceInfo && !isEthernetSupported) {
            FeatureNotSupportedOverlay(
                show = showOverlay,
                onProceed = { showOverlay = false },
                featureName = stringResource(R.string.title_ethernet),
                message = stringResource(R.string.msg_ethernet_unsupported)
            )
        } else {
            ComingSoonOverlay(
                show = showOverlay,
                onProceed = { showOverlay = false },
                viewName = stringResource(R.string.title_ethernet),
                title = stringResource(R.string.title_coming_soon),
                message = stringResource(R.string.msg_ethernet_coming_soon)
            )
        }
        }
    }
}

enum class EthernetTool {
    NETWORK_SCAN, FINGERPRINT, DNS, HTTP
}

/**
 * Network info data class
 */
data class NetworkInfo(
    val ip: String,
    val subnet: String,
    val gateway: String,
    val dns: String,
    val mac: String
)

/**
 * Network device data class
 */
data class NetworkDevice(
    val ip: String,
    val mac: String,
    val hostname: String?,
    val vendor: String?
)

@Composable
private fun NetworkInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariantDark
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = OnSurfaceDark
        )
    }
}

@Composable
private fun ToolButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BrutalistCard(
        modifier = modifier,
        borderColor = if (selected) primaryColor() else OutlineDark,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) primaryColor() else OnSurfaceVariantDark,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) primaryColor() else OnSurfaceVariantDark
            )
        }
    }
}

@Composable
private fun NetworkDeviceCard(device: NetworkDevice, privacyMode: Boolean = false) {
    BrutalistCard(
        modifier = Modifier.fillMaxWidth()
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
                    Icons.Default.Computer,
                    contentDescription = null,
                    tint = primaryColor(),
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text = device.ip.censorIp(privacyMode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                    device.hostname?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantDark
                        )
                    }
                    Text(
                        text = device.mac.censorMac(privacyMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariantDark
                    )
                    device.vendor?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariantDark
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ethernet Connection Banner component
 */
@Composable
private fun EthernetConnectionBanner(
    isConnected: Boolean,
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
                    if (isConnected) Icons.Default.SettingsEthernet else Icons.Default.SettingsEthernet,
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
                            text = stringResource(R.string.label_ethernet_ready),
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
