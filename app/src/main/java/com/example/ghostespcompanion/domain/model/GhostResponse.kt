package com.example.ghostespcompanion.domain.model

/**
 * GhostESP response models for parsing serial output
 * 
 * Optimized for performance with:
 * - Pre-compiled regex patterns
 * - Object pooling for frequent allocations
 * - Minimal string operations
 * - Direct byte parsing where possible
 */

// Pre-compiled regex patterns for performance
private object ResponsePatterns {
    // AP scan (multiline format from firmware):
    // [N] SSID: name,
    //      BSSID: XX:XX:XX:XX:XX:XX,
    //      RSSI: -XX,
    //      Channel: X,
    //      Band: XGHz,
    //      Security: XXX
    //      PMF: XXX (optional)
    //      Vendor: XXX (optional)
    val AP_INDEX = Regex("^\\[(\\d+)\\]\\s*SSID:")
    val AP_SSID = Regex("SSID:\\s*([^,\\n]+)")
    val AP_BSSID = Regex("BSSID:\\s*([0-9A-Fa-f:]{17})")
    val AP_RSSI = Regex("RSSI:\\s*(-?\\d+)")
    val AP_CHANNEL = Regex("Channel:\\s*(\\d+)")
    val AP_SECURITY = Regex("Security:\\s*(\\S+)")
    val AP_PMF = Regex("PMF:\\s*(\\S+)")
    val AP_VENDOR = Regex("Vendor:\\s*(.+?)(?:\\n|$)")
    val AP_BAND = Regex("Band:\\s*(\\S+)")
    
    // Flipper detection (multiline format from firmware):
    // [N] White/Black Flipper Found:
    //      MAC: XX:XX:XX:XX:XX:XX,
    //      Name: XXX,
    //      RSSI: -XX dBm
    val FLIPPER_INDEX = Regex("^\\[(\\d+)\\]\\s*(White|Black|Transparent)?\\s*Flipper\\s*Found", RegexOption.IGNORE_CASE)
    val FLIPPER_MAC = Regex("MAC:\\s*([0-9A-Fa-f:]{17})")
    val FLIPPER_NAME = Regex("Name:\\s*([^,\\n]+)")
    val FLIPPER_RSSI = Regex("RSSI:\\s*(-?\\d+)\\s*dBm")
    val FLIPPER_TYPE = Regex("(White|Black|Transparent)\\s*Flipper", RegexOption.IGNORE_CASE)
    
    // Station scan (multiline format from firmware):
    // Format 1 (indexed):
    // [N] Station MAC: XX:XX:XX:XX:XX:XX,
    //      Station Vendor: XXX,
    //      Associated AP: XXX,
    //      AP BSSID: XX:XX:XX:XX:XX:XX,
    //      AP Vendor: XXX
    // Format 2 (new station):
    // New Station:
    // Station: XX:XX:XX:XX:XX:XX,
    //      STA Vendor: XXX,
    //      Associated AP: XXX,
    //      AP BSSID: XX:XX:XX:XX:XX:XX,
    //      AP Vendor: XXX
    val STATION_INDEX = Regex("^\\[(\\d+)\\]\\s*Station\\s*MAC:")
    val STATION_MAC = Regex("Station(?:\\s*MAC)?:\\s*([0-9A-Fa-f:]{17})")
    val STATION_VENDOR = Regex("(?:Station|STA)\\s*Vendor:\\s*([^,\\n]+)")
    val STATION_AP_SSID = Regex("Associated\\s*AP:\\s*([^,\\n]+)")
    val STATION_AP_BSSID = Regex("AP\\s*BSSID:\\s*([0-9A-Fa-f:]{17})")
    val STATION_AP_VENDOR = Regex("AP\\s*Vendor:\\s*([^,\\n]+)")
    val STATION_RSSI = Regex("RSSI:\\s*(-?\\d+)")
    
    // AirTag detection (multiline format from firmware):
    // [N] AirTag Found (Total: X)
    //      MAC: XX:XX:XX:XX:XX:XX,
    //      RSSI: -XX dBm (XXX),
    //      Payload: XX XX XX...
    val AIRTAG_INDEX = Regex("^\\[(\\d+)\\]\\s*AirTag\\s*Found")
    val AIRTAG_TOTAL = Regex("Total:\\s*(\\d+)")
    val AIRTAG_MAC = Regex("MAC:\\s*([0-9A-Fa-f:]{17})")
    val AIRTAG_RSSI = Regex("RSSI:\\s*(-?\\d+)\\s*dBm")
    val AIRTAG_PAYLOAD = Regex("Payload:\\s*([0-9A-Fa-f ]+)")
    val AIRTAG_RSSI_UPDATE = Regex("^\\[(\\d+)\\]\\s*AirTag\\s*RSSI\\s*Update:\\s*(-?\\d+)\\s*dBm")
    
    // BLE device: BLE: name | RSSI: -XX
    val BLE_NAME = Regex("BLE:\\s*(.+?)\\s*\\|")
    val BLE_RSSI = Regex("RSSI:\\s*(-?\\d+)")
    val BLE_MAC = Regex("([0-9A-Fa-f:]{17})")
    
    // Aerial device: [N] device_id\n    MAC: XX:XX:XX:XX:XX:XX\n    Type: XXX\n    RSSI: -XX dBm
    val AERIAL_INDEX = Regex("^\\[(\\d+)\\]")
    val AERIAL_ID = Regex("^\\[(?:\\d+)\\]\\s*(.+)$", RegexOption.MULTILINE)
    val AERIAL_MAC = Regex("MAC:\\s*([0-9A-Fa-f:]{17})")
    val AERIAL_TYPE = Regex("Type:\\s*(\\w+)")
    val AERIAL_RSSI = Regex("RSSI:\\s*(-?\\d+)")
    val AERIAL_VENDOR = Regex("Vendor:\\s*(.+)$", RegexOption.MULTILINE)
    val AERIAL_LOCATION = Regex("Location:\\s*(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)")
    val AERIAL_ALTITUDE = Regex("Altitude:\\s*(-?\\d+\\.\\d+)\\s*m")
    val AERIAL_SPEED = Regex("Speed:\\s*(-?\\d+\\.\\d+)\\s*m/s")
    val AERIAL_DIRECTION = Regex("@\\s*(-?\\d+\\.\\d+)°")
    val AERIAL_STATUS = Regex("Status:\\s*(\\w+)")
    val AERIAL_OPERATOR = Regex("Operator:\\s*(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)")
    val AERIAL_OPERATOR_ID = Regex("Operator ID:\\s*(.+)$", RegexOption.MULTILINE)
    val AERIAL_DESCRIPTION = Regex("Description:\\s*(.+)$", RegexOption.MULTILINE)
    val AERIAL_LAST_SEEN = Regex("Last seen:\\s*(\\d+)\\s*sec")
    
    // Aerial device tracking: AerialTrack XX:XX:XX:XX:XX:XX RSSI=-XXdBm age=XXus loc=lat,lon alt=X.Xm
    val AERIAL_TRACK = Regex("AerialTrack (\\S+) RSSI=(\\d+)dBm age=(\\d+)l?us?")
    val AERIAL_TRACK_LOCATION = Regex("AerialTrack (\\S+) RSSI=(\\d+)dBm age=(\\d+)l?us? loc=([-0-9.]+),([-0-9.]+)")
    val AERIAL_TRACK_FULL = Regex("AerialTrack (\\S+) RSSI=(\\d+)dBm age=(\\d+)l?us? loc=([-0-9.]+),([-0-9.]+) alt=([-0-9.]+)m")
    
    // SD entry: SD:FILE:[N] filename size or SD:DIR:[N] foldername (NO trailing slash!)
    // JavaScript reference: /^SD:FILE:\[(\d+)\]\s+(.+?)\s+(\d+)$/ and /^SD:DIR:\[(\d+)\]\s+(.+)$/
    val SD_FILE = Regex("SD:FILE:\\[(\\d+)\\]\\s+(.+?)\\s+(\\d+)$")
    val SD_DIR = Regex("SD:DIR:\\[(\\d+)\\]\\s+(.+)$")
    
    // SD responses: SD:OK:, SD:ERR:, SD:READ:, SD:WRITE:, SD:APPEND:, etc.
    val SD_OK = Regex("^SD:OK(:.*)?$")
    val SD_ERROR = Regex("^SD:ERR:([^:]+)(?::(.*))?$")
    val SD_INFO = Regex("SD:INFO:size=(\\d+)")
    val SD_SIZE = Regex("SD:SIZE:(\\d+)")
    val SD_READ_BEGIN = Regex("SD:READ:BEGIN:(.+)$")
    val SD_READ_SIZE = Regex("SD:READ:SIZE:(\\d+)$")
    val SD_READ_OFFSET = Regex("SD:READ:OFFSET:(\\d+)$")
    val SD_READ_LENGTH = Regex("SD:READ:LENGTH:(\\d+)$")
    val SD_READ_END = Regex("SD:READ:END:bytes=(\\d+)$")
    val SD_WRITE = Regex("SD:WRITE:bytes=(\\d+)$")
    val SD_APPEND = Regex("SD:APPEND:bytes=(\\d+)$")
    val SD_LISTED = Regex("SD:OK:listed (\\d+) entries")
    val SD_TREE = Regex("SD:OK:tree (\\d+) items")
    val SD_CREATED = Regex("SD:OK:created:(.+)$")
    val SD_REMOVED = Regex("SD:OK:removed:(.+)$")
    val SD_APPENDED = Regex("SD:OK:appended:(.+)$")
    
