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
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

/**
 * Chameleon Ultra Screen - Emulate NFC tags with Chameleon Ultra device
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChameleonScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var selectedSlot by remember { mutableStateOf(0) }
    var isEmulating by remember { mutableStateOf(false) }
    var showSlotDialog by remember { mutableStateOf(false) }
    
    val connectionState by viewModel.connectionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    
    val emptyStr = stringResource(R.string.label_empty)
    // Available slots (Chameleon Ultra has 8 slots)
    val slots = remember(emptyStr) {
        listOf(
            ChameleonSlot(0, "Slot 1", "MIFARE Classic 1K", true),
            ChameleonSlot(1, "Slot 2", "NTAG213", false),
            ChameleonSlot(2, "Slot 3", emptyStr, false),
            ChameleonSlot(3, "Slot 4", emptyStr, false),
            ChameleonSlot(4, "Slot 5", emptyStr, false),
            ChameleonSlot(5, "Slot 6", emptyStr, false),
            ChameleonSlot(6, "Slot 7", emptyStr, false),
            ChameleonSlot(7, "Slot 8", emptyStr, false)
        )
    }
    
    MainScreen(
        onBack = onBack,
        title = stringResource(R.string.title_chameleon_ultra),
        actions = {
            IconButton(onClick = { showSlotDialog = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_tag),
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
            ChameleonConnectionBanner(
                isConnected = isConnected,
                deviceName = stringResource(R.string.app_name_short),
                onConnect = { viewModel.connectFirstAvailable() }
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Current Emulation Status
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isEmulating) successColor().copy(alpha = 0.1f) else SurfaceVariantDark
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isEmulating) Icons.Default.CreditCard else Icons.Default.CreditCardOff,
                                    contentDescription = null,
                                    tint = if (isEmulating) successColor() else OnSurfaceVariantDark,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column {
                                    Text(
                                        text = if (isEmulating) stringResource(R.string.label_emulating) else stringResource(R.string.label_not_emulating),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEmulating) successColor() else OnSurfaceVariantDark
                                    )
                                    Text(
                                        text = if (isEmulating) slots[selectedSlot].name else stringResource(R.string.msg_select_slot_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariantDark
                                    )
                                }
                            }
                            
                            if (isEmulating) {
                                BrutalistButton(
                                    text = stringResource(R.string.action_stop),
                                    onClick = {
                                        if (isConnected) {
                                            viewModel.sendRaw("chameleon_stop")
                                            isEmulating = false
                                        }
                                    },
                                    containerColor = errorColor(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
                
                // Slot Selection
                item {
                    Text(
                        text = stringResource(R.string.label_available_slots),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor()
                    )
                }
                
                items(slots, key = { it.index }) { slot ->
                    ChameleonSlotCard(
                        slot = slot,
                        isSelected = selectedSlot == slot.index,
                        isEmulating = isEmulating && selectedSlot == slot.index,
                        onSelect = { selectedSlot = slot.index },
                        onEmulate = {
                            if (isConnected) {
                                if (isEmulating && selectedSlot == slot.index) {
                                    viewModel.sendRaw("chameleon_stop")
                                    isEmulating = false
                                } else {
                                    viewModel.sendRaw("chameleon_emulate ${slot.index}")
                                    selectedSlot = slot.index
                                    isEmulating = true
                                }
                            }
                        }
                    )
                }
                
                // Quick Actions
                item {
                    Text(
                        text = stringResource(R.string.header_quick_actions),
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
                        BrutalistOutlinedButton(
                            text = stringResource(R.string.action_scan_to_slot),
                            onClick = {
                                if (isConnected) {
                                    viewModel.scanNfc()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            leadingIcon = { Icon(Icons.Default.Nfc, contentDescription = null) }
                        )
                        
                        BrutalistOutlinedButton(
                            text = stringResource(R.string.action_clear_slot),
                            onClick = {
                                if (isConnected) {
                                    viewModel.sendRaw("chameleon_clear $selectedSlot")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            borderColor = warningColor(),
                            textColor = warningColor(),
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrutalistOutlinedButton(
                            text = stringResource(R.string.action_save_all),
                            onClick = {
                                if (isConnected) {
                                    viewModel.sendRaw("chameleon_save")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                        )
                        
                        BrutalistOutlinedButton(
                            text = stringResource(R.string.action_load_all),
                            onClick = {
                                if (isConnected) {
                                    viewModel.sendRaw("chameleon_load")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                        )
                    }
                }
                
                // Status Message
                if (statusMessage != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = SurfaceVariantDark
                            ),
                            shape = RoundedCornerShape(8.dp)
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
                                text = stringResource(R.string.msg_chameleon_hint),
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

/**
 * Chameleon slot data class
 */
data class ChameleonSlot(
    val index: Int,
    val name: String,
    val tagType: String,
    val hasData: Boolean
)

/**
 * Card displaying a Chameleon slot
 */
@Composable
private fun ChameleonSlotCard(
    slot: ChameleonSlot,
    isSelected: Boolean,
    isEmulating: Boolean,
    onSelect: () -> Unit,
    onEmulate: () -> Unit
) {
    BrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = when {
            isEmulating -> successColor()
            isSelected -> primaryColor()
            else -> OutlineDark
        },
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isEmulating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = successColor(),
                            strokeWidth = 2.dp
                        )
                    }
                    Icon(
                        if (slot.hasData) Icons.Default.CreditCard else Icons.Default.CreditCardOff,
                        contentDescription = null,
                        tint = when {
                            isEmulating -> successColor()
                            isSelected -> primaryColor()
                            slot.hasData -> OnSurfaceDark
                            else -> OnSurfaceVariantDark
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column {
                    Text(
                        text = stringResource(R.string.label_slot_n, slot.index + 1),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isEmulating -> successColor()
                            isSelected -> primaryColor()
                            else -> OnSurfaceDark
                        }
                    )
                    Text(
                        text = slot.tagType,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )
                }
            }
            
            BrutalistButton(
                text = if (isEmulating) stringResource(R.string.action_stop) else stringResource(R.string.action_emulate),
                onClick = onEmulate,
                containerColor = if (isEmulating) errorColor() else if (slot.hasData) primaryColor() else SurfaceVariantDark,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * Chameleon Connection Banner component
 */
@Composable
private fun ChameleonConnectionBanner(
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
                    if (isConnected) Icons.Default.Nfc else Icons.Default.Nfc,
                    contentDescription = null,
                    tint = if (isConnected) successColor() else errorColor()
                )
                Column {
                    Text(
                        text = if (isConnected) stringResource(R.string.status_connected_device, deviceName) else stringResource(R.string.status_disconnected),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) successColor() else errorColor()
                    )
                    if (isConnected) {
                        Text(
                            text = stringResource(R.string.label_ready_to_emulate),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariantDark
                        )
                    }
                }
            }
            
            if (!isConnected) {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor(),
                        contentColor = OnPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.action_connect), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}