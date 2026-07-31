package com.familyshield.mobile.parent

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleSignInTest {
    @Test
    fun explicitGoogleButtonUsesAccountChooserOption() {
        val request = googleSignInRequest("web-client.apps.googleusercontent.com")

        assertEquals(1, request.credentialOptions.size)
        assertTrue(request.credentialOptions.single() is GetSignInWithGoogleOption)
    }

    @Test
    fun accountSelectionClearsActiveCredentialFirst() = runTest {
        val events = mutableListOf<String>()

        val selected = withFreshCredentialSelection(
            clearCredentialState = { events += "clear" },
            selectCredential = { events += "select"; "credential" },
        )

        assertEquals(listOf("clear", "select"), events)
        assertEquals("credential", selected)
    }

    @Test
    fun accountSelectionContinuesWhenStateCleanupFails() = runTest {
        val selected = withFreshCredentialSelection(
            clearCredentialState = { error("Provider cleanup failed") },
            selectCredential = { "credential" },
        )

        assertEquals("credential", selected)
    }
}
