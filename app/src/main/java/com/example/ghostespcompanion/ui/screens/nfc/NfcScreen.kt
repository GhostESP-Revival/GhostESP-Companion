package com.example.ghostespcompanion.ui.screens.nfc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel
import com.example.ghostespcompanion.ui.components.ComingSoonOverlay
import com.example.ghostespcompanion.ui.components.FeatureNotSupportedOverlay

/**
 * NFC Screen
 * 
 * Main NFC feature screen with:
 * - Tag scanning
 * - Chameleon Ultra integration
 * - Saved tags management
 * 
 * @param onNavigateToChameleon Callback to navigate to Chameleon Ultra screen
 * @param onNavigateToSaved Callback to navigate to saved tags screen
 */
@Composable
fun NfcScreen(
    onNavigateToChameleon: () -> Unit,
    onNavigateToSaved: () -> Unit,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var isScanning by remember { mutableStateOf(false) }
    var showOverlay by rememberSaveable { mutableStateOf(true) }
    
    val connectionState by viewModel.connectionState.collectAsState()
    val nfcTags by viewModel.nfcTags.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    
    val isNfcSupported = deviceInfo?.hasFeature(GhostResponse.DeviceFeature.NFC) ?: true
    val hasDeviceInfo = deviceInfo != null
    
    MainScreen(
        onBack = onBack,
        title = stringResource(R.string.title_nfc),
        actions = {
            IconButton(onClick = onNavigateToChameleon) {
                Icon(Icons.Default.CreditCard, contentDescription = stringResource(R.string.title_chameleon_ultra))
            }
            IconButton(onClick = onNavigateToSaved) {
                Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.title_saved_tags))
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
            // Quick action cards
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    QuickActionCard(
                        title = stringResource(R.string.title_scan_tag),
                        subtitle = stringResource(R.string.action_scan),
                        icon = Icons.Default.Nfc,
                        onClick = {
                            if (isConnected) {
                                isScanning = true
                                viewModel.scanNfc()
                            }
                        },
                        enabled = isConnected
                    )
                }
                item {
                    QuickActionCard(
                        title = stringResource(R.string.label_write_tag),
                        subtitle = stringResource(R.string.title_coming_soon),
                        icon = Icons.Default.Edit,
                        onClick = {},
                        enabled = false
                    )
                }
                item {
                    QuickActionCard(
                        title = stringResource(R.string.label_chameleon),
                        subtitle = stringResource(R.string.label_emulate_tags),
                        icon = Icons.Default.CreditCard,
                        onClick = onNavigateToChameleon
                    )
                }
                item {
                    QuickActionCard(
                        title = stringResource(R.string.label_saved),
                        subtitle = stringResource(R.string.label_view_saved_tags),
                        icon = Icons.Default.Folder,
                        onClick = onNavigateToSaved
                    )
                }
            }
            
            // Scanning status
            if (isScanning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.msg_waiting_for_tag),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.msg_place_tag_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = {
                            isScanning = false
                            viewModel.stopNfcScan()
                        }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
            
            // Status message
            statusMessage?.let { status ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Recent tags section
            Text(
                text = stringResource(R.string.label_recent_tags, nfcTags.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Display scanned tags
            if (nfcTags.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(nfcTags.take(6), key = { it.uid }) { tag ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = tag.type.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = stringResource(R.string.label_uid_prefix, tag.uid.take(8)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // Placeholder for no tags
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Contactless,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.msg_no_recent_tags),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.msg_scan_tag_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Feature Not Supported or Coming Soon Overlay
        if (hasDeviceInfo && !isNfcSupported) {
            FeatureNotSupportedOverlay(
                show = showOverlay,
                onProceed = { showOverlay = false },
                featureName = stringResource(R.string.title_nfc),
                message = stringResource(R.string.msg_nfc_unsupported)
            )
        } else {
            ComingSoonOverlay(
                show = showOverlay,
                onProceed = { showOverlay = false },
                viewName = stringResource(R.string.title_nfc),
                title = stringResource(R.string.title_coming_soon),
                message = stringResource(R.string.msg_nfc_coming_soon)
            )
        }
        }
    }
}

/**
 * Quick action card for NFC features
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) 
                MaterialTheme.colorScheme.surfaceVariant 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (enabled) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = if (enabled) 
                    MaterialTheme.colorScheme.onSurface 
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) 
                    MaterialTheme.colorScheme.onSurfaceVariant 
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                textAlign = TextAlign.Center
            )
        }
    }
}
