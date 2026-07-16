package com.example.ghostespcompanion.ui.screens.more

import android.Manifest
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.PhoneLocation
import com.example.ghostespcompanion.data.repository.PhoneWardriveAp
import com.example.ghostespcompanion.data.repository.SavedWardriveCsv
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostResponse
import com.example.ghostespcompanion.ui.screens.MainScreen
import com.example.ghostespcompanion.ui.components.*
import com.example.ghostespcompanion.ui.theme.*
import com.example.ghostespcompanion.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var showOverlay by remember { mutableStateOf(true) }
    var showCsvExplorer by remember { mutableStateOf(false) }
    var showSdCardWarning by remember { mutableStateOf(false) }
    var usePhoneGps by remember { mutableStateOf(false) }
    var phoneWardriveIsBle by remember { mutableStateOf(false) }
    var sdCardCheckDone by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    var phoneLocation by remember { mutableStateOf<PhoneLocation?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }
    
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                viewModel.locationHelper.getLocationUpdates().collect { location ->
                    phoneLocation = location
                    viewModel.updatePhoneLocation(location)
                }
            } catch (_: SecurityException) {
                // Permission revoked at runtime — silently ignore
            }
        }
    }
    
    val connectionState by viewModel.connectionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isConnected = connectionState == SerialManager.ConnectionState.CONNECTED
    
    val sdEntries by viewModel.sdEntries.collectAsState()
    
    LaunchedEffect(isConnected, sdCardCheckDone) {
        if (isConnected && !sdCardCheckDone) {
            sdCardCheckDone = true
            viewModel.checkSdCard()
        }
    }
    
    LaunchedEffect(statusMessage, sdCardCheckDone) {
        if (sdCardCheckDone && statusMessage?.contains("SD Error") == true) {
            showSdCardWarning = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSavedWardriveCsvs(context)
    }
    
    val gpsPosition by viewModel.gpsPosition.collectAsState()
    val wardriveStats by viewModel.wardriveStats.collectAsState()
    val isWardriving by viewModel.isWardriving.collectAsState()
    val isBleWardriving by viewModel.isBleWardriving.collectAsState()
    val isGpsTracking by viewModel.isGpsTracking.collectAsState()
    val isPhoneWardriving by viewModel.isPhoneWardriving.collectAsState()
    val phoneWardriveStats by viewModel.phoneWardriveStats.collectAsState()
    val phoneWardriveAps by viewModel.phoneWardriveAps.collectAsState()
    val savedWardriveCsvs by viewModel.savedWardriveCsvs.collectAsState()
    
    val appSettings by viewModel.appSettings.collectAsState()
    val privacyMode = appSettings.privacyMode
    
    val isGpsSupported = deviceInfo?.hasFeature(GhostResponse.DeviceFeature.GPS) ?: true
    val hasDeviceInfo = deviceInfo != null
    
    val mapView = remember { MapView(context) }
    val phoneWardriveApOverlay = remember { PhoneWardriveApOverlay() }
    
    DisposableEffect(Unit) {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
        mapView.onResume()
        
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }
    
    LaunchedEffect(phoneLocation, gpsPosition, phoneWardriveAps) {
        withContext(Dispatchers.Main) {
            try {
                mapView.overlays.clear()

                phoneWardriveApOverlay.setAps(phoneWardriveAps)
                if (phoneWardriveAps.isNotEmpty()) {
                    mapView.overlays.add(phoneWardriveApOverlay)
                }

                // Create tinted marker icons
                val defaultIcon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
                val phoneIcon = defaultIcon?.constantState?.newDrawable()?.mutate()?.also {
                    DrawableCompat.setTint(it, android.graphics.Color.rgb(33, 150, 243)) // Blue
                }
                val deviceIcon = defaultIcon?.constantState?.newDrawable()?.mutate()?.also {
                    DrawableCompat.setTint(it, android.graphics.Color.rgb(244, 67, 54)) // Red
                }

                phoneLocation?.let { phone ->
                    val phoneMarker = Marker(mapView).apply {
                        position = GeoPoint(phone.latitude, phone.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = context.getString(R.string.label_phone_gps)
                        snippet = context.getString(R.string.label_current_location)
                        icon = phoneIcon
                    }
                    mapView.overlays.add(phoneMarker)

                    if (gpsPosition?.fix == true) {
                        mapView.controller.setCenter(GeoPoint(gpsPosition!!.latitude, gpsPosition!!.longitude))
                    } else {
                        mapView.controller.setCenter(GeoPoint(phone.latitude, phone.longitude))
                    }
                }

                gpsPosition?.let { gps ->
                    if (gps.fix == true) {
                        val ghostMarker = Marker(mapView).apply {
                            position = GeoPoint(gps.latitude, gps.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = context.getString(R.string.label_ghostesp_gps)
                            snippet = context.getString(R.string.label_device_location)
                            icon = deviceIcon
                        }
                        mapView.overlays.add(ghostMarker)
                    }
                }

                mapView.invalidate()
            } catch (e: Exception) {
                // Map view may be detached or in invalid state
            }
        }
    }

    MainScreen(
        onBack = onBack,
        title = stringResource(R.string.title_gps_wardrive)
    ) { paddingValues ->
        // Single Box with the map view always in the composition tree.
        // Removing AndroidView from composition triggers MapView.onDetachedFromWindow()
        // which calls mTileProvider.detach(), killing tile loading. Keeping it permanently
        // present and overlaying the list view on top avoids this entirely.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Map view — always present, never detached from the window.
            AndroidView(
                factory = { mapView },
                update = { mv ->
                    mv.onResume()
                    mv.invalidate()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (privacyMode) Modifier.blur(25.dp) else Modifier)
            )

            // Privacy overlay
            if (privacyMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = stringResource(R.string.label_privacy_mode),
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(R.string.label_privacy_mode),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.msg_map_hidden),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Feature-not-supported overlay (shown in both modes)
            if (hasDeviceInfo && !isGpsSupported) {
                FeatureNotSupportedOverlay(
                    show = showOverlay,
                    onProceed = { showOverlay = false },
                    featureName = "GPS",
                    message = stringResource(R.string.msg_gps_not_supported)
                )
            }

            // SD card required warning overlay
            if (showSdCardWarning) {
                FeatureNotSupportedOverlay(
                    show = true,
                    onProceed = { showSdCardWarning = false },
                    featureName = stringResource(R.string.title_sd_required),
                    message = stringResource(R.string.msg_sd_required_wardrive)
                )
            }

            // Bottom stats/controls card
            Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (isConnected) Icons.Default.Link else Icons.Default.LinkOff,
                                    contentDescription = null,
                                    tint = if (isConnected) Success else Error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isConnected) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isConnected) Success else Error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Phone GPS mode toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (usePhoneGps) primaryColor().copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = if (usePhoneGps) primaryColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.label_phone_gps_mode),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (usePhoneGps) primaryColor() else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (usePhoneGps) stringResource(R.string.msg_phone_gps_hint) else stringResource(R.string.msg_device_gps_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = usePhoneGps,
                                onCheckedChange = { usePhoneGps = it },
                                enabled = !isWardriving && !isBleWardriving && !isPhoneWardriving,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = primaryColor(),
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BrutalistButton(
                                text = if (isWardriving || (isPhoneWardriving && !phoneWardriveIsBle)) stringResource(R.string.action_stop_wardrive) else stringResource(R.string.action_start_wardrive),
                                onClick = {
                                    if (isConnected) {
                                        if (isWardriving) {
                                            viewModel.stopWardrive()
                                        } else if (isPhoneWardriving && !phoneWardriveIsBle) {
                                            viewModel.stopPhoneWardrive(context)
                                        } else {
                                            if (usePhoneGps) {
                                                phoneWardriveIsBle = false
                                                viewModel.startPhoneWardrive(includeBle = false)
                                            } else {
                                                viewModel.startWardrive()
                                            }
                                        }
                                    }
                                },
                                containerColor = if (isWardriving || (isPhoneWardriving && !phoneWardriveIsBle)) errorColor() else successColor(),
                                enabled = isConnected && !isBleWardriving && (!isPhoneWardriving || !phoneWardriveIsBle) && (!usePhoneGps || hasLocationPermission),
                                modifier = Modifier.weight(1f),
                                leadingIcon = {
                                    Icon(
                                        if (isWardriving || (isPhoneWardriving && !phoneWardriveIsBle)) Icons.Default.Stop else Icons.Default.TravelExplore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            BrutalistButton(
                                text = if (isBleWardriving || (isPhoneWardriving && phoneWardriveIsBle)) stringResource(R.string.action_stop_ble_wd) else stringResource(R.string.action_start_ble_wd),
                                onClick = {
                                    if (isConnected) {
                                        if (isBleWardriving) {
                                            viewModel.stopBleWardrive()
                                        } else if (isPhoneWardriving && phoneWardriveIsBle) {
                                            viewModel.stopPhoneWardrive(context)
                                        } else {
                                            if (usePhoneGps) {
                                                phoneWardriveIsBle = true
                                                viewModel.startPhoneWardrive(includeBle = true)
                                            } else {
                                                viewModel.startBleWardrive()
                                            }
                                        }
                                    }
                                },
                                containerColor = if (isBleWardriving || (isPhoneWardriving && phoneWardriveIsBle)) errorColor() else primaryColor(),
                                enabled = isConnected && !isWardriving && (!isPhoneWardriving || phoneWardriveIsBle) && (!usePhoneGps || hasLocationPermission),
                                modifier = Modifier.weight(1f),
                                leadingIcon = {
                                    Icon(
                                        if (isBleWardriving || (isPhoneWardriving && phoneWardriveIsBle)) Icons.Default.Stop else Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (privacyMode) stringResource(R.string.label_unit_degrees, "**..**") else if (phoneLocation != null) stringResource(R.string.label_unit_degrees, String.format("%.5f", phoneLocation!!.latitude)) else "--",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (phoneLocation != null) Success else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.label_lat_short),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (privacyMode) stringResource(R.string.label_unit_degrees, "**..**") else if (phoneLocation != null) stringResource(R.string.label_unit_degrees, String.format("%.5f", phoneLocation!!.latitude)) else "--",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (phoneLocation != null) Success else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.label_lon_short),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${wardriveStats?.gpsSatellites ?: gpsPosition?.satellites ?: 0}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if ((wardriveStats?.gpsSatellites ?: 0) > 0 || gpsPosition?.fix == true) primaryColor() else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.label_sats_short),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.label_unit_meters, (gpsPosition?.altitude?.toInt() ?: 0).toString()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (gpsPosition?.fix == true) primaryColor() else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.label_alt_short),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isPhoneWardriving) "${phoneWardriveStats.accessPoints}" else if (isBleWardriving) "${wardriveStats?.bleDevices ?: 0}" else "${wardriveStats?.accessPoints ?: 0}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWardriving || isBleWardriving || isPhoneWardriving) successColor() else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isBleWardriving) "BLE" else stringResource(R.string.label_aps_short),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isWardriving && (wardriveStats?.gpsRejected ?: 0) > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.msg_gps_rejected, wardriveStats?.gpsRejected ?: 0),
                                style = MaterialTheme.typography.labelSmall,
                                color = Warning
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        BrutalistButton(
                            text = stringResource(R.string.label_saved_csvs, savedWardriveCsvs.size),
                            onClick = {
                                viewModel.refreshSavedWardriveCsvs(context)
                                showCsvExplorer = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            borderColor = MaterialTheme.colorScheme.outline,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }

            // CSV Explorer bottom sheet
            if (showCsvExplorer) {
                ModalBottomSheet(
                    onDismissRequest = { showCsvExplorer = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.title_saved_csvs),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = {
                                viewModel.refreshSavedWardriveCsvs(context)
                            }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.action_refresh),
                                    tint = primaryColor()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (savedWardriveCsvs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.FolderOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.msg_no_saved_csvs),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.msg_phone_csvs_hint),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(savedWardriveCsvs, key = { it.uri }) { csv ->
                                    CsvItem(
                                        csv = csv,
                                        onShare = { viewModel.shareSavedWardriveCsv(context, csv.uri) },
                                        onDelete = { viewModel.deleteSavedWardriveCsv(context, csv.uri) }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CoordinateDisplay(label: String, value: Double, unit: String, privacyMode: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (privacyMode) stringResource(R.string.label_unit_degrees, "**..**") + unit else stringResource(R.string.label_unit_degrees, String.format("%.6f", value)) + unit,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun GpsStatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = primaryColor()
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = primaryColor()
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GpsConnectionBanner(
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
                    if (isConnected) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                    contentDescription = null,
                    tint = if (isConnected) Success else Error
                )
                Column {
                    Text(
                        text = if (isConnected) stringResource(R.string.status_connected_device, deviceName) else stringResource(R.string.status_disconnected),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) Success else Error
                    )
                    if (isConnected) {
                        Text(
                            text = stringResource(R.string.label_gps_ready),
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

@Composable
private fun CsvItem(
    csv: SavedWardriveCsv,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = primaryColor(),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = csv.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatFileSize(csv.size)}  \u2022  ${SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(csv.dateAdded))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.action_proceed),
                    tint = primaryColor().copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = errorColor().copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.title_delete_csv)) },
            text = { Text(stringResource(R.string.msg_delete_csv_confirm, csv.fileName)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = errorColor())
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes ${stringResource(R.string.label_bytes_short)}"
        bytes < 1024 * 1024 -> String.format("%.1f ${stringResource(R.string.label_kilobytes_short)}", bytes / 1024.0)
        else -> String.format("%.1f ${stringResource(R.string.label_megabytes_short)}", bytes / (1024.0 * 1024.0))
    }
}

private class PhoneWardriveApOverlay : Overlay() {
    @Volatile private var aps: List<PhoneWardriveAp> = emptyList()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val point = Point()

    fun setAps(newAps: List<PhoneWardriveAp>) {
        aps = newAps
    }

    private fun rssiColor(rssi: Int, isBle: Boolean): Int {
        if (isBle) {
            val t = ((rssi + 100).coerceIn(0, 60)).toFloat() / 60f
            val r = (76f + (0f - 76f) * t).toInt()
            val g = (175f + (200f - 175f) * t).toInt()
            val b = (80f + (80f - 80f) * t).toInt()
            return android.graphics.Color.rgb(r, g, b)
        }
        val t = ((rssi + 100).coerceIn(0, 60)).toFloat() / 60f
        val r = (244f + (33f - 244f) * t).toInt()
        val g = (67f + (150f - 67f) * t).toInt()
        val b = (54f + (243f - 54f) * t).toInt()
        return android.graphics.Color.rgb(r, g, b)
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val snapshot = aps
        if (snapshot.isEmpty()) return

        val projection = mapView.projection
        val canvasWidth = canvas.width.toFloat()
        val canvasHeight = canvas.height.toFloat()
        val zoom = mapView.zoomLevelDouble
        val radius = (4f + (zoom.toFloat() - 10f).coerceAtLeast(0f) * 1.5f).coerceIn(3f, 14f)
        val drawStroke = zoom >= 15.0

        for (ap in snapshot) {
            projection.toPixels(GeoPoint(ap.latitude, ap.longitude), point)

            val px = point.x.toFloat()
            val py = point.y.toFloat()

            if (px < -radius || px > canvasWidth + radius || py < -radius || py > canvasHeight + radius) {
                continue
            }

            val baseColor = rssiColor(ap.rssi, ap.isBle)
            fillPaint.color = android.graphics.Color.argb(180, android.graphics.Color.red(baseColor), android.graphics.Color.green(baseColor), android.graphics.Color.blue(baseColor))
            canvas.drawCircle(px, py, radius, fillPaint)

            if (drawStroke) {
                strokePaint.color = android.graphics.Color.argb(220, android.graphics.Color.red(baseColor), android.graphics.Color.green(baseColor), android.graphics.Color.blue(baseColor))
                canvas.drawCircle(px, py, radius, strokePaint)
            }
        }
    }
}
