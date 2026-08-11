# GhostESP: Companion Changelog

## v0.5.0 - Firmware compatibility and BLE protocol overhaul

### BLE bridge transport

- Added explicit BLE command completion using `END` frames, with an idle-timeout fallback for older peers.
- Added bidirectional command fragmentation so large SD uploads, terminal commands, BadUSB text, and portal content fit the negotiated GATT MTU.
- Fixed MTU negotiation failures disconnecting compatible devices, including MTU 23 devices.
- BLE `ERR` frames now appear as errors in terminal and parsed responses instead of status text.

### BLE connection reliability

- BLE connections now report success only after connection, service discovery, notification setup, and MTU negotiation.
- Prevented stale callbacks from earlier BLE attempts disconnecting newer connections.
- Added graceful handling for invalid MAC addresses, missing permissions, disabled Bluetooth, security errors, and failed `connectGatt` calls.
- Saved devices now persist only after a successful connection; failed reconnects fall back to USB discovery or the device picker.
- Added one retry for transient GATT failures.

### BLE

- Fixed missing advertiser scan results for `blescan -adv/-oui/-vendor` and `listadv`.
- Combined standard and advertiser scans into one mode selector, shared controls, and a merged result list.
- Removed the redundant BLE Wardrive toggle from Attacks and linked to the GPS screen.
- Removed GPS wardrive helper mode over BLE because the peer already acts as the BLE bridge.

### Command syntax fixes

- Fixed `dhcpstarve` to use `start [threads]`, `stop`, and `display`.
- Fixed Listen Probes stop to send `listenprobes stop`.
- Fixed Sweep stop to send the universal `stop` command instead of restarting.
- Fixed `webauth` to use `on` and `off`.
- Fixed `scanports` and `ethports` to send one `start-end` range.
- Added required font size and alignment arguments to `powerprinter`.
- Removed the unsupported IP argument from `ethfp`, which always scans the local subnet.
- Fixed BLE wardriving to send exclusive `wdstream -ble` mode instead of combining it with WiFi capture.
- Separated immediate `rgbmode` effects from persistent `setrgbmode` settings.

### Response parsing and error handling

- Added parsing for current multiline GATT services while retaining legacy compact-format support.
- Recognized current firmware error wording without misclassifying benign text such as `Failed attempts: 3`.
- Required a complete `CHIPINFO` block before marking capabilities unsupported.
- Added capability fields for BLE, IEEE 802.15.4, Chameleon, OTA, camera, microphone, GhostScript, NRF24, and Sub-GHz.
- Fixed `listflippers` and `listairtags` static results not being parsed.
- Added structured results for `ethinfo`, `ethstats`, `etharp`, `ethports`, `ethping`, and `ethtrace`.
- Added structured results for nine WiFi commands, including WPA3 Check and GTK Abuse verdicts.

### Capability gating

- Added `SUPPORTED`, `UNSUPPORTED`, and `UNKNOWN` capability states so missing evidence does not disable controls.
- Requested `chipinfo` once per connection for use across the app.
- Added capability gating for BLE, NFC/Chameleon, IR, Ethernet, GPS, SD, BadUSB, and BLE/802.15.4 capture; only confirmed unsupported features are disabled.
- Kept phone BLE transport controls independent of firmware BLE capability.
- Distinguished Chameleon support from generic NFC support.

### Device controls

- Added wardrive channel, hop interval, weighted scan, and USB helper controls.
- Added SD tree, info, configuration, SPI/MMC pin, and save controls.
- Added Wireshark capture modes and RGB color controls.

### NFC

- Replaced the NFC placeholder with PN532/ST25R backend selection, scans, save/dump, hardnested recovery, PicoPass/iCLASS, status/stop, and tag emulation.
- Fixed NFC tag parsing to match current firmware output.
- Separated Chameleon Ultra commands from standard NFC support.
- Added scanned-UID reuse, common-key shortcuts, an emulation file picker, and persistent backend selection.

### BadUSB

- Exposed the existing `set_rand` command in the UI.
- Added `type_char <ascii>` and a Run Built-in Script action.
- Added Left, Right, Middle, and Release trackpad buttons plus scroll controls.
- Replaced trackpad coordinate fields with a drag surface and tap-to-click.
- Added VID/PID profiles, named layouts, a `keysend` picker, and persistent configuration.

