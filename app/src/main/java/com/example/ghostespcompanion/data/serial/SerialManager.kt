package com.example.ghostespcompanion.data.serial

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.ghostespcompanion.data.ble.BleBridgeConstants
import com.example.ghostespcompanion.data.ble.BleBridgeDevice
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USB Serial Manager for GhostESP communication
 *
 * Architecture for robust data handling with responsive UI:
 *
 * 1. Serial Read Loop (IO dispatcher):
 *    - Reads raw bytes from USB serial port
 *    - Processes into lines immediately (no blocking)
 *    - Every cleaned line is sent to rawOutput immediately (terminal sees everything)
 *    - Multi-line grouping is only applied for the parsed response channel
 *    - Binary mode: When SD:READ:LENGTH: is detected, switches to raw byte collection
 *
 * 2. Channel Consumer (IO dispatcher):
 *    - Receives grouped lines from responseChannel
 *    - Wraps in GhostSerialResponse and emits to SharedFlow
 *    - Uses tryEmit (non-blocking) with DROP_OLDEST for UI responsiveness
 *
 * 3. UI Collection (Main dispatcher):
 *    - Collects from SharedFlows
 *    - Updates UI state
 *
 * This ensures:
 * - Terminal sees EVERY line the firmware sends, with indentation preserved
 * - Parsed responses get intelligent multi-line grouping for structured data
 * - Serial reading is NEVER blocked (non-blocking Channel.send)
 * - UI remains responsive (DROP_OLDEST prevents backpressure buildup)
 * - Binary file transfers work correctly (raw bytes preserved)
 */
