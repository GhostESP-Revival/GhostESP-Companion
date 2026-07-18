package com.example.ghostespcompanion.ui.components

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.ble.BleBridgeDevice

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSelectionDialog(
    usbDevices: List<UsbDevice>,
    usbPortCounts: Map<String, Int>,
    bleDevices: List<BleBridgeDevice>,
    onUsbSelected: (UsbDevice, Int, Int) -> Unit,
    onBleSelected: (BleBridgeDevice) -> Unit,
    onRefreshUsb: () -> Unit,
    onRefreshBle: () -> Unit,
    onDismiss: () -> Unit,
    usbDebugLog: List<String> = emptyList(),
    allUsbDevices: List<UsbDevice> = emptyList(),
    bluetoothEnabled: Boolean = false,
    bluetoothSupported: Boolean = true,
    isBleScanning: Boolean = false,
    startOnWirelessTab: Boolean = false,
) {
    var selectedTab by remember { mutableIntStateOf(if (startOnWirelessTab) 1 else 0) }
    var showDebugLog by remember { mutableStateOf(false) }
    val baudRates = remember { listOf(115200, 460800, 9600, 57600, 230400, 420000, 921600) }
    val usbPorts = remember(usbDevices, usbPortCounts) {
        usbDevices.flatMap { device ->
            val count = (usbPortCounts[device.deviceName] ?: 1).coerceAtLeast(1)
            (0 until count).map { portIndex -> device to portIndex }
        }
    }
    var selectedBaud by remember { mutableIntStateOf(115200) }
    var baudExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .heightIn(max = 560.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_connect),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.label_wired)) },
                        icon = { Icon(Icons.Default.Cable, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.label_wireless)) },
                        icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    if (usbDebugLog.isNotEmpty()) {
                        TextButton(onClick = { showDebugLog = !showDebugLog }) {
                            Text(if (showDebugLog) stringResource(R.string.action_hide_debug_log) else stringResource(R.string.action_show_debug_log, usbDebugLog.size))
                        }
                    }

                    if (showDebugLog && usbDebugLog.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            LazyColumn(modifier = Modifier.padding(8.dp)) {
                                items(usbDebugLog) { logLine ->
                                    Text(
                                        text = logLine,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.label_baud_rate), style = MaterialTheme.typography.bodyMedium)
                        ExposedDropdownMenuBox(
                            expanded = baudExpanded,
                            onExpandedChange = { baudExpanded = it },
                            modifier = Modifier.width(140.dp)
                        ) {
                            OutlinedButton(
                                onClick = { baudExpanded = true },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            ) {
                                Text(selectedBaud.toString(), style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(expanded = baudExpanded, onDismissRequest = { baudExpanded = false }) {
                                baudRates.forEach { baud ->
                                    DropdownMenuItem(
                                        text = { Text(baud.toString()) },
                                        onClick = {
                                            selectedBaud = baud
                                            baudExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (usbDevices.isEmpty()) {
                        Text(stringResource(R.string.msg_no_serial_devices), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (allUsbDevices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.label_raw_usb_devices, allUsbDevices.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(usbPorts, key = { (device, portIndex) -> "${device.deviceName}:$portIndex" }) { (device, portIndex) ->
                                Card(
                                    onClick = { onUsbSelected(device, selectedBaud, portIndex) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(device.deviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                        Text(
                                            stringResource(R.string.label_usb_ids, device.vendorId.toString(16), device.productId.toString(16)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if ((usbPortCounts[device.deviceName] ?: 1) > 1) {
                                            Text(
                                                "Serial port ${portIndex + 1}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        device.manufacturerName?.let {
                                            Text(
                                                stringResource(R.string.label_manufacturer, it),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    when {
                        !bluetoothSupported -> {
                            Text(stringResource(R.string.msg_ble_not_supported), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        !bluetoothEnabled -> {
                            Text(stringResource(R.string.msg_bluetooth_off), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        bleDevices.isEmpty() -> {
                            Text(
                                if (isBleScanning) stringResource(R.string.msg_scanning_bridges) else stringResource(R.string.msg_no_bridges_found),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 260.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(bleDevices, key = { it.address }) { device ->
                                    Card(
                                        onClick = { onBleSelected(device) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${device.address} • ${stringResource(R.string.label_signal)} ${device.rssi} dBm",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = if (selectedTab == 0) onRefreshUsb else onRefreshBle,
                        enabled = selectedTab == 0 || !isBleScanning
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedTab == 0) stringResource(R.string.action_refresh_wired) else if (isBleScanning) stringResource(R.string.status_connecting) else stringResource(R.string.action_scan_wireless))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_dismiss))
                    }
                }
            }
        }
    }
}