### SD transfer robustness

- Added verified chunked SD uploads with 768-byte chunks, per-chunk checks, and final file-size validation.
- Added file selection, upload progress, and automatic refresh to SD Manager.

### WiFi

- Added UI for PineAP and Flock detection; Open Ports, SSH, NetBIOS, and HTTP Banner scans; SNMP Probe/Walk; Enum Scan; WPA3 Check; Channel Switch Attack; and GTK Abuse.
- Marked Airspace Monitor and Packet Visualizer as on-device-only features without CLI support.
- Added AP multi-select and bulk deauthentication. Station multi-select remains unavailable because firmware accepts one station target.
- Unified Deauth, EAPOL, WPA3 Check, Channel Switch, SAE Flood, and GTK Abuse around one Target AP selector.
- Defaulted network scan targets to the connected network's IP when available.

### Ethernet

- Relabeled `ethping` as Subnet Ping Sweep because it always scans the local subnet.
- Replaced separate Ethernet target fields with one shared target, defaulted from the gateway/IP and selectable from discovered devices.
- Network Information now displays live data instead of mock content.
- Added port-range presets (Top 20/100, Common Web, All 1-1024).
- Added inline ARP poisoning status, domain, cookie, and credential results.

### Dashboard

- Made Quick Links configurable: choose 2-6 of seven destinations, persisted across sessions.

### Wardrive map

- Switched map tiles from OpenStreetMap's public server to CARTO to comply with usage policy.
- Added OpenStreetMap/CARTO attribution and an identifying tile-request User-Agent.
- Added zoom-aware AP clustering, count badges, and density-aware marker sizing.
- Added distinct WiFi and BLE markers, RSSI-based colors, and a compact legend.
- Reused map overlays and location markers instead of rebuilding them on every update.
- Added initial centering and a recenter control without interrupting manual navigation.
- Fixed map centering when only the GhostESP device has a valid GPS fix.
- Fixed the longitude readout displaying the phone's latitude.
- Skipped invalid AP coordinates during map rendering.

### WiFi network scans - structured results

- Added a Sweep row with start/stop controls, live phases, completion status, report path, and final summary.
- Added structured host, port, ARP, and banner results for Local Port, ARP, Port, and SSH scans.
- Added structured congestion and probe-request results.
- Added DHCP Starvation statistics and a Show DHCP Stats action.

### Capture screen

- Added an On-device Captures card with refresh, hashcat indicators, PMKID/M2-M3 metrics, and per-file `.hc22000` export.

### DNS sinkhole

- Added sinkhole controls for start/stop, status, statistics, reload, logs, blocklist editing, and downloads, with live results.
- Exposed WebUI AP-only access and web authentication as toggles.

### Full-screen live attack views

- Added full-screen live views for new WiFi attacks and scans, with status, elapsed time, progress, auto-scrolling results, and run controls.
- Sweep views track phases through completion and display the final network/security summary.
- One-shot scans complete when results arrive and show a delayed no-response hint when needed.
- Long-running attacks retain Stop controls; DHCP Starvation also provides Show Stats.
- Port and SSH scans now use firmware subnet forms when no target is selected.

### Tests

- Added BLE protocol tests for framing, fragmentation, oversized payloads, `ERR`/`END`, and split notifications.
- Added serialization tests for corrected command syntax.
- Added capability tests for complete and incomplete BLE, IEEE 802.15.4, Chameleon, and NFC data.
- Added parser fixtures for APs, stations, GATT devices/services, handshakes, WiFi status, `wdstream`, SD reads, and `chipinfo`.
- Added wardrive map tests for RSSI normalization, clustering, and marker scaling.
- Added regression tests for WiFi scans, DHCP starvation, captures, Ethernet poisoning, sinkhole status, and WebUI/auth toggles.

### Firmware (Ghost_ESP): coordinated changes

- BLE bridge firmware now reassembles fragmented commands, emits `ERR` and `END` on failure, and clears partial commands on disconnect or stop.
- Added command-completion frames and callbacks so the bridge emits successful `END` responses while remaining compatible with older peers.

## v0.4.2

