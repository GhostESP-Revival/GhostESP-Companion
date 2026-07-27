package com.example.ghostespcompanion.data.serial

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleConnectAttemptTrackerTest {
    @Test
    fun staleAttemptCannotCompleteCurrentAttempt() {
        val tracker = BleConnectAttemptTracker()
        val stale = tracker.begin()
        val current = tracker.begin()

        assertFalse(tracker.complete(stale, BleAttemptResult(connected = true)))
        assertFalse(current.completion.isCompleted)
        assertTrue(tracker.complete(current, BleAttemptResult(connected = true)))
        assertTrue(runBlocking { current.completion.await() }.connected)
    }

    @Test
    fun clearedAttemptRejectsLateCompletion() {
        val tracker = BleConnectAttemptTracker()
        val attempt = tracker.begin()

        tracker.clear(attempt)

        assertFalse(tracker.isCurrent(attempt))
        assertFalse(tracker.complete(attempt, BleAttemptResult(connected = true)))
    }

    @Test
    fun retryIsBoundedAndOnlyUsedForTransientFailure() {
        val transient = BleAttemptResult(false, BleConnectionFailure.TIMEOUT, retryable = true)
        val permission = BleAttemptResult(false, BleConnectionFailure.PERMISSION_REQUIRED, retryable = false)

        assertTrue(shouldRetryBleConnection(transient, retryIndex = 0))
        assertFalse(shouldRetryBleConnection(transient, retryIndex = 1))
        assertFalse(shouldRetryBleConnection(permission, retryIndex = 0))
        assertFalse(shouldRetryBleConnection(BleAttemptResult(connected = true), retryIndex = 0))
    }
}
