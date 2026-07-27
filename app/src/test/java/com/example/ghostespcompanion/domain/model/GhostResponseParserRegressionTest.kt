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
        val services = text.split("\n\n").mapNotNull { block ->
            GhostResponse.GattService.parse(block).also { println("block=$block -> $it") }
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
        assertEquals("v1.0", device.revision)
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
}

private object ResponsePatternsRef {
    fun regexMatch(input: String, pattern: String): kotlin.text.MatchResult? =
        Regex(pattern).find(input)
}
