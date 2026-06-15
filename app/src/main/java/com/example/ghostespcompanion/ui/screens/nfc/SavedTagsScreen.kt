package com.example.ghostespcompanion.ui.screens.nfc

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.utils.censorNfc
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

/**
 * Saved Tags Screen - View and manage saved NFC tags
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedTagsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var selectedTag by remember { mutableStateOf<SavedNfcTag?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    val connectionState by viewModel.connectionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    val privacyMode = appSettings.privacyMode
    
    val savedTags = remember { mutableStateListOf<SavedNfcTag>() }
    
    MainScreen(
        onBack = onBack,
        title = "Saved Tags",
        actions = {
            IconButton(onClick = {
                if (isConnected) {
                    viewModel.scanNfc()
                }
            }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Scan New Tag",
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
            // Connection Status Banner
            NfcConnectionBanner(
                isConnected = isConnected,
                deviceName = "GhostESP",
                onConnect = { viewModel.connectFirstAvailable() }
            )
            
            if (savedTags.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.FolderOff,
                        contentDescription = null,
                        tint = OnSurfaceVariantDark,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Saved Tags",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scan NFC tags to save them here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariantDark
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    BrutalistButton(
                        text = "Scan Tag",
                        onClick = {
                            if (isConnected) {
                                viewModel.scanNfc()
                            }
                        },
                        enabled = isConnected,
                        leadingIcon = { Icon(Icons.Default.Nfc, contentDescription = null) }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = SurfaceVariantDark
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem(
                                    label = "Total Tags",
                                    value = savedTags.size.toString()
                                )
                                StatItem(
                                    label = "Writable",
                                    value = savedTags.count { it.writable }.toString()
                                )
                                StatItem(
                                    label = "Types",
                                    value = savedTags.map { it.tagType }.distinct().size.toString()
                                )
                            }
                        }
                    }
                    
                    // Tag List
                    items(savedTags, key = { it.id }) { tag ->
                        SavedTagCard(
                            tag = tag,
                            isSelected = selectedTag?.id == tag.id,
                            privacyMode = privacyMode,
                            onSelect = { selectedTag = if (selectedTag?.id == tag.id) null else tag },
                            onEdit = {
                                selectedTag = tag
                                showEditDialog = true
                            },
                            onDelete = {
                                selectedTag = tag
                                showDeleteDialog = true
                            },
                            onWrite = {
                                if (isConnected) {
                                    viewModel.sendRaw("nfc_write ${tag.id}")
                                }
                            },
                            onEmulate = {
                                if (isConnected) {
                                    viewModel.sendRaw("nfc_emulate ${tag.id}")
                                }
                            }
                        )
                    }
                    
                    // Actions for selected tag
                    if (selectedTag != null) {
                        item {
                            Text(
                                text = "Actions for ${selectedTag?.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor()
                            )
                        }
                        
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BrutalistButton(
                                    text = "Write",
                                    onClick = {
                                        if (isConnected) {
                                            viewModel.sendRaw("nfc_write ${selectedTag?.id}")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    containerColor = primaryColor(),
                                    enabled = isConnected,
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                )
                                
                                BrutalistButton(
                                    text = "Emulate",
                                    onClick = {
                                        if (isConnected) {
                                            viewModel.sendRaw("nfc_emulate ${selectedTag?.id}")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    containerColor = successColor(),
                                    enabled = isConnected,
                                    leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null) }
                                )
                            }
                        }
                        
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BrutalistOutlinedButton(
                                    text = "Export",
                                    onClick = {},
                                    modifier = Modifier.weight(1f),
                                    enabled = false,
                                    leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) }
                                )
                                
                                BrutalistOutlinedButton(
                                    text = "Delete",
                                    onClick = { showDeleteDialog = true },
                                    modifier = Modifier.weight(1f),
                                    borderColor = errorColor(),
                                    textColor = errorColor(),
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                                )
                            }
                        }
                    }
                    
                    // Info Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = SurfaceVariantDark.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = OnSurfaceVariantDark,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Saved tags can be written to blank cards or emulated. Select a tag to see available actions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariantDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog && selectedTag != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Tag?") },
            text = { Text("Are you sure you want to delete \"${selectedTag?.name}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        savedTags.removeIf { it.id == selectedTag?.id }
                        selectedTag = null
                        showDeleteDialog = false
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
    
    // Edit Dialog
    if (showEditDialog && selectedTag != null) {
        var editedName by remember { mutableStateOf(selectedTag?.name ?: "") }
        
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Tag") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val index = savedTags.indexOfFirst { it.id == selectedTag?.id }
                        if (index >= 0) {
                            savedTags[index] = savedTags[index].copy(name = editedName)
                            selectedTag = savedTags[index]
                        }
                        showEditDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Saved NFC tag data class
 */
data class SavedNfcTag(
    val id: String,
    val name: String,
    val tagType: String,
    val uid: String,
    val size: Int,
    val writable: Boolean,
    val savedAt: Long
)

/**
 * Card displaying a saved NFC tag
 */
@Composable
private fun SavedTagCard(
    tag: SavedNfcTag,
    isSelected: Boolean,
    privacyMode: Boolean = false,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onWrite: () -> Unit,
    onEmulate: () -> Unit
) {
    BrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isSelected) primaryColor() else OutlineDark,
        onClick = onSelect
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = if (isSelected) primaryColor() else OnSurfaceDark,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) primaryColor() else OnSurfaceDark
                        )
                        Text(
                            text = tag.tagType,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantDark
                        )
                    }
                }
                
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = OnSurfaceVariantDark
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = errorColor()
                        )
                    }
                }
            }

            if (isSelected) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TagInfoRow("UID", tag.uid.censorNfc(privacyMode))
                    TagInfoRow("Size", "${tag.size} bytes")
                    TagInfoRow("Writable", if (tag.writable) "Yes" else "No")
                    TagInfoRow("Saved", formatTimestamp(tag.savedAt))
                }
            }
        }
    }
}

@Composable
private fun TagInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariantDark
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = OnSurfaceDark
        )
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = primaryColor()
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariantDark
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 3600000 -> "${diff / 60000} minutes ago"
        diff < 86400000 -> "${diff / 3600000} hours ago"
        diff < 604800000 -> "${diff / 86400000} days ago"
        else -> "${diff / 604800000} weeks ago"
    }
}
