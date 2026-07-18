package com.example.ghostespcompanion.ui.screens.ir

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

private const val IR_REMOTES_BASE_PATH = "/mnt/ghostesp/infrared/remotes"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrRemoteDetailScreen(
    remoteIndex: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var isLoadingButtons by remember { mutableStateOf(true) }
    var transmittingButtonIndex by remember { mutableStateOf<Int?>(null) }
    var showLearnDialog by remember { mutableStateOf(false) }
    var isLearning by remember { mutableStateOf(false) }
    var signalWasSaved by remember { mutableStateOf(false) }
    
    val connectionState by viewModel.connectionState.collectAsState()
    val irButtons by viewModel.irButtons.collectAsState()
    val currentRemote by viewModel.currentIrRemote.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    
    val irLearnedSignal by viewModel.irLearnedSignal.collectAsState()
    val irLearnSavedPath by viewModel.irLearnSavedPath.collectAsState()
    val irLearnStatus by viewModel.irLearnStatus.collectAsState()
    
    val remotePath = currentRemote?.filename?.let { filename ->
        if (filename.startsWith("/")) filename else "$IR_REMOTES_BASE_PATH/$filename"
    }
    
    LaunchedEffect(remoteIndex, isConnected) {
        if (isConnected) {
            isLoadingButtons = true
            viewModel.clearIrButtons()
            viewModel.showIrRemote(remoteIndex)
        }
    }
    
    LaunchedEffect(irButtons) {
        if (irButtons.isNotEmpty()) {
            isLoadingButtons = false
        }
    }
    
    LaunchedEffect(isLoadingButtons) {
        if (isLoadingButtons) {
            kotlinx.coroutines.delay(5000)
            if (irButtons.isEmpty()) {
                isLoadingButtons = false
            }
        }
    }
    
    LaunchedEffect(statusMessage) {
        if (statusMessage != null && transmittingButtonIndex != null) {
            kotlinx.coroutines.delay(1500)
            transmittingButtonIndex = null
        }
    }
    
    LaunchedEffect(irLearnStatus) {
        when (irLearnStatus) {
            "STARTED", "WAITING" -> isLearning = true
            "TIMEOUT" -> isLearning = false
        }
    }
    
    LaunchedEffect(irLearnedSignal) {
        if (irLearnedSignal != null && isLearning) {
            isLearning = false
            signalWasSaved = true
        }
    }
    
    val remoteName = currentRemote?.filename?.removeSuffix(".ir")?.removeSuffix(".json") 
        ?: "Remote $remoteIndex"
    
    MainScreen(
        onBack = onBack,
        title = remoteName,
        actions = {
            IconButton(onClick = {
                if (isConnected) {
                    isLoadingButtons = true
                    viewModel.clearIrButtons()
                    viewModel.showIrRemote(remoteIndex)
                }
            }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh), tint = primaryColor())
            }
        },
        floatingActionButton = {
            if (isConnected && !isLearning && transmittingButtonIndex == null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.clearIrLearnState()
                        signalWasSaved = false
                        showLearnDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.label_add_button)) },
                    containerColor = primaryColor(),
                    contentColor = OnPrimary
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            IrDetailConnectionBanner(
                isConnected = isConnected,
                deviceName = stringResource(R.string.app_name_short),
                onConnect = { viewModel.connectFirstAvailable() }
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                BrutalistSectionHeader(
                    title = if (isLoadingButtons) stringResource(R.string.msg_loading_buttons) else "${stringResource(R.string.title_remotes).replace("пульты", "Кнопки").replace("Remotes", "Buttons")} (${irButtons.size})",
                    accentColor = primaryColor()
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                when {
                    isLoadingButtons -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = primaryColor())
                        }
                    }
                    irButtons.isEmpty() -> {
                        BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.SettingsRemote, contentDescription = null, modifier = Modifier.size(48.dp), tint = OnSurfaceVariantDark)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(stringResource(R.string.msg_no_buttons_found), style = MaterialTheme.typography.titleMedium, color = OnSurfaceDark)
                                Text(stringResource(R.string.msg_add_button_hint), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark)
                            }
                        }
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(irButtons, key = { it.index }) { button ->
                                IrButtonCard(
                                    button = button,
                                    isTransmitting = transmittingButtonIndex == button.index,
                                    isEnabled = isConnected && transmittingButtonIndex == null && !isLearning,
                                    onClick = {
                                        if (isConnected && transmittingButtonIndex == null) {
                                            transmittingButtonIndex = button.index
                                            viewModel.sendIrButton(remoteIndex, button.index)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                statusMessage?.let { status ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when {
                                    status.contains("Error", ignoreCase = true) -> Icons.Default.Error
                                    status.contains("OK", ignoreCase = true) -> Icons.Default.CheckCircle
                                    status.contains("sending", ignoreCase = true) -> Icons.Default.Send
                                    else -> Icons.Default.Info
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = when {
                                    status.contains("Error", ignoreCase = true) -> errorColor()
                                    status.contains("OK", ignoreCase = true) -> successColor()
                                    else -> primaryColor()
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(status, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDark)
                        }
                    }
                }
            }
        }
    }
    
    if (showLearnDialog) {
        IrLearnButtonDialog(
            isLearning = isLearning,
            learnedSignal = irLearnedSignal,
            remoteName = remoteName,
            onDismiss = {
                if (!isLearning) {
                    showLearnDialog = false
                    if (signalWasSaved) {
                        isLoadingButtons = true
                        viewModel.clearIrButtons()
                        viewModel.showIrRemote(remoteIndex)
                    }
                    viewModel.clearIrLearnState()
                    signalWasSaved = false
                }
            },
            onStartLearn = {
                isLearning = true
                viewModel.clearIrLearnState()
                signalWasSaved = false
                viewModel.learnIr(remotePath)
            },
            onStopLearn = {
                viewModel.stopAll()
                viewModel.clearIrLearnState()
                isLearning = false
            }
        )
    }
}

