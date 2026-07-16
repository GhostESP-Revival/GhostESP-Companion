package com.example.ghostespcompanion.ui.screens.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ghostespcompanion.R
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.utils.rememberUrlOpener
import com.example.ghostespcompanion.ui.utils.GhostESPUrls

/**
 * More Menu Screen - Minimalist Neo-Brutalist Design
 * 
 * Clean white accents on deep black background.
 * Professional and modern aesthetic.
 */
@Composable
fun MoreMenuScreen(
    viewModel: com.example.ghostespcompanion.ui.viewmodel.MainViewModel,
    onNavigateToNfc: () -> Unit,
    onNavigateToBadUsb: () -> Unit,
    onNavigateToGps: () -> Unit,
    onNavigateToEthernet: () -> Unit,
    onNavigateToSd: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTerminal: () -> Unit
) {
    val openUrl = rememberUrlOpener()
    
    MainScreen(title = stringResource(R.string.title_more)) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Tools section
            item {
                BrutalistSectionHeader(
                    title = stringResource(R.string.header_tools),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    accentColor = primaryColor()
                )
            }
            
            item {
                MenuItem(
                    icon = Icons.Default.Nfc,
                    title = stringResource(R.string.title_nfc),
                    subtitle = stringResource(R.string.subtitle_nfc),
                    accentColor = tertiaryColor(),
                    onClick = onNavigateToNfc
                )
            }
            
            item {
                MenuItem(
                    icon = Icons.Default.Usb,
                    title = stringResource(R.string.title_bad_usb),
                    subtitle = stringResource(R.string.subtitle_badusb),
                    accentColor = errorColor(),
                    onClick = onNavigateToBadUsb
                )
            }
            
            item {
                MenuItem(
                    icon = Icons.Default.GpsFixed,
                    title = stringResource(R.string.title_gps_wardrive),
                    subtitle = stringResource(R.string.subtitle_gps),
                    accentColor = secondaryColor(),
                    onClick = onNavigateToGps
                )
            }
            
            item {
                MenuItem(
                    icon = Icons.Default.SettingsEthernet,
                    title = stringResource(R.string.title_ethernet),
                    subtitle = stringResource(R.string.subtitle_ethernet),
                    accentColor = primaryColor(),
                    onClick = onNavigateToEthernet
                )
            }
            
            item {
                MenuItem(
                    icon = Icons.Default.SdCard,
                    title = stringResource(R.string.title_sd_manager),
                    subtitle = stringResource(R.string.subtitle_sd),
                    accentColor = tertiaryColor(),
                    onClick = onNavigateToSd
                )
            }
            
            // Debug section
            item {
                BrutalistSectionHeader(
                    title = stringResource(R.string.header_debug),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    accentColor = primaryColor()
                )
            }
            
            item {
                MenuItem(
                    icon = Icons.Default.Terminal,
                    title = stringResource(R.string.title_terminal),
                    subtitle = stringResource(R.string.subtitle_terminal),
                    accentColor = secondaryColor(),
                    onClick = onNavigateToTerminal
                )
            }
            
            // Settings section
            item {
                BrutalistSectionHeader(
                    title = stringResource(R.string.title_settings),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    accentColor = primaryColor()
                )
            }
            
            item {
                MenuItem(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.title_settings),
                    subtitle = stringResource(R.string.subtitle_settings),
                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onNavigateToSettings
                )
            }
            
            // About section
            item {
                BrutalistSectionHeader(
                    title = stringResource(R.string.header_about),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    accentColor = primaryColor()
                )
            }
            
            item {
                AboutCard(
                    onDocsClick = { openUrl(GhostESPUrls.DOCUMENTATION) },
                    onGitHubClick = { openUrl(GhostESPUrls.GITHUB_REPO) }
                )
            }
        }
    }
}

/**
 * Menu item - Minimal style
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color = primaryColor(),
    onClick: () -> Unit
) {
    BrutalistCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        borderColor = MaterialTheme.colorScheme.outline,
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderWidth = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with subtle background
            Surface(
                shape = MaterialTheme.shapes.small,
                color = accentColor.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.action_dismiss),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * About card - Minimal style
 */
@Composable
private fun AboutCard(
    onDocsClick: () -> Unit,
    onGitHubClick: () -> Unit
) {
    // Cache the painter to avoid reloading during scroll
    val iconPainter = painterResource(id = R.drawable.gesp)
    
    BrutalistCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        borderColor = MaterialTheme.colorScheme.outline,
        backgroundColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ghost icon with black background for visibility
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color.Black,
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = iconPainter,
                        contentDescription = stringResource(R.string.desc_app_icon),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.label_version_beta),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.msg_app_desc_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Quick links
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrutalistChip(
                    text = stringResource(R.string.label_github),
                    onClick = onGitHubClick,
                    backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                    borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                    textColor = MaterialTheme.colorScheme.secondary
                )
                BrutalistChip(
                    text = stringResource(R.string.label_docs),
                    onClick = onDocsClick,
                    backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                    borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                    textColor = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
