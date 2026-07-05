package com.familyshield.app.net

import com.familyshield.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, message: String) : Exception(message)

/** The FamilyShield API surface the app depends on. Implemented by [HttpApiClient]
 *  for production and by a hand-written fake in tests. */
interface ApiClient {
    suspend fun register(email: String, password: String): Tokens
    suspend fun login(email: String, password: String): Tokens
    suspend fun googleLogin(idToken: String): Tokens
    suspend fun refreshTokens(refreshToken: String): Tokens
    suspend fun registerParentPushToken(token: String, fcmToken: String)
    suspend fun listChildren(token: String): List<Child>
    suspend fun createChild(token: String, name: String, avatar: String? = null): Child
    suspend fun updateChild(token: String, childId: String, name: String, avatar: String? = null): Child
    suspend fun deleteChild(token: String, childId: String)
    suspend fun pairingCode(token: String, childId: String): PairingCode
    suspend fun currentLocation(token: String, childId: String): CurrentLocation?
    suspend fun alerts(token: String, childId: String): List<Alert>
    suspend fun listZones(token: String, childId: String): List<Zone>
    suspend fun createZone(token: String, childId: String, name: String, lat: Double, lng: Double, radiusM: Int): Zone
    suspend fun deleteZone(token: String, childId: String, zoneId: String)
    suspend fun locationHistory(token: String, childId: String, date: String): List<HistoryPoint>
    suspend fun routes(token: String, childId: String): RoutesResponse
    suspend fun appUsage(token: String, childId: String): AppUsageSummary
    suspend fun messages(token: String, childId: String, after: String? = null, before: String? = null, markRead: Boolean = false): MessagesResponse
    suspend fun conversationsSummary(token: String): List<ConversationSummary>
    suspend fun sendMessage(token: String, childId: String, body: String): Message
    suspend fun pair(code: String, platform: String, model: String?): PairResult
    suspend fun addParent(token: String, code: String, platform: String, model: String?): MonitoringInfo
    suspend fun monitoring(token: String): MonitoringInfo
    suspend fun removeMonitor(token: String, parentId: String): MonitorUnpairResult
    suspend fun monitorMessages(token: String, parentId: String, after: String? = null): MessagesResponse
    suspend fun sendMonitorMessage(token: String, parentId: String, body: String): Message
    suspend fun sendLocation(token: String, lat: Double, lng: Double, battery: Int): InsertResult
    suspend fun sendStatus(token: String, battery: Int, charging: Boolean)
    suspend fun deviceMessages(token: String, after: String? = null): MessagesResponse
    suspend fun sendDeviceMessage(token: String, body: String): Message
}

