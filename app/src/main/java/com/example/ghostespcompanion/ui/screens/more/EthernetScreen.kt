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
import com.example.ghostespcompanion.domain.model.GhostCommand
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
    var targetIp by rememberSaveable { mutableStateOf("") }
    var scanResults by remember { mutableStateOf<List<NetworkDevice>>(emptyList()) }
    var showOverlay by rememberSaveable { mutableStateOf(true) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val connectionState by viewModel.connectionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    val privacyMode = appSettings.privacyMode

    val ethernetCapability = deviceInfo.resolve(GhostResponse.DeviceFeature.ETHERNET)
    val commandsEnabled = isConnected && ethernetCapability.isUsable

    val liveEthernetInfo by viewModel.ethernetInfo.collectAsState()
    val liveEthernetStats by viewModel.ethernetStats.collectAsState()
    val liveArpResults by viewModel.arpScanResults.collectAsState()
    val livePortResults by viewModel.portScanResults.collectAsState()
    val livePingResults by viewModel.pingScanResults.collectAsState()
    val liveTraceHops by viewModel.traceHops.collectAsState()
    val livePoisonStatus by viewModel.ethPoisonStatus.collectAsState()
    val livePoisonDomains by viewModel.ethPoisonDomains.collectAsState()
    val livePoisonCookies by viewModel.ethPoisonCookies.collectAsState()
    val livePoisonCreds by viewModel.ethPoisonCreds.collectAsState()

    // eth_scan / etharp discovered hosts (firmware emits the same "ip  mac" lines for both)
    LaunchedEffect(liveArpResults) {
        scanResults = liveArpResults.map { NetworkDevice(ip = it.ip, mac = it.mac, hostname = null, vendor = null) }
    }

    // Tool-specific state variables
    var dnsQuery by rememberSaveable { mutableStateOf("") }
    var httpUrl by rememberSaveable { mutableStateOf("") }
    var httpMethod by remember { mutableStateOf("GET") }
    var poisonActive by remember { mutableStateOf(false) }
    var portRangeStart by rememberSaveable { mutableStateOf("") }
    var portRangeEnd by rememberSaveable { mutableStateOf("") }
    var selectedPortPreset by rememberSaveable { mutableStateOf<PortRangePreset?>(null) }

    // Default once the device reports its own network, but stay freely editable afterwards
    LaunchedEffect(liveEthernetInfo?.gateway, liveEthernetInfo?.ip) {
        if (targetIp.isBlank()) {
            liveEthernetInfo?.gateway?.let { targetIp = it } ?: liveEthernetInfo?.ip?.let { targetIp = it }
        }
    }

    val tabTitles = listOf(
        stringResource(R.string.tab_ethernet_connection),
        stringResource(R.string.tab_ethernet_network_scan),
        stringResource(R.string.tab_ethernet_diagnostics),
        stringResource(R.string.tab_ethernet_poisoning)
    )

    MainScreen(
        onBack = onBack,
        title = stringResource(R.string.title_ethernet),
        actions = {
            IconButton(onClick = {
                if (commandsEnabled) {
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
            CapabilityNotice(ethernetCapability, stringResource(R.string.title_ethernet), Modifier.padding(16.dp))
            // Connection Status Banner
            EthernetConnectionBanner(
                isConnected = isConnected,
                deviceName = stringResource(R.string.app_name_short),
                onConnect = { viewModel.connectFirstAvailable() }
            )

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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
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

                                    liveEthernetInfo?.let { info ->
                                        NetworkInfoRow(stringResource(R.string.label_ip_address), info.ip ?: "-")
                                        NetworkInfoRow(stringResource(R.string.label_subnet_mask), info.netmask ?: "-")
                                        NetworkInfoRow(stringResource(R.string.label_gateway), info.gateway ?: "-")
                                        NetworkInfoRow(stringResource(R.string.label_dns), info.dnsMain ?: "-")
                                        NetworkInfoRow(stringResource(R.string.label_mac_address), info.mac ?: "-")
                                    } ?: Text(
                                        text = stringResource(R.string.msg_no_live_network_info),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariantDark
                                    )
                                }
                            }
                        }

                        item {
                            BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Link Control",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        BrutalistButton(
                                            text = "Up",
                                            onClick = { if (commandsEnabled) viewModel.sendRaw("ethup") },
                                            enabled = commandsEnabled,
                                            modifier = Modifier.weight(1f)
                                        )
                                        BrutalistOutlinedButton(
                                            text = "Down",
                                            onClick = { if (commandsEnabled) viewModel.sendRaw("ethdown") },
                                            enabled = commandsEnabled,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        BrutalistOutlinedButton(
                                            text = "DHCP",
                                            onClick = { if (commandsEnabled) viewModel.sendRaw("ethconfig dhcp") },
                                            enabled = commandsEnabled,
                                            modifier = Modifier.weight(1f)
                                        )
                                        BrutalistOutlinedButton(
                                            text = "MAC",
                                            onClick = { if (commandsEnabled) viewModel.sendRaw("ethmac") },
                                            enabled = commandsEnabled,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    BrutalistButton(
                                        text = "Refresh Info",
                                        onClick = { if (commandsEnabled) viewModel.ethInfo() },
                                        enabled = commandsEnabled,
                                        modifier = Modifier.fillMaxWidth(),
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                                    )

                                    liveEthernetInfo?.let { info ->
                                        BrutalistDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        NetworkInfoRow("Link", if (info.linkUp) "UP" else "DOWN")
                                        info.ip?.let { NetworkInfoRow(stringResource(R.string.label_ip_address), it) }
                                        info.netmask?.let { NetworkInfoRow(stringResource(R.string.label_subnet_mask), it) }
                                        info.gateway?.let { NetworkInfoRow(stringResource(R.string.label_gateway), it) }
                                        info.dnsMain?.let { NetworkInfoRow(stringResource(R.string.label_dns), it) }
                                        info.mac?.let { NetworkInfoRow(stringResource(R.string.label_mac_address), it) }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
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
                                                    viewModel.clearEthArpResults()
                                                    viewModel.sendRaw("eth_scan ${targetIp.ifEmpty { "local" }}")
                                                    isScanning = true
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        containerColor = if (isScanning) errorColor() else primaryColor(),
                                        enabled = commandsEnabled,
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
                                            NetworkDeviceCard(
                                                device = device,
                                                privacyMode = privacyMode,
                                                onClick = { targetIp = device.ip }
                                            )
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
                                            if (commandsEnabled) viewModel.ethFingerprint()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = commandsEnabled,
                                        leadingIcon = {
                                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                                        }
                                    )
                                }
                            }

                            else -> {}
                        }

                        item {
                            BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "ARP / Port Scan",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor()
                                    )
                                    BrutalistButton(
                                        text = "Run ARP Scan",
                                        onClick = { if (commandsEnabled) viewModel.ethArp() },
                                        enabled = commandsEnabled,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    liveArpResults.forEach { entry ->
                                        NetworkInfoRow(entry.ip, entry.vendor?.let { "${entry.mac} ($it)" } ?: entry.mac)
                                    }

                                    BrutalistDivider(modifier = Modifier.padding(vertical = 4.dp))

                                    OutlinedTextField(
                                        value = targetIp,
                                        onValueChange = { targetIp = it },
                                        label = { Text(stringResource(R.string.label_shared_target)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Text(
                                        text = stringResource(R.string.label_port_range_presets),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurfaceVariantDark
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        PortRangePreset.entries.forEach { preset ->
                                            FilterChip(
                                                selected = selectedPortPreset == preset,
                                                onClick = {
                                                    selectedPortPreset = preset
                                                    portRangeStart = preset.start.toString()
                                                    portRangeEnd = preset.end.toString()
                                                },
                                                label = { Text(stringResource(preset.labelRes)) }
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = portRangeStart,
                                            onValueChange = { portRangeStart = it; selectedPortPreset = null },
                                            label = { Text("Start Port") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = portRangeEnd,
                                            onValueChange = { portRangeEnd = it; selectedPortPreset = null },
                                            label = { Text("End Port") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    BrutalistButton(
                                        text = "Run Port Scan",
                                        onClick = {
                                            if (commandsEnabled && targetIp.isNotBlank()) {
                                                viewModel.ethPorts(
                                                    targetIp.trim(),
                                                    portRangeStart.toIntOrNull(),
                                                    portRangeEnd.toIntOrNull()
                                                )
                                            }
                                        },
                                        enabled = commandsEnabled && targetIp.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    livePortResults.forEach { entry ->
                                        NetworkInfoRow("${entry.ip}:${entry.port}", entry.state)
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
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

                        when (selectedTool) {
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
                                            enabled = commandsEnabled && dnsQuery.isNotBlank(),
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
                                            enabled = commandsEnabled && dnsQuery.isNotBlank(),
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
                                        enabled = commandsEnabled && httpUrl.isNotBlank(),
                                        leadingIcon = {
                                            Icon(Icons.Default.Send, contentDescription = null)
                                        }
                                    )
                                }
                            }

                            else -> {}
                        }

                        item {
                            BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Ping / Traceroute / Stats",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor()
                                    )

                                    Text(
                                        text = stringResource(R.string.label_ping_subnet_sweep),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurfaceDark
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.msg_ping_subnet_sweep,
                                            liveEthernetInfo?.netmask?.let { "${liveEthernetInfo?.ip ?: "?"}/$it" } ?: "unknown until connected"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariantDark
                                    )
                                    BrutalistButton(
                                        text = "Ping",
                                        onClick = { if (commandsEnabled) viewModel.ethPing() },
                                        enabled = commandsEnabled,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    livePingResults.forEach { entry ->
                                        Text(entry.ip, style = MaterialTheme.typography.bodySmall, color = successColor())
                                    }

                                    BrutalistDivider(modifier = Modifier.padding(vertical = 4.dp))

                                    OutlinedTextField(
                                        value = targetIp,
                                        onValueChange = { targetIp = it },
                                        label = { Text(stringResource(R.string.label_shared_target)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    BrutalistButton(
                                        text = "Traceroute",
                                        onClick = { if (commandsEnabled && targetIp.isNotBlank()) viewModel.ethTrace(targetIp.trim()) },
                                        enabled = commandsEnabled && targetIp.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    liveTraceHops.forEach { hop ->
                                        NetworkInfoRow(
                                            "Hop ${hop.hop}",
                                            if (hop.timeout) "timeout" else "${hop.ip ?: "?"} ${hop.ms?.let { "${it}ms" } ?: ""}"
                                        )
                                    }

                                    BrutalistDivider(modifier = Modifier.padding(vertical = 4.dp))

                                    BrutalistOutlinedButton(
                                        text = "Ethstats",
                                        onClick = { if (commandsEnabled) viewModel.ethStats() },
                                        enabled = commandsEnabled,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    liveEthernetStats?.let { stats ->
                                        stats.linkStatus?.let { NetworkInfoRow("Link", it) }
                                        stats.rxPackets?.let { NetworkInfoRow("RX Packets", it.toString()) }
                                        stats.txPackets?.let { NetworkInfoRow("TX Packets", it.toString()) }
                                        stats.rxErrors?.let { NetworkInfoRow("RX Errors", it.toString()) }
                                        stats.txErrors?.let { NetworkInfoRow("TX Errors", it.toString()) }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        item {
                            BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "ARP Poisoning",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor()
                                    )
                                    Text(
                                        text = "Run MitM ARP cache poisoning. Poison output (cookies/creds) appears in Terminal.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        BrutalistButton(
                                            text = if (poisonActive) "Stop Poison" else "Start Poison",
                                            onClick = {
                                                if (commandsEnabled) {
                                                    if (poisonActive) {
                                                        viewModel.ethPoison(GhostCommand.EthPoisonAction.STOP)
                                                    } else {
                                                        viewModel.ethPoison(GhostCommand.EthPoisonAction.START)
                                                    }
                                                    poisonActive = !poisonActive
                                                }
                                            },
                                            enabled = commandsEnabled,
                                            containerColor = if (poisonActive) errorColor() else warningColor(),
                                            modifier = Modifier.weight(1f)
                                        )
                                        BrutalistOutlinedButton(
                                            text = "Poison Status",
                                            onClick = {
                                                if (commandsEnabled) viewModel.ethPoison(GhostCommand.EthPoisonAction.STATUS)
                                            },
                                            enabled = commandsEnabled,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        BrutalistOutlinedButton(
                                            text = "Poison List",
                                            onClick = {
                                                if (commandsEnabled) viewModel.ethPoison(GhostCommand.EthPoisonAction.LIST)
                                            },
                                            enabled = commandsEnabled,
                                            modifier = Modifier.weight(1f)
                                        )
                                        BrutalistOutlinedButton(
                                            text = "Dump Cookies",
                                            onClick = {
                                                if (commandsEnabled) viewModel.ethPoison(GhostCommand.EthPoisonAction.COOKIES)
                                            },
                                            enabled = commandsEnabled,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    BrutalistOutlinedButton(
                                        text = "Dump Creds",
                                        onClick = {
                                            if (commandsEnabled) viewModel.ethPoison(GhostCommand.EthPoisonAction.CREDS)
                                        },
                                        enabled = commandsEnabled,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    livePoisonStatus?.let { status ->
                                        BrutalistDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        Text(
                                            text = "State: ${status.state}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryColor()
                                        )
                                        status.hosts?.let { NetworkInfoRow("Hosts", it.toString()) }
                                        status.domains?.let { NetworkInfoRow("Domains", it.toString()) }
                                        status.cookies?.let { NetworkInfoRow("Cookies", it.toString()) }
                                        status.creds?.let { NetworkInfoRow("Creds", it.toString()) }
                                    }
                                    livePoisonDomains.forEach { domain -> NetworkInfoRow("Domain", domain) }
                                    livePoisonCookies.forEach { cookie -> NetworkInfoRow("Cookie", cookie) }
                                    livePoisonCreds.forEach { cred -> NetworkInfoRow("Cred", cred) }
                                }
                            }
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

        // Feature Not Supported Overlay
        if (ethernetCapability == GhostResponse.CapabilityResolution.UNSUPPORTED) {
            FeatureNotSupportedOverlay(
                show = showOverlay,
                onProceed = { showOverlay = false },
                featureName = stringResource(R.string.title_ethernet),
                message = stringResource(R.string.msg_ethernet_unsupported)
            )
        }
        }
    }
}

enum class EthernetTool {
    NETWORK_SCAN, FINGERPRINT, DNS, HTTP
}

enum class PortRangePreset(val start: Int, val end: Int, val labelRes: Int) {
    TOP_20(1, 20, R.string.preset_ports_top_20),
    TOP_100(1, 100, R.string.preset_ports_top_100),
    COMMON_WEB(80, 8443, R.string.preset_ports_common_web),
    ALL(1, 1024, R.string.preset_ports_all)
}

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
private fun NetworkDeviceCard(device: NetworkDevice, privacyMode: Boolean = false, onClick: () -> Unit = {}) {
    BrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
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
