package com.example.ghostespcompanion.data.serial

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

internal object BleBridgeProtocol {
    const val DEFAULT_MTU = 23
    const val MAX_COMMAND_BYTES = 250
    const val MAX_FRAME_PAYLOAD_BYTES = 512
    const val HEADER_LENGTH = 12
    const val FLAG_FIRST = 0x01
    const val FLAG_MORE = 0x02
    private const val ATT_WRITE_OVERHEAD = 3
    private const val TYPE_COMMAND = 1

    data class DecodedFrame(
        val type: Int,
        val status: Int,
        val commandId: Int,
        val payload: ByteArray
    )

    data class DecodeResult(val frames: List<DecodedFrame>, val fallback: ByteArray)

    class Decoder {
        private val buffer = ByteArrayOutputStream()

        fun feed(bytes: ByteArray): DecodeResult {
            buffer.write(bytes)
            val input = buffer.toByteArray()
            val frames = mutableListOf<DecodedFrame>()
            val fallback = ByteArrayOutputStream()
            var offset = 0

            while (input.size - offset >= 3) {
                if (input[offset] != 0x47.toByte() || input[offset + 1] != 0x42.toByte() ||
                    input[offset + 2] != 0x01.toByte()) {
                    fallback.write(input[offset].toInt() and 0xFF)
                    offset++
                    continue
                }
                if (input.size - offset < HEADER_LENGTH) break

                val payloadLength = (input[offset + 10].toInt() and 0xFF) or
                    ((input[offset + 11].toInt() and 0xFF) shl 8)
                val type = input[offset + 3].toInt() and 0xFF
                if (type !in 1..7 || payloadLength > MAX_FRAME_PAYLOAD_BYTES) {
                    val corruptLength = HEADER_LENGTH + payloadLength
                    offset += if (payloadLength <= MAX_FRAME_PAYLOAD_BYTES && input.size - offset >= corruptLength) {
                        corruptLength
                    } else {
                        HEADER_LENGTH
                    }
                    continue
                }
                val frameLength = HEADER_LENGTH + payloadLength
                if (input.size - offset < frameLength) break

                val commandId = (input[offset + 6].toInt() and 0xFF) or
                    ((input[offset + 7].toInt() and 0xFF) shl 8) or
                    ((input[offset + 8].toInt() and 0xFF) shl 16) or
                    ((input[offset + 9].toInt() and 0xFF) shl 24)
                frames += DecodedFrame(
                    type = type,
                    status = input[offset + 4].toInt() and 0xFF,
                    commandId = commandId,
                    payload = input.copyOfRange(offset + HEADER_LENGTH, offset + frameLength)
                )
                offset += frameLength
            }

            val trailing = input.size - offset
            if (trailing in 1..2) {
                val prefixBytes = when {
                    trailing == 2 && input[offset] == 0x47.toByte() && input[offset + 1] == 0x42.toByte() -> 2
                    input[input.lastIndex] == 0x47.toByte() -> 1
                    else -> 0
                }
                val fallbackBytes = trailing - prefixBytes
                if (fallbackBytes > 0) {
                    fallback.write(input, offset, fallbackBytes)
                    offset += fallbackBytes
                }
            }

            buffer.reset()
            if (offset < input.size) buffer.write(input, offset, input.size - offset)
            return DecodeResult(frames, fallback.toByteArray())
        }

        fun reset() = buffer.reset()
    }

    fun commandFrames(commandId: Int, payload: ByteArray, mtu: Int): List<ByteArray> {
        require(payload.isNotEmpty()) { "Command must not be empty" }
        require(payload.size <= MAX_COMMAND_BYTES) { "Command exceeds $MAX_COMMAND_BYTES bytes" }
        val chunkSize = mtu.coerceAtLeast(DEFAULT_MTU) - ATT_WRITE_OVERHEAD - HEADER_LENGTH
        require(chunkSize > 0) { "MTU is too small for bridge header" }

        if (payload.size <= chunkSize) {
            return listOf(frame(commandId, payload, 0))
        }

        return (payload.indices step chunkSize).map { offset ->
            val end = (offset + chunkSize).coerceAtMost(payload.size)
            val hasMore = end < payload.size
            val flags = (if (offset == 0) FLAG_FIRST else 0) or
                (if (hasMore) FLAG_MORE else 0)
            frame(commandId, payload.copyOfRange(offset, end), flags)
        }
    }

    private fun frame(commandId: Int, payload: ByteArray, flags: Int): ByteArray {
        val frame = ByteArray(HEADER_LENGTH + payload.size)
        frame[0] = 0x47
        frame[1] = 0x42
        frame[2] = 0x01
        frame[3] = TYPE_COMMAND.toByte()
        frame[4] = 0
        frame[5] = flags.toByte()
        frame[6] = (commandId and 0xFF).toByte()
        frame[7] = ((commandId ushr 8) and 0xFF).toByte()
        frame[8] = ((commandId ushr 16) and 0xFF).toByte()
        frame[9] = ((commandId ushr 24) and 0xFF).toByte()
        frame[10] = (payload.size and 0xFF).toByte()
        frame[11] = ((payload.size ushr 8) and 0xFF).toByte()
        payload.copyInto(frame, HEADER_LENGTH)
        return frame
    }
}

enum class BleConnectionFailure {
    NONE,
    PERMISSION_REQUIRED,
    BLUETOOTH_DISABLED,
    UNSUPPORTED,
    INVALID_ADDRESS,
    GATT_UNAVAILABLE,
    CONNECTION_FAILED,
    TIMEOUT,
    HANDSHAKE_FAILED
}

internal data class BleAttemptResult(
    val connected: Boolean,
    val failure: BleConnectionFailure = BleConnectionFailure.NONE,
    val retryable: Boolean = false
)

internal class BleConnectAttemptTracker {
    class Attempt internal constructor(
        val token: Long,
        internal val completion: CompletableDeferred<BleAttemptResult>
    )

    private val nextToken = AtomicLong(0)
    private var active: Attempt? = null

    @Synchronized
    fun begin(): Attempt {
        active?.completion?.complete(BleAttemptResult(false, BleConnectionFailure.CONNECTION_FAILED))
        return Attempt(nextToken.incrementAndGet(), CompletableDeferred()).also { active = it }
    }

    @Synchronized
    fun isCurrent(attempt: Attempt): Boolean = active === attempt

    @Synchronized
    fun currentToken(): Long? = active?.token

    @Synchronized
    fun complete(
        attempt: Attempt,
        result: BleAttemptResult,
        keepActive: Boolean = false,
        beforeComplete: () -> Unit = {}
    ): Boolean {
        if (active !== attempt || attempt.completion.isCompleted) return false
        beforeComplete()
        val completed = attempt.completion.complete(result)
        if (completed && !keepActive) active = null
        return completed
    }

    @Synchronized
    fun clear(attempt: Attempt? = null) {
        if (attempt != null && active !== attempt) return
        active?.completion?.complete(BleAttemptResult(false, BleConnectionFailure.CONNECTION_FAILED))
        active = null
    }
}

