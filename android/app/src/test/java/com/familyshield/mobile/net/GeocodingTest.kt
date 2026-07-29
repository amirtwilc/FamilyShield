package com.familyshield.mobile.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GeocodingTest {
    @Test
    fun `coordinate cache key absorbs small GPS jitter`() {
        assertEquals(
            coordinateKey(50.934953, 6.974577),
            coordinateKey(50.934954, 6.974578),
        )
    }

    @Test
    fun `coordinate cache key keeps meaningfully different points separate`() {
        assertNotEquals(
            coordinateKey(50.934953, 6.974577),
            coordinateKey(50.935953, 6.975577),
        )
    }
}
