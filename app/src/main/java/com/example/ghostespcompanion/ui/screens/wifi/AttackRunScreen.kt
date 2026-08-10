package com.example.ghostespcompanion.ui.screens.wifi

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.components.BrutalistButton
import com.example.ghostespcompanion.ui.components.BrutalistCard
import com.example.ghostespcompanion.ui.components.BrutalistOutlinedButton
import com.example.ghostespcompanion.ui.components.BrutalistStatusBadge
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.screens.more.PortRangePreset
import com.example.ghostespcompanion.ui.theme.errorColor
import com.example.ghostespcompanion.ui.theme.primaryColor
import com.example.ghostespcompanion.ui.theme.successColor
import com.example.ghostespcompanion.ui.theme.warningColor
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

private enum class RunMode { ONGOING, ONE_SHOT }

private enum class RunStatus { IDLE, RUNNING, COMPLETED }

private enum class ResultTone { DEFAULT, SUCCESS, WARNING, ERROR }

private data class AttackResultItem(
    val title: String,
    val subtitle: String? = null,
    val fields: List<Pair<String, String>> = emptyList(),
    val tone: ResultTone = ResultTone.DEFAULT
)

/**
 * Per-attack definition used by [AttackRunScreen].
 */
private data class AttackRunConfig(
    val title: String,
    val description: String,
    val mode: RunMode,
    val results: @Composable () -> List<AttackResultItem>,
    val completed: @Composable () -> Boolean,
    val onStart: () -> Unit,
    val onStop: (() -> Unit)? = null,
    val onRefresh: (() -> Unit)? = null,
    val timeoutSeconds: Long? = null,
    val options: (@Composable () -> Unit)? = null
)

/**
 * Full-screen live results view for WiFi attacks/scans.
 *
 * Launched by tapping a row in the WiFi attacks list. Shows a status header
 * (idle/running/completed badge, elapsed timer, progress), a Start/Stop/Run
 * again control, and a live-updating, auto-scrolling results list fed directly
 * from the repository StateFlows.
 */