    // NFC Tag: NFC Tag Found: TYPE UID: XXXXXX
    val NFC_TYPE = Regex("NFC Tag Found:\\s*(\\w+)")
    val NFC_UID = Regex("UID:\\s*([0-9A-Fa-f]+)")
    
    // GPS position - firmware output format:
    // GPS Info
    // Fix: 3D/2D
    // Sats: 8/9 in view
    // Lat: 31deg 54.7830'S
    // Long: 115deg 51.6300'E
    // Alt: 15.1m
    // Speed: 0.0 km/h
    // Direction: 276° WNW
    // HDOP: 1.0
    val GPS_FIX = Regex("Fix:\\s*(\\S+)")
    val GPS_SATS = Regex("Sats:\\s*(\\d+)(?:/(\\d+))?")
    val GPS_LAT = Regex("Lat:\\s*(\\d+)deg\\s+([\\d.]+)'([NS])")
    val GPS_LON = Regex("Long:\\s*(\\d+)deg\\s+([\\d.]+)'([EW])")
    val GPS_ALT = Regex("Alt:\\s*([\\d.]+)m")
    val GPS_SPEED = Regex("Speed:\\s*([\\d.]+)\\s*km/h")
    val GPS_DIRECTION = Regex("Direction:\\s*(\\d+)°\\s*(\\S+)")
    val GPS_HDOP = Regex("HDOP:\\s*([\\d.]+)")
    
    // Wardrive heartbeat - firmware output format:
    // Wardrive: ap=123 logged=45/67 gpsrej=3 ch=1 up=0m42s gps=No Fix/3 pending=0B
    val WARDDRIVE_HEARTBEAT = Regex(
        "Wardrive:\\s*ap=(\\d+)\\s+logged=(\\d+)/(\\d+)\\s+gpsrej=(\\d+)\\s+ch=(\\d+)\\s+up=(\\d+)m(\\d+)s\\s+gps=([^/]+)/(\\d+)(?:\\s+sats=(\\d+))?\\s+pending=(\\d+)B"
    )
    
    // Wardrive multiline info - firmware output format (like GPS):
    // Wardrive Info
    // APs: 123
    // Logged: 45/67
    // GPS Fix: 3D/8
    // Channel: 1
    // Uptime: 0m42s
    // Pending: 0B
    // BLE: 50
    val WARDDRIVE_INFO = Regex("Wardrive\\s+Info", RegexOption.IGNORE_CASE)
    val WARDDRIVE_APS = Regex("APs:\\s*(\\d+)")
    val WARDDRIVE_LOGGED = Regex("Logged:\\s*(\\d+)/(\\d+)")
    val WARDDRIVE_GPS_FIX = Regex("GPS Fix:\\s*([^/]+)/(\\d+)")
    val WARDDRIVE_CHANNEL = Regex("Channel:\\s*(\\d+)")
    val WARDDRIVE_UPTIME = Regex("Uptime:\\s*(\\d+)m(\\d+)s")
    val WARDDRIVE_PENDING = Regex("Pending:\\s*(\\d+)B")
    val WARDDRIVE_BLE = Regex("BLE:\\s*(\\d+)")
    
    // Wardrive heartbeat new firmware format:
    // GPS: Locked
    // APs: 9
    // Sats: 16/9
    // Speed: 0.5 km/h
    // Accuracy: Good
    val WARDRIVE_GPS_STATUS = Regex("^GPS:\\s*(.+)", RegexOption.MULTILINE)
    val WARDRIVE_SATS = Regex("Sats:\\s*(\\d+)(?:/(\\d+))?")
    val WARDRIVE_ACCURACY = Regex("Accuracy:\\s*(\\S+)")
    
    // Error/Success
    val ERROR = Regex("ERROR:\\s*(.+)")
    val SUCCESS = Regex("^OK:\\s*(.+)$", RegexOption.MULTILINE)
    val SD_SUCCESS = Regex("^SD:OK.*$")
    
    // Portal credentials — firmware logs: "Captured credentials: <email> / <password>"
    val PORTAL_CREDS = Regex("Captured credentials:\\s*(.+)\\s*/\\s*(.+)")
    
    // IR learned signal - firmware outputs: "Captured: <protocol> A:<addr> C:<cmd>" or "Captured RAW signal (<n> samples)"
    val IR_LEARNED_PARSED = Regex("Captured:\\s*(\\S+)\\s+A:0x([0-9A-Fa-f]+)\\s+C:0x([0-9A-Fa-f]+)")
    val IR_LEARNED_RAW = Regex("Captured RAW signal\\s*\\((\\d+)\\s+samples\\)")
    val IR_LEARN_SAVED = Regex("Saved to\\s+(.+)")
    val IR_LEARN_TIMEOUT = Regex("Timeout, no signal received")
    val IR_LEARN_TASK_STARTED = Regex("IR learn task started")
    val IR_LEARN_WAITING = Regex("Waiting for IR signal")
    val IR_DAZZLER = Regex("IR_DAZZLER:(\\w+)")
    val IR_SIGNAL = Regex("IR: signal (.+)$")
    val IR_SEND_OK = Regex("IR: send OK")
    
    // Device identification
    val GHOSTESP_OK = Regex("GHOSTESP_OK")
    
    // Settings
    val SETTINGS_KEY_VALUE = Regex("([\\w_]+)\\s*=\\s*(.+)")
    
    // Scan status messages
    val SCAN_PHASE = Regex("(Phase \\d+):\\s*(\\w+)")
    val SCAN_COMPLETE = Regex("Scan Complete", RegexOption.IGNORE_CASE)
    val SCAN_STARTED = Regex("Scan Started", RegexOption.IGNORE_CASE)
    val SCAN_STOPPED = Regex("Scan Stopped", RegexOption.IGNORE_CASE)
    
    // BLE status
    val BLE_STACK_READY = Regex("BLE stack ready", RegexOption.IGNORE_CASE)
    val BLE_STACK_NOT_READY = Regex("BLE stack not ready", RegexOption.IGNORE_CASE)
    val BLE_SCAN_STARTED = Regex("Starting BLE scan", RegexOption.IGNORE_CASE)
    val BLE_SCAN_STOPPED = Regex("Stopping BLE scan", RegexOption.IGNORE_CASE)
    
    // Network tool outputs
    val PORT_SCAN_RESULT = Regex("Port (\\d+):\\s*(\\w+)")
    val ARP_ENTRY = Regex("(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+(\\w{2}:\\w{2}:\\w{2}:\\w{2}:\\w{2}:\\w{2})")
    
    // AP/Station Tracking: ##### -XX dBm (min:-XX max:-XX) [↑ CLOSER|↓ FARTHER]
    val TRACK_RSSI = Regex("#####\\s+(-?\\d+)\\s*dBm\\s*\\(min:(-?\\d+)\\s+max:(-?\\d+)\\)")
    // GATT/BLE Tracking: [##] RSSI: -XX dBm, Min: -XX, Max: -XX, CLOSER/FARTHER
    val TRACK_RSSI_GATT = Regex("\\[[#]+\\]\\s*RSSI:\\s*(-?\\d+)\\s*dBm,\\s*Min:\\s*(-?\\d+),\\s*Max:\\s*(-?\\d+)(?:,\\s*(CLOSER|FARTHER))?")
    val TRACK_CLOSER = Regex("(↑\\s*)?CLOSER")
    val TRACK_FARTHER = Regex("(↓\\s*)?FARTHER")
    // Flipper tracking: Tracking Flipper N: RSSI -XX dBm (proximity)
    val TRACK_FLIPPER = Regex("Tracking Flipper (\\d+):\\s*RSSI\\s*(-?\\d+)\\s*dBm(?:\\s*\\(([^)]+)\\))?", RegexOption.IGNORE_CASE)
    
    // Tracking header: === tracking ap: SSID ===
    val TRACK_HEADER = Regex("===\\s*tracking\\s+(ap|sta):\\s*(.+)\\s*===")
    val TRACK_BSSID = Regex("bssid:\\s*([0-9A-Fa-f:]{17})")
    val TRACK_CHANNEL = Regex("channel:\\s*(\\d+)")
    
    // Handshake detection:
    // Handshake found!
    // AP=24:2f:d0:90:dd:70
    // Pair=M1/M2
    val HANDSHAKE_AP = Regex("AP=([0-9A-Fa-f:]{17})", RegexOption.IGNORE_CASE)
    val HANDSHAKE_PAIR = Regex("Pair=(\\S+)", RegexOption.IGNORE_CASE)
    
    // PCAP file path:
    // PCAP: saving to SD as /mnt/ghostesp/pcaps/eapolscan_1.pcap
    val PCAP_PATH = Regex("/[^\\s]+\\.pcap")
    
    // WiFi Connection status:
    // Got IP: 192.168.1.100
    // WiFi Connected
    // WiFi Disconnected: reason (N)
    // WiFi disconnected manually
    // Attempting boot-time connection to saved network: SSID
    val GOT_IP = Regex("Got IP:\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)")
    val WIFI_CONNECTED = Regex("WiFi\\s+Connected", RegexOption.IGNORE_CASE)
    val WIFI_DISCONNECTED = Regex("WiFi\\s+[Dd]isconnected(?::\\s*(.+))?", RegexOption.IGNORE_CASE)
    val WIFI_CONNECTING = Regex("Attempting\\s+.*connection.*:\\s*(.+)", RegexOption.IGNORE_CASE)
    
