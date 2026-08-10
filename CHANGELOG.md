# GhostESP: Companion Changelog

## v0.5.0 - Firmware compatibility and BLE protocol overhaul

### BLE bridge transport

- Added explicit BLE command completion via a new `END` frame (`PACKET_TYPE_COMMAND_END`), with an idle-timeout fallback for older peers.
- Added two-way command fragmentation. Firmware reassembles `FIRST`/`MORE`-flagged chunks keyed by command ID and rejects malformed sequences with `ERR`+`END`. Android fragments outbound commands to the negotiated MTU (250-byte limit) so SD uploads, long terminal input, BadUSB text, and portal content fit within GATT capacity.
- Fixed MTU negotiation: a failed MTU 128 request no longer tears down the connection; the app tracks the actual negotiated MTU and fragments accordingly, allowing MTU 23 devices to connect.
- BLE `ERR` frames now surface as real errors in the terminal and parsed responses instead of being treated as status text.

### BLE connection reliability

- `connectBle()` now reports success only after the full GATT handshake (connect, discovery, CCCD enable, and MTU negotiation) completes, not immediately after `connectGatt()`.
- Added per-attempt tokens so stale callbacks from an old connection can't tear down a newer one.
- Added handling for invalid MAC addresses, missing `BLUETOOTH_CONNECT` permission, disabled Bluetooth, `SecurityException`, and null/throwing `connectGatt`.
- Saved devices now persist only after a successful connection; failed reconnects fall back to USB enumeration or the picker.
- Added one bounded reconnect retry for transient GATT failures.

### BLE

- Fixed advertiser scanning (`blescan -adv/-oui/-vendor`, `listadv`): responses were never parsed, so results never appeared despite the command working. Added parsing for both the live per-device line and the `listadv` detail block.
- Combined standard and advertiser scanning into one mode selector, one start/stop, one merged results list, replacing two separate cards.
- Removed the redundant "BLE Wardrive" toggle from the Attacks tab and linked to the GPS screen instead.

### Command syntax fixes

- `dhcpstarve`: `start [threads]` / `stop` / `display`, not `-s`.
- `listenprobes stop`, not `listenprobes -s`.
- `sweep` stop now maps to the universal `stop` command instead of restarting a sweep via `sweep -s`.
- `webauth`: `on`/`off`, not `enable`/`disable`.
- `scanports`/`ethports`: one `start-end` range token, not two arguments.
- `powerprinter`: added required font-size and alignment arguments.
- `ethfp`: dropped the IP argument because firmware always scans the local subnet.
- `wdstream`: `-ble` is exclusive with WiFi capture in firmware, not additive; BLE-inclusive wardrive now sends `-ble` alone.
- Split RGB semantics: `rgbmode` for immediate effects, `setrgbmode` for persistent modes, matching firmware.

### Response parsing and error handling

- Added parsing for the current multiline GATT service format (`[N] Service:`/`UUID:`/`Handles:`), preserving the legacy compact format.
- Broadened error detection to current firmware phrasing (`Error`/`Failed`/`Invalid`/`Unknown ...`/`timed out`/`unsupported`) without misclassifying benign lines like `Failed attempts: 3`.
- `chipinfo` collection now requires a complete `[CHIPINFO_START]` through `[CHIPINFO_END]` block before drawing `UNSUPPORTED` conclusions.
- Extended `DeviceFeature` with BLE, IEEE 802.15.4, Chameleon, OTA, Camera, Microphone, GhostScript, NRF24, and Sub-GHz capability fields.
- Fixed `listflippers`/`listairtags`: their static-list output uses a different line format than live-scan lines and was never parsed.
- Implemented Ethernet response parsing (`ethinfo`, `ethstats`, `etharp`, `ethports`, `ethping`, `ethtrace`), previously raw text only.
- Fixed response parsing for nine new WiFi commands that previously showed only a running/idle indicator. WPA3 Check and GTK Abuse now display their verdicts and outcomes.

### Capability gating

- Added tri-state resolution (`SUPPORTED`/`UNSUPPORTED`/`UNKNOWN`) so missing evidence doesn't wrongly disable controls.
- `chipinfo` is now requested once per connection globally, not only on the WiFi screen.
- Gated BLE tools, NFC/Chameleon, IR TX/RX, Ethernet, device GPS, SD, BadUSB, and BLE/802.15.4 capture; unknown capabilities stay usable, only confirmed-unsupported ones are disabled.
- Phone BLE transport controls stay independent of firmware BLE capability.
- Chameleon is now distinguished from generic NFC support.

