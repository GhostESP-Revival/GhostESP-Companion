package com.example.ghostespcompanion.ui.screens.nfc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostCommand
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.components.BrutalistButton
import com.example.ghostespcompanion.ui.components.BrutalistCard
import com.example.ghostespcompanion.ui.components.BrutalistChip
import com.example.ghostespcompanion.ui.components.BrutalistOutlinedButton
import com.example.ghostespcompanion.ui.components.CapabilityNotice
import com.example.ghostespcompanion.ui.components.FeatureNotSupportedOverlay
import com.example.ghostespcompanion.ui.components.resolve
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.theme.errorColor
import com.example.ghostespcompanion.ui.theme.primaryColor
import com.example.ghostespcompanion.ui.theme.successColor
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel

private const val NFC_FILES_DIR = "/mnt/ghostesp/nfc"

private val KNOWN_MIFARE_KEYS = listOf(
    "FFFFFFFFFFFF",
    "A0A1A2A3A4A5",
    "D3F7D3F7D3F7",
    "000000000000"
)

/**
 * NFC Screen
 *
 * Full parity with the firmware `nfc` CLI: backend selection, continuous/single scan,
 * save/dump, MIFARE Classic hardnested attack, PicoPass/iCLASS scan, and emulation
 * (raw UID, NDEF, or a Flipper .nfc file from SD).
 *
 * @param onNavigateToChameleon Callback to navigate to Chameleon Ultra screen
 * @param onNavigateToSaved Callback to navigate to saved tags screen
 */
@Composable
fun NfcScreen(
    onNavigateToChameleon: () -> Unit,
    onNavigateToSaved: () -> Unit,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var showOverlay by rememberSaveable { mutableStateOf(false) }

    val connectionState by viewModel.connectionState.collectAsState()
    val nfcTags by viewModel.nfcTags.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val nfcBackend by viewModel.nfcBackend.collectAsState()
    val nfcTaskRunning by viewModel.nfcTaskRunning.collectAsState()
    val nfcEmulateStatus by viewModel.nfcEmulateStatus.collectAsState()
    val nfcSaveResult by viewModel.nfcSaveResult.collectAsState()
    val nfcHardnestedResult by viewModel.nfcHardnestedResult.collectAsState()
    val nfcPicopassResult by viewModel.nfcPicopassResult.collectAsState()
    val lastNfcBackend by viewModel.lastNfcBackend.collectAsState()
    val sdEntries by viewModel.sdEntries.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED

    val nfcCapability = deviceInfo.resolve(GhostResponse.DeviceFeature.NFC)
    val chameleonCapability = deviceInfo.resolve(GhostResponse.DeviceFeature.CHAMELEON)

    val isPn532 = nfcBackend?.name?.contains("pn532", ignoreCase = true) == true

    LaunchedEffect(isConnected) {
        if (isConnected) {
            val persisted = lastNfcBackend?.let { name ->
                GhostCommand.NfcBackendType.values().find { it.value.equals(name, ignoreCase = true) }
            }
            if (persisted != null) viewModel.nfcSetBackend(persisted) else viewModel.nfcGetBackend()
        }
    }

    MainScreen(
        onBack = onBack,
        title = stringResource(R.string.title_nfc),
        actions = {
            IconButton(onClick = onNavigateToChameleon, enabled = chameleonCapability.isUsable) {
                Icon(Icons.Default.CreditCard, contentDescription = stringResource(R.string.title_chameleon_ultra))
            }
            IconButton(onClick = onNavigateToSaved) {
                Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.title_saved_tags))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }
            val tabTitles = listOf(
                stringResource(R.string.tab_nfc_scan),
                stringResource(R.string.tab_nfc_attacks),
                stringResource(R.string.tab_nfc_emulate)
            )

            Column(modifier = Modifier.fillMaxSize()) {
                CapabilityNotice(nfcCapability, stringResource(R.string.title_nfc), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            item {
                                BackendCard(
                                    isConnected = isConnected,
                                    backend = nfcBackend?.name,
                                    onSelect = { viewModel.nfcSetBackend(it) },
                                    onRefresh = { viewModel.nfcGetBackend() }
                                )
                            }

                            item {
                                ScanCard(
                                    isConnected = isConnected,
                                    nfcCapability = nfcCapability.isUsable,
                                    taskRunning = nfcTaskRunning,
                                    tags = nfcTags,
                                    saveResult = nfcSaveResult,
                                    onScan = { viewModel.nfcScan(parse = true) },
                                    onOnce = { viewModel.nfcOnce(parse = true) },
                                    onSave = { viewModel.nfcSave() },
                                    onStop = { viewModel.nfcStop() },
                                    onStatus = { viewModel.nfcStatus() },
                                    onClear = { viewModel.clearNfcTags() }
                                )
                            }
                        }

                        1 -> {
                            item {
                                HardnestedCard(
                                    isConnected = isConnected,
                                    result = nfcHardnestedResult,
                                    onRun = { known, knownType, key, target, targetType, samples ->
                                        viewModel.nfcHardnested(known, knownType, key, target, targetType, samples)
                                    }
                                )
                            }

                            item {
                                PicopassCard(
                                    isConnected = isConnected,
                                    isPn532 = isPn532,
                                    result = nfcPicopassResult,
                                    onScan = { viewModel.nfcPicopass() }
                                )
                            }
                        }

                        2 -> {
                            item {
                                EmulateCard(
                                    isConnected = isConnected,
                                    isPn532 = isPn532,
                                    status = nfcEmulateStatus,
                                    lastScannedTag = nfcTags.lastOrNull(),
                                    sdFiles = sdEntries,
                                    onBrowseFiles = { viewModel.listSdFiles(NFC_FILES_DIR) },
                                    onEmulateUid = { uid, atqa, sak -> viewModel.nfcEmulateUid(uid, atqa, sak) },
                                    onEmulateNdef = { url, text -> viewModel.nfcEmulateNdef(url, text) },
                                    onEmulateFile = { path -> viewModel.nfcEmulateFile(path) },
                                    onStop = { viewModel.nfcEmulateStop() }
                                )
                            }
                        }
                    }

                    statusMessage?.let { status ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(
                                    text = status,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (nfcCapability == GhostResponse.CapabilityResolution.UNSUPPORTED) {
                FeatureNotSupportedOverlay(
                    show = showOverlay,
                    onProceed = { showOverlay = false },
                    featureName = stringResource(R.string.title_nfc),
                    message = stringResource(R.string.msg_nfc_unsupported)
                )
            }
        }
    }
}