internal fun shouldRetryBleConnection(result: BleAttemptResult, retryIndex: Int): Boolean =
    !result.connected && result.retryable && retryIndex == 0

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
    private val bleAttemptTracker = BleConnectAttemptTracker()
    private val bleGattLock = Any()
    private val bleCommandCounter = AtomicInteger(1)
    @Volatile private var bleMtu = BleBridgeProtocol.DEFAULT_MTU

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionTransport = MutableStateFlow(ConnectionTransport.NONE)
    val connectionTransport: StateFlow<ConnectionTransport> = _connectionTransport.asStateFlow()

    private val _lastBleConnectionFailure = MutableStateFlow(BleConnectionFailure.NONE)
    val lastBleConnectionFailure: StateFlow<BleConnectionFailure> = _lastBleConnectionFailure.asStateFlow()

    // Channel for parsed/grouped responses (multi-line accumulation applied here)
    // UNLIMITED capacity ensures we NEVER block the serial read loop and NEVER lose data
    private val responseChannel = Channel<String>(Channel.UNLIMITED)
    private data class BleNotificationPacket(val attemptToken: Long, val value: ByteArray)
    private val bleNotificationChannel = Channel<BleNotificationPacket>(Channel.UNLIMITED)

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
    @Volatile private var lastTextDataAtMs = 0L
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
    private val bleFrameDecoder = BleBridgeProtocol.Decoder()
    @Volatile private var currentSdReadIsBase64 = false

    private val bleCommandMutex = Mutex()
    private val bleBridgeStateLock = Any()
    private val blePendingCommandEnds = linkedMapOf<Int, CompletableDeferred<Unit>>()
    private val blePendingBridgeAcks = mutableMapOf<Int, CompletableDeferred<Pair<Boolean, Int>>>()
    private val cmdIdLastDataMs = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private var bleActiveCmdId: Int = 0
    @Volatile private var bleWdStreamCmdId: Int = 0
    private val bleWdStreamLineBuffer = StringBuilder(512)
    private val cmdIdIdleCloseMs = 750L

    // Atomic flag for connection status
    private val isConnectedFlag = AtomicBoolean(false)

    // Mutex to prevent concurrent connect/disconnect races
    private val connectionMutex = Mutex()
    private val connectionRequestMutex = Mutex()
    private val usbPermissionMutex = Mutex()
    private val bleWriteMutex = Mutex()
    private val usbPermissionRequestId = AtomicInteger(0)

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
        private const val BLE_CONNECT_ATTEMPT_TIMEOUT_MS = 30000L
        private const val BLE_RECONNECT_DELAY_MS = 300L
        private const val RX_IDLE_POLL_MS = 50L
        /** Stock GhostESP firmware normally uses one of these rates. */
        private val PROBE_BAUD_RATES = listOf(115200, 460800)
        private const val USB_PERMISSION_TIMEOUT_MS = 60_000L
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

    private fun createBleGattCallback(attempt: BleConnectAttemptTracker.Attempt) = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            usbLog("BLE onConnectionStateChange status=$status (${bleGattStatusName(status)}) newState=$newState (${bleStateName(newState)})")
            if (!acceptBleCallback(attempt, gatt)) {
                usbLog("BLE ignoring stale connection callback token=${attempt.token}")
                closeBluetoothGatt(gatt)
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothGatt.STATE_DISCONNECTED) {
                failBleAttempt(attempt, gatt, BleConnectionFailure.CONNECTION_FAILED, "BLE connection failed status=$status", true)
                return
            }
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                bleServiceDiscoveryJob?.cancel()
                bleServiceDiscoveryJob = scope.launch {
                    delay(BLE_DISCOVER_SERVICES_DELAY_MS)
                    if (!acceptBleCallback(attempt, gatt)) {
                        return@launch
                    }

                    if (!hasBluetoothConnectPermission()) {
                        failBleAttempt(attempt, gatt, BleConnectionFailure.PERMISSION_REQUIRED, "BLE connect permission missing")
                        return@launch
                    }

                    val started = try {
                        gatt.discoverServices()
                    } catch (e: Exception) {
                        usbLog("BLE discoverServices exception: ${e.message ?: e.javaClass.simpleName}")
                        false
                    }

                    if (!started) {
                        failBleAttempt(attempt, gatt, BleConnectionFailure.HANDSHAKE_FAILED, "BLE discoverServices dispatch failed", true)
                        return@launch
                    }

                    usbLog("BLE discoverServices dispatched")
                    delay(BLE_DISCOVERY_TIMEOUT_MS)
                    if (acceptBleCallback(attempt, gatt) && isConnecting.get()) {
                        failBleAttempt(attempt, gatt, BleConnectionFailure.TIMEOUT, "BLE service discovery timed out", true)
                    }
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                if (isConnecting.get()) {
                    failBleAttempt(
                        attempt,
                        gatt,
                        BleConnectionFailure.CONNECTION_FAILED,
                        "BLE disconnected while connecting status=$status",
                        retryable = true
                    )
                } else {
                    bleAttemptTracker.clear(attempt)
                    scope.launch { connectionMutex.withLock { disconnectInternal() } }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!acceptBleCallback(attempt, gatt)) return
            usbLog("BLE onServicesDiscovered status=$status (${bleGattStatusName(status)})")
            bleServiceDiscoveryJob?.cancel()
            bleServiceDiscoveryJob = null
            val service: BluetoothGattService? = try {
                gatt.getService(BleBridgeConstants.SERVICE_UUID)
            } catch (e: Exception) {
                usbLog("BLE service lookup exception: ${e.message ?: e.javaClass.simpleName}")
                val failure = if (e is SecurityException) BleConnectionFailure.PERMISSION_REQUIRED
                    else BleConnectionFailure.HANDSHAKE_FAILED
                failBleAttempt(attempt, gatt, failure, "BLE service lookup failed", e !is SecurityException)
                return
            }
            bleRxCharacteristic = service?.getCharacteristic(BleBridgeConstants.RX_UUID)
            bleTxCharacteristic = service?.getCharacteristic(BleBridgeConstants.TX_UUID)
            val rx = bleRxCharacteristic
            val tx = bleTxCharacteristic
            val ctrl = service?.getCharacteristic(BleBridgeConstants.CTRL_UUID)
            if (status == BluetoothGatt.GATT_SUCCESS && rx != null && tx != null && ctrl != null) {
                scope.launch {
                    completeBleHandshake(attempt, gatt, tx)
                }
            } else {
                failBleAttempt(attempt, gatt, BleConnectionFailure.HANDSHAKE_FAILED, "BLE services missing or discovery failed status=$status", true)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!acceptBleCallback(attempt, gatt)) return
            usbLog("BLE onMtuChanged mtu=$mtu status=$status (${bleGattStatusName(status)})")
            if (status == BluetoothGatt.GATT_SUCCESS && mtu >= BleBridgeProtocol.DEFAULT_MTU) {
                bleMtu = mtu
            }
            blePendingMtuChange?.complete(status)
            blePendingMtuChange = null
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (!acceptBleCallback(attempt, gatt)) return
            if (characteristic.uuid != BleBridgeConstants.TX_UUID) return
            val value = characteristic.value
            if (value == null || value.isEmpty()) return
            val queued = bleNotificationChannel.trySend(BleNotificationPacket(attempt.token, value.copyOf()))
            if (queued.isFailure) {
                usbLog("BLE notification enqueue failed bytes=${value.size}")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (!acceptBleCallback(attempt, gatt)) return
            if (characteristic.uuid != BleBridgeConstants.TX_UUID) return
            if (value.isEmpty()) return
            val queued = bleNotificationChannel.trySend(BleNotificationPacket(attempt.token, value.copyOf()))
            if (queued.isFailure) {
                usbLog("BLE notification enqueue failed bytes=${value.size}")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (!acceptBleCallback(attempt, gatt)) return
            usbLog("BLE onDescriptorWrite status=$status (${bleGattStatusName(status)}) uuid=${descriptor.uuid}")
            blePendingDescriptorWrite?.complete(status)
            blePendingDescriptorWrite = null
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (!acceptBleCallback(attempt, gatt)) return
            usbLog("BLE onCharacteristicWrite status=$status (${bleGattStatusName(status)}) uuid=${characteristic.uuid}")
            if (characteristic.uuid == BleBridgeConstants.RX_UUID || characteristic.uuid == BleBridgeConstants.CTRL_UUID) {
                blePendingWrite?.complete(status)
                blePendingWrite = null
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (!acceptBleCallback(attempt, gatt)) return
            val text = characteristic.value?.let { String(it, Charsets.US_ASCII) }.orEmpty().trim()
            usbLog("BLE onCharacteristicRead status=$status (${bleGattStatusName(status)}) uuid=${characteristic.uuid} value=$text")
        }
    }

    private fun acceptBleCallback(attempt: BleConnectAttemptTracker.Attempt, gatt: BluetoothGatt): Boolean {
        if (!bleAttemptTracker.isCurrent(attempt)) return false
        synchronized(bleGattLock) {
            if (!bleAttemptTracker.isCurrent(attempt)) return false
            val currentGatt = bluetoothGatt
            if (currentGatt == null) bluetoothGatt = gatt
            return currentGatt == null || currentGatt === gatt
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
            if (CdcAcmSerialDriver.probe(device)) CdcAcmSerialDriver(device) else null
        } catch (e: Exception) { null }
    }

    fun getSerialPortCount(device: UsbDevice): Int = findDriver(device)?.ports?.size ?: 0

    private suspend fun awaitUsbPermission(device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) return true

        return usbPermissionMutex.withLock {
            if (usbManager.hasPermission(device)) return@withLock true

            withTimeoutOrNull(USB_PERMISSION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val requestId = usbPermissionRequestId.incrementAndGet()
                    val action = "${context.packageName}.USB_PERMISSION.$requestId"
                    val cleanedUp = AtomicBoolean(false)
                    var receiver: BroadcastReceiver? = null
                    var pendingIntent: PendingIntent? = null

                    fun cleanup() {
                        if (!cleanedUp.compareAndSet(false, true)) return
                        try { receiver?.let(context::unregisterReceiver) } catch (_: Exception) {}
                        pendingIntent?.cancel()
                    }

                    fun finish(granted: Boolean) {
                        cleanup()
                        if (continuation.isActive) {
                            runCatching { continuation.resume(granted) }
                        }
                    }

                    receiver = object : BroadcastReceiver() {
                        override fun onReceive(receiveContext: Context?, intent: Intent?) {
                            if (intent?.action != action) return
                            val resultDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                            if (resultDevice?.deviceId != device.deviceId) return
                            finish(
                                intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) &&
                                    usbManager.hasPermission(device)
                            )
                        }
                    }

                    continuation.invokeOnCancellation { cleanup() }

                    try {
                        ContextCompat.registerReceiver(
                            context,
                            receiver,
                            IntentFilter(action),
                            ContextCompat.RECEIVER_NOT_EXPORTED
                        )
                        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                        val permissionIntent = PendingIntent.getBroadcast(
                            context,
                            requestId,
                            Intent(action).setPackage(context.packageName),
                            flags
                        )
                        pendingIntent = permissionIntent
                        usbManager.requestPermission(device, permissionIntent)
                    } catch (e: Exception) {
                        usbLog("USB permission request failed: ${e.message}")
                        finish(false)
                    }
                }
            } ?: false
        }
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
                CdcAcmSerialDriver.probe(device)
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
        if (!hasBluetoothScanPermission()) {
            return
        }
        val scanner = try { bluetoothScanner } catch (_: SecurityException) { null } ?: return
        _bleDevices.value = emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        _isBleScanning.value = true
        try {
            scanner.startScan(null, settings, bleScanCallback)
        } catch (_: SecurityException) {
            _isBleScanning.value = false
        }
    }

    fun stopBleScan() {
        if (hasBluetoothScanPermission()) {
            try { bluetoothScanner?.stopScan(bleScanCallback) } catch (_: SecurityException) {}
        }
        _isBleScanning.value = false
    }

    fun isBluetoothEnabled(): Boolean = try { bluetoothAdapter?.isEnabled == true } catch (_: SecurityException) { false }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothScanPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN
            else Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun closeBluetoothGatt(gatt: BluetoothGatt?) {
        try { gatt?.disconnect() } catch (e: Exception) { /* ignore */ }
        try { gatt?.close() } catch (e: Exception) { /* ignore */ }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectBle(device: BleBridgeDevice): Boolean = connectionRequestMutex.withLock {
        if (isConnecting.get() || isConnectedFlag.get()) return@withLock false
        _lastBleConnectionFailure.value = BleConnectionFailure.NONE
        if (!hasBluetoothConnectPermission()) {
            rejectBleConnection(BleConnectionFailure.PERMISSION_REQUIRED, "BLE connect permission missing")
            return@withLock false
        }
        val adapter = bluetoothAdapter
        if (adapter == null) {
            rejectBleConnection(BleConnectionFailure.UNSUPPORTED, "BLE is not supported")
            return@withLock false
        }
        val enabled = try { adapter.isEnabled } catch (_: SecurityException) {
            rejectBleConnection(BleConnectionFailure.PERMISSION_REQUIRED, "BLE adapter access denied")
            return@withLock false
        }
        if (!enabled) {
            rejectBleConnection(BleConnectionFailure.BLUETOOTH_DISABLED, "Bluetooth is disabled")
            return@withLock false
        }
        if (!BluetoothAdapter.checkBluetoothAddress(device.address)) {
            rejectBleConnection(BleConnectionFailure.INVALID_ADDRESS, "Invalid BLE address")
            return@withLock false
        }

        repeat(2) { retryIndex ->
            val attempt = connectionMutex.withLock {
                disconnectInternal()
                isConnecting.set(true)
                _connectionState.value = ConnectionState.CONNECTING
                _connectionTransport.value = ConnectionTransport.NONE
                resetParsingState()
                bleMtu = BleBridgeProtocol.DEFAULT_MTU
                isBleTransport = true

                val currentAttempt = bleAttemptTracker.begin()
                val callback = createBleGattCallback(currentAttempt)
                val gatt = try {
                    adapter.getRemoteDevice(device.address)
                        .connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                } catch (e: SecurityException) {
                    failBleAttempt(currentAttempt, null, BleConnectionFailure.PERMISSION_REQUIRED, "BLE connect permission denied")
                    null
                } catch (e: IllegalArgumentException) {
                    failBleAttempt(currentAttempt, null, BleConnectionFailure.INVALID_ADDRESS, "Invalid BLE address")
                    null
                } catch (e: Exception) {
                    usbLog("BLE connectGatt exception: ${e.message ?: e.javaClass.simpleName}")
                    failBleAttempt(currentAttempt, null, BleConnectionFailure.GATT_UNAVAILABLE, "BLE connect dispatch failed", true)
                    null
                }

                if (gatt == null && !currentAttempt.completion.isCompleted) {
                    failBleAttempt(currentAttempt, null, BleConnectionFailure.GATT_UNAVAILABLE, "BLE connectGatt returned null", true)
                } else if (gatt != null && !acceptBleCallback(currentAttempt, gatt)) {
                    closeBluetoothGatt(gatt)
                    failBleAttempt(currentAttempt, gatt, BleConnectionFailure.CONNECTION_FAILED, "BLE GATT attempt was superseded")
                }
                if (!currentAttempt.completion.isCompleted || isConnectedFlag.get()) {
                    startBleNotificationProcessor()
                    startConsumer()
                    startFlushTimer()
                }
                currentAttempt
            }

            val result = try {
                withTimeoutOrNull(BLE_CONNECT_ATTEMPT_TIMEOUT_MS) { attempt.completion.await() }
                    ?: BleAttemptResult(false, BleConnectionFailure.TIMEOUT, retryable = true).also {
                        failBleAttempt(attempt, bluetoothGatt, it.failure, "BLE connection handshake timed out", it.retryable)
                    }
            } catch (e: CancellationException) {
                failBleAttempt(attempt, bluetoothGatt, BleConnectionFailure.CONNECTION_FAILED, "BLE connection cancelled")
                throw e
            }

            if (result.connected) return@withLock true
            if (!shouldRetryBleConnection(result, retryIndex)) return@withLock false
            usbLog("BLE direct reconnect retry ${retryIndex + 1}/1 after ${result.failure}")
            delay(BLE_RECONNECT_DELAY_MS)
        }
        false
    }

    private fun rejectBleConnection(failure: BleConnectionFailure, reason: String) {
        usbLog(reason)
        isConnecting.set(false)
        isConnectedFlag.set(false)
        _lastBleConnectionFailure.value = failure
        _connectionState.value = ConnectionState.ERROR
        _connectionTransport.value = ConnectionTransport.NONE
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
    suspend fun connect(
        device: UsbDevice,
        baudRate: Int = 115200,
        portIndex: Int = 0,
        assertDtr: Boolean = false
    ): Boolean = connectionRequestMutex.withLock {
        if (isConnecting.get() || _connectionState.value == ConnectionState.CONNECTING ||
            isConnectedFlag.get() || _connectionState.value == ConnectionState.CONNECTED
        ) {
            return@withLock false
        }
        try {
            _connectionState.value = ConnectionState.CONNECTING
            if (!awaitUsbPermission(device)) return@withLock handleUsbPermissionDenied(device)
            connectInternal(device, baudRate, portIndex, assertDtr)
        } catch (e: CancellationException) {
            resetCancelledUsbConnectionAttempt()
            throw e
        }
    }

    private suspend fun connectInternal(
        device: UsbDevice,
        baudRate: Int,
        portIndex: Int,
        assertDtr: Boolean
    ): Boolean = connectionMutex.withLock {
            if (isConnecting.get()) {
                return@withLock false
            }

            try {
                disconnectInternal()
            } catch (e: Exception) {
                e.printStackTrace()
                forceReset()
            }

            isConnecting.set(true)

            _connectionState.value = ConnectionState.CONNECTING

            try {
                android.util.Log.d("SerialManager", "Connecting to ${device.deviceName} port=$portIndex @ $baudRate baud")

                serialDriver = findDriver(device)

                if (serialDriver == null) {
                    android.util.Log.e("SerialManager", "Could not find serial driver for device")
                    _connectionState.value = ConnectionState.ERROR
                    _connectionTransport.value = ConnectionTransport.NONE
                    isConnecting.set(false)
                    return@withLock false
                }

                android.util.Log.d("SerialManager", "Driver: ${serialDriver!!::class.simpleName} (${serialDriver!!.ports.size} ports)")

                serialPort = serialDriver!!.ports.getOrNull(portIndex) ?: run {
                    usbLog("Serial port index $portIndex is unavailable")
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

                // ESP auto-reset circuits commonly map these lines to EN and GPIO0.
                serialPort?.setDTR(assertDtr)
                serialPort?.setRTS(false)

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
                isConnecting.set(false)
                try {
                    disconnectInternal()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    forceReset()
                }
                _connectionState.value = ConnectionState.ERROR
                _connectionTransport.value = ConnectionTransport.NONE
                false
            }
    }

    private fun handleUsbPermissionDenied(device: UsbDevice): Boolean {
        usbLog("USB permission denied for ${device.deviceName}")
        if (!isConnectedFlag.get()) {
            _connectionState.value = ConnectionState.ERROR
            _connectionTransport.value = ConnectionTransport.NONE
        }
        return false
    }

    private fun resetCancelledUsbConnectionAttempt() {
        if (!isConnectedFlag.get()) {
            isConnecting.set(false)
            _connectionState.value = ConnectionState.DISCONNECTED
            _connectionTransport.value = ConnectionTransport.NONE
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
    suspend fun connectWithAutoBaud(
        device: UsbDevice,
        portIndex: Int = 0,
        assertDtr: Boolean = false
    ): Boolean = connectionRequestMutex.withLock {
        if (isConnecting.get() || _connectionState.value == ConnectionState.CONNECTING ||
            isConnectedFlag.get() || _connectionState.value == ConnectionState.CONNECTED
        ) {
            return@withLock false
        }
        try {
            _connectionState.value = ConnectionState.CONNECTING
            if (!awaitUsbPermission(device)) return@withLock handleUsbPermissionDenied(device)

            val baud = withContext(Dispatchers.IO) {
                detectBaudRate(device, portIndex, assertDtr)
            } ?: 115200
            _detectedBaudRate.value = baud
            usbLog("Auto-baud result: $baud")
            connectInternal(device, baud, portIndex, assertDtr)
        } catch (e: CancellationException) {
            resetCancelledUsbConnectionAttempt()
            throw e
        }
    }

    /**
     * Iterate PROBE_BAUD_RATES and return the first one that yields a valid ASCII
     * response, or null if none do (caller should fall back to 115200).
     */
    private suspend fun detectBaudRate(
        device: UsbDevice,
        portIndex: Int,
        assertDtr: Boolean
    ): Int? {
        for (baud in PROBE_BAUD_RATES) {
            if (probeBaudRate(device, baud, portIndex, assertDtr)) return baud
        }
        return null
    }

    /**
     * Open [device] at [baud], send a "\r\n" probe, wait 350 ms, then check whether
     * >= 75 % of the received bytes are printable ASCII. Returns true on a match.
     *
     * The probe port is opened and closed independently of the main connection. It uses
     * the same control-line policy as the final connection.
     */
    private suspend fun probeBaudRate(
        device: UsbDevice,
        baud: Int,
        portIndex: Int,
        assertDtr: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        var probePort: UsbSerialPort? = null
        var probeConnection: android.hardware.usb.UsbDeviceConnection? = null
        try {
            val driver = findDriver(device) ?: return@withContext false
            probePort = driver.ports.getOrNull(portIndex) ?: return@withContext false
            probeConnection = usbManager.openDevice(device) ?: return@withContext false

            probePort.open(probeConnection)
            probePort.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            probePort.setDTR(assertDtr)
            probePort.setRTS(false)

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
            disconnectInternal()
        } catch (e: Exception) {
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
        bleFrameDecoder.reset()
    }

    @SuppressLint("MissingPermission")
    private suspend fun completeBleHandshake(
        attempt: BleConnectAttemptTracker.Attempt,
        gatt: BluetoothGatt,
        tx: BluetoothGattCharacteristic
    ) {
        if (!acceptBleCallback(attempt, gatt)) return
        if (!hasBluetoothConnectPermission()) {
            failBleAttempt(attempt, gatt, BleConnectionFailure.PERMISSION_REQUIRED, "BLE connect permission missing")
            return
        }

        val notificationsEnabled = try {
            gatt.setCharacteristicNotification(tx, true)
        } catch (e: Exception) {
            usbLog("BLE setCharacteristicNotification exception: ${e.message ?: e.javaClass.simpleName}")
            false
        }

        if (!notificationsEnabled) {
            failBleAttempt(attempt, gatt, BleConnectionFailure.HANDSHAKE_FAILED, "BLE enable notifications failed", true)
            return
        }

        val descriptor = tx.getDescriptor(BleBridgeConstants.CLIENT_CONFIG_UUID)
        if (descriptor == null) {
            failBleAttempt(attempt, gatt, BleConnectionFailure.HANDSHAKE_FAILED, "BLE missing TX CCCD")
            return
        }

        val descriptorStatus = bleWriteDescriptorReliable(
            gatt,
            descriptor,
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            "enable notifications"
        )
        if (descriptorStatus != BluetoothGatt.GATT_SUCCESS) {
            failBleAttempt(attempt, gatt, BleConnectionFailure.HANDSHAKE_FAILED, "BLE descriptor write failed status=$descriptorStatus", true)
            return
        }

        if (!acceptBleCallback(attempt, gatt)) return

        val mtuStatus = bleRequestMtuReliable(gatt, BLE_REQUESTED_MTU)
        if (mtuStatus != BluetoothGatt.GATT_SUCCESS) {
            bleMtu = BleBridgeProtocol.DEFAULT_MTU
            usbLog("BLE mtu request failed status=$mtuStatus; continuing with mtu=$bleMtu")
        }

        finishBleConnect(attempt, gatt)
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
        val dispatched = try {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        } catch (e: Exception) {
            usbLog("BLE descriptor write exception label=$label: ${e.message ?: e.javaClass.simpleName}")
            false
        }
        if (!dispatched) {
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

    private fun finishBleConnect(attempt: BleConnectAttemptTracker.Attempt, gatt: BluetoothGatt) {
        if (!acceptBleCallback(attempt, gatt)) return
        bleAttemptTracker.complete(attempt, BleAttemptResult(connected = true), keepActive = true) {
            isConnectedFlag.set(true)
            isConnecting.set(false)
            _connectionTransport.value = ConnectionTransport.BLE
            bleHeartbeatJob?.cancel()
            bleHeartbeatJob = null
            bleHeartbeatWatchdog?.cancel()
            bleHeartbeatWatchdog = null
            _lastBleConnectionFailure.value = BleConnectionFailure.NONE
            _connectionState.value = ConnectionState.CONNECTED
        }
    }

    private fun failBleAttempt(
        attempt: BleConnectAttemptTracker.Attempt,
        gatt: BluetoothGatt?,
        failure: BleConnectionFailure,
        reason: String,
        retryable: Boolean = false
    ) {
        val completed = bleAttemptTracker.complete(attempt, BleAttemptResult(false, failure, retryable)) {
            usbLog(reason)
            isConnecting.set(false)
            isConnectedFlag.set(false)
            _lastBleConnectionFailure.value = failure
            _connectionTransport.value = ConnectionTransport.NONE
            val gattToClose = synchronized(bleGattLock) {
                val current = bluetoothGatt
                if (gatt == null || current === gatt) bluetoothGatt = null
                gatt ?: current
            }
            closeBluetoothGatt(gattToClose)
            bleServiceDiscoveryJob?.cancel()
            bleServiceDiscoveryJob = null
            bleNotificationJob?.cancel()
            bleNotificationJob = null
            consumerJob?.cancel()
            consumerJob = null
            flushJob?.cancel()
            flushJob = null
            bleRxCharacteristic = null
            bleTxCharacteristic = null
            isBleTransport = false
            failPendingBleOperations()
            _connectionState.value = ConnectionState.ERROR
        }
        if (!completed) {
            gatt?.let(::closeBluetoothGatt)
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

    private fun isWdStreamStartCommand(command: String): Boolean {
        return command.trim().startsWith("wdstream start", ignoreCase = true)
    }

    private fun isBleInterruptCommand(command: String): Boolean {
        val trimmed = command.trim()
        return trimmed.equals("stop", ignoreCase = true) ||
            trimmed.equals("wdstream stop", ignoreCase = true)
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
        bleAttemptTracker.clear()

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
        // Drain binary channel to prevent memory leaks
        while (!binaryChannel.isEmpty) {
            binaryChannel.tryReceive()
        }

        // Close serial port first - with individual try-catch for each operation
        serialPort?.let { port ->
            try { port.setDTR(false) } catch (e: Exception) { /* ignore */ }
            try { port.setRTS(false) } catch (e: Exception) { /* ignore */ }
            try { port.close() } catch (e: Exception) { /* ignore */ }
        }

        // Close USB connection
        try { usbConnection?.close() } catch (e: Exception) { /* ignore */ }
        val gattToClose = synchronized(bleGattLock) {
            bluetoothGatt.also { bluetoothGatt = null }
        }
        closeBluetoothGatt(gattToClose)

        // Clear references
        serialPort = null
        usbConnection = null
        serialDriver = null
        bleRxCharacteristic = null
        bleTxCharacteristic = null
        isBleTransport = false
        bleMtu = BleBridgeProtocol.DEFAULT_MTU
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
            val trimmedCommand = command.trim()
            val commandPayload = trimmedCommand.toByteArray(Charsets.UTF_8)
            if (commandPayload.isEmpty() ||
                (isBleTransport && commandPayload.size > BleBridgeProtocol.MAX_COMMAND_BYTES)) {
                usbLog("Command rejected byteLength=${commandPayload.size}")
                return@withContext false
            }
            currentSdReadIsBase64 = isBase64SdReadCommand(trimmedCommand)

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
            val commandFrames = if (isBleTransport) {
                BleBridgeProtocol.commandFrames(bridgeCommandId, commandPayload, bleMtu)
            } else emptyList()
            val commandBytes = (command + "\r\n").toByteArray(Charsets.UTF_8)
            if (isBleTransport) {
                val gatt = bluetoothGatt ?: return@withContext false
                val characteristic = bleRxCharacteristic ?: return@withContext false
                val commandId = bridgeCommandId
                val isInterruptCommand = isBleInterruptCommand(command)
                val isWdStreamStart = isWdStreamStartCommand(command)

                val ok = bleCommandMutex.withLock {
                    if (isInterruptCommand) {
                        val previousCmdId = bleActiveCmdId
                        bleActiveCmdId = 0
                        bleWdStreamLineBuffer.clear()
                        cmdIdLastDataMs.remove(previousCmdId)
                        synchronized(bleBridgeStateLock) {
                            blePendingBridgeAcks.remove(previousCmdId)
                            blePendingCommandEnds.remove(previousCmdId)
                        }
                    } else {
                        awaitBleActiveCommandClear()
                    }
                    bleActiveCmdId = commandId
                    cmdIdLastDataMs[commandId] = System.currentTimeMillis()
                    if (isWdStreamStart) {
                        bleWdStreamCmdId = commandId
                        bleWdStreamLineBuffer.clear()
                    }

                    val ackDeferred = CompletableDeferred<Pair<Boolean, Int>>()
                    val endDeferred = CompletableDeferred<Unit>()
                    synchronized(bleBridgeStateLock) {
                        blePendingBridgeAcks[commandId] = ackDeferred
                        blePendingCommandEnds[commandId] = endDeferred
                    }

                    for ((index, frame) in commandFrames.withIndex()) {
                        val status = bleWriteCharacteristicReliable(
                            gatt = gatt,
                            characteristic = characteristic,
                            value = frame,
                            writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                            label = "command=$command fragment=${index + 1}/${commandFrames.size} target=rx"
                        )
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            usbLog("BLE command write failed command=$command fragment=${index + 1}/${commandFrames.size} status=$status (${bleGattStatusName(status)})")
                            synchronized(bleBridgeStateLock) {
                                blePendingBridgeAcks.remove(commandId)
                                blePendingCommandEnds.remove(commandId)
                            }
                            return@withLock false
                        }
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
                            isConnectedFlag.set(false)
                            // Clean up the broken connection
                            scope.launch {
                                connectionMutex.withLock {
                                    disconnectInternal()
                                    _connectionState.value = ConnectionState.ERROR
                                }
                            }
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
                if (bleAttemptTracker.currentToken() != packet.attemptToken) continue
                val startNanos = System.nanoTime()
                processBleNotificationPacket(packet.value)
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                if (elapsedMs >= 10) {
                    android.util.Log.w("SerialManager.PERF", "ble notification slow: ${elapsedMs}ms bytes=${packet.value.size}")
                }
            }
        }
    }

    private fun processBleNotificationPacket(packet: ByteArray) {
        lastIncomingDataAtMs = System.currentTimeMillis()
        val decoded = bleFrameDecoder.feed(packet)
        if (decoded.fallback.isNotEmpty()) {
            processIncomingDataFast(decoded.fallback, decoded.fallback.size)
        }
        for (frame in decoded.frames) {
            val bridgeFrame = BleBridgeFrame(
                type = frame.type,
                status = frame.status,
                commandId = frame.commandId,
                payload = frame.payload,
                pendingBytes = if (frame.type == BLE_BRIDGE_FRAME_TYPE_ACK) frame.payload.size else 0
            )
            processBleBridgeFrame(bridgeFrame)
        }
    }

    private fun emitBleBridgeText(payload: ByteArray) {
        if (payload.isEmpty()) return
        processIncomingDataFast(payload, payload.size)
    }

    private fun emitBleWdStreamPayload(payload: ByteArray) {
        if (payload.isEmpty()) return

        for (byte in payload) {
            val value = byte.toInt() and 0xFF
            when {
                value == '\r'.code -> Unit
                value == '\n'.code -> flushBleWdStreamLine()
                value in 0x20..0x7E || value == '\t'.code -> {
                    if (bleWdStreamLineBuffer.length < 2048) {
                        bleWdStreamLineBuffer.append(value.toChar())
                    } else {
                        bleWdStreamLineBuffer.clear()
                    }
                }
                else -> Unit
            }
        }
    }

    private fun flushBleWdStreamLine() {
        if (bleWdStreamLineBuffer.isEmpty()) return
        val rawLine = bleWdStreamLineBuffer.toString()
        bleWdStreamLineBuffer.clear()
        val wdIndex = rawLine.indexOf("WD:")
        if (wdIndex < 0) return

        val line = rawLine.substring(wdIndex).trim()
        if (!line.startsWith("WD:")) return
        processLine(line)
        if (line.startsWith("WD:END")) {
            bleWdStreamCmdId = 0
        }
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
                    if (frame.commandId == bleWdStreamCmdId) {
                        emitBleWdStreamPayload(frame.payload)
                    } else {
                        emitBleBridgeText(frame.payload)
                    }
                }
            }
            BLE_BRIDGE_FRAME_TYPE_END -> {
                android.util.Log.d("SerialManager", "BLE frame END id=${frame.commandId} (dispatch complete)")
                val isCurrentCommand = bleActiveCmdId == frame.commandId
                if (isCurrentCommand) flushMultilineBuffer()
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
                val detail = frame.payload.toString(Charsets.UTF_8).trim()
                val error = buildString {
                    append("ERROR: BLE bridge status=").append(frame.status)
                    if (detail.isNotEmpty()) append(": ").append(detail)
                }
                _rawOutput.tryEmit(error)
                sendToResponseChannel(error)
                cmdIdLastDataMs.remove(frame.commandId)
                if (bleActiveCmdId == frame.commandId) bleActiveCmdId = 0
                synchronized(bleBridgeStateLock) {
                    blePendingCommandEnds.remove(frame.commandId)?.complete(Unit)
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
                if (!isBinaryMode && System.currentTimeMillis() - lastTextDataAtMs >= 750) {
                    takeBufferedLine()?.let(::processLine)
                }
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
        lastTextDataAtMs = lastIncomingDataAtMs
        
        if (isBinaryMode) {
            processBinaryData(buffer, length)
            perfLog("processIncomingDataFast_binary", System.nanoTime() - startNanos)
            return
        }

        for (i in 0 until length) {
            val byte = buffer[i]

            when (byte) {
                '\r'.code.toByte(), '\n'.code.toByte() -> {
                    takeBufferedLine()?.let { line ->
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
                    synchronized(lineBuffer) {
                        lineBuffer.append(byte.toInt().toChar())
                    }
                }
            }
        }
        perfLog("processIncomingDataFast", System.nanoTime() - startNanos, "bytes=$length")
    }

    private fun takeBufferedLine(): String? = synchronized(lineBuffer) {
        if (lineBuffer.isEmpty()) {
            null
        } else {
            lineBuffer.toString().also { lineBuffer.clear() }
        }
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
            // Flush multiline buffer on empty lines (but never chipinfo — it has its own collector,
            // and never WPA3_START — its block has a blank line between PMF and Finding)
            if (isAccumulatingMultiline && multilineBuffer.isNotEmpty() && multilineType != LineType.WPA3_START) {
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
            // Keep the explicit terminator in the parsed response. Its presence is
            // what makes absent inventory entries definitively unsupported.
            sendToResponseChannel("Chip Information: $collected, [CHIPINFO_END]")
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
            LineType.GATT_SERVICE_START -> {
                if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
                    sendToResponseChannel(multilineBuffer.toString())
                }
                multilineBuffer.clear()
                multilineBuffer.append(cleanLine.trim())
                isAccumulatingMultiline = true
                multilineType = lineType
            }
            LineType.GATT_SERVICE_CONTINUATION -> {
                if (isAccumulatingMultiline && multilineType == LineType.GATT_SERVICE_START) {
                    multilineBuffer.append("\n").append(cleanLine.trim())
                    if (cleanLine.trim().startsWith("Handles:", ignoreCase = true)) {
                        flushMultilineBuffer()
                    }
                } else {
                    sendToResponseChannel(cleanLine.trim())
                }
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
            LineType.ETH_INFO_START, LineType.ETH_STATS_START -> {
                if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
                    sendToResponseChannel(multilineBuffer.toString())
                }
                multilineBuffer.clear()
                multilineBuffer.append(cleanLine)
                isAccumulatingMultiline = true
                multilineType = lineType
            }
            LineType.ETH_INFO_CONTINUATION, LineType.ETH_STATS_CONTINUATION -> {
                if (isAccumulatingMultiline) {
                    multilineBuffer.append("\n").append(cleanLine.trim())
                } else {
                    sendToResponseChannel(cleanLine.trim())
                }
            }
            LineType.ADVERTISER_DETAIL_START -> {
                if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
                    sendToResponseChannel(multilineBuffer.toString())
                }
                multilineBuffer.clear()
                multilineBuffer.append(cleanLine.trim())
                isAccumulatingMultiline = true
                multilineType = lineType
            }
            LineType.ADVERTISER_DETAIL_CONTINUATION -> {
                if (isAccumulatingMultiline && multilineType == LineType.ADVERTISER_DETAIL_START) {
                    multilineBuffer.append("\n").append(cleanLine.trim())
                } else {
                    sendToResponseChannel(cleanLine.trim())
                }
            }
            LineType.PINEAP_START -> {
                if (isAccumulatingMultiline && multilineBuffer.isNotEmpty()) {
                    sendToResponseChannel(multilineBuffer.toString())
                }
                multilineBuffer.clear()
                multilineBuffer.append(cleanLine.trim())
                isAccumulatingMultiline = true
                multilineType = lineType
            }
            LineType.PINEAP_CONTINUATION -> {
                if (isAccumulatingMultiline && multilineType == LineType.PINEAP_START) {
                    multilineBuffer.append("\n").append(cleanLine.trim())
                    if (cleanLine.trim().startsWith("SSIDs", ignoreCase = true)) {
                        flushMultilineBuffer()
                    }
                } else {
                    sendToResponseChannel(cleanLine.trim())
                }
            }
            LineType.WPA3_START -> {
                if (isAccumulatingMultiline && multilineBuffer.isNotEmpty() && multilineType != LineType.WPA3_START) {
                    sendToResponseChannel(multilineBuffer.toString())
                }
                multilineBuffer.clear()
                multilineBuffer.append(cleanLine.trim())
                isAccumulatingMultiline = true
                multilineType = lineType
            }
            LineType.WPA3_CONTINUATION -> {
                if (isAccumulatingMultiline && multilineType == LineType.WPA3_START) {
                    multilineBuffer.append("\n").append(cleanLine.trim())
                    if (cleanLine.trim().startsWith("Finding:", ignoreCase = true)) {
                        flushMultilineBuffer()
                    }
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

        if (isIndexedGattServiceStart(trimmed)) {
            return LineType.GATT_SERVICE_START
        }

        // BLE advertiser detail block header (listadv): "[N] BLE Advertiser" or "[N] iBeacon"
        // (no pipe on the line - distinguishes it from the live single-line format below)
        if (ADVERTISER_DETAIL_HEADER_FAST.matches(trimmed)) {
            return LineType.ADVERTISER_DETAIL_START
        }

        if (isAccumulatingMultiline && multilineType == LineType.ADVERTISER_DETAIL_START &&
            ADVERTISER_DETAIL_FIELD_PREFIXES.any { trimmed.startsWith(it) }) {
            return LineType.ADVERTISER_DETAIL_CONTINUATION
        }

        // BLE advertiser live single-line format: "[N] Advertiser | MAC | RSSI dBm | AdvType | ..."
        // Fully self-contained on one line, so no multiline accumulation - just avoid it
        // being misclassified as an IR button below.
        if (ADVERTISER_LIVE_FAST.containsMatchIn(trimmed)) {
            return LineType.SINGLE
        }

        if (isAccumulatingMultiline && multilineType == LineType.GATT_SERVICE_START &&
            (trimmed.startsWith("UUID:", ignoreCase = true) ||
                trimmed.startsWith("Handles:", ignoreCase = true))) {
            return LineType.GATT_SERVICE_CONTINUATION
        }

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

        // Ethernet info - "Status: UP"/"Status: DOWN" starts ethinfo output
        if (trimmed == "Status: UP" || trimmed == "Status: DOWN") {
            return LineType.ETH_INFO_START
        }

        if (isAccumulatingMultiline && multilineType == LineType.ETH_INFO_START) {
            if (trimmed.startsWith("Link:") || trimmed.startsWith("MAC:") ||
                trimmed.startsWith("IP Address:") || trimmed.startsWith("Netmask:") ||
                trimmed.startsWith("Gateway:") || trimmed.startsWith("DNS Main:") ||
                trimmed.startsWith("DNS Backup:") || trimmed.startsWith("DNS Fallback:") ||
                trimmed.startsWith("DHCP Server:") || trimmed.startsWith("Ethernet link is not established")) {
                return LineType.ETH_INFO_CONTINUATION
            }
        }

        // Ethernet statistics - "=== Ethernet Statistics ===" starts ethstats output
        if (trimmed == "=== Ethernet Statistics ===") {
            return LineType.ETH_STATS_START
        }

        if (isAccumulatingMultiline && multilineType == LineType.ETH_STATS_START) {
            if (trimmed.startsWith("Link Status:") || trimmed.startsWith("IP Address:") ||
                trimmed.startsWith("Netmask:") || trimmed.startsWith("Gateway:") ||
                trimmed.startsWith("MAC Address:") || trimmed.startsWith("RX ") ||
                trimmed.startsWith("TX ") || trimmed.startsWith("ARP Requests:") ||
                trimmed.startsWith("ARP Replies:") || trimmed.startsWith("Statistics not available") ||
                trimmed == "--- Packet Statistics ---" || trimmed == "--- ARP Statistics ---") {
                return LineType.ETH_STATS_CONTINUATION
            }
        }

        // Pineapple rogue AP detection block (pineap)
        if (trimmed == "Pineapple detected!" || trimmed == "Pineapple OUI match!") {
            return LineType.PINEAP_START
        }
        if (isAccumulatingMultiline && multilineType == LineType.PINEAP_START &&
            (trimmed.startsWith("BSSID:") || trimmed.startsWith("Channel:") ||
                trimmed.startsWith("RSSI:") || trimmed.startsWith("SSIDs"))) {
            return LineType.PINEAP_CONTINUATION
        }

        // WPA3 compliance check block (wpa3check, single-AP form)
        if (trimmed == "--- WPA3 Compliance ---") {
            return LineType.WPA3_START
        }
        if (isAccumulatingMultiline && multilineType == LineType.WPA3_START &&
            (trimmed.startsWith("SSID:") || trimmed.startsWith("BSSID:") ||
                trimmed.startsWith("Auth:") || trimmed.startsWith("WPA3 Present:") ||
                trimmed.startsWith("Transition Mode:") || trimmed.startsWith("PMF:") ||
                trimmed.startsWith("Finding:"))) {
            return LineType.WPA3_CONTINUATION
        }

        // Continuation lines - but NOT if trimmed starts with [ (those are IR buttons)
        if ((line.startsWith("  ") || line.startsWith("\t") || line.startsWith(" ")) && !trimmed.startsWith("[")) {
            return LineType.CONTINUATION
        }

        return LineType.SINGLE
    }

    private enum class LineType {
        AP_START, FLIPPER_START, AIRTAG_START, STATION_START, GATT_START, GATT_SERVICE_START, GATT_SERVICE_CONTINUATION, CHIP_INFO_START, TRACK_HEADER_START, IR_REMOTE, IR_BUTTON, HANDSHAKE_START, HANDSHAKE_CONTINUATION, WIFI_STATUS_START, WIFI_STATUS_CONTINUATION, GPS_START, GPS_CONTINUATION, ETH_INFO_START, ETH_INFO_CONTINUATION, ETH_STATS_START, ETH_STATS_CONTINUATION, ADVERTISER_DETAIL_START, ADVERTISER_DETAIL_CONTINUATION, PINEAP_START, PINEAP_CONTINUATION, WPA3_START, WPA3_CONTINUATION, CONTINUATION, SINGLE
    }

    // BLE advertiser detail block header (listadv): "[N] BLE Advertiser" or "[N] iBeacon" - no pipe
    private val ADVERTISER_DETAIL_HEADER_FAST = Regex("^\\[\\d+]\\s*(BLE Advertiser|iBeacon)$")

    // BLE advertiser live single-line format (print_advertiser_line)
    private val ADVERTISER_LIVE_FAST = Regex("^\\[\\d+]\\s*(Advertiser|iBeacon)\\s*\\|")

    private val ADVERTISER_DETAIL_FIELD_PREFIXES = listOf(
        "MAC:", "Address Type:", "RSSI:", "Adv Type:", "Name:", "Flags:", "TX Power:",
        "OUI Vendor:", "Manufacturer:", "Appearance:", "Services:", "Service Data:",
        "iBeacon UUID:", "iBeacon Major:", "iBeacon Minor:", "Measured Power:"
    )

    private fun isIndexedGattServiceStart(line: String): Boolean {
        if (!line.startsWith("[") || !line.contains("] Service:", ignoreCase = true)) return false
        return line.substringAfter("[").substringBefore("]").toIntOrNull() != null
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
        NFC_MESSAGE,
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
        WARDDRIVE_STATS,
        WDSTREAM_AP,
        WDSTREAM_BLE,
        WDSTREAM_STATUS,
        ETH_INFO,
        ETH_STATS,
        ETH_ARP_RESULT,
        ETH_PORT_RESULT,
        ETH_PING_RESULT,
        ETH_TRACE_HOP,
        ADVERTISER_DEVICE,
        ADVERTISER_DEVICE_DETAIL,
        PINEAP_DETECTION,
        FLOCK_DETECTION,
        FLOCK_SCAN_COMPLETE,
        NETBIOS_RESULT,
        NETBIOS_COMPLETE,
        HTTP_BANNER_HIT,
        HTTP_BANNER_SUMMARY,
        SNMP_HIT,
        SNMP_SUMMARY,
        ENUM_HIT,
        ENUM_SUMMARY,
        WPA3_COMPLIANCE,
        WPA3_REPORT_HEADER,
        WPA3_REPORT_SUMMARY,
        CSA_TARGETING,
        CSA_TARGET,
        CSA_RATE,
        GTK_ABUSE_STATUS,
        PROBE_REQUEST,
        CONGESTION_HEADER,
        CONGESTION_ROW,
        PORT_SCAN_HOST,
        OPEN_PORT,
        SSH_BANNER,
        SSH_BANNER_BANNER,
        ARP_HOST,
        ARP_SCAN_HEADER,
        ARP_SCAN_SUMMARY,
        SWEEP_PHASE,
        SWEEP_SUMMARY,
        DHCP_STARVE_STATS,
        IP_LOOKUP_DEVICE,
        IP_LOOKUP_DONE,
        SCAN_COMPLETION,
        SSH_SCAN_SUMMARY,
        CAPTURE_LIST_HEADER,
        CAPTURE_LIST_ENTRY,
        CAPTURE_LIST_EMPTY,
        CAPTURE_EXPORT_RESULT,
        CAPTURE_EXPORT_METRICS,
        ETH_POISON_STATUS,
        ETH_POISON_ITEM_HEADER,
        ETH_POISON_ITEM,
        SINKHOLE_STATUS_HEADER,
        SINKHOLE_STATUS_LINE,
        SINKHOLE_LIVE,
        WEBUI_AP_STATE,
        WEB_AUTH_RESULT
    }

    // Lazy evaluation of type for performance
    val type: ResponseType by lazy {
        detectTypeFast()
    }

    private fun detectTypeFast(): ResponseType {
        return when {
            raw.startsWith("[") && raw.contains("SSID:") -> ResponseType.ACCESS_POINT

            raw.startsWith("WD:AP ") -> ResponseType.WDSTREAM_AP

            raw.startsWith("WD:BLE ") -> ResponseType.WDSTREAM_BLE

            raw.startsWith("WD:BEGIN") || raw.startsWith("WD:STATUS ") || raw.startsWith("WD:END") -> ResponseType.WDSTREAM_STATUS

            raw.startsWith("[FLOCK] Surveillance device detected!") -> ResponseType.FLOCK_DETECTION

            raw.startsWith("[FLOCK] Scan stopped.") -> ResponseType.FLOCK_SCAN_COMPLETE

            raw.startsWith("[NetBIOS] Host:") -> ResponseType.NETBIOS_RESULT

            Regex("^NetBIOS Scan: Subnet scan complete$").containsMatchIn(raw.trim()) ||
                Regex("^NetBIOS scan completed on \\S+$").containsMatchIn(raw.trim()) -> ResponseType.NETBIOS_COMPLETE

            raw.startsWith("[SNMP-WALK]") || raw.startsWith("[SNMP]") -> ResponseType.SNMP_HIT

            raw.startsWith("[Enum]") -> ResponseType.ENUM_HIT

            Regex("^\\[\\S+:\\d+]\\s*\\(\\w+\\)\\s*(Server:|Response:|Status: OPEN, no banner|Status: OPEN, TLS banner requires handshake)").containsMatchIn(raw) -> ResponseType.HTTP_BANNER_HIT

            Regex("^\\[\\d+]\\s.+\\(Ch:\\d+\\)\\s[0-9A-Fa-f:]+$").matches(raw.trim()) -> ResponseType.CSA_TARGET

            raw.contains("Pineapple detected!") || raw.contains("Pineapple OUI match!") -> ResponseType.PINEAP_DETECTION

            raw.startsWith("--- WPA3 Compliance Report") -> ResponseType.WPA3_REPORT_HEADER

            raw.startsWith("--- WPA3 Compliance ---") -> ResponseType.WPA3_COMPLIANCE

            raw.startsWith("Summary:") && raw.contains("compliant") -> ResponseType.WPA3_REPORT_SUMMARY

            raw.startsWith("HTTP Banner Scan:") -> ResponseType.HTTP_BANNER_SUMMARY

            raw.startsWith("SNMP Scan:") -> ResponseType.SNMP_SUMMARY

            raw.startsWith("Enum Scan:") -> ResponseType.ENUM_SUMMARY

            raw.startsWith("CSA Attack: Targeting") -> ResponseType.CSA_TARGETING

            raw.startsWith("CSA:") && raw.contains("pkts/sec") -> ResponseType.CSA_RATE

            raw.startsWith("GTK") -> ResponseType.GTK_ABUSE_STATUS

            // ---- WiFi network scans / advanced attacks ----

            raw.startsWith("Probe Req:") -> ResponseType.PROBE_REQUEST

            // congestion table row: "|  6 |   123 | ######## |" (header checked first so rows don't collide)
            raw.trim().startsWith("| CH |") -> ResponseType.CONGESTION_HEADER

            raw.trim().startsWith("|") && raw.trim().endsWith("|") && raw.contains("|") &&
                Regex("^\\|\\s*\\d+\\s*\\|\\s*\\d+\\s*\\|").containsMatchIn(raw) -> ResponseType.CONGESTION_ROW

            // scanports host header: "Found 5 open ports on 192.168.1.1:" / "Host 1.2.3.4 has 3 open ports" /
            // subnet scan: "[Host 1] Found active host: 192.168.1.5" / "UDP ports on 192.168.1.5:"
            Regex("^Found \\d+ (?:open ports|udp ports(?: responding)?) on \\S+:", RegexOption.IGNORE_CASE).containsMatchIn(raw.trim()) ||
                Regex("^Host \\S+ has \\d+ (?:open ports|UDP ports responding)").containsMatchIn(raw.trim()) ||
                Regex("^\\[Host \\d+\\] Found active host: \\S+$").containsMatchIn(raw.trim()) ||
                Regex("^UDP ports on \\S+:$").containsMatchIn(raw.trim()) -> ResponseType.PORT_SCAN_HOST

            // scanports port line: "  Port 80" / "  UDP 53" / "  Port 80: OPEN" / "  UDP 53: OPEN"
            Regex("^Port \\d+(?::\\s*OPEN)?$").matches(raw.trim()) || Regex("^UDP \\d+(?::\\s*OPEN)?$").matches(raw.trim()) -> ResponseType.OPEN_PORT

            // scanports/scanlocal completion: "Scan completed. Found 3 active hosts." / "Scan cancelled. Found 1 active hosts."
            Regex("^Scan (?:completed|cancelled)\\. Found \\d+ active hosts\\.$").containsMatchIn(raw.trim()) -> ResponseType.SCAN_COMPLETION

            // scanlocal (mDNS IP lookup): "Device at: 1.2.3.4" + "  Name:/Type:/Port:" + "IP Scan Done. Found N devices."
            Regex("^Device at:\\s*\\S+$").containsMatchIn(raw.trim()) ||
                Regex("^\\s*Name:\\s*\\S+").containsMatchIn(raw) && raw.trim().startsWith("Name:") ||
                Regex("^\\s*Type:\\s*\\S+").containsMatchIn(raw) && raw.trim().startsWith("Type:") ||
                Regex("^\\s*Port:\\s*\\d+$").containsMatchIn(raw) && raw.trim().startsWith("Port:") -> ResponseType.IP_LOOKUP_DEVICE

            Regex("^IP Scan Done\\. Found \\d+ devices\\.$").containsMatchIn(raw.trim()) -> ResponseType.IP_LOOKUP_DONE

            // scanssh open line: "[1.2.3.4:22] Status: OPEN," (must precede the generic "[..." IR_BUTTON rule)
            Regex("^\\[[\\d.]+:\\d+]\\s*Status: OPEN,?").containsMatchIn(raw.trim()) -> ResponseType.SSH_BANNER

            Regex("^Banner:\\s*").containsMatchIn(raw.trim()) -> ResponseType.SSH_BANNER_BANNER

            // scanssh completion: "SSH scan completed on 1.2.3.4 - found 2 open ports" / "SSH Scan: Subnet scan complete - found 3 hosts with 5 open SSH ports"
            Regex("^SSH scan completed on \\S+ - found \\d+ open ports", RegexOption.IGNORE_CASE).containsMatchIn(raw.trim()) ||
                Regex("^SSH Scan: .*found \\d+ hosts with \\d+ open SSH ports", RegexOption.IGNORE_CASE).containsMatchIn(raw.trim()) -> ResponseType.SSH_SCAN_SUMMARY

            // scanarp entry: " 1. 192.168.1.5 [AA:BB:CC:DD:EE:FF]"
            Regex("^\\d+\\.\\s+\\S+\\s+\\[[0-9A-Fa-f:]{17}]$").matches(raw.trim()) -> ResponseType.ARP_HOST

            raw.trim().startsWith("=== ARP Scan Results ===") -> ResponseType.ARP_SCAN_HEADER

            Regex("^Found \\d+ active hosts on \\S+/\\d+ \\(\\d+ passes\\):").containsMatchIn(raw.trim()) -> ResponseType.ARP_SCAN_SUMMARY

            // sweep phase/progress markers and final summary
            Regex("^--- Phase \\d+:").containsMatchIn(raw) ||
                raw.startsWith("=== Starting Full Environment Sweep ===") ||
                raw.startsWith("=== Sweep Complete ===") ||
                raw.startsWith("Saving report to:") ||
                raw.startsWith("Report saved to:") -> ResponseType.SWEEP_PHASE

            Regex("^WiFi: \\d+ APs, \\d+ stations \\| Security:").containsMatchIn(raw.trim()) -> ResponseType.SWEEP_SUMMARY

            raw.startsWith("DHCP-Starve:") -> ResponseType.DHCP_STARVE_STATS

            raw.trim() == "On-device captures:" -> ResponseType.CAPTURE_LIST_HEADER

            Regex("^\\[\\+|-]\\s+\\S+\\.pcap$").containsMatchIn(raw.trim()) -> ResponseType.CAPTURE_LIST_ENTRY

            raw.contains("No .pcap files found") -> ResponseType.CAPTURE_LIST_EMPTY

            Regex("^Exported\\s+\\S+$").containsMatchIn(raw.trim()) -> ResponseType.CAPTURE_EXPORT_RESULT

            Regex("^PMKID: \\d+\\s+M2/M3: \\d+$").matches(raw.trim()) -> ResponseType.CAPTURE_EXPORT_METRICS

            raw.startsWith("No PMKID or M2/M3 handshakes found") || raw.contains("hc22000 export failed") -> ResponseType.CAPTURE_EXPORT_RESULT

            raw.startsWith("[ARP Poison] State:") || raw.startsWith("[ARP Poison] Not running") ||
                raw.startsWith("[ARP Poison] Stopped.") -> ResponseType.ETH_POISON_STATUS

            Regex("^\\[ARP Poison] Captured (domains|cookies|credentials) \\(\\d+\\):").containsMatchIn(raw.trim()) -> ResponseType.ETH_POISON_ITEM_HEADER

            // ethpoison item: "1. ad.com" (numbered list entry)
            Regex("^\\d+\\.\\s+\\S+").containsMatchIn(raw) && raw.split(" ").size >= 2 -> ResponseType.ETH_POISON_ITEM

            raw == "=== DNS Sinkhole Status ===" -> ResponseType.SINKHOLE_STATUS_HEADER

            Regex("^(State|Queries|Blocked|Block %|Logging|Blocklist):").containsMatchIn(raw) ||
                Regex("^IP: .*:53$").containsMatchIn(raw) -> ResponseType.SINKHOLE_STATUS_LINE

            Regex("^Sinkhole: \\d+ queries, \\d+ blocked, \\d+ dropped").containsMatchIn(raw.trim()) -> ResponseType.SINKHOLE_LIVE

            raw.startsWith("WebUI AP-only restriction") -> ResponseType.WEBUI_AP_STATE

            raw.startsWith("Web authentication") -> ResponseType.WEB_AUTH_RESULT

            // BLE advertiser live single-line format: "[N] Advertiser | MAC | RSSI dBm | AdvType | ..."
            raw.startsWith("[") && Regex("^\\[\\d+]\\s*(Advertiser|iBeacon)\\s*\\|").containsMatchIn(raw) -> ResponseType.ADVERTISER_DEVICE

            // BLE advertiser detail block (listadv): "[N] BLE Advertiser"/"[N] iBeacon" + "Adv Type:"/"MAC:" fields
            raw.startsWith("[") && raw.contains("Adv Type:") && raw.contains("MAC:") -> ResponseType.ADVERTISER_DEVICE_DETAIL

            raw.contains("Flipper") && raw.contains("Found") -> ResponseType.FLIPPER_DEVICE

            raw.contains("AirTag") && raw.contains("Found") -> ResponseType.AIRTAG_DEVICE

            // listflippers/listairtags static output: "[N] MAC: ..., Name: ..." (Flipper) or "[N] MAC: ..., RSSI: ... dBm (...)" (AirTag)
            raw.startsWith("[") && raw.substringAfter("]").trimStart().startsWith("MAC:") && raw.contains("Name:") && raw.contains("RSSI:") -> ResponseType.FLIPPER_DEVICE

            raw.startsWith("[") && raw.substringAfter("]").trimStart().startsWith("MAC:") && raw.contains("RSSI:") && !raw.contains("Name:") -> ResponseType.AIRTAG_DEVICE

            raw.startsWith("[") && raw.contains("Name:") && raw.contains("MAC:") && !raw.contains("SSID:") -> ResponseType.GATT_DEVICE

            (raw.startsWith("Service:", ignoreCase = true) && raw.contains("handles", ignoreCase = true)) ||
                (raw.startsWith("[") && raw.contains("] Service:", ignoreCase = true) &&
                    raw.contains("UUID:", ignoreCase = true) && raw.contains("Handles:", ignoreCase = true)) -> ResponseType.GATT_SERVICE

            raw.startsWith("New Station:") || raw.contains("Station MAC:") || (raw.contains("Station:") && raw.contains("Associated AP:")) -> ResponseType.STATION

            raw.startsWith("BLE:") -> ResponseType.BLE_DEVICE

            raw.startsWith("NFC:") && raw.contains(" uid=") && raw.contains("atqa=") && !raw.contains("emulating") -> ResponseType.NFC_TAG

            raw.startsWith("NFC:") -> ResponseType.NFC_MESSAGE

            raw.trimStart().let { it.startsWith("PACS:") || it.startsWith("Encryption:") || it.startsWith("Auth failed") } -> ResponseType.NFC_MESSAGE

            raw.contains("Wardrive Info") && (raw.contains("APs:") || raw.contains("Logged:")) -> ResponseType.WARDDRIVE_STATS

            // Wardrive heartbeat new format: "GPS: Locked\nAPs: 9\nSats: 16/9\n..." or BLE: "GPS: Locked\nBLE: 16\n..."
            raw.startsWith("GPS:") && (raw.contains("APs:") || raw.contains("BLE:")) -> ResponseType.WARDDRIVE_STATS

            raw.contains("GPS Info") -> ResponseType.GPS_POSITION

            raw.contains("Wardrive:") && raw.contains("ap=") && raw.contains("logged=") -> ResponseType.WARDDRIVE_STATS

            raw.contains("Lat:") && raw.contains("Lon:") -> ResponseType.GPS_POSITION

            raw.contains("Wardrive:") && raw.contains("ap=") -> ResponseType.WARDDRIVE_STATS

            raw.contains("GPS Info") || raw.contains("Acquiring GPS") -> ResponseType.GPS_POSITION

            raw.startsWith("Status: UP") || raw.startsWith("Status: DOWN") -> ResponseType.ETH_INFO

            raw.startsWith("=== Ethernet Statistics ===") -> ResponseType.ETH_STATS

            // etharp entry: "192.168.1.5   aa:bb:cc:dd:ee:ff" (IP followed by a bare MAC, no other tokens)
            raw.trim().let { t -> t.contains(".") && t.contains(":") && t.split(Regex("\\s+")).size == 2 && t.substringAfterLast(" ").count { it == ':' } == 5 } -> ResponseType.ETH_ARP_RESULT

            raw.trim().endsWith("- OPEN") -> ResponseType.ETH_PORT_RESULT

            raw.trim().endsWith("- ALIVE") -> ResponseType.ETH_PING_RESULT

            raw.trim().let { t -> t.firstOrNull()?.isDigit() == true && (t.endsWith("ms") || t.endsWith("(timeout)")) } -> ResponseType.ETH_TRACE_HOP

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
