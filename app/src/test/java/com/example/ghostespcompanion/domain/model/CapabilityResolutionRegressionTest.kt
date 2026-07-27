package com.example.ghostespcompanion.domain.model

import com.example.ghostespcompanion.test.FixtureLoader
import com.example.ghostespcompanion.ui.components.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tri-state capability resolution regression tests for BLE, IEEE 802.15.4,
 * Chameleon, and NFC pools, exercising both complete (`[CHIPINFO_END]`) and
 * incomplete firmware chipinfo inventories.
 *
 * Capability semantics (GhostResponse.DeviceInfo):
 *   SUPPORTED    -> the device explicitly declares a feature
 *   UNSUPPORTED  -> feature was absent but the inventory is complete
 *   UNKNOWN      -> feature was absent and the inventory is incomplete; UI
 *                   keeps controls available (CapabilityResolution.isUsable).
 *
 * Includes the nullable DeviceInfo extension used by the UI layer
 * (CapabilityGating.kt) that maps a null DeviceInfo to UNKNOWN.
 */
class CapabilityResolutionRegressionTest {

    @Test
    fun `BLE is supported when firmware declares BLE either way`() {
        val complete = GhostResponse.DeviceInfo.parse(load("chipinfo_complete.txt"))!!
        val bleFirmwareSanity = parseChipInfoText(enabledFeature = "OTA", silicon = "WiFi/BT/BLE", ended = false)
        val incomplete = GhostResponse.DeviceInfo.parse(bleFirmwareSanity)!!

        assertEquals(GhostResponse.CapabilityResolution.SUPPORTED, complete.resolveCapability(GhostResponse.DeviceFeature.BLE))
        assertEquals(GhostResponse.CapabilityResolution.SUPPORTED, incomplete.resolveCapability(GhostResponse.DeviceFeature.BLE))
    }

    @Test
    fun `BLE flips to UNSUPPORTED only when inventory is complete and BLE is absent`() {
        val complete = parseChipInfo(enabledFeature = "NFC", silicon = "WiFi", ended = true)!!
        assertEquals(
            GhostResponse.CapabilityResolution.UNSUPPORTED,
            complete.resolveCapability(GhostResponse.DeviceFeature.BLE)
        )
    }

    @Test
    fun `BLE stays UNKNOWN when inventory is incomplete and BLE is absent`() {
        val incomplete = parseChipInfo(enabledFeature = "NFC", silicon = "WiFi", ended = false)!!
        assertEquals(
            GhostResponse.CapabilityResolution.UNKNOWN,
            incomplete.resolveCapability(GhostResponse.DeviceFeature.BLE)
        )
        assertTrue(incomplete.resolveCapability(GhostResponse.DeviceFeature.BLE).isUsable)
    }

    @Test
    fun `IEEE 802_15_4 is supported via silicon tokens`() {
        val esp32c6 = parseChipInfo(enabledFeature = "Display", silicon = "WiFi/BT/BLE/802.15.4", ended = false)!!
        assertEquals(SUPPORTED, esp32c6.resolveCapability(GhostResponse.DeviceFeature.IEEE802154))
        // alternate token spelling 802154 should also map
        val alt = parseChipInfo(enabledFeature = "Display", silicon = "WiFi/802154", ended = false)!!
        assertEquals(SUPPORTED, alt.resolveCapability(GhostResponse.DeviceFeature.IEEE802154))
    }

    @Test
    fun `802_15_4 resolves to UNSUPPORTED on a complete non-15_4 board`() {
        val complete = parseChipInfo(enabledFeature = "Display", silicon = "WiFi", ended = true)!!
        assertEquals(UNSUPPORTED, complete.resolveCapability(GhostResponse.DeviceFeature.IEEE802154))
    }

    @Test
    fun `802_15_4 stays UNKNOWN on an incomplete non-15_4 board`() {
        val incomplete = parseChipInfo(enabledFeature = "Display", silicon = "WiFi", ended = false)!!
        assertEquals(UNKNOWN, incomplete.resolveCapability(GhostResponse.DeviceFeature.IEEE802154))
        assertFalse(incomplete.resolveCapability(GhostResponse.DeviceFeature.IEEE802154) == UNSUPPORTED)
    }

