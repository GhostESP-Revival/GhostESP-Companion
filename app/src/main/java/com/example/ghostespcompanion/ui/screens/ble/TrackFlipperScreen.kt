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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun TrackFlipperScreen(
    flipperIndex: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionTransport by viewModel.connectionTransport.collectAsState()
    val flipperDevices by viewModel.flipperDevices.collectAsState()
    val flipperTrackData by viewModel.flipperTrackData.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    val privacyMode = appSettings.privacyMode

    val flipperDevice = flipperDevices.find { it.index == flipperIndex }

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
            viewModel.trackFlipper(flipperIndex)
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

    val targetName = flipperDevice?.name ?: "Flipper Zero"
    val targetMac = flipperDevice?.mac

    MainScreen(
        onBack = onBack,
        title = "Track Flipper",
        actions = {
            IconButton(onClick = {
                if (isTracking) {
                    viewModel.stopAll()
                    isTracking = false
                } else {
                    viewModel.trackFlipper(flipperIndex)
                    isTracking = true
                }
            }) {
                Icon(
                    if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isTracking) "Stop" else "Start",
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
            TrackFlipperBanner(
                isConnected = isConnected,
                connectionTransport = connectionTransport,
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
                        text = "Tracking Flipper",
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
                    flipperDevice?.flipperType?.let { type ->
                        Text(
                            text = type,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantDark
                        )
                    }
                }
            }

            if (isTracking && flipperTrackData != null) {
                FlipperSignalDisplay(
                    trackData = flipperTrackData!!,
                    pulseScale = pulseScale
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
                        text = "Waiting for signal data...",
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
                        text = "Press play to start tracking",
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
                        text = "How to use",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Move closer to increase signal strength\n• Move away to decrease signal strength\n• Use proximity label to gauge distance",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )
                }
            }
        }
    }
}

@Composable
private fun FlipperSignalDisplay(
    trackData: GhostResponse.FlipperTrackData,
    pulseScale: Float
) {
    val rssi = trackData.rssi
    val proximity = trackData.proximity

    val signalQuality = ((rssi + 100).coerceIn(0, 100))

    val signalColor = when {
        rssi >= -50 -> successColor()
        rssi >= -60 -> primaryColor()
        rssi >= -70 -> warningColor()
        else -> errorColor()
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
                    text = "$rssi",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = signalColor
                )
                Text(
                    text = "dBm",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceVariantDark
                )
            }
        }

        if (proximity != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = proximity.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = signalColor
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Signal Quality",
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
private fun TrackFlipperBanner(
    isConnected: Boolean,
    connectionTransport: SerialManager.ConnectionTransport,
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
                        connectionTransport == SerialManager.ConnectionTransport.BLE -> Icons.Default.BluetoothConnected
                        connectionTransport == SerialManager.ConnectionTransport.USB -> Icons.Default.Usb
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
                            !isConnected -> "Not Connected"
                            isTracking -> "Tracking Active"
                            else -> "Ready to Track"
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
                            text = if (isTracking) "Move to find device" else "Press play to start",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariantDark
                        )
                    }
                }
            }

            if (!isConnected) {
                BrutalistButton(
                    text = "Connect",
                    onClick = onConnect,
                    containerColor = primaryColor(),
                    modifier = Modifier
                )
            }
        }
    }
}
