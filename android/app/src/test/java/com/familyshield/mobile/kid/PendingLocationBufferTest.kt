package com.familyshield.mobile.kid

import com.familyshield.mobile.net.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingLocationBufferTest {
    @Test
    fun `locations remain pending until acknowledged`() {
        val buffer = PendingLocationBuffer()
        val first = point("2026-07-28T08:00:00Z")
        val second = point("2026-07-28T08:01:00Z")
        buffer.add(first)
        buffer.add(second)

        val attempted = buffer.batch(null)
        assertEquals(listOf(first, second), attempted)
        assertEquals(attempted, buffer.batch(null))

        buffer.acknowledge(attempted)
        assertEquals(0, buffer.size())
    }

    @Test
    fun `samples added during an upload survive its acknowledgement`() {
        val buffer = PendingLocationBuffer()
        val first = point("2026-07-28T08:00:00Z")
        val arrivedDuringUpload = point("2026-07-28T08:01:00Z")
        buffer.add(first)

        val attempted = buffer.batch(null)
        buffer.add(arrivedDuringUpload)
        buffer.acknowledge(attempted)

        assertEquals(listOf(arrivedDuringUpload), buffer.batch(null))
    }

    @Test
    fun `current location is included while backlog remains bounded`() {
        val buffer = PendingLocationBuffer(maxBatchSize = 3)
        buffer.add(point("2026-07-28T08:00:00Z"))
        buffer.add(point("2026-07-28T08:01:00Z"))
        buffer.add(point("2026-07-28T08:02:00Z"))
        val current = point("2026-07-28T08:03:00Z")

        val batch = buffer.batch(current)

        assertEquals(3, batch.size)
        assertEquals(current, batch.last())
        assertEquals(3, buffer.size())
    }

    @Test
    fun `pending storage drops oldest samples and can be cleared on source switch`() {
        val buffer = PendingLocationBuffer(maxBatchSize = 3, maxPendingSize = 3)
        buffer.add(point("2026-07-28T08:00:00Z"))
        buffer.add(point("2026-07-28T08:01:00Z"))
        buffer.add(point("2026-07-28T08:02:00Z"))
        buffer.add(point("2026-07-28T08:03:00Z"))

        assertEquals(
            listOf("2026-07-28T08:01:00Z", "2026-07-28T08:02:00Z", "2026-07-28T08:03:00Z"),
            buffer.batch(null).map { it.recordedAt },
        )
        buffer.clear()
        assertEquals(0, buffer.size())
    }

    private fun point(at: String) = LocationPoint(
        lat = 50.0,
        lng = 6.0,
        recordedAt = at,
        batteryLevel = null,
    )
}
