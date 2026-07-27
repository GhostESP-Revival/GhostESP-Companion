package com.example.ghostespcompanion.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostResponseTest {
    @Test
    fun `parses old and current GATT service fixtures`() {
        val fixtures = listOf(
            "Service: Generic Access (0x1800) handles 1-7" to
                GhostResponse.GattService("0x1800", "Generic Access", 1, 7),
            "Service: Battery Service (0x180F) [handles 12-15]" to
                GhostResponse.GattService("0x180F", "Battery Service", 12, 15),
            "[2] Service:\r\nUUID: 0x180F\r\nHandles: 12-15" to
                GhostResponse.GattService("0x180F", null, 12, 15),
            """[0] Service: Generic Attribute,
                |     UUID: 0x1801,
                |     Handles: 8-11
            """.trimMargin() to GhostResponse.GattService("0x1801", "Generic Attribute", 8, 11)
        )

        fixtures.forEach { (fixture, expected) -> assertEquals(expected, GhostResponse.GattService.parse(fixture)) }
    }

    @Test
    fun `parses current error forms without classifying benign output`() {
        val errors = listOf(
            "ERROR: Capture Type cannot be empty.",
            "Error: Incorrect number of arguments.",
            "Failed to start service discovery: 5",
            "Invalid channel 27. Must be between 11 and 26",
            "Unknown command: frobnicate",
            "Timeout waiting for device",
            "Timed out while connecting",
            "Unsupported capture mode",
            "BLE is not supported on this board"
        )
        val benign = listOf(
            "Service discovery complete. Found 5 services.",
            "Failed packets: 0",
            "Invalid frames: 0",
            "Unknown devices: 3",
            "Timeout: 30 seconds",
            "This help page lists unsupported forms for reference"
        )

        errors.forEach { assertNotNull(it, GhostResponse.Error.parse(it)) }
        benign.forEach { assertNull(it, GhostResponse.Error.parse(it)) }
    }

    @Test
    fun `parses legacy and current chip capabilities`() {
        val fixture = """
            Chip Information:
              Firmware: GhostESP Development 1.9.9
              Model: ESP32-C6
              Revision: v1.0
              CPU Cores: 1
              Features: WiFi/BT/BLE/802.15.4/Embedded Flash
              Free Heap: 123456 bytes
              Min Heap: 100000 bytes
              IDF Version: v5.5.1

              Enabled Features:
                Display
                Chameleon Ultra
                OTA Updates
                Camera
                Microphone
                GhostScript
                NRF24
                SubGHz
        """.trimIndent()

        val result = GhostResponse.DeviceInfo.parse(fixture)
        assertNotNull(result)
        result!!
        assertTrue(result.hasFeature(GhostResponse.DeviceFeature.DISPLAY))
        listOf(
            GhostResponse.DeviceFeature.BLE,
            GhostResponse.DeviceFeature.IEEE802154,
            GhostResponse.DeviceFeature.CHAMELEON,
            GhostResponse.DeviceFeature.OTA,
            GhostResponse.DeviceFeature.CAMERA,
            GhostResponse.DeviceFeature.MICROPHONE,
            GhostResponse.DeviceFeature.GHOSTSCRIPT,
            GhostResponse.DeviceFeature.NRF24,
            GhostResponse.DeviceFeature.SUB_GHZ
        ).forEach { assertTrue(it.name, result.hasFeature(it)) }
        assertEquals(100000L, result.minFreeHeap)
    }

    @Test
    fun `complete inventory makes absent features unsupported`() {
        val result = GhostResponse.DeviceInfo.parse(chipInfo("NFC", ended = true))!!

        assertTrue(result.chipInfoEndedExplicitly)
        assertEquals(GhostResponse.CapabilityResolution.SUPPORTED, result.resolveFeature(GhostResponse.DeviceFeature.NFC))
        assertEquals(GhostResponse.CapabilityResolution.UNSUPPORTED, result.resolveFeature(GhostResponse.DeviceFeature.BADUSB))
        assertEquals(GhostResponse.CapabilityResolution.UNSUPPORTED, result.resolveCapability(GhostResponse.DeviceFeature.CHAMELEON))
    }

    @Test
    fun `incomplete inventory keeps absent features unknown and usable`() {
        val result = GhostResponse.DeviceInfo.parse(chipInfo("NFC", ended = false))!!

        assertEquals(GhostResponse.CapabilityResolution.UNKNOWN, result.resolveFeature(GhostResponse.DeviceFeature.BADUSB))
        assertTrue(result.resolveFeature(GhostResponse.DeviceFeature.BADUSB).isUsable)
        assertEquals(GhostResponse.CapabilityResolution.UNKNOWN, result.resolveCapability(GhostResponse.DeviceFeature.CHAMELEON))
    }

    @Test
    fun `silicon features resolve capabilities without a complete board inventory`() {
        val result = GhostResponse.DeviceInfo.parse(chipInfo("Display", ended = false, silicon = "WiFi/BT/BLE/802.15.4"))!!

        assertEquals(GhostResponse.CapabilityResolution.SUPPORTED, result.resolveCapability(GhostResponse.DeviceFeature.BLE))
        assertEquals(GhostResponse.CapabilityResolution.SUPPORTED, result.resolveCapability(GhostResponse.DeviceFeature.IEEE802154))
    }

    private fun chipInfo(enabledFeature: String, ended: Boolean, silicon: String = "WiFi") = """
        Chip Information:
        Model: ESP32-C6
        Revision: v1.0
        CPU Cores: 1
        Features: $silicon
        Free Heap: 100 bytes
        Min Heap: 90 bytes
        IDF Version: v5.5.1
        Enabled Features:
        $enabledFeature
        ${if (ended) "[CHIPINFO_END]" else ""}
    """.trimIndent()
}