### Feature family completion

- Capture: `capture -list/-export/-wireshark/-wiresharkble`.
- Wardriving: `--helper`/`--channels`/`--hop`/`--weighted` for `startwd`.
- BLE scanning: `blescan -adv` with OUI/vendor filters, `listadv`.
- BadUSB: `set_vid/pid/mfr/prod/rand/layout`, `keysend`, `trackpad_start/stop/move/button/wheel`, `exec`, `status`.
- Ethernet: `ethstats`, `ethpoison start/stop/list/cookies/creds/status`.
- SD: typed `sd tree/info`, `sd_config`, `sd_pins_spi/mmc`, `sd_save_config`.
- RGB color commands (`rgbmode red/green/...`).

### NFC

- Replaced the NFC screen's stub with real `nfc` CLI support: PN532/ST25R backend selection, continuous/single scan, save/dump, hardnested key recovery, PicoPass/iCLASS (ST25R only), status/stop, tag emulation (UID/NDEF/file).
- Fixed `NfcTag` parsing, which matched a stale format instead of firmware's actual scan-line format.
- Separated NFC from Chameleon Ultra commands, which had been silently conflated.
- Emulate tab can reuse a scanned tag's UID instead of retyping it; Hardnested got quick-select chips for common default keys; emulate-file entry got a picker over free text; backend choice now persists across sessions.

### BadUSB

- Wired up `set_rand`, which existed end-to-end but had no UI control.
- Added `type_char <ascii>` and a "Run Built-in Script" action.
- Replaced the fixed-mask trackpad button with real Left/Right/Middle/Release (`trackpad_button 1|2|4|0`) and scroll controls.
- Replaced the trackpad's dx/dy text fields with a real drag-to-move touch surface plus tap-to-click.
- Added VID/PID device-profile presets, a named layout picker instead of a raw index, a key-name picker for keysend, and persisted config fields.

### SD transfer robustness

- Added chunked, verified upload (`uploadSdFile`): an initial `sd write` followed by `sd append` chunks of up to 768 bytes, per-chunk byte-count verification, and a final `sd size` check. This replaces the previous unverified single-shot upload.
- Wired an "Upload" action into SD Manager with file selection, chunked transfer, progress, and automatic list refresh.

### WiFi

- Added rows for firmware commands with no prior app UI: PineAP Detection, Flock Detection, Open Ports/SSH/NetBIOS/HTTP Banner scans, SNMP Probe/Walk, Enum Scan, WPA3 Compliance Check, Channel Switch Attack, GTK Abuse.
- Documented Airspace Monitor and Packet Visualizer as on-device-only features with no CLI equivalent.
- Added AP multi-select with bulk "Deauth Selected" after confirming firmware deauths every selected AP. Station multi-select and "Track Selected" remain unavailable because firmware does not support multiple targets for those actions.
- Deauth, EAPOL, WPA3 Check, Channel Switch, and SAE Flood now share one "Target AP" selector instead of five pickers; also fixed SAE Flood, which previously had no target selection despite implicitly using the last-selected AP. GTK Abuse's free-text SSID field was replaced by the same picker.
- NetBIOS/HTTP Banner/SNMP/Enum scan targets pre-fill with the connected network's IP when known.

### Ethernet

- Replaced the nonfunctional ping host field with a clear "Subnet Ping Sweep" label because `ethping` takes no argument and always sweeps the local subnet.
- Consolidated five separate target-IP fields (ARP, port scan, ping, traceroute, fingerprint) into one shared field defaulting to the known gateway/IP; tapping a discovered device sets it as the target.
- "Network Information" now shows live parsed data instead of a hardcoded mock.
- Added port-range presets (Top 20/100, Common Web, All 1-1024).
- Parsed ARP Poisoning results inline, including status, domains, cookies, and credentials.

### Dashboard

- Quick Links are now user-editable: a pencil icon opens a checklist of all 7 reachable destinations (WiFi, BLE, IR, NFC, GPS, BadUSB, SD), pick 2-6, persisted across sessions.

### Wardrive map