    @Test
    fun `Chameleon resolves SPECIFIED via Chameleon Ultra enabled feature`() {
        val complete = parseChipInfo(enabledFeature = "Chameleon Ultra", silicon = "WiFi", ended = false)!!
        assertEquals(SUPPORTED, complete.resolveCapability(GhostResponse.DeviceFeature.CHAMELEON))
        // Without end marker, missing still leaves it as UNKNOWN
    }

    @Test
    fun `Chameleon flips to UNSUPPORTED when complete inventory omits it`() {
        val complete = parseChipInfo(enabledFeature = "NFC", silicon = "WiFi", ended = true)!!
        assertEquals(UNSUPPORTED, complete.resolveCapability(GhostResponse.DeviceFeature.CHAMELEON))
    }

    @Test
    fun `Chameleon stays UNKNOWN when incomplete and not declared`() {
        val incomplete = parseChipInfo(enabledFeature = "OTA Updates", silicon = "WiFi", ended = false)!!
        assertEquals(UNKNOWN, incomplete.resolveCapability(GhostResponse.DeviceFeature.CHAMELEON))
    }

    @Test
    fun `NFC supported via DeviceFeature NFC enabled feature`() {
        val complete = parseChipInfo(enabledFeature = "NFC", silicon = "WiFi", ended = true)!!
        assertEquals(SUPPORTED, complete.resolveFeature(GhostResponse.DeviceFeature.NFC))
        // unsupported when complete and absent
        assertEquals(UNSUPPORTED, complete.resolveFeature(GhostResponse.DeviceFeature.BADUSB))
    }

    @Test
    fun `NFC stays UNKNOWN when inventory is incomplete and not declared`() {
        val incomplete = parseChipInfo(enabledFeature = "GPS", silicon = "WiFi", ended = false)!!
        assertEquals(UNKNOWN, incomplete.resolveFeature(GhostResponse.DeviceFeature.NFC))
    }

    @Test
    fun `resolve multi-arg picks supported when any feature is supported`() {
        val complete = parseChipInfo(enabledFeature = "NFC", silicon = "WiFi", ended = true)!!
        assertEquals(
            SUPPORTED,
            complete.resolveFeature(GhostResponse.DeviceFeature.NFC, GhostResponse.DeviceFeature.BADUSB)
        )
    }

    @Test
    fun `resolve multi-arg flips to UNSUPPORTED when complete and none declared`() {
        val complete = parseChipInfo(enabledFeature = "Display", silicon = "WiFi", ended = true)!!
        assertEquals(
            UNSUPPORTED,
            complete.resolveFeature(GhostResponse.DeviceFeature.NFC, GhostResponse.DeviceFeature.BADUSB)
        )
    }

    @Test
    fun `resolve multi-arg stays UNKNOWN when incomplete and none declared`() {
        val incomplete = parseChipInfo(enabledFeature = "Display", silicon = "WiFi", ended = false)!!
        assertEquals(
            UNKNOWN,
            incomplete.resolveFeature(GhostResponse.DeviceFeature.NFC, GhostResponse.DeviceFeature.BADUSB)
        )
    }

    @Test
    fun `null DeviceInfo resolves to UNKNOWN via gadget`() {
        assertEquals(UNKNOWN, (null as GhostResponse.DeviceInfo?).resolve(GhostResponse.DeviceFeature.NFC))
        assertEquals(UNKNOWN, (null as GhostResponse.DeviceInfo?).resolve(GhostResponse.DeviceFeature.BLE))
        assertEquals(UNKNOWN, (null as GhostResponse.DeviceInfo?).resolve(GhostResponse.DeviceFeature.CHAMELEON))
        assertEquals(UNKNOWN, (null as GhostResponse.DeviceInfo?).resolve(GhostResponse.DeviceFeature.IEEE802154))
        assertEquals(
            UNKNOWN,
            (null as GhostResponse.DeviceInfo?).resolve(GhostResponse.DeviceFeature.NFC, GhostResponse.DeviceFeature.BADUSB)
        )
    }