@Composable
private fun BackendCard(
    isConnected: Boolean,
    backend: String?,
    onSelect: (GhostCommand.NfcBackendType) -> Unit,
    onRefresh: () -> Unit
) {
    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Nfc, contentDescription = null, tint = primaryColor())
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.title_nfc_backend), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = isConnected) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_nfc_status))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = listOf(
                    GhostCommand.NfcBackendType.AUTO to stringResource(R.string.label_backend_auto),
                    GhostCommand.NfcBackendType.PN532 to stringResource(R.string.label_backend_pn532),
                    GhostCommand.NfcBackendType.ST25R to stringResource(R.string.label_backend_st25r)
                )
                options.forEach { (type, label) ->
                    val selected = backend?.equals(type.value, ignoreCase = true) == true
                    BrutalistChip(
                        text = label,
                        onClick = { if (isConnected) onSelect(type) },
                        backgroundColor = if (selected) primaryColor().copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        borderColor = if (selected) primaryColor() else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanCard(
    isConnected: Boolean,
    nfcCapability: Boolean,
    taskRunning: Boolean,
    tags: List<GhostResponse.NfcTag>,
    saveResult: GhostResponse.NfcSaveResult?,
    onScan: () -> Unit,
    onOnce: () -> Unit,
    onSave: () -> Unit,
    onStop: () -> Unit,
    onStatus: () -> Unit,
    onClear: () -> Unit
) {
    val enabled = isConnected && nfcCapability
    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Contactless, contentDescription = null, tint = primaryColor())
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.title_scan_tag), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.Circle,
                    contentDescription = null,
                    tint = if (taskRunning) successColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (taskRunning) stringResource(R.string.label_nfc_task_running) else stringResource(R.string.label_nfc_task_idle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalistButton(text = stringResource(R.string.action_nfc_scan), onClick = onScan, enabled = enabled, modifier = Modifier.weight(1f))
                BrutalistButton(text = stringResource(R.string.action_nfc_once), onClick = onOnce, enabled = enabled, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalistOutlinedButton(text = stringResource(R.string.action_nfc_save), onClick = onSave, enabled = enabled, modifier = Modifier.weight(1f))
                BrutalistOutlinedButton(text = stringResource(R.string.action_nfc_status), onClick = onStatus, enabled = enabled, modifier = Modifier.weight(1f))
                BrutalistOutlinedButton(text = stringResource(R.string.action_nfc_stop), onClick = onStop, enabled = enabled, modifier = Modifier.weight(1f))
            }

            saveResult?.let { result ->
                Text(
                    text = if (result.success) "Saved: ${result.dumpType} → ${result.path}" else "Save failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.success) successColor() else errorColor()
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.label_recent_tags, tags.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onClear, enabled = tags.isNotEmpty()) {
                    Text(stringResource(R.string.action_clear))
                }
            }

            if (tags.isEmpty()) {
                Text(
                    text = stringResource(R.string.msg_no_recent_tags),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                tags.take(5).forEach { tag ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tag.type.name, style = MaterialTheme.typography.bodySmall)
                        Text(tag.uid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HardnestedCard(
    isConnected: Boolean,
    result: GhostResponse.NfcHardnestedResult?,
    onRun: (Int, GhostCommand.NfcKeyType, String, Int, GhostCommand.NfcKeyType, Int?) -> Unit
) {
    var knownBlock by rememberSaveable { mutableStateOf("0") }
    var knownKeyType by rememberSaveable { mutableStateOf(GhostCommand.NfcKeyType.A) }
    var knownKey by rememberSaveable { mutableStateOf("FFFFFFFFFFFF") }
    var targetBlock by rememberSaveable { mutableStateOf("4") }
    var targetKeyType by rememberSaveable { mutableStateOf(GhostCommand.NfcKeyType.A) }
    var samples by rememberSaveable { mutableStateOf("") }

    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = primaryColor())
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.title_hardnested), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                text = stringResource(R.string.msg_hardnested_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = knownBlock,
                    onValueChange = { knownBlock = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.label_known_block)) },
                    singleLine = true,
                    enabled = isConnected,
                    modifier = Modifier.weight(1f)
                )
                KeyTypeToggle(
                    label = stringResource(R.string.label_known_key_type),
                    value = knownKeyType,
                    enabled = isConnected,
                    onChange = { knownKeyType = it },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = knownKey,
                onValueChange = { knownKey = it.uppercase() },
                label = { Text(stringResource(R.string.label_known_key)) },
                singleLine = true,
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KNOWN_MIFARE_KEYS.forEach { key ->
                    BrutalistChip(
                        text = key,
                        onClick = { if (isConnected) knownKey = key },
                        backgroundColor = if (knownKey == key) primaryColor().copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        borderColor = if (knownKey == key) primaryColor() else MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = targetBlock,
                    onValueChange = { targetBlock = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.label_target_block)) },
                    singleLine = true,
                    enabled = isConnected,
                    modifier = Modifier.weight(1f)
                )
                KeyTypeToggle(
                    label = stringResource(R.string.label_target_key_type),
                    value = targetKeyType,
                    enabled = isConnected,
                    onChange = { targetKeyType = it },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = samples,
                onValueChange = { samples = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.label_samples_optional)) },
                singleLine = true,
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth()
            )

            val knownBlockInt = knownBlock.toIntOrNull()
            val targetBlockInt = targetBlock.toIntOrNull()
            val canRun = isConnected && knownBlockInt != null && targetBlockInt != null && knownKey.length == 12

            BrutalistButton(
                text = stringResource(R.string.action_run_hardnested),
                onClick = {
                    if (canRun) {
                        onRun(knownBlockInt!!, knownKeyType, knownKey, targetBlockInt!!, targetKeyType, samples.toIntOrNull())
                    }
                },
                enabled = canRun,
                modifier = Modifier.fillMaxWidth()
            )

            result?.let {
                Text(
                    text = if (it.success) "Saved: ${it.path}" else "Hardnested attack failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.success) successColor() else errorColor()
                )
            }
        }
    }
}