    // WiFi Status (wifistatus command) - key=value format:
    // === WIFI STATUS ===
    // connected=true
    // has_saved_network=true
    // connected_ssid=MyNetwork
    // connected_rssi=-45
    // connected_bssid=AA:BB:CC:DD:EE:FF
    // connected_channel=6
    // saved_ssid=MyNetwork
    // === END STATUS ===
    val WIFI_STATUS_HEADER = Regex("===\\s*WIFI\\s*STATUS\\s*===")
    val WIFI_STATUS_FOOTER = Regex("===\\s*END\\s*STATUS\\s*===")
    val WIFI_STATUS_KEY_VALUE = Regex("^(\\w+)=(.*)$")
}

sealed class GhostResponse {
    /** Raw unparsed response */
    data class Raw(val text: String) : GhostResponse()
    
    // ==================== WiFi Models ====================
    
    /** WiFi Access Point - optimized parsing for multiline firmware output */
    data class AccessPoint(
        val index: Int,
        val ssid: String,
        val bssid: String,
        val rssi: Int,
        val channel: Int,
        val security: String,
        val vendor: String? = null,
        val band: String? = null,
        val pmf: String? = null,
        val isHidden: Boolean = false
    ) : GhostResponse() {
        companion object {
            /**
             * Parse AP from firmware multiline output:
             * [N] SSID: name,
             *      BSSID: XX:XX:XX:XX:XX:XX,
             *      RSSI: -XX,
             *      Channel: X,
             *      Band: XGHz,
             *      Security: XXX
             *      PMF: XXX (optional)
             *      Vendor: XXX (optional)
             */
            fun parse(text: String): AccessPoint? {
                // Quick check for AP format before expensive regex
                if (!text.contains("[") || !text.contains("SSID:")) return null
                
                val index = ResponsePatterns.AP_INDEX.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                val ssid = ResponsePatterns.AP_SSID.find(text)?.groupValues?.get(1)?.trim() ?: return null
                val bssid = ResponsePatterns.AP_BSSID.find(text)?.groupValues?.get(1) ?: "??:??:??:??:??:??"
                val rssi = ResponsePatterns.AP_RSSI.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: -100
                val channel = ResponsePatterns.AP_CHANNEL.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                
                // Optional fields
                val security = ResponsePatterns.AP_SECURITY.find(text)?.groupValues?.get(1)?.trim() ?: "Unknown"
                val vendor = ResponsePatterns.AP_VENDOR.find(text)?.groupValues?.get(1)?.trim()
                val band = ResponsePatterns.AP_BAND.find(text)?.groupValues?.get(1)?.trim()
                val pmf = ResponsePatterns.AP_PMF.find(text)?.groupValues?.get(1)?.trim()
                
                return AccessPoint(
                    index = index,
                    ssid = ssid,
                    bssid = bssid,
                    rssi = rssi,
                    channel = channel,
                    security = security,
                    vendor = vendor,
                    band = band,
                    pmf = pmf,
                    isHidden = ssid.isEmpty() || ssid == "(Hidden)"
                )
            }
        }
    }
    
    /** WiFi Station - multiline format */
    data class Station(
        val index: Int,
        val mac: String,
        val vendor: String?,
        val associatedApSsid: String?,
        val apBssid: String?,
        val apVendor: String?,
        val rssi: Int = -100
    ) : GhostResponse() {
        companion object {
            // Counter for generating sequential indices for "New Station:" format
            private var stationCounter = 0
            
            /**
             * Parse Station from firmware multiline output:
             * Format 1 (indexed):
             * [N] Station MAC: XX:XX:XX:XX:XX:XX,
             *      Station Vendor: XXX,
             *      Associated AP: XXX,
             *      AP BSSID: XX:XX:XX:XX:XX:XX,
             *      AP Vendor: XXX
             * Format 2 (new station):
             * New Station:
             * Station: XX:XX:XX:XX:XX:XX,
             *      STA Vendor: XXX,
             *      Associated AP: XXX,
             *      AP BSSID: XX:XX:XX:XX:XX:XX,
             *      AP Vendor: XXX
             */
            fun parse(text: String): Station? {
                // Check for either format
                if (!text.contains("Station MAC:") && !text.contains("Station:") && !text.contains("New Station:")) return null
                
                // Try to get index from [N] format first
                val index = ResponsePatterns.STATION_INDEX.find(text)?.groupValues?.get(1)?.toIntOrNull()
                    // For "New Station:" format, use sequential counter
                    ?: stationCounter++
                    
                val mac = ResponsePatterns.STATION_MAC.find(text)?.groupValues?.get(1) ?: return null
                val vendor = ResponsePatterns.STATION_VENDOR.find(text)?.groupValues?.get(1)?.trim()
                val associatedApSsid = ResponsePatterns.STATION_AP_SSID.find(text)?.groupValues?.get(1)?.trim()
                val apBssid = ResponsePatterns.STATION_AP_BSSID.find(text)?.groupValues?.get(1)
                val apVendor = ResponsePatterns.STATION_AP_VENDOR.find(text)?.groupValues?.get(1)?.trim()
                val rssi = ResponsePatterns.STATION_RSSI.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: -100
                
                return Station(
                    index = index,
                    mac = mac,
                    vendor = vendor,
                    associatedApSsid = associatedApSsid,
                    apBssid = apBssid,
                    apVendor = apVendor,
                    rssi = rssi
                )
            }
            
            /**
             * Reset the station counter (call when clearing stations)
             */
            fun resetCounter() {
                stationCounter = 0
            }
        }
    }
    
    // ==================== Tracking Models ====================
    
    /**
     * AP/Station tracking data
     * Format: ##### -XX dBm (min:-XX max:-XX) [↑ CLOSER|↓ FARTHER]
     */
    data class TrackData(
        val rssi: Int,
        val minRssi: Int,
        val maxRssi: Int,
        val direction: TrackDirection = TrackDirection.STABLE,
        val targetName: String? = null,
        val targetBssid: String? = null,
        val channel: Int? = null
    ) : GhostResponse() {
        companion object {
            /**
             * Parse tracking data from firmware output.
             * WiFi format: ##### -40 dBm (min:-40 max:-39)
             * GATT format: [##] RSSI: -75 dBm, Min: -81, Max: -75, CLOSER
             */
            fun parse(line: String): TrackData? {
                if (!line.contains("dBm")) return null
                
                if (line.contains("#####")) {
                    val rssi = ResponsePatterns.TRACK_RSSI.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                    val minRssi = ResponsePatterns.TRACK_RSSI.find(line)?.groupValues?.get(2)?.toIntOrNull() ?: rssi
                    val maxRssi = ResponsePatterns.TRACK_RSSI.find(line)?.groupValues?.get(3)?.toIntOrNull() ?: rssi
                    
                    val direction = when {
                        ResponsePatterns.TRACK_CLOSER.containsMatchIn(line) -> TrackDirection.CLOSER
                        ResponsePatterns.TRACK_FARTHER.containsMatchIn(line) -> TrackDirection.FARTHER
                        else -> TrackDirection.STABLE
                    }
                    
                    return TrackData(
                        rssi = rssi,
                        minRssi = minRssi,
                        maxRssi = maxRssi,
                        direction = direction
                    )
                }
                
                if (line.contains("[#") && line.contains("RSSI:")) {
                    val match = ResponsePatterns.TRACK_RSSI_GATT.find(line) ?: return null
                    val rssi = match.groupValues[1].toIntOrNull() ?: return null
                    val minRssi = match.groupValues[2].toIntOrNull() ?: rssi
                    val maxRssi = match.groupValues[3].toIntOrNull() ?: rssi
                    val directionStr = match.groupValues.getOrNull(4)?.trim()
                    
                    val direction = when (directionStr) {
                        "CLOSER" -> TrackDirection.CLOSER
                        "FARTHER" -> TrackDirection.FARTHER
                        else -> TrackDirection.STABLE
                    }
                    
                    return TrackData(
                        rssi = rssi,
                        minRssi = minRssi,
                        maxRssi = maxRssi,
                        direction = direction
                    )
                }
                
                return null
            }
            
            /**
             * Parse tracking header from firmware output:
             * WiFi: === tracking ap: SSID ===
             * GATT: === Tracking Device ===
             */
            fun parseHeader(text: String): TrackHeader? {
                if (!text.contains("tracking", ignoreCase = true) && !text.contains("Tracking")) return null
                
                val type = ResponsePatterns.TRACK_HEADER.find(text)?.groupValues?.get(1)?.lowercase()
                val targetName = ResponsePatterns.TRACK_HEADER.find(text)?.groupValues?.get(2)?.trim()
                val bssid = ResponsePatterns.TRACK_BSSID.find(text)?.groupValues?.get(1)
                val channel = ResponsePatterns.TRACK_CHANNEL.find(text)?.groupValues?.get(1)?.toIntOrNull()
                
                if (text.contains("Tracking Device", ignoreCase = true)) {
                    val nameMatch = Regex("Name:\\s*([^,\\n]+)").find(text)
                    val macMatch = Regex("MAC:\\s*([0-9A-Fa-f:]{17})").find(text)
                    return TrackHeader(
                        isAp = false,
                        targetName = nameMatch?.groupValues?.get(1)?.trim(),
                        targetBssid = macMatch?.groupValues?.get(1),
                        channel = null
                    )
                }
                
                return TrackHeader(
                    isAp = type == "ap",
                    targetName = targetName,
                    targetBssid = bssid,
                    channel = channel
                )
            }
        }
    }
    
