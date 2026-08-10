package com.example.ghostespcompanion.data.serial

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BleBridgeProtocolTest {
    @Test
    fun shortCommandUsesLegacyCompleteFlag() {
        val frames = BleBridgeProtocol.commandFrames(0x12345678, "status".toByteArray(), 128)

        assertEquals(1, frames.size)
        assertEquals(0, frames.single()[5].toInt())
        assertEquals(0x78, frames.single()[6].toInt() and 0xFF)
        assertArrayEquals("status".toByteArray(), frames.single().copyOfRange(12, frames.single().size))
    }

    @Test
    fun longCommandUsesFirmwareFragmentFlags() {
        val payload = ByteArray(20) { it.toByte() }
        val frames = BleBridgeProtocol.commandFrames(7, payload, BleBridgeProtocol.DEFAULT_MTU)

        assertEquals(3, frames.size)
        assertEquals(BleBridgeProtocol.FLAG_FIRST or BleBridgeProtocol.FLAG_MORE, frames[0][5].toInt() and 0xFF)
        assertEquals(BleBridgeProtocol.FLAG_MORE, frames[1][5].toInt() and 0xFF)
        assertEquals(0, frames[2][5].toInt() and 0xFF)
        assertEquals(listOf(8, 8, 4), frames.map { it.size - BleBridgeProtocol.HEADER_LENGTH })
        assertArrayEquals(payload, frames.flatMap { it.drop(12) }.toByteArray())
    }

    @Test
    fun commandBoundsAreEnforcedByByteLength() {
        assertThrows(IllegalArgumentException::class.java) {
            BleBridgeProtocol.commandFrames(1, byteArrayOf(), 23)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BleBridgeProtocol.commandFrames(1, ByteArray(BleBridgeProtocol.MAX_COMMAND_BYTES + 1), 23)
        }

        val maximum = ByteArray(BleBridgeProtocol.MAX_COMMAND_BYTES) { it.toByte() }
        val frames = BleBridgeProtocol.commandFrames(1, maximum, 128)
        assertArrayEquals(maximum, frames.flatMap { it.drop(BleBridgeProtocol.HEADER_LENGTH) }.toByteArray())
    }

    @Test
    fun decoderHandlesSplitHeaderAndPayload() {
        val frame = BleBridgeProtocol.commandFrames(42, "fragmented".toByteArray(), 128).single()
        val decoder = BleBridgeProtocol.Decoder()

        assertEquals(0, decoder.feed(frame.copyOfRange(0, 5)).frames.size)
        assertEquals(0, decoder.feed(frame.copyOfRange(5, 14)).frames.size)
        val result = decoder.feed(frame.copyOfRange(14, frame.size))

        assertEquals(1, result.frames.size)
        assertEquals(42, result.frames.single().commandId)
        assertArrayEquals("fragmented".toByteArray(), result.frames.single().payload)
    }

    @Test
    fun decoderReturnsMultipleFramesFromOneNotification() {
        val first = BleBridgeProtocol.commandFrames(1, "one".toByteArray(), 128).single()
        val second = BleBridgeProtocol.commandFrames(2, "two".toByteArray(), 128).single()
        val result = BleBridgeProtocol.Decoder().feed(first + second)

        assertEquals(listOf(1, 2), result.frames.map { it.commandId })
        assertEquals(listOf("one", "two"), result.frames.map { it.payload.toString(Charsets.UTF_8) })
    }

    @Test
    fun decoderHandlesEveryByteInItsOwnNotification() {
        val frame = BleBridgeProtocol.commandFrames(42, "bytewise".toByteArray(), 128).single()
        val decoder = BleBridgeProtocol.Decoder()
        val decoded = frame.flatMap { byte -> decoder.feed(byteArrayOf(byte)).frames }

        assertEquals(1, decoded.size)
        assertEquals(42, decoded.single().commandId)
        assertArrayEquals("bytewise".toByteArray(), decoded.single().payload)
    }

    @Test
    fun decoderHandlesCompleteAndPartialFramesTogether() {
        val first = BleBridgeProtocol.commandFrames(1, "one".toByteArray(), 128).single()
        val second = BleBridgeProtocol.commandFrames(2, "two".toByteArray(), 128).single()
        val decoder = BleBridgeProtocol.Decoder()

        val initial = decoder.feed(first + second.copyOfRange(0, 7))
        val trailing = decoder.feed(second.copyOfRange(7, second.size))

        assertEquals(listOf(1), initial.frames.map { it.commandId })
        assertEquals(listOf(2), trailing.frames.map { it.commandId })
        assertArrayEquals("two".toByteArray(), trailing.frames.single().payload)
    }

    @Test
    fun decoderPreservesSplitMagicAndReturnsUnframedBytes() {
        val frame = BleBridgeProtocol.commandFrames(9, "ok".toByteArray(), 128).single()
        val decoder = BleBridgeProtocol.Decoder()

        val first = decoder.feed("rawG".toByteArray())
        assertArrayEquals("raw".toByteArray(), first.fallback)
        val second = decoder.feed(frame.copyOfRange(1, frame.size))

        assertEquals(1, second.frames.size)
        assertEquals(9, second.frames.single().commandId)
    }

    @Test
    fun decoderResynchronizesAfterInvalidPayloadLength() {
        val corrupt = ByteArray(BleBridgeProtocol.HEADER_LENGTH).apply {
            this[0] = 0x47
            this[1] = 0x42
            this[2] = 0x01
            this[3] = 3
            this[10] = 0xFF.toByte()
            this[11] = 0x7F
        }
        val valid = BleBridgeProtocol.commandFrames(11, "recovered".toByteArray(), 128).single()

        val result = BleBridgeProtocol.Decoder().feed(corrupt + valid)

        assertEquals(1, result.frames.size)
        assertEquals(11, result.frames.single().commandId)
        assertArrayEquals("recovered".toByteArray(), result.frames.single().payload)
        assertTrue(result.fallback.isEmpty())
    }

    @Test
    fun decoderResynchronizesAfterInvalidFrameType() {
        val corrupt = BleBridgeProtocol.commandFrames(10, "bad".toByteArray(), 128).single().apply {
            this[3] = 99
        }
        val valid = BleBridgeProtocol.commandFrames(12, "ok".toByteArray(), 128).single()

        val result = BleBridgeProtocol.Decoder().feed(corrupt + valid)

        assertEquals(listOf(12), result.frames.map { it.commandId })
        assertTrue(result.fallback.isEmpty())
    }

    @Test
    fun currentGattServiceBlockIsClassified() {
        val response = GhostSerialResponse("[2] Service:\nUUID: 0x180f\nHandles: 12-15")

        assertEquals(GhostSerialResponse.ResponseType.GATT_SERVICE, response.type)
    }

    @Test
    fun bridgeErrorTextUsesExistingErrorType() {
        val response = GhostSerialResponse("ERROR: BLE bridge status=3: invalid command")

        assertEquals(GhostSerialResponse.ResponseType.ERROR, response.type)
    }
}
