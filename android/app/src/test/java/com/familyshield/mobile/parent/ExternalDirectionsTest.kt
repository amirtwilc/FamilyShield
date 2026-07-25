package com.familyshield.mobile.parent

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalDirectionsTest {
    private val destination = DirectionDestination(32.1234567, 34.9876543, "Mia Location")

    @Test
    fun `google maps navigation uri targets the destination coordinates`() {
        assertEquals("com.google.android.apps.maps", GOOGLE_MAPS_PACKAGE)
        assertEquals("google.navigation:q=32.123457,34.987654", googleMapsNavigationUri(destination))
    }

    @Test
    fun `waze navigation uri targets the destination coordinates`() {
        assertEquals("com.waze", WAZE_PACKAGE)
        assertEquals("waze://?ll=32.123457,34.987654&navigate=yes", wazeNavigationUri(destination))
    }

    @Test
    fun `generic map uri includes encoded label`() {
        assertEquals("geo:0,0?q=32.123457,34.987654(Mia%20Location)", genericMapUri(destination))
    }
}
