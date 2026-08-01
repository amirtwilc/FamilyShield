package com.familyshield.mobile.kid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryUploadPolicyTest {
    @Test
    fun `GPS acquisition retries quickly until a current fix is available`() {
        assertEquals(GPS_ACQUISITION_RETRY_INTERVAL_MS, nextMonitorDelayMs(false, false))
        assertEquals(TELEMETRY_UPLOAD_INTERVAL_MS, nextMonitorDelayMs(false, true))
        assertEquals(SIMULATION_INTERVAL_MS, nextMonitorDelayMs(true, false))
    }

    @Test
    fun `optional telemetry fields are sent immediately and then on their own intervals`() {
        val policy = TelemetryUploadPolicy(
            appUsageIntervalMs = 30 * 60 * 1000L,
            fcmTokenRefreshIntervalMs = 6 * 60 * 60 * 1000L,
        )

        val first = policy.optionalFields(nowMs = 0L)
        assertTrue(first.appUsage)
        assertTrue(first.fcmToken)
        policy.markUploaded(first, nowMs = 0L)

        val fiveMinutes = policy.optionalFields(nowMs = 5 * 60 * 1000L)
        assertFalse(fiveMinutes.appUsage)
        assertFalse(fiveMinutes.fcmToken)

        val thirtyMinutes = policy.optionalFields(nowMs = 30 * 60 * 1000L)
        assertTrue(thirtyMinutes.appUsage)
        assertFalse(thirtyMinutes.fcmToken)
        policy.markUploaded(thirtyMinutes, nowMs = 30 * 60 * 1000L)

        val sixHours = policy.optionalFields(nowMs = 6 * 60 * 60 * 1000L)
        assertTrue(sixHours.appUsage)
        assertTrue(sixHours.fcmToken)
    }

    @Test
    fun `failed optional uploads remain due until a successful upload marks them sent`() {
        val policy = TelemetryUploadPolicy(appUsageIntervalMs = 30_000L, fcmTokenRefreshIntervalMs = 60_000L)

        assertTrue(policy.optionalFields(nowMs = 0L).appUsage)

        val retry = policy.optionalFields(nowMs = 5_000L)
        assertTrue(retry.appUsage)
        assertTrue(retry.fcmToken)
    }
}