@Composable
private fun KeyTypeToggle(
    label: String,
    value: GhostCommand.NfcKeyType,
    enabled: Boolean,
    onChange: (GhostCommand.NfcKeyType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostCommand.NfcKeyType.values().forEach { type ->
                BrutalistChip(
                    text = type.value,
                    onClick = { if (enabled) onChange(type) },
                    backgroundColor = if (value == type) primaryColor().copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                    borderColor = if (value == type) primaryColor() else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun PicopassCard(
    isConnected: Boolean,
    isPn532: Boolean,
    result: GhostResponse.NfcPicopassResult?,
    onScan: () -> Unit
) {
    val enabled = isConnected && !isPn532
    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = primaryColor())
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.title_picopass), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (isPn532) {
                Text(
                    text = stringResource(R.string.msg_picopass_st25r_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BrutalistButton(
                text = stringResource(R.string.action_scan_picopass),
                onClick = onScan,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
            result?.let {
                when {
                    it.unsupported -> Text(stringResource(R.string.msg_picopass_st25r_only), style = MaterialTheme.typography.bodySmall, color = errorColor())
                    !it.found -> Text("No PicoPass/iCLASS tag found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Column {
                        Text("CSN: ${it.csn ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        if (it.authFailed) {
                            Text("Auth failed; CSN/config/AIA read OK", style = MaterialTheme.typography.bodySmall, color = errorColor())
                        }
                        if (it.fc != null) {
                            Text("PACS: FC=${it.fc} CN=${it.cn} (${it.bits}bit)", style = MaterialTheme.typography.bodySmall)
                        }
                        if (it.encryption != null) {
                            Text(
                                "Encryption: 0x${it.encryption}, Biometrics: ${if (it.biometrics == true) "yes" else "no"}, PIN len: ${it.pinLen}, SIO: ${if (it.sio == true) "yes" else "no"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class EmulateMode { UID, NDEF, FILE }

@Composable
private fun EmulateCard(
    isConnected: Boolean,
    isPn532: Boolean,
    status: GhostResponse.NfcEmulateStatus?,
    lastScannedTag: GhostResponse.NfcTag?,
    sdFiles: List<GhostResponse.SdEntry>,
    onBrowseFiles: () -> Unit,
    onEmulateUid: (String, String?, String?) -> Unit,
    onEmulateNdef: (String?, String?) -> Unit,
    onEmulateFile: (String) -> Unit,
    onStop: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(EmulateMode.UID) }
    var uid by rememberSaveable { mutableStateOf("") }
    var atqa by rememberSaveable { mutableStateOf("") }
    var sak by rememberSaveable { mutableStateOf("") }
    var ndefUseText by rememberSaveable { mutableStateOf(false) }
    var ndefUrl by rememberSaveable { mutableStateOf("https://ghostesp.net") }
    var ndefText by rememberSaveable { mutableStateOf("") }
    var filePath by rememberSaveable { mutableStateOf("") }
    var showFilePicker by remember { mutableStateOf(false) }

    val enabled = isConnected && !isPn532

    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sensors, contentDescription = null, tint = primaryColor())
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.title_emulate), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (isPn532) {
                Text(
                    text = stringResource(R.string.msg_picopass_st25r_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalistChip(
                    text = stringResource(R.string.label_emulate_mode_uid),
                    onClick = { mode = EmulateMode.UID },
                    backgroundColor = if (mode == EmulateMode.UID) primaryColor().copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                    borderColor = if (mode == EmulateMode.UID) primaryColor() else MaterialTheme.colorScheme.outline
                )
                BrutalistChip(
                    text = stringResource(R.string.label_emulate_mode_ndef),
                    onClick = { mode = EmulateMode.NDEF },
                    backgroundColor = if (mode == EmulateMode.NDEF) primaryColor().copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                    borderColor = if (mode == EmulateMode.NDEF) primaryColor() else MaterialTheme.colorScheme.outline
                )
                BrutalistChip(
                    text = stringResource(R.string.label_emulate_mode_file),
                    onClick = { mode = EmulateMode.FILE },
                    backgroundColor = if (mode == EmulateMode.FILE) primaryColor().copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                    borderColor = if (mode == EmulateMode.FILE) primaryColor() else MaterialTheme.colorScheme.outline
                )
            }

            when (mode) {
                EmulateMode.UID -> {
                    if (lastScannedTag != null) {
                        BrutalistOutlinedButton(
                            text = stringResource(R.string.action_use_last_scanned_tag, lastScannedTag.uid),
                            onClick = {
                                uid = lastScannedTag.uid
                                atqa = lastScannedTag.atqa ?: atqa
                                sak = lastScannedTag.sak ?: sak
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = uid,
                        onValueChange = { uid = it },
                        label = { Text(stringResource(R.string.label_uid_hex)) },
                        singleLine = true,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = atqa,
                            onValueChange = { atqa = it },
                            label = { Text(stringResource(R.string.label_atqa_optional)) },
                            singleLine = true,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sak,
                            onValueChange = { sak = it },
                            label = { Text(stringResource(R.string.label_sak_optional)) },
                            singleLine = true,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    BrutalistButton(
                        text = stringResource(R.string.action_start_emulate_uid),
                        onClick = { onEmulateUid(uid.trim(), atqa.trim().ifBlank { null }, sak.trim().ifBlank { null }) },
                        enabled = enabled && uid.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                EmulateMode.NDEF -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrutalistChip(
                            text = stringResource(R.string.label_ndef_url),
                            onClick = { ndefUseText = false },
                            backgroundColor = if (!ndefUseText) primaryColor().copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            borderColor = if (!ndefUseText) primaryColor() else MaterialTheme.colorScheme.outline
                        )
                        BrutalistChip(
                            text = stringResource(R.string.label_ndef_text),
                            onClick = { ndefUseText = true },
                            backgroundColor = if (ndefUseText) primaryColor().copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            borderColor = if (ndefUseText) primaryColor() else MaterialTheme.colorScheme.outline
                        )
                    }
                    if (ndefUseText) {
                        OutlinedTextField(
                            value = ndefText,
                            onValueChange = { ndefText = it },
                            label = { Text(stringResource(R.string.label_ndef_text)) },
                            singleLine = true,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = ndefUrl,
                            onValueChange = { ndefUrl = it },
                            label = { Text(stringResource(R.string.label_ndef_url)) },
                            singleLine = true,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    BrutalistButton(
                        text = stringResource(R.string.action_start_emulate_ndef),
                        onClick = {
                            if (ndefUseText) onEmulateNdef(null, ndefText.trim())
                            else onEmulateNdef(ndefUrl.trim().ifBlank { null }, null)
                        },
                        enabled = enabled && (if (ndefUseText) ndefText.isNotBlank() else true),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                EmulateMode.FILE -> {
                    OutlinedTextField(
                        value = filePath,
                        onValueChange = { filePath = it },
                        label = { Text(stringResource(R.string.label_nfc_file_path)) },
                        singleLine = true,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                    BrutalistOutlinedButton(
                        text = stringResource(R.string.action_browse_nfc_files),
                        onClick = {
                            showFilePicker = true
                            onBrowseFiles()
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                    BrutalistButton(
                        text = stringResource(R.string.action_start_emulate_file),
                        onClick = { onEmulateFile(filePath.trim()) },
                        enabled = enabled && filePath.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (showFilePicker) {
                NfcFilePickerDialog(
                    files = sdFiles.filter { !it.isDirectory && it.name.endsWith(".nfc", ignoreCase = true) },
                    onSelect = { path ->
                        filePath = path
                        showFilePicker = false
                    },
                    onDismiss = { showFilePicker = false }
                )
            }

            BrutalistOutlinedButton(
                text = stringResource(R.string.action_nfc_stop),
                onClick = onStop,
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth()
            )

            status?.let {
                Text(
                    text = if (it.running) stringResource(R.string.msg_emulating_uid, it.uid ?: "?") else stringResource(R.string.msg_emulation_stopped),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.running) successColor() else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NfcFilePickerDialog(
    files: List<GhostResponse.SdEntry>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_pick_nfc_file)) },
        text = {
            if (files.isEmpty()) {
                Text(
                    text = stringResource(R.string.msg_no_nfc_files),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(files) { entry ->
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(entry.path) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
