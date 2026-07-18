# GhostESP: Companion Changelog

## v0.4.2

- Fixed Dashboard "Settings" button incorrectly triggering NFC scan instead of navigating to Settings
- Fixed NFC scan navigation going to Settings screen instead of NFC screen
- Fixed EthConfig STATIC mode sending literal "null" when IP/netmask/gateway are missing
- Fixed portal credential parsing splitting passwords incorrectly when they contain slashes
- Fixed BLE device unique ID using unstable RSSI, causing duplicate list entries
- Fixed GPS position parser guard mismatch between "Lon:" check and "Long:" regex
- Fixed AP detail sheet crash from force-unwrap when selected AP becomes null asynchronously
- Fixed EthPorts port scan sending endPort as startPort when startPort is not specified
- Fixed double-tap on connect button launching parallel connection attempts
- Fixed USB serial control lines leaving some CH340-based ESP boards stuck in the ROM bootloader
- Fixed forceDisconnect blocking the main thread, causing potential ANR
- Fixed WiFi status boolean parsing failing on case-sensitive "True"/"TRUE" from firmware
- Fixed GHOSTESP_OK detection failing when response has trailing whitespace
- Fixed GPS position returning 0.0, 0.0 (Gulf of Guinea) instead of null on parse failure
- Fixed NFC tag type matching misclassifying MIFARE_DESFIRE as MIFARE_CLASSIC
- Fixed WiFi scan state getting stuck when scanning in live mode
- Fixed handshake events lost when no subscriber is active (replay=0 → replay=1)
- Fixed WiFi scan state not resetting when scan command fails immediately
- Fixed BrutalistButton disabled text invisible in dark mode (Color.Black → theme-aware)
- Fixed BrutalistButton disabled container jarring in dark mode (Color.White → theme-aware)
- Fixed NFC and Ethernet connection banners showing identical icon for connected/disconnected states
- Fixed terminal screen auto-scroll crash from out-of-bounds index
- Fixed overlay re-showing on every screen visit for NFC, BadUSB, GPS, and SD Manager
- Fixed beacon add/remove commands breaking on SSIDs containing spaces
- Fixed BadUSB type command breaking on text containing spaces
- Fixed Vibrator service cast crash on devices where getSystemService returns null
- Fixed Station counter not thread-safe across concurrent parse calls
- Fixed SD file download offset overflow for files larger than 2GB
- Fixed phone wardrive observation counters not thread-safe
- Fixed notification channels never created, causing silent notification drops on Android 8+
- Fixed background service WakeLock with no timeout, preventing device sleep
- Fixed AirTag spoofing stop order inconsistent with other stop functions
- Fixed Evil Portal template index out-of-bounds when SD card file list changes
- Fixed Evil Portal credential timestamp key collisions in LazyColumn
- Fixed SD Manager file list key collisions on duplicate filenames
- Fixed AP BSSID using fake "??:??:??:??:??:??" that can't be distinguished from real address
- Fixed duplicate painterResource import in DashboardScreen

## v0.4.1

- Fixed dual auto-connect race condition that caused intermittent startup connection failures
- Fixed BLE disconnect callback bypassing connection mutex, preventing stuck/hung states
- Added 15-second GATT connect timeout so the app no longer hangs in "Connecting..." if a BLE device disappears
- Fixed USB read loop not cleaning up after consecutive errors, leaving zombie connections
- Fixed stale BLE GATT connections leaking when late STATE_CONNECTED callbacks arrive after timeout
- Fixed `failBleConnection` closing GATT synchronously instead of deferring to a coroutine
- Removed ineffective `withTimeout` wrappers around non-suspend `disconnectInternal` calls
- Added `DisposableEffect` cleanup to BLE, WiFi, Evil Portal, IR, and BadUSB screens to stop scans/attacks when navigating away
- Fixed BLE scan continuing after selecting a USB device from the connection dialog
- Fixed `binaryChannel` not being drained on disconnect, preventing memory leaks
- Optimized terminal buffer from O(n) list copy per line to O(1) ArrayDeque with snapshot

## v0.4.0

- Changed the app icon to use the new evil mascot image
- Removed redundant map/list toggle from GPS/Wardriving screen
- Added built-in CSV explorer bottom sheet to browse, share, and delete saved Phone GPS Wardrive CSVs
- Replaced grid-based map cell rendering with individual circular AP markers colored by RSSI strength
- Added support for using a GhostESP device to wardrive using the Phone's GPS saved to the Phone's storage
- Added foreground background support for wardriving and other explicit run-until-stopped actions

## v0.3.0

- Added wireless bridge connection support alongside USB
- Added transport-aware connection status so key screens distinguish USB and wireless bridge connections
- Updated SD downloads to use `sd read ... --base64` and decode `SD:READ:DATA:` chunks safely over BLE
- Fixed BLE bridge DATA handling so split or combined SD response lines are buffered correctly
- Improved wireless bridge scan UX by disabling duplicate scan taps while scanning
- Added clearer SD download progress over USB and wireless connections
- Added remembered device reconnect that re-joins the last USB or wireless bridge on startup
- Added a WiFi quick action to start and stop firmware packet capture modes (EAPOL, probe, deauth, beacon, raw, WPS, pwn, BLE, skimmer, 802.15.4)
- Hardened AP list parsing so scans with banners or interleaved lines still populate the network list
- Added Open Folder / Open File actions so SD downloads can be opened from the system file manager

## v0.2.0

- Added better support for attaching multiple USB serial devices at once with auto baud rate detection
- Added support for more serial USB devices
- Added GPS/Wardriving support
- Stop any previous command when running a new one
- Performance improvements
- Minor UI tweaks

## v0.1.0 - Initial release
