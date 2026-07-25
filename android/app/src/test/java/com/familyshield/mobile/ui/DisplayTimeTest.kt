package com.familyshield.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class DisplayTimeTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `formats iso instant timestamps`() {
        assertEquals("18:56", formatMessageTime("2026-07-24T18:56:28.832291Z", utc))
    }

    @Test
    fun `formats postgres timestamps with short utc offset`() {
        assertEquals("18:56", formatMessageTime("2026-07-24 18:56:28.832291+00", utc))
    }

    @Test
    fun `converts offset timestamps to the device zone`() {
        assertEquals("18:56", formatMessageTime("2026-07-24T21:56:28+03:00", utc))
    }

    @Test
    fun `formats local datetime timestamps`() {
        assertEquals("18:56", formatMessageTime("2026-07-24T18:56:28", utc))
    }

    @Test
    fun `falls back to embedded clock time`() {
        assertEquals("18:56", formatMessageTime("sent at 18:56:28.832291+00", utc))
    }

    @Test
    fun `extracts local date from offset timestamps`() {
        assertEquals(LocalDate.of(2026, 7, 24), messageLocalDate("2026-07-24 18:56:28.832291+00", utc))
    }

    @Test
    fun `formats message dates`() {
        assertEquals("Jul 24, 2026", formatMessageDate("2026-07-24T18:56:28Z", utc, Locale.US))
    }
}
