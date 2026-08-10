package com.example.ghostespcompanion.ui.screens.more

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.domain.model.GhostCommand
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val statusMessage by viewModel.statusMessage.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val webAuthResult by viewModel.webAuthResult.collectAsState()
    val webUiApState by viewModel.webUiApState.collectAsState()
    val sinkholeStatus by viewModel.sinkholeStatus.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == com.example.ghostespcompanion.data.serial.SerialManager.ConnectionState.CONNECTED

    var webAuthOn by remember { mutableStateOf(false) }
    var webUiApOn by remember { mutableStateOf(false) }
    var sinkholeDomain by remember { mutableStateOf("") }
    var sinkholeBlocklistNumber by remember { mutableStateOf("") }
    LaunchedEffect(webAuthResult) { webAuthResult?.let { webAuthOn = it.enabled } }
    LaunchedEffect(webUiApState) { webUiApState?.let { webUiApOn = it.enabled } }
    
    val iconPainter = painterResource(R.mipmap.ic_launcher_foreground)
    
    MainScreen(
        onBack = onBack,
        title = stringResource(R.string.title_settings)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                BrutalistSectionHeader(
                    title = stringResource(R.string.header_app_settings),
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                BrutalistCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsToggle(
                            title = stringResource(R.string.setting_dark_mode),
                            subtitle = stringResource(R.string.setting_dark_mode_sub),
                            icon = Icons.Default.DarkMode,
                            checked = appSettings.darkMode,
                            onCheckedChange = { enabled ->
                                viewModel.setDarkMode(enabled)
                                viewModel.performHapticFeedback()
                            }
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        SettingsToggle(
                            title = stringResource(R.string.setting_haptic),
                            subtitle = stringResource(R.string.setting_haptic_sub),
                            icon = Icons.Default.Vibration,
                            checked = appSettings.hapticFeedback,
                            onCheckedChange = { enabled ->
                                viewModel.setHapticFeedback(enabled)
                            }
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        SettingsToggle(
                            title = stringResource(R.string.setting_auto_connect),
                            subtitle = stringResource(R.string.setting_auto_connect_sub),
                            icon = Icons.Default.BluetoothConnected,
                            checked = appSettings.autoConnect,
                            onCheckedChange = { enabled ->
                                viewModel.setAutoConnect(enabled)
                                viewModel.performHapticFeedback()
                            }
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        SettingsToggle(
                            title = stringResource(R.string.setting_notifications),
                            subtitle = stringResource(R.string.setting_notifications_sub),
                            icon = Icons.Default.Notifications,
                            checked = appSettings.showNotifications,
                            onCheckedChange = { enabled ->
                                viewModel.setShowNotifications(enabled)
                                viewModel.performHapticFeedback()
                            }
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        SettingsToggle(
                            title = stringResource(R.string.setting_privacy),
                            subtitle = stringResource(R.string.setting_privacy_sub),
                            icon = Icons.Default.PrivacyTip,
                            checked = appSettings.privacyMode,
                            onCheckedChange = { enabled ->
                                viewModel.setPrivacyMode(enabled)
                                viewModel.performHapticFeedback()
                            }
                        )
                    }
                }
            }

            item {
                BrutalistSectionHeader(
                    title = "Wired Compatibility",
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }

            item {
                BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsToggle(
                            title = "Assert DTR",
                            subtitle = "Enable only for USB CDC devices that stay silent",
                            icon = Icons.Default.Cable,
                            checked = appSettings.dtrCompatibilityMode,
                            onCheckedChange = { enabled ->
                                viewModel.setDtrCompatibilityMode(enabled)
                                viewModel.performHapticFeedback()
                            }
                        )
                    }
                }
            }
            
            if (statusMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = statusMessage!!,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            item {
                BrutalistSectionHeader(
                    title = "Device Web / DNS",
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }

            item {
                BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsToggle(
                            title = "Web Authentication",
                            subtitle = "Require login on the device Web UI",
                            icon = Icons.Default.Lock,
                            checked = webAuthOn,
                            onCheckedChange = { enabled ->
                                viewModel.webAuth(enabled)
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggle(
                            title = "WebUI AP-only",
                            subtitle = "Restrict Web UI access to the device AP",
                            icon = Icons.Default.WifiTethering,
                            checked = webUiApOn,
                            onCheckedChange = { enabled ->
                                viewModel.webUiAp(if (enabled) GhostCommand.WebUiApAction.ON else GhostCommand.WebUiApAction.OFF)
                            }
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
                            text = "DNS Sinkhole",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Blocklist-based DNS proxy for the device AP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            BrutalistButton(
                                text = "Start",
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.START) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                            BrutalistOutlinedButton(
                                text = "Stop",
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.STOP) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            BrutalistOutlinedButton(
                                text = "Status",
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.STATUS) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                            BrutalistOutlinedButton(
                                text = "Stats",
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.STATS) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                            BrutalistOutlinedButton(
                                text = "Reload",
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.RELOAD) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            BrutalistOutlinedButton(
                                text = "Log ON",
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.LOG_ON) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                            BrutalistOutlinedButton(
                                text = "Log OFF",
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.LOG_OFF) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = sinkholeBlocklistNumber,
                            onValueChange = { sinkholeBlocklistNumber = it.filter(Char::isDigit).take(2) },
                            label = { Text("Blocklist source (1-3)") },
                            placeholder = { Text("1 = Peter Lowe, 2 = OISD Basic, 3 = StevenBlack") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BrutalistOutlinedButton(
                            text = "Download Blocklist",
                            onClick = {
                                val number = sinkholeBlocklistNumber.toIntOrNull()
                                if (number != null) {
                                    viewModel.sinkhole(GhostCommand.SinkholeAction.DOWNLOAD, number.toString())
                                }
                            },
                            enabled = isConnected && sinkholeBlocklistNumber.toIntOrNull() != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = sinkholeDomain,
                            onValueChange = { sinkholeDomain = it },
                            label = { Text("Domain") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            BrutalistOutlinedButton(
                                text = "Add to Blocklist",
                                onClick = {
                                    if (sinkholeDomain.isNotBlank()) {
                                        viewModel.sinkhole(GhostCommand.SinkholeAction.ADD, sinkholeDomain.trim())
                                        sinkholeDomain = ""
                                    }
                                },
                                enabled = isConnected && sinkholeDomain.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            )
                            BrutalistOutlinedButton(
                                text = "Remove",
                                onClick = {
                                    if (sinkholeDomain.isNotBlank()) {
                                        viewModel.sinkhole(GhostCommand.SinkholeAction.REMOVE, sinkholeDomain.trim())
                                        sinkholeDomain = ""
                                    }
                                },
                                enabled = isConnected && sinkholeDomain.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        sinkholeStatus?.let { status ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            status.state?.let { Text("State: $it", style = MaterialTheme.typography.bodySmall) }
                            status.ip?.let { Text("IP: $it", style = MaterialTheme.typography.bodySmall) }
                            status.queries?.let {
                                val pct = status.blockPercent?.let { p -> "  (${p}%)" } ?: ""
                                Text("Queries: $it  Blocked: ${status.blocked ?: "?"}$pct", style = MaterialTheme.typography.bodySmall)
                            }
                            status.logging?.let { Text("Logging: ${if (it) "ON" else "OFF"}", style = MaterialTheme.typography.bodySmall) }
                            status.blocklist?.let { Text("Blocklist: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            item {
                BrutalistSectionHeader(
                    title = stringResource(R.string.header_about),
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                BrutalistCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = Color.Black,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = iconPainter,
                                        contentDescription = stringResource(R.string.desc_app_icon),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.label_version_beta),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        Text(
                            text = stringResource(R.string.msg_app_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
