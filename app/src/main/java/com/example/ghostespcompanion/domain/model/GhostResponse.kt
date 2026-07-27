package com.example.ghostespcompanion.domain.model

import androidx.compose.runtime.Immutable

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

    // listflippers/listairtags static output (flipper_scan_print_results / airtag_scan_print_results):
    // [N] MAC: XX:XX:XX:XX:XX:XX,
    //      Name: XXX,               <- Flipper only
    //      RSSI: -XX dBm (XXX)      <- AirTag has proximity in parens, Flipper doesn't
    val MAC_LIST_INDEX = Regex("^\\[(\\d+)\\]\\s*MAC:")
    
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
    
    // Ethernet info (ethinfo, multiline block from "Status: UP"/"Status: DOWN"):
    // Status: UP
    // Link: 100Mbps Full Duplex
    // MAC: XX:XX:XX:XX:XX:XX
    // IP Address: X.X.X.X
    // Netmask: X.X.X.X
    // Gateway: X.X.X.X
    // DNS Main: X.X.X.X
    // DHCP Server: X.X.X.X
    val ETH_LINK = Regex("Link:\\s*(.+)")
    val ETH_MAC = Regex("MAC:\\s*([0-9A-Fa-f:]{17})")
    val ETH_IP = Regex("IP Address:\\s*([^\\n]+)")
    val ETH_NETMASK = Regex("Netmask:\\s*([^\\n]+)")
    val ETH_GATEWAY = Regex("Gateway:\\s*([^\\n]+)")
    val ETH_DNS_MAIN = Regex("DNS Main:\\s*([^\\n]+)")
    val ETH_DHCP_SERVER = Regex("DHCP Server:\\s*([^\\n]+)")

    // Ethernet statistics (ethstats, multiline block from "=== Ethernet Statistics ===")
    val ETH_LINK_STATUS = Regex("Link Status:\\s*(\\w+)")
    val ETH_RX_PACKETS = Regex("RX Packets:\\s*(\\d+)")
    val ETH_TX_PACKETS = Regex("TX Packets:\\s*(\\d+)")
    val ETH_RX_ERRORS = Regex("RX Errors:\\s*(\\d+)")
    val ETH_RX_DROPS = Regex("RX Drops:\\s*(\\d+)")
    val ETH_TX_ERRORS = Regex("TX Errors:\\s*(\\d+)")
    val ETH_TX_DROPS = Regex("TX Drops:\\s*(\\d+)")
    val ETH_ARP_REQUESTS = Regex("ARP Requests:\\s*(\\d+)")
    val ETH_ARP_REPLIES = Regex("ARP Replies:\\s*(\\d+)")

    // Ethernet ARP scan entry (etharp): "  192.168.1.5   aa:bb:cc:dd:ee:ff"
    val ETH_ARP_ENTRY = Regex("^(\\d{1,3}(?:\\.\\d{1,3}){3})\\s+([0-9A-Fa-f:]{17})$")

    // Ethernet port scan entry (ethports): "  192.168.1.1:80 - OPEN"
    val ETH_PORT_OPEN = Regex("^(\\S+):(\\d+)\\s*-\\s*OPEN$")

    // Ethernet ping scan entry (ethping): "  192.168.1.5 - ALIVE"
    val ETH_PING_ALIVE = Regex("^(\\S+)\\s*-\\s*ALIVE$")

    // Ethernet traceroute hop (ethtrace): "  1  192.168.1.1  12ms" or "  5  *  (timeout)"
    val ETH_TRACE_HOP = Regex("^(\\d+)\\s+(\\S+)\\s+(.+)$")

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
    
    // NFC Tag scan line: "NFC: <TypeName> uid=<XX:XX:...> atqa=0x%04X sak=0x%02X"
    val NFC_SCAN_LINE = Regex(
        "^NFC:\\s*(MIFARE Classic|MIFARE DESFire|ISO14443-4|ISO14443-A)\\s+uid=([0-9A-Fa-f:]+)\\s+atqa=0x([0-9A-Fa-f]+)\\s+sak=0x([0-9A-Fa-f]+)"
    )
    // "NFC: backend=<name>" or "NFC: backend set to <name>"
    val NFC_BACKEND_CURRENT = Regex("^NFC:\\s*backend=(\\w+)")
    val NFC_BACKEND_SET = Regex("^NFC:\\s*backend set to (\\w+)")
    // "NFC: emulating NFC-A uid=<hex> atqa=0x%04X sak=0x%02X..."
    val NFC_EMULATE_START = Regex(
        "^NFC:\\s*emulating NFC-A uid=([0-9A-Fa-f:]+)\\s+atqa=0x([0-9A-Fa-f]+)\\s+sak=0x([0-9A-Fa-f]+)"
    )
    // "NFC: PicoPass/iCLASS CSN=<hex:hex:...>"
    val NFC_PICOPASS_CSN = Regex("^NFC:\\s*PicoPass/iCLASS CSN=([0-9A-Fa-f:]+)")
    // "  PACS: FC=%u CN=%u (%ubit)"
    val NFC_PICOPASS_PACS = Regex("PACS:\\s*FC=(\\d+)\\s+CN=(\\d+)\\s*\\((\\d+)bit\\)")
    // "  Encryption: 0x%02X, Biometrics: yes/no, PIN len: %u, SIO: yes/no"
    val NFC_PICOPASS_ENCRYPTION = Regex(
        "Encryption:\\s*0x([0-9A-Fa-f]+),\\s*Biometrics:\\s*(yes|no),\\s*PIN len:\\s*(\\d+),\\s*SIO:\\s*(yes|no)"
    )
    // "NFC: saved MIFARE Classic dump: <path>" / "NFC: saved Type 2/NTAG dump: <path>" / "NFC: failed to save ..."
    val NFC_SAVE_OK = Regex("^NFC:\\s*saved (.+?) dump:\\s*(.+)$")
    // "NFC: hardnested capture saved: <path>"
    val NFC_HARDNESTED_SAVED = Regex("^NFC:\\s*hardnested capture saved:\\s*(.+)$")
    
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
    
    // BLE Advertiser live single-line format (print_advertiser_line):
    // [N] Advertiser|iBeacon | MAC | RSSI dBm | AdvType | Name (opt) | OUI xxx (opt) | MFG xxx (opt) | SVC xxx (opt) | Major N Minor N (opt)
    val ADVERTISER_LIVE = Regex(
        "^\\[(\\d+)]\\s*(Advertiser|iBeacon)\\s*\\|\\s*([0-9A-Fa-f:]{17})\\s*\\|\\s*(-?\\d+)\\s*dBm\\s*\\|\\s*(\\S+)"
    )

    // BLE Advertiser detailed block format (print_advertiser_detail), preceded by
    // "--- BLE Advertisers (N) ---" header, one indented field-block per device:
    // [N] BLE Advertiser / [N] iBeacon
    //      MAC: xx:xx:xx:xx:xx:xx
    //      Address Type: xxx
    //      RSSI: -XX dBm (xxx), seen N
    //      Adv Type: xxx
    //      Name: xxx (optional)
    //      Flags: 0xXX (optional)
    //      TX Power: -XX dBm (optional)
    //      OUI Vendor: xxx (optional)
    //      Manufacturer: xxx (optional)
    //      Appearance: 0xXXXX xxx (optional)
    //      Services: xxx (optional)
    //      Service Data: xxx (optional)
    //      iBeacon UUID: xxx (optional, iBeacon only)
    //      iBeacon Major: N / iBeacon Minor: N (optional, iBeacon only)
    //      Measured Power: -XX dBm (optional, iBeacon only)
    val ADVERTISER_DETAIL_HEADER = Regex("^\\[(\\d+)]\\s*(BLE Advertiser|iBeacon)\\s*$", RegexOption.MULTILINE)
    val ADVERTISER_DETAIL_MAC = Regex("MAC:\\s*([0-9A-Fa-f:]{17})")
    val ADVERTISER_DETAIL_ADDR_TYPE = Regex("Address Type:\\s*(\\S+)")
    val ADVERTISER_DETAIL_RSSI = Regex("RSSI:\\s*(-?\\d+)\\s*dBm\\s*\\(([^)]*)\\),\\s*seen\\s*(\\d+)")
    val ADVERTISER_DETAIL_ADV_TYPE = Regex("Adv Type:\\s*(\\S+)")
    val ADVERTISER_DETAIL_NAME = Regex("(?:^|\\n)\\s*Name:\\s*([^\\n]+)")
    val ADVERTISER_DETAIL_FLAGS = Regex("Flags:\\s*0x([0-9A-Fa-f]+)")
    val ADVERTISER_DETAIL_TX_POWER = Regex("TX Power:\\s*(-?\\d+)\\s*dBm")
    val ADVERTISER_DETAIL_OUI = Regex("OUI Vendor:\\s*([^\\n]+)")
    val ADVERTISER_DETAIL_MFG = Regex("Manufacturer:\\s*([^\\n]+)")
    val ADVERTISER_DETAIL_APPEARANCE = Regex("Appearance:\\s*0x([0-9A-Fa-f]+)\\s*([^\\n]*)")
    val ADVERTISER_DETAIL_SERVICES = Regex("(?:^|\\n)\\s*Services:\\s*([^\\n]+)")
    val ADVERTISER_DETAIL_SERVICE_DATA = Regex("Service Data:\\s*([^\\n]+)")
    val ADVERTISER_DETAIL_IBEACON_UUID = Regex("iBeacon UUID:\\s*(\\S+)")
    val ADVERTISER_DETAIL_IBEACON_MAJOR = Regex("iBeacon Major:\\s*(\\d+)")
    val ADVERTISER_DETAIL_IBEACON_MINOR = Regex("iBeacon Minor:\\s*(\\d+)")
    val ADVERTISER_DETAIL_MEASURED_POWER = Regex("Measured Power:\\s*(-?\\d+)\\s*dBm")

    // Error/Success
    val ERROR_PREFIX = Regex("^error\\b\\s*[:\\-]?\\s*(.*)$", RegexOption.IGNORE_CASE)
    val ERROR_FAILED = Regex("^failed\\b\\s*[:\\-]?\\s*(.*)$", RegexOption.IGNORE_CASE)
    val ERROR_INVALID = Regex("^invalid\\b\\s*[:\\-]?\\s*(.*)$", RegexOption.IGNORE_CASE)
    val NON_ERROR_METRIC = Regex("^(?:failed|invalid)\\s+\\w+:\\s*\\d+(?:\\s|$)", RegexOption.IGNORE_CASE)
    val ERROR_UNKNOWN = Regex("^unknown\\s+(?:command|subcommand|option|argument|mode|capture type)\\b.*$", RegexOption.IGNORE_CASE)
    val ERROR_TIMEOUT = Regex(
        "^(?:timed?\\s+out\\b.*|timeout(?!\\s*:\\s*\\d+\\s*(?:seconds?|ms)\\b)\\b.*)$",
        RegexOption.IGNORE_CASE
    )
    val ERROR_UNSUPPORTED = Regex("^(?:unsupported\\b.*|.*\\b(?:is\\s+)?not supported\\b.*)$", RegexOption.IGNORE_CASE)
    val SUCCESS = Regex("^OK:\\s*(.+)$", RegexOption.MULTILINE)
    val SD_SUCCESS = Regex("^SD:OK.*$")
    
    // Portal credentials — firmware logs: "Captured credentials: <email> / <password>"
    val PORTAL_CREDS = Regex("Captured credentials:\\s*(.+?)\\s*/\\s*(.+)")
    
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
    @Immutable
    data class Raw(val text: String) : GhostResponse()
    
    // ==================== WiFi Models ====================
    
    /** WiFi Access Point - optimized parsing for multiline firmware output */
    @Immutable
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

    /** Structured wardrive stream AP observation from wdstream. */
    @Immutable
    data class WdStreamAp(
        val timestampMs: Long,
        val bssid: String,
        val ssid: String,
        val rssi: Int,
        val channel: Int,
        val auth: String,
        val hidden: Boolean
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): WdStreamAp? {
                if (!line.startsWith("WD:AP ")) return null

                val fields = parseWdFields(line.removePrefix("WD:AP "))
                val bssid = fields["bssid"]?.uppercase() ?: return null
                return WdStreamAp(
                    timestampMs = fields["ts"]?.toLongOrNull() ?: 0L,
                    bssid = bssid,
                    ssid = decodeHex(fields["ssid_hex"].orEmpty()),
                    rssi = fields["rssi"]?.toIntOrNull() ?: -100,
                    channel = fields["ch"]?.toIntOrNull() ?: 0,
                    auth = fields["auth"] ?: "UNKNOWN",
                    hidden = fields["hidden"] == "1"
                )
            }

            private fun parseWdFields(text: String): Map<String, String> {
                val fields = HashMap<String, String>()
                text.split(' ').forEach { token ->
                    val separator = token.indexOf('=')
                    if (separator > 0 && separator < token.lastIndex) {
                        fields[token.substring(0, separator)] = token.substring(separator + 1)
                    } else if (separator > 0) {
                        fields[token.substring(0, separator)] = ""
                    }
                }
                return fields
            }

            private fun decodeHex(hex: String): String {
                if (hex.isEmpty() || hex.length % 2 != 0) return ""
                return try {
                    val bytes = ByteArray(hex.length / 2)
                    var i = 0
                    while (i < hex.length) {
                        bytes[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
                        i += 2
                    }
                    bytes.toString(Charsets.UTF_8)
                } catch (_: Exception) {
                    ""
                }
            }
        }
    }

    /** Structured wardrive stream BLE observation from wdstream. */
    @Immutable
    data class WdStreamBle(
        val timestampMs: Long,
        val mac: String,
        val name: String,
        val rssi: Int,
        val eventType: String,
        val manufacturerId: String
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): WdStreamBle? {
                if (!line.startsWith("WD:BLE ")) return null

                val fields = parseWdFields(line.removePrefix("WD:BLE "))
                val mac = fields["mac"]?.uppercase() ?: return null
                return WdStreamBle(
                    timestampMs = fields["ts"]?.toLongOrNull() ?: 0L,
                    mac = mac,
                    name = decodeHex(fields["name_hex"].orEmpty()),
                    rssi = fields["rssi"]?.toIntOrNull() ?: -100,
                    eventType = fields["type"] ?: "adv",
                    manufacturerId = fields["mfg"].orEmpty()
                )
            }

            private fun parseWdFields(text: String): Map<String, String> {
                val fields = HashMap<String, String>()
                text.split(' ').forEach { token ->
                    val separator = token.indexOf('=')
                    if (separator > 0 && separator < token.lastIndex) {
                        fields[token.substring(0, separator)] = token.substring(separator + 1)
                    } else if (separator > 0) {
                        fields[token.substring(0, separator)] = ""
                    }
                }
                return fields
            }

            private fun decodeHex(hex: String): String {
                if (hex.isEmpty() || hex.length % 2 != 0) return ""
                return try {
                    val bytes = ByteArray(hex.length / 2)
                    var i = 0
                    while (i < hex.length) {
                        bytes[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
                        i += 2
                    }
                    bytes.toString(Charsets.UTF_8)
                } catch (_: Exception) {
                    ""
                }
            }
        }
    }

    /** Status line from wdstream. */
    @Immutable
    data class WdStreamStatus(
        val running: Boolean,
        val accessPoints: Int,
        val channel: Int?,
        val message: String
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): WdStreamStatus? {
                if (!line.startsWith("WD:")) return null
                if (line.startsWith("WD:BEGIN")) {
                    return WdStreamStatus(running = true, accessPoints = 0, channel = null, message = line)
                }
                if (line.startsWith("WD:END")) {
                    return WdStreamStatus(running = false, accessPoints = 0, channel = null, message = line)
                }
                if (!line.startsWith("WD:STATUS ")) return null

                val fields = line.removePrefix("WD:STATUS ").split(' ').mapNotNull { token ->
                    val separator = token.indexOf('=')
                    if (separator > 0) token.substring(0, separator) to token.substring(separator + 1) else null
                }.toMap()
                return WdStreamStatus(
                    running = true,
                    accessPoints = fields["aps"]?.toIntOrNull() ?: 0,
                    channel = fields["ch"]?.toIntOrNull(),
                    message = line
                )
            }
        }
    }
    
    /** WiFi Station - multiline format */
    @Immutable
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
    @Immutable
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
    @Immutable
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
@Immutable
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
    @Immutable
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
    @Immutable
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
                    connected = values["connected"]?.equals("true", ignoreCase = true) ?: false,
                    hasSavedNetwork = values["has_saved_network"]?.equals("true", ignoreCase = true) ?: false,
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
    @Immutable
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
        fun getUniqueId(): String = mac ?: "ble_${name ?: "unknown"}"
        
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
    @Immutable
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
                val liveMatch = ResponsePatterns.FLIPPER_INDEX.find(text)
                val listMatch = if (liveMatch == null) {
                    ResponsePatterns.MAC_LIST_INDEX.find(text)?.takeIf { text.contains("Name:") }
                } else null
                val index = (liveMatch ?: listMatch)?.groupValues?.get(1)?.toIntOrNull() ?: return null
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
    @Immutable
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
    @Immutable
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
                val liveMatch = ResponsePatterns.AIRTAG_INDEX.find(text)
                val listMatch = if (liveMatch == null) {
                    ResponsePatterns.MAC_LIST_INDEX.find(text)?.takeIf { !text.contains("Name:") && text.contains("RSSI:") }
                } else null
                val index = (liveMatch ?: listMatch)?.groupValues?.get(1)?.toIntOrNull() ?: return null
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
    @Immutable
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

    /**
     * BLE Advertiser (blescan -adv/-oui/-vendor, listadv) - covers both the live
     * single-line format emitted per newly-discovered advertiser and the richer
     * detail block emitted by listadv. Detail-only fields are nullable since the
     * live format doesn't carry them.
     */
    @Immutable
    data class AdvertiserDevice(
        val index: Int,
        val mac: String,
        val rssi: Int,
        val advType: String,
        val name: String? = null,
        val ouiVendor: String? = null,
        val manufacturer: String? = null,
        val services: String? = null,
        val isIBeacon: Boolean = false,
        val ibeaconMajor: Int? = null,
        val ibeaconMinor: Int? = null,
        // Detail-only fields (listadv)
        val addressType: String? = null,
        val seenCount: Long? = null,
        val proximity: String? = null,
        val txPower: Int? = null,
        val flags: Int? = null,
        val appearance: Int? = null,
        val appearanceName: String? = null,
        val serviceData: String? = null,
        val ibeaconUuid: String? = null,
        val measuredPower: Int? = null
    ) : GhostResponse() {
        companion object {
            /**
             * Parse the live single-line advertiser format:
             * [N] Advertiser|iBeacon | MAC | RSSI dBm | AdvType | Name (opt) | OUI xxx (opt) | MFG xxx (opt) | SVC xxx (opt) | Major N Minor N (opt)
             */
            fun parseLive(line: String): AdvertiserDevice? {
                val match = ResponsePatterns.ADVERTISER_LIVE.find(line) ?: return null
                val index = match.groupValues[1].toIntOrNull() ?: return null
                val isIBeacon = match.groupValues[2] == "iBeacon"
                val mac = match.groupValues[3]
                val rssi = match.groupValues[4].toIntOrNull() ?: -100
                val advType = match.groupValues[5]

                var name: String? = null
                var oui: String? = null
                var mfg: String? = null
                var services: String? = null
                var major: Int? = null
                var minor: Int? = null

                line.substring(match.range.last + 1).split("|").map { it.trim() }.filter { it.isNotEmpty() }.forEach { segment ->
                    when {
                        segment.startsWith("OUI ") -> oui = segment.removePrefix("OUI ").trim()
                        segment.startsWith("MFG ") -> mfg = segment.removePrefix("MFG ").trim()
                        segment.startsWith("SVC ") -> services = segment.removePrefix("SVC ").trim()
                        segment.startsWith("Major ") -> {
                            val majorMinor = Regex("Major\\s+(\\d+)\\s+Minor\\s+(\\d+)").find(segment)
                            major = majorMinor?.groupValues?.get(1)?.toIntOrNull()
                            minor = majorMinor?.groupValues?.get(2)?.toIntOrNull()
                        }
                        name == null -> name = segment
                    }
                }

                return AdvertiserDevice(
                    index = index,
                    mac = mac,
                    rssi = rssi,
                    advType = advType,
                    name = name,
                    ouiVendor = oui,
                    manufacturer = mfg,
                    services = services,
                    isIBeacon = isIBeacon,
                    ibeaconMajor = major,
                    ibeaconMinor = minor
                )
            }

            /**
             * Parse a single detailed device block from listadv output:
             * [N] BLE Advertiser (or "[N] iBeacon")
             *      MAC: xx:xx:xx:xx:xx:xx
             *      Address Type: xxx
             *      RSSI: -XX dBm (xxx), seen N
             *      Adv Type: xxx
             *      Name/Flags/TX Power/OUI Vendor/Manufacturer/Appearance/Services/Service Data (all optional)
             *      iBeacon UUID/Major/Minor, Measured Power (iBeacon only)
             */
            fun parseDetail(text: String): AdvertiserDevice? {
                val header = ResponsePatterns.ADVERTISER_DETAIL_HEADER.find(text) ?: return null
                val index = header.groupValues[1].toIntOrNull() ?: return null
                val isIBeacon = header.groupValues[2] == "iBeacon"
                val mac = ResponsePatterns.ADVERTISER_DETAIL_MAC.find(text)?.groupValues?.get(1) ?: return null

                val addressType = ResponsePatterns.ADVERTISER_DETAIL_ADDR_TYPE.find(text)?.groupValues?.get(1)?.trim()
                val rssiMatch = ResponsePatterns.ADVERTISER_DETAIL_RSSI.find(text)
                val rssi = rssiMatch?.groupValues?.get(1)?.toIntOrNull() ?: -100
                val proximity = rssiMatch?.groupValues?.get(2)?.trim()?.takeIf { it.isNotEmpty() }
                val seenCount = rssiMatch?.groupValues?.get(3)?.toLongOrNull()
                val advType = ResponsePatterns.ADVERTISER_DETAIL_ADV_TYPE.find(text)?.groupValues?.get(1) ?: "UNKNOWN"
                val name = ResponsePatterns.ADVERTISER_DETAIL_NAME.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                val flags = ResponsePatterns.ADVERTISER_DETAIL_FLAGS.find(text)?.groupValues?.get(1)?.toIntOrNull(16)
                val txPower = ResponsePatterns.ADVERTISER_DETAIL_TX_POWER.find(text)?.groupValues?.get(1)?.toIntOrNull()
                val oui = ResponsePatterns.ADVERTISER_DETAIL_OUI.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                val mfg = ResponsePatterns.ADVERTISER_DETAIL_MFG.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                val appearanceMatch = ResponsePatterns.ADVERTISER_DETAIL_APPEARANCE.find(text)
                val appearance = appearanceMatch?.groupValues?.get(1)?.toIntOrNull(16)
                val appearanceName = appearanceMatch?.groupValues?.get(2)?.trim()?.takeIf { it.isNotEmpty() }
                val services = ResponsePatterns.ADVERTISER_DETAIL_SERVICES.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                val serviceData = ResponsePatterns.ADVERTISER_DETAIL_SERVICE_DATA.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                val ibeaconUuid = ResponsePatterns.ADVERTISER_DETAIL_IBEACON_UUID.find(text)?.groupValues?.get(1)
                val ibeaconMajor = ResponsePatterns.ADVERTISER_DETAIL_IBEACON_MAJOR.find(text)?.groupValues?.get(1)?.toIntOrNull()
                val ibeaconMinor = ResponsePatterns.ADVERTISER_DETAIL_IBEACON_MINOR.find(text)?.groupValues?.get(1)?.toIntOrNull()
                val measuredPower = ResponsePatterns.ADVERTISER_DETAIL_MEASURED_POWER.find(text)?.groupValues?.get(1)?.toIntOrNull()

                return AdvertiserDevice(
                    index = index,
                    mac = mac,
                    rssi = rssi,
                    advType = advType,
                    name = name,
                    ouiVendor = oui,
                    manufacturer = mfg,
                    services = services,
                    isIBeacon = isIBeacon,
                    ibeaconMajor = ibeaconMajor,
                    ibeaconMinor = ibeaconMinor,
                    addressType = addressType,
                    seenCount = seenCount,
                    proximity = proximity,
                    txPower = txPower,
                    flags = flags,
                    appearance = appearance,
                    appearanceName = appearanceName,
                    serviceData = serviceData,
                    ibeaconUuid = ibeaconUuid,
                    measuredPower = measuredPower
                )
            }
        }
    }
    
    // ==================== GATT Service Models ====================
    
    /** GATT Service from enumgatt command */
    @Immutable
    data class GattService(
        val uuid: String,
        val name: String? = null,
        val startHandle: Int,
        val endHandle: Int,
        val characteristics: List<GattCharacteristic> = emptyList()
    ) : GhostResponse() {
        companion object {
            private val COMPACT_SERVICE_PATTERN = Regex(
                "Service:\\s*(.+?)\\s*\\(([^)]+)\\)\\s*(?:\\[\\s*)?handles\\s*(\\d+)\\s*-\\s*(\\d+)(?:\\s*])?",
                RegexOption.IGNORE_CASE
            )
            private val MULTILINE_SERVICE_NAME = Regex("(?:^|\\r?\\n)[ \\t]*(?:\\[\\d+])?[ \\t]*Service:[ \\t]*([^,\\r\\n]*)", RegexOption.IGNORE_CASE)
            private val MULTILINE_UUID = Regex("(?:^|\\r?\\n)\\s*UUID:\\s*([^,\\s]+)", RegexOption.IGNORE_CASE)
            private val MULTILINE_HANDLES = Regex("(?:^|\\r?\\n)\\s*Handles:\\s*(\\d+)\\s*-\\s*(\\d+)", RegexOption.IGNORE_CASE)
            
            fun parse(line: String): GattService? {
                val compact = COMPACT_SERVICE_PATTERN.find(line)
                val name: String
                val uuid: String
                val startHandle: Int
                val endHandle: Int
                if (compact != null) {
                    name = compact.groupValues[1].trim()
                    uuid = compact.groupValues[2].trim()
                    startHandle = compact.groupValues[3].toIntOrNull() ?: return null
                    endHandle = compact.groupValues[4].toIntOrNull() ?: return null
                } else {
                    val service = MULTILINE_SERVICE_NAME.find(line) ?: return null
                    name = service.groupValues[1].trim()
                    uuid = MULTILINE_UUID.find(line)?.groupValues?.get(1)?.trim() ?: return null
                    val handles = MULTILINE_HANDLES.find(line) ?: return null
                    startHandle = handles.groupValues[1].toIntOrNull() ?: return null
                    endHandle = handles.groupValues[2].toIntOrNull() ?: return null
                }
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
    @Immutable
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
    @Immutable
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
    
    /**
     * NFC Tag scan result.
     * Firmware line format: "NFC: <TypeName> uid=<XX:XX:...> atqa=0x%04X sak=0x%02X"
     * or "NFC: no tag found" (does not parse to a tag).
     */
    @Immutable
    data class NfcTag(
        val uid: String,
        val type: NfcTagType,
        val atqa: String? = null,
        val sak: String? = null,
        val data: String? = null
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): NfcTag? {
                val match = ResponsePatterns.NFC_SCAN_LINE.find(line.trim()) ?: return null
                val (typeStr, uid, atqa, sak) = match.destructured

                return NfcTag(
                    uid = uid,
                    type = when (typeStr) {
                        "MIFARE Classic" -> NfcTagType.MIFARE_CLASSIC
                        "MIFARE DESFire" -> NfcTagType.MIFARE_DESFIRE
                        "ISO14443-4" -> NfcTagType.ISO14443_4
                        "ISO14443-A" -> NfcTagType.ISO14443_A
                        else -> NfcTagType.UNKNOWN
                    },
                    atqa = atqa,
                    sak = sak,
                    data = line
                )
            }
        }
    }

    enum class NfcTagType {
        MIFARE_CLASSIC, MIFARE_DESFIRE, ISO14443_4, ISO14443_A, UNKNOWN
    }

    /** Current/updated NFC backend, from "nfc backend" get or set */
    @Immutable
    data class NfcBackend(val name: String) : GhostResponse() {
        companion object {
            fun parse(line: String): NfcBackend? {
                val trimmed = line.trim()
                ResponsePatterns.NFC_BACKEND_SET.find(trimmed)?.let { return NfcBackend(it.groupValues[1]) }
                ResponsePatterns.NFC_BACKEND_CURRENT.find(trimmed)?.let { return NfcBackend(it.groupValues[1]) }
                return null
            }
        }
    }

    /** Current NFC/emulation task status ("NFC: task running"/"idle", or emulate uid=.../"emulation stopped") */
    @Immutable
    data class NfcEmulateStatus(
        val running: Boolean,
        val uid: String? = null,
        val atqa: String? = null,
        val sak: String? = null
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): NfcEmulateStatus? {
                val trimmed = line.trim()
                ResponsePatterns.NFC_EMULATE_START.find(trimmed)?.let {
                    val (uid, atqa, sak) = it.destructured
                    return NfcEmulateStatus(running = true, uid = uid, atqa = atqa, sak = sak)
                }
                if (trimmed.contains("NFC: emulation stopped")) return NfcEmulateStatus(running = false)
                return null
            }
        }
    }

    /** Result of "nfc save"/"nfc dump" */
    @Immutable
    data class NfcSaveResult(val success: Boolean, val dumpType: String? = null, val path: String? = null) : GhostResponse() {
        companion object {
            fun parse(line: String): NfcSaveResult? {
                val trimmed = line.trim()
                if (trimmed.startsWith("NFC: failed to save")) return NfcSaveResult(success = false)
                ResponsePatterns.NFC_SAVE_OK.find(trimmed)?.let {
                    return NfcSaveResult(success = true, dumpType = it.groupValues[1], path = it.groupValues[2])
                }
                return null
            }
        }
    }

    /** Result of "nfc hardnested" attack */
    @Immutable
    data class NfcHardnestedResult(val success: Boolean, val path: String? = null) : GhostResponse() {
        companion object {
            fun parse(line: String): NfcHardnestedResult? {
                val trimmed = line.trim()
                if (trimmed.contains("NFC: hardnested capture failed")) return NfcHardnestedResult(success = false)
                ResponsePatterns.NFC_HARDNESTED_SAVED.find(trimmed)?.let {
                    return NfcHardnestedResult(success = true, path = it.groupValues[1])
                }
                return null
            }
        }
    }

    /** Result of "nfc picopass" scan - accumulated across multiple response lines */
    @Immutable
    data class NfcPicopassResult(
        val found: Boolean,
        val csn: String? = null,
        val fc: Int? = null,
        val cn: Int? = null,
        val bits: Int? = null,
        val encryption: String? = null,
        val biometrics: Boolean? = null,
        val pinLen: Int? = null,
        val sio: Boolean? = null,
        val authFailed: Boolean = false,
        val unsupported: Boolean = false
    ) : GhostResponse() {
        companion object {
            fun parseCsn(line: String): String? =
                ResponsePatterns.NFC_PICOPASS_CSN.find(line.trim())?.groupValues?.get(1)

            data class Pacs(val fc: Int?, val cn: Int?, val bits: Int?)
            fun parsePacs(line: String): Pacs? =
                ResponsePatterns.NFC_PICOPASS_PACS.find(line.trim())?.let {
                    Pacs(it.groupValues[1].toIntOrNull(), it.groupValues[2].toIntOrNull(), it.groupValues[3].toIntOrNull())
                }

            data class Encryption(val value: String, val biometrics: Boolean, val pinLen: Int?, val sio: Boolean)
            fun parseEncryption(line: String): Encryption? =
                ResponsePatterns.NFC_PICOPASS_ENCRYPTION.find(line.trim())?.let {
                    Encryption(
                        value = it.groupValues[1],
                        biometrics = it.groupValues[2].equals("yes", ignoreCase = true),
                        pinLen = it.groupValues[3].toIntOrNull(),
                        sio = it.groupValues[4].equals("yes", ignoreCase = true)
                    )
                }
        }
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
        SD_CARD_MMC;

        enum class Capability {
            BLE,
            IEEE802154,
            CHAMELEON,
            OTA,
            CAMERA,
            MICROPHONE,
            GHOSTSCRIPT,
            NRF24,
            SUB_GHZ
        }

        companion object {
            val BLE = Capability.BLE
            val IEEE802154 = Capability.IEEE802154
            val CHAMELEON = Capability.CHAMELEON
            val OTA = Capability.OTA
            val CAMERA = Capability.CAMERA
            val MICROPHONE = Capability.MICROPHONE
            val GHOSTSCRIPT = Capability.GHOSTSCRIPT
            val NRF24 = Capability.NRF24
            val SUB_GHZ = Capability.SUB_GHZ
        }
    }

    enum class CapabilityResolution {
        SUPPORTED,
        UNSUPPORTED,
        UNKNOWN;

        val isUsable: Boolean get() = this != UNSUPPORTED
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
    @Immutable
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
        val capabilities: Set<DeviceFeature.Capability> = emptySet(),
        val firmwareVersion: String? = null,
        val gitCommit: String? = null,
        val rawResponse: String? = null,
        val parseErrors: List<String> = emptyList(),
        /** True only when the firmware explicitly closed this inventory. */
        val chipInfoEndedExplicitly: Boolean = false
    ) : GhostResponse() {
        fun hasFeature(feature: DeviceFeature): Boolean = enabledFeatures.contains(feature)
        fun hasFeature(feature: DeviceFeature.Capability): Boolean = capabilities.contains(feature)

        fun resolveFeature(feature: DeviceFeature): CapabilityResolution = when {
            hasFeature(feature) -> CapabilityResolution.SUPPORTED
            chipInfoEndedExplicitly -> CapabilityResolution.UNSUPPORTED
            else -> CapabilityResolution.UNKNOWN
        }

        fun resolveFeature(vararg features: DeviceFeature): CapabilityResolution = when {
            features.any(::hasFeature) -> CapabilityResolution.SUPPORTED
            chipInfoEndedExplicitly -> CapabilityResolution.UNSUPPORTED
            else -> CapabilityResolution.UNKNOWN
        }

        fun resolveCapability(feature: DeviceFeature.Capability): CapabilityResolution = when {
            hasFeature(feature) -> CapabilityResolution.SUPPORTED
            chipInfoEndedExplicitly -> CapabilityResolution.UNSUPPORTED
            else -> CapabilityResolution.UNKNOWN
        }
        
        companion object {
            private val MODEL_PATTERN = Regex("Model:\\s*([^,\\s\\n]+(?:\\s+[^,\\s\\n]+)*?)(?=\\s*(?:[,\\n]|$))")
            private val REVISION_PATTERN = Regex("Revision:\\s*v?(\\d+(?:\\.\\d+)+)")
            private val CORES_PATTERN = Regex("CPU Cores:\\s*(\\d+)")
            // Features uses "/" as separator (WiFi/BLE/802.15.4/…) — stop at comma or newline
            private val FEATURES_PATTERN = Regex("(?<!Enabled )Features:\\s*([^,\\n]+)")
            private val FREE_HEAP_PATTERN = Regex("Free Heap:\\s*(\\d+)")
            private val MIN_FREE_HEAP_PATTERN = Regex("Min(?:imum)?(?: Free)? Heap:\\s*(\\d+)")
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

            private val CAPABILITY_MAPPING = mapOf(
                "BLE" to DeviceFeature.BLE,
                "Bluetooth LE" to DeviceFeature.BLE,
                "802.15.4" to DeviceFeature.IEEE802154,
                "802154" to DeviceFeature.IEEE802154,
                "Chameleon" to DeviceFeature.CHAMELEON,
                "Chameleon Ultra" to DeviceFeature.CHAMELEON,
                "OTA" to DeviceFeature.OTA,
                "OTA Updates" to DeviceFeature.OTA,
                "Camera" to DeviceFeature.CAMERA,
                "Microphone" to DeviceFeature.MICROPHONE,
                "MIC" to DeviceFeature.MICROPHONE,
                "GhostScript" to DeviceFeature.GHOSTSCRIPT,
                "NRF24" to DeviceFeature.NRF24,
                "SubGHz" to DeviceFeature.SUB_GHZ,
                "Sub-GHz" to DeviceFeature.SUB_GHZ
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
                val capabilities = parseCapabilities(text, features)
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
                    capabilities = capabilities,
                    firmwareVersion = firmwareVersion,
                    gitCommit = gitCommit,
                    rawResponse = text,
                    parseErrors = errors,
                    chipInfoEndedExplicitly = text.contains("[CHIPINFO_END]")
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

                val features = mutableSetOf<DeviceFeature>()
                val afterMarker = text.substring(markerIndex + marker.length)
                // Split on ", " OR "\n" to handle both the comma-joined buffer format
                // and any future raw-newline format
                afterMarker.split(", ", "\n").forEach { segment ->
                    val trimmed = segment.trim().removePrefix("-").trim().removeSuffix(": Yes").trim()
                    if (trimmed.isNotEmpty()) {
                        FEATURE_MAPPING[trimmed]?.let { features.add(it) }
                    }
                }
                return features
            }

            private fun parseCapabilities(text: String, chipFeatures: String): Set<DeviceFeature.Capability> {
                val capabilities = mutableSetOf<DeviceFeature.Capability>()
                chipFeatures.split('/').forEach { token ->
                    CAPABILITY_MAPPING[token.trim()]?.let(capabilities::add)
                }

                val markerIndex = text.indexOf("Enabled Features:")
                if (markerIndex == -1) return capabilities
                text.substring(markerIndex + "Enabled Features:".length)
                    .split(Regex("\\s*(?:,|\\r?\\n)\\s*"))
                    .map { it.trim().removePrefix("-").trim().removeSuffix(": Yes").trim() }
                    .forEach { CAPABILITY_MAPPING[it]?.let(capabilities::add) }
                return capabilities
            }
        }
    }
    
    /** Scan status update */
    @Immutable
    data class ScanStatus(
        val message: String,
        val progress: Float? = null,
        val type: ScanType,
        val phase: Int? = null
    ) : GhostResponse() {
        enum class ScanType { WIFI_AP, WIFI_STA, BLE, NFC, AERIAL, SWEEP }
    }
    
    /** Error response */
    @Immutable
    data class Error(
        val message: String,
        val code: Int? = null
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): Error? {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.contains('\n')) return null

                ResponsePatterns.ERROR_PREFIX.matchEntire(trimmed)?.let { match ->
                    val detail = match.groupValues[1].trim()
                    return Error(message = detail.ifEmpty { trimmed })
                }
                if (!ResponsePatterns.NON_ERROR_METRIC.containsMatchIn(trimmed)) {
                    ResponsePatterns.ERROR_FAILED.matchEntire(trimmed)?.let { match ->
                        val detail = match.groupValues[1].trim()
                        return Error(message = detail.ifEmpty { trimmed })
                    }
                    ResponsePatterns.ERROR_INVALID.matchEntire(trimmed)?.let { match ->
                        val detail = match.groupValues[1].trim()
                        return Error(message = detail.ifEmpty { trimmed })
                    }
                }
                if (ResponsePatterns.ERROR_UNKNOWN.matches(trimmed) ||
                    ResponsePatterns.ERROR_TIMEOUT.matches(trimmed) ||
                    ResponsePatterns.ERROR_UNSUPPORTED.matches(trimmed)
                ) {
                    return Error(message = trimmed)
                }
                return null
            }
        }
    }
    
    /** Success response */
    @Immutable
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
        fun matches(line: String): Boolean = ResponsePatterns.GHOSTESP_OK.containsMatchIn(line)
    }
    
    // ==================== GPS Models ====================
    
    /** GPS Position - parsed from firmware gpsinfo command output */
    @Immutable
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
                if (!line.contains("GPS Info") && !line.contains("Lat:") && !line.contains("Long:")) return null
                
                val fixStr = ResponsePatterns.GPS_FIX.find(line)?.groupValues?.get(1) ?: "No Fix"
                val hasFix = fixStr.equals("3D", ignoreCase = true) || fixStr.equals("2D", ignoreCase = true) || fixStr.equals("Fix", ignoreCase = true)
                
                val satsMatch = ResponsePatterns.GPS_SATS.find(line)
                val satsUsed = satsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val satsInView = satsMatch?.groupValues?.get(2)?.toIntOrNull() ?: satsUsed
                
                val latMatch = ResponsePatterns.GPS_LAT.find(line)
                val lonMatch = ResponsePatterns.GPS_LON.find(line)
                
                if (latMatch == null || lonMatch == null) {
                    return null
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
    @Immutable
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
    @Immutable
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
    @Immutable
    data class SdStatus(
        val mounted: Boolean,
        val type: String,
        val capacity: Long,
        val used: Long,
        val available: Long
    ) : GhostResponse()
    
    /** SD Card operation result - handles all SD:OK and SD:ERR responses */
    @Immutable
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
    @Immutable
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
    @Immutable
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
    @Immutable
    data class PortalInfo(
        val name: String,
        val path: String
    ) : GhostResponse()
    
    // ==================== IR Models ====================
    
    /** IR learned signal - matches firmware output:
     *  Parsed: "Captured: <protocol> A:0x<addr> C:0x<cmd>"
     *  Raw: "Captured RAW signal (<samples> samples)"
     */
    @Immutable
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
    @Immutable
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
    @Immutable
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
    @Immutable
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
    @Immutable
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
    @Immutable
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
    @Immutable
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

    /** Ethernet info (from ethinfo) */
    @Immutable
    data class EthernetInfo(
        val linkUp: Boolean,
        val link: String?,
        val mac: String?,
        val ip: String?,
        val netmask: String?,
        val gateway: String?,
        val dnsMain: String?,
        val dhcpServer: String?
    ) : GhostResponse() {
        companion object {
            fun parse(text: String): EthernetInfo? {
                if (!text.startsWith("Status:")) return null

                return EthernetInfo(
                    linkUp = text.contains("Status: UP"),
                    link = ResponsePatterns.ETH_LINK.find(text)?.groupValues?.get(1)?.trim(),
                    mac = ResponsePatterns.ETH_MAC.find(text)?.groupValues?.get(1),
                    ip = ResponsePatterns.ETH_IP.find(text)?.groupValues?.get(1)?.trim(),
                    netmask = ResponsePatterns.ETH_NETMASK.find(text)?.groupValues?.get(1)?.trim(),
                    gateway = ResponsePatterns.ETH_GATEWAY.find(text)?.groupValues?.get(1)?.trim(),
                    dnsMain = ResponsePatterns.ETH_DNS_MAIN.find(text)?.groupValues?.get(1)?.trim(),
                    dhcpServer = ResponsePatterns.ETH_DHCP_SERVER.find(text)?.groupValues?.get(1)?.trim()
                )
            }
        }
    }

    /** Ethernet statistics (from ethstats) */
    @Immutable
    data class EthernetStats(
        val linkStatus: String?,
        val rxPackets: Long?,
        val txPackets: Long?,
        val rxErrors: Long?,
        val rxDrops: Long?,
        val txErrors: Long?,
        val txDrops: Long?,
        val arpRequests: Long?,
        val arpReplies: Long?
    ) : GhostResponse() {
        companion object {
            fun parse(text: String): EthernetStats? {
                if (!text.startsWith("=== Ethernet Statistics ===")) return null

                return EthernetStats(
                    linkStatus = ResponsePatterns.ETH_LINK_STATUS.find(text)?.groupValues?.get(1),
                    rxPackets = ResponsePatterns.ETH_RX_PACKETS.find(text)?.groupValues?.get(1)?.toLongOrNull(),
                    txPackets = ResponsePatterns.ETH_TX_PACKETS.find(text)?.groupValues?.get(1)?.toLongOrNull(),
                    rxErrors = ResponsePatterns.ETH_RX_ERRORS.find(text)?.groupValues?.get(1)?.toLongOrNull(),
                    rxDrops = ResponsePatterns.ETH_RX_DROPS.find(text)?.groupValues?.get(1)?.toLongOrNull(),
                    txErrors = ResponsePatterns.ETH_TX_ERRORS.find(text)?.groupValues?.get(1)?.toLongOrNull(),
                    txDrops = ResponsePatterns.ETH_TX_DROPS.find(text)?.groupValues?.get(1)?.toLongOrNull(),
                    arpRequests = ResponsePatterns.ETH_ARP_REQUESTS.find(text)?.groupValues?.get(1)?.toLongOrNull(),
                    arpReplies = ResponsePatterns.ETH_ARP_REPLIES.find(text)?.groupValues?.get(1)?.toLongOrNull()
                )
            }
        }
    }

    /** Port scan result (from ethports): "192.168.1.1:80 - OPEN" */
    @Immutable
    data class PortScanResult(
        val ip: String,
        val port: Int,
        val state: String, // OPEN, CLOSED, FILTERED
        val service: String? = null
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): PortScanResult? {
                val match = ResponsePatterns.ETH_PORT_OPEN.find(line.trim()) ?: return null
                val ip = match.groupValues[1]
                val port = match.groupValues[2].toIntOrNull() ?: return null
                return PortScanResult(ip = ip, port = port, state = "OPEN")
            }
        }
    }

    /** ARP scan result (from etharp): "192.168.1.5   aa:bb:cc:dd:ee:ff" */
    @Immutable
    data class ArpScanResult(
        val ip: String,
        val mac: String,
        val vendor: String? = null
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): ArpScanResult? {
                val match = ResponsePatterns.ETH_ARP_ENTRY.find(line.trim()) ?: return null
                return ArpScanResult(ip = match.groupValues[1], mac = match.groupValues[2])
            }
        }
    }

    /** Ping scan result (from ethping): "192.168.1.5 - ALIVE" */
    @Immutable
    data class PingScanResult(val ip: String) : GhostResponse() {
        companion object {
            fun parse(line: String): PingScanResult? {
                val match = ResponsePatterns.ETH_PING_ALIVE.find(line.trim()) ?: return null
                return PingScanResult(ip = match.groupValues[1])
            }
        }
    }

    /** Traceroute hop (from ethtrace): "1  192.168.1.1  12ms" or "5  *  (timeout)" */
    @Immutable
    data class TraceHop(
        val hop: Int,
        val ip: String?,
        val ms: Long?,
        val timeout: Boolean
    ) : GhostResponse() {
        companion object {
            fun parse(line: String): TraceHop? {
                val match = ResponsePatterns.ETH_TRACE_HOP.find(line.trim()) ?: return null
                val hop = match.groupValues[1].toIntOrNull() ?: return null
                val target = match.groupValues[2]
                val timing = match.groupValues[3]
                return if (target == "*" || timing.contains("timeout")) {
                    TraceHop(hop = hop, ip = null, ms = null, timeout = true)
                } else {
                    TraceHop(hop = hop, ip = target, ms = timing.removeSuffix("ms").toLongOrNull(), timeout = false)
                }
            }
        }
    }

    // ==================== WiFi Scan/Attack Findings ====================

    /** Pineapple rogue AP detection block (pineap): heading + BSSID/Channel/RSSI/SSIDs lines */
    @Immutable
    data class PineapDetection(
        val heading: String,
        val bssid: String,
        val channel: Int,
        val rssi: Int,
        val ssidCount: Int,
        val ssids: String
    ) : GhostResponse() {
        companion object {
            private val HEADING = Regex("(Pineapple detected!|Pineapple OUI match!)")
            private val BSSID = Regex("BSSID:\\s*([0-9A-Fa-f:]+)")
            private val CHANNEL = Regex("Channel:\\s*(\\d+)")
            private val RSSI = Regex("RSSI:\\s*(-?\\d+)")
            private val SSIDS = Regex("SSIDs\\s*\\((\\d+)\\):\\s*(.*)")

            fun parse(text: String): PineapDetection? {
                val heading = HEADING.find(text)?.groupValues?.get(1) ?: return null
                val bssid = BSSID.find(text)?.groupValues?.get(1) ?: return null
                val channel = CHANNEL.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                val rssi = RSSI.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                val ssidsMatch = SSIDS.find(text)
                val ssidCount = ssidsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val ssids = ssidsMatch?.groupValues?.get(2)?.trim() ?: ""
                return PineapDetection(heading, bssid, channel, rssi, ssidCount, ssids)
            }
        }
    }

    /** Flock Safety surveillance device detection (flockscan) */
    @Immutable
    data class FlockDetection(
        val method: String,
        val mac: String,
        val signalLabel: String,
        val rssi: Int,
        val channel: Int,
        val ssid: String?,
        val hits: Int
    ) : GhostResponse() {
        companion object {
            private val PATTERN = Regex(
                "\\[FLOCK] Surveillance device detected! (.+?) \\| MAC: ([0-9A-Fa-f:]+) \\| Signal: (\\w+) \\((-?\\d+)dBm\\) \\| Ch: (\\d+)(?: \\| SSID: (.+?))? \\| Hits: (\\d+)"
            )

            fun parse(line: String): FlockDetection? {
                val m = PATTERN.find(line) ?: return null
                return FlockDetection(
                    method = m.groupValues[1],
                    mac = m.groupValues[2],
                    signalLabel = m.groupValues[3],
                    rssi = m.groupValues[4].toIntOrNull() ?: -100,
                    channel = m.groupValues[5].toIntOrNull() ?: 0,
                    ssid = m.groupValues[6].takeIf { it.isNotEmpty() },
                    hits = m.groupValues[7].toIntOrNull() ?: 0
                )
            }
        }
    }

    /** Flock scan completion summary: "[FLOCK] Scan stopped. N surveillance device(s) found." */
    @Immutable
    data class FlockScanComplete(val count: Int) : GhostResponse() {
        companion object {
            private val PATTERN = Regex("\\[FLOCK] Scan stopped\\. (\\d+) surveillance device")

            fun parse(line: String): FlockScanComplete? {
                val count = PATTERN.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                return FlockScanComplete(count)
            }
        }
    }

    /** NetBIOS host result (netbiosscan): either a Names line or an IP/Flags line for a host */
    @Immutable
    data class NetBiosResult(
        val host: String,
        val names: String? = null,
        val remoteIp: String? = null,
        val flags: Int? = null
    ) : GhostResponse() {
        companion object {
            private val NAMES = Regex("\\[NetBIOS] Host:\\s*(\\S+)\\s+Names:\\s*(.*)")
            private val IP_FLAGS = Regex("\\[NetBIOS] Host:\\s*(\\S+)\\s+IP:\\s*(\\S+)\\s+Flags:\\s*0x([0-9A-Fa-f]+)")

            fun parse(line: String): NetBiosResult? {
                IP_FLAGS.find(line)?.let { m ->
                    return NetBiosResult(
                        host = m.groupValues[1],
                        remoteIp = m.groupValues[2],
                        flags = m.groupValues[3].toIntOrNull(16)
                    )
                }
                NAMES.find(line)?.let { m ->
                    return NetBiosResult(host = m.groupValues[1], names = m.groupValues[2].trim().takeIf { it.isNotEmpty() && it != "none" })
                }
                return null
            }
        }
    }

    /** HTTP banner scan hit (httpbannerscan) */
    @Immutable
    data class HttpBannerHit(
        val ip: String,
        val port: Int,
        val scheme: String,
        val server: String?
    ) : GhostResponse() {
        companion object {
            private val SERVER = Regex("^\\[(\\S+):(\\d+)] \\((\\w+)\\) Server:\\s*(.+)$")
            private val NO_BANNER = Regex("^\\[(\\S+):(\\d+)] \\((\\w+)\\) Status: OPEN, no banner$")

            fun parse(line: String): HttpBannerHit? {
                SERVER.find(line)?.let { m ->
                    return HttpBannerHit(m.groupValues[1], m.groupValues[2].toIntOrNull() ?: 0, m.groupValues[3], m.groupValues[4].trim())
                }
                NO_BANNER.find(line)?.let { m ->
                    return HttpBannerHit(m.groupValues[1], m.groupValues[2].toIntOrNull() ?: 0, m.groupValues[3], null)
                }
                return null
            }
        }
    }

    /** HTTP banner scan completion: "HTTP Banner Scan: ... found N hosts with M HTTP service(s)" */
    @Immutable
    data class HttpBannerSummary(val hostsFound: Int, val servicesFound: Int) : GhostResponse() {
        companion object {
            private val PATTERN = Regex("HTTP Banner Scan:.*found (\\d+) hosts with (\\d+) HTTP service")

            fun parse(line: String): HttpBannerSummary? {
                val m = PATTERN.find(line) ?: return null
                return HttpBannerSummary(m.groupValues[1].toIntOrNull() ?: 0, m.groupValues[2].toIntOrNull() ?: 0)
            }
        }
    }

    /** SNMP probe/walk hit (snmpprobe) */
    @Immutable
    data class SnmpHit(
        val ip: String,
        val community: String? = null,
        val sysDescr: String? = null,
        val oid: String? = null,
        val value: String? = null,
        val type: String? = null
    ) : GhostResponse() {
        companion object {
            private val PROBE = Regex("\\[SNMP] (\\S+) \\(community: (\\S+)\\) sysDescr:\\s*(.*)")
            private val WALK = Regex("\\[SNMP-WALK] (\\S+) = (.*) \\((\\w+)\\)")

            fun parse(line: String): SnmpHit? {
                PROBE.find(line)?.let { m ->
                    return SnmpHit(ip = m.groupValues[1], community = m.groupValues[2], sysDescr = m.groupValues[3].trim())
                }
                WALK.find(line)?.let { m ->
                    return SnmpHit(ip = "", oid = m.groupValues[1], value = m.groupValues[2].trim(), type = m.groupValues[3])
                }
                return null
            }
        }
    }

    /** SNMP scan completion: "SNMP Scan: ... found N SNMP host(s)" */
    @Immutable
    data class SnmpSummary(val hostsFound: Int) : GhostResponse() {
        companion object {
            private val PATTERN = Regex("SNMP Scan:.*found (\\d+) SNMP host")

            fun parse(line: String): SnmpSummary? {
                val m = PATTERN.find(line) ?: return null
                return SnmpSummary(m.groupValues[1].toIntOrNull() ?: 0)
            }
        }
    }

    /** SMB/NetBIOS enumeration hit (enumscan) - kept as raw display line since fields are variadic */
    @Immutable
    data class EnumHit(val raw: String) : GhostResponse() {
        companion object {
            fun parse(line: String): EnumHit? {
                if (!line.startsWith("[Enum]")) return null
                val body = line.removePrefix("[Enum]").trim()
                return body.takeIf { it.isNotEmpty() }?.let { EnumHit(it) }
            }
        }
    }

    /** Enum scan completion: "Enum Scan: ... Found N host(s)" */
    @Immutable
    data class EnumSummary(val hostsFound: Int) : GhostResponse() {
        companion object {
            private val PATTERN = Regex("Enum Scan:.*Found (\\d+) host")

            fun parse(line: String): EnumSummary? {
                val m = PATTERN.find(line) ?: return null
                return EnumSummary(m.groupValues[1].toIntOrNull() ?: 0)
            }
        }
    }

    /** WPA3 compliance check result for a single AP (wpa3check) */
    @Immutable
    data class Wpa3Compliance(
        val ssid: String,
        val bssid: String,
        val auth: String,
        val wpa3Present: Boolean,
        val transitionMode: Boolean,
        val pmf: String,
        val finding: String
    ) : GhostResponse() {
        companion object {
            private val SSID = Regex("SSID:\\s*(.*)")
            private val BSSID = Regex("BSSID:\\s*([0-9A-Fa-f:]+)")
            private val AUTH = Regex("Auth:\\s*(.*)")
            private val WPA3 = Regex("WPA3 Present:\\s*(Yes|No)")
            private val TRANSITION = Regex("Transition Mode:\\s*(Enabled|Disabled)")
            private val PMF = Regex("PMF:\\s*(.*)")
            private val FINDING = Regex("Finding:\\s*(.*)")

            fun parse(text: String): Wpa3Compliance? {
                val ssid = SSID.find(text)?.groupValues?.get(1)?.trim() ?: return null
                val bssid = BSSID.find(text)?.groupValues?.get(1) ?: return null
                val auth = AUTH.find(text)?.groupValues?.get(1)?.trim() ?: ""
                val wpa3Present = WPA3.find(text)?.groupValues?.get(1) == "Yes"
                val transitionMode = TRANSITION.find(text)?.groupValues?.get(1) == "Enabled"
                val pmf = PMF.find(text)?.groupValues?.get(1)?.trim() ?: ""
                val finding = FINDING.find(text)?.groupValues?.get(1)?.trim() ?: return null
                return Wpa3Compliance(ssid, bssid, auth, wpa3Present, transitionMode, pmf, finding)
            }
        }
    }

    /** WPA3 compliance report summary across all cached APs (wpa3check on multiple APs) */
    @Immutable
    data class Wpa3ReportSummary(
        val apCount: Int,
        val compliant: Int,
        val downgradable: Int,
        val legacy: Int,
        val open: Int,
        val other: Int
    ) : GhostResponse() {
        companion object {
            private val HEADER = Regex("--- WPA3 Compliance Report \\((\\d+) APs\\) ---")
            private val SUMMARY = Regex(
                "Summary:\\s*(\\d+) compliant,\\s*(\\d+) downgradable,\\s*(\\d+) legacy,\\s*(\\d+) open,\\s*(\\d+) other"
            )

            fun parseHeader(line: String): Int? = HEADER.find(line)?.groupValues?.get(1)?.toIntOrNull()

            fun parseSummary(line: String, apCount: Int): Wpa3ReportSummary? {
                val m = SUMMARY.find(line) ?: return null
                return Wpa3ReportSummary(
                    apCount = apCount,
                    compliant = m.groupValues[1].toIntOrNull() ?: 0,
                    downgradable = m.groupValues[2].toIntOrNull() ?: 0,
                    legacy = m.groupValues[3].toIntOrNull() ?: 0,
                    open = m.groupValues[4].toIntOrNull() ?: 0,
                    other = m.groupValues[5].toIntOrNull() ?: 0
                )
            }
        }
    }

    /** Channel Switch Announcement (CSA) attack status (attack -c) */
    @Immutable
    data class CsaAttackStatus(
        val targetCount: Int,
        val targets: List<String> = emptyList(),
        val packetsPerSecond: Int? = null
    ) : GhostResponse() {
        companion object {
            private val TARGETING = Regex("CSA Attack: Targeting (\\d+) AP")
            private val TARGET_LINE = Regex("^\\[(\\d+)] (.+?) \\(Ch:(\\d+)\\) ([0-9A-Fa-f:]+)$")
            private val RATE = Regex("CSA: (\\d+) pkts/sec")

            fun parseTargeting(line: String): Int? = TARGETING.find(line)?.groupValues?.get(1)?.toIntOrNull()

            fun parseTarget(line: String): String? {
                val m = TARGET_LINE.find(line.trim()) ?: return null
                return "${m.groupValues[2]} (Ch:${m.groupValues[3]}) ${m.groupValues[4]}"
            }

            fun parseRate(line: String): Int? = RATE.find(line)?.groupValues?.get(1)?.toIntOrNull()
        }
    }

    /** GTK Abuse attack progress/outcome (attack -g) */
    @Immutable
    data class GtkAbuseStatus(val message: String) : GhostResponse() {
        companion object {
            fun parse(line: String): GtkAbuseStatus? {
                if (!line.startsWith("GTK")) return null
                return GtkAbuseStatus(line.trim())
            }
        }
    }
}
