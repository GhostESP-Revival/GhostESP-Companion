package com.example.ghostespcompanion.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.theme.*

/**
 * Minimalist Neo-Brutalist UI Components
 * 
 * Features:
 * - Clean white/gray borders
 * - Subtle offset shadows
 * - Rounded corners
 * - Dark mode first design
 * - Professional minimalist aesthetic
 */

/**
 * Neo-Brutalist Card with subtle border and shadow
 * 
 * Performance optimized: Uses Box with offset shadow surface instead of drawBehind
 * to reduce GPU overdraw and avoid per-frame drawing operations.
 * 
 * Includes press scale animation for polished UX.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrutalistCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    shadowColor: Color = borderColor.copy(alpha = 0.2f),
    borderWidth: Dp = 1.dp,
    shadowOffset: Dp = 3.dp,
    cornerRadius: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    enableScaleAnimation: Boolean = true,
    scale: Float = 0.98f,
    enableHaptic: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = rememberHapticController()

    // Animate scale on press
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && enableScaleAnimation && onClick != null) scale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    // Use Box with offset shadow surface for better performance than drawBehind
    Box(modifier = modifier.scale(animatedScale)) {
        // Shadow layer - offset behind the card
        Surface(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset),
            shape = shape,
            color = shadowColor,
            content = {}
        )

        // Main card
        if (onClick != null) {
            Card(
                onClick = {
                    if (enableHaptic) haptic(HapticType.LIGHT)
                    onClick()
                },
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                border = BorderStroke(borderWidth, borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                interactionSource = interactionSource
            ) {
                Column(
                    modifier = Modifier.padding(contentPadding),
                    content = content
                )
            }
        } else {
            Card(
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                border = BorderStroke(borderWidth, borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(contentPadding),
                    content = content
                )
            }
        }
    }
}

/**
 * Neo-Brutalist Button with clean styling
 * 
 * Performance optimized: Uses Box with offset shadow surface instead of drawBehind.
 * Includes haptic feedback and scale animation for polished UX.
 */
@Composable
fun BrutalistButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = primaryColor(),
    borderColor: Color = containerColor,
    textColor: Color = MaterialTheme.colorScheme.onPrimary,
    shadowColor: Color = containerColor.copy(alpha = 0.2f),
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    iconTint: Color = textColor,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    enableScaleAnimation: Boolean = true,
    scale: Float = 0.98f,
    enableHaptic: Boolean = true
) {
    val shape = RoundedCornerShape(8.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = rememberHapticController()
    
    // Animate scale on press
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && enableScaleAnimation && enabled && !isLoading) scale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale"
    )
    
    Box(modifier = modifier.height(48.dp).scale(animatedScale)) {
        // Shadow layer - only when enabled and not loading
        if (enabled && !isLoading) {
            Surface(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 3.dp, y = 3.dp),
                shape = shape,
                color = shadowColor,
                content = {}
            )
        }
        
        Button(
            onClick = {
                if (enableHaptic && enabled && !isLoading) {
                    haptic(HapticType.MEDIUM)
                }
                onClick()
            },
            modifier = Modifier.matchParentSize(),
            enabled = enabled && !isLoading,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
            ),
            border = BorderStroke(1.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.5f)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            contentPadding = contentPadding,
            interactionSource = interactionSource
        ) {
            if (isLoading) {
                GhostSpinner(
                    color = textColor,
                    strokeWidth = 2.dp,
                    size = 18.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                if (leadingIcon != null) {
                    Box(Modifier.size(18.dp)) {
                        CompositionLocalProvider(LocalContentColor provides iconTint) {
                            leadingIcon()
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Text(
                text = text,
                style = BrutalistButtonText,
                color = if (enabled) textColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

/**
 * Neo-Brutalist Secondary Button (outlined style)
 * Includes haptic feedback and scale animation for polished UX.
 */
@Composable
fun BrutalistOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    enableScaleAnimation: Boolean = true,
    scale: Float = 0.98f,
    enableHaptic: Boolean = true
) {
    val shape = RoundedCornerShape(8.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = rememberHapticController()
    
    // Animate scale on press
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && enableScaleAnimation && enabled) scale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "outlinedButtonScale"
    )
    
    OutlinedButton(
        onClick = {
            if (enableHaptic && enabled) {
                haptic(HapticType.LIGHT)
            }
            onClick()
        },
        modifier = modifier.height(48.dp).scale(animatedScale),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = textColor,
            disabledContentColor = textColor.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.5f)),
        interactionSource = interactionSource
    ) {
        leadingIcon?.invoke()
        if (leadingIcon != null) Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = BrutalistButtonText,
            color = textColor
        )
    }
}

/**
 * Neo-Brutalist Chip/Tag
 */
@Composable
fun BrutalistChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        onClick = onClick ?: {}
    ) {
        Text(
            text = text,
            style = BrutalistCaption,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Neo-Brutalist Status Badge
 */
@Composable
fun BrutalistStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    statusColor: Color = successColor()
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = statusColor.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            style = BrutalistCaption,
            color = statusColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

/**
 * Neo-Brutalist Section Header
 */
@Composable
fun BrutalistSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    accentColor: Color = primaryColor()
) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Subtle accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(accentColor, RoundedCornerShape(2.dp))
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
    }
}

