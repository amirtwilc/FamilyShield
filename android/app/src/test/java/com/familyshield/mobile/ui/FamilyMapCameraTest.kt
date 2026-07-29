package com.familyshield.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyMapCameraTest {
    @Test
    fun `caps an excessively close family map zoom`() {
        assertEquals(15.0, cappedFamilyMapZoom(22.0), 0.0)
    }

    @Test
    fun `preserves a wider family map zoom`() {
        assertEquals(11.5, cappedFamilyMapZoom(11.5), 0.0)
    }

    @Test
    fun `recognizes multiple children at the exact same location`() {
        val locations = listOf(
            32.0853 to 34.7818,
            32.0853 to 34.7818,
        )

        assertEquals(true, familyMapLocationsCoincide(locations))
    }

    @Test
    fun `does not treat nearby distinct locations as coincident`() {
        val locations = listOf(
            32.0853 to 34.7818,
            32.0854 to 34.7818,
        )

        assertEquals(false, familyMapLocationsCoincide(locations))
    }
}
