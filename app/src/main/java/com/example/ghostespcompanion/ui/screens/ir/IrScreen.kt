package com.example.ghostespcompanion.ui.screens.ir

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

/**
 * Infrared Screen - Minimalist Neo-Brutalist Design
 * 
 * Clean white accents on deep black background.
 * Professional and modern aesthetic.
 */
@Composable
fun IrScreen(
    onNavigateToLearn: () -> Unit,
    onNavigateToRemoteDetail: (Int) -> Unit,
    viewModel: MainViewModel
) {
    var isDazzlerRunning by remember { mutableStateOf(false) }
    var isLoadingRemotes by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    
    val connectionState by viewModel.connectionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val irRemotes by viewModel.irRemotes.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    
    // Stop IR dazzler when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            if (isDazzlerRunning) {
                viewModel.stopIrDazzler()
            }
        }
    }
    
    val isIrSupported = deviceInfo?.let {
        it.hasFeature(GhostResponse.DeviceFeature.INFRARED_TX) || 
        it.hasFeature(GhostResponse.DeviceFeature.INFRARED_RX) 
    } ?: true
    val hasIrRx = deviceInfo?.hasFeature(GhostResponse.DeviceFeature.INFRARED_RX) ?: true
    val hasDeviceInfo = deviceInfo != null
    
    // Load IR remotes when connected
    LaunchedEffect(isConnected) {
        if (isConnected) {
            isLoadingRemotes = true
            viewModel.listIrRemotes()
        }
    }
    
    // Stop loading when we receive remotes
    LaunchedEffect(irRemotes) {
        if (irRemotes.isNotEmpty()) {
            isLoadingRemotes = false
        }
    }
    
    // Timeout for loading - stop after 5 seconds if no remotes received
    LaunchedEffect(isLoadingRemotes) {
        if (isLoadingRemotes) {
            kotlinx.coroutines.delay(5000)
            if (irRemotes.isEmpty()) {
                isLoadingRemotes = false
            }
        }
    }
    
    // Convert IR remotes to display format - only show real data from device
    // Wrap in remember to avoid recomputing on every recomposition
    val displayRemotes = remember(irRemotes) {
        irRemotes.map { remote ->
            RemotePreview(
                name = remote.filename.removeSuffix(".ir").removeSuffix(".json"),
                brand = remote.filename.substringAfterLast(".").uppercase()
            )
        }
    }
    
    MainScreen(
        title = stringResource(R.string.title_ir),
        actions = {
            IconButton(onClick = onNavigateToLearn) {
                Icon(
                    Icons.Default.SettingsRemote, 
                    contentDescription = stringResource(R.string.title_learn_signal),
                    tint = primaryColor()
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Quick actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dazzler button
                BrutalistButton(
                    text = if (isDazzlerRunning) stringResource(R.string.action_stop) else stringResource(R.string.label_ir_dazzler),
                    onClick = {
                        if (isConnected) {
                            if (isDazzlerRunning) {
                                viewModel.stopIrDazzler()
                            } else {
                                viewModel.startIrDazzler()
                            }
                            isDazzlerRunning = !isDazzlerRunning
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (isDazzlerRunning) errorColor() else MaterialTheme.colorScheme.primary,
                    leadingIcon = { 
                        Icon(
                            if (isDazzlerRunning) Icons.Default.Stop else Icons.Default.FlashOn,
                            contentDescription = null
                        )
                    },
                    enabled = isConnected
                )
            }
            
            // Dazzler warning - Minimal style
            if (isDazzlerRunning) {
                val dazzlerErrorColor = errorColor()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .drawBehind {
                            val shadowSize = 2.dp.toPx()
                            drawRect(
                                color = dazzlerErrorColor.copy(alpha = 0.15f),
                                topLeft = Offset(shadowSize, shadowSize),
                                size = size
                            )
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = dazzlerErrorColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, dazzlerErrorColor)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = dazzlerErrorColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.msg_ir_dazzler_running),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = dazzlerErrorColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Status message
            statusMessage?.let { status ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceVariantDark
                    )
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Remote library header
            BrutalistSectionHeader(
                title = if (isLoadingRemotes) stringResource(R.string.status_connecting) else stringResource(R.string.label_remote_library, displayRemotes.size),
                modifier = Modifier.padding(horizontal = 16.dp),
                accentColor = primaryColor()
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoadingRemotes) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = primaryColor())
                        }
                    }
                } else if (displayRemotes.isEmpty() && isConnected) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.msg_no_remotes_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                items(displayRemotes, key = { it.name }) { remote ->
                    RemoteCard(
                        remote = remote,
                        onClick = {
                            if (isConnected) {
                                // Find the actual remote index from the original list
                                val originalRemote = irRemotes.find { 
                                    it.filename.removeSuffix(".ir").removeSuffix(".json") == remote.name 
                                }
                                if (originalRemote != null) {
                                    // Set the current remote before navigating
                                    viewModel.setCurrentIrRemote(originalRemote)
                                    onNavigateToRemoteDetail(originalRemote.index)
                                }
                            }
                        }
                    )
                }
                
                item {
                    BrutalistOutlinedButton(
                        text = stringResource(R.string.action_learn_remote),
                        onClick = onNavigateToLearn,
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = MaterialTheme.colorScheme.outline,
                        leadingIcon = { Icon(Icons.Default.SettingsRemote, contentDescription = null) },
                        enabled = isConnected && hasIrRx
                    )
                }
            }
        }
        
        // Feature Not Supported Overlay
        if (hasDeviceInfo && !isIrSupported) {
            FeatureNotSupportedOverlay(
                show = showOverlay,
                onProceed = { showOverlay = false },
                featureName = stringResource(R.string.title_ir),
                message = stringResource(R.string.msg_ir_not_supported)
            )
        }
        }
    }
}

/**
 * Remote card - Minimal style
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteCard(
    remote: RemotePreview,
    onClick: () -> Unit = {}
) {
    BrutalistCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.outline,
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderWidth = 1.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Remote icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SettingsRemote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = remote.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = remote.brand,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.action_dismiss),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Preview data class for remote
 */
data class RemotePreview(
    val name: String,
    val brand: String
)