/**
 * Neo-Brutalist Divider
 */
@Composable
fun BrutalistDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(color)
    )
}

/**
 * BLE Connection Status Banner
 */
@Composable
fun BleConnectionBanner(
    isConnected: Boolean,
    deviceName: String,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val success = successColor()
    val error = errorColor()
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isConnected) success.copy(alpha = 0.15f) else error.copy(alpha = 0.15f),
        border = BorderStroke(
            1.dp,
            if (isConnected) success else error
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    tint = if (isConnected) success else error
                )
                Column {
                    Text(
                        text = if (isConnected) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isConnected) success else error
                    )
                    if (isConnected) {
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (!isConnected) {
                TextButton(onClick = onConnect) {
                    Text(stringResource(R.string.action_connect), color = error)
                }
            }
        }
    }
}

/**
 * WiFi Connection Status Banner
 */
@Composable
fun WifiConnectionBanner(
    isConnected: Boolean,
    deviceName: String? = null,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val success = successColor()
    val error = errorColor()
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isConnected) success.copy(alpha = 0.15f) else error.copy(alpha = 0.15f),
        border = BorderStroke(
            1.dp,
            if (isConnected) success else error
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (isConnected) success else error
                )
                Column {
                    Text(
                        text = if (isConnected) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isConnected) success else error
                    )
                    if (isConnected && deviceName != null) {
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (!isConnected) {
                TextButton(onClick = onConnect) {
                    Text(stringResource(R.string.action_connect), color = error)
                }
            }
        }
    }
}

/**
 * NFC Connection Status Banner
 */
@Composable
fun NfcConnectionBanner(
    isConnected: Boolean,
    deviceName: String = "GhostESP",
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val success = successColor()
    val error = errorColor()
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isConnected) success.copy(alpha = 0.15f) else error.copy(alpha = 0.15f),
        border = BorderStroke(
            1.dp,
            if (isConnected) success else error
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = null,
                    tint = if (isConnected) success else error
                )
                Column {
                    Text(
                        text = if (isConnected) stringResource(R.string.nfc_ready) else stringResource(R.string.nfc_unavailable),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isConnected) success else error
                    )
                }
            }
        }
    }
}

/**
 * Ethernet Connection Status Banner
 */
@Composable
fun EthernetConnectionBanner(
    isConnected: Boolean,
    deviceName: String,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val success = successColor()
    val error = errorColor()
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isConnected) success.copy(alpha = 0.15f) else error.copy(alpha = 0.15f),
        border = BorderStroke(
            1.dp,
            if (isConnected) success else error
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.SettingsEthernet else Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = if (isConnected) success else error
                )
                Column {
                    Text(
                        text = if (isConnected) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isConnected) success else error
                    )
                    if (isConnected) {
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (!isConnected) {
                TextButton(onClick = onConnect) {
                    Text(stringResource(R.string.action_connect), color = error)
                }
            }
        }
    }
}

/**
 * Device Info Dialog - Shows chip information from GhostESP
 */
