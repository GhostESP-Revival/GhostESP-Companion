package com.example.ghostespcompanion.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ghostespcompanion.data.LocationHelper
import com.example.ghostespcompanion.data.PhoneLocation
import com.example.ghostespcompanion.data.ble.BleBridgeDevice
import com.example.ghostespcompanion.data.repository.AppSettings
import com.example.ghostespcompanion.data.repository.FileTransferProgress
import com.example.ghostespcompanion.data.repository.GhostRepository
import com.example.ghostespcompanion.data.repository.PhoneWardriveAp
import com.example.ghostespcompanion.data.repository.PhoneWardriveStats
import com.example.ghostespcompanion.data.repository.SavedWardriveCsv
import com.example.ghostespcompanion.data.repository.PreferencesRepository
import com.example.ghostespcompanion.data.repository.SettingsManager
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostCommand
import com.example.ghostespcompanion.domain.model.GhostResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Shared ViewModel for GhostESP Companion
 *
 * Provides access to the GhostRepository and manages connection state
 * across all screens in the app.
 *
 * All serial operations (connect, disconnect, sendCommand) are launched
 * on background dispatchers to keep the UI thread free for animations
 * and rendering.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val ghostRepository: GhostRepository,
    private val settingsManager: SettingsManager,
    private val preferencesRepository: PreferencesRepository,
    val locationHelper: LocationHelper
) : ViewModel() {

    // App settings from DataStore
    val appSettings: StateFlow<AppSettings> = preferencesRepository.appSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    // Connection state
    val connectionState: StateFlow<SerialManager.ConnectionState> = ghostRepository.connectionState
    val connectionTransport: StateFlow<SerialManager.ConnectionTransport> = ghostRepository.connectionTransport

    // Raw serial output for terminal
    val rawOutput: SharedFlow<String> = ghostRepository.rawOutput

    // Terminal buffer - maintains history even when terminal screen is not visible
    // Bounded to prevent memory issues (keeps last 1000 lines)
    private val _terminalLines = MutableStateFlow<List<String>>(emptyList())
    val terminalLines: StateFlow<List<String>> = _terminalLines.asStateFlow()

    companion object {
        private const val MAX_TERMINAL_LINES = 1000
    }

    private var terminalBuildCount = 0
    private var terminalSlowCount = 0

    init {
        // Collect raw output and store in terminal buffer
        // This ensures no data is lost when terminal screen is not visible
        // Build the new list on Default dispatcher to avoid main thread list copies
        viewModelScope.launch(Dispatchers.Default) {
            ghostRepository.rawOutput.collect { line ->
                val startNanos = System.nanoTime()
                _terminalLines.value = buildList {
                    val currentLines = _terminalLines.value
                    // If at capacity, drop oldest line(s) to make room
                    val startIndex = if (currentLines.size >= MAX_TERMINAL_LINES) {
                        currentLines.size - MAX_TERMINAL_LINES + 1
                    } else {
                        0
                    }
                    // Add all lines we want to keep
                    for (i in startIndex until currentLines.size) {
                        add(currentLines[i])
                    }
                    // Add new line
                    add(line)
                }
                terminalBuildCount++
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                if (elapsedMs >= 5) {
                    terminalSlowCount++
                    if (terminalSlowCount % 20 == 1) {
                        android.util.Log.w("MainViewModel.PERF", "terminal build slow: ${elapsedMs}ms lines=${_terminalLines.value.size}")
                    }
                }
            }
        }
        
        // Haptic feedback on connection state changes
        viewModelScope.launch {
            var wasConnected = false
            ghostRepository.connectionState.collect { state ->
                val isConnected = state == SerialManager.ConnectionState.CONNECTED
                val settings = appSettings.value
                
                if (isConnected && !wasConnected) {
                    // Just connected - success haptic
                    settingsManager.performHapticFeedback(settings.hapticFeedback, SettingsManager.HAPTIC_SUCCESS)
                    settingsManager.showNotification("GhostESP Connected", "Device connected successfully", settings.showNotifications)
                    // Check WiFi status to identify connected AP
                    ghostRepository.getWifiStatus()
                } else if (!isConnected && wasConnected && state == SerialManager.ConnectionState.DISCONNECTED) {
                    // Clean disconnect - light haptic
                    settingsManager.performHapticFeedback(settings.hapticFeedback, SettingsManager.HAPTIC_LIGHT)
                } else if (state == SerialManager.ConnectionState.ERROR) {
                    // Error - error haptic
                    settingsManager.performHapticFeedback(settings.hapticFeedback, SettingsManager.HAPTIC_ERROR)
                }
                
                wasConnected = isConnected
            }
        }
    }

    /**
     * Clear terminal history
     */
    fun clearTerminal() {
        _terminalLines.value = emptyList()
    }

    // WiFi
    val accessPoints: StateFlow<List<GhostResponse.AccessPoint>> = ghostRepository.accessPoints
    val stations: StateFlow<List<GhostResponse.Station>> = ghostRepository.stations
    val wifiStatus: StateFlow<String?> = ghostRepository.statusMessage
    val wifiConnection: StateFlow<GhostResponse.WifiConnection?> = ghostRepository.wifiConnection
    
    private val _isWifiScanning = MutableStateFlow(false)
    val isWifiScanning: StateFlow<Boolean> = _isWifiScanning.asStateFlow()

    // BLE
    val bleDevices: StateFlow<List<GhostResponse.BleDevice>> = ghostRepository.bleDevices
    val flipperDevices: StateFlow<List<GhostResponse.FlipperDevice>> = ghostRepository.flipperDevices
    val airTagDevices: StateFlow<List<GhostResponse.AirTagDevice>> = ghostRepository.airTagDevices
    val gattDevices: StateFlow<List<GhostResponse.GattDevice>> = ghostRepository.gattDevices
    val gattServices: StateFlow<List<GhostResponse.GattService>> = ghostRepository.gattServices

    // NFC
    val nfcTags: StateFlow<List<GhostResponse.NfcTag>> = ghostRepository.nfcTags

    // SD Card
    val sdEntries: StateFlow<List<GhostResponse.SdEntry>> = ghostRepository.sdEntries

    // Aerial devices
    val aerialDevices: StateFlow<List<GhostResponse.AerialDevice>> = ghostRepository.aerialDevices

    // Portal credentials
    val portalCredentials: StateFlow<List<GhostResponse.PortalCredentials>> = ghostRepository.portalCredentials

    // IR Remotes
    val irRemotes: StateFlow<List<GhostResponse.IrRemote>> = ghostRepository.irRemotes

    // IR Buttons
    val irButtons: StateFlow<List<GhostResponse.IrButton>> = ghostRepository.irButtons

    // Current IR Remote
    val currentIrRemote: StateFlow<GhostResponse.IrRemote?> = ghostRepository.currentIrRemote

    // BadUSB
    val badUsbScripts: StateFlow<List<String>> = ghostRepository.badUsbScripts

    // IR Learn state
    val irLearnedSignal: StateFlow<GhostResponse.IrLearned?> = ghostRepository.irLearnedSignal
    val irLearnSavedPath: StateFlow<String?> = ghostRepository.irLearnSavedPath
    val irLearnStatus: StateFlow<String?> = ghostRepository.irLearnStatus

    // Settings
    val settings: StateFlow<Map<String, String>> = ghostRepository.settings

    // Status message
    val statusMessage: StateFlow<String?> = ghostRepository.statusMessage

    // Tracking data
    val trackData: StateFlow<GhostResponse.TrackData?> = ghostRepository.trackData
    val flipperTrackData: StateFlow<GhostResponse.FlipperTrackData?> = ghostRepository.flipperTrackData
    val trackHeader: StateFlow<GhostResponse.TrackHeader?> = ghostRepository.trackHeader
    
    // Handshake capture
    val handshakeEvents: SharedFlow<GhostResponse.Handshake> = ghostRepository.handshakeEvents
    val pcapFile: StateFlow<String?> = ghostRepository.pcapFile
    
    // GPS and Wardriving
    val gpsPosition: StateFlow<GhostResponse.GpsPosition?> = ghostRepository.gpsPosition
    val wardriveStats: StateFlow<GhostResponse.WardriveStats?> = ghostRepository.wardriveStats
    val isWardriving: StateFlow<Boolean> = ghostRepository.isWardriving
    val isBleWardriving: StateFlow<Boolean> = ghostRepository.isBleWardriving
    val isGpsTracking: StateFlow<Boolean> = ghostRepository.isGpsTracking
    val isPhoneWardriving: StateFlow<Boolean> = ghostRepository.isPhoneWardriving
    val phoneWardriveStats: StateFlow<PhoneWardriveStats> = ghostRepository.phoneWardriveStats
    val phoneWardriveAps: StateFlow<List<PhoneWardriveAp>> = ghostRepository.phoneWardriveAps

    private val _savedWardriveCsvs = MutableStateFlow<List<SavedWardriveCsv>>(emptyList())
    val savedWardriveCsvs: StateFlow<List<SavedWardriveCsv>> = _savedWardriveCsvs.asStateFlow()
    
    // Loading state
    val isLoading: StateFlow<Boolean> = ghostRepository.isLoading

    // File transfer progress
    val transferProgress: StateFlow<FileTransferProgress> = ghostRepository.transferProgress

    // Device info - exposed from repository
    val deviceInfo: StateFlow<GhostResponse.DeviceInfo?> = ghostRepository.deviceInfo

    // Debug: raw chipinfo text received + parse status (visible even when deviceInfo is null)
    val chipInfoRaw: StateFlow<String?> = ghostRepository.chipInfoRaw
    val chipInfoParseStatus: StateFlow<String?> = ghostRepository.chipInfoParseStatus
    val chipInfoDebugLog: StateFlow<List<String>> = ghostRepository.chipInfoDebugLog
    
    // USB device detection debug log
    val usbDebugLog: StateFlow<List<String>> = ghostRepository.usbDebugLog

    // ==================== Connection ====================

    // USB device lists — updated on IO dispatcher to keep main thread free
    private val _availableUsbDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val availableUsbDevices: StateFlow<List<UsbDevice>> = _availableUsbDevices.asStateFlow()

    private val _allUsbDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val allUsbDevices: StateFlow<List<UsbDevice>> = _allUsbDevices.asStateFlow()

    val availableBleDevices: StateFlow<List<BleBridgeDevice>> = ghostRepository.availableBleDevices
    val isBleScanning: StateFlow<Boolean> = ghostRepository.isBleScanning

    fun isBluetoothEnabled(): Boolean = ghostRepository.isBluetoothEnabled()

    fun isBluetoothSupported(): Boolean = ghostRepository.isBluetoothSupported()

    fun refreshAvailableDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            _availableUsbDevices.value = ghostRepository.getAvailableDevices()
        }
    }

    /** Suspend version — for callers (e.g. LaunchedEffect) that need the result inline. */
    suspend fun fetchAvailableDevices(): List<UsbDevice> = withContext(Dispatchers.IO) {
        val devices = ghostRepository.getAvailableDevices()
        _availableUsbDevices.value = devices
        devices
    }

    fun refreshAllUsbDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            _allUsbDevices.value = ghostRepository.getAllUsbDevices()
        }
    }

    fun logUsbDebug() = ghostRepository.logUsbDebug()

    fun startBleBridgeScan() = ghostRepository.startBleBridgeScan()

    fun stopBleBridgeScan() = ghostRepository.stopBleBridgeScan()

    fun connect(device: UsbDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.connect(device)
        }
    }

    fun connectWithAutoBaud(device: UsbDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.connectWithAutoBaud(device)
        }
    }

    fun connectWithBaud(device: UsbDevice, baudRate: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.connect(device, baudRate)
        }
    }

    fun connectBle(device: BleBridgeDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.connectBle(device)
        }
    }

    val detectedBaudRate: StateFlow<Int?> = ghostRepository.detectedBaudRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun connectFirstAvailable() {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.connectFirstAvailable()
        }
    }

    fun connectSavedDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.connectSavedDevice()
        }
    }

    fun forgetSavedDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.forgetSavedDevice()
        }
    }

    fun openDownloadsFolder(context: Context) {
        val intent = ghostRepository.openDownloadsFolderIntent(context)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "No activity to open Downloads folder: ${e.message}")
        }
    }

    fun openDownloadedFile(context: Context, fileName: String) {
        val intent = ghostRepository.openDownloadedFileIntent(context, fileName) ?: return
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "No activity to open file: ${e.message}")
        }
    }    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.disconnect()
        }
    }

    /**
     * Force disconnect - use when normal disconnect hangs or connection is stuck
     */
    fun forceDisconnect() {
        ghostRepository.forceDisconnect()
    }

    fun isConnected(): Boolean = ghostRepository.isConnected()

    // ==================== WiFi ====================

    fun scanWifi(duration: Int? = null, live: Boolean = false) {
        val scanDuration = duration ?: 5
        viewModelScope.launch(Dispatchers.IO) {
            _isWifiScanning.value = true
            ghostRepository.scanWifi(scanDuration, live)
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay((scanDuration + 1) * 1000L)
            _isWifiScanning.value = false
        }
    }
    
    fun stopWifiScanAndReset() {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.stopWifiScan()
        }
        _isWifiScanning.value = false
    }

    fun scanSta() {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.scanSta()
        }
    }

    fun stopWifiScan() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopWifiScan() }
    }

    fun listAccessPoints() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.listAccessPoints() }
    }

    fun listStations() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.listStations() }
    }

    fun selectAp(indices: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.selectAp(indices) }
    }

    fun selectStation(indices: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.selectStation(indices) }
    }

    fun connectWifi(ssid: String, password: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.connectWifi(ssid, password)
        }
    }

    fun startDeauth() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.startDeauth() }
    }

    fun stopDeauth() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopDeauth() }
    }

    fun startBeaconSpam(mode: GhostCommand.BeaconSpamMode = GhostCommand.BeaconSpamMode.RANDOM) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.startBeaconSpam(mode) }
    }

    fun stopBeaconSpam() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopBeaconSpam() }
    }

    fun startKarma(ssids: List<String>? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.startKarma(ssids)
        }
    }

    fun stopKarma() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopKarma() }
    }

    fun trackAp() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.trackAp() }
    }

    fun trackSta() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.trackSta() }
    }
    
    fun startEapolCapture(channel: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.startEapolCapture(channel) }
    }

    fun startPacketCapture(mode: GhostCommand.CaptureMode, channel: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.startPacketCapture(mode, channel) }
    }

    fun stopPacketCapture() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopPacketCapture() }
    }

    // ==================== BLE ====================

    fun scanBle(mode: GhostCommand.BleScanMode) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.scanBle(mode)
        }
    }

    fun stopBleScan() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopBleScan() }
    }

    fun startBleSpam(mode: GhostCommand.BleSpamMode? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.startBleSpam(mode)
        }
    }

    fun stopBleSpam() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopBleSpam() }
    }

    fun listFlippers() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.listFlippers() }
    }

    fun listAirTags() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.listAirTags() }
    }

    fun spoofAirTag(start: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.spoofAirTag(start)
        }
    }

    fun listGatt() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.listGatt() }
    }

    fun enumGatt() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.enumGatt() }
    }

    fun selectGatt(indices: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.selectGatt(indices) }
    }

    fun selectAndEnumGatt(indices: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.selectGatt(indices)
            ghostRepository.enumGatt()
        }
    }

    fun selectAndTrackGatt(indices: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.selectGatt(indices)
            ghostRepository.trackGatt()
        }
    }

    fun trackGatt() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.trackGatt() }
    }

    fun trackFlipper(index: Int) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.trackFlipper(index) }
    }
    
    fun clearGattDevices() {
        ghostRepository.clearGattDevices()
    }

    // ==================== NFC ====================

    fun scanNfc(timeout: Int = 60) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.scanNfc(timeout)
        }
    }

    fun stopNfcScan() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopNfcScan() }
    }

    // ==================== IR ====================

    fun listIrRemotes(path: String? = null) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.listIrRemotes(path) }
    }

    fun sendIr(remote: String, buttonIndex: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.sendIr(remote, buttonIndex) }
    }

    fun learnIr(path: String? = null) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.learnIr(path) }
    }

    fun startIrDazzler() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.startIrDazzler() }
    }

    fun stopIrDazzler() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopIrDazzler() }
    }

    fun showIrRemote(remoteIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.showIrRemote(remoteIndex) }
    }

    fun setCurrentIrRemote(remote: GhostResponse.IrRemote) = ghostRepository.setCurrentIrRemote(remote)

    fun clearIrButtons() = ghostRepository.clearIrButtons()

    fun clearIrLearnState() = ghostRepository.clearIrLearnState()

    fun sendIrButton(remoteIndex: Int, buttonIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.sendIr(remoteIndex.toString(), buttonIndex) }
    }

    // ==================== BadUSB ====================

    fun listBadUsbScripts() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.listBadUsbScripts() }
    }

    fun runBadUsbScript(filename: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.runBadUsbScript(filename) }
    }

    fun stopBadUsb() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopBadUsb() }
    }

    fun startBadUsbKeyboard() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.startBadUsbKeyboard() }
    }

    fun stopBadUsbKeyboard() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopBadUsbKeyboard() }
    }

    fun typeBadUsbText(text: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.typeBadUsbText(text) }
    }

    fun startBadUsbJiggler() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.startBadUsbJiggler() }
    }

    fun stopBadUsbJiggler() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopBadUsbJiggler() }
    }

    // ==================== GPS ====================

    fun getGpsInfo() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.getGpsInfo() }
    }

    fun stopGpsInfo() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopGpsInfo() }
    }

    fun startWardrive() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.startWardrive() }
    }

    fun stopWardrive() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopWardrive() }
    }

    fun startBleWardrive() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.startBleWardrive() }
    }

    fun stopBleWardrive() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopBleWardrive() }
    }

    fun updatePhoneLocation(location: PhoneLocation) {
        ghostRepository.updatePhoneLocation(location)
    }

    fun startPhoneWardrive(includeBle: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.startPhoneWardrive(includeBle) }
    }

    fun stopPhoneWardrive(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.stopPhoneWardrive(context)
            refreshSavedWardriveCsvs(context)
        }
    }

    fun refreshSavedWardriveCsvs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _savedWardriveCsvs.value = ghostRepository.listSavedWardriveCsvs(context)
        }
    }

    fun deleteSavedWardriveCsv(context: Context, uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.deleteSavedWardriveCsv(context, uriString)
            refreshSavedWardriveCsvs(context)
        }
    }

    fun shareSavedWardriveCsv(context: Context, uriString: String) {
        ghostRepository.getSavedWardriveCsvShareIntent(context, uriString)?.let { intent ->
            try {
                context.startActivity(Intent.createChooser(intent, "Share CSV"))
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "No activity to share CSV: ${e.message}")
            }
        }
    }

    // ==================== SD Card ====================

    fun getSdStatus() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.getSdStatus() }
    }

    fun listSdFiles(path: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.listSdFiles(path)
        }
    }

    fun getSdFileSize(path: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.getSdFileSize(path) }
    }

    fun readSdFile(path: String, offset: Int, length: Int) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.readSdFile(path, offset, length) }
    }

    fun writeSdFile(path: String, base64Data: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.writeSdFile(path, base64Data) }
    }

    fun appendSdFile(path: String, base64Data: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.appendSdFile(path, base64Data) }
    }

    fun createSdDirectory(path: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.createSdDirectory(path) }
    }

    fun deleteSdEntry(path: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.deleteSdEntry(path) }
    }

    fun downloadSdFile(context: Context, filePath: String, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.downloadSdFile(context, filePath, fileName)
        }
    }

    fun checkSdCard() {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.checkSdCard()
        }
    }

    // ==================== Aerial ====================

    fun startAerialScan(duration: Int = 30) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.startAerialScan(duration)
        }
    }

    fun stopAerialScan() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopAerialScan() }
    }

    fun listAerialDevices() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.listAerialDevices() }
    }

    fun trackAerialDevice(indexOrMac: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.trackAerialDevice(indexOrMac) }
    }

    fun spoofAerialDevice(
        deviceId: String = "GHOST-TEST",
        lat: Double = 37.7749,
        lon: Double = -122.4194,
        alt: Float = 100.0f
    ) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.spoofAerialDevice(deviceId, lat, lon, alt) }
    }

    fun stopAerialSpoof() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopAerialSpoof() }
    }

    // ==================== Portal ====================

    fun startPortal(path: String, ssid: String, password: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            ghostRepository.startPortal(path, ssid, password)
        }
    }

    fun stopPortal() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopPortal() }
    }

    fun listPortals() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.listPortals() }
    }

    // ==================== Settings ====================

    fun listSettings() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.listSettings() }
    }

    fun getSetting(key: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.getSetting(key) }
    }

    fun setSetting(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.setSetting(key, value) }
    }

    // ==================== System ====================

    fun sendRawCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.sendRaw(command) }
    }

    fun sendRaw(command: String) {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.sendRaw(command) }
    }

    fun getChipInfo() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.getChipInfo() }
    }

    fun identify() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.identify() }
    }

    fun stopAll() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.stopAll() }
    }

    fun reboot() {
        viewModelScope.launch(Dispatchers.IO) { ghostRepository.reboot() }
    }

    // ==================== Clear Functions ====================

    fun clearAccessPoints() = ghostRepository.clearAccessPoints()

    fun clearStations() = ghostRepository.clearStations()

    fun clearBleDevices() = ghostRepository.clearBleDevices()

    fun clearFlipperDevices() = ghostRepository.clearFlipperDevices()

    fun clearAirTagDevices() = ghostRepository.clearAirTagDevices()

    fun clearNfcTags() = ghostRepository.clearNfcTags()

    fun clearSdEntries() = ghostRepository.clearSdEntries()

    fun clearAerialDevices() = ghostRepository.clearAerialDevices()

    fun clearPortalCredentials() = ghostRepository.clearPortalCredentials()

    // ==================== App Settings ====================

    /**
     * Perform haptic feedback if enabled in settings
     */
    fun performHapticFeedback() {
        val settings = appSettings.value
        settingsManager.performHapticFeedback(settings.hapticFeedback)
    }

    /**
     * Show notification if enabled in settings
     */
    fun showNotification(title: String, message: String) {
        val settings = appSettings.value
        settingsManager.showNotification(title, message, settings.showNotifications)
    }

    /**
     * Update dark mode setting
     */
    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDarkMode(enabled)
        }
    }

    /**
     * Update haptic feedback setting
     */
    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHapticFeedback(enabled)
        }
    }

    /**
     * Update auto connect setting
     */
    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoConnect(enabled)
        }
    }

    /**
     * Update show notifications setting
     */
    fun setShowNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowNotifications(enabled)
        }
    }

    /**
     * Update privacy mode setting
     */
    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setPrivacyMode(enabled)
        }
    }

    // Note: Don't call ghostRepository.destroy() here since it's a singleton
    // shared across all screens. The repository will be cleaned up when the
    // application process ends.
}
