package com.example.ghostespcompanion.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

/**
 * Terminal Screen - Raw Serial Console
 * 
 * Provides direct access to the GhostESP serial CLI.
 * Allows sending commands and viewing raw responses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var commandText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // Collect state from ViewModel
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionTransport by viewModel.connectionTransport.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    
    // Collect terminal lines from ViewModel (persists across navigation)
    val terminalLines by viewModel.terminalLines.collectAsState()
    
    // Auto-scroll when new lines are added - use instant scroll instead of animation
    // to avoid frame drops during rapid output
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            // Use scrollToItem for instant positioning instead of animateScrollToItem
            // which causes frame drops during rapid terminal output
            listState.scrollToItem((terminalLines.size + 2).coerceAtLeast(0)) // +2 for welcome messages
        }
    }
    
    MainScreen(
        onBack = onBack,
        title = "Terminal",
        actions = {
            // Connection status
            Icon(
                imageVector = when {
                    !isConnected -> Icons.Default.UsbOff
                    connectionTransport == SerialManager.ConnectionTransport.BLE -> Icons.Default.BluetoothConnected
                    else -> Icons.Default.Usb
                },
                contentDescription = when {
                    !isConnected -> "Disconnected"
                    connectionTransport == SerialManager.ConnectionTransport.BLE -> "Connected wirelessly"
                    else -> "Connected over USB"
                },
                tint = if (isConnected) successColor() else errorColor()
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Clear terminal
            IconButton(onClick = { viewModel.clearTerminal() }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Clear",
                    tint = primaryColor()
                )
            }
            // Connect/Disconnect
            IconButton(onClick = {
                if (isConnected) {
                    viewModel.disconnect()
                } else {
                    // If in ERROR state, force disconnect first to clear the stuck state
                    if (connectionState == SerialManager.ConnectionState.ERROR) {
                        viewModel.forceDisconnect()
                    }
                    viewModel.connectFirstAvailable()
                }
            }) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.LinkOff else Icons.Default.Link,
                    contentDescription = if (isConnected) "Disconnect" else "Connect",
                    tint = primaryColor()
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Terminal output
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Color(0xFF0D0D0D)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Welcome message
                    item {
                        TerminalLine(
                            text = "GhostESP Terminal",
                            color = MaterialTheme.colorScheme.primary,
                            isCommand = false
                        )
                    }
                    item {
                        TerminalLine(
                            text = "Type 'help' for available commands",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            isCommand = false
                        )
                    }
                    item {
                        TerminalLine(
                            text = "----------------------------------------",
                            color = MaterialTheme.colorScheme.outline,
                            isCommand = false
                        )
                    }
                    
                    itemsIndexed(
                        items = terminalLines,
                        key = { index, line -> "${index}_${line.hashCode()}" },
                        contentType = { _, _ -> "terminal_line" }
                    ) { index, line ->
                        val isCommand = line.startsWith("> ")
                        TerminalLine(
                            text = line,
                            color = if (isCommand) MaterialTheme.colorScheme.primary else Color(0xFF00FF00),
                            isCommand = isCommand
                        )
                    }
                }
            }
            
            // Command input
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prompt indicator
                    Text(
                        text = ">",
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Command input field
                    BasicTextField(
                        value = commandText,
                        onValueChange = { commandText = it },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (commandText.isNotBlank() && isConnected) {
                                    viewModel.sendRawCommand(commandText)
                                    commandText = ""
                                }
                            }
                        ),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box {
                                if (commandText.isEmpty()) {
                                    Text(
                                        text = "Enter command...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Send button
                    IconButton(
                        onClick = {
                            if (commandText.isNotBlank() && isConnected) {
                                viewModel.sendRawCommand(commandText)
                                commandText = ""
                            }
                        },
                        enabled = commandText.isNotBlank() && isConnected
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (commandText.isNotBlank() && isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Terminal line component
 */
@Composable
private fun TerminalLine(
    text: String,
    color: Color,
    isCommand: Boolean
) {
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        fontWeight = if (isCommand) FontWeight.Medium else FontWeight.Normal
    )
}
