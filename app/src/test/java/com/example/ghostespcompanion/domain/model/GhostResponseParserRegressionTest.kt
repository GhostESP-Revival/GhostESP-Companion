package com.example.ghostespcompanion.domain.model

import com.example.ghostespcompanion.test.FixtureLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compatibility / regression tests for GhostResponse parsers against current
 * firmware output formats. Fixtures live under app/src/test/resources/fixtures
 * so the raw firmware samples never drift away from what the parser expects.
 */
class GhostResponseParserRegressionTest {

    @Test
    fun `parses firmware AP fixture entry with all fields`() {
        val sample = """
            [0] SSID: HomeNetwork,
                 BSSID: AA:BB:CC:DD:EE:FF,
                 RSSI: -42,
                 Channel: 6,
                 Band: 2GHz,
                 Security: WPA2-PSK
                 PMF: Disabled
                 Vendor: TP-Link
        """.trimIndent()

        val ap = GhostResponse.AccessPoint.parse(sample)
        assertNotNull(ap)
        ap!!
        assertEquals(0, ap.index)
        assertEquals("HomeNetwork", ap.ssid)
        assertEquals("AA:BB:CC:DD:EE:FF", ap.bssid)
        assertEquals(-42, ap.rssi)
        assertEquals(6, ap.channel)
        assertEquals("WPA2-PSK", ap.security)
        assertEquals("2GHz", ap.band)
        assertEquals("Disabled", ap.pmf)
        assertEquals("TP-Link", ap.vendor)
        assertFalse(ap.isHidden)
    }

    @Test
    fun `parses hidden AP fixture entry with minimal fields`() {
        val sample = "[1] SSID: , BSSID: 11:22:33:44:55:66, RSSI: -85, Channel: 11, Band: 2GHz, Security: OPEN"
        val ap = GhostResponse.AccessPoint.parse(sample)
        assertNotNull(ap)
        ap!!
        assertEquals(1, ap.index)
        assertEquals("", ap.ssid)
        assertTrue(ap.isHidden)
        assertEquals("OPEN", ap.security)
        assertNull(ap.pmf)
        assertNull(ap.vendor)
        assertEquals("11:22:33:44:55:66", ap.bssid)
    }

    @Test
    fun `parses AP fixture resource line by line`() {
        val text = FixtureLoader.load("ap_scan.txt")
        val parsed = splitApBlocks(text).mapNotNull { GhostResponse.AccessPoint.parse(it) }
        assertEquals(2, parsed.size)
        assertEquals("HomeNetwork", parsed[0].ssid)
        assertTrue(parsed[1].isHidden)
    }