    @Test
    fun `complete chipinfo fixture resolves BLE 802_15_4 Chameleon NFC consistently`() {
        val device = GhostResponse.DeviceInfo.parse(load("chipinfo_complete.txt"))!!
        assertTrue(device.chipInfoEndedExplicitly)
        assertEquals(SUPPORTED, device.resolveCapability(GhostResponse.DeviceFeature.BLE))
        assertEquals(SUPPORTED, device.resolveCapability(GhostResponse.DeviceFeature.IEEE802154))
        assertEquals(SUPPORTED, device.resolveCapability(GhostResponse.DeviceFeature.CHAMELEON))
        assertEquals(SUPPORTED, device.resolveFeature(GhostResponse.DeviceFeature.NFC))

        // capability declared as ENABLED on the complete board
        val supportedCapabilities = listOf(
            GhostResponse.DeviceFeature.BLE,
            GhostResponse.DeviceFeature.IEEE802154,
            GhostResponse.DeviceFeature.CHAMELEON,
            GhostResponse.DeviceFeature.OTA,
            GhostResponse.DeviceFeature.CAMERA,
            GhostResponse.DeviceFeature.MICROPHONE,
            GhostResponse.DeviceFeature.GHOSTSCRIPT,
            GhostResponse.DeviceFeature.NRF24,
            GhostResponse.DeviceFeature.SUB_GHZ
        )
        supportedCapabilities.forEach { capability ->
            assertEquals(
                "capability ${capability.name}",
                SUPPORTED,
                device.resolveCapability(capability)
            )
        }

        val supportedDeviceFeatures = listOf(
            GhostResponse.DeviceFeature.DISPLAY
        )
        supportedDeviceFeatures.forEach { feature ->
            assertEquals(
                "feature ${feature.name}",
                SUPPORTED,
                device.resolveFeature(feature)
            )
        }
    }

    @Test
    fun `complete fixture marks NFC supported and BluetoothLE supported via capability tree`() {
        val device = GhostResponse.DeviceInfo.parse(parseChipInfoText(
            "NFC", silicon = "WiFi/BT/BLE/802.15.4", ended = true,
            extraEnabled = "BLE"
        ))!!
        assertEquals(SUPPORTED, device.resolveFeature(GhostResponse.DeviceFeature.NFC))
        assertEquals(SUPPORTED, device.resolveCapability(GhostResponse.DeviceFeature.BLE))
        // After complete inventory, undetected capability still comes from silicon but unspecified
        assertEquals(SUPPORTED, device.resolveCapability(GhostResponse.DeviceFeature.IEEE802154))
    }

    companion object {
        private val SUPPORTED = GhostResponse.CapabilityResolution.SUPPORTED
        private val UNSUPPORTED = GhostResponse.CapabilityResolution.UNSUPPORTED
        private val UNKNOWN = GhostResponse.CapabilityResolution.UNKNOWN
    }

    private fun load(name: String) = FixtureLoader.load(name)

    private fun parseChipInfo(enabledFeature: String, silicon: String, ended: Boolean): GhostResponse.DeviceInfo? =
        GhostResponse.DeviceInfo.parse(parseChipInfoText(enabledFeature, silicon, ended))

    private fun parseChipInfoText(enabledFeature: String, silicon: String, ended: Boolean, extraEnabled: String = ""): String {
        val tail = if (extraEnabled.isNotEmpty()) "  $extraEnabled\n" else ""
        val endMarker = if (ended) "[CHIPINFO_END]" else ""
        return """
            === Chip Information ===
            Model: TestBoard
            Revision: v1.0
            CPU Cores: 1
            Features: $silicon
            Free Heap: 100 bytes
            Min Heap: 90 bytes
            IDF Version: v5.5.0
            Enabled Features:
              $enabledFeature
            $tail$endMarker
        """.trimIndent()
    }
}
