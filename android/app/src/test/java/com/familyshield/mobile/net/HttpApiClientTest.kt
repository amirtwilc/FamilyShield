package com.familyshield.mobile.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpApiClientTest {
    @Test
    fun `invalid App Check response force refreshes attestation and retries once`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"invalid_app_check","message":"App attestation failed"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"parentId":"parent-1"}"""),
        )
        server.start()
        try {
            val refreshRequests = mutableListOf<Boolean>()
            val client = HttpApiClient(
                baseUrl = server.url("/").toString().trimEnd('/'),
                appCheckTokenProvider = { forceRefresh ->
                    refreshRequests += forceRefresh
                    if (forceRefresh) "fresh-app-check" else "cached-app-check"
                },
            )

            assertEquals("parent-1", client.bootstrapParent("firebase-id-token").parentId)
            assertEquals(listOf(false, true), refreshRequests)
            assertEquals(
                "cached-app-check",
                server.takeRequest().getHeader("X-Firebase-AppCheck"),
            )
            assertEquals(
                "fresh-app-check",
                server.takeRequest().getHeader("X-Firebase-AppCheck"),
            )
        } finally {
            server.shutdown()
        }
    }
}
