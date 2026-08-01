package com.familyshield.mobile.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpApiClientTest {
    @Test
    fun `telemetry sends GPS speed and accuracy with a location sample`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ok":true,"locationInserted":1,"appUsageInserted":0}"""),
        )
        server.start()
        try {
            val client = HttpApiClient(baseUrl = server.url("/").toString().trimEnd('/'))
            val point = LocationPoint(
                32.1,
                34.8,
                "2026-07-26T09:00:00Z",
                82,
                speed = 6.5,
                accuracy = 8.0,
            )

            client.sendTelemetry("device-token", DeviceTelemetryBody(location = point))

            val body = server.takeRequest().body.readUtf8()
            assertEquals(true, body.contains("\"speed\":6.5"))
            assertEquals(true, body.contains("\"accuracy\":8.0"))
        } finally {
            server.shutdown()
        }
    }

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
