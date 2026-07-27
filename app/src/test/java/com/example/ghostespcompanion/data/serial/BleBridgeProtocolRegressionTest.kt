package com.example.ghostespcompanion.data.serial

import com.example.ghostespcompanion.domain.model.GhostResponse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for BleBridgeProtocol encoder/decoder covering:
 * - single-frame (legacy flags=0) and fragmented (FIRST|MORE / MORE / final flags=0)
 * - oversized-rejection boundaries
 * - decoder behaviour vs ERR-style bridge frames (relayed via GhostSerialResponse)
 * - decoder behaviour across fragment boundaries
 *
 * The actual END-of-stream handling lives in SerialManager.processBleBridgeFrame
 * (Android-bound code beyond this unit test); the unit-level checks here ensure
 * the decoder surfaces the proper frame type+commandId+payload so the END
 * completion is correctly routed.
 */
class BleBridgeProtocolRegressionTest {

    @Test
    fun `single frame uses legacy flags zero and exact command id`() {
        val frames = BleBridgeProtocol.commandFrames(0xABCD, byteArrayOf(0x01, 0x02, 0x03), 23)
        assertEquals(1, frames.size)
        val frame = frames.single()
        assertEquals(0, frame[5].toInt() and 0xFF)            // flags=0
        assertEquals(1, frame[3].toInt() and 0xFF)            // TYPE_COMMAND
        assertEquals(0xCD, frame[6].toInt() and 0xFF)          // commandId byte0
        assertEquals(0xAB, frame[7].toInt() and 0xFF)         // commandId byte1
        assertEquals(0, frame[8].toInt() and 0xFF)
        assertEquals(0, frame[9].toInt() and 0xFF)
        assertEquals(3, frame[10].toInt() and 0xFF)            // payload length low
        assertEquals(0, frame[11].toInt() and 0xFF)            // payload length high
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), frame.copyOfRange(BleBridgeProtocol.HEADER_LENGTH, frame.size))
    }

    @Test
    fun `large payload fragments with FIRST MORE and final flags`() {
        val payload = ByteArray(BleBridgeProtocol.MAX_COMMAND_BYTES) { (it and 0xFF).toByte() }
        val frames = BleBridgeProtocol.commandFrames(42, payload, BleBridgeProtocol.DEFAULT_MTU)

        assertTrue("expected multiple fragments", frames.size >= 2)
        // First chunk carries FIRST|MORE
        assertEquals(BleBridgeProtocol.FLAG_FIRST or BleBridgeProtocol.FLAG_MORE, frames[0][5].toInt() and 0xFF)
        // Middle chunks carry MORE only
        for (i in 1 until frames.size - 2) {
            assertEquals(BleBridgeProtocol.FLAG_MORE, frames[i][5].toInt() and 0xFF)
        }
        // Last chunk carries flags=0
        assertEquals(0, frames.last()[5].toInt() and 0xFF)
        // All chunks combined still preserve the exact payload
        assertArrayEquals(payload, frames.flatMap { it.drop(BleBridgeProtocol.HEADER_LENGTH) }.toByteArray())
        // Every frame header begins with magic constant 0x47 0x42 0x01
        frames.forEach { frame ->
            assertEquals(0x47, frame[0].toInt() and 0xFF)
            assertEquals(0x42, frame[1].toInt() and 0xFF)
            assertEquals(0x01, frame[2].toInt() and 0xFF)
            assertEquals(1, frame[3].toInt() and 0xFF)        // TYPE_COMMAND
            assertEquals(0, frame[4].toInt() and 0xFF)         // status=0
        }
    }

    @Test
    fun `payload exactly at chunk size emits single frame with flags zero`() {
        val chunkSize = BleBridgeProtocol.DEFAULT_MTU - 3 - BleBridgeProtocol.HEADER_LENGTH
        val payload = ByteArray(chunkSize) { 0x55 }
        val frames = BleBridgeProtocol.commandFrames(1, payload, BleBridgeProtocol.DEFAULT_MTU)
        assertEquals(1, frames.size)
        assertEquals(0, frames.single()[5].toInt() and 0xFF)
        assertArrayEquals(payload, frames.single().copyOfRange(BleBridgeProtocol.HEADER_LENGTH, frames.single().size))
    }

    @Test
    fun `payload one byte above chunk boundary starts fragmentation`() {
        val chunkSize = BleBridgeProtocol.DEFAULT_MTU - 3 - BleBridgeProtocol.HEADER_LENGTH
        val payload = ByteArray(chunkSize + 1) { 0xAA.toByte() }
        val frames = BleBridgeProtocol.commandFrames(1, payload, BleBridgeProtocol.DEFAULT_MTU)
        assertEquals(2, frames.size)
        assertEquals(BleBridgeProtocol.FLAG_FIRST or BleBridgeProtocol.FLAG_MORE, frames[0][5].toInt() and 0xFF)
        assertEquals(0, frames[1][5].toInt() and 0xFF)
    }

    @Test
    fun `oversized payload above MAX_COMMAND_BYTES is rejected`() {
        val oversized = ByteArray(BleBridgeProtocol.MAX_COMMAND_BYTES + 1) { 0x00 }
        assertThrows(IllegalArgumentException::class.java) {
            BleBridgeProtocol.commandFrames(1, oversized, 23)
        }
    }

    @Test
    fun `empty payload is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BleBridgeProtocol.commandFrames(1, byteArrayOf(), 23)
        }
    }

    @Test
    fun `maximum payload fits in one frame`() {
        val max = ByteArray(BleBridgeProtocol.MAX_COMMAND_BYTES) { 0x33 }
        val frames = BleBridgeProtocol.commandFrames(7, max, 512)
        assertEquals(1, frames.size)
        assertArrayEquals(max, frames.single().copyOfRange(BleBridgeProtocol.HEADER_LENGTH, frames.single().size))
    }

    @Test
    fun `decoder produces correct type status and commandId for single frame`() {
        val frame = BleBridgeProtocol.commandFrames(0x12345, "abc".toByteArray(), 256).single()
        val decoded = BleBridgeProtocol.Decoder().feed(frame)
        assertEquals(1, decoded.frames.size)
        val d = decoded.frames.single()
        assertEquals(1, d.type)             // TYPE_COMMAND
        assertEquals(0, d.status)
        assertEquals(0x12345, d.commandId)
        assertArrayEquals("abc".toByteArray(), d.payload)
        assertTrue(decoded.fallback.isEmpty())
    }

    @Test
    fun `decoder reassembles fragment stream as independent frames`() {
        val big = ByteArray(BleBridgeProtocol.MAX_COMMAND_BYTES) { it.toByte() }
        val frames = BleBridgeProtocol.commandFrames(0x77, big, BleBridgeProtocol.DEFAULT_MTU)
        val decoder = BleBridgeProtocol.Decoder()
        val decoded = frames.flatMap { decoder.feed(it).frames }
        assertEquals(0x77, decoded.single().commandId)
        assertArrayEquals(big, decoded.flatMap { it.payload.toList() }.toByteArray())
    }

    @Test
    fun `decoder feeds bytes one at a time across frame boundaries`() {
        val frame = BleBridgeProtocol.commandFrames(99, "Boundary".toByteArray(), 128).single()
        val decoder = BleBridgeProtocol.Decoder()
        val bytes = frame.toList().toByteArray()
        val decoded = bytes.flatMap { byte -> decoder.feed(byteArrayOf(byte)).frames }
        assertEquals(1, decoded.size)
        assertEquals(99, decoded.single().commandId)
        assertArrayEquals("Boundary".toByteArray(), decoded.single().payload)
    }

    @Test
    fun `decoder preserves residual split-magic bytes for next feed`() {
        val frame = BleBridgeProtocol.commandFrames(8, "X".toByteArray(), 128).single()
        val decoder = BleBridgeProtocol.Decoder()

        // Feed full frame, then start feeding another frame's magic byte-by-byte
        decoder.feed(frame)
        val partial = frame.copyOfRange(0, 3)                  // magic 0x47 0x42 0x01
        assertEquals(0, decoder.feed(partial).frames.size)

        val rest = BleBridgeProtocol.commandFrames(8, "Y".toByteArray(), 128).single()
        val rebuilt = partial + rest.copyOfRange(3, rest.size)
        val decoded = decoder.feed(rebuilt.copyOfRange(partial.size, rebuilt.size))
        assertEquals(1, decoded.frames.size)
        assertEquals(8, decoded.frames.single().commandId)
        assertArrayEquals("Y".toByteArray(), decoded.frames.single().payload)
    }

    @Test
    fun `decoder returns fallback for non-frame bytes`() {
        val decoder = BleBridgeProtocol.Decoder()
        val result = decoder.feed(">>> prompt".toByteArray())
        assertArrayEquals(">>> prompt".toByteArray(), result.fallback)
        assertEquals(0, result.frames.size)
    }

    @Test
    fun `decoder meets only partial header returns no frames and buffers bytes`() {
        val frame = BleBridgeProtocol.commandFrames(13, "partial".toByteArray(), 64).single()
        val decoder = BleBridgeProtocol.Decoder()
        // Feed incomplete header (less than the 3-byte magic + at least one byte of length)
        assertEquals(0, decoder.feed(frame.copyOfRange(0, 4)).frames.size)
        assertEquals(0, decoder.feed(frame.copyOfRange(4, 6)).frames.size)
        val final = decoder.feed(frame.copyOfRange(6, frame.size))
        assertEquals(1, final.frames.size)
        assertArrayEquals("partial".toByteArray(), final.frames.single().payload)
    }

    @Test
    fun `ERR-style bridge status text is treated as ERROR by GhostSerialResponse`() {
        val response = GhostSerialResponse("ERROR: BLE bridge status=3: invalid command")
        assertEquals(GhostSerialResponse.ResponseType.ERROR, response.type)

        val detail = GhostResponse.Error.parse(response.raw)
        assertNotNull("error parser should consume bridge error relay", detail)
        assertEquals("BLE bridge status=3: invalid command", detail!!.message)
    }

    @Test
    fun `decoding ERR-typed frame preserves command id payload and nonzero status`() {
        val errorFrame = errorBridgeFrame(commandId = 0x99, status = 4, payload = "denied".toByteArray())
        val decoder = BleBridgeProtocol.Decoder()
        val result = decoder.feed(errorFrame)
        assertEquals(1, result.frames.size)
        // FLAG_FIRST remains 0x01 on the encoder's compact representation
        assertEquals(BleBridgeProtocol.FLAG_FIRST, 1)
        val d = result.frames.single()
        assertEquals(5, d.type)                                // type==ERR in production code
        assertEquals(4, d.status)
        assertEquals(0x99, d.commandId)
        assertArrayEquals("denied".toByteArray(), d.payload)
    }

    @Test
    fun `END-typed frame decodes with type 4 and same commandId`() {
        val endFrame = endBridgeFrame(commandId = 0x55)
        val decoder = BleBridgeProtocol.Decoder()
        val result = decoder.feed(endFrame)
        assertEquals(1, result.frames.size)
        val d = result.frames.single()
        assertEquals(4, d.type)                                 // END type
        assertEquals(0x55, d.commandId)
        assertTrue(d.payload.isEmpty())
    }

    @Test
    fun `decoder tracks bytes correctly when fed a frame interleaved with noise`() {
        val frame = BleBridgeProtocol.commandFrames(7, "ABC".toByteArray(), 64).single()
        val decoder = BleBridgeProtocol.Decoder()

        // Noise prefix, partial frame, more noise, frame completion
        val first = decoder.feed("--->".toByteArray())
        assertArrayEquals("--->".toByteArray(), first.fallback)

        val second = decoder.feed(frame.copyOfRange(0, 7))
        assertEquals(0, second.frames.size)

        val third = decoder.feed(frame.copyOfRange(7, frame.size))
        assertEquals(1, third.frames.size)
        assertEquals(7, third.frames.single().commandId)
        assertArrayEquals("ABC".toByteArray(), third.frames.single().payload)
    }

    @Test
    fun `legacy decoder resets cleanly`() {
        val decoder = BleBridgeProtocol.Decoder()
        decoder.feed("noise".toByteArray())
        decoder.reset()
        val frame = BleBridgeProtocol.commandFrames(2, "OK".toByteArray(), 64).single()
        val result = decoder.feed(frame)
        assertEquals(1, result.frames.size)
        assertEquals(2, result.frames.single().commandId)
        assertArrayEquals("OK".toByteArray(), result.frames.single().payload)
    }

    @Test
    fun `decoder frames are produced when frameMagixStraddle across feeds`() {
        // Refers to the "split-magic" handling in Decoder.feed; the magic 0x47 0x42 0x01
        // may straddle notifications. The cursor keeps the last 1-2 bytes so subsequent
        // feed() completes the magic and decodes the frame.
        val frame = BleBridgeProtocol.commandFrames(17, "straddle".toByteArray(), 64).single()
        val decoder = BleBridgeProtocol.Decoder()
        // Feed every byte except the very first magic byte 0x47
        val body = frame.copyOfRange(1, frame.size)
        // Feed from 0x47 alone (single byte incomplete)
        assertEquals(0, decoder.feed(byteArrayOf(0x47)).frames.size)
        // Feed the rest
        val result = decoder.feed(body)
        assertEquals(1, result.frames.size)
        assertEquals(17, result.frames.single().commandId)
        assertArrayEquals("straddle".toByteArray(), result.frames.single().payload)
    }

    @Test
    fun `pure random payload bytes parsed by decoder produce no frames if no magic`() {
        val decoder = BleBridgeProtocol.Decoder()
        val noise = ByteArray(50) { (it * 7 % 256).toByte() }
        val result = decoder.feed(noise)
        assertEquals(0, result.frames.size)
        // The output length should be roughly the noise with possibly the trailing 0x47 byte withheld
        assertTrue("fallback should contain most bytes: ${result.fallback.size}", result.fallback.size >= 49)
    }

    private fun rawBridgeFrame(type: Int, status: Int, commandId: Int, payload: ByteArray): ByteArray {
        val header = ByteArray(BleBridgeProtocol.HEADER_LENGTH)
        header[0] = 0x47
        header[1] = 0x42
        header[2] = 0x01
        header[3] = type.toByte()
        header[4] = status.toByte()
        header[5] = 0
        header[6] = (commandId and 0xFF).toByte()
        header[7] = ((commandId ushr 8) and 0xFF).toByte()
        header[8] = ((commandId ushr 16) and 0xFF).toByte()
        header[9] = ((commandId ushr 24) and 0xFF).toByte()
        header[10] = (payload.size and 0xFF).toByte()
        header[11] = ((payload.size ushr 8) and 0xFF).toByte()
        return header + payload
    }

    private fun errorBridgeFrame(commandId: Int, status: Int, payload: ByteArray): ByteArray =
        rawBridgeFrame(type = 5, status = status, commandId = commandId, payload = payload)

    private fun endBridgeFrame(commandId: Int): ByteArray =
        rawBridgeFrame(type = 4, status = 0, commandId = commandId, payload = ByteArray(0))
}