/** Thin coroutine wrapper over the FamilyShield REST API (OkHttp + kotlinx.serialization). */
class HttpApiClient(private val baseUrl: String = BuildConfig.API_BASE_URL) : ApiClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Performs the request and returns the raw response body, throwing ApiException on non-2xx. */
    private suspend fun requestRaw(
        method: String,
        path: String,
        bodyJson: String? = null,
        token: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl + path)
        val reqBody = bodyJson?.toRequestBody(jsonMedia)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(reqBody ?: ByteArray(0).toRequestBody(jsonMedia))
            "PATCH" -> builder.patch(reqBody ?: ByteArray(0).toRequestBody(jsonMedia))
            "DELETE" -> builder.delete(reqBody)
            else -> error("unsupported method $method")
        }
        token?.let { builder.header("Authorization", "Bearer $it") }

        http.newCall(builder.build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching {
                    (json.parseToJsonElement(text) as? JsonObject)
                        ?.get("error")?.let { it as? JsonObject }
                        ?.get("message")?.toString()?.trim('"')
                }.getOrNull() ?: "Request failed (${res.code})"
                throw ApiException(res.code, msg)
            }
            text
        }
    }

    // ---- Parent ----
    override suspend fun register(email: String, password: String): Tokens =
        json.decodeFromString(requestRaw("POST", "/api/auth/register",
            json.encodeToString(Credentials(email, password))))

    override suspend fun login(email: String, password: String): Tokens =
        json.decodeFromString(requestRaw("POST", "/api/auth/login",
            json.encodeToString(Credentials(email, password))))

    override suspend fun googleLogin(idToken: String): Tokens =
        json.decodeFromString(requestRaw("POST", "/api/auth/google",
            json.encodeToString(GoogleLoginBody(idToken))))

    override suspend fun refreshTokens(refreshToken: String): Tokens =
        json.decodeFromString(requestRaw("POST", "/api/auth/refresh",
            json.encodeToString(RefreshBody(refreshToken))))

    override suspend fun registerParentPushToken(token: String, fcmToken: String) {
        requestRaw("POST", "/api/parent/push-token", json.encodeToString(PushTokenBody(fcmToken)), token)
    }

    override suspend fun listChildren(token: String): List<Child> =
        json.decodeFromString<ChildrenResponse>(requestRaw("GET", "/api/children", token = token)).children

    override suspend fun createChild(token: String, name: String, avatar: String?): Child =
        json.decodeFromString(requestRaw("POST", "/api/children",
            json.encodeToString(CreateChildBody(name, avatar)), token))

    override suspend fun updateChild(token: String, childId: String, name: String, avatar: String?): Child =
        json.decodeFromString(requestRaw("PATCH", "/api/children/$childId",
            json.encodeToString(CreateChildBody(name, avatar)), token))

    override suspend fun deleteChild(token: String, childId: String) {
        requestRaw("DELETE", "/api/children/$childId", token = token)
    }

    override suspend fun pairingCode(token: String, childId: String): PairingCode =
        json.decodeFromString(requestRaw("POST", "/api/children/$childId/pairing-code", token = token))

    override suspend fun currentLocation(token: String, childId: String): CurrentLocation? =
        json.decodeFromString(requestRaw("GET", "/api/children/$childId/location/current", token = token))

    override suspend fun alerts(token: String, childId: String): List<Alert> =
        json.decodeFromString<AlertsResponse>(
            requestRaw("GET", "/api/children/$childId/alerts", token = token)).alerts

    override suspend fun listZones(token: String, childId: String): List<Zone> =
        json.decodeFromString<ZonesResponse>(
            requestRaw("GET", "/api/children/$childId/zones", token = token)).zones

    override suspend fun createZone(token: String, childId: String, name: String, lat: Double, lng: Double, radiusM: Int): Zone =
        json.decodeFromString(requestRaw("POST", "/api/children/$childId/zones",
            json.encodeToString(CreateZoneBody(name, lat, lng, radiusM)), token))

    override suspend fun deleteZone(token: String, childId: String, zoneId: String) {
        requestRaw("DELETE", "/api/children/$childId/zones/$zoneId", token = token)
    }

    override suspend fun locationHistory(token: String, childId: String, date: String): List<HistoryPoint> =
        json.decodeFromString<HistoryResponse>(
            requestRaw("GET", "/api/children/$childId/location/history?date=$date", token = token)).points

    override suspend fun routes(token: String, childId: String): RoutesResponse =
        json.decodeFromString(requestRaw("GET", "/api/children/$childId/routes", token = token))

    override suspend fun appUsage(token: String, childId: String): AppUsageSummary =
        json.decodeFromString(requestRaw("GET", "/api/children/$childId/app-usage", token = token))

    override suspend fun messages(token: String, childId: String, after: String?, before: String?, markRead: Boolean): MessagesResponse {
        val q = buildList {
            if (after != null) add("after=" + java.net.URLEncoder.encode(after, "UTF-8"))
            if (before != null) add("before=" + java.net.URLEncoder.encode(before, "UTF-8"))
            if (markRead) add("markRead=1")
        }.joinToString("&")
        val path = "/api/children/$childId/messages" + if (q.isEmpty()) "" else "?$q"
        return json.decodeFromString(requestRaw("GET", path, token = token))
    }

    override suspend fun conversationsSummary(token: String): List<ConversationSummary> =
        json.decodeFromString<ConversationsResponse>(requestRaw("GET", "/api/messages/summary", token = token)).conversations

    override suspend fun sendMessage(token: String, childId: String, body: String): Message =
        json.decodeFromString(requestRaw("POST", "/api/children/$childId/messages",
            json.encodeToString(SendMessageBody(body)), token))

    // ---- Kid device ----
    override suspend fun pair(code: String, platform: String, model: String?): PairResult =
        json.decodeFromString(requestRaw("POST", "/api/pair",
            json.encodeToString(PairBody(code, platform, model))))

    override suspend fun addParent(token: String, code: String, platform: String, model: String?): MonitoringInfo =
        json.decodeFromString(requestRaw("POST", "/api/pair",
            json.encodeToString(PairBody(code, platform, model)), token))

    override suspend fun monitoring(token: String): MonitoringInfo =
        json.decodeFromString(requestRaw("GET", "/api/device/monitoring", token = token))

    override suspend fun removeMonitor(token: String, parentId: String): MonitorUnpairResult =
        json.decodeFromString(requestRaw("DELETE", "/api/device/monitors/$parentId", token = token))

    override suspend fun monitorMessages(token: String, parentId: String, after: String?): MessagesResponse {
        val path = "/api/device/monitors/$parentId/messages" +
            if (after != null) "?after=" + java.net.URLEncoder.encode(after, "UTF-8") else ""
        return json.decodeFromString(requestRaw("GET", path, token = token))
    }

    override suspend fun sendMonitorMessage(token: String, parentId: String, body: String): Message =
        json.decodeFromString(requestRaw("POST", "/api/device/monitors/$parentId/messages",
            json.encodeToString(SendMessageBody(body)), token))

    override suspend fun sendLocation(token: String, lat: Double, lng: Double, battery: Int): InsertResult {
        val batch = LocationBatch(listOf(LocationPoint(lat, lng, nowIso(), battery)))
        return json.decodeFromString(requestRaw("POST", "/api/locations",
            json.encodeToString(batch), token))
    }

    override suspend fun sendStatus(token: String, battery: Int, charging: Boolean) {
        requestRaw("POST", "/api/device/status", json.encodeToString(StatusBody(battery, charging)), token)
    }

    override suspend fun deviceMessages(token: String, after: String?): MessagesResponse {
        val path = "/api/device/messages" + if (after != null) "?after=" + java.net.URLEncoder.encode(after, "UTF-8") else ""
        return json.decodeFromString(requestRaw("GET", path, token = token))
    }

    override suspend fun sendDeviceMessage(token: String, body: String): Message =
        json.decodeFromString(requestRaw("POST", "/api/device/messages",
            json.encodeToString(SendMessageBody(body)), token))

    private fun nowIso(): String {
        val df = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        df.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return df.format(java.util.Date())
    }
}
