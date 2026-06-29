package com.example.ghostespcompanion.ui.screens.wifi

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.utils.PrivacyUtils
import com.example.ghostespcompanion.ui.utils.censorSsid
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Evil Portal Screen - Captive portal management
 * 
 * Allows setting up and managing evil portal attacks
 * for capturing WiFi credentials.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvilPortalScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var isPortalRunning by remember { mutableStateOf(false) }
    var portalSsid by remember { mutableStateOf("FreeWiFi") }
    var portalPassword by remember { mutableStateOf("") }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var selectedHtmlTemplate by remember { mutableStateOf(0) }
    
    val connectionState by viewModel.connectionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val portalCredentials by viewModel.portalCredentials.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val sdEntries by viewModel.sdEntries.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    val privacyMode = appSettings.privacyMode
    
    // Stop portal when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            if (isPortalRunning) {
                viewModel.stopPortal()
            }
        }
    }
    
    val portalFiles = remember(sdEntries) {
        sdEntries
            .filter { !it.isDirectory && it.name.endsWith(".html", ignoreCase = true) }
            .sortedBy { it.name }
    }
    
    LaunchedEffect(isConnected) {
        if (isConnected) {
            viewModel.listSdFiles("/mnt/ghostesp/evil_portal/portals")
        }
    }
    
    // Convert firmware portal credentials to display format
    val capturedCredentials = remember(portalCredentials) {
        portalCredentials.map { cred ->
            CapturedCredential(
                ssid = portalSsid,  // Use the current portal SSID
                username = cred.username,
                password = cred.password,
                timestamp = cred.timestamp
            )
        }
    }
    
    val htmlTemplates = remember(portalFiles) {
        buildList {
            add("Default Portal" to "default")
            portalFiles.forEach { entry ->
                val displayName = entry.name.removeSuffix(".html").removeSuffix(".HTML")
                add(displayName to entry.name)
            }
        }
    }
    
    MainScreen(
        onBack = onBack,
        title = "Evil Portal",
        actions = {
            IconButton(onClick = { showAdvancedOptions = !showAdvancedOptions }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Advanced Options",
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
            WifiConnectionBanner(
                isConnected = isConnected,
                deviceName = "GhostESP",
                onConnect = { viewModel.connectFirstAvailable() }
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Portal Configuration Card
                item {
                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Portal Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor()
                            )
                            
                            // SSID Input
                            OutlinedTextField(
                                value = portalSsid,
                                onValueChange = { portalSsid = it },
                                label = { Text("Portal SSID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryColor(),
                                    unfocusedBorderColor = OutlineDark,
                                    focusedTextColor = OnSurfaceDark,
                                    unfocusedTextColor = OnSurfaceDark
                                )
                            )
                            
                            // Password Input (optional)
                            OutlinedTextField(
                                value = portalPassword,
                                onValueChange = { portalPassword = it },
                                label = { Text("Password (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Primary,
                                    unfocusedBorderColor = OutlineDark,
                                    focusedTextColor = OnSurfaceDark,
                                    unfocusedTextColor = OnSurfaceDark
                                )
                            )
                            
                            // HTML Template Selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "HTML Template",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OnSurfaceVariantDark
                                )
                                IconButton(
                                    onClick = { viewModel.listSdFiles("/mnt/ghostesp/evil_portal/portals") },
                                    enabled = isConnected
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = if (isConnected) OnSurfaceVariantDark else OnSurfaceVariantDark.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            htmlTemplates.forEachIndexed { index, (name, _) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedHtmlTemplate == index,
                                        onClick = { selectedHtmlTemplate = index },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Primary,
                                            unselectedColor = OnSurfaceVariantDark
                                        )
                                    )
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OnSurfaceDark
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Start/Stop Portal Button
                item {
                    BrutalistButton(
                        text = if (isPortalRunning) "Stop Portal" else "Start Evil Portal",
                        onClick = {
                            if (isConnected) {
                                if (isPortalRunning) {
                                    viewModel.stopPortal()
                                    isPortalRunning = false
                                } else {
                                    // Start portal with configuration using proper command
                                    val templatePath = htmlTemplates[selectedHtmlTemplate].second
                                    val password = portalPassword.ifBlank { null }
                                    viewModel.startPortal(
                                        path = templatePath,
                                        ssid = portalSsid,
                                        password = password
                                    )
                                    isPortalRunning = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = if (isPortalRunning) errorColor() else primaryColor(),
                        enabled = isConnected,
                        leadingIcon = {
                            if (isPortalRunning) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                            } else {
                                Icon(painterResource(R.drawable.ic_evil_portal), contentDescription = null)
                            }
                        }
                    )
                }
                
                // Portal Running Status
                if (isPortalRunning) {
                    item {
PortalRunningCard(
                            ssid = portalSsid,
                            credentialCount = capturedCredentials.size,
                            privacyMode = privacyMode
                        )
                    }
                }
                
                // Captured Credentials Section
                if (capturedCredentials.isNotEmpty()) {
                    item {
                        Text(
                            text = "Captured Credentials",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor()
                        )
                    }
                    
                    items(capturedCredentials, key = { it.timestamp }) { credential ->
                        CapturedCredentialCard(
                            credential = credential,
                            privacyMode = privacyMode,
                            onDelete = {
                                // Clear all credentials since firmware doesn't support individual deletion
                                viewModel.clearPortalCredentials()
                            }
                        )
                    }
                }
                
                // Advanced Options
                if (showAdvancedOptions) {
                    item {
                        AdvancedOptionsCard(
                            isConnected = isConnected,
                            viewModel = viewModel,
                            onClearCaptured = { viewModel.clearPortalCredentials() }
                        )
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
                                text = "Evil Portal creates a fake WiFi network that captures credentials when users try to connect. Use responsibly and only on networks you own.",
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
 * Captured credential data class
 */
data class CapturedCredential(
    val ssid: String,
    val username: String?,
    val password: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Animated status card shown while the evil portal is actively running
 */
@Composable
private fun PortalRunningCard(ssid: String, credentialCount: Int, privacyMode: Boolean = false) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Warning.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.08f)),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pulsing live dot
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = Warning,
                    modifier = Modifier
                        .size(12.dp)
                        .alpha(dotAlpha)
                )
                Column {
                    Text(
                        text = "Portal Active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Warning
                    )
Text(
                        text = "Broadcasting as \"${ssid.censorSsid(privacyMode)}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )
                }
            }
            // Credential hit counter
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$credentialCount",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = if (credentialCount > 0) Warning else OnSurfaceVariantDark
                )
                Text(
                    text = if (credentialCount == 1) "hit" else "hits",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariantDark
                )
            }
        }
    }
}

/**
 * Card displaying a captured credential
 */
@Composable
private fun CapturedCredentialCard(
    credential: CapturedCredential,
    privacyMode: Boolean = false,
    onDelete: () -> Unit
) {
    val timeString = remember(credential.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(credential.timestamp))
    }
    BrutalistCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = primaryColor(),
                modifier = Modifier
                    .size(36.dp)
                    .padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (privacyMode) PrivacyUtils.censorSsid(credential.ssid) else credential.ssid,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariantDark
                    )
                }
                if (credential.username != null) {
                    Text(
                        text = "Email: ${if (privacyMode) PrivacyUtils.censorText(credential.username, 2) else credential.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )
                }
                Text(
                    text = "Password: ${if (privacyMode) PrivacyUtils.censorPassword(credential.password) else credential.password}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark
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
}

/**
 * Advanced Options Card with file upload functionality
 */
@Composable
private fun AdvancedOptionsCard(
    isConnected: Boolean,
    viewModel: MainViewModel,
    onClearCaptured: () -> Unit
) {
    val context = LocalContext.current
    var customHtmlPath by remember { mutableStateOf("") }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                // Read the file content
                val inputStream: InputStream? = context.contentResolver.openInputStream(selectedUri)
                inputStream?.use { stream ->
                    val content = stream.bufferedReader().use { it.readText() }
                    
                    // Get filename from URI
                    val fileName = context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex("_display_name")
                        if (cursor.moveToFirst() && nameIndex >= 0) {
                            cursor.getString(nameIndex)
                        } else {
                            "custom_portal.html"
                        }
                    } ?: "custom_portal.html"
                    
                    // Update the path
                    customHtmlPath = "/sdcard/portal/$fileName"
                    
                    // Send the file content to the device
                    // In a real implementation, this would upload the file to the GhostESP device
                    viewModel.sendRaw("portal_upload $fileName")
                    
                    // Show success message
                    uploadStatus = "HTML file '$fileName' uploaded successfully"
                    showSuccessDialog = true
                } ?: run {
                    errorMessage = "Could not read file content"
                    showErrorDialog = true
                }
            } catch (e: Exception) {
                errorMessage = "Error reading file: ${e.message}"
                showErrorDialog = true
            }
        }
    }
    
    BrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = Warning
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Advanced Options",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Warning
            )
            
            // Custom HTML path display
            OutlinedTextField(
                value = customHtmlPath,
                onValueChange = { customHtmlPath = it },
                label = { Text("Custom HTML Path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Warning,
                    unfocusedBorderColor = OutlineDark
                ),
                enabled = false
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrutalistOutlinedButton(
                    text = "Upload HTML",
                    onClick = { filePickerLauncher.launch("text/html") },
                    modifier = Modifier.weight(1f),
                    borderColor = Warning,
                    enabled = isConnected
                )
                
                BrutalistOutlinedButton(
                    text = "Clear Captured",
                    onClick = onClearCaptured,
                    modifier = Modifier.weight(1f),
                    borderColor = Error,
                    textColor = Error
                )
            }
            
            // Upload status
            uploadStatus?.let { status ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Success.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Success,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = Success
                        )
                    }
                }
            }
        }
    }
    
    // Success dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success) },
            title = { Text("Upload Successful") },
            text = { Text(uploadStatus ?: "HTML file uploaded successfully") },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    // Error dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            icon = { Icon(Icons.Default.Error, contentDescription = null, tint = errorColor()) },
            title = { Text("Upload Failed") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}