@Singleton
class SerialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val bluetoothScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private var serialDriver: UsbSerialDriver? = null
    private var serialPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var bleRxCharacteristic: BluetoothGattCharacteristic? = null
    private var bleTxCharacteristic: BluetoothGattCharacteristic? = null
    private var isBleTransport = false
    private var bleHeartbeatJob: Job? = null
    private var blePendingWrite: CompletableDeferred<Int>? = null
    private var blePendingDescriptorWrite: CompletableDeferred<Int>? = null
    private var blePendingMtuChange: CompletableDeferred<Int>? = null
    private var bleServiceDiscoveryJob: Job? = null
    private val bleCommandCounter = AtomicInteger(1)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionTransport = MutableStateFlow(ConnectionTransport.NONE)
    val connectionTransport: StateFlow<ConnectionTransport> = _connectionTransport.asStateFlow()

    // Channel for parsed/grouped responses (multi-line accumulation applied here)
    // UNLIMITED capacity ensures we NEVER block the serial read loop and NEVER lose data
    private val responseChannel = Channel<String>(Channel.UNLIMITED)
    private val bleNotificationChannel = Channel<ByteArray>(Channel.UNLIMITED)

    // SharedFlows for UI consumption
    // DROP_OLDEST ensures UI never blocks even if consumer is slow
    private val _responses = MutableSharedFlow<GhostSerialResponse>(
        replay = 1,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val responses: SharedFlow<GhostSerialResponse> = _responses.asSharedFlow()

    // Raw output for terminal display — every line goes here immediately
    // No multi-line grouping, indentation preserved
    private val _rawOutput = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 2048,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val rawOutput: SharedFlow<String> = _rawOutput.asSharedFlow()

    private val _bleBridgeDataPayloads = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val bleBridgeDataPayloads: SharedFlow<ByteArray> = _bleBridgeDataPayloads.asSharedFlow()

    // Debug log for chipinfo lifecycle — captures flush/skip/send events
    private val _chipInfoDebugLog = MutableStateFlow<List<String>>(emptyList())
    val chipInfoDebugLog: StateFlow<List<String>> = _chipInfoDebugLog.asStateFlow()
    
    // USB device detection log for UI display
    private val _usbDebugLog = MutableStateFlow<List<String>>(emptyList())
    val usbDebugLog: StateFlow<List<String>> = _usbDebugLog.asStateFlow()

    private val _bleDevices = MutableStateFlow<List<BleBridgeDevice>>(emptyList())
    val bleDevices: StateFlow<List<BleBridgeDevice>> = _bleDevices.asStateFlow()

    private val _isBleScanning = MutableStateFlow(false)
    val isBleScanning: StateFlow<Boolean> = _isBleScanning.asStateFlow()
    
    private fun usbLog(msg: String) {
        val ts = System.currentTimeMillis() % 100_000
        val newLog = (_usbDebugLog.value + "[$ts] $msg").takeLast(50)
        _usbDebugLog.value = newLog
        android.util.Log.d("SerialManager", msg)
    }

    private fun bleGattStatusName(status: Int): String = when (status) {
        BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
        8 -> "GATT_CONN_TIMEOUT"
        19 -> "GATT_CONN_TERMINATE_PEER_USER"
        22 -> "GATT_CONN_TERMINATE_LOCAL_HOST"
        34 -> "GATT_LMP_TIMEOUT"
        62 -> "GATT_CONN_FAIL_ESTABLISH"
        133 -> "GATT_ERROR"
        257 -> "GATT_CONN_CANCEL"
        else -> "UNKNOWN"
    }

    private fun bleStateName(state: Int): String = when (state) {
        BluetoothGatt.STATE_CONNECTED -> "CONNECTED"
        BluetoothGatt.STATE_CONNECTING -> "CONNECTING"
        BluetoothGatt.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothGatt.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN"
    }

    private fun chipInfoLog(msg: String) {
        val ts = System.currentTimeMillis() % 100_000
        _chipInfoDebugLog.value = (_chipInfoDebugLog.value + "[$ts] $msg").takeLast(20)
    }

    private var lastPerfLogTime = 0L
    private var bytesProcessedTotal = 0L
    private var linesProcessedTotal = 0L
    private var perfLogCount = 0

    private fun perfLog(tag: String, durationNanos: Long, detail: String = "") {
        val elapsedMs = durationNanos / 1_000_000
        if (elapsedMs >= 10) {
            android.util.Log.w("SerialManager.PERF", "$tag: ${elapsedMs}ms $detail")
        }
    }

    // Binary data chunks for file transfers
    // Firmware sends raw binary after SD:READ:LENGTH: line, terminated by \nSD:READ:END:
    // Using Channel instead of SharedFlow to avoid race conditions - the collector
    // will receive the data reliably, whereas SharedFlow with replay=0 can lose data
    private val binaryChannel = Channel<ByteArray>(Channel.UNLIMITED)
    
    // Exposed as flow for collection
    val binaryChunks: Flow<ByteArray> = binaryChannel.receiveAsFlow()

    private var readJob: Job? = null
    private var consumerJob: Job? = null
    private var bleNotificationJob: Job? = null
    private var flushJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var readLoopCount = 0L
    private var readLoopBytes = 0L
    private var readLoopStartTime = 0L
    @Volatile private var lastIncomingDataAtMs = 0L
    private var bleHeartbeatWatchdog: Job? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Track if we're in the process of connecting (to prevent double-connects)
    private val isConnecting = AtomicBoolean(false)

    // Pre-allocated buffers for performance
    private val readBuffer = ByteArray(4096)
    private val lineBuffer = StringBuilder(1024)
    private val multilineBuffer = StringBuilder(512)
    private var isAccumulatingMultiline = false
    private var multilineType: LineType? = null
    private var lastLineTime = 0L

    // Dedicated chipinfo collector — completely independent of the multiline
    // state machine.  Every line that matches a known chipinfo field is appended
    // here regardless of indentation, ordering, or prompt-stripping artefacts.
    // The collector is armed when we see "chipinfo" or "Chip Information" and
    // flushed by the timer once no new field has arrived for 500ms.
    private val chipInfoCollector = StringBuilder(512)
    private var chipInfoCollectorActive = false
    private var chipInfoLastFieldTime = 0L
    private var chipInfoCollectAllUntil = 0L
    private var chipInfoSeenCount = 0
    private val recentLines = ArrayDeque<String>(32)

    // Binary mode state for SD file transfers
    // Firmware protocol: SD:READ:BEGIN:... SD:READ:SIZE:... SD:READ:OFFSET:... SD:READ:LENGTH:...
    // Then raw binary data, then \nSD:READ:END:bytes=N\n
    private var isBinaryMode = false
    private val binaryAccumulator = ByteArrayOutputStream(8192)
    private val binaryTerminator = "\nSD:READ:END:".toByteArray(Charsets.US_ASCII)
    private var terminatorMatchPos = 0
    private val binaryHeaderBuffer = ByteArrayOutputStream(256)
    private var isCollectingBinaryHeader = false
    private val bleFrameBuffer = ByteArrayOutputStream(2048)
    private val bleFallbackBuffer = ByteArrayOutputStream(256)
    @Volatile private var currentSdReadIsBase64 = false

    private val bleCommandMutex = Mutex()
    private val bleBridgeStateLock = Any()
    private val blePendingCommandEnds = linkedMapOf<Int, CompletableDeferred<Unit>>()
    private val blePendingBridgeAcks = mutableMapOf<Int, CompletableDeferred<Pair<Boolean, Int>>>()
    private val cmdIdLastDataMs = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private var bleActiveCmdId: Int = 0
    private val cmdIdIdleCloseMs = 200L

    // Atomic flag for connection status
    private val isConnectedFlag = AtomicBoolean(false)

    // Mutex to prevent concurrent connect/disconnect races
    private val connectionMutex = Mutex()
    private val bleWriteMutex = Mutex()

    // Baud rate resolved during auto-detection (null = not yet detected)
    private val _detectedBaudRate = MutableStateFlow<Int?>(null)
    val detectedBaudRate: StateFlow<Int?> = _detectedBaudRate.asStateFlow()

    /**
     * Connection state enum
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    enum class ConnectionTransport {
        NONE,
        USB,
        BLE
    }

    private data class BleBridgeFrame(
        val type: Int,
        val status: Int,
        val commandId: Int,
        val payload: ByteArray,
        val pendingBytes: Int = 0
    )

    companion object {
        private const val BLE_BRIDGE_FRAME_MAGIC0: Byte = 0x47
        private const val BLE_BRIDGE_FRAME_MAGIC1: Byte = 0x42
        private const val BLE_BRIDGE_FRAME_VERSION: Byte = 0x01
        private const val BLE_BRIDGE_FRAME_HEADER_LEN = 12
        private const val BLE_BRIDGE_FRAME_TYPE_CMD = 1
        private const val BLE_BRIDGE_FRAME_TYPE_ACK = 2
        private const val BLE_BRIDGE_FRAME_TYPE_DATA = 3
        private const val BLE_BRIDGE_FRAME_TYPE_END = 4
        private const val BLE_BRIDGE_FRAME_TYPE_ERR = 5
        private const val BLE_BRIDGE_FRAME_TYPE_FETCH = 6
        private const val BLE_BRIDGE_FRAME_TYPE_HAS_DATA = 7
        private const val BLE_BRIDGE_STATUS_OK = 0
        private const val BLE_BRIDGE_ACK_TIMEOUT_MS = 5000L
        private const val BLE_BRIDGE_ACTIVE_TIMEOUT_MS = 120000L
        private const val BLE_REQUESTED_MTU = 128
        private const val BLE_DESCRIPTOR_WRITE_TIMEOUT_MS = 4000L
        private const val BLE_MTU_TIMEOUT_MS = 4000L
        private const val BLE_CHARACTERISTIC_WRITE_TIMEOUT_MS = 4000L
        private const val BLE_GATT_OP_GAP_MS = 75L
        private const val BLE_DISCOVER_SERVICES_DELAY_MS = 300L
        private const val BLE_DISCOVERY_TIMEOUT_MS = 6000L
        private const val BLE_FALLBACK_FLUSH_BYTES = 64
        private const val RX_IDLE_POLL_MS = 50L
        /** Ordered list of baud rates to probe. 115200 first — covers stock GhostESP firmware. */
        private val PROBE_BAUD_RATES = listOf(115200, 9600, 57600, 230400, 420600, 460800, 921600)
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val record = result.scanRecord
            val hasBridgeService = record?.serviceUuids?.any { it.uuid == BleBridgeConstants.SERVICE_UUID } == true
            val rawName = record?.deviceName
            val name = rawName ?: if (hasBridgeService) "GhostESP Bridge" else return
            if (!hasBridgeService) {
                return
            }
            val entry = BleBridgeDevice(device.address, name, result.rssi)
            _bleDevices.value = (_bleDevices.value + entry)
                .distinctBy { it.address }
                .sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _isBleScanning.value = false
            android.util.Log.e("SerialManager", "BLE scan failed: $errorCode")
        }
    }

    private val bleGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            usbLog("BLE onConnectionStateChange status=$status (${bleGattStatusName(status)}) newState=$newState (${bleStateName(newState)})")
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                bluetoothGatt = gatt
                bleServiceDiscoveryJob?.cancel()
                bleServiceDiscoveryJob = scope.launch {
                    delay(BLE_DISCOVER_SERVICES_DELAY_MS)
                    if (bluetoothGatt !== gatt || !isConnecting.get()) {
                        return@launch
                    }

                    if (!hasBluetoothConnectPermission()) {
                        failBleConnection("BLE connect permission missing")
                        return@launch
                    }

                    val started = try {
                        gatt.discoverServices()
                    } catch (e: Exception) {
                        usbLog("BLE discoverServices exception: ${e.message ?: e.javaClass.simpleName}")
                        false
                    }

                    if (!started) {
                        failBleConnection("BLE discoverServices dispatch failed")
                        return@launch
                    }

                    usbLog("BLE discoverServices dispatched")
                    delay(BLE_DISCOVERY_TIMEOUT_MS)
                    if (bluetoothGatt === gatt && isConnecting.get()) {
                        failBleConnection("BLE service discovery timed out")
                    }
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                mainScope.launch {
                    disconnectInternal()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            usbLog("BLE onServicesDiscovered status=$status (${bleGattStatusName(status)})")
            bleServiceDiscoveryJob?.cancel()
            bleServiceDiscoveryJob = null
            val service: BluetoothGattService? = gatt.getService(BleBridgeConstants.SERVICE_UUID)
            bleRxCharacteristic = service?.getCharacteristic(BleBridgeConstants.RX_UUID)
            bleTxCharacteristic = service?.getCharacteristic(BleBridgeConstants.TX_UUID)
            val rx = bleRxCharacteristic
            val tx = bleTxCharacteristic
            val ctrl = service?.getCharacteristic(BleBridgeConstants.CTRL_UUID)
            if (status == BluetoothGatt.GATT_SUCCESS && rx != null && tx != null && ctrl != null) {
                scope.launch {
                    completeBleHandshake(gatt, tx)
                }
            } else {
                failBleConnection("BLE services missing or discovery failed status=$status")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            usbLog("BLE onMtuChanged mtu=$mtu status=$status (${bleGattStatusName(status)})")
            blePendingMtuChange?.complete(status)
            blePendingMtuChange = null
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value
            if (value == null || value.isEmpty()) return
            val queued = bleNotificationChannel.trySend(value.copyOf())
            if (queued.isFailure) {
                usbLog("BLE notification enqueue failed bytes=${value.size}")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (value.isEmpty()) return
            val queued = bleNotificationChannel.trySend(value.copyOf())
            if (queued.isFailure) {
                usbLog("BLE notification enqueue failed bytes=${value.size}")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            usbLog("BLE onDescriptorWrite status=$status (${bleGattStatusName(status)}) uuid=${descriptor.uuid}")
            blePendingDescriptorWrite?.complete(status)
            blePendingDescriptorWrite = null
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            usbLog("BLE onCharacteristicWrite status=$status (${bleGattStatusName(status)}) uuid=${characteristic.uuid}")
            if (characteristic.uuid == BleBridgeConstants.RX_UUID || characteristic.uuid == BleBridgeConstants.CTRL_UUID) {
                blePendingWrite?.complete(status)
                blePendingWrite = null
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val text = characteristic.value?.let { String(it, Charsets.US_ASCII) }.orEmpty().trim()
            usbLog("BLE onCharacteristicRead status=$status (${bleGattStatusName(status)}) uuid=${characteristic.uuid} value=$text")
        }
    }

    /**
     * Builds the custom probe table shared by getAvailableDevices(), connect(), and probeBaudRate().
     */
    private fun buildCustomProbeTable(): ProbeTable {
        val table = ProbeTable()
        table.addProduct(0x10C4, 0xEA60, Cp21xxSerialDriver::class.java)
        table.addProduct(0x1A86, 0x55D4, Ch34xSerialDriver::class.java)
        table.addProduct(0x1A86, 0x7522, Ch34xSerialDriver::class.java)
        table.addProduct(0x1A86, 0x7523, Ch34xSerialDriver::class.java)
        table.addProduct(0x0403, 0x6001, FtdiSerialDriver::class.java)
        table.addProduct(0x0403, 0x6010, FtdiSerialDriver::class.java)
        table.addProduct(0x0403, 0x6011, FtdiSerialDriver::class.java)
        table.addProduct(0x0403, 0x6014, FtdiSerialDriver::class.java)
        table.addProduct(0x0403, 0x6015, FtdiSerialDriver::class.java)
        table.addProduct(0x303A, 0x4001, CdcAcmSerialDriver::class.java)
        table.addProduct(0x303A, 0x4002, CdcAcmSerialDriver::class.java)
        table.addProduct(0x239A, 0x800B, CdcAcmSerialDriver::class.java)
        table.addProduct(0x239A, 0x0010, CdcAcmSerialDriver::class.java)
        table.addProduct(0x2E8A, 0x000A, CdcAcmSerialDriver::class.java)
        table.addProduct(0x2E8A, 0x0005, CdcAcmSerialDriver::class.java)
        return table
    }

    /**
     * Resolves a USB serial driver for [device] using the default prober,
     * then the custom probe table, then a CDC ACM fallback.
     */
    private fun findDriver(device: UsbDevice): UsbSerialDriver? {
        UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            .find { it.device == device }?.let { return it }

        UsbSerialProber(buildCustomProbeTable()).findAllDrivers(usbManager)
            .find { it.device == device }?.let { return it }

        return try {
            val cdc = CdcAcmSerialDriver(device)
            if (cdc.ports.isNotEmpty()) cdc else null
        } catch (e: Exception) { null }
    }

    /**
     * Get list of available USB serial devices
     * Uses the usb-serial-for-android library prober which supports:
     * - FTDI FT232, FT2232, FT4232
     * - CP210x
     * - CH340, CH341, CH9102
     * - CDC/ACM devices
     */
    fun getAvailableDevices(): List<UsbDevice> {
        _usbDebugLog.value = emptyList()
        
        val allDevices = usbManager.deviceList.values.toList()
        usbLog("=== USB Device Scan ===")
        usbLog("Total raw devices: ${allDevices.size}")
        
        allDevices.forEach { device ->
            usbLog("Raw: ${device.deviceName} VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)} if=${device.interfaceCount}")
        }
        
        val foundDevices = mutableListOf<UsbDevice>()
        
        val defaultProber = UsbSerialProber.getDefaultProber()
        val defaultDrivers = defaultProber.findAllDrivers(usbManager)
        
        usbLog("Default prober: ${defaultDrivers.size} drivers")
        defaultDrivers.forEach { driver ->
            val device = driver.device
            usbLog("  Default: ${device.deviceName} VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)}")
            foundDevices.add(device)
        }
        
        val customProber = UsbSerialProber(buildCustomProbeTable())
        val customDrivers = customProber.findAllDrivers(usbManager)
        
        usbLog("Custom prober: ${customDrivers.size} drivers")
        customDrivers.forEach { driver ->
            val device = driver.device
            if (device !in foundDevices) {
                usbLog("  Custom: ${device.deviceName} VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)}")
                foundDevices.add(device)
            }
        }
        
        var cdcCount = 0
        for (device in allDevices) {
            val isSerialDevice = try {
                val testDriver = CdcAcmSerialDriver(device)
                testDriver.ports.isNotEmpty()
            } catch (e: Exception) {
                false
            }
            
            if (isSerialDevice && device !in foundDevices) {
                usbLog("  CDC ACM: ${device.deviceName} VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)}")
                foundDevices.add(device)
                cdcCount++
            }
        }
        
        usbLog("CDC ACM fallback: $cdcCount additional")
        usbLog("=== Total serial devices: ${foundDevices.size} ===")
        
        return foundDevices.distinctBy { "${it.vendorId}-${it.productId}-${it.deviceName}" }
    }

    fun startBleScan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val scanner = bluetoothScanner ?: return
        _bleDevices.value = emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        _isBleScanning.value = true
        scanner.startScan(null, settings, bleScanCallback)
    }

    fun stopBleScan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothScanner?.stopScan(bleScanCallback)
        }
        _isBleScanning.value = false
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun closeBluetoothGatt(gatt: BluetoothGatt?) {
        try { gatt?.disconnect() } catch (e: Exception) { /* ignore */ }
        try { gatt?.close() } catch (e: Exception) { /* ignore */ }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectBle(device: BleBridgeDevice): Boolean = connectionMutex.withLock {
        if (isConnecting.get()) return@withLock false
        disconnectInternal()
        isConnecting.set(true)
        _connectionState.value = ConnectionState.CONNECTING
        if (!hasBluetoothConnectPermission()) {
            _connectionState.value = ConnectionState.ERROR
            _connectionTransport.value = ConnectionTransport.NONE
            isConnecting.set(false)
            return@withLock false
        }
        resetParsingState()
        isBleTransport = true
        val remoteDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        if (remoteDevice == null) {
            _connectionState.value = ConnectionState.ERROR
            _connectionTransport.value = ConnectionTransport.NONE
            isConnecting.set(false)
            return@withLock false
        }
        bluetoothGatt = remoteDevice.connectGatt(context, false, bleGattCallback, BluetoothDevice.TRANSPORT_LE)
        startBleNotificationProcessor()
        startConsumer()
        startFlushTimer()
        true
    }
    
    /**
     * Get ALL USB devices attached (for debugging purposes)
     * Returns devices even if not recognized as serial devices
     */
    fun getAllUsbDevices(): List<UsbDevice> {
        val devices = usbManager.deviceList.values.toList()
        usbLog("=== All USB Devices ===")
        usbLog("Total count: ${devices.size}")
        devices.forEach { device ->
            usbLog("Device: ${device.deviceName}")
            usbLog("  VID: 0x${device.vendorId.toString(16).uppercase()} PID: 0x${device.productId.toString(16).uppercase()}")
            usbLog("  Interfaces: ${device.interfaceCount}")
            usbLog("  Manufacturer: ${device.manufacturerName ?: "N/A"}")
            usbLog("  Product: ${device.productName ?: "N/A"}")
            usbLog("  Permission: ${if (usbManager.hasPermission(device)) "YES" else "NO"}")
            
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                usbLog("  Iface $i: class=${iface.interfaceClass} eps=${iface.endpointCount}")
            }
        }
        usbLog("=== End All USB Devices ===")
        return devices
    }
    
    /**
     * Debug function to log all USB device info
     */
    fun logAllUsbDevices() {
        val devices = usbManager.deviceList.values
        android.util.Log.i("SerialManager", "=== USB Device Debug (Manual) ===")
        devices.forEach { device ->
            android.util.Log.i("SerialManager", "Device: ${device.deviceName}")
            android.util.Log.i("SerialManager", "  VendorID: 0x${device.vendorId.toString(16)} ProductID: 0x${device.productId.toString(16)}")
            android.util.Log.i("SerialManager", "  Interfaces: ${device.interfaceCount}")
            android.util.Log.i("SerialManager", "  Manufacturer: ${device.manufacturerName ?: "N/A"}")
            android.util.Log.i("SerialManager", "  Product: ${device.productName ?: "N/A"}")
            android.util.Log.i("SerialManager", "  HasPermission: ${usbManager.hasPermission(device)}")
            
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                android.util.Log.i("SerialManager", "  Interface $i: class=${iface.interfaceClass} endpoints=${iface.endpointCount}")
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    android.util.Log.i("SerialManager", "    Endpoint $j: addr=0x${ep.address.toString(16)} dir=${if (ep.direction == 0) "OUT" else "IN"} type=${ep.type}")
                }
            }
        }
        android.util.Log.i("SerialManager", "=== End USB Device Debug ===")
    }

    /**
     * Connect to a USB device
     * 
     * Improved reconnection handling:
     * - Uses isConnecting flag to prevent concurrent connection attempts
     * - Uses withTimeout to prevent hanging on blocked operations
     * - Force-closes previous connection before attempting new one
     */
    @Suppress("DEPRECATION")
    suspend fun connect(device: UsbDevice, baudRate: Int = 115200): Boolean = connectionMutex.withLock {
        if (isConnecting.get()) {
            return@withLock false
        }

        try {
            withTimeout(2000) { disconnectInternal() }
        } catch (e: TimeoutCancellationException) {
            forceReset()
        } catch (e: Exception) {
            e.printStackTrace()
            forceReset()
        }

        isConnecting.set(true)

        _connectionState.value = ConnectionState.CONNECTING

        try {
            android.util.Log.d("SerialManager", "Connecting to ${device.deviceName} @ $baudRate baud")

            serialDriver = findDriver(device)

            if (serialDriver == null) {
                android.util.Log.e("SerialManager", "Could not find serial driver for device")
                _connectionState.value = ConnectionState.ERROR
                _connectionTransport.value = ConnectionTransport.NONE
                isConnecting.set(false)
                return@withLock false
            }

            android.util.Log.d("SerialManager", "Driver: ${serialDriver!!::class.simpleName} (${serialDriver!!.ports.size} ports)")

            serialPort = serialDriver!!.ports.firstOrNull() ?: run {
                _connectionState.value = ConnectionState.ERROR
                _connectionTransport.value = ConnectionTransport.NONE
                isConnecting.set(false)
                return@withLock false
            }

            usbConnection = usbManager.openDevice(device) ?: run {
                _connectionState.value = ConnectionState.ERROR
                _connectionTransport.value = ConnectionTransport.NONE
                isConnecting.set(false)
                return@withLock false
            }

            serialPort?.open(usbConnection)
            serialPort?.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            serialPort?.setDTR(true)
            serialPort?.setRTS(true)

            resetParsingState()

            isBleTransport = false
            _connectionTransport.value = ConnectionTransport.USB
            isConnectedFlag.set(true)
            isConnecting.set(false)
            startReading()
            startConsumer()
            startFlushTimer()

            _connectionState.value = ConnectionState.CONNECTED
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _connectionState.value = ConnectionState.ERROR
            _connectionTransport.value = ConnectionTransport.NONE
            isConnecting.set(false)
            try {
                withTimeout(1000) {
                    disconnectInternal()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                forceReset()
            }
            false
        }
    }

    /**
     * Connect using the first available device
     */
    suspend fun connectFirstAvailable(): Boolean {
        val devices = getAvailableDevices()
        return if (devices.isNotEmpty()) {
            connect(devices.first())
        } else {
            false
        }
    }

    /**
     * Connect with automatic baud rate detection.
     *
     * Probes each rate in PROBE_BAUD_RATES by opening the port, sending "\r\n",
     * and checking whether the response is valid ASCII (GhostESP echoes "ghost-cli>").
     * Falls back to 115200 if no baud rate produces a readable response (e.g. device
     * is silent at startup, or uses native USB CDC which ignores the baud setting).
     *
     * For native USB CDC devices (ESP32-S3 JTAG) the rate is a no-op — 115200 will
     * succeed on the first probe and connect immediately.
     */
    suspend fun connectWithAutoBaud(device: UsbDevice): Boolean {
        val baud = withContext(Dispatchers.IO) { detectBaudRate(device) } ?: 115200
        _detectedBaudRate.value = baud
        usbLog("Auto-baud result: $baud")
        return connect(device, baud)
    }

    /**
     * Iterate PROBE_BAUD_RATES and return the first one that yields a valid ASCII
     * response, or null if none do (caller should fall back to 115200).
     */
    private suspend fun detectBaudRate(device: UsbDevice): Int? {
        for (baud in PROBE_BAUD_RATES) {
            if (probeBaudRate(device, baud)) return baud
        }
        return null
    }

    /**
     * Open [device] at [baud], send a "\r\n" probe, wait 350 ms, then check whether
     * >= 75 % of the received bytes are printable ASCII. Returns true on a match.
     *
     * The probe port is opened and closed independently of the main connection so it
     * doesn't disturb the connection mutex or the main serial state.
     * DTR/RTS are intentionally NOT toggled here to avoid accidentally resetting the ESP.
     */
    private suspend fun probeBaudRate(device: UsbDevice, baud: Int): Boolean = withContext(Dispatchers.IO) {
        var probePort: UsbSerialPort? = null
        var probeConnection: android.hardware.usb.UsbDeviceConnection? = null
        try {
            val driver = findDriver(device) ?: return@withContext false
            probePort = driver.ports.firstOrNull() ?: return@withContext false
            probeConnection = usbManager.openDevice(device) ?: return@withContext false

            probePort.open(probeConnection)
            probePort.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            probePort.write("\r\n".toByteArray(Charsets.US_ASCII), 500)
            delay(350)

            val buf = ByteArray(256)
            val n = probePort.read(buf, 500)

            if (n > 3) {
                val printable = (0 until n).count { i ->
                    val b = buf[i].toInt() and 0xFF
                    b in 0x20..0x7E || b == 0x0D || b == 0x0A
                }
                val ratio = printable.toFloat() / n
                usbLog("Probe $baud baud: $n bytes, printable ratio=%.2f".format(ratio))
                ratio >= 0.75f
            } else {
                usbLog("Probe $baud baud: no response ($n bytes)")
                false
            }
        } catch (e: Exception) {
            usbLog("Probe $baud baud error: ${e.message}")
            false
        } finally {
            try { probePort?.close() } catch (_: Exception) {}
            try { probeConnection?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Disconnect from the USB device (thread-safe, serialized with connect)
     */
    suspend fun disconnect() = connectionMutex.withLock {
        try {
            withTimeout(2000) {
                disconnectInternal()
            }
        } catch (e: TimeoutCancellationException) {
            forceReset()
        }
    }

    /**
     * Force disconnect - can be called from UI when app appears stuck
     * This bypasses the mutex lock and forces a reset
     */
    fun forceDisconnect() {
        isConnecting.set(false)
        isConnectedFlag.set(false)
        forceReset()
    }

    private fun resetParsingState() {
        lineBuffer.clear()
        multilineBuffer.clear()
        isAccumulatingMultiline = false
        multilineType = null
        lastLineTime = 0L
        isBinaryMode = false
        binaryAccumulator.reset()
        terminatorMatchPos = 0
        binaryHeaderBuffer.reset()
        isCollectingBinaryHeader = false
        bleFrameBuffer.reset()
        bleFallbackBuffer.reset()
    }

    @SuppressLint("MissingPermission")
    private suspend fun completeBleHandshake(gatt: BluetoothGatt, tx: BluetoothGattCharacteristic) {
        if (!hasBluetoothConnectPermission()) {
            failBleConnection("BLE connect permission missing")
            return
        }

        val notificationsEnabled = try {
            gatt.setCharacteristicNotification(tx, true)
        } catch (e: Exception) {
            usbLog("BLE setCharacteristicNotification exception: ${e.message ?: e.javaClass.simpleName}")
            false
        }

        if (!notificationsEnabled) {
            failBleConnection("BLE enable notifications failed")
            return
        }

        val descriptor = tx.getDescriptor(BleBridgeConstants.CLIENT_CONFIG_UUID)
        if (descriptor == null) {
            failBleConnection("BLE missing TX CCCD")
            return
        }

        val descriptorStatus = bleWriteDescriptorReliable(
            gatt,
            descriptor,
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            "enable notifications"
        )
        if (descriptorStatus != BluetoothGatt.GATT_SUCCESS) {
            failBleConnection("BLE descriptor write failed status=$descriptorStatus")
            return
        }

        val mtuStatus = bleRequestMtuReliable(gatt, BLE_REQUESTED_MTU)
        if (mtuStatus != BluetoothGatt.GATT_SUCCESS) {
            failBleConnection("BLE mtu request failed status=$mtuStatus")
            return
        }

        finishBleConnect()
    }

    @SuppressLint("MissingPermission")
    private suspend fun bleWriteDescriptorReliable(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
        label: String
    ): Int = bleWriteMutex.withLock {
        if (!hasBluetoothConnectPermission()) {
            usbLog("BLE descriptor write skipped; connect permission missing label=$label")
            return@withLock -1
        }

        val deferred = CompletableDeferred<Int>()
        blePendingDescriptorWrite = deferred
        descriptor.value = value
        if (!gatt.writeDescriptor(descriptor)) {
            blePendingDescriptorWrite = null
            usbLog("BLE descriptor dispatch failed label=$label")
            return@withLock -1
        }

        usbLog("BLE descriptor dispatched label=$label")
        val status = withTimeoutOrNull(BLE_DESCRIPTOR_WRITE_TIMEOUT_MS) { deferred.await() } ?: -1
        if (blePendingDescriptorWrite === deferred) {
            blePendingDescriptorWrite = null
        }
        delay(BLE_GATT_OP_GAP_MS)
        status
    }

    @SuppressLint("MissingPermission")
    private suspend fun bleRequestMtuReliable(gatt: BluetoothGatt, mtu: Int): Int = bleWriteMutex.withLock {
        if (!hasBluetoothConnectPermission()) {
            usbLog("BLE requestMtu skipped; connect permission missing")
            return@withLock -1
        }

        val deferred = CompletableDeferred<Int>()
        blePendingMtuChange = deferred

        val requested = try {
            gatt.requestMtu(mtu)
        } catch (e: Exception) {
            usbLog("BLE requestMtu exception: ${e.message ?: e.javaClass.simpleName}")
            false
        }

        if (!requested) {
            blePendingMtuChange = null
            usbLog("BLE requestMtu($mtu) dispatch failed")
            return@withLock -1
        }

        usbLog("BLE requestMtu($mtu) dispatched")
        val status = withTimeoutOrNull(BLE_MTU_TIMEOUT_MS) { deferred.await() } ?: -1
        if (blePendingMtuChange === deferred) {
            blePendingMtuChange = null
        }
        delay(BLE_GATT_OP_GAP_MS)
        status
    }

    @SuppressLint("MissingPermission")
    private suspend fun bleWriteCharacteristicReliable(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
        label: String
    ): Int = bleWriteMutex.withLock {
        if (!hasBluetoothConnectPermission()) {
            usbLog("BLE characteristic write skipped; connect permission missing label=$label")
            return@withLock -1
        }

        val deferred = CompletableDeferred<Int>()
        blePendingWrite = deferred
        characteristic.writeType = writeType
        characteristic.value = value

        val dispatched = try {
            gatt.writeCharacteristic(characteristic)
        } catch (e: Exception) {
            usbLog("BLE characteristic write exception label=$label: ${e.message ?: e.javaClass.simpleName}")
            false
        }

        if (!dispatched) {
            blePendingWrite = null
            usbLog("BLE characteristic dispatch failed label=$label")
            return@withLock -1
        }

        usbLog("BLE characteristic dispatched label=$label writeType=$writeType bytes=${value.size}")
        val status = withTimeoutOrNull(BLE_CHARACTERISTIC_WRITE_TIMEOUT_MS) { deferred.await() } ?: -1
        if (blePendingWrite === deferred) {
            blePendingWrite = null
        }
        delay(BLE_GATT_OP_GAP_MS)
        status
    }

    private fun finishBleConnect() {
        isConnectedFlag.set(true)
        isConnecting.set(false)
        _connectionTransport.value = ConnectionTransport.BLE
        bleHeartbeatJob?.cancel()
        bleHeartbeatJob = null
        bleHeartbeatWatchdog?.cancel()
        bleHeartbeatWatchdog = null
        mainScope.launch {
            _connectionState.value = ConnectionState.CONNECTED
        }
    }

    private fun failBleConnection(reason: String) {
        usbLog(reason)
        isConnecting.set(false)
        _connectionTransport.value = ConnectionTransport.NONE
        mainScope.launch {
            _connectionState.value = ConnectionState.ERROR
            disconnectInternal()
        }
    }

    private fun nextBleCommandId(): Int {
        while (true) {
            val current = bleCommandCounter.get()
            val next = if (current == Int.MAX_VALUE) 1 else current + 1
            if (bleCommandCounter.compareAndSet(current, next)) {
                return current
            }
        }
    }

    private suspend fun awaitBleActiveCommandClear() {
        val previousCmdId = bleActiveCmdId
        if (previousCmdId == 0) return
        val started = System.currentTimeMillis()
        while (true) {
            val lastMs = cmdIdLastDataMs[previousCmdId] ?: break
            val now = System.currentTimeMillis()
            if (now - lastMs >= cmdIdIdleCloseMs) break
            if (now - started >= BLE_BRIDGE_ACTIVE_TIMEOUT_MS) {
                usbLog("BLE active command idle timeout id=$previousCmdId")
                break
            }
            delay(20L)
        }
        cmdIdLastDataMs.remove(previousCmdId)
        synchronized(bleBridgeStateLock) {
            blePendingCommandEnds.remove(previousCmdId)
            blePendingBridgeAcks.remove(previousCmdId)
        }
    }

    private fun buildBleBridgeCommandFrame(commandId: Int, command: String): ByteArray {
        val payload = command.toByteArray(Charsets.US_ASCII)
        val frame = ByteArray(BLE_BRIDGE_FRAME_HEADER_LEN + payload.size)
        frame[0] = BLE_BRIDGE_FRAME_MAGIC0
        frame[1] = BLE_BRIDGE_FRAME_MAGIC1
        frame[2] = BLE_BRIDGE_FRAME_VERSION
        frame[3] = BLE_BRIDGE_FRAME_TYPE_CMD.toByte()
        frame[4] = BLE_BRIDGE_STATUS_OK.toByte()
        frame[5] = 0
        frame[6] = (commandId and 0xFF).toByte()
        frame[7] = ((commandId shr 8) and 0xFF).toByte()
        frame[8] = ((commandId shr 16) and 0xFF).toByte()
        frame[9] = ((commandId shr 24) and 0xFF).toByte()
        frame[10] = (payload.size and 0xFF).toByte()
        frame[11] = ((payload.size shr 8) and 0xFF).toByte()
        payload.copyInto(frame, BLE_BRIDGE_FRAME_HEADER_LEN)
        return frame
    }

    private fun isBase64SdReadCommand(command: String): Boolean {
        val trimmed = command.trim()
        return trimmed.startsWith("sd read ", ignoreCase = true) &&
            trimmed.split(Regex("\\s+")).any { it.equals("--base64", ignoreCase = true) }
    }

    private fun handleBleBridgeAck(frame: BleBridgeFrame, ok: Boolean, pendingBytes: Int) {
        synchronized(bleBridgeStateLock) {
            val deferred = blePendingBridgeAcks.remove(frame.commandId)
            if (deferred != null) {
                deferred.complete(ok to pendingBytes)
            }
        }
    }

    private fun failPendingBleOperations() {
        blePendingWrite?.cancel()
        blePendingWrite = null
        blePendingDescriptorWrite?.cancel()
        blePendingDescriptorWrite = null
        blePendingMtuChange?.cancel()
        blePendingMtuChange = null
        synchronized(bleBridgeStateLock) {
            blePendingBridgeAcks.values.forEach { it.cancel() }
            blePendingBridgeAcks.clear()
            blePendingCommandEnds.values.forEach { it.cancel() }
            blePendingCommandEnds.clear()
        }
    }

    /**
     * Internal disconnect - must only be called while holding connectionMutex
     * Improved to handle stuck connections more gracefully
     */
    private fun disconnectInternal() {
        // Set flag first to stop read loop
        isConnectedFlag.set(false)
        isConnecting.set(false)

        // Cancel jobs immediately (don't wait for them)
        readJob?.cancel()
        readJob = null
        consumerJob?.cancel()
        consumerJob = null
        bleNotificationJob?.cancel()
        bleNotificationJob = null
        flushJob?.cancel()
        flushJob = null
        bleHeartbeatJob?.cancel()
        bleHeartbeatJob = null
        bleServiceDiscoveryJob?.cancel()
        bleServiceDiscoveryJob = null
        failPendingBleOperations()
        while (!bleNotificationChannel.isEmpty) {
            bleNotificationChannel.tryReceive()
        }

        // Close serial port first - with individual try-catch for each operation
        serialPort?.let { port ->
            try { port.setDTR(false) } catch (e: Exception) { /* ignore */ }
            try { port.setRTS(false) } catch (e: Exception) { /* ignore */ }
            try { port.close() } catch (e: Exception) { /* ignore */ }
        }

        // Close USB connection
        try { usbConnection?.close() } catch (e: Exception) { /* ignore */ }
        if (hasBluetoothConnectPermission()) {
            closeBluetoothGatt(bluetoothGatt)
        }

        // Clear references
        serialPort = null
        usbConnection = null
        serialDriver = null
        bluetoothGatt = null
        bleRxCharacteristic = null
        bleTxCharacteristic = null
        isBleTransport = false
        bleActiveCmdId = 0
        cmdIdLastDataMs.clear()
        bleHeartbeatWatchdog?.cancel()
        bleHeartbeatWatchdog = null
        currentSdReadIsBase64 = false

        // Clear buffers
        resetParsingState()

        // Clear the channel
        while (!responseChannel.isEmpty) {
            responseChannel.tryReceive()
        }

        _connectionState.value = ConnectionState.DISCONNECTED
        _connectionTransport.value = ConnectionTransport.NONE
    }

    /**
     * Force reset - use when connection is in error state
     */
    private fun forceReset() {
        try {
            disconnectInternal()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Ensure state is clean
        serialPort = null
        usbConnection = null
        serialDriver = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectionTransport.value = ConnectionTransport.NONE
    }

    /**
     * Send a command string to the device
     */
    suspend fun sendCommand(command: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConnectedFlag.get()) return@withContext false

        try {
            currentSdReadIsBase64 = isBase64SdReadCommand(command)

            // Flush any pending multiline buffer before sending a new command
            // so previous response data isn't lost
            flushMultilineBuffer()

            // If we're about to send chipinfo, arm the collector immediately
            // and collect all non-empty lines for a short window. This avoids
            // missing early fields that may arrive before the echo/header.
            if (command.trim().equals("chipinfo", ignoreCase = true)) {
                chipInfoCollector.clear()
                chipInfoCollectorActive = true
                chipInfoLastFieldTime = System.currentTimeMillis()
                chipInfoCollectAllUntil = chipInfoLastFieldTime + 1500
                chipInfoSeenCount = 0
                chipInfoLog("COLLECTOR armed (command)")
                seedChipInfoCollectorFromRecentLines()
            }

            val bridgeCommandId = if (isBleTransport) nextBleCommandId() else 0
            val commandBytes = if (isBleTransport) {
                buildBleBridgeCommandFrame(bridgeCommandId, command.trim())
            } else {
                (command + "\r\n").toByteArray(Charsets.US_ASCII)
            }
            if (isBleTransport) {
                val gatt = bluetoothGatt ?: return@withContext false
                val characteristic = bleRxCharacteristic ?: return@withContext false
                val commandId = bridgeCommandId

                val ok = bleCommandMutex.withLock {
                    awaitBleActiveCommandClear()
                    bleActiveCmdId = commandId
                    cmdIdLastDataMs[commandId] = System.currentTimeMillis()

                    val ackDeferred = CompletableDeferred<Pair<Boolean, Int>>()
                    val endDeferred = CompletableDeferred<Unit>()
                    synchronized(bleBridgeStateLock) {
                        blePendingBridgeAcks[commandId] = ackDeferred
                        blePendingCommandEnds[commandId] = endDeferred
                    }

                    val status = bleWriteCharacteristicReliable(
                        gatt = gatt,
                        characteristic = characteristic,
                        value = commandBytes,
                        writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                        label = "command=$command target=rx"
                    )
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        usbLog("BLE command write failed command=$command status=$status (${bleGattStatusName(status)})")
                        synchronized(bleBridgeStateLock) {
                            blePendingBridgeAcks.remove(commandId)
                            blePendingCommandEnds.remove(commandId)
                        }
                        return@withLock false
                    }

                    val ackResult = withTimeoutOrNull(BLE_BRIDGE_ACK_TIMEOUT_MS) {
                        ackDeferred.await()
                    }
                    if (ackResult == null) {
                        usbLog("BLE bridge ACK timed out command=$command id=$commandId")
                        synchronized(bleBridgeStateLock) {
                            blePendingBridgeAcks.remove(commandId)
                            blePendingCommandEnds.remove(commandId)
                        }
                        return@withLock false
                    }

                    val (ackOk, _) = ackResult
                    if (!ackOk) {
                        synchronized(bleBridgeStateLock) {
                            blePendingBridgeAcks.remove(commandId)
                            blePendingCommandEnds.remove(commandId)
                        }
                        return@withLock false
                    }

                    true
                }
                if (!ok) return@withContext false
            } else {
                serialPort?.write(commandBytes, 1000)
            }
            _rawOutput.tryEmit("> $command")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun waitForRxIdle(idleMs: Long = 400L, timeoutMs: Long = 4000L): Boolean {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()
                val lastRx = lastIncomingDataAtMs
                if (lastRx == 0L || now - lastRx >= idleMs) {
                    return@withContext true
                }
                if (now - start >= timeoutMs) {
                    usbLog("RX idle wait timed out idleMs=$idleMs timeoutMs=$timeoutMs")
                    return@withContext false
                }
                delay(RX_IDLE_POLL_MS)
            }
            false
        }
    }

    /**
     * Start reading from serial port
     * This runs on IO dispatcher and sends data to Channel (never blocks)
     */
    private fun startReading() {
        readLoopStartTime = System.currentTimeMillis()
        readJob = scope.launch {
            var consecutiveErrors = 0
            while (isActive && isConnectedFlag.get()) {
                try {
                    serialPort?.let { port ->
                        val bytesRead = port.read(readBuffer, 1000)
                        if (bytesRead > 0) {
                            consecutiveErrors = 0
                            readLoopCount++
                            readLoopBytes += bytesRead
                            processIncomingDataFast(readBuffer, bytesRead)
                            
                            // Log throughput every ~500 reads
                            if (readLoopCount % 500 == 0L) {
                                val elapsed = System.currentTimeMillis() - readLoopStartTime
                                val rate = if (elapsed > 0) (readLoopBytes * 1000 / elapsed) else 0
                                android.util.Log.i("SerialManager.PERF", "Throughput: ${readLoopCount} reads, ${readLoopBytes} bytes, ${rate} bytes/sec")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive && isConnectedFlag.get()) {
                        consecutiveErrors++
                        e.printStackTrace()

                        if (consecutiveErrors > 5) {
                            mainScope.launch {
                                _connectionState.value = ConnectionState.ERROR
                            }
                            isConnectedFlag.set(false)
                            break
                        }
                        delay(100)
                    }
                }
            }
        }
    }

    /**
     * Start the channel consumer that processes grouped lines and emits to response flow
     * This runs on IO dispatcher separate from the read loop
     */
    private var consumerLoopCount = 0L
    
    private fun startConsumer() {
        consumerJob = scope.launch {
            for (line in responseChannel) {
                val startNanos = System.nanoTime()
                val response = GhostSerialResponse(line)
                _responses.tryEmit(response)
                consumerLoopCount++
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                if (elapsedMs >= 3 && consumerLoopCount % 100 == 1L) {
                    android.util.Log.w("SerialManager.PERF", "consumer slow: ${elapsedMs}ms")
                }
            }
        }
    }

    private fun startBleNotificationProcessor() {
        if (bleNotificationJob?.isActive == true) return
        bleNotificationJob = scope.launch {
            for (packet in bleNotificationChannel) {
                val startNanos = System.nanoTime()
                processBleNotificationPacket(packet)
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                if (elapsedMs >= 10) {
                    android.util.Log.w("SerialManager.PERF", "ble notification slow: ${elapsedMs}ms bytes=${packet.size}")
                }
            }
        }
    }

    private fun processBleNotificationPacket(packet: ByteArray) {
        lastIncomingDataAtMs = System.currentTimeMillis()
        bleFrameBuffer.write(packet)
        val buffered = bleFrameBuffer.toByteArray()
        var offset = 0

        while (buffered.size - offset >= BLE_BRIDGE_FRAME_HEADER_LEN) {
            if (buffered[offset] != BLE_BRIDGE_FRAME_MAGIC0 ||
                buffered[offset + 1] != BLE_BRIDGE_FRAME_MAGIC1 ||
                buffered[offset + 2] != BLE_BRIDGE_FRAME_VERSION) {
                bleFallbackBuffer.write(buffered[offset].toInt() and 0xFF)
                offset += 1
                continue
            }

            if (bleFallbackBuffer.size() > 0) {
                val fallback = bleFallbackBuffer.toByteArray()
                bleFallbackBuffer.reset()
                processIncomingDataFast(fallback, fallback.size)
            }

            val frameType = buffered[offset + 3].toInt() and 0xFF
            var payloadLen =
                (buffered[offset + 10].toInt() and 0xFF) or
                ((buffered[offset + 11].toInt() and 0xFF) shl 8)

            // Some bridge firmware builds emitted DATA frames with a zero payload
            // length even though bytes followed the header. Infer that payload so
            // file downloads do not depend on the raw-byte fallback path.
            if (payloadLen == 0 &&
                (frameType == BLE_BRIDGE_FRAME_TYPE_DATA || frameType == BLE_BRIDGE_FRAME_TYPE_ERR) &&
                buffered.size - offset > BLE_BRIDGE_FRAME_HEADER_LEN) {
                val nextFrameOffset = findNextBleFrameOffset(buffered, offset + BLE_BRIDGE_FRAME_HEADER_LEN)
                val payloadEnd = if (nextFrameOffset >= 0) nextFrameOffset else buffered.size
                payloadLen = (payloadEnd - offset - BLE_BRIDGE_FRAME_HEADER_LEN).coerceAtMost(0xFFFF)
            }

            val frameLen = BLE_BRIDGE_FRAME_HEADER_LEN + payloadLen
            if (buffered.size - offset < frameLen) {
                break
            }

            val commandId =
                (buffered[offset + 6].toInt() and 0xFF) or
                ((buffered[offset + 7].toInt() and 0xFF) shl 8) or
                ((buffered[offset + 8].toInt() and 0xFF) shl 16) or
                ((buffered[offset + 9].toInt() and 0xFF) shl 24)
            val payload = buffered.copyOfRange(offset + BLE_BRIDGE_FRAME_HEADER_LEN, offset + frameLen)
            val pendingBytes = if (frameType == BLE_BRIDGE_FRAME_TYPE_ACK) {
                payloadLen
            } else {
                0
            }
            processBleBridgeFrame(
                BleBridgeFrame(
                    type = frameType,
                    status = buffered[offset + 4].toInt() and 0xFF,
                    commandId = commandId,
                    payload = payload,
                    pendingBytes = pendingBytes
                )
            )
            offset += frameLen
        }

        bleFrameBuffer.reset()
        if (offset < buffered.size) {
            bleFrameBuffer.write(buffered, offset, buffered.size - offset)
        }

        if (bleFallbackBuffer.size() >= BLE_FALLBACK_FLUSH_BYTES) {
            val fallback = bleFallbackBuffer.toByteArray()
            bleFallbackBuffer.reset()
            processIncomingDataFast(fallback, fallback.size)
        }
    }

    private fun findNextBleFrameOffset(buffer: ByteArray, start: Int): Int {
        var i = start
        while (i <= buffer.size - 3) {
            if (buffer[i] == BLE_BRIDGE_FRAME_MAGIC0 &&
                buffer[i + 1] == BLE_BRIDGE_FRAME_MAGIC1 &&
                buffer[i + 2] == BLE_BRIDGE_FRAME_VERSION) {
                return i
            }
            i++
        }
        return -1
    }

    private fun emitBleBridgeText(payload: ByteArray) {
        if (payload.isEmpty()) return
        processIncomingDataFast(payload, payload.size)
    }

    private fun processBleBridgeFrame(frame: BleBridgeFrame) {
        when (frame.type) {
            BLE_BRIDGE_FRAME_TYPE_ACK -> {
                android.util.Log.d("SerialManager", "BLE frame ACK id=${frame.commandId} status=${frame.status}")
                handleBleBridgeAck(frame, frame.status == BLE_BRIDGE_STATUS_OK, frame.pendingBytes)
            }
            BLE_BRIDGE_FRAME_TYPE_DATA -> {
                cmdIdLastDataMs[frame.commandId] = System.currentTimeMillis()
                if (frame.payload.isNotEmpty()) {
                    _bleBridgeDataPayloads.tryEmit(frame.payload.copyOf())
                    emitBleBridgeText(frame.payload)
                }
            }
            BLE_BRIDGE_FRAME_TYPE_END -> {
                android.util.Log.d("SerialManager", "BLE frame END id=${frame.commandId}")
                cmdIdLastDataMs[frame.commandId] = System.currentTimeMillis()
                synchronized(bleBridgeStateLock) {
                    blePendingCommandEnds.remove(frame.commandId)?.complete(Unit)
                }
            }
            BLE_BRIDGE_FRAME_TYPE_HAS_DATA -> {
                android.util.Log.d("SerialManager", "BLE frame HAS_DATA id=${frame.commandId} (ignored)")
            }
            BLE_BRIDGE_FRAME_TYPE_ERR -> {
                android.util.Log.d("SerialManager", "BLE frame ERR id=${frame.commandId} status=${frame.status} bytes=${frame.payload.size}")
                if (frame.payload.isNotEmpty()) {
                    emitBleBridgeText(frame.payload)
                }
                handleBleBridgeAck(frame, false, 0)
            }
            else -> {
                if (frame.payload.isNotEmpty()) {
                    emitBleBridgeText(frame.payload)
                }
            }
        }
    }

    /**
     * Periodic flush timer for multiline buffer.
     * If no new data arrives for 500ms while accumulating, flush the buffer.
     * This prevents data from getting stuck (e.g., chipinfo being the last response).
     */
    private fun startFlushTimer() {
        flushJob = scope.launch {
            while (isActive && isConnectedFlag.get()) {
                delay(500)
                // Flush normal multiline buffer
                if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
                    val elapsed = System.currentTimeMillis() - lastLineTime
                    if (elapsed >= 500) {
                        flushMultilineBuffer()
                    }
                }
                // Flush chipinfo collector independently
                if (chipInfoCollectorActive && chipInfoCollector.isNotEmpty()) {
                    val elapsed = System.currentTimeMillis() - chipInfoLastFieldTime
                    if (elapsed >= 500) {
                        val collected = chipInfoCollector.toString()
                        chipInfoCollector.clear()
                        chipInfoCollectorActive = false
                        chipInfoCollectAllUntil = 0L
                        chipInfoSeenCount = 0
                        chipInfoLog("COLLECTOR flushed (${collected.length} chars, idle ${elapsed}ms)")
                        // Build a synthetic chipinfo response the parser can recognise
                        sendToResponseChannel("Chip Information: $collected")
                    }
                }
            }
        }
    }

    /**
     * Flush any accumulated multiline data to the response channel.
     * Chipinfo is handled by its own dedicated collector — this only
     * handles AP / station / flipper / handshake / etc. multi-line groups.
     */
    private fun flushMultilineBuffer() {
        if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
            val buffer = multilineBuffer.toString()
            multilineBuffer.clear()
            isAccumulatingMultiline = false
            multilineType = null
            sendToResponseChannel(buffer)
        }
    }
    
    /**
     * Returns true when [line] looks like a chipinfo field or sub-field.
     * Used by the dedicated chipinfo collector to capture every relevant line
     * regardless of indentation or arrival order.
     */
    private fun isChipInfoField(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("Model:") ||
                t.startsWith("Revision:") ||
                t.startsWith("CPU Cores:") ||
                t.startsWith("Features:") && !t.startsWith("Features:,") ||
                t.startsWith("Free Heap:") ||
                t.startsWith("Min Free Heap:") ||
                t.startsWith("IDF Version:") ||
                t.startsWith("Build Config:") ||
                t.startsWith("Firmware:") ||
                t.startsWith("Git Commit:") ||
                t.startsWith("Enabled Features") ||
                // Sub-fields under "Enabled Features:" — short names like
                // "Display", "NFC", "BadUSB", "Infrared TX", etc.
                (chipInfoCollectorActive &&
                 t.length < 40 && !t.startsWith("[") && !t.contains(":") && t.isNotEmpty())
    }

    private fun rememberRecentLine(line: String) {
        val t = line.trim()
        if (t.isEmpty()) return
        if (recentLines.size >= 25) {
            recentLines.removeFirst()
        }
        recentLines.addLast(t)
    }

    private fun seedChipInfoCollectorFromRecentLines() {
        var seeded = 0
        for (line in recentLines) {
            if (isChipInfoField(line)) {
                if (chipInfoCollector.isNotEmpty()) chipInfoCollector.append(", ")
                chipInfoCollector.append(line.trim())
                seeded += 1
            }
        }
        if (seeded > 0) {
            chipInfoLastFieldTime = System.currentTimeMillis()
            chipInfoLog("COLLECTOR seeded ($seeded)")
        }
    }

    /**
     * Fast data processing - avoids string allocations where possible
     * Every line goes to rawOutput immediately for terminal display.
     * Multi-line grouping only happens for the response channel (parsed data).
     * Binary mode: When SD:READ:LENGTH: is detected, switches to raw byte collection.
     */
    private fun processIncomingDataFast(buffer: ByteArray, length: Int) {
        val startNanos = System.nanoTime()
        lastIncomingDataAtMs = System.currentTimeMillis()
        
        if (isBinaryMode) {
            processBinaryData(buffer, length)
            perfLog("processIncomingDataFast_binary", System.nanoTime() - startNanos)
            return
        }

        for (i in 0 until length) {
            val byte = buffer[i]

            when (byte) {
                '\r'.code.toByte(), '\n'.code.toByte() -> {
                    if (lineBuffer.isNotEmpty()) {
                        val line = lineBuffer.toString()
                        lineBuffer.clear()
                        
                        // App downloads request --base64, so their SD data stays line-oriented.
                        if (line.startsWith("SD:READ:LENGTH:") && !currentSdReadIsBase64) {
                            processLine(line)
                            // Switch to binary mode after the LENGTH line.
                            // Any bytes remaining in this buffer after the newline are
                            // already binary payload — pass them to processBinaryData.
                            isBinaryMode = true
                            binaryAccumulator.reset()
                            terminatorMatchPos = 0
                            val remaining = i + 1
                            if (remaining < length) {
                                processBinaryData(buffer, length, offset = remaining)
                            }
                            return
                        } else {
                            processLine(line)
                        }
                    }
                }
                else -> {
                    lineBuffer.append(byte.toInt().toChar())
                }
            }
        }
        perfLog("processIncomingDataFast", System.nanoTime() - startNanos, "bytes=$length")
    }

    /**
     * Process binary data during SD file transfers.
     * Collects raw bytes until terminator "\nSD:READ:END:" is detected.
     * [offset] lets us start mid-buffer when switching from line mode on the same read.
     */
    private fun processBinaryData(buffer: ByteArray, length: Int, offset: Int = 0) {
        for (i in offset until length) {
            val byte = buffer[i]

            // Check if this byte matches the next expected terminator byte
            if (byte == binaryTerminator[terminatorMatchPos]) {
                terminatorMatchPos++
                
                if (terminatorMatchPos == binaryTerminator.size) {
                    // Complete terminator found - emit the binary chunk
                    val chunkData = binaryAccumulator.toByteArray()
                    binaryChannel.trySend(chunkData)
                    
                    // Reset binary mode
                    isBinaryMode = false
                    binaryAccumulator.reset()
                    terminatorMatchPos = 0
                    
                    // Process remaining bytes in this buffer as lines
                    if (i + 1 < length) {
                        processIncomingDataFast(buffer.copyOfRange(i + 1, length), length - i - 1)
                    }
                    return
                }
            } else {
                // Byte doesn't match terminator
                if (terminatorMatchPos > 0) {
                    // We had a partial match - flush those bytes to accumulator
                    for (j in 0 until terminatorMatchPos) {
                        binaryAccumulator.write(binaryTerminator[j].toInt())
                    }
                    terminatorMatchPos = 0
                    
                    // Re-check this byte against first terminator byte
                    if (byte == binaryTerminator[0]) {
                        terminatorMatchPos = 1
                        continue
                    }
                }
                // Add this byte to accumulator
                binaryAccumulator.write(byte.toInt())
            }
        }
    }

    /**
     * Process a complete line from serial.
     * 1. Strip ANSI codes and prompt prefix
     * 2. Emit to rawOutput immediately (terminal sees everything, indentation preserved)
     * 3. Feed into multi-line state machine for parsed responses only
     */
    private fun processLine(line: String) {
        val startNanos = System.nanoTime()
        
        // Strip ANSI escape codes efficiently
        var cleanLine = stripBridgePrefix(stripAnsiFast(line))

        // Strip prompt prefix
        when {
            cleanLine.startsWith("ghost-cli>") -> {
                cleanLine = cleanLine.removePrefix("ghost-cli>").trim()
            }
            cleanLine.startsWith("> ") -> {
                val afterPrompt = cleanLine.removePrefix("> ")
                if (afterPrompt.isNotBlank()) {
                    cleanLine = afterPrompt.trim()
                }
            }
        }

        if (cleanLine.isEmpty()) {
            // Flush multiline buffer on empty lines (but never chipinfo — it has its own collector)
            if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
                flushMultilineBuffer()
            }
            return
        }

        // Always emit to raw output for terminal display — indentation preserved
        _rawOutput.tryEmit(cleanLine)

        val trimmedLine = cleanLine.trim()
        if (isBridgeMetadataLine(trimmedLine)) {
            return
        }
        if (trimmedLine == "SD:OK" || trimmedLine.startsWith("SD:ERR:")) {
            currentSdReadIsBase64 = false
        }

        // Keep a short history of recent lines in case chipinfo output
        // arrives before the collector is armed.
        rememberRecentLine(cleanLine)

        // ── Chipinfo collector (independent of multiline state machine) ──
        // Use markers [CHIPINFO_START] and [CHIPINFO_END] for robust parsing
        val isChipInfoStartMarker = trimmedLine.startsWith("[CHIPINFO_START]")
        val isChipInfoEndMarker = trimmedLine.startsWith("[CHIPINFO_END]")
        val isChipInfoTrigger = trimmedLine.equals("chipinfo", ignoreCase = true) ||
            trimmedLine.startsWith("Chip Information") || isChipInfoStartMarker
        val isChipInfoData = isChipInfoField(cleanLine)
        val now = System.currentTimeMillis()
        val collectAllWindow = chipInfoCollectorActive && now <= chipInfoCollectAllUntil

        // Handle [CHIPINFO_END] - flush immediately
        if (isChipInfoEndMarker && chipInfoCollectorActive && chipInfoCollector.isNotEmpty()) {
            val collected = chipInfoCollector.toString()
            chipInfoCollector.clear()
            chipInfoCollectorActive = false
            chipInfoCollectAllUntil = 0L
            chipInfoSeenCount = 0
            chipInfoLog("COLLECTOR flushed (${collected.length} chars, end marker)")
            sendToResponseChannel("Chip Information: $collected")
            return
        }

        if ((isChipInfoTrigger || isChipInfoData) && !chipInfoCollectorActive) {
            chipInfoCollector.clear()
            chipInfoCollectorActive = true
            chipInfoLastFieldTime = now
            chipInfoCollectAllUntil = now + 1500
            chipInfoSeenCount = 0
            chipInfoLog("COLLECTOR armed (marker: ${isChipInfoStartMarker}, auto)")
            seedChipInfoCollectorFromRecentLines()
        }

        val shouldCollectLine = chipInfoCollectorActive &&
            !isChipInfoTrigger &&
            !isChipInfoEndMarker &&
            trimmedLine.isNotEmpty() &&
            (isChipInfoData || collectAllWindow)

        if (collectAllWindow && chipInfoSeenCount < 20) {
            chipInfoSeenCount += 1
            chipInfoLog("SEEN: '${trimmedLine.take(40)}'")
        }

        if (shouldCollectLine) {
            if (chipInfoCollector.isNotEmpty()) chipInfoCollector.append(", ")
            chipInfoCollector.append(trimmedLine)
            chipInfoLastFieldTime = now
            chipInfoLog("COLLECT: '${trimmedLine.take(40)}' (buf=${chipInfoCollector.length})")
        }

        // ── Normal multi-line grouping for everything else ──
        lastLineTime = System.currentTimeMillis()
        val lineType = detectLineTypeFast(cleanLine)

        when (lineType) {
            // Chipinfo triggers no longer participate in the multiline state machine
            LineType.CHIP_INFO_START -> {
                // Already handled above by the collector — skip
            }
            LineType.AP_START, LineType.FLIPPER_START, LineType.AIRTAG_START,
            LineType.STATION_START, LineType.GATT_START,
            LineType.TRACK_HEADER_START -> {
                // Flush any previous accumulation
                if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
                    sendToResponseChannel(multilineBuffer.toString())
                }
                multilineBuffer.clear()
                multilineBuffer.append(cleanLine)
                isAccumulatingMultiline = true
                multilineType = lineType
            }
            LineType.HANDSHAKE_START -> {
                if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
                    sendToResponseChannel(multilineBuffer.toString())
                }
                multilineBuffer.clear()
                multilineBuffer.append(cleanLine)
                isAccumulatingMultiline = true
                multilineType = lineType
            }
            LineType.HANDSHAKE_CONTINUATION -> {
                if (isAccumulatingMultiline) {
                    multilineBuffer.append(", ").append(cleanLine.trim())
                } else {
                    sendToResponseChannel(cleanLine.trim())
                }
            }
            LineType.WIFI_STATUS_START -> {
                if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
                    sendToResponseChannel(multilineBuffer.toString())
                }
                multilineBuffer.clear()
                multilineBuffer.append(cleanLine)
                isAccumulatingMultiline = true
                multilineType = lineType
            }
            LineType.WIFI_STATUS_CONTINUATION -> {
                if (isAccumulatingMultiline) {
                    multilineBuffer.append("\n").append(cleanLine.trim())
                    // Check for end marker - flush immediately
                    if (cleanLine.contains("=== END STATUS ===")) {
                        val buffer = multilineBuffer.toString()
                        multilineBuffer.clear()
                        isAccumulatingMultiline = false
                        multilineType = null
                        sendToResponseChannel(buffer)
                    }
                } else {
                    sendToResponseChannel(cleanLine.trim())
                }
            }
            LineType.GPS_START -> {
                // Flush any previous accumulation
                if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
                    sendToResponseChannel(multilineBuffer.toString())
                }
                multilineBuffer.clear()
                multilineBuffer.append(cleanLine)
                isAccumulatingMultiline = true
                multilineType = lineType
            }
            LineType.GPS_CONTINUATION -> {
                if (isAccumulatingMultiline) {
                    multilineBuffer.append("\n").append(cleanLine.trim())
                } else {
                    sendToResponseChannel(cleanLine.trim())
                }
            }
            LineType.CONTINUATION -> {
                if (isAccumulatingMultiline) {
                    multilineBuffer.append(", ").append(cleanLine.trim())
                } else {
                    sendToResponseChannel(cleanLine.trim())
                }
            }
            LineType.IR_REMOTE, LineType.IR_BUTTON -> {
                if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
                    sendToResponseChannel(multilineBuffer.toString())
                    multilineBuffer.clear()
                    isAccumulatingMultiline = false
                    multilineType = null
                }
                sendToResponseChannel(cleanLine)
            }
            else -> {
                if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
                    sendToResponseChannel(multilineBuffer.toString())
                    multilineBuffer.clear()
                    isAccumulatingMultiline = false
                    multilineType = null
                    sendToResponseChannel(cleanLine)
                } else {
                    sendToResponseChannel(cleanLine)
                }
            }
        }
        perfLog("processLine", System.nanoTime() - startNanos)
    }

    /**
     * Send grouped/parsed line to response channel for structured parsing.
     * Trimmed since parsers don't need indentation.
     */
    private fun sendToResponseChannel(line: String) {
        responseChannel.trySend(line.trim())
    }

    /**
     * Fast ANSI stripping without regex
     */
    private fun stripAnsiFast(input: String): String {
        val result = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\u001b' && i + 1 < input.length && input[i + 1] == '[') {
                i += 2
                while (i < input.length) {
                    val ch = input[i]
                    if (ch in 'A'..'Z' || ch in 'a'..'z' || ch == '~') {
                        i++
                        break
                    }
                    i++
                }
            } else if (c >= ' ' || c == '\t') {
                result.append(c)
                i++
            } else {
                i++
            }
        }
        return result.toString()
    }

    private fun stripBridgePrefix(input: String): String {
        val prefix = "ESP Comm Response:"
        if (!input.startsWith(prefix)) return input

        val withoutPrefix = input.removePrefix(prefix)
        return if (withoutPrefix.startsWith(" ")) {
            withoutPrefix.substring(1)
        } else {
            withoutPrefix
        }
    }

    private fun isBridgeMetadataLine(trimmedLine: String): Boolean {
        return trimmedLine.startsWith("Received command from peer:") ||
            trimmedLine.startsWith("Executing received command:")
    }

    /**
     * Fast line type detection without regex
     */
    private fun detectLineTypeFast(line: String): LineType {
        if (line.startsWith("Chip Information") || line.trim().equals("chipinfo", ignoreCase = true)) {
            return LineType.CHIP_INFO_START
        }

        val trimmed = line.trim()

        // IR remote files - check trimmed since firmware may indent
        if (trimmed.startsWith("[") && (trimmed.contains(".ir") || trimmed.contains(".json"))) {
            if (!trimmed.contains("SSID:") && !trimmed.contains("Flipper", ignoreCase = true)
                && !trimmed.contains("AirTag") && !trimmed.contains("Station MAC:")
                && !trimmed.contains("Name:")) {
                return LineType.IR_REMOTE
            }
        }

        // IR buttons - firmware outputs like "  [0] Power (NEC)" with leading spaces
        // Must check BEFORE CONTINUATION to avoid misclassifying
        if (trimmed.startsWith("[") && trimmed.contains("]") && !trimmed.contains(".ir") && !trimmed.contains(".json")) {
            // Check if it looks like an IR button: [N] name (protocol) or [N] name
            val afterBracket = trimmed.substringAfter("]", "").trim()
            if (afterBracket.isNotEmpty() && !afterBracket.contains("SSID:") && !afterBracket.contains("Station MAC:")
                && !afterBracket.contains("Name:") && !afterBracket.contains("Flipper") && !afterBracket.contains("AirTag")
                && !afterBracket.contains("RSSI:")) {
                return LineType.IR_BUTTON
            }
        }

        if (line.startsWith("[") && line.contains("SSID:")) {
            return LineType.AP_START
        }

        if (line.startsWith("[") && line.contains("Flipper", ignoreCase = true) && line.contains("Found")) {
            return LineType.FLIPPER_START
        }

        if (line.startsWith("[") && line.contains("AirTag") && line.contains("Found")) {
            return LineType.AIRTAG_START
        }

        if (line.startsWith("New Station:") || (line.startsWith("[") && line.contains("Station MAC:")) || line.startsWith("Station:")) {
            return LineType.STATION_START
        }

        if (line.startsWith("[") && line.contains("Name:") && !line.contains("SSID:")) {
            return LineType.GATT_START
        }

        // GATT tracking header - "=== Tracking Device ===" starts a multi-line block
        if (trimmed.startsWith("===") && trimmed.contains("Tracking Device", ignoreCase = true)) {
            return LineType.TRACK_HEADER_START
        }

        // Handshake detection - "Handshake found!" starts a multi-line block
        if (trimmed.startsWith("Handshake found", ignoreCase = true)) {
            return LineType.HANDSHAKE_START
        }

        // Handshake continuation - AP= and Pair= lines
        if (trimmed.startsWith("AP=") || trimmed.startsWith("Pair=")) {
            return LineType.HANDSHAKE_CONTINUATION
        }
        
        // WiFi Status header - "=== WIFI STATUS ===" starts a multi-line block
        if (trimmed.contains("=== WIFI STATUS ===")) {
            return LineType.WIFI_STATUS_START
        }
        
        // WiFi Status continuation - key=value lines and end marker
        if (trimmed.contains("=") && !trimmed.startsWith("[") && 
            (trimmed.startsWith("connected=") || trimmed.startsWith("has_saved_network=") ||
             trimmed.startsWith("connected_ssid=") || trimmed.startsWith("connected_rssi=") ||
             trimmed.startsWith("connected_bssid=") || trimmed.startsWith("connected_channel=") ||
             trimmed.startsWith("saved_ssid=") || trimmed.contains("=== END STATUS ==="))) {
            return LineType.WIFI_STATUS_CONTINUATION
        }

        // GPS info start - "GPS Info" or "Acquiring GPS" starts a multiline block
        if (trimmed.startsWith("GPS Info") || trimmed == "Acquiring GPS") {
            return LineType.GPS_START
        }

        // GPS continuation - lines that are part of GPS output
        if (isAccumulatingMultiline && multilineType == LineType.GPS_START) {
            if (trimmed.startsWith("Fix:") || trimmed.startsWith("Sats:") ||
                trimmed.startsWith("Lat:") || trimmed.startsWith("Long:") ||
                trimmed.startsWith("Alt:") || trimmed.startsWith("Speed:") ||
                trimmed.startsWith("Direction:") || trimmed.startsWith("HDOP:") ||
                trimmed.startsWith("Acquiring GPS")) {
                return LineType.GPS_CONTINUATION
            }
        }

        // Wardrive start - similar multiline format to GPS
        if (trimmed.startsWith("Wardrive Info") || trimmed.startsWith("Wardrive Status")) {
            return LineType.GPS_START  // Reuse GPS_START for wardrive multiline
        }

        // Wardrive continuation - lines that are part of wardrive output
        if (isAccumulatingMultiline && multilineType == LineType.GPS_START && 
            (trimmed.startsWith("APs:") || trimmed.startsWith("Logged:") ||
             trimmed.startsWith("GPS Fix:") || trimmed.startsWith("Channel:") ||
             trimmed.startsWith("Uptime:") || trimmed.startsWith("Pending:") ||
             trimmed.startsWith("BLE:") || trimmed.startsWith("Accuracy:"))) {
            return LineType.GPS_CONTINUATION
        }

        // Wardrive heartbeat (new firmware format) - "GPS: Locked", "GPS: No Fix" etc.
        // NOT "GPS Info" (that's the gpsinfo command output)
        if (trimmed.startsWith("GPS:") && !trimmed.startsWith("GPS Info")) {
            return LineType.GPS_START
        }

        // Continuation lines - but NOT if trimmed starts with [ (those are IR buttons)
        if ((line.startsWith("  ") || line.startsWith("\t") || line.startsWith(" ")) && !trimmed.startsWith("[")) {
            return LineType.CONTINUATION
        }

        return LineType.SINGLE
    }

    private enum class LineType {
        AP_START, FLIPPER_START, AIRTAG_START, STATION_START, GATT_START, CHIP_INFO_START, TRACK_HEADER_START, IR_REMOTE, IR_BUTTON, HANDSHAKE_START, HANDSHAKE_CONTINUATION, WIFI_STATUS_START, WIFI_STATUS_CONTINUATION, GPS_START, GPS_CONTINUATION, CONTINUATION, SINGLE
    }

    /**
     * Check if device is connected
     */
    fun isConnected(): Boolean = isConnectedFlag.get()

    /**
     * Clean up resources
     */
    fun destroy() {
        disconnectInternal()
        responseChannel.close()
        scope.cancel()
        mainScope.cancel()
    }
}