@Composable
private fun IrLearnButtonDialog(
    isLearning: Boolean,
    learnedSignal: GhostResponse.IrLearned?,
    remoteName: String,
    onDismiss: () -> Unit,
    onStartLearn: () -> Unit,
    onStopLearn: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isLearning) stringResource(R.string.status_connecting).replace("…", "") else stringResource(R.string.title_add_button_to, remoteName)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLearning) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp), color = primaryColor(), strokeWidth = 4.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.msg_point_remote_hint), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDark)
                    Text(stringResource(R.string.msg_press_to_capture_10s), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark)
                } else if (learnedSignal != null) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = successColor(), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.msg_signal_captured), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = successColor())
                    Spacer(modifier = Modifier.height(8.dp))
                    learnedSignal.protocol?.let { Text("${stringResource(R.string.label_protocol)}: $it", style = MaterialTheme.typography.bodySmall) }
                    learnedSignal.address?.let { Text("${stringResource(R.string.label_address)}: $it", style = MaterialTheme.typography.bodySmall) }
                    learnedSignal.command?.let { Text("${stringResource(R.string.label_command)}: $it", style = MaterialTheme.typography.bodySmall) }
                    learnedSignal.rawSamples?.let { Text("${stringResource(R.string.label_raw_samples)}: $it", style = MaterialTheme.typography.bodySmall) }
                } else {
                    Icon(Icons.Default.SettingsRemote, contentDescription = null, tint = OnSurfaceVariantDark, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.msg_capture_new_signal), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDark)
                    Text(stringResource(R.string.msg_append_to_file_hint), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark)
                }
            }
        },
        confirmButton = {
            if (isLearning) {
                TextButton(onClick = onStopLearn) { Text(stringResource(R.string.action_cancel), color = errorColor()) }
            } else {
                Button(
                    onClick = onStartLearn,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor(), contentColor = OnPrimary)
                ) {
                    Text(if (learnedSignal != null) stringResource(R.string.action_capture_another) else stringResource(R.string.action_start_learning))
                }
            }
        },
        dismissButton = {
            if (!isLearning) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
            }
        }
    )
}

