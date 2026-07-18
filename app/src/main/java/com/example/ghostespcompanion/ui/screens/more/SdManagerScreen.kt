package com.example.ghostespcompanion.ui.screens.more

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ghostespcompanion.data.repository.FileTransferProgress
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

/**
 * SD Manager Screen - Browse and manage SD card files
 * Based on JavaScript reference implementation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SdManagerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    // Use /mnt/ghostesp as default path to match JavaScript implementation
    var currentPath by remember { mutableStateOf("/mnt/ghostesp") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<GhostResponse.SdEntry?>(null) }
    var lastListedPath by remember { mutableStateOf<String?>(null) }
    var showOverlay by rememberSaveable { mutableStateOf(true) }
    
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionTransport by viewModel.connectionTransport.collectAsState()
    val sdEntries by viewModel.sdEntries.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED

    val isSdCardSupported = deviceInfo?.let {
        it.hasFeature(GhostResponse.DeviceFeature.SD_CARD_SPI) ||
        it.hasFeature(GhostResponse.DeviceFeature.SD_CARD_MMC)
    } ?: true
    val hasDeviceInfo = deviceInfo != null
    
    // Load files on initial composition and when path changes
    LaunchedEffect(currentPath, isConnected) {
        if (isConnected && lastListedPath != currentPath) {
            lastListedPath = currentPath
            viewModel.listSdFiles(currentPath)
        }
    }
    
    MainScreen(
        onBack = onBack,
        title = "SD Manager",
        actions = {
            IconButton(onClick = { viewModel.openDownloadsFolder(context) }) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = "Open Downloads Folder",
                    tint = primaryColor()
                )
            }
            IconButton(onClick = {
                if (isConnected && !isLoading) {
                    lastListedPath = currentPath
                    viewModel.listSdFiles(currentPath)
                }
            }) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
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
            // Connection Status Banner
            SdConnectionBanner(
                isConnected = isConnected,
                connectionTransport = connectionTransport,
                deviceName = "GhostESP",
                onConnect = { viewModel.connectFirstAvailable() }
            )
            
            // Current path breadcrumb
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SurfaceVariantDark
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentPath,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (currentPath != "/" && currentPath != "/mnt/ghostesp") {
                        TextButton(
                            onClick = {
                                val parentPath = currentPath.substringBeforeLast('/')
                                currentPath = if (parentPath.isEmpty()) "/" else parentPath
                            }
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Up")
                        }
                    }
                }
            }
            
            // Transfer progress
            when (val progress = transferProgress) {
                is FileTransferProgress.Idle -> { /* No progress UI */ }
                is FileTransferProgress.Downloading -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = primaryColor().copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading ${progress.fileName}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${progress.percentage}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress.percentage / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = primaryColor()
                            )
                        }
                    }
                }
                is FileTransferProgress.Uploading -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = tertiaryColor().copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Uploading ${progress.fileName}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${progress.percentage}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress.percentage / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Tertiary
                            )
                        }
                    }
                }
                is FileTransferProgress.Complete -> {
                    if (progress.success) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = successColor().copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = successColor())
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Downloaded ${progress.fileName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = successColor(),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    viewModel.openDownloadedFile(context, progress.fileName)
                                }) {
                                    Text("Open")
                                }
                                TextButton(onClick = { viewModel.openDownloadsFolder(context) }) {
                                    Text("Folder")
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = errorColor().copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = errorColor())
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Transfer failed: ${progress.error ?: "Unknown error"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = errorColor()
                                )
                            }
                        }
                    }
                }
                is FileTransferProgress.Cancelled -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Warning.copy(alpha = 0.1f)
                        )
                    ) {
                        Text(
                            text = "Transfer cancelled",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading files...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (sdEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FolderOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No files found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "The SD card may be empty or not inserted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sdEntries, key = { it.name }) { entry ->
                        SdEntryItem(
                            entry = entry,
                            currentPath = currentPath,
                            onClick = {
                                if (entry.isDirectory) {
                                    currentPath = if (currentPath == "/") {
                                        "/${entry.name}"
                                    } else {
                                        "$currentPath/${entry.name}"
                                    }
                                }
                            },
                            onDownload = {
                                if (!entry.isDirectory) {
                                    val fullPath = if (currentPath == "/") "/${entry.name}" else "$currentPath/${entry.name}"
                                    viewModel.downloadSdFile(context, fullPath, entry.name)
                                }
                            },
                            onDelete = {
                                selectedEntry = entry
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
            
            // Status message
            if (statusMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceVariantDark
                    )
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

        // Feature Not Supported Overlay (only shown when we have device info and SD is absent)
        if (hasDeviceInfo && !isSdCardSupported) {
            FeatureNotSupportedOverlay(
                show = showOverlay,
                onProceed = { showOverlay = false },
                featureName = "SD Card",
                message = "This device does not have an SD card. File storage and management require a connected SD card."
            )
        }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog && selectedEntry != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${if (selectedEntry!!.isDirectory) "Folder" else "File"}?") },
            text = { Text("Are you sure you want to delete \"${selectedEntry!!.name}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        if (isConnected) {
                            // Build full path
                            val fullPath = if (currentPath == "/") "/${selectedEntry!!.name}" else "$currentPath/${selectedEntry!!.name}"
                            viewModel.deleteSdEntry(fullPath)
                            // Force refresh
                            lastListedPath = null
                        }
                        showDeleteDialog = false
                        selectedEntry = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = errorColor())
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // New folder dialog
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank() && isConnected) {
                            val newPath = if (currentPath == "/") "/$folderName" else "$currentPath/$folderName"
                            viewModel.createSdDirectory(newPath)
                            lastListedPath = null // Force refresh
                        }
                        showNewFolderDialog = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * SD Entry item component
 */
@Composable
private fun SdEntryItem(
    entry: GhostResponse.SdEntry,
    currentPath: String,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    BrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.outline,
        backgroundColor = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (entry.isDirectory) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Default.Folder else getFileIcon(entry.name),
                        contentDescription = null,
                        tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // File info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.isDirectory && entry.size != null) {
                    Text(
                        text = formatFileSize(entry.size!!),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Actions
            if (!entry.isDirectory) {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = primaryColor().copy(alpha = 0.7f)
                    )
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = errorColor().copy(alpha = 0.7f)
                )
            }
            
            if (entry.isDirectory) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Get appropriate icon for file type
 */
private fun getFileIcon(filename: String): ImageVector {
    return when (filename.substringAfterLast('.').lowercase()) {
        "txt", "md", "log" -> Icons.Default.Description
        "json", "xml", "yaml", "yml" -> Icons.Default.DataObject
        "py", "js", "kt", "java", "c", "cpp", "h" -> Icons.Default.Code
        "html", "css" -> Icons.Default.Web
        "jpg", "jpeg", "png", "gif", "bmp" -> Icons.Default.Image
        "mp3", "wav", "ogg" -> Icons.Default.AudioFile
        "mp4", "avi", "mkv" -> Icons.Default.VideoFile
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
        "bin", "hex", "elf" -> Icons.Default.Memory
        "pcap", "pcapng" -> Icons.Default.Insights
        "csv" -> Icons.Default.TableChart
        "ir" -> Icons.Default.SettingsRemote
        "nfc" -> Icons.Default.Nfc
        else -> Icons.Default.InsertDriveFile
    }
}

/**
 * Format file size to human readable format
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }
}

/**
 * SD Connection Banner component
 */
@Composable
private fun SdConnectionBanner(
    isConnected: Boolean,
    connectionTransport: SerialManager.ConnectionTransport,
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
                    if (isConnected) Icons.Default.SdCard else Icons.Default.SdCardAlert,
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
                            text = "SD card ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when (connectionTransport) {
                                SerialManager.ConnectionTransport.USB -> "Connected over USB"
                                SerialManager.ConnectionTransport.BLE -> "Connected over wireless bridge"
                                SerialManager.ConnectionTransport.NONE -> "Connection active"
                            },
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Connect", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