/**
 * Serial response wrapper with optimized type detection
 */
data class GhostSerialResponse(
    val raw: String
) {
    enum class ResponseType {
        UNKNOWN,
        ACCESS_POINT,
        BLE_DEVICE,
        FLIPPER_DEVICE,
        AIRTAG_DEVICE,
        GATT_DEVICE,
        GATT_SERVICE,
        STATION,
        NFC_TAG,
        GPS_POSITION,
        SD_ENTRY,
        ERROR,
        SUCCESS,
        STATUS,
        PROMPT,
        AERIAL_DEVICE,
        PORTAL_CREDS,
        IR_LEARNED,
        IR_LEARN_SAVED,
        IR_LEARN_STATUS,
        IR_DAZZLER,
        IR_REMOTE,
        IR_BUTTON,
        GHOSTESP_OK,
        SETTING_VALUE,
        DEVICE_INFO,
        TRACK_DATA,
        TRACK_HEADER,
        FLIPPER_TRACK_DATA,
        HANDSHAKE,
        PCAP_FILE,
        WIFI_CONNECTION,
        WIFI_STATUS,
        WARDDRIVE_STATS
    }

    // Lazy evaluation of type for performance
    val type: ResponseType by lazy {
        detectTypeFast()
    }

    private fun detectTypeFast(): ResponseType {
        return when {
            raw.startsWith("[") && raw.contains("SSID:") -> ResponseType.ACCESS_POINT

            raw.contains("Flipper") && raw.contains("Found") -> ResponseType.FLIPPER_DEVICE

            raw.contains("AirTag") && raw.contains("Found") -> ResponseType.AIRTAG_DEVICE

            raw.startsWith("[") && raw.contains("Name:") && raw.contains("MAC:") && !raw.contains("SSID:") -> ResponseType.GATT_DEVICE

            raw.startsWith("Service:") && raw.contains("handles") -> ResponseType.GATT_SERVICE

            raw.startsWith("New Station:") || raw.contains("Station MAC:") || (raw.contains("Station:") && raw.contains("Associated AP:")) -> ResponseType.STATION

            raw.startsWith("BLE:") -> ResponseType.BLE_DEVICE

            raw.contains("NFC Tag") -> ResponseType.NFC_TAG

            raw.contains("Wardrive Info") && (raw.contains("APs:") || raw.contains("Logged:")) -> ResponseType.WARDDRIVE_STATS

            // Wardrive heartbeat new format: "GPS: Locked\nAPs: 9\nSats: 16/9\n..." or BLE: "GPS: Locked\nBLE: 16\n..."
            raw.startsWith("GPS:") && (raw.contains("APs:") || raw.contains("BLE:")) -> ResponseType.WARDDRIVE_STATS

            raw.contains("GPS Info") -> ResponseType.GPS_POSITION

            raw.contains("Wardrive:") && raw.contains("ap=") && raw.contains("logged=") -> ResponseType.WARDDRIVE_STATS

            raw.contains("Lat:") && raw.contains("Lon:") -> ResponseType.GPS_POSITION

            raw.contains("Wardrive:") && raw.contains("ap=") -> ResponseType.WARDDRIVE_STATS

            raw.contains("GPS Info") || raw.contains("Acquiring GPS") -> ResponseType.GPS_POSITION

            raw.startsWith("SD:") -> ResponseType.SD_ENTRY

            raw.startsWith("ERROR:") -> ResponseType.ERROR

            raw.startsWith("OK:") -> ResponseType.SUCCESS

            raw.startsWith(">") -> ResponseType.PROMPT

            raw.startsWith("[") && raw.contains("MAC:") && raw.contains("Type:") -> ResponseType.AERIAL_DEVICE

            raw.contains("Captured credentials:") -> ResponseType.PORTAL_CREDS

            raw.contains("Captured:") && raw.contains("A:") && raw.contains("C:") -> ResponseType.IR_LEARNED

            raw.contains("Captured RAW signal") -> ResponseType.IR_LEARNED

            raw.contains("Saved to") && raw.contains(".ir") -> ResponseType.IR_LEARN_SAVED

            raw.contains("Waiting for IR signal") || raw.contains("IR learn task started") -> ResponseType.IR_LEARN_STATUS

            raw.contains("Timeout, no signal received") -> ResponseType.IR_LEARN_STATUS

            raw.startsWith("IR_DAZZLER:") -> ResponseType.IR_DAZZLER

            raw.trim().startsWith("[") && raw.trim().contains(".ir") -> ResponseType.IR_REMOTE
            raw.trim().startsWith("[") && raw.trim().contains(".json") && raw.contains("IR files") -> ResponseType.IR_REMOTE

            (raw.contains("#####") || (raw.startsWith("[") && raw.contains("RSSI:") && raw.contains("Min:") && raw.contains("Max:"))) && raw.contains("dBm") -> ResponseType.TRACK_DATA

            raw.contains("Tracking Flipper", ignoreCase = true) && raw.contains("RSSI") && raw.contains("dBm") -> ResponseType.FLIPPER_TRACK_DATA

            raw.trim().startsWith("[") && !raw.contains(".ir") && !raw.contains(".json") -> ResponseType.IR_BUTTON

            raw == "GHOSTESP_OK" -> ResponseType.GHOSTESP_OK

            raw.contains("Chip Information") ||
                (raw.contains("Model:") && raw.contains("IDF Version:") && raw.contains("CPU Cores:")) -> ResponseType.DEVICE_INFO

            raw.contains(" = ") && !raw.startsWith("[") -> ResponseType.SETTING_VALUE

            (raw.contains("tracking") || raw.contains("Tracking")) && raw.contains("===") -> ResponseType.TRACK_HEADER

            raw.contains("Handshake found", ignoreCase = true) -> ResponseType.HANDSHAKE

            raw.contains("PCAP") && raw.contains(".pcap") -> ResponseType.PCAP_FILE

            raw.contains("Got IP:") || 
                raw.contains("WiFi Connected", ignoreCase = true) ||
                raw.contains("WiFi Disconnected", ignoreCase = true) ||
                raw.contains("Attempting", ignoreCase = true) && raw.contains("connection", ignoreCase = true) -> ResponseType.WIFI_CONNECTION

            raw.contains("=== WIFI STATUS ===") || 
                (raw.contains("connected=") && raw.contains("has_saved_network=")) -> ResponseType.WIFI_STATUS

            else -> ResponseType.STATUS
        }
    }
}