    /** Tracking header info */
    data class TrackHeader(
        val isAp: Boolean,
        val targetName: String?,
        val targetBssid: String?,
        val channel: Int?
    )
    
    /** Direction of movement relative to target */
    enum class TrackDirection {
        CLOSER, FARTHER, STABLE
    }
    
    // ==================== Handshake Models ====================
    
    /**
     * WPA/WPA2 Handshake capture
     * Format:
     * Handshake found!
     * AP=24:2f:d0:90:dd:70
     * Pair=M1/M2
     */
data class Handshake(
        val apBssid: String,
        val pairType: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : GhostResponse() {
        companion object {
            fun parse(text: String): Handshake? {
                if (!text.contains("Handshake found", ignoreCase = true)) return null
                
                val apBssid = ResponsePatterns.HANDSHAKE_AP.find(text)?.groupValues?.get(1) ?: return null
                val pairType = ResponsePatterns.HANDSHAKE_PAIR.find(text)?.groupValues?.get(1) ?: "Unknown"
                
                return Handshake(
                    apBssid = apBssid,
                    pairType = pairType
                )
            }
        }
    }
    
    /**
     * WiFi Connection status from firmware
     * Firmware logs:
     * - Got IP: 192.168.1.100
     * - WiFi Connected
     * - WiFi Disconnected: reason (N)
     * - WiFi disconnected manually
     * - Attempting boot-time connection to saved network: SSID
     */
    data class WifiConnection(
        val isConnected: Boolean,
        val ssid: String? = null,
        val ip: String? = null,
        val reason: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : GhostResponse() {
        companion object {
            fun parse(text: String): WifiConnection? {
                if (!text.contains("Got IP:") && 
                    !text.contains("WiFi Connected", ignoreCase = true) &&
                    !text.contains("WiFi Disconnected", ignoreCase = true) &&
                    !text.contains("WiFi disconnected", ignoreCase = true) &&
                    !text.contains("Attempting", ignoreCase = true)) return null
                
                return when {
                    text.contains("Got IP:") -> {
                        val ip = ResponsePatterns.GOT_IP.find(text)?.groupValues?.get(1)
                        WifiConnection(isConnected = true, ip = ip)
                    }
                    ResponsePatterns.WIFI_CONNECTED.containsMatchIn(text) -> {
                        WifiConnection(isConnected = true)
                    }
                    ResponsePatterns.WIFI_DISCONNECTED.containsMatchIn(text) -> {
                        val reason = ResponsePatterns.WIFI_DISCONNECTED.find(text)?.groupValues?.get(1)
                        WifiConnection(isConnected = false, reason = reason)
                    }
                    ResponsePatterns.WIFI_CONNECTING.containsMatchIn(text) -> {
                        val ssid = ResponsePatterns.WIFI_CONNECTING.find(text)?.groupValues?.get(1)?.trim()
                        WifiConnection(isConnected = false, ssid = ssid)
                    }
                    else -> null
                }
            }
        }
    }
    
    /**
     * WiFi Status from wifistatus command
     * Machine-parseable key=value format with header/footer markers:
     * === WIFI STATUS ===
     * connected=true
     * has_saved_network=true
     * connected_ssid=MyNetwork
     * connected_rssi=-45
     * connected_bssid=AA:BB:CC:DD:EE:FF
     * connected_channel=6
     * saved_ssid=MyNetwork
     * === END STATUS ===
     */
    data class WifiStatus(
        val connected: Boolean,
        val hasSavedNetwork: Boolean,
        val connectedSsid: String?,
        val connectedRssi: Int?,
        val connectedBssid: String?,
        val connectedChannel: Int?,
        val savedSsid: String?,
        val timestamp: Long = System.currentTimeMillis()
    ) : GhostResponse() {
        companion object {
            fun parse(text: String): WifiStatus? {
                if (!text.contains("=== WIFI STATUS ===") && 
                    !text.contains("connected=") &&
                    !ResponsePatterns.WIFI_STATUS_HEADER.containsMatchIn(text)) return null
                
                val values = mutableMapOf<String, String>()
                
                // Parse all key=value lines
                text.lines().forEach { line ->
                    val trimmed = line.trim()
                    val match = ResponsePatterns.WIFI_STATUS_KEY_VALUE.find(trimmed)
                    if (match != null) {
                        values[match.groupValues[1]] = match.groupValues[2]
                    }
                }
                
                // Check if we have the minimum required field
                if (!values.containsKey("connected")) return null
                
                return WifiStatus(
                    connected = values["connected"]?.toBooleanStrictOrNull() ?: false,
                    hasSavedNetwork = values["has_saved_network"]?.toBooleanStrictOrNull() ?: false,
                    connectedSsid = values["connected_ssid"]?.takeIf { it.isNotEmpty() },
                    connectedRssi = values["connected_rssi"]?.toIntOrNull(),
                    connectedBssid = values["connected_bssid"]?.takeIf { it.isNotEmpty() },
                    connectedChannel = values["connected_channel"]?.toIntOrNull(),
                    savedSsid = values["saved_ssid"]?.takeIf { it.isNotEmpty() }
                )
            }
        }
    }
    
    // ==================== BLE Models ====================
    
    /** BLE Device */
    data class BleDevice(
        val name: String?,
        val mac: String?,  // MAC is optional - generic BLE scan doesn't include it
        val rssi: Int,
        val deviceType: BleDeviceType = BleDeviceType.GENERIC
    ) : GhostResponse() {
        /**
         * Generate a unique identifier for this device
         * Uses MAC if available, otherwise falls back to name + rssi
         */
        fun getUniqueId(): String = mac ?: "ble_${name ?: "unknown"}_$rssi"
        
        companion object {
            fun parse(line: String): BleDevice? {
                if (!line.startsWith("BLE:")) return null
                
                val name = ResponsePatterns.BLE_NAME.find(line)?.groupValues?.get(1)?.trim()
                val rssi = ResponsePatterns.BLE_RSSI.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: -100
                // MAC is optional - firmware format "BLE: name | RSSI: -XX" doesn't include MAC
                val mac = ResponsePatterns.BLE_MAC.find(line)?.groupValues?.get(1)
                
                val type = when {
                    name?.contains("Flipper", ignoreCase = true) == true -> BleDeviceType.FLIPPER_ZERO
                    name?.contains("AirTag", ignoreCase = true) == true -> BleDeviceType.AIR_TAG
                    name?.contains("iPhone", ignoreCase = true) == true -> BleDeviceType.IPHONE
                    name?.contains("Samsung", ignoreCase = true) == true -> BleDeviceType.SAMSUNG
                    name?.contains("Google", ignoreCase = true) == true -> BleDeviceType.GOOGLE
                    else -> BleDeviceType.GENERIC
                }
                
                return BleDevice(name = name, mac = mac, rssi = rssi, deviceType = type)
            }
        }
    }
    
    /** Flipper Zero device - multiline format */
    data class FlipperDevice(
        val index: Int,
        val name: String?,
        val mac: String,
        val rssi: Int,
        val flipperType: String // White, Black, or Transparent
    ) : GhostResponse() {
        companion object {
            /**
             * Parse Flipper detection from firmware multiline output:
             * [N] White/Black/Transparent Flipper Found:
             *      MAC: XX:XX:XX:XX:XX:XX,
             *      Name: XXX,
             *      RSSI: -XX dBm
             */
            fun parse(text: String): FlipperDevice? {
                if (!text.contains("Flipper") || !text.contains("Found")) return null
                
                val index = ResponsePatterns.FLIPPER_INDEX.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                val flipperType = ResponsePatterns.FLIPPER_TYPE.find(text)?.groupValues?.get(1)?.trim() ?: "Unknown"
                val mac = ResponsePatterns.FLIPPER_MAC.find(text)?.groupValues?.get(1) ?: return null
                val name = ResponsePatterns.FLIPPER_NAME.find(text)?.groupValues?.get(1)?.trim()
                val rssi = ResponsePatterns.FLIPPER_RSSI.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: -100
                
                return FlipperDevice(
                    index = index,
                    name = name,
                    mac = mac,
                    rssi = rssi,
                    flipperType = flipperType
                )
            }
        }
    }
    
    /** Flipper tracking data - emitted while a Flipper is selected for tracking */
    data class FlipperTrackData(
        val index: Int,
        val rssi: Int,
        val proximity: String? = null
    ) : GhostResponse() {
        companion object {
            /**
             * Parse Flipper tracking update from firmware output:
             * Tracking Flipper N: RSSI -XX dBm (proximity)
             */
            fun parse(line: String): FlipperTrackData? {
                val match = ResponsePatterns.TRACK_FLIPPER.find(line) ?: return null
                val index = match.groupValues[1].toIntOrNull() ?: return null
                val rssi = match.groupValues[2].toIntOrNull() ?: return null
                val proximity = match.groupValues.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }
                return FlipperTrackData(index = index, rssi = rssi, proximity = proximity)
            }
        }
    }

    /** AirTag device - multiline format */
    data class AirTagDevice(
        val index: Int,
        val mac: String,
        val rssi: Int,
        val total: Int,
        val payload: String? = null
    ) : GhostResponse() {
        companion object {
            /**
             * Parse AirTag detection from firmware multiline output:
             * [N] AirTag Found (Total: X)
             *      MAC: XX:XX:XX:XX:XX:XX,
             *      RSSI: -XX dBm (XXX),
             *      Payload: XX XX XX...
             */
            fun parse(text: String): AirTagDevice? {
                if (!text.contains("AirTag")) return null
                
                val index = ResponsePatterns.AIRTAG_INDEX.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                val total = ResponsePatterns.AIRTAG_TOTAL.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val mac = ResponsePatterns.AIRTAG_MAC.find(text)?.groupValues?.get(1) ?: return null
                val rssi = ResponsePatterns.AIRTAG_RSSI.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: -100
                val payload = ResponsePatterns.AIRTAG_PAYLOAD.find(text)?.groupValues?.get(1)?.trim()
                
                return AirTagDevice(
                    index = index,
                    mac = mac,
                    rssi = rssi,
                    total = total,
                    payload = payload
                )
            }
        }
    }
    
    /** GATT Device - multiline format from blescan -g */
    data class GattDevice(
        val index: Int,
        val name: String?,
        val mac: String,
        val rssi: Int,
        val type: String? = null
    ) : GhostResponse() {
        companion object {
            // GATT device patterns
            val GATT_INDEX = Regex("^\\[(\\d+)\\]\\s*Name:")
            val GATT_NAME = Regex("Name:\\s*([^,\\n]*)")
            val GATT_MAC = Regex("MAC:\\s*([0-9A-Fa-f:]{17})")
            val GATT_RSSI = Regex("RSSI:\\s*(-?\\d+)")
            val GATT_TYPE = Regex("Type:\\s*([^,\\n]+)")
            
            /**
             * Parse GATT device from firmware multiline output:
             * [N] Name: XXX,
             *      MAC: XX:XX:XX:XX:XX:XX,
             *      RSSI: -XX,
             *      Type: XXX (optional)
             */
            fun parse(text: String): GattDevice? {
                if (!text.contains("Name:") || !text.contains("MAC:") || text.contains("SSID:")) return null
                
                val index = GATT_INDEX.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                val name = GATT_NAME.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                val mac = GATT_MAC.find(text)?.groupValues?.get(1) ?: return null
                val rssi = GATT_RSSI.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: -100
                val type = GATT_TYPE.find(text)?.groupValues?.get(1)?.trim()
                
                return GattDevice(
                    index = index,
                    name = name,
                    mac = mac,
                    rssi = rssi,
                    type = type
                )
            }
        }
    }
    
    enum class BleDeviceType {
        GENERIC, FLIPPER_ZERO, AIR_TAG, IPHONE, SAMSUNG, GOOGLE, GATT_DEVICE
    }
    
    // ==================== GATT Service Models ====================
    
    /** GATT Service from enumgatt command */
    data class GattService(
        val uuid: String,
        val name: String? = null,
        val startHandle: Int,
        val endHandle: Int,
        val characteristics: List<GattCharacteristic> = emptyList()
    ) : GhostResponse() {
        companion object {
            private val SERVICE_PATTERN = Regex("Service:\\s*(.+?)\\s*\\((0x[0-9A-Fa-f]{4}|0x[0-9A-Fa-f]+)\\)\\s*handles\\s*(\\d+)-(\\d+)", RegexOption.IGNORE_CASE)
            private val SERVICE_UUID_PATTERN = Regex("\\(([0-9A-Fa-f]{4})\\)")
            
            fun parse(line: String): GattService? {
                val match = SERVICE_PATTERN.find(line) ?: return null
                val name = match.groupValues[1].trim()
                val uuid = match.groupValues[2]
                val startHandle = match.groupValues[3].toIntOrNull() ?: return null
                val endHandle = match.groupValues[4].toIntOrNull() ?: return null
                return GattService(
                    uuid = uuid,
                    name = name.takeIf { it.isNotEmpty() && it != "Unknown" },
                    startHandle = startHandle,
                    endHandle = endHandle
                )
            }
        }
    }
    
    /** GATT Characteristic */
    data class GattCharacteristic(
        val uuid: String,
        val name: String? = null,
        val handle: Int,
        val properties: List<String> = emptyList(),
        val value: String? = null
    ) : GhostResponse() {
        companion object {
            private val CHAR_PATTERN = Regex("Characteristic:\\s*(.+?)\\s*\\((0x[0-9A-Fa-f]+)\\)", RegexOption.IGNORE_CASE)
            private val HANDLE_PATTERN = Regex("handle[:\\s]+(\\d+)", RegexOption.IGNORE_CASE)
            private val PROPS_PATTERN = Regex("properties[:\\s]+\\[([^\\]]+)\\]", RegexOption.IGNORE_CASE)
            private val VALUE_PATTERN = Regex("value[:\\s]+(.+)$", RegexOption.IGNORE_CASE)
            
            fun parse(line: String): GattCharacteristic? {
                val match = CHAR_PATTERN.find(line) ?: return null
                val name = match.groupValues[1].trim()
                val uuid = match.groupValues[2]
                val handle = HANDLE_PATTERN.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val props = PROPS_PATTERN.find(line)?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList()
                val value = VALUE_PATTERN.find(line)?.groupValues?.get(1)?.trim()
                return GattCharacteristic(
                    uuid = uuid,
                    name = name.takeIf { it.isNotEmpty() && it != "Unknown" },
                    handle = handle,
                    properties = props,
                    value = value
                )
            }
        }
    }
    
    // ==================== Aerial (Drone) Models ====================
    
    /** Aerial device detection */
    data class AerialDevice(
        val index: Int,
        val deviceId: String,
        val mac: String,
        val type: AerialType,
        val rssi: Int,
        val vendor: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val altitude: Float? = null,
        val speed: Float? = null,
        val direction: Float? = null,
        val status: String? = null,
        val operatorLatitude: Double? = null,
        val operatorLongitude: Double? = null,
        val operatorId: String? = null,
        val description: String? = null,
        val lastSeenSec: Int = 0
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): AerialDevice? {
                val trimmed = line.trim()
                if (!trimmed.startsWith("[") || !trimmed.contains("MAC:")) return null
                
                val index = ResponsePatterns.AERIAL_INDEX.find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                val mac = ResponsePatterns.AERIAL_MAC.find(trimmed)?.groupValues?.get(1) ?: return null
                val typeStr = ResponsePatterns.AERIAL_TYPE.find(trimmed)?.groupValues?.get(1) ?: return null
                val rssi = ResponsePatterns.AERIAL_RSSI.find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                
                // Extract device ID from first line
                val deviceId = trimmed.lineSequence().firstOrNull()
                    ?.removePrefix("[$index]")
                    ?.trim() ?: "Unknown"
                
                val type = when (typeStr.uppercase()) {
                    "DRONE" -> AerialType.DRONE
                    "REMOTE" -> AerialType.REMOTE
                    "BEACON" -> AerialType.BEACON
                    "WIRED" -> AerialType.WIRED
                    else -> AerialType.UNKNOWN
                }
                
                val vendor = ResponsePatterns.AERIAL_VENDOR.find(trimmed)?.groupValues?.get(1)?.trim()
                
                val locMatch = ResponsePatterns.AERIAL_LOCATION.find(trimmed)
                val latitude = locMatch?.groupValues?.get(1)?.toDoubleOrNull()
                val longitude = locMatch?.groupValues?.get(2)?.toDoubleOrNull()
                val altitude = ResponsePatterns.AERIAL_ALTITUDE.find(trimmed)?.groupValues?.get(1)?.toFloatOrNull()
                val speed = ResponsePatterns.AERIAL_SPEED.find(trimmed)?.groupValues?.get(1)?.toFloatOrNull()
                val direction = ResponsePatterns.AERIAL_DIRECTION.find(trimmed)?.groupValues?.get(1)?.toFloatOrNull()
                val status = ResponsePatterns.AERIAL_STATUS.find(trimmed)?.groupValues?.get(1)
                
                val opMatch = ResponsePatterns.AERIAL_OPERATOR.find(trimmed)
                val operatorLatitude = opMatch?.groupValues?.get(1)?.toDoubleOrNull()
                val operatorLongitude = opMatch?.groupValues?.get(2)?.toDoubleOrNull()
                val operatorId = ResponsePatterns.AERIAL_OPERATOR_ID.find(trimmed)?.groupValues?.get(1)?.trim()
                val description = ResponsePatterns.AERIAL_DESCRIPTION.find(trimmed)?.groupValues?.get(1)?.trim()
                val lastSeenSec = ResponsePatterns.AERIAL_LAST_SEEN.find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                
                return AerialDevice(
                    index = index,
                    deviceId = deviceId,
                    mac = mac,
                    type = type,
                    rssi = rssi,
                    vendor = vendor,
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitude,
                    speed = speed,
                    direction = direction,
                    status = status,
                    operatorLatitude = operatorLatitude,
                    operatorLongitude = operatorLongitude,
                    operatorId = operatorId,
                    description = description,
                    lastSeenSec = lastSeenSec
                )
            }
        }
    }
    
    enum class AerialType {
        DRONE, REMOTE, BEACON, WIRED, UNKNOWN
    }
    
    // ==================== NFC Models ====================
    
    /** NFC Tag */
    data class NfcTag(
        val uid: String,
        val type: NfcTagType,
        val data: String? = null
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): NfcTag? {
                if (!line.contains("NFC Tag")) return null
                
                val typeStr = ResponsePatterns.NFC_TYPE.find(line)?.groupValues?.get(1) ?: return null
                val uid = ResponsePatterns.NFC_UID.find(line)?.groupValues?.get(1) ?: return null
                
                return NfcTag(
                    uid = uid,
                    type = when {
                        typeStr.contains("NTAG213", ignoreCase = true) -> NfcTagType.NTAG213
                        typeStr.contains("NTAG215", ignoreCase = true) -> NfcTagType.NTAG215
                        typeStr.contains("NTAG216", ignoreCase = true) -> NfcTagType.NTAG216
                        typeStr.contains("MIFARE", ignoreCase = true) -> NfcTagType.MIFARE_CLASSIC
                        typeStr.contains("DESFIRE", ignoreCase = true) -> NfcTagType.MIFARE_DESFIRE
                        else -> NfcTagType.UNKNOWN
                    },
                    data = line
                )
            }
        }
    }
    
    enum class NfcTagType {
        NTAG213, NTAG215, NTAG216, MIFARE_CLASSIC, MIFARE_DESFIRE, UNKNOWN
    }
    
    // ==================== Status Models ====================
    
    /**
     * Device feature flags parsed from chipinfo output
     */
    enum class DeviceFeature {
        DISPLAY,
        TOUCHSCREEN,
        STATUS_DISPLAY,
        NFC,
        BADUSB,
        INFRARED_TX,
        INFRARED_RX,
        GPS,
        ETHERNET,
        BATTERY,
        BATTERY_ADC,
        FUEL_GAUGE,
        RTC_CLOCK,
        COMPASS,
        ACCELEROMETER,
        JOYSTICK,
        CARDPUTER,
        TDECK,
        ROTARY_ENCODER,
        USB_KEYBOARD,
        GHOST_BOARD,
        S3TWATCH,
        SD_CARD_SPI,
        SD_CARD_MMC
    }

    /**
     * Device information from chipinfo command
     * Firmware output format:
     *   Chip Information:
     *     Model: ESP32-XXX
     *     Revision: vX.X
     *     CPU Cores: X
     *     Features: WiFi/BT/BLE/802.15.4/Embedded Flash/Embedded PSRAM
     *     Free Heap: XXX bytes
     *     Min Free Heap: XXX bytes
     *     IDF Version: X.X.X
     *     Build Config: XXX (optional)
     *   
     *   Enabled Features:
     *     Display
     *     NFC
     *     BadUSB
     *     ...
     */
    data class DeviceInfo(
        val model: String,
        val revision: String,
        val cores: Int,
        val features: String,
        val freeHeap: Long,
        val minFreeHeap: Long,
        val idfVersion: String,
        val buildConfig: String? = null,
        val enabledFeatures: Set<DeviceFeature> = emptySet(),
        val firmwareVersion: String? = null,
        val gitCommit: String? = null,
        val rawResponse: String? = null,
        val parseErrors: List<String> = emptyList()
    ) : GhostResponse() {
        fun hasFeature(feature: DeviceFeature): Boolean = enabledFeatures.contains(feature)
        
        companion object {
            private val MODEL_PATTERN = Regex("Model:\\s*([^,\\s\\n]+(?:\\s+[^,\\s\\n]+)*?)(?=\\s*(?:[,\\n]|$))")
            private val REVISION_PATTERN = Regex("Revision:\\s*v?(\\d+(?:\\.\\d+)+)")
            private val CORES_PATTERN = Regex("CPU Cores:\\s*(\\d+)")
            // Features uses "/" as separator (WiFi/BLE/802.15.4/…) — stop at comma or newline
            private val FEATURES_PATTERN = Regex("(?<!Enabled )Features:\\s*([^,\\n]+)")
            private val FREE_HEAP_PATTERN = Regex("Free Heap:\\s*(\\d+)")
            private val MIN_FREE_HEAP_PATTERN = Regex("Min Free Heap:\\s*(\\d+)")
            private val IDF_VERSION_PATTERN = Regex("IDF Version:\\s*([^,\\s\\n]+)")
            private val BUILD_CONFIG_PATTERN = Regex("Build Config:\\s*([^,\\n]+)")
            private val FIRMWARE_PATTERN = Regex("Firmware:\\s*([^,\\n]+)")
            private val GIT_COMMIT_PATTERN = Regex("Git Commit:\\s*([a-fA-F0-9]+)")
            private val FIRMWARE_VERSION_PATTERN = Regex("Firmware Version:\\s*(v?[\\d.]+)")
            
            private val FEATURE_MAPPING = mapOf(
                "Display" to DeviceFeature.DISPLAY,
                "Touchscreen" to DeviceFeature.TOUCHSCREEN,
                "Status Display (OLED)" to DeviceFeature.STATUS_DISPLAY,
                "Status Display" to DeviceFeature.STATUS_DISPLAY,
                "NFC" to DeviceFeature.NFC,
                "BadUSB" to DeviceFeature.BADUSB,
                "Infrared TX" to DeviceFeature.INFRARED_TX,
                "Infrared RX" to DeviceFeature.INFRARED_RX,
                "GPS" to DeviceFeature.GPS,
                "Ethernet" to DeviceFeature.ETHERNET,
                "Battery (Power Save)" to DeviceFeature.BATTERY,
                "Battery ADC" to DeviceFeature.BATTERY_ADC,
                "Fuel Gauge" to DeviceFeature.FUEL_GAUGE,
                "RTC Clock" to DeviceFeature.RTC_CLOCK,
                "Compass" to DeviceFeature.COMPASS,
                "Accelerometer" to DeviceFeature.ACCELEROMETER,
                "Joystick" to DeviceFeature.JOYSTICK,
                "Cardputer" to DeviceFeature.CARDPUTER,
                "T-Deck" to DeviceFeature.TDECK,
                "Rotary Encoder" to DeviceFeature.ROTARY_ENCODER,
                "USB Keyboard (Host)" to DeviceFeature.USB_KEYBOARD,
                "Ghost Board" to DeviceFeature.GHOST_BOARD,
                "S3TWatch" to DeviceFeature.S3TWATCH,
                "SD Card (SPI)" to DeviceFeature.SD_CARD_SPI,
                "SD Card (MMC)" to DeviceFeature.SD_CARD_MMC
            )
            
            fun parse(text: String): DeviceInfo? {
                val isDeviceInfo = text.contains("Chip Information") ||
                    (text.contains("Model:") && text.contains("IDF Version:") && text.contains("CPU Cores:"))
                if (!isDeviceInfo) return null
                
                val errors = mutableListOf<String>()
                
                val model = MODEL_PATTERN.find(text)?.groupValues?.get(1)?.trim()
                    ?: run { errors.add("Failed to parse Model"); return null }
                val revision = REVISION_PATTERN.find(text)?.groupValues?.get(1)
                    ?: run { errors.add("Failed to parse Revision"); "0.0" }
                val cores = CORES_PATTERN.find(text)?.groupValues?.get(1)?.toIntOrNull()
                    ?: run { errors.add("Failed to parse CPU Cores"); 1 }
                val features = FEATURES_PATTERN.find(text)?.groupValues?.get(1)?.trim()
                    ?: run { errors.add("Failed to parse Features"); "Unknown" }
                val freeHeap = FREE_HEAP_PATTERN.find(text)?.groupValues?.get(1)?.toLongOrNull()
                    ?: run { errors.add("Failed to parse Free Heap"); 0L }
                val minFreeHeap = MIN_FREE_HEAP_PATTERN.find(text)?.groupValues?.get(1)?.toLongOrNull()
                    ?: run { errors.add("Failed to parse Min Free Heap"); 0L }
                val idfVersion = IDF_VERSION_PATTERN.find(text)?.groupValues?.get(1)?.trim()
                    ?: run { errors.add("Failed to parse IDF Version"); "Unknown" }
                val buildConfig = BUILD_CONFIG_PATTERN.find(text)?.groupValues?.get(1)?.trim()
                val firmware = FIRMWARE_PATTERN.find(text)?.groupValues?.get(1)?.trim()
                val firmwareVersion = FIRMWARE_VERSION_PATTERN.find(text)?.groupValues?.get(1)?.trim() ?: firmware
                val gitCommit = GIT_COMMIT_PATTERN.find(text)?.groupValues?.get(1)?.trim()
                
                val enabledFeatures = parseEnabledFeatures(text)
                if (enabledFeatures.isEmpty() && text.contains("Enabled Features:")) {
                    errors.add("Enabled Features section found but no features parsed")
                }
                
                return DeviceInfo(
                    model = model,
                    revision = revision,
                    cores = cores,
                    features = features,
                    freeHeap = freeHeap,
                    minFreeHeap = minFreeHeap,
                    idfVersion = idfVersion,
                    buildConfig = buildConfig,
                    enabledFeatures = enabledFeatures,
                    firmwareVersion = firmwareVersion,
                    gitCommit = gitCommit,
                    rawResponse = text,
                    parseErrors = errors
                )
            }
            
            private fun parseEnabledFeatures(text: String): Set<DeviceFeature> {
                // The multiline buffer joins all chipinfo lines with ", " (comma-space).
                // After "Enabled Features:," each feature name follows as a separate
                // comma-space-delimited token: "..., Enabled Features:, Display, NFC, ..."
                // We find the section marker and split everything after it.
                val marker = "Enabled Features:"
                val markerIndex = text.indexOf(marker)
                if (markerIndex == -1) return emptySet()
                
                val afterMarker = text.substring(markerIndex + marker.length)
                
                val features = mutableSetOf<DeviceFeature>()
                // Split on ", " OR "\n" to handle both the comma-joined buffer format
                // and any future raw-newline format
                afterMarker.split(", ", "\n").forEach { segment ->
                    val trimmed = segment.trim()
                    if (trimmed.isNotEmpty()) {
                        FEATURE_MAPPING[trimmed]?.let { features.add(it) }
                    }
                }
                return features
            }
        }
    }
    
    /** Scan status update */
    data class ScanStatus(
        val message: String,
        val progress: Float? = null,
        val type: ScanType,
        val phase: Int? = null
    ) : GhostResponse() {
        enum class ScanType { WIFI_AP, WIFI_STA, BLE, NFC, AERIAL, SWEEP }
    }
    
    /** Error response */
    data class Error(
        val message: String,
        val code: Int? = null
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): Error? {
                if (!line.startsWith("ERROR:")) return null
                val message = ResponsePatterns.ERROR.find(line)?.groupValues?.get(1)?.trim() ?: return null
                return Error(message = message)
            }
        }
    }
    
    /** Success response */
    data class Success(
        val message: String
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): Success? {
                if (!line.startsWith("OK:")) return null
                val message = ResponsePatterns.SUCCESS.find(line)?.groupValues?.get(1)?.trim() ?: return null
                return Success(message = message)
            }
        }
    }
    
    /** Device identification response */
    data object GhostEspOk : GhostResponse() {
        fun matches(line: String): Boolean = ResponsePatterns.GHOSTESP_OK.matches(line)
    }
    
    // ==================== GPS Models ====================
    
    /** GPS Position - parsed from firmware gpsinfo command output */
    data class GpsPosition(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double?,
        val speed: Float?,
        val satellites: Int,
        val satellitesInView: Int,
        val fix: Boolean,
        val fixType: String,
        val hdop: Float?,
        val direction: Int?,
        val directionName: String?
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): GpsPosition? {
                if (!line.contains("GPS Info") && !line.contains("Lat:") && !line.contains("Lon:")) return null
                
                val fixStr = ResponsePatterns.GPS_FIX.find(line)?.groupValues?.get(1) ?: "No Fix"
                val hasFix = fixStr.equals("3D", ignoreCase = true) || fixStr.equals("2D", ignoreCase = true) || fixStr.equals("Fix", ignoreCase = true)
                
                val satsMatch = ResponsePatterns.GPS_SATS.find(line)
                val satsUsed = satsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val satsInView = satsMatch?.groupValues?.get(2)?.toIntOrNull() ?: satsUsed
                
                val latMatch = ResponsePatterns.GPS_LAT.find(line)
                val lonMatch = ResponsePatterns.GPS_LON.find(line)
                
                if (latMatch == null || lonMatch == null) {
                    return GpsPosition(
                        latitude = 0.0,
                        longitude = 0.0,
                        altitude = null,
                        speed = null,
                        satellites = satsUsed,
                        satellitesInView = satsInView,
                        fix = hasFix,
                        fixType = fixStr,
                        hdop = null,
                        direction = null,
                        directionName = null
                    )
                }
                
                val latDeg = latMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                val latMin = latMatch.groupValues[2].toDoubleOrNull() ?: 0.0
                val latDir = latMatch.groupValues[3]
                val latitude = (latDeg + latMin / 60.0) * if (latDir == "S") -1.0 else 1.0
                
                val lonDeg = lonMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                val lonMin = lonMatch.groupValues[2].toDoubleOrNull() ?: 0.0
                val lonDir = lonMatch.groupValues[3]
                val longitude = (lonDeg + lonMin / 60.0) * if (lonDir == "W") -1.0 else 1.0
                
                val alt = ResponsePatterns.GPS_ALT.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                val speed = ResponsePatterns.GPS_SPEED.find(line)?.groupValues?.get(1)?.toFloatOrNull()
                val hdop = ResponsePatterns.GPS_HDOP.find(line)?.groupValues?.get(1)?.toFloatOrNull()
                
                val dirMatch = ResponsePatterns.GPS_DIRECTION.find(line)
                val direction = dirMatch?.groupValues?.get(1)?.toIntOrNull()
                val directionName = dirMatch?.groupValues?.get(2)
                
                return GpsPosition(
                    latitude = latitude,
                    longitude = longitude,
                    altitude = alt,
                    speed = speed,
                    satellites = satsUsed,
                    satellitesInView = satsInView,
                    fix = hasFix,
                    fixType = fixStr,
                    hdop = hdop,
                    direction = direction,
                    directionName = directionName
                )
            }
        }
    }
    
    /** Wardrive Statistics - parsed from firmware wardrive output (both heartbeat and multiline formats) */
    data class WardriveStats(
        val accessPoints: Int,
        val loggedOk: Int,
        val logAttempts: Int,
        val gpsRejected: Int,
        val channel: Int,
        val uptimeMinutes: Int,
        val uptimeSeconds: Int,
        val gpsFixStatus: String,
        val gpsSatellites: Int,
        val pendingBytes: Int,
        val bleDevices: Int = 0
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): WardriveStats? {
                 // Check for multiline format first (like GPS info)
                if (line.contains("Wardrive") && (line.contains("APs:") || line.contains("Logged:") || line.contains("GPS Fix:"))) {
                    val aps = ResponsePatterns.WARDDRIVE_APS.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val loggedMatch = ResponsePatterns.WARDDRIVE_LOGGED.find(line)
                    val loggedOk = loggedMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val logAttempts = loggedMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                    val gpsFixMatch = ResponsePatterns.WARDDRIVE_GPS_FIX.find(line)
                    val gpsFixStatus = gpsFixMatch?.groupValues?.get(1) ?: "No Fix"
                    val gpsSatellites = gpsFixMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                    val channel = ResponsePatterns.WARDDRIVE_CHANNEL.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val uptimeMatch = ResponsePatterns.WARDDRIVE_UPTIME.find(line)
                    val uptimeMinutes = uptimeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val uptimeSeconds = uptimeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                    val pendingBytes = ResponsePatterns.WARDDRIVE_PENDING.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val bleDevices = ResponsePatterns.WARDDRIVE_BLE.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    
                    return WardriveStats(
                        accessPoints = aps,
                        loggedOk = loggedOk,
                        logAttempts = logAttempts,
                        gpsRejected = 0,
                        channel = channel,
                        uptimeMinutes = uptimeMinutes,
                        uptimeSeconds = uptimeSeconds,
                        gpsFixStatus = gpsFixStatus,
                        gpsSatellites = gpsSatellites,
                        pendingBytes = pendingBytes,
                        bleDevices = bleDevices
                    )
                }
                
                // New firmware format: GPS: Locked\nAPs: 9\nSats: 16/9\nSpeed: 0.5 km/h\nAccuracy: Good
                // Also handles BLE wardrive: GPS: Locked\nBLE: 16\nSats: 6/9\nSpeed: 10.8 km/h\nAccuracy: Fair
                if (line.startsWith("GPS:") && (line.contains("APs:") || line.contains("BLE:"))) {
                    val gpsStatus = ResponsePatterns.WARDRIVE_GPS_STATUS.find(line)?.groupValues?.get(1)?.trim() ?: "Unknown"
                    val aps = ResponsePatterns.WARDDRIVE_APS.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val bleDevices = ResponsePatterns.WARDDRIVE_BLE.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val satsMatch = ResponsePatterns.WARDRIVE_SATS.find(line)
                    val satsUsed = satsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    
                    // Map firmware status to fix type
                    val fixStatus = when {
                        gpsStatus.equals("Locked", ignoreCase = true) -> "3D"
                        gpsStatus.contains("No", ignoreCase = true) -> "No Fix"
                        else -> gpsStatus
                    }
                    
                    return WardriveStats(
                        accessPoints = aps,
                        loggedOk = 0,
                        logAttempts = 0,
                        gpsRejected = 0,
                        channel = 0,
                        uptimeMinutes = 0,
                        uptimeSeconds = 0,
                        gpsFixStatus = fixStatus,
                        gpsSatellites = satsUsed,
                        pendingBytes = 0,
                        bleDevices = bleDevices
                    )
                }
                
                // Fallback to heartbeat format
                if (!line.contains("Wardrive:") || !line.contains("ap=")) return null
                
                val match = ResponsePatterns.WARDDRIVE_HEARTBEAT.find(line) ?: return null
                
                return WardriveStats(
                    accessPoints = match.groupValues[1].toIntOrNull() ?: 0,
                    loggedOk = match.groupValues[2].toIntOrNull() ?: 0,
                    logAttempts = match.groupValues[3].toIntOrNull() ?: 0,
                    gpsRejected = match.groupValues[4].toIntOrNull() ?: 0,
                    channel = match.groupValues[5].toIntOrNull() ?: 1,
                    uptimeMinutes = match.groupValues[6].toIntOrNull() ?: 0,
                    uptimeSeconds = match.groupValues[7].toIntOrNull() ?: 0,
                    gpsFixStatus = match.groupValues[8],
                    gpsSatellites = match.groupValues[9].toIntOrNull() ?: 0,
                    pendingBytes = match.groupValues[11].toIntOrNull() ?: 0,
                    bleDevices = 0
                )
            }
        }
    }
    
    // ==================== SD Card Models ====================
    
    /** SD Card file/directory entry - matches firmware format:
     * SD:FILE:[N] filename size
     * SD:DIR:[N] foldername (no trailing slash)
     */
    data class SdEntry(
        val index: Int,
        val name: String,
        val isDirectory: Boolean,
        val size: Long? = null,
        val path: String
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): SdEntry? {
                if (!line.startsWith("SD:")) return null
                
                // File format: SD:FILE:[N] filename size
                ResponsePatterns.SD_FILE.find(line)?.let { result ->
                    val index = result.groupValues[1].toIntOrNull() ?: return null
                    val name = result.groupValues[2].trim()
                    val size = result.groupValues.getOrNull(3)?.toLongOrNull()
                    return SdEntry(index = index, name = name, isDirectory = false, size = size, path = name)
                }
                
                // Directory format: SD:DIR:[N] foldername
                ResponsePatterns.SD_DIR.find(line)?.let { result ->
                    val index = result.groupValues[1].toIntOrNull() ?: return null
                    val name = result.groupValues[2].trim()
                    return SdEntry(index = index, name = name, isDirectory = true, path = name)
                }
                
                return null
            }
        }
    }
    
    /** SD Card status */
    data class SdStatus(
        val mounted: Boolean,
        val type: String,
        val capacity: Long,
        val used: Long,
        val available: Long
    ) : GhostResponse()
    
    /** SD Card operation result - handles all SD:OK and SD:ERR responses */
    data class SdOperationResult(
        val success: Boolean,
        val operation: String,
        val details: String? = null,
        val path: String? = null,
        val bytes: Long? = null
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): SdOperationResult? {
                if (!line.startsWith("SD:")) return null
                
                // Handle OK responses
                ResponsePatterns.SD_OK.find(line)?.let {
                    val detailPart = line.removePrefix("SD:OK").removePrefix(":")
                    
                    // Parse specific OK responses
                    ResponsePatterns.SD_LISTED.find(line)?.let { result ->
                        return SdOperationResult(success = true, operation = "listed", details = "${result.groupValues[1]} entries")
                    }
                    ResponsePatterns.SD_TREE.find(line)?.let { result ->
                        return SdOperationResult(success = true, operation = "tree", details = "${result.groupValues[1]} items")
                    }
                    ResponsePatterns.SD_CREATED.find(line)?.let { result ->
                        return SdOperationResult(success = true, operation = "created", path = result.groupValues[1].trim())
                    }
                    ResponsePatterns.SD_REMOVED.find(line)?.let { result ->
                        return SdOperationResult(success = true, operation = "removed", path = result.groupValues[1].trim())
                    }
                    ResponsePatterns.SD_APPENDED.find(line)?.let { result ->
                        return SdOperationResult(success = true, operation = "appended", path = result.groupValues[1].trim())
                    }
                    ResponsePatterns.SD_WRITE.find(line)?.let { result ->
                        return SdOperationResult(success = true, operation = "write", bytes = result.groupValues[1].toLongOrNull())
                    }
                    ResponsePatterns.SD_APPEND.find(line)?.let { result ->
                        return SdOperationResult(success = true, operation = "append", bytes = result.groupValues[1].toLongOrNull())
                    }
                    ResponsePatterns.SD_READ_END.find(line)?.let { result ->
                        return SdOperationResult(success = true, operation = "read_end", bytes = result.groupValues[1].toLongOrNull())
                    }
                    
                    // Generic OK
                    return SdOperationResult(success = true, operation = "OK", details = detailPart.ifEmpty { null })
                }
                
                // Handle error responses
                ResponsePatterns.SD_ERROR.find(line)?.let { result ->
                    val errorType = result.groupValues[1]
                    val errorDetail = result.groupValues.getOrNull(2)
                    return SdOperationResult(success = false, operation = errorType, details = errorDetail)
                }
                
                return null
            }
        }
    }
    
    /** SD File read result */
    data class SdReadResult(
        val filename: String,
        val size: Long,
        val offset: Long,
        val length: Long,
        val data: String? = null,
        val success: Boolean = true
    ) : GhostResponse()
    
    // ==================== Portal Models ====================
    
    /** Portal credentials captured */
    data class PortalCredentials(
        val username: String,
        val password: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): PortalCredentials? {
                if (!line.contains("Captured credentials:")) return null
                val match = ResponsePatterns.PORTAL_CREDS.find(line) ?: return null
                val username = match.groupValues[1].trim()
                val password = match.groupValues[2].trim()
                return PortalCredentials(username = username, password = password)
            }
        }
    }
    
    /** Portal info */
    data class PortalInfo(
        val name: String,
        val path: String
    ) : GhostResponse()
    
    // ==================== IR Models ====================
    
    /** IR learned signal - matches firmware output:
     *  Parsed: "Captured: <protocol> A:0x<addr> C:0x<cmd>"
     *  Raw: "Captured RAW signal (<samples> samples)"
     */
    data class IrLearned(
        val protocol: String?,
        val address: String?,
        val command: String?,
        val rawSamples: Int?,
        val filePath: String? = null
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): IrLearned? {
                // Parsed signal: Captured: NEC A:0x12345678 C:0x000000FF
                ResponsePatterns.IR_LEARNED_PARSED.find(line)?.let { match ->
                    return IrLearned(
                        protocol = match.groupValues[1],
                        address = match.groupValues[2],
                        command = match.groupValues[3],
                        rawSamples = null
                    )
                }
                // Raw signal: Captured RAW signal (120 samples)
                ResponsePatterns.IR_LEARNED_RAW.find(line)?.let { match ->
                    return IrLearned(
                        protocol = "RAW",
                        address = null,
                        command = null,
                        rawSamples = match.groupValues[1].toIntOrNull()
                    )
                }
                return null
            }
        }
    }
    
    /** IR learn saved to file - "Saved to /path/to/file.ir" */
    data class IrLearnSaved(val path: String) : GhostResponse() {
        companion object {
            fun parse(line: String): IrLearnSaved? {
                return ResponsePatterns.IR_LEARN_SAVED.find(line)?.let { match ->
                    IrLearnSaved(path = match.groupValues[1].trim())
                }
            }
        }
    }
    
    /** IR learn status messages */
    data class IrLearnStatus(
        val status: String, // STARTED, WAITING, TIMEOUT
        val message: String
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): IrLearnStatus? {
                return when {
                    ResponsePatterns.IR_LEARN_TASK_STARTED.containsMatchIn(line) -> 
                        IrLearnStatus("STARTED", line)
                    ResponsePatterns.IR_LEARN_WAITING.containsMatchIn(line) -> 
                        IrLearnStatus("WAITING", line)
                    ResponsePatterns.IR_LEARN_TIMEOUT.containsMatchIn(line) -> 
                        IrLearnStatus("TIMEOUT", line)
                    else -> null
                }
            }
        }
    }
    
    /** IR dazzler status */
    data class IrDazzlerStatus(
        val status: String // STARTED, STOPPING, NOT_RUNNING, ALREADY_RUNNING, FAILED
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): IrDazzlerStatus? {
                if (!line.startsWith("IR_DAZZLER:")) return null
                val status = ResponsePatterns.IR_DAZZLER.find(line)?.groupValues?.get(1) ?: return null
                return IrDazzlerStatus(status = status)
            }
        }
    }
    
    /** IR remote file from ir list command
     *  Format: [N] filename.ir or [N] filename.json
     *  Example: [0] Samsung.ir
     */
    data class IrRemote(
        val index: Int,
        val filename: String
    ) : GhostResponse() {
        companion object {
            private val IR_REMOTE_PATTERN = Regex("""\[(\d+)\]\s*(\S+\.(ir|json))""")
            
            fun parse(line: String): IrRemote? {
                val match = IR_REMOTE_PATTERN.find(line.trim()) ?: return null
                return IrRemote(
                    index = match.groupValues[1].toIntOrNull() ?: return null,
                    filename = match.groupValues[2]
                )
            }
        }
    }
    
    /** IR button/signal from ir show command
     *  Format: [N] button_name (protocol) or [N] button_name
     *  Example: [0] Power (NEC), [1] Volume_Up, [2] CH+ (RC5)
     */
    data class IrButton(
        val index: Int,
        val name: String,
        val protocol: String? = null
    ) : GhostResponse() {
        companion object {
            // Pattern matches: [0] Power (NEC) or [1] Volume_Up
            private val IR_BUTTON_PATTERN = Regex("""\[(\d+)\]\s*(\S+)(?:\s*\(([^)]+)\))?""")
            
            fun parse(line: String): IrButton? {
                val trimmed = line.trim()
                // Skip header lines
                if (trimmed.startsWith("Signals in ") || 
                    trimmed.startsWith("Unique buttons in ") ||
                    trimmed.startsWith("IR: ") ||
                    !trimmed.startsWith("[")) {
                    return null
                }
                
                val match = IR_BUTTON_PATTERN.find(trimmed) ?: return null
                return IrButton(
                    index = match.groupValues[1].toIntOrNull() ?: return null,
                    name = match.groupValues[2],
                    protocol = match.groupValues[3].takeIf { it.isNotEmpty() }
                )
            }
        }
    }
    
    // ==================== Settings Models ====================
    
    /** Setting key-value pair */
    data class SettingValue(
        val key: String,
        val value: String
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): SettingValue? {
                val match = ResponsePatterns.SETTINGS_KEY_VALUE.find(line) ?: return null
                return SettingValue(key = match.groupValues[1], value = match.groupValues[2].trim())
            }
        }
    }
    
    // ==================== Ethernet Models ====================
    
    /** Ethernet info */
    data class EthernetInfo(
        val linkUp: Boolean,
        val ip: String?,
        val netmask: String?,
        val gateway: String?,
        val mac: String?
    ) : GhostResponse()
    
    /** Port scan result */
    data class PortScanResult(
        val ip: String,
        val port: Int,
        val state: String, // OPEN, CLOSED, FILTERED
        val service: String? = null
    ) : GhostResponse()
    
    /** ARP scan result */
    data class ArpScanResult(
        val ip: String,
        val mac: String,
        val vendor: String? = null
    ) : GhostResponse()
}