- Replaced direct use of OSMF's `tile.openstreetmap.org` with CARTO's basemap CDN to comply with OSMF's tile policy.
- Added the required `© OpenStreetMap contributors © CARTO` attribution overlay and an identifying User-Agent for tile requests.
- Added zoom-aware AP clustering with count badges, polished marker shadows and outlines, and density-aware sizing.
- Added distinct WiFi circle and BLE diamond markers, an RSSI strength palette, and a compact map legend.
- Reused stable map overlays and location markers instead of rebuilding every overlay after each update.
- Added one-time initial centering and an explicit recenter control so location updates no longer interrupt manual map navigation.
- Fixed the map failing to center when only the GhostESP device had a valid GPS fix.
- Fixed the longitude readout incorrectly displaying the phone's latitude.
- Added coordinate validation so invalid AP locations are skipped during rendering.

### WiFi network scans - structured results

- Sweep (`sweep`): new row with start/stop, live phase markers (`--- Phase N: ... ---`, `=== Sweep Complete ===`, `Report saved to: ...`), and the final `WiFi: N APs, N stations | Security: ...` summary.
- Local port scan (`scanlocal`), ARP scan (`scanarp`), port scan (`scanports`), SSH scan (`scanssh`): previously command-only rows with no output. Added parsers for `Host X has N open ports` / `Port N` / `UDP N` lines, ARP host entries (`N. IP [MAC]`) plus the `Found N active hosts on ...` summary, and SSH `[IP:port] Status: OPEN` + banner lines; each row now renders its results list.
- Congestion scan and Listen Probes: added parsers for the `| CH | Count | Bar |` table and `Probe Req: SRC -> DST for SSID` lines; rows now show results instead of raw terminal text.
- DHCP Starvation: added a "Show DHCP Stats" action wired to `dhcpstarve display` and a stats readout for `DHCP-Starve: N/sec | Total: N` lines.

### Capture screen

- On-device capture list (`capture -list`): new "On-device captures" card with refresh, per-file hashcat-material indicator (`[+]/[-]`), and an "Export .hc22000" button per file (`capture -export <name>`), with PMKID/M2-M3 metrics shown from the firmware result.

### DNS sinkhole

- Added the `sinkhole` command family (start/stop/status/stats/reload/log/add/remove/download) under Settings > Device Web / DNS, with a live status readout (state, IP, queries/blocked/block %, logging, blocklist) fed by the `=== DNS Sinkhole Status ===` block and `Sinkhole: N queries, N blocked, N dropped` heartbeat lines.
- WebUI AP-only restriction (`webuiap`) and web authentication (`webauth`) are now exposed as toggles instead of command-only entries.

### Full-screen live attack views

- Every new attack/scan row in the WiFi Attacks list now opens a dedicated full-screen live view instead of inline results: status header with IDLE/RUNNING/COMPLETED badge, elapsed timer, progress bar, live-updating auto-scrolling results, and Start/Stop/Run again controls.
- Sweep view tracks phases until `=== Sweep Complete ===`, then flips to COMPLETED with the final `WiFi: N APs ... | Security: ...` summary.
- One-shot scans (congestion, scanlocal, scanarp, scanports, scanssh) auto-flip to COMPLETED when their results arrive; a "no response from device yet" hint appears after a per-scan timeout.
- Ongoing attacks (sweep, listenprobes, dhcpstarve) keep a Stop button; the DHCP starve view adds a "Show Stats" refresh action.
- `scanports` with no target now sends `scanports local` (local subnet scan); `scanssh` with no target sends the bare subnet form.

### Tests

- Added BLE protocol regression tests for single-frame and fragmented messages, oversized payload rejection, `ERR`/`END` decoding, and split notification boundaries.
- Added command serialization tests for every corrected command string.
- Added capability resolution tests for BLE, IEEE 802.15.4, Chameleon, and NFC across complete and incomplete `chipinfo` responses.
- Added parser fixture tests for APs, stations, GATT devices and services, handshakes, WiFi status, `wdstream`, base64 SD reads, and `chipinfo`.
- Added wardrive map rendering tests for RSSI normalization, clustering thresholds, and marker scaling.
- Added parser regression tests for probe requests, congestion rows, port/SSH/ARP scan lines, sweep phases, DHCP starve stats, capture list/export, ethpoison status and items, sinkhole status, and webui/web-auth toggles.

### Firmware (Ghost_ESP): coordinated changes

- `main/managers/ble_bridge_manager.c`: added `GB_TYPE_END`, command fragmentation reassembly, terminal `ERR`+`END` on failure, reassembly cleanup on disconnect/stop.
- `main/core/esp_comm_manager.c` + header: added `PACKET_TYPE_COMMAND_END` and a completion callback so the bridge emits success `END` frames; older peers stay compatible.

## v0.4.2

- Added Russian localization, translated by @MoonshinException
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
