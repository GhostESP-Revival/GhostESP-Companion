# GhostESP: Companion Changelog

## v0.4.0

- Refined GPS wardriving screen: removed redundant map/list toggle, map is now always displayed
- Renamed "Phone GPS WD" to "Phone GPS Wardrive" and converted it to a toggle switch
- Added built-in CSV explorer bottom sheet to browse, share, and delete saved Phone GPS Wardrive CSVs from Downloads
- Updated Phone GPS Wardrive CSV format to WiGLE v1.6 to match firmware output (added Frequency, RCOIs, MfgrId columns; auth mode now uses WiGLE capabilities format; lat/lon precision set to 6 decimals; altitude rounded to integer)
- Replaced grid-based map cell rendering with individual circular AP markers colored by RSSI strength

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
