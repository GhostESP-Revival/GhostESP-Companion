package com.example.ghostespcompanion.data.repository

import android.Manifest
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.ble.BleBridgeDevice
import com.example.ghostespcompanion.data.serial.GhostSerialResponse
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostCommand
import com.example.ghostespcompanion.domain.model.GhostResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Repository for GhostESP device communication
 *
 * Optimized for performance with:
 * - Concurrent data structures for thread-safe access
 * - Efficient list updates with deduplication
 * - Caching for frequently accessed data
 * - Batched state updates
 */
@Singleton
class GhostRepository @Inject constructor(
    private val serialManager: SerialManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Connection state
    val connectionState: StateFlow<SerialManager.ConnectionState> = serialManager.connectionState

    // Raw serial output for terminal
    val rawOutput: SharedFlow<String> = serialManager.rawOutput

    // Optimized state flows with initial capacity hints
    private val _accessPoints = MutableStateFlow<List<GhostResponse.AccessPoint>>(emptyList())
    val accessPoints: StateFlow<List<GhostResponse.AccessPoint>> = _accessPoints.asStateFlow()

    private val _stations = MutableStateFlow<List<GhostResponse.Station>>(emptyList())
    val stations: StateFlow<List<GhostResponse.Station>> = _stations.asStateFlow()

    private val _bleDevices = MutableStateFlow<List<GhostResponse.BleDevice>>(emptyList())
    val bleDevices: StateFlow<List<GhostResponse.BleDevice>> = _bleDevices.asStateFlow()

    private val _flipperDevices = MutableStateFlow<List<GhostResponse.FlipperDevice>>(emptyList())
    val flipperDevices: StateFlow<List<GhostResponse.FlipperDevice>> = _flipperDevices.asStateFlow()

    private val _airTagDevices = MutableStateFlow<List<GhostResponse.AirTagDevice>>(emptyList())
    val airTagDevices: StateFlow<List<GhostResponse.AirTagDevice>> = _airTagDevices.asStateFlow()
    
    private val _gattDevices = MutableStateFlow<List<GhostResponse.GattDevice>>(emptyList())
    val gattDevices: StateFlow<List<GhostResponse.GattDevice>> = _gattDevices.asStateFlow()
    
    private val _gattServices = MutableStateFlow<List<GhostResponse.GattService>>(emptyList())
    val gattServices: StateFlow<List<GhostResponse.GattService>> = _gattServices.asStateFlow()

    private val _nfcTags = MutableStateFlow<List<GhostResponse.NfcTag>>(emptyList())
    val nfcTags: StateFlow<List<GhostResponse.NfcTag>> = _nfcTags.asStateFlow()

    private val _sdEntries = MutableStateFlow<List<GhostResponse.SdEntry>>(emptyList())
    val sdEntries: StateFlow<List<GhostResponse.SdEntry>> = _sdEntries.asStateFlow()

    private val _aerialDevices = MutableStateFlow<List<GhostResponse.AerialDevice>>(emptyList())
    val aerialDevices: StateFlow<List<GhostResponse.AerialDevice>> = _aerialDevices.asStateFlow()

    private val _portalCredentials = MutableStateFlow<List<GhostResponse.PortalCredentials>>(emptyList())
    val portalCredentials: StateFlow<List<GhostResponse.PortalCredentials>> = _portalCredentials.asStateFlow()

    private val _irRemotes = MutableStateFlow<List<GhostResponse.IrRemote>>(emptyList())
    val irRemotes: StateFlow<List<GhostResponse.IrRemote>> = _irRemotes.asStateFlow()

    private val _irButtons = MutableStateFlow<List<GhostResponse.IrButton>>(emptyList())
    val irButtons: StateFlow<List<GhostResponse.IrButton>> = _irButtons.asStateFlow()

    private val _currentIrRemote = MutableStateFlow<GhostResponse.IrRemote?>(null)
    val currentIrRemote: StateFlow<GhostResponse.IrRemote?> = _currentIrRemote.asStateFlow()

    private val _irLearnedSignal = MutableStateFlow<GhostResponse.IrLearned?>(null)
    val irLearnedSignal: StateFlow<GhostResponse.IrLearned?> = _irLearnedSignal.asStateFlow()

    private val _irLearnSavedPath = MutableStateFlow<String?>(null)
    val irLearnSavedPath: StateFlow<String?> = _irLearnSavedPath.asStateFlow()

    private val _irLearnStatus = MutableStateFlow<String?>(null)
    val irLearnStatus: StateFlow<String?> = _irLearnStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _settings = MutableStateFlow<Map<String, String>>(emptyMap())
    val settings: StateFlow<Map<String, String>> = _settings.asStateFlow()

    private val _deviceInfo = MutableStateFlow<GhostResponse.DeviceInfo?>(null)
    val deviceInfo: StateFlow<GhostResponse.DeviceInfo?> = _deviceInfo.asStateFlow()

    // Debug state: raw chipinfo text received (regardless of parse success) + parse outcome
    private val _chipInfoRaw = MutableStateFlow<String?>(null)
    val chipInfoRaw: StateFlow<String?> = _chipInfoRaw.asStateFlow()

    private val _chipInfoParseStatus = MutableStateFlow<String?>(null)
    val chipInfoParseStatus: StateFlow<String?> = _chipInfoParseStatus.asStateFlow()

    // Serial-manager level debug log (flush/timer events) forwarded through the repository
    val chipInfoDebugLog: StateFlow<List<String>> = serialManager.chipInfoDebugLog
    
    val usbDebugLog: StateFlow<List<String>> = serialManager.usbDebugLog

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // File transfer state
    private val _transferProgress = MutableStateFlow<FileTransferProgress>(FileTransferProgress.Idle)
    val transferProgress: StateFlow<FileTransferProgress> = _transferProgress.asStateFlow()
    
    // Tracking state
    private val _trackData = MutableStateFlow<GhostResponse.TrackData?>(null)
    val trackData: StateFlow<GhostResponse.TrackData?> = _trackData.asStateFlow()

    private val _flipperTrackData = MutableStateFlow<GhostResponse.FlipperTrackData?>(null)
    val flipperTrackData: StateFlow<GhostResponse.FlipperTrackData?> = _flipperTrackData.asStateFlow()

    private val _trackHeader = MutableStateFlow<GhostResponse.TrackHeader?>(null)
    val trackHeader: StateFlow<GhostResponse.TrackHeader?> = _trackHeader.asStateFlow()
    
    // GPS and Wardriving state
    private val _gpsPosition = MutableStateFlow<GhostResponse.GpsPosition?>(null)
    val gpsPosition: StateFlow<GhostResponse.GpsPosition?> = _gpsPosition.asStateFlow()
    
    private val _wardriveStats = MutableStateFlow<GhostResponse.WardriveStats?>(null)
    val wardriveStats: StateFlow<GhostResponse.WardriveStats?> = _wardriveStats.asStateFlow()
    
    private val _isWardriving = MutableStateFlow(false)
    val isWardriving: StateFlow<Boolean> = _isWardriving.asStateFlow()
    
    private val _isBleWardriving = MutableStateFlow(false)
    val isBleWardriving: StateFlow<Boolean> = _isBleWardriving.asStateFlow()
    
    private val _isGpsTracking = MutableStateFlow(false)
    val isGpsTracking: StateFlow<Boolean> = _isGpsTracking.asStateFlow()
    
    // Handshake capture state - using SharedFlow to emit each handshake event
    private val _handshakeEvents = MutableSharedFlow<GhostResponse.Handshake>(replay = 0, extraBufferCapacity = 16)
    val handshakeEvents: SharedFlow<GhostResponse.Handshake> = _handshakeEvents.asSharedFlow()
    
    // PCAP file path
    private val _pcapFile = MutableStateFlow<String?>(null)
    val pcapFile: StateFlow<String?> = _pcapFile.asStateFlow()
    
    // WiFi connection state - tracks which network the device is connected to
    private val _wifiConnection = MutableStateFlow<GhostResponse.WifiConnection?>(null)
    val wifiConnection: StateFlow<GhostResponse.WifiConnection?> = _wifiConnection.asStateFlow()
    
    // WiFi status from wifistatus command - detailed connection info
    private val _wifiStatus = MutableStateFlow<GhostResponse.WifiStatus?>(null)
    val wifiStatus: StateFlow<GhostResponse.WifiStatus?> = _wifiStatus.asStateFlow()
    
    // Track the SSID we're attempting to connect to
    private var pendingConnectionSsid: String? = null

    // Response buffer for multi-line SD responses
    private val sdResponseBuffer = StringBuilder()

    // Cache for deduplication - using ConcurrentHashMap for thread safety
    private val apCache = ConcurrentHashMap<Int, GhostResponse.AccessPoint>()
    private val stationCache = ConcurrentHashMap<String, GhostResponse.Station>()
    private val bleCache = ConcurrentHashMap<String, GhostResponse.BleDevice>()
    private val flipperCache = ConcurrentHashMap<String, GhostResponse.FlipperDevice>()
    private val airTagCache = ConcurrentHashMap<String, GhostResponse.AirTagDevice>()
    private val gattCache = ConcurrentHashMap<String, GhostResponse.GattDevice>()
    private val aerialCache = ConcurrentHashMap<String, GhostResponse.AerialDevice>()

    private var scanJob: Job? = null
    private var currentCommand: GhostCommand? = null

    // Response collection job
    private var responseJob: Job? = null

    init {
        // Listen to serial responses and parse them
        // Process on IO dispatcher to avoid blocking UI thread
        // StateFlow is thread-safe and can be updated from any thread
        responseJob = scope.launch(Dispatchers.IO) {
            serialManager.responses.collect { response ->
                // Process directly on IO dispatcher - StateFlow is thread-safe
                parseResponse(response)
            }
        }

        // Clear cached data on unexpected disconnection (e.g. device unplugged).
        // The explicit disconnect()/forceDisconnect() paths already call clearAllData()
        // themselves, but an error-induced or spontaneous disconnect only sets the
        // connection state without clearing data. We track whether we were previously
        // connected so we don't clear on the initial DISCONNECTED state at startup.
        scope.launch(Dispatchers.IO) {
            var wasConnected = false
            serialManager.connectionState.collect { state ->
                when (state) {
                    SerialManager.ConnectionState.CONNECTED -> wasConnected = true
                    SerialManager.ConnectionState.DISCONNECTED,
                    SerialManager.ConnectionState.ERROR -> {
                        if (wasConnected) {
                            wasConnected = false
                            clearAllData()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Get available USB devices
     */
    fun getAvailableDevices(): List<UsbDevice> = serialManager.getAvailableDevices()
    
    fun getAllUsbDevices(): List<UsbDevice> = serialManager.getAllUsbDevices()
    
    fun logUsbDebug() = serialManager.logAllUsbDevices()

    val availableBleDevices: StateFlow<List<BleBridgeDevice>> = serialManager.bleDevices

    fun startBleBridgeScan() = serialManager.startBleScan()

    fun stopBleBridgeScan() = serialManager.stopBleScan()

    val isBleScanning: StateFlow<Boolean> = serialManager.isBleScanning

    fun isBluetoothEnabled(): Boolean = serialManager.isBluetoothEnabled()

    fun isBluetoothSupported(): Boolean = serialManager.isBluetoothSupported()

    /**
     * Connect to a specific device
     */
    suspend fun connect(device: UsbDevice): Boolean = serialManager.connect(device)

    suspend fun connect(device: UsbDevice, baudRate: Int): Boolean = serialManager.connect(device, baudRate)

    suspend fun connectBle(device: BleBridgeDevice): Boolean = serialManager.connectBle(device)

    /**
     * Connect with automatic baud rate detection
     */
    suspend fun connectWithAutoBaud(device: UsbDevice): Boolean = serialManager.connectWithAutoBaud(device)

    val detectedBaudRate = serialManager.detectedBaudRate

    /**
     * Connect to first available device
     */
    suspend fun connectFirstAvailable(): Boolean = serialManager.connectFirstAvailable()

    /**
     * Disconnect from device and clear all cached data
     */
    suspend fun disconnect() {
        serialManager.disconnect()
        clearAllData()
    }

    /**
     * Force disconnect - use when normal disconnect hangs
     * Also clears all cached data
     */
    fun forceDisconnect() {
        serialManager.forceDisconnect()
        clearAllData()
    }

    /**
     * Check if connected
     */
    fun isConnected(): Boolean = serialManager.isConnected()

    /**
     * Send a command.
     * If the command starts a new long-running operation, a universal Stop is sent first
     * to ensure the firmware is not busy before the new operation begins.
     */
    suspend fun sendCommand(command: GhostCommand): Boolean {
        if (command.requiresStopFirst) {
            serialManager.sendCommand(GhostCommand.Stop.commandString)
            delay(200)
        }
        currentCommand = command
        return serialManager.sendCommand(command.commandString)
    }

    /**
     * Send raw command string
     */
    suspend fun sendRaw(command: String): Boolean = serialManager.sendCommand(command)

    // ==================== WiFi Commands ====================

    /**
     * Scan for WiFi access points
     */
    suspend fun scanWifi(duration: Int? = null, live: Boolean = false) {
        clearAccessPoints()
        sendCommand(GhostCommand.ScanAp(duration, live))
    }

    /**
     * Scan for WiFi stations
     */
    suspend fun scanSta() {
        sendCommand(GhostCommand.ScanSta)
    }

    /**
     * Stop WiFi scan
     */
    suspend fun stopWifiScan() {
        sendCommand(GhostCommand.ScanAp(stop = true))
    }

    /**
     * Get access point list
     */
    suspend fun listAccessPoints() {
        sendCommand(GhostCommand.ListResults(GhostCommand.ListMode.ACCESSPoints))
    }

    /**
     * Get station list
     */
    suspend fun listStations() {
        sendCommand(GhostCommand.ListResults(GhostCommand.ListMode.STATIONS))
    }

    /**
     * Select an access point (supports comma-separated indices for multi-select)
     */
    suspend fun selectAp(indices: String) {
        sendCommand(GhostCommand.Select(GhostCommand.SelectTarget.ACCESS_POINT, indices))
    }

    /**
     * Select a station (supports comma-separated indices for multi-select)
     */
    suspend fun selectStation(indices: String) {
        sendCommand(GhostCommand.Select(GhostCommand.SelectTarget.STATION, indices))
    }

    /**
     * Connect to WiFi
     */
    suspend fun connectWifi(ssid: String, password: String? = null) {
        pendingConnectionSsid = ssid
        sendCommand(GhostCommand.Connect(ssid, password))
    }
    
    /**
     * Get WiFi status
     */
    suspend fun getWifiStatus() {
        sendCommand(GhostCommand.WifiStatus)
    }

    /**
     * Run deauth attack
     */
    suspend fun startDeauth() {
        sendCommand(GhostCommand.AttackDeauth())
    }

    /**
     * Stop deauth attack
     */
    suspend fun stopDeauth() {
        sendCommand(GhostCommand.StopDeauth)
    }

    /**
     * Start beacon spam
     */
    suspend fun startBeaconSpam(mode: GhostCommand.BeaconSpamMode = GhostCommand.BeaconSpamMode.RANDOM) {
        sendCommand(GhostCommand.BeaconSpam(mode))
    }

    /**
     * Stop beacon spam
     */
    suspend fun stopBeaconSpam() {
        sendCommand(GhostCommand.StopSpam)
    }

    /**
     * Start karma attack
     */
    suspend fun startKarma(ssids: List<String>? = null) {
        sendCommand(GhostCommand.KarmaStart(ssids))
    }

    /**
     * Stop karma attack
     */
    suspend fun stopKarma() {
        sendCommand(GhostCommand.KarmaStop)
    }

    /**
     * Track selected AP
     */
    suspend fun trackAp() {
        sendCommand(GhostCommand.TrackAp)
    }

    /**
     * Track selected station
     */
    suspend fun trackSta() {
        sendCommand(GhostCommand.TrackSta)
    }
    
    /**
     * Start EAPOL capture (handshake capture)
     */
    suspend fun startEapolCapture(channel: Int? = null) {
        sendCommand(GhostCommand.Capture(GhostCommand.CaptureMode.EAPOL, channel))
    }

    // ==================== BLE Commands ====================

    /**
     * Scan for BLE devices
     */
    suspend fun scanBle(
        mode: GhostCommand.BleScanMode,
        stop: Boolean = false
    ) {
        if (!stop) {
            // Clear the appropriate device list based on scan mode
            when (mode) {
                GhostCommand.BleScanMode.FLIPPER -> clearFlipperDevices()
                GhostCommand.BleScanMode.AIR_TAG -> clearAirTagDevices()
                else -> clearBleDevices()
            }
        }
        sendCommand(GhostCommand.BleScan(mode, stop))
    }

    /**
     * Stop BLE scan
     */
    suspend fun stopBleScan() {
        sendCommand(GhostCommand.BleScanStop)
    }

    /**
     * Start BLE spam
     */
    suspend fun startBleSpam(mode: GhostCommand.BleSpamMode? = null) {
        sendCommand(GhostCommand.BleSpam(mode))
    }

    /**
     * Stop BLE spam
     */
    suspend fun stopBleSpam() {
        sendCommand(GhostCommand.BleSpam(GhostCommand.BleSpamMode.STOP))
    }

    /**
     * List Flipper devices
     */
    suspend fun listFlippers() {
        sendCommand(GhostCommand.ListFlippers)
    }

    /**
     * List AirTags
     */
    suspend fun listAirTags() {
        sendCommand(GhostCommand.ListAirTags)
    }

    /**
     * Spoof AirTag
     */
    suspend fun spoofAirTag(start: Boolean) {
        sendCommand(GhostCommand.SpoofAirTag(start))
    }

    /**
     * List GATT devices
     */
    suspend fun listGatt() {
        sendCommand(GhostCommand.ListGatt)
    }

    /**
     * Enumerate GATT services
     */
    suspend fun enumGatt() {
        _gattServices.value = emptyList()
        sendCommand(GhostCommand.EnumGatt)
    }
    
    suspend fun selectGatt(indices: String) {
        sendCommand(GhostCommand.Select(GhostCommand.SelectTarget.GATT, indices))
        // Give the firmware time to process the selection before the next command
        delay(300)
    }

    suspend fun trackGatt() {
        sendCommand(GhostCommand.TrackGatt)
    }

    suspend fun trackFlipper(index: Int) {
        _flipperTrackData.value = null
        sendCommand(GhostCommand.TrackFlipper(index))
    }

    fun clearFlipperTrackData() {
        _flipperTrackData.value = null
    }

    fun clearGattDevices() {
        gattCache.clear()
        _gattDevices.value = emptyList()
        _gattServices.value = emptyList()
    }

    // ==================== NFC Commands ====================

    /**
     * Scan for NFC tags using Chameleon
     */
    suspend fun scanNfc(timeout: Int = 60) {
        clearNfcTags()
        sendCommand(GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.Scan(timeout)))
    }

    /**
     * Stop NFC Scan
     */
    suspend fun stopNfcScan() {
        sendCommand(GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.ScanStop))
    }

    // ==================== IR Commands ====================

    /**
     * List IR remotes
     */
    suspend fun listIrRemotes(path: String? = null) {
        _irRemotes.value = emptyList()  // Clear previous results
        sendCommand(GhostCommand.Ir(GhostCommand.IrSubcommand.List(path)))
    }

    /**
     * Send IR signal
     */
    suspend fun sendIr(remote: String, buttonIndex: Int? = null) {
        sendCommand(GhostCommand.Ir(GhostCommand.IrSubcommand.Send(remote, buttonIndex)))
    }

    /**
     * Learn IR signal
     */
    suspend fun learnIr(path: String? = null) {
        sendCommand(GhostCommand.Ir(GhostCommand.IrSubcommand.Learn(path)))
    }

    /**
     * Start IR dazzler
     */
    suspend fun startIrDazzler() {
        sendCommand(GhostCommand.Ir(GhostCommand.IrSubcommand.Dazzler(false)))
    }

    /**
     * Stop IR dazzler
     */
    suspend fun stopIrDazzler() {
        sendCommand(GhostCommand.Ir(GhostCommand.IrSubcommand.Dazzler(true)))
    }

    /**
     * Show IR remote buttons (ir show command)
     */
    suspend fun showIrRemote(remoteIndex: Int) {
        _irButtons.value = emptyList()  // Clear previous buttons
        sendCommand(GhostCommand.Ir(GhostCommand.IrSubcommand.Show(remoteIndex.toString())))
    }

    /**
     * Set the current IR remote being viewed
     */
    fun setCurrentIrRemote(remote: GhostResponse.IrRemote) {
        _currentIrRemote.value = remote
    }

    /**
     * Clear IR buttons
     */
    fun clearIrButtons() {
        _irButtons.value = emptyList()
    }

    /**
     * Clear IR learn state
     */
    fun clearIrLearnState() {
        _irLearnedSignal.value = null
        _irLearnSavedPath.value = null
        _irLearnStatus.value = null
    }

    // ==================== BadUSB Commands ====================

    /**
     * List BadUSB scripts
     */
    suspend fun listBadUsbScripts() {
        sendCommand(GhostCommand.BadUsbList)
    }

    /**
     * Run BadUSB script
     */
    suspend fun runBadUsbScript(filename: String) {
        sendCommand(GhostCommand.BadUsbRun(filename))
    }

    /**
     * Stop BadUSB script
     */
    suspend fun stopBadUsb() {
        sendCommand(GhostCommand.BadUsbStop)
    }

    // ==================== GPS Commands ====================

    /**
     * Get GPS info
     */
    suspend fun getGpsInfo() {
        _isGpsTracking.value = true
        sendCommand(GhostCommand.GpsInfo(false))
    }

    /**
     * Stop GPS info
     */
    suspend fun stopGpsInfo() {
        _isGpsTracking.value = false
        sendCommand(GhostCommand.GpsInfo(true))
    }

    /**
     * Start wardriving
     */
    suspend fun startWardrive() {
        _isWardriving.value = true
        _wardriveStats.value = null
        sendCommand(GhostCommand.StartWardrive(false))
    }

    /**
     * Stop wardriving
     */
    suspend fun stopWardrive() {
        _isWardriving.value = false
        sendCommand(GhostCommand.StartWardrive(true))
    }

    /**
     * Start BLE wardriving
     */
    suspend fun startBleWardrive() {
        _isBleWardriving.value = true
        _wardriveStats.value = null
        sendCommand(GhostCommand.BleWardrive(false))
    }

    /**
     * Stop BLE wardriving
     */
    suspend fun stopBleWardrive() {
        _isBleWardriving.value = false
        sendCommand(GhostCommand.BleWardrive(true))
    }

    // ==================== SD Card Commands ====================

    /**
     * Get SD card status
     */
    suspend fun getSdStatus() {
        sendCommand(GhostCommand.SdStatus)
    }

    /**
     * List SD files
     */
    suspend fun listSdFiles(path: String? = null) {
        clearSdEntries()
        _isLoading.value = true
        sendCommand(GhostCommand.SdList(path))
    }

    /**
     * Get file size
     */
    suspend fun getSdFileSize(path: String) {
        sendCommand(GhostCommand.SdSize(path))
    }

    /**
     * Read file chunk
     */
    suspend fun readSdFile(path: String, offset: Int, length: Int) {
        sendCommand(GhostCommand.SdRead(path, offset, length))
    }

    /**
     * Write file (creates new or overwrites)
     */
    suspend fun writeSdFile(path: String, base64Data: String) {
        sendCommand(GhostCommand.SdWrite(path, base64Data))
    }

    /**
     * Append to file
     */
    suspend fun appendSdFile(path: String, base64Data: String) {
        sendCommand(GhostCommand.SdAppend(path, base64Data))
    }

    /**
     * Create directory
     */
    suspend fun createSdDirectory(path: String) {
        sendCommand(GhostCommand.SdMkdir(path))
    }

    /**
     * Delete file or directory
     */
    suspend fun deleteSdEntry(path: String) {
        sendCommand(GhostCommand.SdRm(path))
    }

    /**
     * Download a file from the SD card and save it to the device's Downloads folder.
     * Uses SerialManager's binary mode for proper handling of binary files.
     * Firmware sends RAW BINARY data (not base64) - see commandline.c:5031
     */
    suspend fun downloadSdFile(context: Context, filePath: String, fileName: String) {
        _transferProgress.value = FileTransferProgress.Downloading(fileName, 0, 0, 0)
        try {
            // Step 1: get file size (text response)
            val sizeResponse = awaitResponse(
                command = GhostCommand.SdSize(filePath),
                terminator = { it.contains("SD:SIZE:") || it.contains("SD:ERR") },
                timeoutMs = 10_000
            ) ?: throw Exception("Timeout waiting for file size")

            val sizeMatch = Regex("SD:SIZE:(\\d+)").find(sizeResponse)
                ?: throw Exception("Could not parse file size")
            val fileSize = sizeMatch.groupValues[1].toLong()
            if (fileSize == 0L) throw Exception("File is empty or size is 0")

            // Step 2: read in chunks using binary mode
            val chunkSize = 4096
            var offset = 0L
            val allBytes = ByteArrayOutputStream((fileSize * 1.1).toInt())

            while (offset < fileSize) {
                val length = minOf(chunkSize.toLong(), fileSize - offset).toInt()
                val chunk = awaitBinaryChunk(
                    command = GhostCommand.SdRead(filePath, offset.toInt(), length),
                    timeoutMs = 30_000
                ) ?: throw Exception("Timeout reading chunk at offset $offset")

                allBytes.write(chunk)
                offset += chunk.size // Use actual chunk size, not requested length
                val pct = if (fileSize > 0) ((offset * 100) / fileSize).toInt() else 0
                _transferProgress.value = FileTransferProgress.Downloading(fileName, offset, fileSize, pct)
            }

            // Step 3: save to Downloads and show notification
            val bytes = allBytes.toByteArray()
            val uri = saveToDownloads(context, fileName, bytes)
            showDownloadNotification(context, fileName, uri, bytes.size)

            _transferProgress.value = FileTransferProgress.Complete(fileName, true)
            // Reset to idle after a brief moment so the UI clears
            scope.launch {
                delay(2000)
                if (_transferProgress.value is FileTransferProgress.Complete) {
                    _transferProgress.value = FileTransferProgress.Idle
                }
            }
        } catch (e: CancellationException) {
            _transferProgress.value = FileTransferProgress.Cancelled
            throw e
        } catch (e: Exception) {
            _transferProgress.value = FileTransferProgress.Complete(fileName, false, e.message)
        }
    }

    suspend fun checkSdCard() {
        clearSdEntries()
        sendCommand(GhostCommand.SdList("/mnt/ghostesp"))
    }

    /**
     * Send a command and wait for a binary chunk from SerialManager.
     * SerialManager automatically switches to binary mode when it sees SD:READ:LENGTH:
     * and emits the raw bytes when it detects the terminator.
     * Uses Channel.receiveCatching() for reliable delivery - no race conditions.
     */
    private suspend fun awaitBinaryChunk(
        command: GhostCommand,
        timeoutMs: Long
    ): ByteArray? = withTimeoutOrNull(timeoutMs) {
        sendCommand(command)
        // Channel.receiveCatching() will suspend until data arrives
        serialManager.binaryChunks.first()
    }

    /**
     * Send a command and collect rawOutput lines until [terminator] returns true,
     * or until [timeoutMs] elapses. Returns the concatenated lines, or null on timeout.
     */
    private suspend fun awaitResponse(
        command: GhostCommand,
        terminator: (String) -> Boolean,
        timeoutMs: Long
    ): String? = withTimeoutOrNull(timeoutMs) {
        val buffer = StringBuilder()
        // Subscribe BEFORE sending to avoid missing fast responses
        val job = scope.launch {
            serialManager.rawOutput.collect { line ->
                buffer.appendLine(line)
            }
        }
        try {
            sendCommand(command)
            // Poll the buffer until terminator is satisfied
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (terminator(buffer.toString())) break
                delay(50)
            }
            if (terminator(buffer.toString())) buffer.toString() else null
        } finally {
            job.cancel()
        }
    }

    /**
     * Save [bytes] to the Downloads folder using MediaStore (Android 10+)
     * or legacy Environment path (Android 9 and below).
     * Returns the content Uri for the saved file.
     */
    private fun saveToDownloads(context: Context, fileName: String, bytes: ByteArray): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, getMimeType(fileName))
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("Could not create Downloads entry")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw Exception("Could not open output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            file.writeBytes(bytes)
            Uri.fromFile(file)
        }
    }

    /**
     * Get MIME type based on file extension
     */
    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "pcap" -> "application/vnd.tcpdump.pcap"
            "json" -> "application/json"
            "txt", "log" -> "text/plain"
            "csv" -> "text/csv"
            "html" -> "text/html"
            "bin" -> "application/octet-stream"
            "ir" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    /**
     * Show a notification when download completes with option to open the file
     */
    private fun showDownloadNotification(context: Context, fileName: String, uri: Uri?, fileSize: Int) {
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w("GhostRepository", "Notification permission not granted, showing toast instead")
                showToast(context, "Downloaded $fileName (${formatFileSize(fileSize.toLong())})")
                return
            }
        }

        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                // Android 10+: Use the content Uri directly
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, getMimeType(fileName))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else if (uri != null) {
                // Android 9 and below: Use FileProvider
                val filePath = uri.path ?: run {
                    // Fallback: Open Downloads app
                    showFallbackNotification(context, fileName, fileSize)
                    return
                }
                Intent(Intent.ACTION_VIEW).apply {
                    val fileUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        File(filePath)
                    )
                    setDataAndType(fileUri, getMimeType(fileName))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                // Fallback: Open Downloads app
                Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            }

            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, "ghostesp_downloads")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Download Complete")
                .setContentText("$fileName (${formatFileSize(fileSize.toLong())}) - Tap to open")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context)
                .notify(fileName.hashCode(), notification)
            
            Log.d("GhostRepository", "Download notification shown for $fileName")
        } catch (e: SecurityException) {
            Log.e("GhostRepository", "SecurityException showing notification", e)
            showToast(context, "Downloaded $fileName (${formatFileSize(fileSize.toLong())})")
        } catch (e: Exception) {
            Log.e("GhostRepository", "Exception showing notification", e)
            showToast(context, "Downloaded $fileName (${formatFileSize(fileSize.toLong())})")
        }
    }

    private fun showToast(context: Context, message: String) {
        try {
            val mainHandler = android.os.Handler(context.mainLooper)
            mainHandler.post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("GhostRepository", "Failed to show toast", e)
        }
    }

    /**
     * Format file size for display
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Fallback notification that opens the Downloads app
     */
    private fun showFallbackNotification(context: Context, fileName: String, fileSize: Int) {
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                showToast(context, "Downloaded $fileName (${formatFileSize(fileSize.toLong())})")
                return
            }
        }

        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, "ghostesp_downloads")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Download Complete")
                .setContentText("$fileName (${formatFileSize(fileSize.toLong())}) - Tap to open Downloads")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context)
                .notify(fileName.hashCode(), notification)
        } catch (e: SecurityException) {
            showToast(context, "Downloaded $fileName (${formatFileSize(fileSize.toLong())})")
        }
    }

    // ==================== Aerial Commands ====================

    /**
     * Start aerial scan
     */
    suspend fun startAerialScan(duration: Int = 30) {
        clearAerialDevices()
        sendCommand(GhostCommand.AerialScan(duration))
    }

    /**
     * Stop aerial scan
     */
    suspend fun stopAerialScan() {
        sendCommand(GhostCommand.AerialScan(stop = true))
    }

    /**
     * List aerial devices
     */
    suspend fun listAerialDevices() {
        sendCommand(GhostCommand.AerialList)
    }

    /**
     * Track aerial device
     */
    suspend fun trackAerialDevice(indexOrMac: String) {
        sendCommand(GhostCommand.AerialTrack(indexOrMac))
    }

    /**
     * Spoof aerial device
     */
    suspend fun spoofAerialDevice(
        deviceId: String = "GHOST-TEST",
        lat: Double = 37.7749,
        lon: Double = -122.4194,
        alt: Float = 100.0f
    ) {
        sendCommand(GhostCommand.AerialSpoof(deviceId, lat, lon, alt))
    }

    /**
     * Stop aerial spoofing
     */
    suspend fun stopAerialSpoof() {
        sendCommand(GhostCommand.AerialSpoofStop)
    }

    // ==================== Portal Commands ====================

    /**
     * Start evil portal
     */
    suspend fun startPortal(path: String, ssid: String, password: String? = null) {
        clearPortalCredentials()
        sendCommand(GhostCommand.StartPortal(path, ssid, password))
    }

    /**
     * Stop evil portal
     */
    suspend fun stopPortal() {
        sendCommand(GhostCommand.StopPortal)
    }

    /**
     * List available portals
     */
    suspend fun listPortals() {
        sendCommand(GhostCommand.ListPortals)
    }

    // ==================== Settings Commands ====================

    /**
     * Get chip info
     */
    suspend fun getChipInfo() {
        sendCommand(GhostCommand.ChipInfo)
    }

    /**
     * Identify device
     */
    suspend fun identify() {
        sendCommand(GhostCommand.Identify)
    }

    /**
     * Stop all operations
     */
    suspend fun stopAll() {
        sendCommand(GhostCommand.Stop)
    }

    /**
     * Reboot device
     */
    suspend fun reboot() {
        sendCommand(GhostCommand.Reboot)
    }

    /**
     * List settings
     */
    suspend fun listSettings() {
        sendCommand(GhostCommand.SettingsList)
    }

    /**
     * Get setting value
     */
    suspend fun getSetting(key: String) {
        sendCommand(GhostCommand.SettingsGet(key))
    }

    /**
     * Set setting value
     */
    suspend fun setSetting(key: String, value: String) {
        sendCommand(GhostCommand.SettingsSet(key, value))
    }

    // ==================== Response Parsing ====================

    private var parseResponseCount = 0
    private var parseResponseSlowCount = 0

    private fun parseResponse(response: GhostSerialResponse) {
        val startNanos = System.nanoTime()
        
        when (response.type) {
            GhostSerialResponse.ResponseType.ACCESS_POINT -> {
                GhostResponse.AccessPoint.parse(response.raw)?.let { ap ->
                    // Use cache for deduplication
                    apCache[ap.index] = ap
                    _accessPoints.value = apCache.values.sortedBy { it.index }
                }
            }
            GhostSerialResponse.ResponseType.BLE_DEVICE -> {
                GhostResponse.BleDevice.parse(response.raw)?.let { device ->
                    // Use unique ID for caching since MAC may be null
                    val cacheKey = device.getUniqueId()
                    bleCache[cacheKey] = device
                    _bleDevices.value = bleCache.values.sortedByDescending { it.rssi }
                }
            }
            GhostSerialResponse.ResponseType.FLIPPER_DEVICE -> {
                GhostResponse.FlipperDevice.parse(response.raw)?.let { device ->
                    flipperCache[device.mac] = device
                    _flipperDevices.value = flipperCache.values.sortedByDescending { it.rssi }
                    _statusMessage.value = "Flipper found: ${device.name ?: device.mac}"
                }
            }
            GhostSerialResponse.ResponseType.AIRTAG_DEVICE -> {
                GhostResponse.AirTagDevice.parse(response.raw)?.let { device ->
                    airTagCache[device.mac] = device
                    _airTagDevices.value = airTagCache.values.sortedByDescending { it.rssi }
                    _statusMessage.value = "AirTag found: ${device.mac}"
                }
            }
            GhostSerialResponse.ResponseType.GATT_DEVICE -> {
                GhostResponse.GattDevice.parse(response.raw)?.let { gattDevice ->
                    gattCache[gattDevice.mac] = gattDevice
                    _gattDevices.value = gattCache.values.sortedByDescending { it.rssi }
                    _statusMessage.value = "GATT device found: ${gattDevice.name ?: gattDevice.mac}"
                }
            }
            GhostSerialResponse.ResponseType.GATT_SERVICE -> {
                GhostResponse.GattService.parse(response.raw)?.let { service ->
                    _gattServices.update { current ->
                        if (current.any { it.uuid == service.uuid && it.startHandle == service.startHandle }) current
                        else current + service
                    }
                }
            }
            GhostSerialResponse.ResponseType.STATION -> {
                GhostResponse.Station.parse(response.raw)?.let { station ->
                    // Use cache for deduplication
                    stationCache[station.mac] = station
                    _stations.value = stationCache.values.sortedBy { it.index }
                    _statusMessage.value = "Station: ${station.mac}"
                }
            }
            GhostSerialResponse.ResponseType.NFC_TAG -> {
                GhostResponse.NfcTag.parse(response.raw)?.let { tag ->
                    _nfcTags.update { current ->
                        if (current.any { it.uid == tag.uid }) current else current + tag
                    }
                }
            }
            GhostSerialResponse.ResponseType.SD_ENTRY -> {
                // Check for listing completion: SD:OK:listed N entries
                if (response.raw.startsWith("SD:OK:listed") || response.raw.startsWith("SD:OK:tree")) {
                    _isLoading.value = false
                } else if (response.raw.startsWith("SD:ERR")) {
                    // Error during listing
                    _isLoading.value = false
                    _statusMessage.value = "SD Error: ${response.raw.removePrefix("SD:ERR:")}"
                } else {
                    // Try to parse as file/directory entry
                    GhostResponse.SdEntry.parse(response.raw)?.let { entry ->
                        _sdEntries.update { current -> current + entry }
                    }
                }
            }
            GhostSerialResponse.ResponseType.AERIAL_DEVICE -> {
                GhostResponse.AerialDevice.parse(response.raw)?.let { device ->
                    aerialCache[device.mac] = device
                    _aerialDevices.value = aerialCache.values.sortedBy { it.index }
                }
            }
            GhostSerialResponse.ResponseType.PORTAL_CREDS -> {
                GhostResponse.PortalCredentials.parse(response.raw)?.let { creds ->
                    _portalCredentials.update { current -> current + creds }
                    _statusMessage.value = "Captured: ${creds.username}"
                }
            }
            GhostSerialResponse.ResponseType.IR_LEARNED -> {
                GhostResponse.IrLearned.parse(response.raw)?.let { ir ->
                    _irLearnedSignal.value = ir
                    val msg = if (ir.protocol == "RAW") {
                        "IR Learned: RAW signal (${ir.rawSamples} samples)"
                    } else {
                        "IR Learned: ${ir.protocol} A:${ir.address} C:${ir.command}"
                    }
                    _statusMessage.value = msg
                }
            }
            GhostSerialResponse.ResponseType.IR_LEARN_SAVED -> {
                GhostResponse.IrLearnSaved.parse(response.raw)?.let { saved ->
                    _irLearnSavedPath.value = saved.path
                    _statusMessage.value = "IR signal saved to: ${saved.path}"
                }
            }
            GhostSerialResponse.ResponseType.IR_LEARN_STATUS -> {
                GhostResponse.IrLearnStatus.parse(response.raw)?.let { status ->
                    _irLearnStatus.value = status.status
                    _statusMessage.value = status.message
                }
            }
            GhostSerialResponse.ResponseType.IR_DAZZLER -> {
                GhostResponse.IrDazzlerStatus.parse(response.raw)?.let { dazzler ->
                    _statusMessage.value = "IR Dazzler: ${dazzler.status}"
                }
            }
            GhostSerialResponse.ResponseType.IR_REMOTE -> {
                GhostResponse.IrRemote.parse(response.raw)?.let { remote ->
                    _irRemotes.update { current ->
                        // Avoid duplicates by index
                        if (current.any { it.index == remote.index }) current
                        else current + remote
                    }
                }
            }
            GhostSerialResponse.ResponseType.IR_BUTTON -> {
                GhostResponse.IrButton.parse(response.raw)?.let { button ->
                    _irButtons.update { current ->
                        // Avoid duplicates by index
                        if (current.any { it.index == button.index }) current
                        else current + button
                    }
                }
            }
            GhostSerialResponse.ResponseType.ERROR -> {
                GhostResponse.Error.parse(response.raw)?.let { error ->
                    _statusMessage.value = "Error: ${error.message}"
                }
            }
            GhostSerialResponse.ResponseType.SUCCESS -> {
                GhostResponse.Success.parse(response.raw)?.let { success ->
                    _statusMessage.value = success.message
                }
            }
            GhostSerialResponse.ResponseType.GHOSTESP_OK -> {
                _statusMessage.value = "Device identified: GhostESP"
            }
            GhostSerialResponse.ResponseType.SETTING_VALUE -> {
                GhostResponse.SettingValue.parse(response.raw)?.let { setting ->
                    _settings.update { current -> current + (setting.key to setting.value) }
                }
            }
            GhostSerialResponse.ResponseType.DEVICE_INFO -> {
                _chipInfoRaw.value = response.raw
                val info = GhostResponse.DeviceInfo.parse(response.raw)
                if (info != null) {
                    _deviceInfo.value = info
                    _statusMessage.value = "Device: ${info.model}"
                    _chipInfoParseStatus.value = "OK — model=${info.model}, features=${info.enabledFeatures.size}"
                } else {
                    // parse() returned null — try to diagnose why
                    val raw = response.raw
                    val reason = when {
                        !raw.contains("Chip Information") -> "missing 'Chip Information'"
                        !raw.contains("Model:") -> "missing 'Model:'"
                        else -> "MODEL_PATTERN did not match (raw starts: '${raw.take(120)}')"
                    }
                    _chipInfoParseStatus.value = "FAILED: $reason"
                    Log.e("GhostRepo", "DeviceInfo.parse() returned null — $reason")
                }
            }
            GhostSerialResponse.ResponseType.TRACK_DATA -> {
                GhostResponse.TrackData.parse(response.raw)?.let { trackData ->
                    _trackData.value = trackData
                }
            }
            GhostSerialResponse.ResponseType.FLIPPER_TRACK_DATA -> {
                GhostResponse.FlipperTrackData.parse(response.raw)?.let { data ->
                    _flipperTrackData.value = data
                }
            }
            GhostSerialResponse.ResponseType.TRACK_HEADER -> {
                GhostResponse.TrackData.parseHeader(response.raw)?.let { header ->
                    _trackHeader.value = header
                }
            }
            GhostSerialResponse.ResponseType.HANDSHAKE -> {
                GhostResponse.Handshake.parse(response.raw)?.let { handshake ->
                    _handshakeEvents.tryEmit(handshake)
                    _statusMessage.value = "Handshake captured: ${handshake.pairType}"
                }
            }
            GhostSerialResponse.ResponseType.PCAP_FILE -> {
                val pcapMatch = Regex("/[^\\s]+\\.pcap").find(response.raw)
                pcapMatch?.value?.let { path ->
                    _pcapFile.value = path
                    _statusMessage.value = "PCAP saved: $path"
                }
            }
            GhostSerialResponse.ResponseType.WIFI_CONNECTION -> {
                GhostResponse.WifiConnection.parse(response.raw)?.let { connection ->
                    // If we have a pending SSID and just got connected, use that SSID
                    val updatedConnection = if (connection.isConnected && pendingConnectionSsid != null) {
                        connection.copy(ssid = pendingConnectionSsid)
                    } else if (!connection.isConnected) {
                        // Clear pending SSID on disconnect
                        pendingConnectionSsid = null
                        connection
                    } else {
                        connection
                    }
                    _wifiConnection.value = updatedConnection
                    _statusMessage.value = when {
                        updatedConnection.isConnected -> "WiFi Connected: ${updatedConnection.ssid ?: updatedConnection.ip ?: "Unknown"}"
                        updatedConnection.reason != null -> "WiFi Disconnected: ${updatedConnection.reason}"
                        updatedConnection.ssid != null -> "Connecting to ${updatedConnection.ssid}..."
                        else -> response.raw
                    }
                }
            }
            GhostSerialResponse.ResponseType.WIFI_STATUS -> {
                GhostResponse.WifiStatus.parse(response.raw)?.let { status ->
                    _wifiStatus.value = status
                    // Also update wifiConnection based on wifistatus
                    _wifiConnection.value = GhostResponse.WifiConnection(
                        isConnected = status.connected,
                        ssid = status.connectedSsid ?: status.savedSsid
                    )
                    _statusMessage.value = if (status.connected) {
                        "WiFi Connected: ${status.connectedSsid} (RSSI: ${status.connectedRssi})"
                    } else if (status.hasSavedNetwork) {
                        "WiFi Disconnected (Saved: ${status.savedSsid})"
                    } else {
                        "WiFi Disconnected (No saved network)"
                    }
                }
            }
            GhostSerialResponse.ResponseType.GPS_POSITION -> {
                GhostResponse.GpsPosition.parse(response.raw)?.let { position ->
                    _gpsPosition.value = position
                    if (position.fix) {
                        _statusMessage.value = "GPS Fix: ${position.fixType} (${position.satellites} sats)"
                    }
                }
            }
            GhostSerialResponse.ResponseType.WARDDRIVE_STATS -> {
                GhostResponse.WardriveStats.parse(response.raw)?.let { stats ->
                    _wardriveStats.value = stats
                }
            }
            else -> {
                // Check for status messages
                if (!response.raw.startsWith(">") && !response.raw.startsWith("$")) {
                    _statusMessage.value = response.raw
                }
            }
        }
        
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        if (elapsedMs >= 5) {
            parseResponseSlowCount++
            if (parseResponseSlowCount % 50 == 1) {
                Log.w("GhostRepo.PERF", "parseResponse slow: ${elapsedMs}ms type=${response.type} count=$parseResponseSlowCount")
            }
        }
    }

    // ==================== Clear Functions ====================

    /**
     * Clear scan results
     */
    fun clearAccessPoints() {
        apCache.clear()
        _accessPoints.value = emptyList()
    }

    fun clearStations() {
        stationCache.clear()
        _stations.value = emptyList()
        GhostResponse.Station.resetCounter()
    }

    fun clearBleDevices() {
        bleCache.clear()
        _bleDevices.value = emptyList()
    }

    fun clearFlipperDevices() {
        flipperCache.clear()
        _flipperDevices.value = emptyList()
    }

    fun clearAirTagDevices() {
        airTagCache.clear()
        _airTagDevices.value = emptyList()
    }

    fun clearNfcTags() {
        _nfcTags.value = emptyList()
    }

    fun clearSdEntries() {
        _sdEntries.value = emptyList()
    }

    fun clearAerialDevices() {
        aerialCache.clear()
        _aerialDevices.value = emptyList()
    }

    fun clearPortalCredentials() {
        _portalCredentials.value = emptyList()
    }
    
    fun clearPcapFile() {
        _pcapFile.value = null
    }

    fun clearSettings() {
        _settings.value = emptyMap()
    }
    
    fun clearGpsData() {
        _gpsPosition.value = null
        _isGpsTracking.value = false
    }
    
    fun clearWardriveData() {
        _wardriveStats.value = null
        _isWardriving.value = false
        _isBleWardriving.value = false
    }
    
    /**
     * Clear all cached data - call on disconnect
     */
    fun clearAllData() {
        // Clear all scan results
        clearAccessPoints()
        clearStations()
        clearBleDevices()
        clearFlipperDevices()
        clearAirTagDevices()
        clearNfcTags()
        clearAerialDevices()
        clearPortalCredentials()
        
        // Clear SD entries
        clearSdEntries()
        
        // Clear IR data
        _irRemotes.value = emptyList()
        _irButtons.value = emptyList()
        _currentIrRemote.value = null
        clearIrLearnState()
        
        // Clear device info
        _deviceInfo.value = null
        
        // Clear GATT data
        clearGattDevices()

        // Clear tracking data
        _trackData.value = null
        _trackHeader.value = null
        _flipperTrackData.value = null
        
        // Clear GPS and wardriving data
        clearGpsData()
        clearWardriveData()
        
        // Clear PCAP file
        clearPcapFile()
        
        // Clear settings
        clearSettings()
        
        // Clear WiFi connection
        _wifiConnection.value = null
        _wifiStatus.value = null
        pendingConnectionSsid = null
        
        // Clear status message
        _statusMessage.value = null
    }

    /**
     * Clean up
     */
    fun destroy() {
        responseJob?.cancel()
        scanJob?.cancel()
        scope.cancel()
        serialManager.destroy()
    }
}

/**
 * File transfer progress state
 */
sealed class FileTransferProgress {
    data object Idle : FileTransferProgress()
    data class Downloading(
        val fileName: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val percentage: Int
    ) : FileTransferProgress()
    data class Uploading(
        val fileName: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val percentage: Int
    ) : FileTransferProgress()
    data class Complete(val fileName: String, val success: Boolean, val error: String? = null) : FileTransferProgress()
    data object Cancelled : FileTransferProgress()
}
