package com.example.ghostespcompanion.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression test for `Error.parse()` variants covering firmware error / benign lines. */
class GhostResponseErrorRegressionTest {

    @Test
    fun `Error colon prefix maps to detail`() {
        val cases = listOf(
            "ERROR: Capture Type cannot be empty." to "Capture Type cannot be empty.",
            "Error: Incorrect number of arguments." to "Incorrect number of arguments.",
            "error command not found" to "command not found",
            "error:    leading spaces" to "leading spaces"
        )
        cases.forEach { (input, expected) ->
            val parsed = GhostResponse.Error.parse(input)
            assertNotNull(input, parsed)
            assertEquals(input, expected, parsed!!.message)
        }
    }

    @Test
    fun `Failed prefix maps to error and ignores benign metric lines`() {
        val failures = listOf(
            Pair("Failed to start service discovery: 5", Pair(true, "to start service discovery: 5")),
            Pair("failed: no such channel", Pair(true, ": no such channel"))
        )
        failures.forEach { (input, _) ->
            assertNotNull(input, GhostResponse.Error.parse(input))
        }

        val benign = listOf(
            "Failed packets: 0",
            "failed connections: 0",
            "failed retries (avg): 0"
        )
        benign.forEach { line -> assertNull(line, GhostResponse.Error.parse(line)) }
    }

    @Test
    fun `Invalid prefix maps to error and ignores benign metric lines`() {
        val failures = listOf("Invalid channel 27. Must be between 11 and 26")
        failures.forEach { input ->
            assertNotNull(input, GhostResponse.Error.parse(input))
        }
        val benign = listOf(
            "Invalid frames: 0",
            "invalid checksums (avg): 0",
            "invalid responses: 0"
        )
        benign.forEach { line -> assertNull(line, GhostResponse.Error.parse(line)) }
    }

    @Test
    fun `Unknown firmware messages map to error but benign Unknown lines are ignored`() {
        val cases = listOf(
            "Unknown command: frobnicate",
            "unknown subcommand: foo",
            "Unknown option: --bad",
            "unknown argument: 42",
            "Unknown mode: hex",
            "unknown capture type: wibble"
        )
        cases.forEach { input ->
            assertNotNull(input, GhostResponse.Error.parse(input))
        }
        assertNull(GhostResponse.Error.parse("Unknown devices: 3"))
    }

    @Test
    fun `timeout variants map to error but status-line timeout is benign`() {
        val cases = listOf(
            "Timeout waiting for device",
            "timed out while connecting",
            "Time out: BLE bridge unavailable"
        )
        cases.forEach { input ->
            assertNotNull(input, GhostResponse.Error.parse(input))
        }
        assertNull(GhostResponse.Error.parse("Timeout: 30 seconds"))
        assertNull(GhostResponse.Error.parse("timeout: 500 ms"))
    }

    @Test
    fun `unsupported variants map to error but documentary mention is benign`() {
        val cases = listOf(
            "Unsupported capture mode",
            "BLE is not supported on this board",
            "not supported by this hardware"
        )
        cases.forEach { input ->
            assertNotNull(input, GhostResponse.Error.parse(input))
        }
        assertNull(GhostResponse.Error.parse("This help page lists unsupported forms for reference"))
    }

    @Test
    fun `benign firmware status lines are not classified as errors`() {
        val benign = listOf(
            "Service discovery complete. Found 5 services.",
            "Stop scan complete",
            "Beacon spam started",
            "BLE stack ready",
            "Starting BLE scan",
            "Stopping BLE scan",
            "GHOSTESP_OK",
            "",
            "single-line benign output"
        )
        benign.forEach { line -> assertNull(line, GhostResponse.Error.parse(line)) }
    }

    @Test
    fun `multiline string is rejected by error parser`() {
        assertNull(GhostResponse.Error.parse("ERROR: big\nerror"))
    }
}