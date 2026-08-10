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
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.PhoneLocation
import com.example.ghostespcompanion.data.ble.BleBridgeDevice
import com.example.ghostespcompanion.data.serial.BleConnectionFailure
import com.example.ghostespcompanion.data.serial.GhostSerialResponse
import com.example.ghostespcompanion.data.serial.SerialManager
import com.example.ghostespcompanion.domain.model.GhostCommand
import com.example.ghostespcompanion.domain.model.GhostResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

enum class SavedConnectionAttempt {
    NOT_FOUND,
    STARTED,
    FAILED,
    BLUETOOTH_PERMISSION_REQUIRED,
    BLUETOOTH_DISABLED
}

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
    private val serialManager: SerialManager,
    private val preferencesRepository: PreferencesRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Connection state
    val connectionState: StateFlow<SerialManager.ConnectionState> = serialManager.connectionState
    val connectionTransport: StateFlow<SerialManager.ConnectionTransport> = serialManager.connectionTransport
    val lastBleConnectionFailure: StateFlow<BleConnectionFailure> = serialManager.lastBleConnectionFailure

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

    private val _advertiserDevices = MutableStateFlow<List<GhostResponse.AdvertiserDevice>>(emptyList())
    val advertiserDevices: StateFlow<List<GhostResponse.AdvertiserDevice>> = _advertiserDevices.asStateFlow()
    
    private val _gattServices = MutableStateFlow<List<GhostResponse.GattService>>(emptyList())
    val gattServices: StateFlow<List<GhostResponse.GattService>> = _gattServices.asStateFlow()

    private val _ethernetInfo = MutableStateFlow<GhostResponse.EthernetInfo?>(null)
    val ethernetInfo: StateFlow<GhostResponse.EthernetInfo?> = _ethernetInfo.asStateFlow()

    private val _ethernetStats = MutableStateFlow<GhostResponse.EthernetStats?>(null)
    val ethernetStats: StateFlow<GhostResponse.EthernetStats?> = _ethernetStats.asStateFlow()

    private val _arpScanResults = MutableStateFlow<List<GhostResponse.ArpScanResult>>(emptyList())
    val arpScanResults: StateFlow<List<GhostResponse.ArpScanResult>> = _arpScanResults.asStateFlow()

    private val _portScanResults = MutableStateFlow<List<GhostResponse.PortScanResult>>(emptyList())
    val portScanResults: StateFlow<List<GhostResponse.PortScanResult>> = _portScanResults.asStateFlow()

    private val _pingScanResults = MutableStateFlow<List<GhostResponse.PingScanResult>>(emptyList())
    val pingScanResults: StateFlow<List<GhostResponse.PingScanResult>> = _pingScanResults.asStateFlow()

    private val _traceHops = MutableStateFlow<List<GhostResponse.TraceHop>>(emptyList())
    val traceHops: StateFlow<List<GhostResponse.TraceHop>> = _traceHops.asStateFlow()

    private val _pineapDetections = MutableStateFlow<List<GhostResponse.PineapDetection>>(emptyList())
    val pineapDetections: StateFlow<List<GhostResponse.PineapDetection>> = _pineapDetections.asStateFlow()

    private val _flockDetections = MutableStateFlow<List<GhostResponse.FlockDetection>>(emptyList())
    val flockDetections: StateFlow<List<GhostResponse.FlockDetection>> = _flockDetections.asStateFlow()

    private val _flockScanComplete = MutableStateFlow<GhostResponse.FlockScanComplete?>(null)
    val flockScanComplete: StateFlow<GhostResponse.FlockScanComplete?> = _flockScanComplete.asStateFlow()

    private val _netBiosResults = MutableStateFlow<List<GhostResponse.NetBiosResult>>(emptyList())
    val netBiosResults: StateFlow<List<GhostResponse.NetBiosResult>> = _netBiosResults.asStateFlow()

    private val _netBiosScanComplete = MutableStateFlow<GhostResponse.NetBiosScanComplete?>(null)
    val netBiosScanComplete: StateFlow<GhostResponse.NetBiosScanComplete?> = _netBiosScanComplete.asStateFlow()

    private val _httpBannerHits = MutableStateFlow<List<GhostResponse.HttpBannerHit>>(emptyList())
    val httpBannerHits: StateFlow<List<GhostResponse.HttpBannerHit>> = _httpBannerHits.asStateFlow()

    private val _httpBannerSummary = MutableStateFlow<GhostResponse.HttpBannerSummary?>(null)
    val httpBannerSummary: StateFlow<GhostResponse.HttpBannerSummary?> = _httpBannerSummary.asStateFlow()

    private val _snmpHits = MutableStateFlow<List<GhostResponse.SnmpHit>>(emptyList())
    val snmpHits: StateFlow<List<GhostResponse.SnmpHit>> = _snmpHits.asStateFlow()

    private val _snmpSummary = MutableStateFlow<GhostResponse.SnmpSummary?>(null)
    val snmpSummary: StateFlow<GhostResponse.SnmpSummary?> = _snmpSummary.asStateFlow()

    private val _enumHits = MutableStateFlow<List<GhostResponse.EnumHit>>(emptyList())
    val enumHits: StateFlow<List<GhostResponse.EnumHit>> = _enumHits.asStateFlow()

    private val _enumSummary = MutableStateFlow<GhostResponse.EnumSummary?>(null)
    val enumSummary: StateFlow<GhostResponse.EnumSummary?> = _enumSummary.asStateFlow()

    private val _wpa3Compliance = MutableStateFlow<GhostResponse.Wpa3Compliance?>(null)
    val wpa3Compliance: StateFlow<GhostResponse.Wpa3Compliance?> = _wpa3Compliance.asStateFlow()

    private val _wpa3ReportSummary = MutableStateFlow<GhostResponse.Wpa3ReportSummary?>(null)
    val wpa3ReportSummary: StateFlow<GhostResponse.Wpa3ReportSummary?> = _wpa3ReportSummary.asStateFlow()

    private var pendingWpa3ReportApCount: Int = 0

    // Pending-state for per-line scan parsers (ports without an explicit IP in the header, export metrics)
    private var pendingSshBanner: GhostResponse.SshBanner? = null
    private var pendingExportPath: String? = null
    private var pendingEthPoisonKind: GhostResponse.EthPoisonItem.EthPoisonKind? = null

    private val _csaAttackStatus = MutableStateFlow(GhostResponse.CsaAttackStatus(targetCount = 0))
    val csaAttackStatus: StateFlow<GhostResponse.CsaAttackStatus> = _csaAttackStatus.asStateFlow()

    private val _gtkAbuseLog = MutableStateFlow<List<GhostResponse.GtkAbuseStatus>>(emptyList())
    val gtkAbuseLog: StateFlow<List<GhostResponse.GtkAbuseStatus>> = _gtkAbuseLog.asStateFlow()

    private val _probeRequests = MutableStateFlow<List<GhostResponse.ProbeRequest>>(emptyList())
    val probeRequests: StateFlow<List<GhostResponse.ProbeRequest>> = _probeRequests.asStateFlow()

    private val _congestionRows = MutableStateFlow<List<GhostResponse.CongestionRow>>(emptyList())
    val congestionRows: StateFlow<List<GhostResponse.CongestionRow>> = _congestionRows.asStateFlow()

    private val _openPorts = MutableStateFlow<List<GhostResponse.OpenPort>>(emptyList())
    val openPorts: StateFlow<List<GhostResponse.OpenPort>> = _openPorts.asStateFlow()

    private val _portScanHost = MutableStateFlow<String?>(null)
    val portScanHost: StateFlow<String?> = _portScanHost.asStateFlow()

    private val _sshBanners = MutableStateFlow<List<GhostResponse.SshBanner>>(emptyList())
    val sshBanners: StateFlow<List<GhostResponse.SshBanner>> = _sshBanners.asStateFlow()

    private val _sshScanSummary = MutableStateFlow<GhostResponse.SshScanSummary?>(null)
    val sshScanSummary: StateFlow<GhostResponse.SshScanSummary?> = _sshScanSummary.asStateFlow()

    private val _ipLookupDevices = MutableStateFlow<List<GhostResponse.IpLookupDevice>>(emptyList())
    val ipLookupDevices: StateFlow<List<GhostResponse.IpLookupDevice>> = _ipLookupDevices.asStateFlow()

    private val _ipLookupDone = MutableStateFlow<Int?>(null)
    val ipLookupDone: StateFlow<Int?> = _ipLookupDone.asStateFlow()

    private val _scanCompletion = MutableStateFlow<GhostResponse.ScanCompletion?>(null)
    val scanCompletion: StateFlow<GhostResponse.ScanCompletion?> = _scanCompletion.asStateFlow()

    private var pendingIpLookupDevice: GhostResponse.IpLookupDevice? = null

    private val _arpHosts = MutableStateFlow<List<GhostResponse.ArpHostEntry>>(emptyList())
    val arpHosts: StateFlow<List<GhostResponse.ArpHostEntry>> = _arpHosts.asStateFlow()

    private val _arpScanSummary = MutableStateFlow<GhostResponse.ArpScanSummary?>(null)
    val arpScanSummary: StateFlow<GhostResponse.ArpScanSummary?> = _arpScanSummary.asStateFlow()

    private val _sweepPhases = MutableStateFlow<List<GhostResponse.SweepPhase>>(emptyList())
    val sweepPhases: StateFlow<List<GhostResponse.SweepPhase>> = _sweepPhases.asStateFlow()

    private val _sweepSummary = MutableStateFlow<GhostResponse.SweepSummary?>(null)
    val sweepSummary: StateFlow<GhostResponse.SweepSummary?> = _sweepSummary.asStateFlow()

    private val _dhcpStarveStats = MutableStateFlow<GhostResponse.DhcpStarveStats?>(null)
    val dhcpStarveStats: StateFlow<GhostResponse.DhcpStarveStats?> = _dhcpStarveStats.asStateFlow()

    private val _captureFiles = MutableStateFlow<List<GhostResponse.CaptureListEntry>>(emptyList())
    val captureFiles: StateFlow<List<GhostResponse.CaptureListEntry>> = _captureFiles.asStateFlow()

    private val _captureExportResult = MutableStateFlow<GhostResponse.CaptureExportResult?>(null)
    val captureExportResult: StateFlow<GhostResponse.CaptureExportResult?> = _captureExportResult.asStateFlow()

    private val _ethPoisonStatus = MutableStateFlow<GhostResponse.EthPoisonStatus?>(null)
    val ethPoisonStatus: StateFlow<GhostResponse.EthPoisonStatus?> = _ethPoisonStatus.asStateFlow()

    private val _ethPoisonDomains = MutableStateFlow<List<String>>(emptyList())
    val ethPoisonDomains: StateFlow<List<String>> = _ethPoisonDomains.asStateFlow()

    private val _ethPoisonCookies = MutableStateFlow<List<String>>(emptyList())
    val ethPoisonCookies: StateFlow<List<String>> = _ethPoisonCookies.asStateFlow()

    private val _ethPoisonCreds = MutableStateFlow<List<String>>(emptyList())
    val ethPoisonCreds: StateFlow<List<String>> = _ethPoisonCreds.asStateFlow()

    private val _sinkholeStatus = MutableStateFlow<GhostResponse.SinkholeStatus?>(null)
    val sinkholeStatus: StateFlow<GhostResponse.SinkholeStatus?> = _sinkholeStatus.asStateFlow()

    private val _webUiApState = MutableStateFlow<GhostResponse.WebUiApState?>(null)
    val webUiApState: StateFlow<GhostResponse.WebUiApState?> = _webUiApState.asStateFlow()

    private val _webAuthResult = MutableStateFlow<GhostResponse.WebAuthResult?>(null)
    val webAuthResult: StateFlow<GhostResponse.WebAuthResult?> = _webAuthResult.asStateFlow()

    fun clearPineapDetections() { _pineapDetections.value = emptyList() }
    fun clearFlockScan() {
        _flockDetections.value = emptyList()
        _flockScanComplete.value = null
    }
    fun clearNetBiosResults() {
        _netBiosResults.value = emptyList()
        _netBiosScanComplete.value = null
    }
    fun clearHttpBannerResults() {
        _httpBannerHits.value = emptyList()
        _httpBannerSummary.value = null
    }
    fun clearSnmpResults() {
        _snmpHits.value = emptyList()
        _snmpSummary.value = null
    }
    fun clearEnumResults() {
        _enumHits.value = emptyList()
        _enumSummary.value = null
    }
    fun clearWpa3Results() {
        _wpa3Compliance.value = null
        _wpa3ReportSummary.value = null
    }
    fun clearCsaAttackStatus() { _csaAttackStatus.value = GhostResponse.CsaAttackStatus(targetCount = 0) }
    fun clearGtkAbuseLog() { _gtkAbuseLog.value = emptyList() }
    fun clearProbeRequests() { _probeRequests.value = emptyList() }
    fun clearCongestionRows() { _congestionRows.value = emptyList() }
    fun clearOpenPorts() { _openPorts.value = emptyList(); _portScanHost.value = null }
    fun clearSshBanners() { _sshBanners.value = emptyList() }
    fun clearSshScanSummary() { _sshScanSummary.value = null }
    fun clearIpLookup() {
        _ipLookupDevices.value = emptyList()
        _ipLookupDone.value = null
        pendingIpLookupDevice = null
    }
    fun clearScanCompletion() { _scanCompletion.value = null }
    fun clearArpScan() {
        _arpHosts.value = emptyList()
        _arpScanSummary.value = null
    }
    fun clearSweep() {
        _sweepPhases.value = emptyList()
        _sweepSummary.value = null
    }
    fun clearDhcpStarveStats() { _dhcpStarveStats.value = null }
    fun clearCaptureFiles() { _captureFiles.value = emptyList() }
    fun clearCaptureExportResult() { _captureExportResult.value = null }
    fun clearEthPoison() {
        _ethPoisonStatus.value = null
        _ethPoisonDomains.value = emptyList()
        _ethPoisonCookies.value = emptyList()
        _ethPoisonCreds.value = emptyList()
    }
    fun clearSinkholeStatus() { _sinkholeStatus.value = null }

    private val _nfcTags = MutableStateFlow<List<GhostResponse.NfcTag>>(emptyList())
    val nfcTags: StateFlow<List<GhostResponse.NfcTag>> = _nfcTags.asStateFlow()

    private val _nfcBackend = MutableStateFlow<GhostResponse.NfcBackend?>(null)
    val nfcBackend: StateFlow<GhostResponse.NfcBackend?> = _nfcBackend.asStateFlow()

    private val _nfcTaskRunning = MutableStateFlow(false)
    val nfcTaskRunning: StateFlow<Boolean> = _nfcTaskRunning.asStateFlow()

    private val _nfcEmulateStatus = MutableStateFlow<GhostResponse.NfcEmulateStatus?>(null)
    val nfcEmulateStatus: StateFlow<GhostResponse.NfcEmulateStatus?> = _nfcEmulateStatus.asStateFlow()

    private val _nfcSaveResult = MutableStateFlow<GhostResponse.NfcSaveResult?>(null)
    val nfcSaveResult: StateFlow<GhostResponse.NfcSaveResult?> = _nfcSaveResult.asStateFlow()

    private val _nfcHardnestedResult = MutableStateFlow<GhostResponse.NfcHardnestedResult?>(null)
    val nfcHardnestedResult: StateFlow<GhostResponse.NfcHardnestedResult?> = _nfcHardnestedResult.asStateFlow()

    private val _nfcPicopassResult = MutableStateFlow<GhostResponse.NfcPicopassResult?>(null)
    val nfcPicopassResult: StateFlow<GhostResponse.NfcPicopassResult?> = _nfcPicopassResult.asStateFlow()

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

    private val _isPhoneWardriving = MutableStateFlow(false)
    val isPhoneWardriving: StateFlow<Boolean> = _isPhoneWardriving.asStateFlow()

    private val _phoneWardriveStats = MutableStateFlow(PhoneWardriveStats())
    val phoneWardriveStats: StateFlow<PhoneWardriveStats> = _phoneWardriveStats.asStateFlow()

    private val _phoneWardriveAps = MutableStateFlow<List<PhoneWardriveAp>>(emptyList())
    val phoneWardriveAps: StateFlow<List<PhoneWardriveAp>> = _phoneWardriveAps.asStateFlow()

    @Volatile private var latestPhoneLocation: PhoneLocation? = null
    private val phoneWardriveRows = ConcurrentHashMap<String, PhoneWardriveRow>()
    private val phoneWardriveApsLock = Any()
    private var phoneWardriveObservations = 0
    private var phoneWardriveLocatedObservations = 0
    private var phoneWardriveStartedAt = 0L
    
    // Handshake capture state - using SharedFlow to emit each handshake event
    private val _handshakeEvents = MutableSharedFlow<GhostResponse.Handshake>(replay = 1, extraBufferCapacity = 16)
    val handshakeEvents: SharedFlow<GhostResponse.Handshake> = _handshakeEvents.asSharedFlow()
    
    // PCAP file path
    private val _pcapFile = MutableStateFlow<String?>(null)
    val pcapFile: StateFlow<String?> = _pcapFile.asStateFlow()

    private val _badUsbScripts = MutableStateFlow<List<String>>(emptyList())
    val badUsbScripts: StateFlow<List<String>> = _badUsbScripts.asStateFlow()
    
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
    private val advertiserCache = ConcurrentHashMap<String, GhostResponse.AdvertiserDevice>()
    private val aerialCache = ConcurrentHashMap<String, GhostResponse.AerialDevice>()

    private var scanJob: Job? = null
    private var currentCommand: GhostCommand? = null
    private var badUsbListExpectedCount: Int? = null
    private var badUsbListCollecting = false

    // Response collection job
    private var responseJob: Job? = null
    @Volatile private var chipInfoRequestedForConnection = false

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
                    SerialManager.ConnectionState.CONNECTED -> {
                        wasConnected = true
                        if (!chipInfoRequestedForConnection) {
                            _deviceInfo.value = null
                            getChipInfo()
                        }
                    }
                    SerialManager.ConnectionState.DISCONNECTED,
                    SerialManager.ConnectionState.ERROR -> {
                        synchronized(this@GhostRepository) {
                            chipInfoRequestedForConnection = false
                        }
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

    fun getSerialPortCount(device: UsbDevice): Int = serialManager.getSerialPortCount(device)
    
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
    suspend fun connect(
        device: UsbDevice,
        baudRate: Int = 115200,
        portIndex: Int = 0
    ): Boolean {
        val assertDtr = preferencesRepository.appSettings.first().dtrCompatibilityMode
        val ok = serialManager.connect(device, baudRate, portIndex, assertDtr)
        if (ok) {
            preferencesRepository.setSavedDevice(
                SavedDevice.Usb(
                    vendorId = device.vendorId,
                    productId = device.productId,
                    deviceName = device.deviceName,
                    baudRate = baudRate,
                    portIndex = portIndex
                )
            )
        }
        return ok
    }

    suspend fun connectBle(device: BleBridgeDevice): Boolean {
        val ok = serialManager.connectBle(device)
        if (ok) {
            preferencesRepository.setSavedDevice(
                SavedDevice.Ble(device.address, device.name)
            )
        }
        return ok
    }

    suspend fun connectSavedDevice(): SavedConnectionAttempt {
        val saved = preferencesRepository.getSavedDevice() ?: return SavedConnectionAttempt.NOT_FOUND
        return when (saved) {
            is SavedDevice.Usb -> {
                val devices = serialManager.getAvailableDevices()
                val device = devices.firstOrNull { d ->
                    d.deviceName == saved.deviceName &&
                        d.vendorId == saved.vendorId && d.productId == saved.productId
                } ?: devices.firstOrNull { d ->
                    d.vendorId == saved.vendorId && d.productId == saved.productId
                } ?: return SavedConnectionAttempt.FAILED
                val assertDtr = preferencesRepository.appSettings.first().dtrCompatibilityMode
                if (serialManager.connect(device, saved.baudRate, saved.portIndex, assertDtr)) {
                    SavedConnectionAttempt.STARTED
                } else {
                    SavedConnectionAttempt.FAILED
                }
            }
            is SavedDevice.Ble -> {
                val device = BleBridgeDevice(
                    address = saved.address,
                    name = saved.name,
                    rssi = 0
                )
                if (serialManager.connectBle(device)) {
                    SavedConnectionAttempt.STARTED
                } else {
                    when (serialManager.lastBleConnectionFailure.value) {
                        BleConnectionFailure.PERMISSION_REQUIRED -> SavedConnectionAttempt.BLUETOOTH_PERMISSION_REQUIRED
                        BleConnectionFailure.BLUETOOTH_DISABLED -> SavedConnectionAttempt.BLUETOOTH_DISABLED
                        else -> SavedConnectionAttempt.FAILED
                    }
                }
            }
        }
    }

    suspend fun forgetSavedDevice() = preferencesRepository.clearSavedDevice()

    /**
     * Connect with automatic baud rate detection
     */
    suspend fun connectWithAutoBaud(device: UsbDevice, portIndex: Int = 0): Boolean {
        val assertDtr = preferencesRepository.appSettings.first().dtrCompatibilityMode
        val ok = serialManager.connectWithAutoBaud(device, portIndex, assertDtr)
        if (ok) {
            preferencesRepository.setSavedDevice(
                SavedDevice.Usb(
                    vendorId = device.vendorId,
                    productId = device.productId,
                    deviceName = device.deviceName,
                    baudRate = serialManager.detectedBaudRate.value ?: 115200,
                    portIndex = portIndex
                )
            )
        }
        return ok
    }

    val detectedBaudRate = serialManager.detectedBaudRate

    /**
     * Connect to first available device
     */
    suspend fun connectFirstAvailable(): Boolean {
        val device = serialManager.getAvailableDevices().firstOrNull() ?: return false
        return connectWithAutoBaud(device)
    }

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
        sendCommand(GhostCommand.Stop)
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

    suspend fun startPacketCapture(mode: GhostCommand.CaptureMode, channel: Int? = null) {
        sendCommand(GhostCommand.Capture(mode, channel))
    }

    suspend fun stopPacketCapture() {
        sendCommand(GhostCommand.CaptureStop)
    }

    suspend fun captureList() {
        clearCaptureFiles()
        sendCommand(GhostCommand.CaptureList)
    }

    suspend fun captureExport(pcapFile: String) {
        _captureExportResult.value = null
        sendCommand(GhostCommand.CaptureExport(pcapFile))
    }

    suspend fun startWiresharkCapture(channel: Int? = null) {
        sendCommand(GhostCommand.CaptureWireshark(channel))
    }

    suspend fun startWiresharkBleCapture() {
        sendCommand(GhostCommand.CaptureWiresharkBle)
    }

    // ==================== WiFi Network Scans / Advanced Attacks ====================

    suspend fun runSweep() {
        clearSweep()
        sendCommand(GhostCommand.Sweep())
    }

    suspend fun stopSweep() {
        sendCommand(GhostCommand.Sweep(stop = true))
    }

    suspend fun scanLocal() {
        clearOpenPorts()
        clearIpLookup()
        clearScanCompletion()
        sendCommand(GhostCommand.ScanLocal)
    }

    suspend fun scanArp() {
        clearArpScan()
        sendCommand(GhostCommand.ScanArp)
    }

suspend fun scanPorts(target: String?, startPort: Int? = null, endPort: Int? = null) {
        clearOpenPorts()
        clearScanCompletion()
        sendCommand(GhostCommand.ScanPorts(target, startPort, endPort))
    }

suspend fun scanSsh(target: String?) {
        clearSshBanners()
        clearSshScanSummary()
        sendCommand(GhostCommand.ScanSsh(target))
    }

    suspend fun runCongestion() {
        clearCongestionRows()
        sendCommand(GhostCommand.Congestion)
    }

    suspend fun startListenProbes() {
        clearProbeRequests()
        sendCommand(GhostCommand.ListenProbes())
    }

    suspend fun stopListenProbes() {
        sendCommand(GhostCommand.ListenProbes(stop = true))
    }

    suspend fun dhcpStarveDisplay() {
        _dhcpStarveStats.value = null
        sendCommand(GhostCommand.DhcpStarve(display = true))
    }

    suspend fun sinkhole(action: GhostCommand.SinkholeAction, arg: String? = null) {
        if (action == GhostCommand.SinkholeAction.START) clearSinkholeStatus()
        sendCommand(GhostCommand.Sinkhole(action, arg))
    }

    suspend fun webUiAp(action: GhostCommand.WebUiApAction) {
        sendCommand(GhostCommand.WebUiAp(action))
    }

    suspend fun webAuth(enable: Boolean) {
        sendCommand(GhostCommand.WebAuth(enable))
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
     * Scan for BLE advertisers with optional OUI/vendor filter.
     */
    suspend fun scanBleAdvertisers(filter: GhostCommand.BleAdvertiserFilter = GhostCommand.BleAdvertiserFilter.All) {
        clearAdvertiserDevices()
        sendCommand(GhostCommand.BleAdvertiserScan(filter))
    }

    /**
     * List discovered BLE advertisers.
     */
    suspend fun listAdvertisers() {
        clearAdvertiserDevices()
        sendCommand(GhostCommand.ListAdvertisers)
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

    suspend fun nfcGetBackend() {
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.Backend()))
    }

    suspend fun nfcSetBackend(backend: GhostCommand.NfcBackendType) {
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.Backend(backend)))
    }

    suspend fun nfcScan(parse: Boolean = false) {
        clearNfcTags()
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.Scan(parse)))
    }

    suspend fun nfcOnce(parse: Boolean = false) {
        clearNfcTags()
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.Once(parse)))
    }

    suspend fun nfcSave() {
        _nfcSaveResult.value = null
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.Save))
    }

    suspend fun nfcHardnested(
        knownBlock: Int,
        knownKeyType: GhostCommand.NfcKeyType,
        knownKeyHex: String,
        targetBlock: Int,
        targetKeyType: GhostCommand.NfcKeyType,
        samples: Int? = null
    ) {
        _nfcHardnestedResult.value = null
        sendCommand(
            GhostCommand.Nfc(
                GhostCommand.NfcSubcommand.Hardnested(
                    knownBlock, knownKeyType, knownKeyHex, targetBlock, targetKeyType, samples
                )
            )
        )
    }

    suspend fun nfcPicopass(save: Boolean = false) {
        _nfcPicopassResult.value = null
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.Picopass(save)))
    }

    suspend fun nfcStatus() {
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.Status))
    }

    suspend fun nfcStop() {
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.Stop))
    }

    suspend fun nfcEmulateUid(uid: String, atqa: String? = null, sak: String? = null) {
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.EmulateUid(uid, atqa, sak)))
    }

    suspend fun nfcEmulateNdef(url: String? = null, text: String? = null) {
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.EmulateNdef(url, text)))
    }

    suspend fun nfcEmulateFile(path: String) {
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.EmulateFile(path)))
    }

    suspend fun nfcEmulateStop() {
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.EmulateStop))
    }

    suspend fun nfcEmulateStatus() {
        sendCommand(GhostCommand.Nfc(GhostCommand.NfcSubcommand.EmulateStatus))
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
        _badUsbScripts.value = emptyList()
        badUsbListExpectedCount = null
        badUsbListCollecting = true
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

    suspend fun startBadUsbKeyboard() {
        sendCommand(GhostCommand.BadUsbKeyboardStart)
    }

    suspend fun stopBadUsbKeyboard() {
        sendCommand(GhostCommand.BadUsbKeyboardStop)
    }

    suspend fun typeBadUsbText(text: String) {
        sendCommand(GhostCommand.BadUsbType(text))
    }

    suspend fun typeBadUsbChar(charCode: Int) {
        sendCommand(GhostCommand.BadUsbTypeChar(charCode))
    }

    suspend fun startBadUsbJiggler() {
        sendCommand(GhostCommand.BadUsbJiggleStart)
    }

    suspend fun stopBadUsbJiggler() {
        sendCommand(GhostCommand.BadUsbJiggleStop)
    }

    suspend fun badUsbConfig(setting: GhostCommand.BadUsbSetting) {
        sendCommand(GhostCommand.BadUsbConfig(setting))
    }

    suspend fun badUsbKey(modifier: Int, keyCode: Int) {
        sendCommand(GhostCommand.BadUsbKey(modifier, keyCode))
    }

    suspend fun badUsbTrackpad(action: GhostCommand.BadUsbTrackpadAction) {
        sendCommand(GhostCommand.BadUsbTrackpad(action))
    }

    suspend fun badUsbExec(size: Int) {
        sendCommand(GhostCommand.BadUsbExec(size))
    }

    suspend fun badUsbStatus(status: String) {
        sendCommand(GhostCommand.BadUsbStatus(status))
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
     * Start wardriving with advanced firmware flags.
     */
    suspend fun startWardrive(
        helper: Boolean = false,
        channels: String? = null,
        hopMs: Int? = null,
        weighted: Boolean = false
    ) {
        _isWardriving.value = true
        _wardriveStats.value = null
        sendCommand(GhostCommand.StartWardrive(false, helper, channels, hopMs, weighted))
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

    fun updatePhoneLocation(location: PhoneLocation) {
        latestPhoneLocation = location
        if (_isPhoneWardriving.value) {
            publishPhoneWardriveStats()
        }
    }

    suspend fun startPhoneWardrive(includeBle: Boolean = false) {
        phoneWardriveRows.clear()
        synchronized(phoneWardriveApsLock) {
            _phoneWardriveAps.value = emptyList()
        }
        phoneWardriveObservations = 0
        phoneWardriveLocatedObservations = 0
        phoneWardriveStartedAt = System.currentTimeMillis()
        _phoneWardriveStats.value = PhoneWardriveStats(gpsFix = latestPhoneLocation != null)
        _isPhoneWardriving.value = true
        sendCommand(GhostCommand.WdStream(includeBle = includeBle))
    }

    suspend fun stopPhoneWardrive(context: Context) {
        _isPhoneWardriving.value = false
        sendCommand(GhostCommand.WdStream(stop = true))
        savePhoneWardriveCsv(context)
    }

    fun listSavedWardriveCsvs(context: Context): List<SavedWardriveCsv> {
        val results = mutableListOf<SavedWardriveCsv>()
        try {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.DATE_ADDED
                )
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                val args = arrayOf("ghostesp_phone_wardrive_%.csv")
                val sortOrder = "${MediaStore.Downloads.DATE_ADDED} DESC"
                resolver.query(collection, projection, selection, args, sortOrder)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
                    while (cursor.moveToNext()) {
                        val uri = Uri.withAppendedPath(collection, cursor.getLong(idCol).toString())
                        results.add(SavedWardriveCsv(
                            uri = uri.toString(),
                            fileName = cursor.getString(nameCol),
                            size = cursor.getLong(sizeCol),
                            dateAdded = cursor.getLong(dateCol) * 1000L
                        ))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.listFiles()?.filter {
                    it.name.startsWith("ghostesp_phone_wardrive_") && it.name.endsWith(".csv")
                }?.sortedByDescending { it.lastModified() }?.forEach { file ->
                    results.add(SavedWardriveCsv(
                        uri = Uri.fromFile(file).toString(),
                        fileName = file.name,
                        size = file.length(),
                        dateAdded = file.lastModified()
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e("GhostRepository", "Failed to list saved wardrive CSVs", e)
        }
        return results
    }

    fun deleteSavedWardriveCsv(context: Context, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: Exception) {
            Log.e("GhostRepository", "Failed to delete wardrive CSV", e)
            false
        }
    }

    fun getSavedWardriveCsvShareIntent(context: Context, uriString: String): Intent? {
        return try {
            val uri = Uri.parse(uriString)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Log.e("GhostRepository", "Failed to create share intent", e)
            null
        }
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
     * The app downloader uses sd read --base64 so BLE framing never carries raw file bytes.
     */
    suspend fun downloadSdFile(context: Context, filePath: String, fileName: String) {
        _transferProgress.value = FileTransferProgress.Downloading(fileName, 0, 0, 0)
        try {
            val chunkSize = 768
            val readPath = compactSdPathForCommand(filePath)
            var offset = 0L
            var fileSize: Long? = null
            val allBytes = ByteArrayOutputStream()

            while (offset < (fileSize ?: Long.MAX_VALUE)) {
                val remaining = fileSize?.let { it - offset }
                val length = if (remaining != null) minOf(chunkSize.toLong(), remaining).toInt() else chunkSize
                val parser = awaitSdBase64Chunk(
                    command = GhostCommand.SdRead(readPath, offset.toInt(), length, base64 = true),
                    timeoutMs = 30_000
                ) ?: throw Exception("Timeout reading chunk at offset $offset")

                fileSize = parser.fileSize ?: fileSize
                val chunk = parser.decodedChunk()
                if (chunk.isEmpty()) {
                    if (fileSize == 0L) break
                    throw Exception("No data decoded for chunk at offset $offset")
                }

                allBytes.write(chunk)
                offset += chunk.size
                val total = fileSize ?: 0L
                val pct = if (total > 0) ((offset * 100) / total).coerceAtMost(100).toInt() else 0
                _transferProgress.value = FileTransferProgress.Downloading(fileName, offset, total, pct)
            }

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

    private fun compactSdPathForCommand(path: String): String {
        return path.removePrefix("/mnt/ghostesp/").ifEmpty { path }
    }

    /**
     * Upload [bytes] to the SD card at [path], chunked to match the firmware's CLI argument
     * limits (mirrors the 768-byte raw chunk size used by downloadSdFile/sd read). The first
     * chunk is sent via `sd write` (create/truncate) and the rest via `sd append`. Each chunk's
     * reported byte count is verified before the next is sent, and the final file size is
     * checked against [bytes].size once the transfer completes.
     */
    suspend fun uploadSdFile(
        path: String,
        bytes: ByteArray,
        fileName: String = path.substringAfterLast('/'),
        onProgress: (uploaded: Long, total: Long, pct: Int) -> Unit = { _, _, _ -> }
    ) {
        _transferProgress.value = FileTransferProgress.Uploading(fileName, 0, bytes.size.toLong(), 0)
        try {
            val chunkSize = 768
            val writePath = compactSdPathForCommand(path)
            val total = bytes.size.toLong()
            var offset = 0
            var chunkIndex = 0

            do {
                val end = minOf(offset + chunkSize, bytes.size)
                val chunk = bytes.copyOfRange(offset, end)
                val encoded = Base64.encodeToString(chunk, Base64.NO_WRAP)
                val command = if (chunkIndex == 0) {
                    GhostCommand.SdWrite(writePath, encoded)
                } else {
                    GhostCommand.SdAppend(writePath, encoded)
                }

                val result = awaitSdWriteChunk(command, timeoutMs = 30_000)
                    ?: throw Exception("Timeout writing chunk at offset $offset")
                if (result.errorMessage != null) {
                    throw Exception(result.errorMessage)
                }
                if (result.reportedBytes != chunk.size) {
                    throw Exception("SD write size mismatch at offset $offset: sent=${chunk.size} reported=${result.reportedBytes}")
                }

                offset = end
                chunkIndex++
                val pct = if (total > 0) ((offset * 100) / total).coerceAtMost(100).toInt() else 100
                _transferProgress.value = FileTransferProgress.Uploading(fileName, offset.toLong(), total, pct)
                onProgress(offset.toLong(), total, pct)
            } while (offset < bytes.size)

            val finalSize = awaitSdSize(writePath, timeoutMs = 10_000)
            if (finalSize != total) {
                throw Exception("Upload verification failed: expected $total bytes, SD reports $finalSize")
            }

            _transferProgress.value = FileTransferProgress.Complete(fileName, true)
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

    private class SdWriteParser {
        private val lock = Any()
        private val lineBuffer = StringBuilder()

        @Volatile
        var lastDataAtMs: Long = 0
            private set

        var reportedBytes: Int? = null
            private set

        var errorMessage: String? = null
            private set

        var done = false
            private set

        fun onBytes(bytes: ByteArray) = synchronized(lock) {
            lastDataAtMs = System.currentTimeMillis()
            lineBuffer.append(bytes.toString(Charsets.UTF_8))

            while (true) {
                val newline = lineBuffer.indexOf("\n")
                if (newline < 0) return@synchronized

                val line = lineBuffer.substring(0, newline).trimEnd('\r')
                lineBuffer.delete(0, newline + 1)
                handleLine(line)
            }
        }

        fun onLine(line: String) = synchronized(lock) {
            handleLine(line)
        }

        private fun handleLine(line: String) {
            val trimmed = line.trimEnd('\r')
            when {
                trimmed.startsWith("SD:WRITE:bytes=") -> {
                    reportedBytes = trimmed.removePrefix("SD:WRITE:bytes=").toIntOrNull()
                }
                trimmed.startsWith("SD:APPEND:bytes=") -> {
                    reportedBytes = trimmed.removePrefix("SD:APPEND:bytes=").toIntOrNull()
                }
                trimmed.startsWith("SD:ERR:") -> {
                    errorMessage = trimmed
                    done = true
                }
                trimmed.startsWith("SD:OK") -> {
                    done = true
                }
            }
        }
    }

    private suspend fun awaitSdWriteChunk(
        command: GhostCommand,
        timeoutMs: Long
    ): SdWriteParser? = withTimeoutOrNull(timeoutMs) {
        val parser = SdWriteParser()
        val completed = CompletableDeferred<SdWriteParser>()
        val useBlePayloads = serialManager.connectionTransport.value == SerialManager.ConnectionTransport.BLE
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (useBlePayloads) {
                serialManager.bleBridgeDataPayloads.collect { bytes ->
                    parser.onBytes(bytes)
                    if (parser.done && !completed.isCompleted) {
                        completed.complete(parser)
                    }
                }
            } else {
                serialManager.rawOutput.collect { line ->
                    parser.onLine(line)
                    if (parser.done && !completed.isCompleted) {
                        completed.complete(parser)
                    }
                }
            }
        }
        try {
            if (!sendCommand(command)) {
                throw IllegalStateException("Failed to send ${command.commandString}")
            }
            completed.await()
        } finally {
            job.cancel()
        }
    }

    private suspend fun awaitSdSize(
        path: String,
        timeoutMs: Long
    ): Long? = withTimeoutOrNull(timeoutMs) {
        val completed = CompletableDeferred<Long?>()
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serialManager.rawOutput.collect { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("SD:SIZE:")) {
                    if (!completed.isCompleted) completed.complete(trimmed.removePrefix("SD:SIZE:").toLongOrNull())
                } else if (trimmed.startsWith("SD:ERR:")) {
                    if (!completed.isCompleted) completed.complete(null)
                }
            }
        }
        try {
            if (!sendCommand(GhostCommand.SdSize(path))) {
                throw IllegalStateException("Failed to send sd size $path")
            }
            completed.await()
        } finally {
            job.cancel()
        }
    }

    private class SdReadParser {
        private val lock = Any()
        private val lineBuffer = StringBuilder()
        private val chunkBytes = ByteArrayOutputStream()
        private var sawBegin = false

        @Volatile
        var lastDataAtMs: Long = 0
            private set

        var fileSize: Long? = null
            private set

        var offset: Long? = null
            private set

        var expectedBytes: Int? = null
            private set

        var done = false
            private set

        fun onBytes(bytes: ByteArray) = synchronized(lock) {
            lastDataAtMs = System.currentTimeMillis()
            lineBuffer.append(bytes.toString(Charsets.UTF_8))

            while (true) {
                val newline = lineBuffer.indexOf("\n")
                if (newline < 0) return@synchronized

                val line = lineBuffer.substring(0, newline).trimEnd('\r')
                lineBuffer.delete(0, newline + 1)
                handleLine(line)
            }
        }

        fun onLine(line: String) = synchronized(lock) {
            handleLine(line)
        }

        fun flushPendingTerminalLine(): Boolean = synchronized(lock) {
            val pending = lineBuffer.toString().trimEnd('\r')
            if (pending != "SD:OK" && !pending.startsWith("SD:ERR:")) {
                return@synchronized false
            }
            lineBuffer.clear()
            handleLine(pending)
            true
        }

        private fun handleLine(line: String) {
            val trimmed = line.trimEnd('\r')
            if (!sawBegin) {
                if (trimmed.startsWith("SD:READ:BEGIN:")) {
                    sawBegin = true
                } else if (trimmed.startsWith("SD:ERR:")) {
                    throw IllegalStateException(trimmed)
                } else {
                    return
                }
            }

            when {
                trimmed.startsWith("SD:READ:SIZE:") -> {
                    fileSize = trimmed.removePrefix("SD:READ:SIZE:").toLongOrNull()
                }
                trimmed.startsWith("SD:READ:OFFSET:") -> {
                    offset = trimmed.removePrefix("SD:READ:OFFSET:").toLongOrNull()
                }
                trimmed.startsWith("SD:READ:LENGTH:") -> {
                    expectedBytes = trimmed.removePrefix("SD:READ:LENGTH:").toIntOrNull()
                }
                trimmed.startsWith("SD:READ:DATA:") -> {
                    val encoded = trimmed.removePrefix("SD:READ:DATA:")
                    val decoded = Base64.decode(encoded, Base64.DEFAULT)
                    chunkBytes.write(decoded)
                }
                trimmed.startsWith("SD:READ:END:bytes=") -> {
                    val reported = trimmed.removePrefix("SD:READ:END:bytes=").toIntOrNull()
                    if (reported != null && reported != chunkBytes.size()) {
                        throw IllegalStateException("SD read size mismatch: reported=$reported decoded=${chunkBytes.size()}")
                    }
                    done = true
                }
                trimmed == "SD:OK" -> {
                    done = true
                }
                trimmed.startsWith("SD:ERR:") -> {
                    throw IllegalStateException(trimmed)
                }
            }
        }

        fun decodedChunk(): ByteArray = synchronized(lock) { chunkBytes.toByteArray() }
    }

    suspend fun checkSdCard() {
        clearSdEntries()
        sendCommand(GhostCommand.SdList("/mnt/ghostesp"))
    }

    /**
     * Recursive directory tree listing.
     */
    suspend fun sdTree(path: String? = null, depth: Int? = null) {
        clearSdEntries()
        sendCommand(GhostCommand.SdTree(path, depth))
    }

    /**
     * Get info for a file or directory.
     */
    suspend fun sdInfo(path: String) {
        sendCommand(GhostCommand.SdInfo(path))
    }

    /**
     * Show current SD configuration.
     */
    suspend fun sdConfig() {
        sendCommand(GhostCommand.SdConfig)
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

    private suspend fun awaitSdBase64Chunk(
        command: GhostCommand.SdRead,
        timeoutMs: Long
    ): SdReadParser? = withTimeoutOrNull(timeoutMs) {
        val parser = SdReadParser()
        val completed = CompletableDeferred<SdReadParser>()
        val useBlePayloads = serialManager.connectionTransport.value == SerialManager.ConnectionTransport.BLE
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (useBlePayloads) {
                serialManager.bleBridgeDataPayloads.collect { bytes ->
                    try {
                        parser.onBytes(bytes)
                        if (parser.done && !completed.isCompleted) {
                            completed.complete(parser)
                        }
                    } catch (e: Exception) {
                        if (!completed.isCompleted) completed.completeExceptionally(e)
                    }
                }
            } else {
                serialManager.rawOutput.collect { line ->
                    try {
                        parser.onLine(line)
                        if (parser.done && !completed.isCompleted) {
                            completed.complete(parser)
                        }
                    } catch (e: Exception) {
                        if (!completed.isCompleted) completed.completeExceptionally(e)
                    }
                }
            }
        }
        val idleFlushJob = if (useBlePayloads) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                while (!completed.isCompleted) {
                    delay(25)
                    if (parser.lastDataAtMs > 0 && System.currentTimeMillis() - parser.lastDataAtMs >= 250) {
                        try {
                            if (parser.flushPendingTerminalLine() && parser.done && !completed.isCompleted) {
                                completed.complete(parser)
                            }
                        } catch (e: Exception) {
                            if (!completed.isCompleted) completed.completeExceptionally(e)
                        }
                    }
                }
            }
        } else {
            null
        }
        try {
            if (!sendCommand(command)) {
                throw IllegalStateException("Failed to send ${command.commandString}")
            }
            completed.await()
        } finally {
            job.cancel()
            idleFlushJob?.cancel()
        }
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
     * Build an Intent that opens the system Downloads folder in the user's
     * preferred file explorer / file manager.
     *
     * Tries the modern MediaStore Downloads collection first, then falls
     * back to the legacy DownloadManager view.
     */
    fun openDownloadsFolderIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "vnd.android.cursor.dir/vnd.android.document.collection")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Look up a previously saved file by name in the Downloads collection
     * and return a content Uri the file manager can open.
     */
    fun openDownloadedFileIntent(context: Context, fileName: String): Intent? {
        return try {
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.MIME_TYPE
            )
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            } else {
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            }
            val args = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(fileName)
            } else {
                arrayOf(fileName, "Download/%")
            }
            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val mime = cursor.getString(2) ?: getMimeType(fileName)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("GhostRepository", "Failed to resolve download uri for $fileName", e)
            null
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

    // ==================== Ethernet Diagnostics Commands ====================

    suspend fun ethStats() {
        sendCommand(GhostCommand.EthStats)
    }

    suspend fun ethInfo() {
        sendCommand(GhostCommand.EthInfo)
    }

    suspend fun ethFingerprint() {
        sendCommand(GhostCommand.EthFingerprint)
    }

    suspend fun ethArp() {
        _arpScanResults.value = emptyList()
        sendCommand(GhostCommand.EthArp)
    }

    fun clearEthArpResults() {
        _arpScanResults.value = emptyList()
    }

    suspend fun ethPorts(ip: String, startPort: Int? = null, endPort: Int? = null) {
        _portScanResults.value = emptyList()
        sendCommand(GhostCommand.EthPorts(ip, startPort, endPort))
    }

    suspend fun ethPing() {
        _pingScanResults.value = emptyList()
        sendCommand(GhostCommand.EthPing)
    }

    suspend fun ethTrace(hostname: String, maxHops: Int? = null) {
        _traceHops.value = emptyList()
        sendCommand(GhostCommand.EthTrace(hostname, maxHops))
    }

    suspend fun ethPoison(action: GhostCommand.EthPoisonAction) {
        when (action) {
            GhostCommand.EthPoisonAction.START -> clearEthPoison()
            GhostCommand.EthPoisonAction.LIST -> {
                _ethPoisonDomains.value = emptyList()
                pendingEthPoisonKind = null
            }
            GhostCommand.EthPoisonAction.COOKIES -> {
                _ethPoisonCookies.value = emptyList()
                pendingEthPoisonKind = null
            }
            GhostCommand.EthPoisonAction.CREDS -> {
                _ethPoisonCreds.value = emptyList()
                pendingEthPoisonKind = null
            }
            GhostCommand.EthPoisonAction.STATUS, GhostCommand.EthPoisonAction.STOP -> Unit
        }
        sendCommand(GhostCommand.EthPoison(action))
    }

    // ==================== RGB Commands ====================

    suspend fun setRgbColor(color: GhostCommand.RgbColorType) {
        sendCommand(GhostCommand.RgbColor(color))
    }

    suspend fun setRgbMode(mode: GhostCommand.RgbModeType) {
        sendCommand(GhostCommand.RgbMode(mode))
    }

    suspend fun setPersistentRgbMode(mode: GhostCommand.PersistentRgbMode) {
        sendCommand(GhostCommand.SetRgbMode(mode))
    }

    // ==================== Settings Commands ====================

    /**
     * Get chip info
     */
    suspend fun getChipInfo() {
        val shouldRequest = synchronized(this) {
            if (chipInfoRequestedForConnection) false else {
                chipInfoRequestedForConnection = true
                true
            }
        }
        if (!shouldRequest) return
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

    private fun handleBadUsbListLine(raw: String): Boolean {
        if (!badUsbListCollecting && !raw.startsWith("BadUSB scripts")) return false

        when {
            raw.startsWith("BadUSB scripts") -> {
                badUsbListCollecting = true
                badUsbListExpectedCount = Regex("BadUSB scripts \\((\\d+)\\)").find(raw)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
                _statusMessage.value = raw
                return true
            }
            raw.startsWith("No scripts found") -> {
                _badUsbScripts.value = emptyList()
                badUsbListExpectedCount = 0
                badUsbListCollecting = false
                _statusMessage.value = raw
                return true
            }
            badUsbListCollecting -> {
                val match = Regex("^\\[(\\d+)\\]\\s+(.+)$").find(raw.trim())
                if (match != null) {
                    val filename = match.groupValues[2].trim()
                    _badUsbScripts.update { current ->
                        if (current.contains(filename)) current else current + filename
                    }
                    val expected = badUsbListExpectedCount
                    if (expected != null && _badUsbScripts.value.size >= expected) {
                        badUsbListCollecting = false
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun parseAccessPointsFromRaw(raw: String): Boolean {
        if (!raw.contains("SSID:")) return false

        val starts = Regex("(?m)^\\s*\\[(\\d+)]\\s*SSID:").findAll(raw).toList()
        if (starts.isEmpty()) return false

        var parsedAny = false
        starts.forEachIndexed { index, match ->
            val start = match.range.first
            val end = starts.getOrNull(index + 1)?.range?.first ?: raw.length
            val record = raw.substring(start, end).trim()
            GhostResponse.AccessPoint.parse(record)?.let { ap ->
                apCache[ap.index] = ap
                parsedAny = true
            }
        }

        if (parsedAny) {
            _accessPoints.value = apCache.values.sortedBy { it.index }
        }
        return parsedAny
    }

    private fun handleWdStreamAp(ap: GhostResponse.WdStreamAp) {
        phoneWardriveObservations += 1

        val location = latestPhoneLocation
        if (location != null) {
            phoneWardriveLocatedObservations += 1
            val firstSeen = phoneWardriveRows[ap.bssid]?.firstSeen ?: formatWigleTimestamp(location.timestamp)
            phoneWardriveRows[ap.bssid] = PhoneWardriveRow(
                bssid = ap.bssid,
                ssid = if (ap.hidden) "" else ap.ssid,
                auth = ap.auth,
                channel = ap.channel,
                rssi = ap.rssi,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude ?: 0.0,
                accuracy = location.accuracy ?: 0f,
                firstSeen = firstSeen
            )
            addPhoneWardriveAp(ap.bssid, if (ap.hidden) "" else ap.ssid, ap.rssi, location, isBle = false)
        }

        publishPhoneWardriveStats()
    }

    private fun handleWdStreamBle(ble: GhostResponse.WdStreamBle) {
        phoneWardriveObservations += 1

        val location = latestPhoneLocation
        if (location != null) {
            phoneWardriveLocatedObservations += 1
            val firstSeen = phoneWardriveRows[ble.mac]?.firstSeen ?: formatWigleTimestamp(location.timestamp)
            phoneWardriveRows[ble.mac] = PhoneWardriveRow(
                bssid = ble.mac,
                ssid = ble.name,
                auth = "Misc [LE]",
                channel = 0,
                rssi = ble.rssi,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude ?: 0.0,
                accuracy = location.accuracy ?: 0f,
                firstSeen = firstSeen,
                type = "BLE",
                manufacturerId = ble.manufacturerId
            )
            addPhoneWardriveAp(ble.mac, ble.name, ble.rssi, location, isBle = true)
        }

        publishPhoneWardriveStats()
    }

    private fun addPhoneWardriveAp(bssid: String, ssid: String, rssi: Int, location: PhoneLocation, isBle: Boolean = false) {
        synchronized(phoneWardriveApsLock) {
            val current = _phoneWardriveAps.value
            val existing = current.find { it.bssid == bssid }
            val ap = PhoneWardriveAp(
                bssid = bssid,
                ssid = ssid,
                rssi = rssi,
                latitude = location.latitude,
                longitude = location.longitude,
                isBle = isBle
            )
            if (existing != null) {
                _phoneWardriveAps.value = current.map { if (it.bssid == bssid) ap else it }
            } else {
                _phoneWardriveAps.value = (current + ap).take(10_000)
            }
        }
    }

    private fun publishPhoneWardriveStats(savedFileName: String? = _phoneWardriveStats.value.savedFileName) {
        _phoneWardriveStats.value = PhoneWardriveStats(
            accessPoints = phoneWardriveRows.size,
            observations = phoneWardriveObservations,
            locatedObservations = phoneWardriveLocatedObservations,
            gpsFix = latestPhoneLocation != null,
            savedFileName = savedFileName
        )
    }

    private fun savePhoneWardriveCsv(context: Context) {
        if (phoneWardriveRows.isEmpty()) {
            publishPhoneWardriveStats()
            _statusMessage.value = "Phone wardrive stopped: no GPS-tagged APs captured"
            return
        }

        val fileName = "ghostesp_phone_wardrive_${formatFileTimestamp(phoneWardriveStartedAt)}.csv"
        val csv = buildPhoneWardriveCsv()
        try {
            val uri = saveToDownloads(context, fileName, csv.toByteArray(Charsets.UTF_8))
            showDownloadNotification(context, fileName, uri, csv.length)
            publishPhoneWardriveStats(fileName)
            _statusMessage.value = "Phone wardrive saved: $fileName"
        } catch (e: Exception) {
            Log.e("GhostRepository", "Failed to save phone wardrive CSV", e)
            _statusMessage.value = "Phone wardrive save failed: ${e.message ?: "unknown error"}"
        }
    }

    private fun buildPhoneWardriveCsv(): String {
        val header = "WigleWifi-1.6,appRelease=GhostESP Companion,model=Android,release=,device=phone,display=NONE,board=Android,brand=GhostESP,star=Sol,body=3,subBody=0\n" +
            "MAC,SSID,AuthMode,FirstSeen,Channel,Frequency,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,RCOIs,MfgrId,Type\n"
        return buildString {
            append(header)
            phoneWardriveRows.values.sortedBy { it.bssid }.forEach { row ->
                append(csvEscape(row.bssid)).append(',')
                append(csvEscape(row.ssid)).append(',')
                if (row.type == "BLE") {
                    append(csvEscape(row.auth)).append(',')
                } else {
                    append(csvEscape(wigleWifiCapabilities(row.auth))).append(',')
                }
                append(csvEscape(row.firstSeen)).append(',')
                append(row.channel).append(',')
                if (row.type == "BLE") {
                    append("0,")  // Frequency: 0 for BLE
                } else {
                    append(channelToFrequency(row.channel)).append(',')
                }
                append(row.rssi).append(',')
                append(String.format(Locale.US, "%.6f", row.latitude)).append(',')
                append(String.format(Locale.US, "%.6f", row.longitude)).append(',')
                append(Math.round(row.altitude)).append(',')
                append(String.format(Locale.US, "%.1f", row.accuracy)).append(',')
                append(',')  // RCOIs (empty)
                append(csvEscape(row.manufacturerId)).append(',')
                append(row.type).append('\n')
            }
        }
    }

    private fun wigleWifiCapabilities(auth: String): String {
        return when (auth.uppercase()) {
            "OPEN", "" -> "[ESS]"
            "WEP" -> "[WEP][ESS]"
            "WPA" -> "[WPA-PSK][ESS]"
            "WPA2" -> "[WPA2-PSK][ESS]"
            "WPA3" -> "[WPA3-SAE][ESS]"
            "OWE" -> "[OWE][ESS]"
            else -> "[ESS]"
        }
    }

    private fun channelToFrequency(channel: Int): Int {
        return if (channel == 14) {
            2484
        } else if (channel > 14) {
            5000 + (channel * 5)
        } else {
            2407 + (channel * 5)
        }
    }

    private fun csvEscape(value: String): String {
        if (!value.contains(',') && !value.contains('"') && !value.contains('\n')) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun formatWigleTimestamp(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp))
    }

    private fun formatFileTimestamp(timestamp: Long): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(timestamp.takeIf { it > 0 } ?: System.currentTimeMillis()))
    }

    private fun parseResponse(response: GhostSerialResponse) {
        val startNanos = System.nanoTime()

        if (handleBadUsbListLine(response.raw)) {
            return
        }

        if (parseAccessPointsFromRaw(response.raw)) {
            return
        }
        
        when (response.type) {
            GhostSerialResponse.ResponseType.ACCESS_POINT -> {
                GhostResponse.AccessPoint.parse(response.raw)?.let { ap ->
                    // Use cache for deduplication
                    apCache[ap.index] = ap
                    _accessPoints.value = apCache.values.sortedBy { it.index }
                }
            }
            GhostSerialResponse.ResponseType.WDSTREAM_AP -> {
                GhostResponse.WdStreamAp.parse(response.raw)?.let { ap ->
                    handleWdStreamAp(ap)
                    if (_isPhoneWardriving.value) {
                        _statusMessage.value = "Phone WD: ${phoneWardriveRows.size} APs, ${phoneWardriveLocatedObservations}/${phoneWardriveObservations} GPS-tagged"
                    }
                }
            }
            GhostSerialResponse.ResponseType.WDSTREAM_BLE -> {
                GhostResponse.WdStreamBle.parse(response.raw)?.let { ble ->
                    handleWdStreamBle(ble)
                    if (_isPhoneWardriving.value) {
                        _statusMessage.value = "Phone WD: ${phoneWardriveRows.size} devices, ${phoneWardriveLocatedObservations}/${phoneWardriveObservations} GPS-tagged"
                    }
                }
            }
            GhostSerialResponse.ResponseType.WDSTREAM_STATUS -> {
                GhostResponse.WdStreamStatus.parse(response.raw)?.let { status ->
                    publishPhoneWardriveStats()
                    _statusMessage.value = status.message
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
            GhostSerialResponse.ResponseType.ADVERTISER_DEVICE -> {
                GhostResponse.AdvertiserDevice.parseLive(response.raw)?.let { device ->
                    advertiserCache[device.mac] = device
                    _advertiserDevices.value = advertiserCache.values.sortedByDescending { it.rssi }
                }
            }
            GhostSerialResponse.ResponseType.ADVERTISER_DEVICE_DETAIL -> {
                GhostResponse.AdvertiserDevice.parseDetail(response.raw)?.let { device ->
                    advertiserCache[device.mac] = device
                    _advertiserDevices.value = advertiserCache.values.sortedByDescending { it.rssi }
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
            GhostSerialResponse.ResponseType.ETH_INFO -> {
                GhostResponse.EthernetInfo.parse(response.raw)?.let { info ->
                    _ethernetInfo.value = info
                }
            }
            GhostSerialResponse.ResponseType.ETH_STATS -> {
                GhostResponse.EthernetStats.parse(response.raw)?.let { stats ->
                    _ethernetStats.value = stats
                }
            }
            GhostSerialResponse.ResponseType.ETH_ARP_RESULT -> {
                GhostResponse.ArpScanResult.parse(response.raw)?.let { entry ->
                    _arpScanResults.update { it + entry }
                }
            }
            GhostSerialResponse.ResponseType.ETH_PORT_RESULT -> {
                GhostResponse.PortScanResult.parse(response.raw)?.let { entry ->
                    _portScanResults.update { it + entry }
                }
            }
            GhostSerialResponse.ResponseType.ETH_PING_RESULT -> {
                GhostResponse.PingScanResult.parse(response.raw)?.let { entry ->
                    _pingScanResults.update { it + entry }
                }
            }
            GhostSerialResponse.ResponseType.ETH_TRACE_HOP -> {
                GhostResponse.TraceHop.parse(response.raw)?.let { hop ->
                    _traceHops.update { it + hop }
                }
            }
            GhostSerialResponse.ResponseType.PINEAP_DETECTION -> {
                GhostResponse.PineapDetection.parse(response.raw)?.let { detection ->
                    _pineapDetections.update { it + detection }
                }
            }
            GhostSerialResponse.ResponseType.FLOCK_DETECTION -> {
                GhostResponse.FlockDetection.parse(response.raw)?.let { detection ->
                    _flockDetections.update { it + detection }
                }
            }
            GhostSerialResponse.ResponseType.FLOCK_SCAN_COMPLETE -> {
                GhostResponse.FlockScanComplete.parse(response.raw)?.let { _flockScanComplete.value = it }
            }
            GhostSerialResponse.ResponseType.NETBIOS_RESULT -> {
                GhostResponse.NetBiosResult.parse(response.raw)?.let { result ->
                    _netBiosResults.update { it + result }
                }
            }
            GhostSerialResponse.ResponseType.NETBIOS_COMPLETE -> {
                GhostResponse.NetBiosScanComplete.parse(response.raw)?.let { _netBiosScanComplete.value = it }
            }
            GhostSerialResponse.ResponseType.HTTP_BANNER_HIT -> {
                GhostResponse.HttpBannerHit.parse(response.raw)?.let { hit ->
                    _httpBannerHits.update { it + hit }
                }
            }
            GhostSerialResponse.ResponseType.HTTP_BANNER_SUMMARY -> {
                GhostResponse.HttpBannerSummary.parse(response.raw)?.let { _httpBannerSummary.value = it }
            }
            GhostSerialResponse.ResponseType.SNMP_HIT -> {
                GhostResponse.SnmpHit.parse(response.raw)?.let { hit ->
                    _snmpHits.update { it + hit }
                }
            }
            GhostSerialResponse.ResponseType.SNMP_SUMMARY -> {
                GhostResponse.SnmpSummary.parse(response.raw)?.let { _snmpSummary.value = it }
            }
            GhostSerialResponse.ResponseType.ENUM_HIT -> {
                GhostResponse.EnumHit.parse(response.raw)?.let { hit ->
                    _enumHits.update { it + hit }
                }
            }
            GhostSerialResponse.ResponseType.ENUM_SUMMARY -> {
                GhostResponse.EnumSummary.parse(response.raw)?.let { _enumSummary.value = it }
            }
            GhostSerialResponse.ResponseType.WPA3_COMPLIANCE -> {
                GhostResponse.Wpa3Compliance.parse(response.raw)?.let { _wpa3Compliance.value = it }
            }
            GhostSerialResponse.ResponseType.WPA3_REPORT_HEADER -> {
                GhostResponse.Wpa3ReportSummary.parseHeader(response.raw)?.let { pendingWpa3ReportApCount = it }
            }
            GhostSerialResponse.ResponseType.WPA3_REPORT_SUMMARY -> {
                GhostResponse.Wpa3ReportSummary.parseSummary(response.raw, pendingWpa3ReportApCount)?.let {
                    _wpa3ReportSummary.value = it
                }
            }
            GhostSerialResponse.ResponseType.CSA_TARGETING -> {
                GhostResponse.CsaAttackStatus.parseTargeting(response.raw)?.let { count ->
                    _csaAttackStatus.value = GhostResponse.CsaAttackStatus(targetCount = count)
                }
            }
            GhostSerialResponse.ResponseType.CSA_TARGET -> {
                GhostResponse.CsaAttackStatus.parseTarget(response.raw)?.let { target ->
                    _csaAttackStatus.update { it.copy(targets = it.targets + target) }
                }
            }
            GhostSerialResponse.ResponseType.CSA_RATE -> {
                GhostResponse.CsaAttackStatus.parseRate(response.raw)?.let { rate ->
                    _csaAttackStatus.update { it.copy(packetsPerSecond = rate) }
                }
            }
            GhostSerialResponse.ResponseType.GTK_ABUSE_STATUS -> {
                GhostResponse.GtkAbuseStatus.parse(response.raw)?.let { status ->
                    _gtkAbuseLog.update { it + status }
                }
            }
            GhostSerialResponse.ResponseType.PROBE_REQUEST -> {
                GhostResponse.ProbeRequest.parse(response.raw)?.let { probe ->
                    _probeRequests.update { it + probe }
                }
            }
            GhostSerialResponse.ResponseType.CONGESTION_HEADER -> {
                _congestionRows.value = emptyList()
            }
            GhostSerialResponse.ResponseType.CONGESTION_ROW -> {
                GhostResponse.CongestionRow.parse(response.raw)?.let { row ->
                    _congestionRows.update { it + row }
                }
            }
            GhostSerialResponse.ResponseType.PORT_SCAN_HOST -> {
                _portScanHost.value = GhostResponse.OpenPort.parseHostHeader(response.raw)
            }
            GhostSerialResponse.ResponseType.OPEN_PORT -> {
                GhostResponse.OpenPort.parsePort(response.raw)?.let { (port, udp) ->
                    val host = _portScanHost.value
                    _openPorts.update { it + GhostResponse.OpenPort(ip = host, port = port, udp = udp) }
                }
            }
            GhostSerialResponse.ResponseType.SSH_BANNER -> {
                GhostResponse.SshBanner.parseOpen(response.raw)?.let { banner ->
                    pendingSshBanner = banner
                    _sshBanners.update { it + banner }
                }
            }
            GhostSerialResponse.ResponseType.SSH_BANNER_BANNER -> {
                val banner = GhostResponse.SshBanner.parseBanner(response.raw)
                pendingSshBanner?.let { pending ->
                    val updated = pending.copy(banner = banner)
                    pendingSshBanner = updated
                    _sshBanners.update { list ->
                        list.map { if (it === pending) updated else it }
                    }
                }
            }
            GhostSerialResponse.ResponseType.SSH_SCAN_SUMMARY -> {
                GhostResponse.SshScanSummary.parse(response.raw)?.let { summary ->
                    _sshScanSummary.value = summary
                    _statusMessage.value = if (summary.target != null) {
                        "SSH scan on ${summary.target}: ${summary.portCount} open port(s)"
                    } else {
                        "SSH scan: ${summary.hostCount} host(s), ${summary.portCount} open port(s)"
                    }
                }
            }
            GhostSerialResponse.ResponseType.IP_LOOKUP_DEVICE -> handleIpLookupLine(response.raw)
            GhostSerialResponse.ResponseType.IP_LOOKUP_DONE -> {
                GhostResponse.IpLookupDevice.parseDone(response.raw)?.let { count ->
                    commitPendingIpLookupDevice()
                    _ipLookupDone.value = count
                    _statusMessage.value = "IP lookup done: $count device(s)"
                }
            }
            GhostSerialResponse.ResponseType.SCAN_COMPLETION -> {
                GhostResponse.ScanCompletion.parse(response.raw)?.let { completion ->
                    _scanCompletion.value = completion
                    _statusMessage.value = if (completion.cancelled) {
                        "Port scan cancelled: ${completion.hostCount} active host(s)"
                    } else {
                        "Port scan completed: ${completion.hostCount} active host(s)"
                    }
                }
            }
            GhostSerialResponse.ResponseType.ARP_HOST -> {
                GhostResponse.ArpHostEntry.parse(response.raw)?.let { host ->
                    _arpHosts.update { it + host }
                }
            }
            GhostSerialResponse.ResponseType.ARP_SCAN_HEADER -> {
                _arpHosts.value = emptyList()
                _arpScanSummary.value = null
            }
            GhostSerialResponse.ResponseType.ARP_SCAN_SUMMARY -> {
                GhostResponse.ArpHostEntry.parseSummary(response.raw)?.let { summary ->
                    _arpScanSummary.value = summary
                }
            }
            GhostSerialResponse.ResponseType.SWEEP_PHASE -> {
                GhostResponse.SweepPhase.parse(response.raw)?.let { phase ->
                    _sweepPhases.update { it + phase }
                    if (phase.message == "Sweep complete") _statusMessage.value = phase.message
                }
            }
            GhostSerialResponse.ResponseType.SWEEP_SUMMARY -> {
                GhostResponse.SweepSummary.parse(response.raw)?.let { summary ->
                    _sweepSummary.value = summary
                }
            }
            GhostSerialResponse.ResponseType.DHCP_STARVE_STATS -> {
                GhostResponse.DhcpStarveStats.parse(response.raw)?.let { stats ->
                    _dhcpStarveStats.value = stats
                }
            }
            GhostSerialResponse.ResponseType.CAPTURE_LIST_HEADER -> {
                _captureFiles.value = emptyList()
            }
            GhostSerialResponse.ResponseType.CAPTURE_LIST_ENTRY -> {
                GhostResponse.CaptureListEntry.parse(response.raw)?.let { entry ->
                    _captureFiles.update { it + entry }
                }
            }
            GhostSerialResponse.ResponseType.CAPTURE_LIST_EMPTY -> {
                _captureFiles.value = emptyList()
            }
            GhostSerialResponse.ResponseType.CAPTURE_EXPORT_RESULT -> {
                val result = GhostResponse.CaptureExportResult.parseExported(response.raw)
                    ?: GhostResponse.CaptureExportResult.parseFailure(response.raw)
                result?.let { r ->
                    if (r.path != null) pendingExportPath = r.path
                    if (r.failure != null) {
                        _captureExportResult.value = r
                        _statusMessage.value = r.failure
                    }
                }
            }
            GhostSerialResponse.ResponseType.CAPTURE_EXPORT_METRICS -> {
                GhostResponse.CaptureExportResult.parseMetrics(response.raw)?.let { metrics ->
                    _captureExportResult.value = metrics.copy(path = pendingExportPath)
                }
            }
            GhostSerialResponse.ResponseType.ETH_POISON_STATUS -> {
                GhostResponse.EthPoisonStatus.parse(response.raw)?.let { status ->
                    _ethPoisonStatus.value = status
                }
            }
            GhostSerialResponse.ResponseType.ETH_POISON_ITEM_HEADER -> {
                GhostResponse.EthPoisonItem.parseHeader(response.raw)?.let { header ->
                    pendingEthPoisonKind = header.kind
                    when (header.kind) {
                        GhostResponse.EthPoisonItem.EthPoisonKind.DOMAINS -> _ethPoisonDomains.value = emptyList()
                        GhostResponse.EthPoisonItem.EthPoisonKind.COOKIES -> _ethPoisonCookies.value = emptyList()
                        GhostResponse.EthPoisonItem.EthPoisonKind.CREDS -> _ethPoisonCreds.value = emptyList()
                    }
                }
            }
            GhostSerialResponse.ResponseType.ETH_POISON_ITEM -> {
                GhostResponse.EthPoisonItem.parseItem(response.raw)?.let { item ->
                    when (pendingEthPoisonKind ?: GhostResponse.EthPoisonItem.EthPoisonKind.DOMAINS) {
                        GhostResponse.EthPoisonItem.EthPoisonKind.DOMAINS -> _ethPoisonDomains.update { it + item.value }
                        GhostResponse.EthPoisonItem.EthPoisonKind.COOKIES -> _ethPoisonCookies.update { it + item.value }
                        GhostResponse.EthPoisonItem.EthPoisonKind.CREDS -> _ethPoisonCreds.update { it + item.value }
                    }
                }
            }
            GhostSerialResponse.ResponseType.SINKHOLE_STATUS_HEADER -> {
                _sinkholeStatus.value = null
            }
            GhostSerialResponse.ResponseType.SINKHOLE_STATUS_LINE, GhostSerialResponse.ResponseType.SINKHOLE_LIVE -> {
                GhostResponse.SinkholeStatus.parse(response.raw)?.let { line ->
                    val current = _sinkholeStatus.value
                    _sinkholeStatus.value = GhostResponse.SinkholeStatus(
                        state = line.state ?: current?.state,
                        ip = line.ip ?: current?.ip,
                        queries = line.queries ?: current?.queries,
                        blocked = line.blocked ?: current?.blocked,
                        dropped = line.dropped ?: current?.dropped,
                        blockPercent = line.blockPercent ?: current?.blockPercent,
                        logging = line.logging ?: current?.logging,
                        blocklist = line.blocklist ?: current?.blocklist
                    )
                }
            }
            GhostSerialResponse.ResponseType.WEBUI_AP_STATE -> {
                GhostResponse.WebUiApState.parse(response.raw)?.let { state ->
                    _webUiApState.value = state
                    _statusMessage.value = "WebUI AP-only restriction: ${if (state.enabled) "on" else "off"}"
                }
            }
            GhostSerialResponse.ResponseType.WEB_AUTH_RESULT -> {
                GhostResponse.WebAuthResult.parse(response.raw)?.let { result ->
                    _webAuthResult.value = result
                    _statusMessage.value = "Web authentication: ${if (result.enabled) "on" else "off"}"
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
                    _statusMessage.value = "NFC tag found: ${tag.type.name} ${tag.uid}"
                }
            }
            GhostSerialResponse.ResponseType.NFC_MESSAGE -> handleNfcMessage(response.raw)
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

    /**
     * Assemble scanlocal (mDNS IP lookup) device blocks from firmware lines:
     * "Device at: IP" starts a block; indented "Name:"/"Type:"/"Port:" lines fill it.
     */
    private fun handleIpLookupLine(raw: String) {
        GhostResponse.IpLookupDevice.parseDevice(raw)?.let { ip ->
            commitPendingIpLookupDevice()
            pendingIpLookupDevice = GhostResponse.IpLookupDevice(ip = ip)
            return
        }

        val pending = pendingIpLookupDevice ?: return
        GhostResponse.IpLookupDevice.parseName(raw)?.let { name ->
            pendingIpLookupDevice = pending.copy(name = name)
            return
        }
        GhostResponse.IpLookupDevice.parseType(raw)?.let { type ->
            pendingIpLookupDevice = pending.copy(type = type)
            return
        }
        GhostResponse.IpLookupDevice.parsePort(raw)?.let { port ->
            pendingIpLookupDevice = pending.copy(port = port)
        }
    }

    private fun commitPendingIpLookupDevice() {
        pendingIpLookupDevice?.let { device ->
            _ipLookupDevices.update { current ->
                if (current.any { it.ip == device.ip }) current else current + device
            }
            pendingIpLookupDevice = null
        }
    }

    /** Dispatches non-tag "nfc" CLI response lines (backend/status/save/hardnested/picopass/emulate). */
    private fun handleNfcMessage(raw: String) {        val trimmed = raw.trim()

        GhostResponse.NfcBackend.parse(trimmed)?.let {
            _nfcBackend.value = it
            _statusMessage.value = "NFC backend: ${it.name}"
            return
        }

        GhostResponse.NfcEmulateStatus.parse(trimmed)?.let {
            _nfcEmulateStatus.value = it
            _statusMessage.value = if (it.running) "NFC emulating uid=${it.uid}" else "NFC emulation stopped"
            return
        }

        GhostResponse.NfcSaveResult.parse(trimmed)?.let {
            _nfcSaveResult.value = it
            _statusMessage.value = if (it.success) "NFC dump saved: ${it.path}" else "NFC save failed"
            return
        }

        GhostResponse.NfcHardnestedResult.parse(trimmed)?.let {
            _nfcHardnestedResult.value = it
            _statusMessage.value = if (it.success) "Hardnested capture saved: ${it.path}" else "Hardnested capture failed"
            return
        }

        when {
            trimmed.contains("PicoPass/iCLASS requires ST25R3916") -> {
                _nfcPicopassResult.value = GhostResponse.NfcPicopassResult(found = false, unsupported = true)
            }
            trimmed.contains("no PicoPass/iCLASS tag found") -> {
                _nfcPicopassResult.value = GhostResponse.NfcPicopassResult(found = false)
            }
            trimmed.startsWith("NFC:") && trimmed.contains("PicoPass/iCLASS CSN=") -> {
                GhostResponse.NfcPicopassResult.parseCsn(trimmed)?.let { csn ->
                    _nfcPicopassResult.value = GhostResponse.NfcPicopassResult(found = true, csn = csn)
                }
            }
            trimmed.startsWith("Auth failed") -> {
                _nfcPicopassResult.update { (it ?: GhostResponse.NfcPicopassResult(found = true)).copy(authFailed = true) }
            }
            trimmed.startsWith("PACS:") -> {
                GhostResponse.NfcPicopassResult.parsePacs(trimmed)?.let { pacs ->
                    _nfcPicopassResult.update {
                        (it ?: GhostResponse.NfcPicopassResult(found = true)).copy(fc = pacs.fc, cn = pacs.cn, bits = pacs.bits)
                    }
                }
            }
            trimmed.startsWith("Encryption:") -> {
                GhostResponse.NfcPicopassResult.parseEncryption(trimmed)?.let { enc ->
                    _nfcPicopassResult.update {
                        (it ?: GhostResponse.NfcPicopassResult(found = true)).copy(
                            encryption = enc.value,
                            biometrics = enc.biometrics,
                            pinLen = enc.pinLen,
                            sio = enc.sio
                        )
                    }
                }
            }
            trimmed.contains("NFC: task running") -> _nfcTaskRunning.value = true
            trimmed.contains("NFC: task idle") -> _nfcTaskRunning.value = false
            trimmed.contains("NFC: stopping") -> _nfcTaskRunning.value = false
            trimmed.contains("NFC: not running") -> _nfcTaskRunning.value = false
            trimmed.contains("NFC: no tag found") -> _statusMessage.value = "No NFC tag found"
            trimmed.contains("emulation requires ST25R3916") -> _statusMessage.value = trimmed.removePrefix("NFC:").trim()
            else -> Unit
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

    fun clearAdvertiserDevices() {
        advertiserCache.clear()
        _advertiserDevices.value = emptyList()
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
        _isPhoneWardriving.value = false
        phoneWardriveRows.clear()
        synchronized(phoneWardriveApsLock) {
            _phoneWardriveAps.value = emptyList()
        }
        phoneWardriveObservations = 0
        phoneWardriveLocatedObservations = 0
        phoneWardriveStartedAt = 0L
        _phoneWardriveStats.value = PhoneWardriveStats(gpsFix = latestPhoneLocation != null)
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

        // Clear BadUSB scripts
        _badUsbScripts.value = emptyList()
        badUsbListCollecting = false
        badUsbListExpectedCount = null

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

        // Clear network-scan results
        _openPorts.value = emptyList()
        _portScanHost.value = null
        _sshBanners.value = emptyList()
        _sshScanSummary.value = null
        _arpHosts.value = emptyList()
        _arpScanSummary.value = null
        _sweepPhases.value = emptyList()
        _sweepSummary.value = null
        _dhcpStarveStats.value = null
        _congestionRows.value = emptyList()
        _probeRequests.value = emptyList()
        _scanCompletion.value = null
        _netBiosResults.value = emptyList()
        _netBiosScanComplete.value = null
        clearIpLookup()
        
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

data class PhoneWardriveStats(
    val accessPoints: Int = 0,
    val observations: Int = 0,
    val locatedObservations: Int = 0,
    val gpsFix: Boolean = false,
    val savedFileName: String? = null
)

data class PhoneWardriveAp(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val isBle: Boolean = false
)

private data class PhoneWardriveRow(
    val bssid: String,
    val ssid: String,
    val auth: String,
    val channel: Int,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val firstSeen: String,
    val type: String = "WIFI",
    val manufacturerId: String = ""
)

data class SavedWardriveCsv(
    val uri: String,
    val fileName: String,
    val size: Long,
    val dateAdded: Long
)
