package com.example.ghostespcompanion.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.components.BrutalistButton
import com.example.ghostespcompanion.ui.components.BrutalistCard
import com.example.ghostespcompanion.ui.components.BrutalistOutlinedButton
import com.example.ghostespcompanion.ui.components.FeatureNotSupportedOverlay
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.theme.errorColor
import com.example.ghostespcompanion.ui.theme.onPrimaryColor
import com.example.ghostespcompanion.ui.theme.primaryColor
import com.example.ghostespcompanion.ui.theme.successColor
import com.example.ghostespcompanion.ui.theme.warningColor
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

@Composable
fun BadUsbScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var runningScript by remember { mutableStateOf<String?>(null) }
    var keyboardActive by remember { mutableStateOf(false) }
    var jigglerActive by remember { mutableStateOf(false) }
    var textToType by remember { mutableStateOf("") }
    var showUnsupportedOverlay by remember { mutableStateOf(true) }

    val connectionState by viewModel.connectionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val scripts by viewModel.badUsbScripts.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    val isBadUsbSupported = deviceInfo?.hasFeature(GhostResponse.DeviceFeature.BADUSB) ?: true
    val hasDeviceInfo = deviceInfo != null

    // Stop all BadUSB operations when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            if (runningScript != null) {
                viewModel.stopBadUsb()
            }
            if (keyboardActive) {
                viewModel.stopBadUsbKeyboard()
            }
            if (jigglerActive) {
                viewModel.stopBadUsbJiggler()
            }
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            viewModel.listBadUsbScripts()
        }
    }

    MainScreen(
        onBack = onBack,
        title = "BadUSB",
        actions = {
            IconButton(
                onClick = { viewModel.listBadUsbScripts() },
                enabled = isConnected
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh scripts", tint = primaryColor())
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    UsbConnectionBanner(
                        isConnected = isConnected,
                        deviceName = "GhostESP",
                        onConnect = { viewModel.connectFirstAvailable() }
                    )
                }

                if (runningScript != null) {
                    item {
                        ActiveBadUsbCard(
                            label = "Running: $runningScript",
                            onStop = {
                                viewModel.stopBadUsb()
                                runningScript = null
                            },
                            enabled = isConnected
                        )
                    }
                }

                item {
                    Text(
                        text = "Scripts (${scripts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor()
                    )
                }

                if (!isConnected) {
                    item {
                        EmptyBadUsbCard("Connect to a GhostESP device to list and run BadUSB scripts.")
                    }
                } else if (scripts.isEmpty()) {
                    item {
                        EmptyBadUsbCard("No scripts reported by firmware. Put DuckyScript files in /mnt/ghostesp/badusb/ and refresh.")
                    }
                } else {
                    items(scripts, key = { it }) { script ->
                        BadUsbScriptCard(
                            script = script,
                            isRunning = runningScript == script,
                            enabled = isConnected && runningScript == null,
                            onRun = {
                                viewModel.runBadUsbScript(script.toFirmwareBadUsbRunArg())
                                runningScript = script
                            }
                        )
                    }
                }

                item {
                    DirectKeyboardCard(
                        isConnected = isConnected,
                        keyboardActive = keyboardActive,
                        textToType = textToType,
                        onTextChange = { textToType = it },
                        onKeyboardToggle = {
                            if (keyboardActive) {
                                viewModel.stopBadUsbKeyboard()
                            } else {
                                viewModel.startBadUsbKeyboard()
                            }
                            keyboardActive = !keyboardActive
                        },
                        onType = {
                            if (textToType.isNotBlank()) {
                                viewModel.typeBadUsbText(textToType)
                                textToType = ""
                                keyboardActive = true
                            }
                        }
                    )
                }

                item {
                    MouseJigglerCard(
                        isConnected = isConnected,
                        jigglerActive = jigglerActive,
                        onToggle = {
                            if (jigglerActive) {
                                viewModel.stopBadUsbJiggler()
                            } else {
                                viewModel.startBadUsbJiggler()
                            }
                            jigglerActive = !jigglerActive
                        }
                    )
                }

                statusMessage?.takeIf { it.contains("BadUSB", ignoreCase = true) || it.startsWith("BadUSB scripts") }?.let { message ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                item {
                    WarningCard()
                }
            }

            if (hasDeviceInfo && !isBadUsbSupported) {
                FeatureNotSupportedOverlay(
                    show = showUnsupportedOverlay,
                    onProceed = { showUnsupportedOverlay = false },
                    featureName = "BadUSB",
                    message = "This firmware/device does not report BadUSB support. USB HID commands may fail."
                )
            }
        }
    }
}

private fun String.toFirmwareBadUsbRunArg(): String {
    return if (contains("builtin", ignoreCase = true)) "builtin" else this
}

@Composable
private fun ActiveBadUsbCard(
    label: String,
    onStop: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = warningColor().copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = warningColor())
            BrutalistButton(
                text = "Stop",
                onClick = onStop,
                enabled = enabled,
                containerColor = errorColor(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun EmptyBadUsbCard(message: String) {
    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BadUsbScriptCard(
    script: String,
    isRunning: Boolean,
    enabled: Boolean,
    onRun: () -> Unit
) {
    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = if (isRunning) warningColor() else primaryColor(),
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = script,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isRunning) "Running" else "Firmware script",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            BrutalistButton(
                text = if (isRunning) "Running" else "Run",
                onClick = onRun,
                enabled = enabled,
                containerColor = if (isRunning) warningColor() else primaryColor(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun DirectKeyboardCard(
    isConnected: Boolean,
    keyboardActive: Boolean,
    textToType: String,
    onTextChange: (String) -> Unit,
    onKeyboardToggle: () -> Unit,
    onType: () -> Unit
) {
    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Keyboard, contentDescription = null, tint = primaryColor())
                Spacer(modifier = Modifier.width(8.dp))
                Text("Direct Keyboard", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "Uses firmware commands: badusb keyboard_start, badusb type, badusb keyboard_stop.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalistOutlinedButton(
                    text = if (keyboardActive) "Stop Keyboard" else "Start Keyboard",
                    onClick = onKeyboardToggle,
                    enabled = isConnected,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = textToType,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected,
                label = { Text("Text to type") },
                singleLine = true
            )
            BrutalistButton(
                text = "Type Text",
                onClick = onType,
                enabled = isConnected && textToType.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Keyboard, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun MouseJigglerCard(
    isConnected: Boolean,
    jigglerActive: Boolean,
    onToggle: () -> Unit
) {
    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mouse, contentDescription = null, tint = if (jigglerActive) warningColor() else primaryColor())
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Mouse Jiggler", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (jigglerActive) "Running" else "Uses badusb jiggle_start/stop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            BrutalistButton(
                text = if (jigglerActive) "Stop" else "Start",
                onClick = onToggle,
                enabled = isConnected,
                containerColor = if (jigglerActive) errorColor() else primaryColor(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun WarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = errorColor().copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = errorColor(), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Only run BadUSB payloads on systems you own or have explicit permission to test.",
                style = MaterialTheme.typography.bodySmall,
                color = errorColor()
            )
        }
    }
}

@Composable
private fun UsbConnectionBanner(
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
                    if (isConnected) Icons.Default.Usb else Icons.Default.UsbOff,
                    contentDescription = null,
                    tint = if (isConnected) successColor() else errorColor()
                )
                Column {
                    Text(
                        text = if (isConnected) "$deviceName Connected" else "Not Connected",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) successColor() else errorColor()
                    )
                    if (isConnected) {
                        Text(
                            text = "Ready for BadUSB commands",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("Connect", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
