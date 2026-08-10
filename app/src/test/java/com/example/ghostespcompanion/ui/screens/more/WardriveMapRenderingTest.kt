package com.example.ghostespcompanion.ui.screens.more

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WardriveMapRenderingTest {
    @Test
    fun `signal strength is normalized and clamped`() {
        assertEquals(0f, wardriveSignalStrength(-120), 0f)
        assertEquals(0.5f, wardriveSignalStrength(-70), 0.001f)
        assertEquals(1f, wardriveSignalStrength(-30), 0f)
    }

    @Test
    fun `clusters are replaced by individual markers at detail zoom`() {
        assertTrue(shouldClusterWardriveAps(15.99))
        assertFalse(shouldClusterWardriveAps(16.0))
    }

    @Test
    fun `marker radius scales with density and zoom`() {
        assertEquals(9f, wardriveMarkerRadius(13.0, 2f), 0.001f)
        assertTrue(wardriveMarkerRadius(18.0, 2f) > wardriveMarkerRadius(14.0, 2f))
    }
}
