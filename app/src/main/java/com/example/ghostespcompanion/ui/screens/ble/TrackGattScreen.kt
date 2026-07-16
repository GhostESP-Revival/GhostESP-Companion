package com.example.ghostespcompanion.ui.screens.ble

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.utils.censorMac
import com.example.ghostespcompanion.ui.utils.censorDevice
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackGattScreen(
    gattIndex: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val gattDevices by viewModel.gattDevices.collectAsState()
    val trackData by viewModel.trackData.collectAsState()
    val trackHeader by viewModel.trackHeader.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    val privacyMode = appSettings.privacyMode
    
    val gattDevice = gattDevices.find { it.index == gattIndex }
    
    var isTracking by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "signal")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    LaunchedEffect(isConnected) {
        if (isConnected && !isTracking) {
            viewModel.selectAndTrackGatt(gattIndex.toString())
            isTracking = true
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            if (isTracking) {
                viewModel.stopAll()
            }
        }
    }
    
    val targetName = trackHeader?.targetName ?: gattDevice?.name ?: stringResource(R.string.label_unknown)
    val targetMac = trackHeader?.targetBssid ?: gattDevice?.mac
    
    MainScreen(
        onBack = onBack,
        title = stringResource(R.string.title_track_gatt_device),
        actions = {
            IconButton(onClick = {
                if (isTracking) {
                    viewModel.stopAll()
                    isTracking = false
                } else {
                    viewModel.selectAndTrackGatt(gattIndex.toString())
                    isTracking = true
                }
            }) {
                Icon(
                    if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isTracking) stringResource(R.string.action_stop) else stringResource(R.string.action_start),
                    tint = if (isTracking) errorColor() else primaryColor()
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TrackGattBanner(
                isConnected = isConnected,
                isTracking = isTracking,
                onConnect = { viewModel.connectFirstAvailable() }
            )
            
            BrutalistCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.label_tracking_device),
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariantDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = targetName.censorDevice(privacyMode),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    if (targetMac != null) {
                        Text(
                            text = targetMac.censorMac(privacyMode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariantDark
                        )
                    }
                    gattDevice?.type?.let { type ->
                        Text(
                            text = type,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantDark
                        )
                    }
                }
            }
            
            if (isTracking && trackData != null) {
                GattSignalDisplay(
                    trackData = trackData!!,
                    pulseScale = if (isTracking) pulseScale else 1f,
                    privacyMode = privacyMode
                )
            } else if (isTracking) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = primaryColor()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.msg_waiting_signal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariantDark
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = OnSurfaceVariantDark
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.msg_press_play_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariantDark
                    )
                }
            }
            
            BrutalistCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_how_to_use),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.msg_track_instructions),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )
                }
            }
        }
    }
}

@Composable
private fun GattSignalDisplay(
    trackData: GhostResponse.TrackData,
    pulseScale: Float,
    privacyMode: Boolean
) {
    val rssi = trackData.rssi
    val minRssi = trackData.minRssi
    val maxRssi = trackData.maxRssi
    val direction = trackData.direction
    
    val signalQuality = ((rssi + 100).coerceIn(0, 100))
    
    val signalColor = when {
        rssi >= -50 -> successColor()
        rssi >= -60 -> primaryColor()
        rssi >= -70 -> warningColor()
        else -> errorColor()
    }
    
    val directionColor = when (direction) {
        GhostResponse.TrackDirection.CLOSER -> successColor()
        GhostResponse.TrackDirection.FARTHER -> errorColor()
        GhostResponse.TrackDirection.STABLE -> OnSurfaceVariantDark
    }
    
    val directionIcon = when (direction) {
        GhostResponse.TrackDirection.CLOSER -> Icons.Default.ArrowUpward
        GhostResponse.TrackDirection.FARTHER -> Icons.Default.ArrowDownward
        GhostResponse.TrackDirection.STABLE -> Icons.Default.Remove
    }
    
    val directionText = when (direction) {
        GhostResponse.TrackDirection.CLOSER -> stringResource(R.string.msg_closer)
        GhostResponse.TrackDirection.FARTHER -> stringResource(R.string.msg_farther)
        GhostResponse.TrackDirection.STABLE -> stringResource(R.string.msg_stable)
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .scale(pulseScale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            signalColor.copy(alpha = 0.3f),
                            signalColor.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${rssi}",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = signalColor
                )
                Text(
                    text = stringResource(R.string.label_dbm),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceVariantDark
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                directionIcon,
                contentDescription = null,
                tint = directionColor,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = directionText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = directionColor
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GattMinMaxCard(
                label = stringResource(R.string.label_min),
                value = minRssi,
                color = Error
            )
            GattMinMaxCard(
                label = stringResource(R.string.label_max),
                value = maxRssi,
                color = Success
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.label_signal_quality),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariantDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { signalQuality / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = signalColor,
                trackColor = SurfaceVariantDark
            )
        }
    }
}

@Composable
private fun GattMinMaxCard(
    label: String,
    value: Int,
    color: Color
) {
    BrutalistCard {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariantDark
            )
            Text(
                text = "${value} ${stringResource(R.string.label_dbm)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun TrackGattBanner(
    isConnected: Boolean,
    isTracking: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !isConnected -> errorColor().copy(alpha = 0.1f)
                isTracking -> successColor().copy(alpha = 0.1f)
                else -> warningColor().copy(alpha = 0.1f)
            }
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
                        isTracking -> Icons.Default.BluetoothSearching
                        else -> Icons.Default.Bluetooth
                    },
                    contentDescription = null,
                    tint = when {
                        !isConnected -> errorColor()
                        isTracking -> successColor()
                        else -> warningColor()
                    }
                )
                Column {
                    Text(
                        text = when {
                            !isConnected -> stringResource(R.string.status_disconnected)
                            isTracking -> stringResource(R.string.label_tracking_active)
                            else -> stringResource(R.string.label_ready_to_track)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            !isConnected -> errorColor()
                            isTracking -> successColor()
                            else -> warningColor()
                        }
                    )
                    if (isConnected) {
                        Text(
                            text = if (isTracking) stringResource(R.string.msg_move_to_find_device) else stringResource(R.string.msg_press_play_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariantDark
                        )
                    }
                }
            }
            
            if (!isConnected) {
                BrutalistButton(
                    text = stringResource(R.string.action_connect),
                    onClick = onConnect,
                    containerColor = primaryColor(),
                    modifier = Modifier
                )
            }
        }
    }
}