@Composable
private fun IrButtonCard(
    button: GhostResponse.IrButton,
    isTransmitting: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    BrutalistCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        borderColor = when {
            isTransmitting -> primaryColor()
            !isEnabled -> OutlineDark.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.outline
        },
        backgroundColor = if (isTransmitting) primaryColor().copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when {
                    isTransmitting -> primaryColor().copy(alpha = 0.2f)
                    !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isTransmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = primaryColor(), strokeWidth = 2.dp)
                    } else {
                        Icon(getButtonIcon(button.name), contentDescription = null, tint = if (isEnabled) MaterialTheme.colorScheme.primary else OnSurfaceVariantDark, modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(
                    text = formatButtonName(button.name),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else OnSurfaceVariantDark
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    button.protocol?.let { proto ->
                        Text(proto, style = MaterialTheme.typography.labelSmall, color = primaryColor())
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("#${button.index}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariantDark)
                }
            }
            
            if (isTransmitting) {
                Text(stringResource(R.string.msg_transmitting), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = primaryColor())
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.label_start), tint = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else OnSurfaceVariantDark.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun getButtonIcon(name: String) = when {
    name.contains("power", ignoreCase = true) -> Icons.Default.PowerSettingsNew
    name.contains("vol", ignoreCase = true) || name.contains("volume", ignoreCase = true) -> {
        if (name.contains("up", ignoreCase = true)) Icons.Default.VolumeUp
        else if (name.contains("down", ignoreCase = true)) Icons.Default.VolumeDown
        else Icons.Default.VolumeUp
    }
    name.contains("ch", ignoreCase = true) || name.contains("channel", ignoreCase = true) -> Icons.Default.Tv
    name.contains("menu", ignoreCase = true) -> Icons.Default.Menu
    name.contains("ok", ignoreCase = true) || name.contains("enter", ignoreCase = true) -> Icons.Default.CheckCircle
    name.contains("up", ignoreCase = true) -> Icons.Default.KeyboardArrowUp
    name.contains("down", ignoreCase = true) -> Icons.Default.KeyboardArrowDown
    name.contains("left", ignoreCase = true) -> Icons.Default.KeyboardArrowLeft
    name.contains("right", ignoreCase = true) -> Icons.Default.KeyboardArrowRight
    name.contains("mute", ignoreCase = true) -> Icons.Default.VolumeOff
    name.contains("play", ignoreCase = true) -> Icons.Default.PlayArrow
    name.contains("pause", ignoreCase = true) -> Icons.Default.Pause
    name.contains("stop", ignoreCase = true) -> Icons.Default.Stop
    name.contains("rec", ignoreCase = true) || name.contains("record", ignoreCase = true) -> Icons.Default.FiberManualRecord
    name.contains("home", ignoreCase = true) -> Icons.Default.Home
    name.contains("back", ignoreCase = true) -> Icons.Default.ArrowBack
    name.contains("exit", ignoreCase = true) || name.contains("quit", ignoreCase = true) -> Icons.Default.Close
    name.contains("input", ignoreCase = true) || name.contains("source", ignoreCase = true) -> Icons.Default.Input
    name.contains("setup", ignoreCase = true) || name.contains("settings", ignoreCase = true) -> Icons.Default.Settings
    name.contains("info", ignoreCase = true) -> Icons.Default.Info
    name.contains("guide", ignoreCase = true) || name.contains("epg", ignoreCase = true) -> Icons.Default.List
    name.contains("fav", ignoreCase = true) || name.contains("favorite", ignoreCase = true) -> Icons.Default.Favorite
    name.contains("sleep", ignoreCase = true) || name.contains("timer", ignoreCase = true) -> Icons.Default.Bedtime
    name.contains("audio", ignoreCase = true) || name.contains("sound", ignoreCase = true) -> Icons.Default.Speaker
    name.contains("subtitle", ignoreCase = true) || name.contains("cc", ignoreCase = true) -> Icons.Default.Subtitles
    name.contains("zoom", ignoreCase = true) || name.contains("aspect", ignoreCase = true) -> Icons.Default.ZoomIn
    name.contains("prev", ignoreCase = true) || name.contains("rewind", ignoreCase = true) -> Icons.Default.FastRewind
    name.contains("next", ignoreCase = true) || name.contains("forward", ignoreCase = true) -> Icons.Default.FastForward
    name.contains("eject", ignoreCase = true) -> Icons.Default.Eject
    name.contains("1", ignoreCase = true) -> Icons.Default.LooksOne
    name.contains("2", ignoreCase = true) -> Icons.Default.LooksTwo
    name.contains("3", ignoreCase = true) -> Icons.Default.Looks3
    name.contains("4", ignoreCase = true) -> Icons.Default.Looks4
    name.contains("5", ignoreCase = true) -> Icons.Default.Looks5
    name.contains("6", ignoreCase = true) -> Icons.Default.Looks6
    else -> Icons.Default.SettingsRemote
}

private fun formatButtonName(name: String): String {
    return name.replace("_", " ").replace("-", " ").split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun IrDetailConnectionBanner(
    isConnected: Boolean,
    deviceName: String,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isConnected) successColor().copy(alpha = 0.1f) else errorColor().copy(alpha = 0.1f)),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isConnected) Icons.Default.Usb else Icons.Default.UsbOff, contentDescription = null, tint = if (isConnected) successColor() else errorColor())
                Column {
                    Text(
                        text = if (isConnected) stringResource(R.string.status_connected_device, deviceName) else stringResource(R.string.status_disconnected),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) successColor() else errorColor()
                    )
                    if (isConnected) {
                        Text(stringResource(R.string.msg_tap_to_transmit), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariantDark)
                    }
                }
            }
            
            if (!isConnected) {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor(), contentColor = OnPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.action_connect), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
