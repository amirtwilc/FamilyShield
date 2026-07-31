package com.familyshield.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingClipboardTest {
    @Test
    fun `clipboard accepts only an exact six digit code`() {
        assertEquals("123456", pairingCodeFromClipboard("123456"))
        assertEquals("123456", pairingCodeFromClipboard(" 123456\n"))
        assertNull(pairingCodeFromClipboard("code: 123456"))
        assertNull(pairingCodeFromClipboard("12345"))
        assertNull(pairingCodeFromClipboard("1234567"))
        assertNull(pairingCodeFromClipboard(null))
    }

    @Test
    fun `code edits reject non digits and excess characters`() {
        assertEquals("", pairingCodeEditOrNull(""))
        assertEquals("123", pairingCodeEditOrNull("123"))
        assertEquals("123456", pairingCodeEditOrNull("123456"))
        assertNull(pairingCodeEditOrNull("12a3"))
        assertNull(pairingCodeEditOrNull("1234567"))
    }
}
