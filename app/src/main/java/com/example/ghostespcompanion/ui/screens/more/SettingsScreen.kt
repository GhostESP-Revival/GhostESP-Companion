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
    
    val iconPainter = painterResource(R.drawable.gesp)
    
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