    private fun splitApBlocks(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        text.lines().forEach { line ->
            if (line.startsWith("[") && sb.isNotEmpty()) {
                out.add(sb.toString())
                sb.setLength(0)
            }
            sb.append(line).append('\n')
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    @Test
    fun `parses indexed station fixture`() {
        val sample = """
            [0] Station MAC: 00:11:22:33:44:55,
                 Station Vendor: Espressif,
                 Associated AP: HomeNetwork,
                 AP BSSID: AA:BB:CC:DD:EE:FF,
                 AP Vendor: TP-Link
        """.trimIndent()
        val station = GhostResponse.Station.parse(sample)
        assertNotNull(station)
        station!!
        assertEquals(0, station.index)
        assertEquals("00:11:22:33:44:55", station.mac)
        assertEquals("Espressif", station.vendor)
        assertEquals("HomeNetwork", station.associatedApSsid)
        assertEquals("AA:BB:CC:DD:EE:FF", station.apBssid)
        assertEquals("TP-Link", station.apVendor)
    }

    @Test
    fun `parses New Station fixture (sequential index)`() {
        GhostResponse.Station.resetCounter()
        val sample = """
            New Station:
            Station: 66:77:88:99:AA:BB,
                 STA Vendor: Apple,
                 Associated AP: HomeNetwork,
                 AP BSSID: AA:BB:CC:DD:EE:FF,
                 AP Vendor: TP-Link
        """.trimIndent()
        val station = GhostResponse.Station.parse(sample)
        assertNotNull(station)
        station!!
        assertEquals("66:77:88:99:AA:BB", station.mac)
        assertEquals("Apple", station.vendor)
        assertEquals(0, station.index)
    }

    @Test
    fun `station parser rejects non-station text`() {
        assertNull(GhostResponse.Station.parse("Handshake found!\nAP=AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `parses GATT device firmware multiline fixture`() {
        val sample = """
            [0] Name: Flipper-F7A1,
                 MAC: AA:BB:CC:DD:EE:FF,
                 RSSI: -67,
                 Type: UNKNOWN
        """.trimIndent()
        val device = GhostResponse.GattDevice.parse(sample)
        assertNotNull(device)
        device!!
        assertEquals(0, device.index)
        assertEquals("Flipper-F7A1", device.name)
        assertEquals("AA:BB:CC:DD:EE:FF", device.mac)
        assertEquals(-67, device.rssi)
        assertEquals("UNKNOWN", device.type)
    }

    @Test
    fun `parses anonymous GATT device firmware fixture`() {
        val sample = """
            [1] Name: ,
                 MAC: 12:34:56:78:9A:BC,
                 RSSI: -90,
                 Type: Peripheral
        """.trimIndent()
        val device = GhostResponse.GattDevice.parse(sample)
        assertNotNull(device)
        device!!
        assertEquals(1, device.index)
        assertNull(device.name)
        assertEquals("12:34:56:78:9A:BC", device.mac)
        assertEquals(-90, device.rssi)
        assertEquals("Peripheral", device.type)
    }

    @Test
    fun `parses current multiline GATT service fixture with N prefix`() {
        val sample = """
            [2] Service: Battery Service,
                 UUID: 0x180F,
                 Handles: 12-15
        """.trimIndent()
        val service = GhostResponse.GattService.parse(sample)
        assertNotNull(service)
        assertEquals(GhostResponse.GattService("0x180F", "Battery Service", 12, 15), service)
    }

    @Test
    fun `parses current multiline GATT service fixture without N prefix`() {
        val sample = """
            Service: Generic Access,
                 UUID: 0x1800,
                 Handles: 1-7
        """.trimIndent()
        val service = GhostResponse.GattService.parse(sample)
        assertNotNull(service)
        assertEquals(GhostResponse.GattService("0x1800", "Generic Access", 1, 7), service)
    }

    @Test
    fun `gatt service fixture file parses every service entry`() {
        val text = FixtureLoader.load("gatt_service.txt")
        val blocks = text.split(Regex("(?=^\\[\\d+\\]\\s+Service:)", RegexOption.MULTILINE))
        val services = blocks.mapNotNull { block ->
            GhostResponse.GattService.parse(block)
        }
        assertEquals(3, services.size)
        assertEquals("0x1800", services[0].uuid)
        assertEquals("Generic Access", services[0].name)
        assertEquals(7, services[0].endHandle)
        assertEquals("0xFFE0", services[2].uuid)
        assertEquals(20, services[2].startHandle)
        assertEquals(30, services[2].endHandle)
    }

    @Test
    fun `parses handshake firmware fixture`() {
        val sample = """
            Handshake found!
            AP=24:2F:D0:90:DD:70
            Pair=M1/M2
        """.trimIndent()
        val handshake = GhostResponse.Handshake.parse(sample)
        assertNotNull(handshake)
        handshake!!
        assertEquals("24:2F:D0:90:DD:70", handshake.apBssid)
        assertEquals("M1/M2", handshake.pairType)
    }

    @Test
    fun `handshake parser requires Handshake found and AP line`() {
        assertNull(GhostResponse.Handshake.parse("AP=AA:BB:CC:DD:EE:FF"))
        assertNull(GhostResponse.Handshake.parse("Handshake found!\nNothing else"))
    }

    @Test
    fun `parses firmware WifiStatus machine-readable fixture`() {
        val text = FixtureLoader.load("wifi_status.txt")
        val status = GhostResponse.WifiStatus.parse(text)
        assertNotNull(status)
        status!!
        assertTrue(status.connected)
        assertTrue(status.hasSavedNetwork)
        assertEquals("HomeNetwork", status.connectedSsid)
        assertEquals(-45, status.connectedRssi)
        assertEquals("AA:BB:CC:DD:EE:FF", status.connectedBssid)
        assertEquals(6, status.connectedChannel)
        assertEquals("HomeNetwork", status.savedSsid)
    }

    @Test
    fun `wifi status rejects text without the connected field`() {
        assertNull(
            GhostResponse.WifiStatus.parse(
                "=== WIFI STATUS ===\nhas_saved_network=true\n=== END STATUS ==="
            )
        )
    }

    @Test
    fun `parses wifi connection variants`() {
        val connected = GhostResponse.WifiConnection.parse("Got IP: 192.168.1.100")
        assertNotNull(connected)
        assertTrue(connected!!.isConnected)
        assertEquals("192.168.1.100", connected.ip)

        val manual = GhostResponse.WifiConnection.parse("WiFi Connected")
        assertTrue(manual!!.isConnected)

        val disconn = GhostResponse.WifiConnection.parse("WiFi Disconnected: reason (4)")
        assertFalse(disconn!!.isConnected)
        assertEquals("reason (4)", disconn.reason)

        val connecting = GhostResponse.WifiConnection.parse(
            "Attempting boot-time connection to saved network: HomeNetwork"
        )
        assertNotNull(connecting)
        assertEquals("HomeNetwork", connecting!!.ssid)
    }

    @Test
    fun `parses base64 SD read and operation result sequence`() {
        val lines = FixtureLoader.load("sd_read_base64.txt").lines().filter { it.isNotEmpty() }
        val begin = lines.first { it.startsWith("SD:READ:BEGIN") }
        val size = lines.first { it.startsWith("SD:READ:SIZE") }
        val end = lines.first { it.startsWith("SD:READ:END") }
        val data = lines.first { !it.startsWith("SD:") }

        // SD:READ:BEGIN:<filename>
        assertNotNull(ResponsePatternsRef.regexMatch(begin, "SD:READ:BEGIN:(.+)"))
        // SD:READ:SIZE:1024
        assertNotNull(ResponsePatternsRef.regexMatch(size, "SD:READ:SIZE:(\\d+)"))
        // SD:READ:END:bytes=1024
        assertNotNull(ResponsePatternsRef.regexMatch(end, "SD:READ:END:bytes=(\\d+)"))

        val bytes = java.util.Base64.getDecoder().decode(data)
        assertEquals("test base64 data blob", String(bytes, Charsets.UTF_8))

        val opResult = GhostResponse.SdOperationResult.parse(end)
        assertNotNull(opResult)
        assertTrue(opResult!!.success)
        assertEquals(1024L, opResult.bytes)
    }

    @Test
    fun `wdstream parses AP BLE and STATUS lines`() {
        val lines = FixtureLoader.load("wdstream.txt").lines().filter { it.isNotEmpty() }
        val begin = lines.first { it.startsWith("WD:BEGIN") }
        val statusFirst = lines.first { it.startsWith("WD:STATUS") }
        val ap = lines.first { it.startsWith("WD:AP ") }
        val ble = lines.first { it.startsWith("WD:BLE ") }
        val end = lines.first { it.startsWith("WD:END") }

        // WD:BEGIN -> status object with running=true
        val beginStatus = GhostResponse.WdStreamStatus.parse(begin)
        assertNotNull(beginStatus)
        assertTrue(beginStatus!!.running)

        // WD:STATUS aps=12 ch=3 up=2s
        val status = GhostResponse.WdStreamStatus.parse(statusFirst)
        assertNotNull(status)
        assertTrue(status!!.running)
        assertEquals(12, status.accessPoints)
        assertEquals(3, status.channel)

        // WD:AP ts=1500 bssid=AA:BB:CC:DD:EE:FF ssid_hex=486F6D65 rssi=-55 ch=6 auth=WPA2 hidden=0
        val apParsed = GhostResponse.WdStreamAp.parse(ap)
        assertNotNull(apParsed)
        apParsed!!
        assertEquals("AA:BB:CC:DD:EE:FF", apParsed.bssid)
        assertEquals("Home", apParsed.ssid)
        assertEquals(-55, apParsed.rssi)
        assertEquals(6, apParsed.channel)
        assertEquals("WPA2", apParsed.auth)
        assertFalse(apParsed.hidden)

        // WD:BLE ts=1600 mac=... name_hex=466C6970706572 -> "Flipper"
        val bleParsed = GhostResponse.WdStreamBle.parse(ble)
        assertNotNull(bleParsed)
        bleParsed!!
        assertEquals("DE:AD:BE:EF:00:01", bleParsed.mac)
        assertEquals("Flipper", bleParsed.name)
        assertEquals(-70, bleParsed.rssi)

        // WD:END -> running=false
        val endStatus = GhostResponse.WdStreamStatus.parse(end)
        assertNotNull(endStatus)
        assertFalse(endStatus!!.running)
    }

    @Test
    fun `wdstream status ignores non WD lines`() {
        assertNull(GhostResponse.WdStreamStatus.parse("random text"))
    }

    @Test
    fun `parses complete chipinfo fixture`() {
        val text = FixtureLoader.load("chipinfo_complete.txt")
        val device = GhostResponse.DeviceInfo.parse(text)
        assertNotNull(device)
        device!!
        assertEquals("ESP32-C6", device.model)
        assertEquals("1.0", device.revision)
        assertEquals(1, device.cores)
        assertEquals(234567L, device.freeHeap)
        assertEquals(198765L, device.minFreeHeap)
        assertEquals("v5.5.1", device.idfVersion)
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.DISPLAY))
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.CHAMELEON))
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.BLE))
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.IEEE802154))
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.OTA))
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.CAMERA))
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.MICROPHONE))
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.GHOSTSCRIPT))
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.NRF24))
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.SUB_GHZ))
        assertTrue(device.chipInfoEndedExplicitly)
    }

    @Test
    fun `parses incomplete chipinfo fixture keeps closed flag unset`() {
        val text = FixtureLoader.load("chipinfo_incomplete.txt")
        val device = GhostResponse.DeviceInfo.parse(text)
        assertNotNull(device)
        device!!
        assertFalse(device.chipInfoEndedExplicitly)
        // capabilities still resolved from silicon features
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.BLE))
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.IEEE802154))
        // Display explicitly listed
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.DISPLAY))
        assertTrue(device.hasFeature(GhostResponse.DeviceFeature.CHAMELEON))
    }

    @Test
    fun `parses listenprobes probe request line`() {
        val probe = GhostResponse.ProbeRequest.parse("Probe Req: AA:BB:CC:DD:EE:FF -> 11:22:33:44:55:66 for iPhone")
        assertNotNull(probe)
        probe!!
        assertEquals("AA:BB:CC:DD:EE:FF", probe.srcMac)
        assertEquals("11:22:33:44:55:66", probe.destMac)
        assertEquals("iPhone", probe.ssid)

        val broadcast = GhostResponse.ProbeRequest.parse("Probe Req: AA:BB:CC:DD:EE:FF -> FF:FF:FF:FF:FF:FF for Broadcast")
        assertNotNull(broadcast)
        assertEquals("Broadcast", broadcast!!.ssid)

        assertNull(GhostResponse.ProbeRequest.parse("Starting to listen for probe requests (channel hopping)..."))
    }

    @Test
    fun `parses congestion table rows`() {
        assertTrue(GhostResponse.CongestionRow.isHeader("| CH | Count | Bar        |"))
        val row = GhostResponse.CongestionRow.parse("|  6 |   123 | ######## |")
        assertNotNull(row)
        row!!
        assertEquals(6, row.channel)
        assertEquals(123, row.count)
        assertEquals("########", row.bar)
        assertNull(GhostResponse.CongestionRow.parse("| CH | Count | Bar        |"))
    }

    @Test
    fun `parses scanports host headers and port lines`() {
        assertEquals("192.168.1.1", GhostResponse.OpenPort.parseHostHeader("Found 3 open ports on 192.168.1.1:"))
        assertEquals("192.168.1.5", GhostResponse.OpenPort.parseHostHeader("Host 192.168.1.5 has 3 open ports"))
        assertEquals("192.168.1.1", GhostResponse.OpenPort.parseHostHeader("Found 2 udp ports responding on 192.168.1.1:"))
        assertEquals(null, GhostResponse.OpenPort.parseHostHeader("No common open ports found."))

        val (port, udp) = GhostResponse.OpenPort.parsePort("  Port 80")!!
        assertEquals(80, port)
        assertFalse(udp)
        val (udpPort, isUdp) = GhostResponse.OpenPort.parsePort("  UDP 53")!!
        assertEquals(53, udpPort)
        assertTrue(isUdp)
    }

    @Test
    fun `parses scanssh open line and banner continuation`() {
        val open = GhostResponse.SshBanner.parseOpen("[192.168.1.5:22] Status: OPEN,")
        assertNotNull(open)
        open!!
        assertEquals("192.168.1.5", open.ip)
        assertEquals(22, open.port)
        assertNull(open.banner)
        assertEquals("SSH-2.0-OpenSSH_7.4", GhostResponse.SshBanner.parseBanner("Banner: SSH-2.0-OpenSSH_7.4"))
        assertEquals("(none)", GhostResponse.SshBanner.parseBanner("Banner: (none)"))
        assertNull(GhostResponse.SshBanner.parseOpen("SSH scan completed on 192.168.1.5 - found 1 open ports"))
    }

    @Test
    fun `parses scanarp host entries and summary`() {
        val host = GhostResponse.ArpHostEntry.parse(" 1. 192.168.1.5 [AA:BB:CC:DD:EE:FF]")
        assertNotNull(host)
        host!!
        assertEquals(1, host.index)
        assertEquals("192.168.1.5", host.ip)
        assertEquals("AA:BB:CC:DD:EE:FF", host.mac)

        val summary = GhostResponse.ArpHostEntry.parseSummary("Found 9 active hosts on 192.168.1.0/24 (3 passes):")
        assertNotNull(summary)
        summary!!
        assertEquals(9, summary.hostCount)
        assertEquals("192.168.1.0", summary.subnet)
        assertEquals(24, summary.cidr)
        assertEquals(3, summary.passes)
    }

    @Test
    fun `parses sweep phase markers and final summary`() {
        assertEquals("Phase 1: WiFi AP Scan (10s)", GhostResponse.SweepPhase.parse("--- Phase 1: WiFi AP Scan (10s) ---")?.message)
        assertEquals("Sweep started", GhostResponse.SweepPhase.parse("=== Starting Full Environment Sweep ===")?.message)
        assertEquals("Sweep complete", GhostResponse.SweepPhase.parse("=== Sweep Complete ===")?.message)
        assertEquals("Report saved to: /mnt/ghostesp/reports/sweep.txt", GhostResponse.SweepPhase.parse("Report saved to: /mnt/ghostesp/reports/sweep.txt")?.message)
        assertEquals("Saving report to: /mnt/ghostesp/sweeps/sweep_1.csv", GhostResponse.SweepPhase.parse("Saving report to: /mnt/ghostesp/sweeps/sweep_1.csv")?.message)

        val summary = GhostResponse.SweepSummary.parse("WiFi: 9 APs, 3 stations | Security: 2 open, 1 weak, 6 secure")
        assertNotNull(summary)
        summary!!
        assertEquals(9, summary.aps)
        assertEquals(3, summary.stations)
        assertEquals(2, summary.open)
        assertEquals(1, summary.weak)
        assertEquals(6, summary.secure)
        assertNull(GhostResponse.SweepSummary.parse("Found 9 access points"))
    }

    @Test
    fun `parses dhcp starve rate and total lines`() {
        val rate = GhostResponse.DhcpStarveStats.parse("DHCP-Starve: 123/sec | Total: 456")!!
        assertEquals(123L, rate.pps)
        assertEquals(456L, rate.total)

        val total = GhostResponse.DhcpStarveStats.parse("DHCP-Starve: Total: 456 packets")!!
        assertNull(total.pps)
        assertEquals(456L, total.total)

        val stopped = GhostResponse.DhcpStarveStats.parse("DHCP-Starve stopped. Total: 456 packets")!!
        assertNull(stopped.pps)
        assertEquals(456L, stopped.total)
    }

    @Test
    fun `parses capture list entries and export outcomes`() {
        val entry = GhostResponse.CaptureListEntry.parse("  [+] handshake_wpa2.pcap")!!
        assertTrue(entry.hasHashcatMaterial)
        assertEquals("handshake_wpa2.pcap", entry.name)
        assertFalse(GhostResponse.CaptureListEntry.parse("  [-] beacon_capture.pcap")!!.hasHashcatMaterial)
        assertTrue(GhostResponse.CaptureListEntry.isEmptyMarker("  No .pcap files found."))
        assertNull(GhostResponse.CaptureListEntry.parse("On-device captures:"))

        val exported = GhostResponse.CaptureExportResult.parseExported("Exported /mnt/ghostesp/pcaps/handshake_wpa2.hc22000")!!
        assertEquals("/mnt/ghostesp/pcaps/handshake_wpa2.hc22000", exported.path)
        val metrics = GhostResponse.CaptureExportResult.parseMetrics("PMKID: 2  M2/M3: 3")!!
        assertEquals(2, metrics.pmkid)
        assertEquals(3, metrics.m2m3)
        assertEquals("No PMKID or handshakes found", GhostResponse.CaptureExportResult.parseFailure("No PMKID or M2/M3 handshakes found in handshake.pcap")!!.failure)
        assertEquals("hc22000 export failed (err=-1)", GhostResponse.CaptureExportResult.parseFailure("hc22000 export failed for x.pcap (err=-1)")!!.failure)
    }

    @Test
    fun `parses ethpoison status and captured items`() {
        val status = GhostResponse.EthPoisonStatus.parse("[ARP Poison] State: RUNNING | Hosts: 4 | Domains: 2 | Cookies: 5 | Creds: 3")!!
        assertEquals("RUNNING", status.state)
        assertEquals(4, status.hosts)
        assertEquals(2, status.domains)
        assertEquals(5, status.cookies)
        assertEquals(3, status.creds)
        assertEquals("STOPPED", GhostResponse.EthPoisonStatus.parse("[ARP Poison] Not running")!!.state)
        val stopped = GhostResponse.EthPoisonStatus.parse("[ARP Poison] Stopped. 1 domains, 2 cookies, 3 creds captured.")!!
        assertEquals("STOPPED", stopped.state)
        assertEquals(2, stopped.cookies)

        val header = GhostResponse.EthPoisonItem.parseHeader("[ARP Poison] Captured domains (2):")!!
        assertEquals(GhostResponse.EthPoisonItem.EthPoisonKind.DOMAINS, header.kind)
        assertEquals(2, header.total)
        assertEquals("ad.doubleclick.net", GhostResponse.EthPoisonItem.parseItem("  1. ad.doubleclick.net")!!.value)
    }

    @Test
    fun `parses sinkhole status lines and live stats`() {
        val running = GhostResponse.SinkholeStatus.parse("  State:    RUNNING")!!
        assertEquals("RUNNING", running.state)
        val queries = GhostResponse.SinkholeStatus.parse("  Queries:  123")!!
        assertEquals(123L, queries.queries)
        val blocked = GhostResponse.SinkholeStatus.parse("  Blocked:  12")!!
        assertEquals(12L, blocked.blocked)
        val pct = GhostResponse.SinkholeStatus.parse("  Block %:  9.8%")!!
        assertEquals(9.8, pct.blockPercent!!, 0.001)
        assertEquals(true, GhostResponse.SinkholeStatus.parse("  Logging:  ON")!!.logging)
        assertEquals("present", GhostResponse.SinkholeStatus.parse("  Blocklist: present")!!.blocklist)
        assertTrue(GhostResponse.SinkholeStatus.isHeader("=== DNS Sinkhole Status ==="))

        val live = GhostResponse.SinkholeStatus.parse("Sinkhole: 5 queries, 1 blocked, 0 dropped")!!
        assertEquals(5L, live.queries)
        assertEquals(1L, live.blocked)
        assertEquals(0L, live.dropped)
        assertNull(GhostResponse.SinkholeStatus.parse("DNS sinkhole: ready"))
    }

    @Test
    fun `parses netbios scan completion markers`() {
        val subnet = GhostResponse.NetBiosScanComplete.parse("NetBIOS Scan: Subnet scan complete")!!
        assertNull(subnet.target)

        val host = GhostResponse.NetBiosScanComplete.parse("NetBIOS scan completed on 192.168.1.5")!!
        assertEquals("192.168.1.5", host.target)

        assertNull(GhostResponse.NetBiosScanComplete.parse("[NetBIOS] Host: 192.168.1.5  Names: none"))
        assertNull(GhostResponse.NetBiosScanComplete.parse("NetBIOS Scan: Scanning 254 hosts..."))
    }

    @Test
    fun `parses webui ap and webauth toggles`() {
        assertEquals(true, GhostResponse.WebUiApState.parse("WebUI AP-only restriction is enabled.")!!.enabled)
        assertEquals(false, GhostResponse.WebUiApState.parse("WebUI AP-only restriction disabled.")!!.enabled)
        assertEquals(true, GhostResponse.WebAuthResult.parse("Web authentication enabled.")!!.enabled)
        assertEquals(false, GhostResponse.WebAuthResult.parse("Web authentication disabled.")!!.enabled)
    }

    @Test
    fun `parses current firmware wardrive heartbeat - primary variant`() {
        val line = "Wardrive: ap=123 logged=45/67 gpsrej=3 helper=1/2 peergps(rx/fix tx_ok/fail)=0/0 0/0 ch=1 up=0m42s gps=No Fix/0 q=0/64 hi=0 drop=0/0/0 pending=12B heap=345/456B"
        val stats = GhostResponse.WardriveStats.parse(line)
        assertNotNull(stats)
        stats!!
        assertEquals(123, stats.accessPoints)
        assertEquals(45, stats.loggedOk)
        assertEquals(67, stats.logAttempts)
        assertEquals(3, stats.gpsRejected)
        assertEquals(1, stats.channel)
        assertEquals(0, stats.uptimeMinutes)
        assertEquals(42, stats.uptimeSeconds)
        assertEquals("No Fix", stats.gpsFixStatus)
        assertEquals(0, stats.gpsSatellites)
        assertEquals(12, stats.pendingBytes)
    }

    @Test
    fun `parses current firmware wardrive heartbeat - helper variant`() {
        val line = "Wardrive: ap=9 logged=2/4 gpsrej=0 helper=2/10 tx(n/p/r/t/s)=1/2/3/4/5 send(ok/fail)=6/7 peergps(rx/fix tx_ok/fail)=1/1 1/1 ch=6 up=1m05s gps=3D/8 q=1/64 hi=2 drop=0/0/0 pending=0B heap=200/300B"
        val stats = GhostResponse.WardriveStats.parse(line)
        assertNotNull(stats)
        stats!!
        assertEquals(9, stats.accessPoints)
        assertEquals(2, stats.loggedOk)
        assertEquals(6, stats.channel)
        assertEquals(1, stats.uptimeMinutes)
        assertEquals(5, stats.uptimeSeconds)
        assertEquals("3D", stats.gpsFixStatus)
        assertEquals(8, stats.gpsSatellites)
    }

    @Test
    fun `parses scanlocal ip lookup device lines`() {
        assertEquals("192.168.1.5", GhostResponse.IpLookupDevice.parseDevice("Device at: 192.168.1.5"))
        assertEquals("Living Room TV", GhostResponse.IpLookupDevice.parseName("  Name: Living Room TV"))
        assertEquals("Media Streamer", GhostResponse.IpLookupDevice.parseType("  Type: Media Streamer"))
        assertEquals(8080, GhostResponse.IpLookupDevice.parsePort("  Port: 8080"))
        assertEquals(3, GhostResponse.IpLookupDevice.parseDone("IP Scan Done. Found 3 devices."))
        assertNull(GhostResponse.IpLookupDevice.parseDevice("IP Scan Done. Found 3 devices."))
    }

    @Test
    fun `parses scanports range and subnet output formats`() {
        // range/all scan: "Port 80: OPEN" / "UDP 53: OPEN"
        val (tcpPort, tcpUdp) = GhostResponse.OpenPort.parsePort("  Port 80: OPEN")!!
        assertEquals(80, tcpPort)
        assertFalse(tcpUdp)
        val (udpPort, udp) = GhostResponse.OpenPort.parsePort("  UDP 53: OPEN")!!
        assertEquals(53, udpPort)
        assertTrue(udp)
        // common scan still works
        assertEquals(80 to false, GhostResponse.OpenPort.parsePort("  Port 80"))

        // subnet scan host discovery + UDP block header
        assertEquals("192.168.1.5", GhostResponse.OpenPort.parseHostHeader("[Host 1] Found active host: 192.168.1.5"))
        assertEquals("192.168.1.5", GhostResponse.OpenPort.parseHostHeader("UDP ports on 192.168.1.5:"))
    }

    @Test
    fun `parses scan completion lines`() {
        val done = GhostResponse.ScanCompletion.parse("Scan completed. Found 3 active hosts.")!!
        assertEquals(3, done.hostCount)
        assertFalse(done.cancelled)
        val cancelled = GhostResponse.ScanCompletion.parse("Scan cancelled. Found 1 active hosts.")!!
        assertEquals(1, cancelled.hostCount)
        assertTrue(cancelled.cancelled)
        assertNull(GhostResponse.ScanCompletion.parse("Scanning 254 hosts..."))
    }

    @Test
    fun `parses ssh scan completion summaries`() {
        val host = GhostResponse.SshScanSummary.parse("SSH scan completed on 192.168.1.5 - found 2 open ports")!!
        assertEquals("192.168.1.5", host.target)
        assertEquals(2, host.portCount)
        assertNull(host.hostCount)

        val subnet = GhostResponse.SshScanSummary.parse("SSH Scan: Subnet scan complete - found 3 hosts with 5 open SSH ports")!!
        assertEquals(3, subnet.hostCount)
        assertEquals(5, subnet.portCount)

        val cancelled = GhostResponse.SshScanSummary.parse("SSH Scan: Cancelled. Found 1 hosts with 1 open SSH ports")!!
        assertEquals(1, cancelled.hostCount)
        assertEquals(1, cancelled.portCount)
    }

    @Test
    fun `parses http banner response and tls lines`() {
        val response = GhostResponse.HttpBannerHit.parse("[192.168.1.5:80] (http) Response: <!DOCTYPE html><html>...")!!
        assertEquals("192.168.1.5", response.ip)
        assertEquals(80, response.port)
        assertTrue(response.response!!.startsWith("<!DOCTYPE html>"))

        val tls = GhostResponse.HttpBannerHit.parse("[192.168.1.5:443] (https) Status: OPEN, TLS banner requires handshake")!!
        assertTrue(tls.tlsNoBanner)
        assertNull(tls.server)
    }

    @Test
    fun `wdstream status honors running field`() {
        val running = GhostResponse.WdStreamStatus.parse("WD:STATUS running=1 type=wifi interval=2000 channel=auto aps=12 bles=0 scans=3 ch=6 uptime=5s")!!
        assertTrue(running.running)
        assertEquals(12, running.accessPoints)

        val stopped = GhostResponse.WdStreamStatus.parse("WD:STATUS running=0 aps=12 bles=0")!!
        assertFalse(stopped.running)
    }
}

private object ResponsePatternsRef {
    fun regexMatch(input: String, pattern: String): kotlin.text.MatchResult? =
        Regex(pattern).find(input)
}
