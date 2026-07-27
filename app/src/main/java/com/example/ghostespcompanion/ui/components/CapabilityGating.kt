package com.example.ghostespcompanion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ghostespcompanion.domain.model.GhostResponse

fun GhostResponse.DeviceInfo?.resolve(feature: GhostResponse.DeviceFeature): GhostResponse.CapabilityResolution =
    this?.resolveFeature(feature) ?: GhostResponse.CapabilityResolution.UNKNOWN

fun GhostResponse.DeviceInfo?.resolve(vararg features: GhostResponse.DeviceFeature): GhostResponse.CapabilityResolution =
    this?.resolveFeature(*features) ?: GhostResponse.CapabilityResolution.UNKNOWN

fun GhostResponse.DeviceInfo?.resolve(feature: GhostResponse.DeviceFeature.Capability): GhostResponse.CapabilityResolution =
    this?.resolveCapability(feature) ?: GhostResponse.CapabilityResolution.UNKNOWN

@Composable
fun CapabilityNotice(
    resolution: GhostResponse.CapabilityResolution,
    featureName: String,
    modifier: Modifier = Modifier
) {
    if (resolution == GhostResponse.CapabilityResolution.SUPPORTED) return
    val unsupported = resolution == GhostResponse.CapabilityResolution.UNSUPPORTED
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (unsupported) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (unsupported) Icons.Default.Block else Icons.Default.HelpOutline, contentDescription = null)
            Text(
                if (unsupported) "$featureName is not supported by this device"
                else "$featureName support is unknown; controls remain available",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