@Composable
fun AttackRunScreen(
    attackType: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val config = rememberAttackRunConfig(attackType, viewModel)

    var running by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var startRealtime by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        config.onStart()
        running = true
        completed = false
        startRealtime = SystemClock.elapsedRealtime()
    }

    val runCompleted = config.completed()
    val latestRunning by rememberUpdatedState(running)
    val latestStop by rememberUpdatedState(config.onStop)

    LaunchedEffect(runCompleted, running) {
        if (runCompleted && running) {
            completed = true
            running = false
        }
    }

    LaunchedEffect(running) {
        while (running) {
            elapsedSeconds = (SystemClock.elapsedRealtime() - startRealtime) / 1000L
            delay(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (latestRunning) {
                latestStop?.invoke()
            }
        }
    }

    val results = config.results()
    val status = when {
        running -> RunStatus.RUNNING
        completed -> RunStatus.COMPLETED
        else -> RunStatus.IDLE
    }

    MainScreen(title = config.title, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                BrutalistCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = when (status) {
                        RunStatus.RUNNING -> errorColor()
                        RunStatus.COMPLETED -> successColor()
                        RunStatus.IDLE -> MaterialTheme.colorScheme.outline
                    },
                    backgroundColor = when (status) {
                        RunStatus.RUNNING -> errorColor().copy(alpha = 0.08f)
                        RunStatus.COMPLETED -> successColor().copy(alpha = 0.08f)
                        RunStatus.IDLE -> MaterialTheme.colorScheme.surface
                    },
                    borderWidth = if (status == RunStatus.RUNNING) 2.dp else 1.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = config.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = formatElapsed(elapsedSeconds),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        BrutalistStatusBadge(
                            text = when (status) {
                                RunStatus.RUNNING -> stringResource(R.string.label_status_running)
                                RunStatus.COMPLETED -> stringResource(R.string.label_status_completed)
                                RunStatus.IDLE -> stringResource(R.string.label_status_idle)
                            },
                            statusColor = when (status) {
                                RunStatus.RUNNING -> errorColor()
                                RunStatus.COMPLETED -> successColor()
                                RunStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    if (status == RunStatus.RUNNING) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 3.dp,
                                color = errorColor()
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (running && config.timeoutSeconds != null && elapsedSeconds > config.timeoutSeconds && results.isEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.hint_no_device_response),
                            style = MaterialTheme.typography.bodySmall,
                            color = warningColor()
                        )
                    }

                    config.options?.let { options ->
                        Spacer(modifier = Modifier.height(12.dp))
                        options()
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (running && config.onStop != null) {
                        BrutalistButton(
                            text = stringResource(R.string.action_stop),
                            onClick = {
                                config.onStop?.invoke()
                                running = false
                                completed = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = warningColor(),
                            borderColor = warningColor(),
                            leadingIcon = {
                                Icon(Icons.Default.Stop, contentDescription = null)
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BrutalistButton(
                                text = if (status == RunStatus.COMPLETED) {
                                    stringResource(R.string.action_run_again)
                                } else {
                                    stringResource(R.string.action_launch)
                                },
                                onClick = {
                                    config.onStart()
                                    running = true
                                    completed = false
                                    startRealtime = SystemClock.elapsedRealtime()
                                    elapsedSeconds = 0L
                                },
                                modifier = Modifier.weight(1f),
                                leadingIcon = {
                                    Icon(
                                        if (status == RunStatus.COMPLETED) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                }
                            )
                            if (config.onRefresh != null) {
                                BrutalistOutlinedButton(
                                    text = stringResource(R.string.action_show_stats),
                                    onClick = config.onRefresh,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (results.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.label_scan_findings_count, results.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                StructuredResultsList(
                    results = results,
                    running = running,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = stringResource(R.string.msg_waiting_for_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun StructuredResultsList(
    results: List<AttackResultItem>,
    running: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(results.size, running) {
        if (running && results.isNotEmpty()) {
            listState.scrollToItem(results.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(results) { _, result ->
            val accent = when (result.tone) {
                ResultTone.SUCCESS -> successColor()
                ResultTone.WARNING -> warningColor()
                ResultTone.ERROR -> errorColor()
                ResultTone.DEFAULT -> MaterialTheme.colorScheme.outline
            }
            BrutalistCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                borderColor = accent,
                backgroundColor = accent.copy(alpha = 0.05f)
            ) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                result.subtitle?.let {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (result.fields.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    result.fields.forEach { (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                value,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberAttackRunConfig(
    attackType: String,
    viewModel: MainViewModel
): AttackRunConfig {
    val sweepTitle = stringResource(R.string.label_sweep)
    val sweepDesc = stringResource(R.string.desc_sweep)
    val congestionTitle = stringResource(R.string.label_congestion_scan)
    val congestionDesc = stringResource(R.string.desc_congestion_scan)
    val probesTitle = stringResource(R.string.label_listen_probes)
    val probesDesc = stringResource(R.string.desc_listen_probes)
    val scanLocalTitle = stringResource(R.string.label_scan_local)
    val scanLocalDesc = stringResource(R.string.desc_scan_local)
    val scanArpTitle = stringResource(R.string.label_scan_arp)
    val scanArpDesc = stringResource(R.string.desc_scan_arp)
    val scanPortsTitle = stringResource(R.string.label_open_ports_scan)
    val scanPortsDesc = stringResource(R.string.desc_open_ports_scan)
    val scanSshTitle = stringResource(R.string.label_ssh_scan)
    val scanSshDesc = stringResource(R.string.desc_ssh_scan)
    val starveTitle = stringResource(R.string.label_dhcp_starvation)
    val starveDesc = stringResource(R.string.desc_dhcp_starvation)
    val pineapTitle = stringResource(R.string.label_pineap_detection)
    val pineapDesc = stringResource(R.string.desc_pineap_detection)
    val flockTitle = stringResource(R.string.label_flock_detection)
    val flockDesc = stringResource(R.string.desc_flock_detection)
    val netBiosTitle = stringResource(R.string.label_netbios_scan)
    val netBiosDesc = stringResource(R.string.desc_netbios_scan)
    val httpBannerTitle = stringResource(R.string.label_http_banner_scan)
    val httpBannerDesc = stringResource(R.string.desc_http_banner_scan)
    val snmpTitle = stringResource(R.string.label_snmp_probe)
    val snmpProbeDesc = stringResource(R.string.desc_snmp_probe)
    val snmpWalkDesc = stringResource(R.string.desc_snmp_walk)
    val enumTitle = stringResource(R.string.label_enum_scan)
    val enumDesc = stringResource(R.string.desc_enum_scan)
    val wpa3Title = stringResource(R.string.label_wpa3_check)
    val wpa3Desc = stringResource(R.string.desc_wpa3_check)
    val csaTitle = stringResource(R.string.label_channel_switch_attack)
    val csaDesc = stringResource(R.string.desc_channel_switch_attack)
    val gtkTitle = stringResource(R.string.label_gtk_abuse)
    val gtkDesc = stringResource(R.string.desc_gtk_abuse)

    val sweepPhases by viewModel.sweepPhases.collectAsState()
    val sweepSummary by viewModel.sweepSummary.collectAsState()
    val congestionRows by viewModel.congestionRows.collectAsState()
    val probeRequests by viewModel.probeRequests.collectAsState()
    val openPorts by viewModel.openPorts.collectAsState()
    val sshBanners by viewModel.sshBanners.collectAsState()
    val sshScanSummary by viewModel.sshScanSummary.collectAsState()
    val ipLookupDevices by viewModel.ipLookupDevices.collectAsState()
    val ipLookupDone by viewModel.ipLookupDone.collectAsState()
    val scanCompletion by viewModel.scanCompletion.collectAsState()
    val arpHosts by viewModel.arpHosts.collectAsState()
    val arpScanSummary by viewModel.arpScanSummary.collectAsState()
    val dhcpStarveStats by viewModel.dhcpStarveStats.collectAsState()
    val pineapDetections by viewModel.pineapDetections.collectAsState()
    val flockDetections by viewModel.flockDetections.collectAsState()
    val flockScanComplete by viewModel.flockScanComplete.collectAsState()
    val netBiosResults by viewModel.netBiosResults.collectAsState()
    val netBiosScanComplete by viewModel.netBiosScanComplete.collectAsState()
    val httpBannerHits by viewModel.httpBannerHits.collectAsState()
    val httpBannerSummary by viewModel.httpBannerSummary.collectAsState()
    val snmpHits by viewModel.snmpHits.collectAsState()
    val snmpSummary by viewModel.snmpSummary.collectAsState()
    val enumHits by viewModel.enumHits.collectAsState()
    val enumSummary by viewModel.enumSummary.collectAsState()
    val wpa3Compliance by viewModel.wpa3Compliance.collectAsState()
    val wpa3ReportSummary by viewModel.wpa3ReportSummary.collectAsState()
    val csaAttackStatus by viewModel.csaAttackStatus.collectAsState()
    val gtkAbuseLog by viewModel.gtkAbuseLog.collectAsState()

    val scanPortsTarget = remember { mutableStateOf("") }
    val scanPortsPreset = remember { mutableStateOf<PortRangePreset?>(null) }
    val scanPortsCustomStart = remember { mutableStateOf("") }
    val scanPortsCustomEnd = remember { mutableStateOf("") }
    val activeRange: Pair<Int, Int>? = scanPortsPreset.value?.let { it.start to it.end }
        ?: run {
            val start = scanPortsCustomStart.value.toIntOrNull()
            val end = scanPortsCustomEnd.value.toIntOrNull()
            if (start != null && end != null && start <= end) start to end else null
        }
    val scanTarget = remember { mutableStateOf("") }
    val snmpWalkMode = remember { mutableStateOf(false) }
    val connectedIp by viewModel.wifiConnection.collectAsState()

    // Pre-fill recon scan targets with the connected network's IP once known
    LaunchedEffect(connectedIp?.ip) {
        val ip = connectedIp?.ip ?: return@LaunchedEffect
        if (scanTarget.value.isBlank()) {
            scanTarget.value = ip
        }
    }

    return when (attackType) {
        "sweep" -> AttackRunConfig(
            title = sweepTitle,
            description = sweepDesc,
            mode = RunMode.ONGOING,
            results = {
                buildList {
                    sweepPhases.takeLast(12).forEachIndexed { index, phase ->
                        add(AttackResultItem("Phase ${index + 1}", phase.message))
                    }
                    sweepSummary?.let {
                        add(AttackResultItem(
                            title = "Environment summary",
                            fields = listOf(
                                "Access points" to it.aps.toString(),
                                "Stations" to it.stations.toString(),
                                "Open / weak / secure" to "${it.open} / ${it.weak} / ${it.secure}"
                            ),
                            tone = ResultTone.SUCCESS
                        ))
                    }
                }
            },
            completed = { sweepPhases.any { it.message == "Sweep complete" } },
            onStart = { viewModel.runSweep() },
            onStop = { viewModel.stopSweep() }
        )

        "congestion" -> AttackRunConfig(
            title = congestionTitle,
            description = congestionDesc,
            mode = RunMode.ONE_SHOT,
            results = {
                congestionRows.map { row ->
                    AttackResultItem(
                        title = "Channel ${row.channel}",
                        subtitle = row.bar,
                        fields = listOf("Observed frames" to row.count.toString()),
                        tone = if (row.count > 500) ResultTone.WARNING else ResultTone.DEFAULT
                    )
                }
            },
            completed = { congestionRows.isNotEmpty() },
            onStart = { viewModel.runCongestionScan() },
            timeoutSeconds = 90L
        )

        "listenprobes" -> AttackRunConfig(
            title = probesTitle,
            description = probesDesc,
            mode = RunMode.ONGOING,
            results = {
                probeRequests.takeLast(50).map { probe ->
                    AttackResultItem(
                        title = probe.ssid.ifBlank { "Wildcard probe" },
                        fields = listOf("Source" to probe.srcMac, "Destination" to probe.destMac)
                    )
                }
            },
            completed = { false },
            onStart = { viewModel.startListenProbes() },
            onStop = { viewModel.stopListenProbes() }
        )

        "scanlocal" -> AttackRunConfig(
            title = scanLocalTitle,
            description = scanLocalDesc,
            mode = RunMode.ONE_SHOT,
            results = {
                buildList {
                    ipLookupDevices.forEach { device ->
                        add(AttackResultItem(
                            title = device.name ?: device.ip,
                            subtitle = if (device.name != null) device.ip else null,
                            fields = buildList {
                                device.type?.let { add("Type" to it) }
                                device.port?.let { add("Port" to it.toString()) }
                            }
                        ))
                    }
                    ipLookupDone?.let {
                        add(AttackResultItem("IP lookup complete", fields = listOf("Devices" to it.toString()), tone = ResultTone.SUCCESS))
                    }
                }
            },
            completed = { ipLookupDone != null || scanCompletion != null },
            onStart = { viewModel.runScanLocal() },
            timeoutSeconds = 90L
        )

        "scanarp" -> AttackRunConfig(
            title = scanArpTitle,
            description = scanArpDesc,
            mode = RunMode.ONE_SHOT,
            results = {
                buildList {
                    arpScanSummary?.let {
                        add(AttackResultItem(
                            "ARP scan summary",
                            fields = listOf(
                                "Subnet" to "${it.subnet}/${it.cidr ?: "?"}",
                                "Active hosts" to it.hostCount.toString(),
                                "Passes" to (it.passes?.toString() ?: "?")
                            ),
                            tone = ResultTone.SUCCESS
                        ))
                    }
                    arpHosts.forEach {
                        add(AttackResultItem(it.ip, fields = listOf("MAC address" to it.mac, "Index" to it.index.toString())))
                    }
                }
            },
            completed = { arpScanSummary != null },
            onStart = { viewModel.runScanArp() },
            timeoutSeconds = 120L
        )

        "scanports" -> AttackRunConfig(
            title = scanPortsTitle,
            description = scanPortsDesc,
            mode = RunMode.ONE_SHOT,
            results = {
                buildList {
                    openPorts.map { port ->
                        val label = if (port.udp) "UDP" else "TCP"
                        add(AttackResultItem(
                            title = "${port.ip ?: "Unknown host"}:${port.port}",
                            fields = listOf("Protocol" to label, "State" to "OPEN"),
                            tone = ResultTone.WARNING
                        ))
                    }
                    scanCompletion?.let {
                        val verb = if (it.cancelled) "cancelled" else "completed"
                        add(AttackResultItem("Scan $verb", fields = listOf("Active hosts" to it.hostCount.toString()), tone = ResultTone.SUCCESS))
                    }
                }
            },
            completed = { scanCompletion != null },
            onStart = {
                val range = activeRange
                val target = scanPortsTarget.value.trim().ifEmpty { null }
                viewModel.runScanPorts(
                    target = if (target != null || range == null) target else null,
                    startPort = range?.first,
                    endPort = range?.second
                )
            },
            timeoutSeconds = 120L,
            options = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = scanPortsTarget.value,
                        onValueChange = { scanPortsTarget.value = it },
                        label = { Text(stringResource(R.string.label_scan_target_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = scanPortsPreset.value == null && activeRange == null,
                            onClick = {
                                scanPortsPreset.value = null
                                scanPortsCustomStart.value = ""
                                scanPortsCustomEnd.value = ""
                            },
                            label = { Text(stringResource(R.string.preset_ports_default)) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                        PortRangePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = scanPortsPreset.value == preset,
                                onClick = {
                                    scanPortsPreset.value = preset
                                    scanPortsCustomStart.value = ""
                                    scanPortsCustomEnd.value = ""
                                },
                                label = { Text(stringResource(preset.labelRes)) },
                                colors = FilterChipDefaults.filterChipColors()
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = scanPortsCustomStart.value,
                            onValueChange = {
                                scanPortsCustomStart.value = it.filter(Char::isDigit)
                                scanPortsPreset.value = null
                            },
                            label = { Text(stringResource(R.string.label_port_range_start)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = scanPortsCustomEnd.value,
                            onValueChange = {
                                scanPortsCustomEnd.value = it.filter(Char::isDigit)
                                scanPortsPreset.value = null
                            },
                            label = { Text(stringResource(R.string.label_port_range_end)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (activeRange != null && scanPortsTarget.value.isBlank()) {
                        Text(
                            text = stringResource(R.string.msg_range_requires_target),
                            style = MaterialTheme.typography.labelSmall,
                            color = warningColor()
                        )
                    }
                }
            }
        )

        "scanssh" -> AttackRunConfig(
            title = scanSshTitle,
            description = scanSshDesc,
            mode = RunMode.ONE_SHOT,
            results = {
                buildList {
                    sshBanners.map { banner ->
                        add(AttackResultItem(
                            title = "${banner.ip}:${banner.port}",
                            subtitle = banner.banner?.takeIf { it != "(none)" } ?: "No banner returned",
                            fields = listOf("Service" to "SSH", "State" to "OPEN"),
                            tone = ResultTone.WARNING
                        ))
                    }
                    sshScanSummary?.let { summary ->
                        add(AttackResultItem(
                            title = "SSH scan complete",
                            subtitle = summary.target,
                            fields = buildList {
                                add("Open ports" to summary.portCount.toString())
                                summary.hostCount?.let { add("Hosts" to it.toString()) }
                            },
                            tone = ResultTone.SUCCESS
                        ))
                    }
                }
            },
            completed = { sshScanSummary != null },
            onStart = { viewModel.runScanSsh(target = null) },
            timeoutSeconds = 120L
        )

        "dhcpstarve" -> AttackRunConfig(
            title = starveTitle,
            description = starveDesc,
            mode = RunMode.ONGOING,
            results = {
                dhcpStarveStats?.let { stats ->
                    val pps = stats.pps?.let { "$it/sec" } ?: "n/a"
                    listOf(AttackResultItem(
                        title = "DHCP starvation telemetry",
                        fields = listOf("Current rate" to pps, "Total requests" to stats.total.toString()),
                        tone = ResultTone.WARNING
                    ))
                } ?: emptyList()
            },
            completed = { false },
            onStart = { viewModel.startDhcpStarve() },
            onStop = { viewModel.stopDhcpStarve() },
            onRefresh = { viewModel.dhcpStarveDisplay() }
        )

        "pineap" -> AttackRunConfig(
            title = pineapTitle,
            description = pineapDesc,
            mode = RunMode.ONGOING,
            results = {
                pineapDetections.map { d ->
                    AttackResultItem(
                        title = d.heading,
                        subtitle = d.bssid,
                        fields = listOf(
                            "Channel / RSSI" to "${d.channel} / ${d.rssi} dBm",
                            "Advertised SSIDs" to "${d.ssidCount}: ${d.ssids}"
                        ),
                        tone = ResultTone.WARNING
                    )
                }
            },
            completed = { false },
            onStart = { viewModel.startPineApDetection() },
            onStop = { viewModel.stopPineApDetection() }
        )

        "flock" -> AttackRunConfig(
            title = flockTitle,
            description = flockDesc,
            mode = RunMode.ONGOING,
            results = {
                buildList {
                    flockDetections.map { d ->
                        add(AttackResultItem(
                            title = d.ssid ?: d.mac,
                            subtitle = d.method,
                            fields = listOf(
                                "MAC" to d.mac,
                                "Signal" to "${d.signalLabel} (${d.rssi} dBm)",
                                "Channel / hits" to "${d.channel} / ${d.hits}"
                            ),
                            tone = ResultTone.WARNING
                        ))
                    }
                    flockScanComplete?.let {
                        add(AttackResultItem("Flock scan complete", fields = listOf("Devices" to it.count.toString()), tone = ResultTone.SUCCESS))
                    }
                }
            },
            completed = { flockScanComplete != null },
            onStart = { viewModel.startFlockScan() },
            onStop = { viewModel.stopFlockScan() }
        )

        "netbios" -> AttackRunConfig(
            title = netBiosTitle,
            description = netBiosDesc,
            mode = RunMode.ONE_SHOT,
            results = {
                netBiosResults.map { r ->
                    AttackResultItem(
                        title = r.host,
                        fields = buildList {
                            r.remoteIp?.let { add("IP address" to it) }
                            r.names?.let { add("Names" to it) }
                            r.flags?.let { add("Flags" to "0x${it.toString(16)}") }
                        }
                    )
                }
            },
            completed = { netBiosScanComplete != null },
            onStart = { viewModel.runNetBiosScan(scanTarget.value.trim()) },
            timeoutSeconds = 120L,
            options = {
                OutlinedTextField(
                    value = scanTarget.value,
                    onValueChange = { scanTarget.value = it },
                    label = { Text(stringResource(R.string.label_scan_target_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )

        "httpbanner" -> AttackRunConfig(
            title = httpBannerTitle,
            description = httpBannerDesc,
            mode = RunMode.ONE_SHOT,
            results = {
                buildList {
                    httpBannerHits.map { h ->
                        val detail = when {
                            h.server != null -> "Server: ${h.server}"
                            h.response != null -> "Response: ${h.response.take(60)}"
                            h.tlsNoBanner -> "TLS, no banner"
                            else -> "OPEN, no banner"
                        }
                        add(AttackResultItem(
                            title = "${h.ip}:${h.port}",
                            subtitle = detail,
                            fields = listOf("Scheme" to h.scheme, "State" to "OPEN"),
                            tone = ResultTone.WARNING
                        ))
                    }
                    httpBannerSummary?.let {
                        add(AttackResultItem(
                            "HTTP banner scan complete",
                            fields = listOf("Hosts" to it.hostsFound.toString(), "Services" to it.servicesFound.toString()),
                            tone = ResultTone.SUCCESS
                        ))
                    }
                }
            },
            completed = { httpBannerSummary != null },
            onStart = { viewModel.runHttpBannerScan(scanTarget.value.trim()) },
            timeoutSeconds = 120L,
            options = {
                OutlinedTextField(
                    value = scanTarget.value,
                    onValueChange = { scanTarget.value = it },
                    label = { Text(stringResource(R.string.label_scan_target_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )

        "snmp" -> AttackRunConfig(
            title = snmpTitle,
            description = if (snmpWalkMode.value) snmpWalkDesc else snmpProbeDesc,
            mode = RunMode.ONE_SHOT,
            results = {
                buildList {
                    snmpHits.map { h ->
                        if (h.oid != null) {
                            add(AttackResultItem(
                                title = h.oid,
                                subtitle = h.value,
                                fields = listOf("Type" to (h.type ?: "unknown"))
                            ))
                        } else {
                            add(AttackResultItem(
                                title = h.ip,
                                subtitle = h.sysDescr,
                                fields = listOf("Community" to (h.community ?: "unknown"))
                            ))
                        }
                    }
                    snmpSummary?.let {
                        add(AttackResultItem("SNMP scan complete", fields = listOf("Hosts" to it.hostsFound.toString()), tone = ResultTone.SUCCESS))
                    }
                }
            },
            completed = { snmpSummary != null },
            onStart = { viewModel.runSnmpProbe(scanTarget.value.trim(), snmpWalkMode.value) },
            timeoutSeconds = 120L,
            options = {
                OutlinedTextField(
                    value = scanTarget.value,
                    onValueChange = { scanTarget.value = it },
                    label = { Text(stringResource(R.string.label_scan_target_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = snmpWalkMode.value,
                        onCheckedChange = { snmpWalkMode.value = it }
                    )
                    Text(
                        text = stringResource(R.string.label_snmp_walk_mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        "enum" -> AttackRunConfig(
            title = enumTitle,
            description = enumDesc,
            mode = RunMode.ONE_SHOT,
            results = {
                buildList {
                    enumHits.map { add(AttackResultItem("Enumeration finding", it.raw)) }
                    enumSummary?.let {
                        add(AttackResultItem("Enumeration complete", fields = listOf("Hosts" to it.hostsFound.toString()), tone = ResultTone.SUCCESS))
                    }
                }
            },
            completed = { enumSummary != null },
            onStart = { viewModel.runEnumScan(scanTarget.value.trim()) },
            timeoutSeconds = 180L,
            options = {
                OutlinedTextField(
                    value = scanTarget.value,
                    onValueChange = { scanTarget.value = it },
                    label = { Text(stringResource(R.string.label_scan_target_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )

        "wpa3" -> AttackRunConfig(
            title = wpa3Title,
            description = wpa3Desc,
            mode = RunMode.ONE_SHOT,
            results = {
                buildList {
                    wpa3Compliance?.let { c ->
                        add(AttackResultItem(
                            title = c.ssid,
                            subtitle = c.bssid,
                            fields = listOf(
                                "Authentication" to c.auth,
                                "PMF" to c.pmf,
                                "WPA3" to if (c.wpa3Present) "Present${if (c.transitionMode) " (transition)" else ""}" else "Not present",
                                "Finding" to c.finding
                            ),
                            tone = if (c.wpa3Present && !c.transitionMode) ResultTone.SUCCESS else ResultTone.WARNING
                        ))
                    }
                    wpa3ReportSummary?.let { r ->
                        add(AttackResultItem(
                            title = "WPA3 compliance report",
                            fields = listOf(
                                "Access points" to r.apCount.toString(),
                                "Compliant / downgradable" to "${r.compliant} / ${r.downgradable}",
                                "Legacy / open / other" to "${r.legacy} / ${r.open} / ${r.other}"
                            ),
                            tone = ResultTone.SUCCESS
                        ))
                    }
                }
            },
            completed = { wpa3Compliance != null || wpa3ReportSummary != null },
            onStart = { viewModel.runWpa3Check() },
            timeoutSeconds = 120L
        )

        "csa" -> AttackRunConfig(
            title = csaTitle,
            description = csaDesc,
            mode = RunMode.ONGOING,
            results = {
                buildList {
                    if (csaAttackStatus.targetCount > 0) {
                        add(AttackResultItem(
                            title = "CSA attack telemetry",
                            fields = buildList {
                                add("Targets" to csaAttackStatus.targetCount.toString())
                                csaAttackStatus.packetsPerSecond?.let { add("Packet rate" to "$it/sec") }
                            },
                            tone = ResultTone.WARNING
                        ))
                    }
                    csaAttackStatus.targets.forEach { add(AttackResultItem("Target access point", it)) }
                }
            },
            completed = { false },
            onStart = { viewModel.startChannelSwitchAttack() },
            onStop = { viewModel.stopAll() }
        )

        "gtk" -> AttackRunConfig(
            title = gtkTitle,
            description = gtkDesc,
            mode = RunMode.ONGOING,
            results = {
                buildList {
                    val isolationBroken = gtkAbuseLog.any { it.message.contains("isolation is BROKEN", ignoreCase = true) }
                    val isolationOk = gtkAbuseLog.any { it.message.contains("No echo reply received", ignoreCase = true) }
                    if (isolationBroken || isolationOk) {
                        add(AttackResultItem(
                            title = if (isolationBroken) "Client isolation broken" else "Client isolation held",
                            subtitle = if (isolationBroken) "Cross-client traffic was observed" else "No echo reply received",
                            tone = if (isolationBroken) ResultTone.ERROR else ResultTone.SUCCESS
                        ))
                    }
                    gtkAbuseLog.forEach { add(AttackResultItem("GTK status", it.message)) }
                }
            },
            completed = { false },
            onStart = {
                val (ssid, password) = viewModel.consumePendingGtkAbuse()
                if (ssid.isNotBlank()) {
                    viewModel.startGtkAbuse(ssid, password)
                }
            },
            onStop = { viewModel.stopAll() }
        )

        else -> AttackRunConfig(
            title = attackType,
            description = attackType,
            mode = RunMode.ONE_SHOT,
            results = { emptyList() },
            completed = { false },
            onStart = {}
        )
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
