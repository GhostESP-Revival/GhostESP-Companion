package com.example.ghostespcompanion.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.repository.BadUsbConfig
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostCommand
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.components.BrutalistButton
import com.example.ghostespcompanion.ui.components.BrutalistCard
import com.example.ghostespcompanion.ui.components.BrutalistOutlinedButton
import com.example.ghostespcompanion.ui.components.FeatureNotSupportedOverlay
import com.example.ghostespcompanion.ui.components.CapabilityNotice
import com.example.ghostespcompanion.ui.components.resolve
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
    var showUnsupportedOverlay by rememberSaveable { mutableStateOf(true) }

    val connectionState by viewModel.connectionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val scripts by viewModel.badUsbScripts.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    val badUsbCapability = deviceInfo.resolve(GhostResponse.DeviceFeature.BADUSB)
    val commandsEnabled = isConnected && badUsbCapability.isUsable

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
            viewModel.badUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Stop)
        }
    }

    LaunchedEffect(isConnected) {
        if (commandsEnabled) {
            viewModel.listBadUsbScripts()
        }
    }

    MainScreen(
        onBack = onBack,
        title = stringResource(R.string.title_bad_usb),
        actions = {
            IconButton(
                onClick = { viewModel.listBadUsbScripts() },
                enabled = commandsEnabled
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh), tint = primaryColor())
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            var selectedTab by rememberSaveable { mutableStateOf(0) }
            val tabTitles = listOf(
                stringResource(R.string.tab_badusb_scripts),
                stringResource(R.string.tab_badusb_keyboard),
                stringResource(R.string.tab_badusb_trackpad),
                stringResource(R.string.tab_badusb_config)
            )

            Column(modifier = Modifier.fillMaxSize()) {
                CapabilityNotice(badUsbCapability, stringResource(R.string.title_bad_usb))

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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        UsbConnectionBanner(
                            isConnected = isConnected,
                            deviceName = stringResource(R.string.app_name_short),
                            onConnect = { viewModel.connectFirstAvailable() }
                        )
                    }

                    when (selectedTab) {
                        0 -> {
                            if (runningScript != null) {
                                item {
                                    ActiveBadUsbCard(
                                        label = stringResource(R.string.msg_running_script, runningScript!!),
                                        onStop = {
                                            viewModel.stopBadUsb()
                                            runningScript = null
                                        },
                                        enabled = commandsEnabled
                                    )
                                }
                            }

                            item {
                                Text(
                                    text = stringResource(R.string.label_scripts_count, scripts.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor()
                                )
                            }

                            item {
                                val builtinScriptLabel = stringResource(R.string.label_builtin_script)
                                BrutalistOutlinedButton(
                                    text = stringResource(R.string.action_run_builtin_script),
                                    onClick = {
                                        viewModel.runBadUsbScript(GhostCommand.BadUsbRun.builtin().filename)
                                        runningScript = builtinScriptLabel
                                    },
                                    enabled = commandsEnabled && runningScript == null,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (!isConnected) {
                                item {
                                    EmptyBadUsbCard(stringResource(R.string.msg_badusb_not_connected))
                                }
                            } else if (scripts.isEmpty()) {
                                item {
                                    EmptyBadUsbCard(stringResource(R.string.msg_no_scripts_found))
                                }
                            } else {
                                items(scripts, key = { it }) { script ->
                                    BadUsbScriptCard(
                                        script = script,
                                        isRunning = runningScript == script,
                                        enabled = commandsEnabled && runningScript == null,
                                        onRun = {
                                            viewModel.runBadUsbScript(script.toFirmwareBadUsbRunArg())
                                            runningScript = script
                                        }
                                    )
                                }
                            }
                        }

                        1 -> {
                            item {
                                DirectKeyboardCard(
                                    isConnected = commandsEnabled,
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
                                    },
                                    onTypeChar = { code -> viewModel.typeBadUsbChar(code) }
                                )
                            }

                            item {
                                KeysendCard(isConnected = commandsEnabled, viewModel = viewModel)
                            }

                            item {
                                MouseJigglerCard(
                                    isConnected = commandsEnabled,
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
                        }

                        2 -> {
                            item {
                                BadUsbTrackpadCard(
                                    isConnected = commandsEnabled,
                                    viewModel = viewModel
                                )
                            }
                        }

                        3 -> {
                            item {
                                BadUsbConfigCard(
                                    isConnected = commandsEnabled,
                                    viewModel = viewModel
                                )
                            }
                        }
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
            }

            // Feature Not Supported Overlay
            if (badUsbCapability == GhostResponse.CapabilityResolution.UNSUPPORTED) {
                FeatureNotSupportedOverlay(
                    show = showUnsupportedOverlay,
                    onProceed = { showUnsupportedOverlay = false },
                    featureName = stringResource(R.string.title_bad_usb),
                    message = stringResource(R.string.msg_badusb_unsupported)
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
                text = stringResource(R.string.action_stop),
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
                        text = if (isRunning) stringResource(R.string.status_connecting).replace("…", "") else stringResource(R.string.label_firmware_script),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            BrutalistButton(
                text = if (isRunning) stringResource(R.string.status_connecting).replace("…", "") else stringResource(R.string.label_start).lowercase().replaceFirstChar { it.uppercase() },
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
    onType: () -> Unit,
    onTypeChar: (Int) -> Unit
) {
    var charToType by remember { mutableStateOf("") }

    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Keyboard, contentDescription = null, tint = primaryColor())
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.label_direct_keyboard), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                text = stringResource(R.string.msg_keyboard_commands_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalistOutlinedButton(
                    text = if (keyboardActive) stringResource(R.string.action_stop_keyboard) else stringResource(R.string.action_start_keyboard),
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
                label = { Text(stringResource(R.string.label_text_to_type)) },
                singleLine = true
            )
            BrutalistButton(
                text = stringResource(R.string.action_type_text),
                onClick = onType,
                enabled = isConnected && textToType.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Keyboard, contentDescription = null) }
            )
            OutlinedTextField(
                value = charToType,
                onValueChange = { charToType = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected,
                label = { Text(stringResource(R.string.label_char_code_to_type)) },
                singleLine = true
            )
            BrutalistButton(
                text = stringResource(R.string.action_send_char),
                onClick = {
                    val code = charToType.toIntOrNull()
                    if (code != null) {
                        onTypeChar(code)
                        charToType = ""
                    }
                },
                enabled = isConnected && charToType.toIntOrNull() != null,
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
                    Text(stringResource(R.string.label_mouse_jiggler), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (jigglerActive) stringResource(R.string.status_connecting).replace("…", "") else stringResource(R.string.msg_jiggler_commands_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            BrutalistButton(
                text = if (jigglerActive) stringResource(R.string.action_stop) else stringResource(R.string.label_start).lowercase().replaceFirstChar { it.uppercase() },
                onClick = onToggle,
                enabled = isConnected,
                containerColor = if (jigglerActive) errorColor() else primaryColor(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

// USB HID modifier bitmask values, see HID_MOD_* in Ghost_ESP/main/managers/hid_script_parser.c
private val keysendModifierOptions = listOf(
    "Ctrl" to 0x01,
    "Shift" to 0x02,
    "Alt" to 0x04,
    "GUI" to 0x08
)

// USB HID usage IDs, see HID_KEY_* in Ghost_ESP/main/managers/hid_script_parser.c
private val keysendKeyOptions: List<Pair<String, Int>> = buildList {
    add("Enter" to 0x28)
    add("Esc" to 0x29)
    add("Backspace" to 0x2A)
    add("Tab" to 0x2B)
    add("Delete" to 0x4C)
    add("Right" to 0x4F)
    add("Left" to 0x50)
    add("Down" to 0x51)
    add("Up" to 0x52)
    for (letter in 'A'..'Z') add(letter.toString() to (0x04 + (letter - 'A')))
    for (digit in 1..9) add(digit.toString() to (0x1E + (digit - 1)))
    add("0" to 0x27)
    for (fKey in 1..12) add("F$fKey" to (0x3A + (fKey - 1)))
}

@Composable
private fun KeysendCard(
    isConnected: Boolean,
    viewModel: MainViewModel
) {
    var keyMod by rememberSaveable { mutableStateOf("0") }
    var keyCode by rememberSaveable { mutableStateOf("4") }
    var selectedMods by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var keyMenuExpanded by remember { mutableStateOf(false) }
    val selectedKeyName = keysendKeyOptions.find { it.second == keyCode.toIntOrNull() }?.first

    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Keyboard, contentDescription = null, tint = primaryColor())
                Spacer(modifier = Modifier.width(8.dp))
                Text("Keysend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            Text(stringResource(R.string.label_modifiers), style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keysendModifierOptions.forEach { (name, bit) ->
                    FilterChip(
                        selected = bit in selectedMods,
                        onClick = {
                            selectedMods = if (bit in selectedMods) selectedMods - bit else selectedMods + bit
                            keyMod = selectedMods.sum().toString()
                        },
                        label = { Text(name) },
                        enabled = isConnected,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryColor().copy(alpha = 0.2f),
                            selectedLabelColor = primaryColor()
                        )
                    )
                }
            }

            Text(stringResource(R.string.label_key), style = MaterialTheme.typography.labelMedium)
            Box {
                OutlinedButton(
                    onClick = { keyMenuExpanded = true },
                    enabled = isConnected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedKeyName ?: "Custom ($keyCode)", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ExpandMore, contentDescription = null)
                }
                DropdownMenu(
                    expanded = keyMenuExpanded,
                    onDismissRequest = { keyMenuExpanded = false }
                ) {
                    keysendKeyOptions.forEach { (name, code) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                keyCode = code.toString()
                                keyMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Text(stringResource(R.string.label_advanced_raw_entry), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keyMod,
                    onValueChange = {
                        keyMod = it.filter { c -> c.isDigit() }
                        selectedMods = emptySet()
                    },
                    label = { Text("Mod") },
                    singleLine = true,
                    enabled = isConnected,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = keyCode,
                    onValueChange = { keyCode = it.filter { c -> c.isDigit() } },
                    label = { Text("KeyCode") },
                    singleLine = true,
                    enabled = isConnected,
                    modifier = Modifier.weight(1f)
                )
            }
            BrutalistButton(
                text = "Keysend",
                onClick = {
                    val mod = keyMod.toIntOrNull() ?: 0
                    val code = keyCode.toIntOrNull() ?: 0
                    if (isConnected) viewModel.badUsbKey(mod, code)
                },
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BadUsbTrackpadCard(
    isConnected: Boolean,
    viewModel: MainViewModel
) {
    var isPadActive by remember { mutableStateOf(false) }
    val padUsable = isConnected && isPadActive

    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mouse, contentDescription = null, tint = primaryColor())
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Trackpad",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isPadActive,
                    onCheckedChange = { enabled ->
                        isPadActive = enabled
                        if (isConnected) {
                            viewModel.badUsbTrackpad(
                                if (enabled) GhostCommand.BadUsbTrackpadAction.Start else GhostCommand.BadUsbTrackpadAction.Stop
                            )
                        }
                    },
                    enabled = isConnected
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        if (padUsable) primaryColor() else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(12.dp)
                    )
                    .pointerInput(padUsable) {
                        if (padUsable) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                viewModel.badUsbTrackpad(
                                    GhostCommand.BadUsbTrackpadAction.Move(
                                        dragAmount.x.roundToInt(),
                                        dragAmount.y.roundToInt()
                                    )
                                )
                            }
                        }
                    }
                    .pointerInput(padUsable) {
                        if (padUsable) {
                            detectTapGestures {
                                viewModel.badUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Button(1))
                                viewModel.badUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Button(0))
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (padUsable) "Drag to move • Tap to click" else "Enable trackpad to use",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalistButton(
                    text = "Left",
                    onClick = { if (isConnected) viewModel.badUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Button(1)) },
                    enabled = padUsable,
                    modifier = Modifier.weight(1f)
                )
                BrutalistButton(
                    text = "Right",
                    onClick = { if (isConnected) viewModel.badUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Button(2)) },
                    enabled = padUsable,
                    modifier = Modifier.weight(1f)
                )
                BrutalistButton(
                    text = "Middle",
                    onClick = { if (isConnected) viewModel.badUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Button(4)) },
                    enabled = padUsable,
                    modifier = Modifier.weight(1f)
                )
                BrutalistButton(
                    text = "Release",
                    onClick = { if (isConnected) viewModel.badUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Button(0)) },
                    enabled = padUsable,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalistButton(
                    text = "Scroll Up",
                    onClick = { if (isConnected) viewModel.badUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Wheel(-5)) },
                    enabled = padUsable,
                    modifier = Modifier.weight(1f)
                )
                BrutalistButton(
                    text = "Scroll Down",
                    onClick = { if (isConnected) viewModel.badUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Wheel(5)) },
                    enabled = padUsable,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// Real vendor/product HID pairs commonly emulated by BadUSB payloads.
private data class UsbDevicePreset(val label: String, val vid: String, val pid: String)

private val usbDevicePresets = listOf(
    UsbDevicePreset("Apple Mouse", "0x05AC", "0x0210"),
    UsbDevicePreset("Apple Keyboard", "0x05AC", "0x0220"),
    UsbDevicePreset("Logitech Unifying", "0x046D", "0xC52B"),
    UsbDevicePreset("Microsoft Keyboard", "0x045E", "0x0750")
)

// Layout indices, see s_active_layout switch in Ghost_ESP/main/managers/hid_script_parser.c
private val badUsbLayoutOptions = listOf(
    0 to "US (default)",
    1 to "German (DE)",
    2 to "French (FR)",
    3 to "UK",
    4 to "Spanish (ES)"
)

@Composable
private fun BadUsbConfigCard(
    isConnected: Boolean,
    viewModel: MainViewModel
) {
    val lastConfig by viewModel.lastBadUsbConfig.collectAsState()
    var vid by rememberSaveable { mutableStateOf(lastConfig.vid) }
    var pid by rememberSaveable { mutableStateOf(lastConfig.pid) }
    var mfr by rememberSaveable { mutableStateOf(lastConfig.mfr) }
    var prod by rememberSaveable { mutableStateOf(lastConfig.prod) }
    var layout by rememberSaveable { mutableStateOf(lastConfig.layout.toString()) }
    var randomize by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("running") }
    var execSize by rememberSaveable { mutableStateOf("64") }
    var layoutMenuExpanded by remember { mutableStateOf(false) }
    var loadedPersisted by remember { mutableStateOf(false) }

    LaunchedEffect(lastConfig) {
        if (!loadedPersisted) {
            vid = lastConfig.vid
            pid = lastConfig.pid
            mfr = lastConfig.mfr
            prod = lastConfig.prod
            layout = lastConfig.layout.toString()
            loadedPersisted = true
        }
    }

    fun persist() {
        viewModel.setLastBadUsbConfig(
            BadUsbConfig(vid = vid, pid = pid, mfr = mfr, prod = prod, layout = layout.toIntOrNull() ?: 0)
        )
    }

    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Usb, contentDescription = null, tint = primaryColor())
                Spacer(modifier = Modifier.width(8.dp))
                Text("USB Config", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "VID/PID, manufacturer, product, layout. Responses display in Terminal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(stringResource(R.string.label_device_preset), style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                usbDevicePresets.forEach { preset ->
                    FilterChip(
                        selected = vid.equals(preset.vid, ignoreCase = true) && pid.equals(preset.pid, ignoreCase = true),
                        onClick = {
                            vid = preset.vid
                            pid = preset.pid
                            persist()
                        },
                        label = { Text(preset.label) },
                        enabled = isConnected,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryColor().copy(alpha = 0.2f),
                            selectedLabelColor = primaryColor()
                        )
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vid,
                    onValueChange = { vid = it },
                    label = { Text("VID") },
                    singleLine = true,
                    enabled = isConnected,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = pid,
                    onValueChange = { pid = it },
                    label = { Text("PID") },
                    singleLine = true,
                    enabled = isConnected,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalistButton(
                    text = "Set VID",
                    onClick = {
                        if (isConnected) viewModel.badUsbConfig(GhostCommand.BadUsbSetting.VendorId(vid.trim()))
                        persist()
                    },
                    enabled = isConnected && vid.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
                BrutalistButton(
                    text = "Set PID",
                    onClick = {
                        if (isConnected) viewModel.badUsbConfig(GhostCommand.BadUsbSetting.ProductId(pid.trim()))
                        persist()
                    },
                    enabled = isConnected && pid.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = mfr,
                onValueChange = { mfr = it },
                label = { Text("Manufacturer") },
                singleLine = true,
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth()
            )
            BrutalistButton(
                text = "Set Manufacturer",
                onClick = {
                    if (isConnected && mfr.isNotBlank()) viewModel.badUsbConfig(GhostCommand.BadUsbSetting.Manufacturer(mfr.trim()))
                    persist()
                },
                enabled = isConnected && mfr.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = prod,
                onValueChange = { prod = it },
                label = { Text("Product") },
                singleLine = true,
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth()
            )
            BrutalistButton(
                text = "Set Product",
                onClick = {
                    if (isConnected && prod.isNotBlank()) viewModel.badUsbConfig(GhostCommand.BadUsbSetting.Product(prod.trim()))
                    persist()
                },
                enabled = isConnected && prod.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.label_layout), style = MaterialTheme.typography.labelMedium)
            Box {
                OutlinedButton(
                    onClick = { layoutMenuExpanded = true },
                    enabled = isConnected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        badUsbLayoutOptions.find { it.first == layout.toIntOrNull() }?.second ?: "Custom ($layout)",
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ExpandMore, contentDescription = null)
                }
                DropdownMenu(
                    expanded = layoutMenuExpanded,
                    onDismissRequest = { layoutMenuExpanded = false }
                ) {
                    badUsbLayoutOptions.forEach { (index, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                layout = index.toString()
                                layoutMenuExpanded = false
                            }
                        )
                    }
                }
            }
            BrutalistButton(
                text = "Set Layout",
                onClick = {
                    val idx = layout.toIntOrNull()
                    if (isConnected && idx != null) viewModel.badUsbConfig(GhostCommand.BadUsbSetting.Layout(idx))
                    persist()
                },
                enabled = isConnected && layout.toIntOrNull() != null,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Randomize USB details", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = randomize,
                    onCheckedChange = { checked ->
                        randomize = checked
                        if (isConnected) viewModel.badUsbConfig(GhostCommand.BadUsbSetting.Randomize(checked))
                    },
                    enabled = isConnected
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Usb, contentDescription = null, tint = primaryColor())
                Spacer(modifier = Modifier.width(8.dp))
                Text("Exec / Status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = execSize,
                onValueChange = { execSize = it.filter { c -> c.isDigit() } },
                label = { Text("Exec buffer size") },
                singleLine = true,
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth()
            )
            BrutalistButton(
                text = "Exec",
                onClick = {
                    val size = execSize.toIntOrNull()
                    if (isConnected && size != null) viewModel.badUsbExec(size)
                },
                enabled = isConnected && execSize.toIntOrNull() != null,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = status,
                onValueChange = { status = it },
                label = { Text("Status query") },
                singleLine = true,
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth()
            )
            BrutalistButton(
                text = "Status",
                onClick = {
                    if (isConnected && status.isNotBlank()) viewModel.badUsbStatus(status.trim())
                },
                enabled = isConnected && status.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
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
                text = stringResource(R.string.msg_badusb_warning),
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
                        text = if (isConnected) stringResource(R.string.status_connected_device, deviceName) else stringResource(R.string.status_disconnected),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) successColor() else errorColor()
                    )
                    if (isConnected) {
                        Text(
                            text = stringResource(R.string.msg_ready_for_badusb),
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
                    Text(stringResource(R.string.action_connect), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