@Composable
fun DeviceInfoDialog(
    deviceInfo: GhostResponse.DeviceInfo?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    chipInfoRaw: String? = null,
    chipInfoParseStatus: String? = null,
    chipInfoDebugLog: List<String> = emptyList()
) {
    val primary = primaryColor()
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .drawBehind {
                    drawRect(
                        color = primary.copy(alpha = 0.15f),
                        topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                        size = size
                    )
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(0.5.dp, primary.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.label_device_info),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (deviceInfo != null) {
                    deviceInfo.firmwareVersion?.let { fw ->
                        DeviceInfoRow(label = stringResource(R.string.label_firmware), value = fw)
                    }
                    deviceInfo.gitCommit?.let { commit ->
                        DeviceInfoRow(label = stringResource(R.string.label_git_commit), value = commit)
                    }
                    DeviceInfoRow(label = stringResource(R.string.label_model), value = deviceInfo.model)
                    DeviceInfoRow(label = stringResource(R.string.label_revision), value = "v${deviceInfo.revision}")
                    DeviceInfoRow(label = stringResource(R.string.label_cpu_cores), value = deviceInfo.cores.toString())
                    DeviceInfoRow(label = stringResource(R.string.label_features), value = deviceInfo.features)
                    DeviceInfoRow(
                        label = stringResource(R.string.label_free_heap),
                        value = stringResource(R.string.label_bytes, deviceInfo.freeHeap.toString())
                    )
                    DeviceInfoRow(
                        label = stringResource(R.string.label_min_free_heap),
                        value = stringResource(R.string.label_bytes, deviceInfo.minFreeHeap.toString())
                    )
                    DeviceInfoRow(label = stringResource(R.string.label_idf_version), value = deviceInfo.idfVersion)
                    deviceInfo.buildConfig?.let { config ->
                        DeviceInfoRow(label = stringResource(R.string.label_build_config), value = config)
                    }
                    
                    if (deviceInfo.enabledFeatures.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = primary.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = stringResource(R.string.label_enabled_features),
                            style = MaterialTheme.typography.labelLarge,
                            color = primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val featureNames = deviceInfo.enabledFeatures.map { feature ->
                            when (feature) {
                                GhostResponse.DeviceFeature.DISPLAY -> stringResource(R.string.feature_display)
                                GhostResponse.DeviceFeature.TOUCHSCREEN -> stringResource(R.string.feature_touchscreen)
                                GhostResponse.DeviceFeature.STATUS_DISPLAY -> stringResource(R.string.feature_status_display)
                                GhostResponse.DeviceFeature.NFC -> stringResource(R.string.feature_nfc)
                                GhostResponse.DeviceFeature.BADUSB -> stringResource(R.string.feature_badusb)
                                GhostResponse.DeviceFeature.INFRARED_TX -> stringResource(R.string.feature_infrared_tx)
                                GhostResponse.DeviceFeature.INFRARED_RX -> stringResource(R.string.feature_infrared_rx)
                                GhostResponse.DeviceFeature.GPS -> stringResource(R.string.feature_gps)
                                GhostResponse.DeviceFeature.ETHERNET -> stringResource(R.string.feature_ethernet)
                                GhostResponse.DeviceFeature.BATTERY -> stringResource(R.string.feature_battery)
                                GhostResponse.DeviceFeature.BATTERY_ADC -> stringResource(R.string.feature_battery_adc)
                                GhostResponse.DeviceFeature.FUEL_GAUGE -> stringResource(R.string.feature_fuel_gauge)
                                GhostResponse.DeviceFeature.RTC_CLOCK -> stringResource(R.string.feature_rtc_clock)
                                GhostResponse.DeviceFeature.COMPASS -> stringResource(R.string.feature_compass)
                                GhostResponse.DeviceFeature.ACCELEROMETER -> stringResource(R.string.feature_accelerometer)
                                GhostResponse.DeviceFeature.JOYSTICK -> stringResource(R.string.feature_joystick)
                                GhostResponse.DeviceFeature.CARDPUTER -> stringResource(R.string.feature_cardputer)
                                GhostResponse.DeviceFeature.TDECK -> stringResource(R.string.feature_tdeck)
                                GhostResponse.DeviceFeature.ROTARY_ENCODER -> stringResource(R.string.feature_rotary_encoder)
                                GhostResponse.DeviceFeature.USB_KEYBOARD -> stringResource(R.string.feature_usb_keyboard)
                                GhostResponse.DeviceFeature.GHOST_BOARD -> stringResource(R.string.feature_ghost_board)
                                GhostResponse.DeviceFeature.S3TWATCH -> stringResource(R.string.feature_s3twatch)
                                GhostResponse.DeviceFeature.SD_CARD_SPI -> stringResource(R.string.feature_sd_card_spi)
                                GhostResponse.DeviceFeature.SD_CARD_MMC -> stringResource(R.string.feature_sd_card_mmc)
                            }
                        }
                        
                        Text(
                            text = featureNames.sorted().joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Debug section
                    var showRawResponse by remember { mutableStateOf(false) }
                    val error = errorColor()
                    
                    if (deviceInfo.parseErrors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = error.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = stringResource(R.string.label_parse_errors),
                            style = MaterialTheme.typography.labelLarge,
                            color = error,
                            fontWeight = FontWeight.Bold
                        )
                        deviceInfo.parseErrors.forEach { err ->
                            Text(
                                text = "• $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = error
                            )
                        }
                    }
                    
                    // Raw response toggle
                    deviceInfo.rawResponse?.let { raw ->
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = primary.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(
                            onClick = { showRawResponse = !showRawResponse },
                            colors = ButtonDefaults.textButtonColors(contentColor = primary)
                        ) {
                            Icon(
                                if (showRawResponse) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showRawResponse) stringResource(R.string.action_hide_raw_response) else stringResource(R.string.action_show_raw_response),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        
                        if (showRawResponse) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = raw,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .horizontalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                } else {
                    // Loading state
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Spinner + waiting text
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = primary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = stringResource(R.string.msg_fetching_device_info),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Parse status line (visible as soon as anything is received)
                        chipInfoParseStatus?.let { status ->
                            val isError = status.startsWith("FAILED")
                            val statusColor = if (isError) errorColor() else MaterialTheme.colorScheme.tertiary
                            Text(
                                text = stringResource(R.string.label_parse_status, status),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = statusColor
                            )
                        }

                        // Raw response preview (first 200 chars)
                        chipInfoRaw?.let { raw ->
                            var showRaw by remember { mutableStateOf(false) }
                            TextButton(
                                onClick = { showRaw = !showRaw },
                                colors = ButtonDefaults.textButtonColors(contentColor = primary)
                            ) {
                                Icon(
                                    if (showRaw) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showRaw) stringResource(R.string.action_hide_raw_response) else stringResource(R.string.action_show_raw_response),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (showRaw) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = raw,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .horizontalScroll(rememberScrollState())
                                    )
                                }
                            }
                        }

                        // Serial debug log (flush/timer events from SerialManager)
                        if (chipInfoDebugLog.isNotEmpty()) {
                            var showLog by remember { mutableStateOf(false) }
                            TextButton(
                                onClick = { showLog = !showLog },
                                colors = ButtonDefaults.textButtonColors(contentColor = primary)
                            ) {
                                Icon(
                                    if (showLog) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showLog) stringResource(R.string.action_hide_serial_debug) else stringResource(R.string.action_show_serial_debug, chipInfoDebugLog.size),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (showLog) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        chipInfoDebugLog.forEach { entry ->
                                            Text(
                                                text = entry,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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
                
                // Refresh button
                BrutalistOutlinedButton(
                    text = stringResource(R.string.action_refresh),
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(2f)
        )
    }
    BrutalistDivider(
        modifier = Modifier.padding(top = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

/**
 * Coming Soon Overlay - Blurred overlay with "Coming Soon" message and proceed button
 *
 * @param show Whether to show the overlay
 * @param onProceed Callback when user clicks "Proceed" button
 * @param viewName The name of the view/feature (displayed above "Coming Soon")
 * @param message Optional custom message
 */
@Composable
fun ComingSoonOverlay(
    show: Boolean,
    onProceed: () -> Unit,
    viewName: String = "",
    title: String = stringResource(R.string.title_coming_soon),
    message: String = stringResource(R.string.msg_coming_soon)
) {
    val primary = primaryColor()
    
    if (show) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .drawBehind {
                        drawRect(
                            color = primary.copy(alpha = 0.3f),
                            topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                            size = size
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(2.dp, primary)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Icon
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    // View Name (if provided)
                    if (viewName.isNotEmpty()) {
                        Text(
                            text = viewName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = primary
                        )
                    }
                    
                    // Title
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )
                    
                    // Message
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Dismiss Button
                    BrutalistButton(
                        text = stringResource(R.string.action_dismiss),
                        onClick = onProceed,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = primary,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Feature Not Supported Overlay - Sleek minimal overlay for unsupported hardware features
 *
 * @param show Whether to show the overlay
 * @param onProceed Callback when user clicks "Proceed" button
 * @param featureName The name of the feature (e.g., "NFC", "BadUSB", "GPS")
 * @param message Optional custom message explaining why it's not supported
 */
@Composable
fun FeatureNotSupportedOverlay(
    show: Boolean,
    onProceed: () -> Unit,
    featureName: String = "",
    message: String? = null
) {
    val primary = primaryColor()
    
    if (show) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .drawBehind {
                        drawRect(
                            color = primary.copy(alpha = 0.2f),
                            topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                            size = size
                        )
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.5.dp, primary.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(36.dp)
                    )
                    
                    if (featureName.isNotEmpty()) {
                        Text(
                            text = featureName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = primary
                        )
                    }
                    
                    Text(
                        text = stringResource(R.string.title_not_supported),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = message ?: stringResource(R.string.msg_not_supported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    TextButton(
                        onClick = onProceed,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = primary
                        )
                    ) {
                        Text(stringResource(R.string.action_proceed))
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