- Added Russian localization, translated by @MoonshinException.
- Fixed the Dashboard Settings button opening NFC instead of Settings.
- Fixed NFC navigation opening Settings instead of NFC.
- Fixed Ethernet static configuration sending `null` for missing IP, netmask, or gateway values.
- Fixed portal credential parsing for passwords containing slashes.
- Fixed unstable BLE device IDs causing duplicate results.
- Fixed GPS longitude parsing expecting inconsistent `Lon:` and `Long:` labels.
- Fixed an AP details crash when the selected AP disappears asynchronously.
- Fixed Ethernet port scans using the end port as an omitted start port.
- Prevented double-tapping Connect from starting parallel attempts.
- Fixed USB control lines leaving some CH340 ESP boards in the ROM bootloader.
- Fixed forced disconnects blocking the main thread and potentially causing an ANR.
- Fixed case-sensitive WiFi status boolean parsing.
- Fixed `GHOSTESP_OK` detection with trailing whitespace.
- Fixed GPS parse failures returning `0.0, 0.0` instead of no position.
- Fixed `MIFARE_DESFIRE` tags being misclassified as `MIFARE_CLASSIC`.
- Fixed live WiFi scans remaining stuck in the scanning state.
- Fixed handshake events being lost when no subscriber was active.
- Fixed WiFi scan state not resetting after an immediate command failure.
- Fixed disabled BrutalistButton text and backgrounds in dark mode.
- Fixed NFC and Ethernet banners using the same connected and disconnected icon.
- Fixed an out-of-bounds crash during terminal auto-scroll.
- Fixed overlays reappearing on every visit to NFC, BadUSB, GPS, and SD Manager.
- Fixed beacon commands for SSIDs containing spaces.
- Fixed BadUSB typing for text containing spaces.
- Fixed a vibrator-service cast crash on unsupported devices.
- Made station counting safe across concurrent parsing.
- Fixed SD download offsets overflowing for files larger than 2 GB.
- Made phone wardrive observation counting thread-safe.
- Fixed missing notification channels causing silent notifications on Android 8 and later.
- Added a timeout to the background-service wake lock so devices can sleep.
- Made AirTag spoofing stop behavior consistent with other actions.
- Fixed an Evil Portal template crash when the SD file list changes.
- Fixed duplicate Evil Portal credential keys caused by timestamp collisions.
- Fixed duplicate SD Manager list keys for repeated filenames.
- Stopped using a fake `??:??:??:??:??:??` BSSID for unknown AP addresses.

## v0.4.1

- Fixed competing auto-connect attempts causing intermittent startup failures.
- Fixed BLE disconnect callbacks bypassing connection synchronization and leaving connections stuck.
- Added a 15-second GATT timeout so vanished BLE devices no longer leave the app connecting indefinitely.
- Fixed USB read-loop cleanup after consecutive errors.
- Fixed stale BLE GATT connections leaking after late callbacks.
- Deferred GATT closure during failed BLE connections.
- Stopped scans and active operations when leaving BLE, WiFi, Evil Portal, IR, and BadUSB screens.
- Stopped BLE scanning after selecting a USB device.
- Fixed binary-channel data being retained after disconnect.
- Reduced terminal-buffer insertion from O(n) copying to O(1).

## v0.4.0

- Updated the app icon with the new evil mascot.
- Removed the redundant map/list toggle from GPS/Wardriving.
- Added a CSV explorer for browsing, sharing, and deleting phone wardrive files.
- Replaced map grid cells with AP markers colored by RSSI.
- Added phone-stored wardriving using GhostESP observations and the phone's GPS.
- Added foreground-service support for wardriving and other run-until-stopped actions.

## v0.3.0

- Added wireless bridge connections alongside USB.
- Added transport-aware status distinguishing USB and wireless bridge connections.
- Updated SD downloads to decode base64 chunks safely over BLE.
- Fixed BLE bridge buffering for split or combined SD response lines.
- Prevented repeated wireless scan actions while scanning.
- Improved SD download progress over USB and wireless connections.
- Added startup reconnection to the last USB or wireless bridge.
- Added WiFi quick actions for firmware packet capture modes.
- Hardened AP parsing against banners and interleaved scan output.
- Added Open Folder and Open File actions for SD downloads.

## v0.2.0

- Expanded multi-device USB serial support with automatic baud detection and more adapters.
- Added GPS and wardriving support.
- Stopped the previous command before starting a new one.

## v0.1.0 - Initial release
