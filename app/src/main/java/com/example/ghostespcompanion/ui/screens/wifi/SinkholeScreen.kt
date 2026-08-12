package com.example.ghostespcompanion.ui.screens.wifi

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostCommand
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

/**
 * DNS Sinkhole - Blocklist-based DNS proxy for the device AP.
 * Mirrors the firmware's WiFi menu placement (sibling of Evil Portal).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinkholeScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val sinkholeStatus by viewModel.sinkholeStatus.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED

    var blocklistNumber by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }

    // Refresh live status when the screen opens
    LaunchedEffect(isConnected) {
        if (isConnected) {
            viewModel.sinkhole(GhostCommand.SinkholeAction.STATUS)
        }
    }

    MainScreen(
        onBack = onBack,
        title = stringResource(R.string.title_sinkhole)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            sinkholeStatus?.let { status ->
                item {
                    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.title_sinkhole),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            status.state?.let {
                                Text(
                                    text = stringResource(R.string.label_sinkhole_state, it),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            status.ip?.let {
                                Text(
                                    text = stringResource(R.string.label_sinkhole_ip, it),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            status.queries?.let { queries ->
                                val pct = status.blockPercent?.let { p -> "  ($p%)" } ?: ""
                                Text(
                                    text = stringResource(
                                        R.string.label_sinkhole_queries_blocked,
                                        queries.toString(),
                                        (status.blocked ?: "?").toString(),
                                        pct
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            status.logging?.let {
                                Text(
                                    text = stringResource(
                                        R.string.label_sinkhole_logging,
                                        if (it) "ON" else "OFF"
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            status.blocklist?.let {
                                Text(
                                    text = stringResource(R.string.label_sinkhole_blocklist, it),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            item {
                BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.label_sinkhole_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            BrutalistButton(
                                text = stringResource(R.string.action_sinkhole_start),
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.START) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                            BrutalistOutlinedButton(
                                text = stringResource(R.string.action_sinkhole_stop),
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.STOP) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            BrutalistOutlinedButton(
                                text = stringResource(R.string.action_sinkhole_status),
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.STATUS) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                            BrutalistOutlinedButton(
                                text = stringResource(R.string.action_sinkhole_stats),
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.STATS) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                            BrutalistOutlinedButton(
                                text = stringResource(R.string.action_sinkhole_reload),
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.RELOAD) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            BrutalistOutlinedButton(
                                text = stringResource(R.string.action_sinkhole_log_on),
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.LOG_ON) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                            BrutalistOutlinedButton(
                                text = stringResource(R.string.action_sinkhole_log_off),
                                onClick = { viewModel.sinkhole(GhostCommand.SinkholeAction.LOG_OFF) },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = blocklistNumber,
                            onValueChange = { blocklistNumber = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.label_sinkhole_blocklist_source)) },
                            placeholder = { Text(stringResource(R.string.label_sinkhole_blocklist_source_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BrutalistOutlinedButton(
                            text = stringResource(R.string.action_sinkhole_download),
                            onClick = {
                                val number = blocklistNumber.toIntOrNull()
                                if (number != null) {
                                    viewModel.sinkhole(GhostCommand.SinkholeAction.DOWNLOAD, number.toString())
                                }
                            },
                            enabled = isConnected && blocklistNumber.toIntOrNull() != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text(stringResource(R.string.label_sinkhole_domain)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            BrutalistOutlinedButton(
                                text = stringResource(R.string.action_sinkhole_add),
                                onClick = {
                                    if (domain.isNotBlank()) {
                                        viewModel.sinkhole(GhostCommand.SinkholeAction.ADD, domain.trim())
                                        domain = ""
                                    }
                                },
                                enabled = isConnected && domain.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            )
                            BrutalistOutlinedButton(
                                text = stringResource(R.string.action_sinkhole_remove),
                                onClick = {
                                    if (domain.isNotBlank()) {
                                        viewModel.sinkhole(GhostCommand.SinkholeAction.REMOVE, domain.trim())
                                        domain = ""
                                    }
                                },
                                enabled = isConnected && domain.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}