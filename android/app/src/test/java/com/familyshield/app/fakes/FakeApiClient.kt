package com.familyshield.app.fakes

import com.familyshield.app.net.*

/**
 * In-memory fake of [ApiClient] that mirrors the real FamilyShield backend's behaviour:
 * account creation/login, child management, pairing, location ingestion (kid GPS),
 * location read-back (parent), device status, and low-battery alerting. Lets the
 * ViewModels be unit-tested on the JVM without a network or a real server.
 */
class FakeApiClient(private val lowBatteryThreshold: Int = 15) : ApiClient {

    private val parents = mutableMapOf<String, String>()        // email -> password
    private val childName = linkedMapOf<String, String>()        // childId -> name
    private val childParents = mutableMapOf<String, MutableMap<String, String>>() // childId -> parent email -> display name
    private val deviceByChild = mutableMapOf<String, Device>()   // childId -> its device
    private val locations = mutableMapOf<String, CurrentLocation>()
    private val alertsByChild = mutableMapOf<String, MutableList<Alert>>()
    private val codeToChild = mutableMapOf<String, String>()     // pairing code -> childId
    private val deviceTokenToChild = mutableMapOf<String, String>()
    private var seq = 0
    private val now get() = "2026-06-20T00:00:0${seq % 10}Z"

    private fun parentEmail(token: String) = token.removePrefix("acc:")

    // ---- Parent ----
    override suspend fun register(email: String, password: String): Tokens {
        if (parents.containsKey(email)) throw ApiException(409, "Email already registered")
        parents[email] = password
        return Tokens("acc:$email", "ref:$email")
    }

    override suspend fun login(email: String, password: String): Tokens {
        if (parents[email] != password) throw ApiException(401, "Invalid email or password")
        return Tokens("acc:$email", "ref:$email")
    }

    override suspend fun googleLogin(idToken: String): Tokens =
        Tokens("acc:google:$idToken", "ref:google:$idToken")

    override suspend fun refreshTokens(refreshToken: String): Tokens {
        if (!refreshToken.startsWith("ref:")) throw ApiException(401, "Invalid refresh token")
        val email = refreshToken.removePrefix("ref:")
        return Tokens("acc:$email", "ref:$email")
    }

    /** When set, the next listChildren call rejects with 401 (simulates an expired access token). */
    var expireNextCall = false

    override suspend fun listChildren(token: String): List<Child> {
        if (expireNextCall) { expireNextCall = false; throw ApiException(401, "Invalid token") }
        val parent = parentEmail(token)
        return childName.mapNotNull { (id, fallbackName) ->
            val name = childParents[id]?.get(parent) ?: return@mapNotNull null
            Child(id, name, deviceByChild[id]?.let { listOf(it) } ?: emptyList())
        }
    }

    override suspend fun createChild(token: String, name: String): Child {
        val id = "child-${++seq}"
        childName[id] = name
        childParents.getOrPut(id) { linkedMapOf() }[parentEmail(token)] = name
        return Child(id, name, emptyList())
    }

    override suspend fun renameChild(token: String, childId: String, name: String): Child {
        childParents.getOrPut(childId) { linkedMapOf() }[parentEmail(token)] = name
        childName[childId] = name
        return Child(childId, name, deviceByChild[childId]?.let { listOf(it) } ?: emptyList())
    }

    override suspend fun pairingCode(token: String, childId: String): PairingCode {
        val code = (100000 + (++seq)).toString().takeLast(6)
        codeToChild[code] = childId
        return PairingCode(code, now)
    }

    override suspend fun currentLocation(token: String, childId: String): CurrentLocation? =
        locations[childId]

    override suspend fun alerts(token: String, childId: String): List<Alert> =
        alertsByChild[childId]?.toList() ?: emptyList()

    private val zonesByChild = mutableMapOf<String, MutableList<Zone>>()

    override suspend fun listZones(token: String, childId: String): List<Zone> =
        zonesByChild[childId]?.toList() ?: emptyList()

    override suspend fun createZone(token: String, childId: String, name: String, lat: Double, lng: Double, radiusM: Int): Zone {
        val z = Zone("zone-${++seq}", name, lat, lng, radiusM)
        zonesByChild.getOrPut(childId) { mutableListOf() }.add(z)
        return z
    }

    override suspend fun deleteZone(token: String, childId: String, zoneId: String) {
        zonesByChild[childId]?.removeAll { it.id == zoneId }
    }

    override suspend fun locationHistory(token: String, childId: String, date: String): List<HistoryPoint> =
        locations[childId]?.let { listOf(HistoryPoint(it.lat, it.lng, it.recordedAt)) } ?: emptyList()

    /** Test-controllable routes payload. */
    var routesResult = RoutesResponse()
    override suspend fun routes(token: String, childId: String): RoutesResponse = routesResult

    var appUsageResult = AppUsageSummary()
    override suspend fun appUsage(token: String, childId: String): AppUsageSummary = appUsageResult

    // ---- Chat ----
    private val messagesByChild = mutableMapOf<String, MutableList<Message>>()
    private val monitorMessagesByChild = mutableMapOf<String, MutableMap<String, MutableList<Message>>>()

    private fun markReadFor(list: MutableList<Message>?, sender: String) {
        list?.let {
            for (i in it.indices) {
                val m = it[i]
                if (m.sender == sender && m.readAt == null) it[i] = m.copy(readAt = now)
            }
        }
    }

    /** Mirrors the server's after/before paging (cursor token = a message's createdAt). */
    private fun pageFake(all: List<Message>, after: String?, before: String?): MessagesResponse {
        if (after != null) return MessagesResponse(all.filter { it.createdAt > after }, null)
        val pageSize = 50
        val older = if (before != null) all.filter { it.createdAt < before } else all
        val page = older.takeLast(pageSize)
        val next = if (older.size > pageSize) page.first().createdAt else null
        return MessagesResponse(page, next)
    }

    override suspend fun messages(token: String, childId: String, after: String?, before: String?, markRead: Boolean): MessagesResponse {
        val parent = parentEmail(token)
        val list = monitorMessagesByChild[childId]?.get(parent)
        if (markRead) markReadFor(list, "child")
        return pageFake(list?.toList() ?: emptyList(), after, before)
    }

    override suspend fun conversationsSummary(token: String): List<ConversationSummary> =
        childParents.flatMap { (cid, parents) ->
            parents.keys.mapNotNull { parent ->
                if (parent != parentEmail(token)) return@mapNotNull null
                val list = monitorMessagesByChild[cid]?.get(parent).orEmpty()
                ConversationSummary(cid, unread = list.count { it.sender == "child" && it.readAt == null }, last = list.lastOrNull())
            }
        }

    override suspend fun sendMessage(token: String, childId: String, body: String): Message {
        val parent = parentEmail(token)
        val m = Message("msg-${++seq}", "parent", body, now)
        monitorMessagesByChild.getOrPut(childId) { mutableMapOf() }.getOrPut(parent) { mutableListOf() }.add(m)
        messagesByChild.getOrPut(childId) { mutableListOf() }.add(m)
        return m
    }

    override suspend fun deviceMessages(token: String, after: String?): MessagesResponse {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val parent = childParents[childId]?.keys?.firstOrNull() ?: return MessagesResponse()
        val list = monitorMessagesByChild[childId]?.get(parent)
        markReadFor(list, "parent")
        return pageFake(list?.toList() ?: emptyList(), after, null)
    }

    override suspend fun sendDeviceMessage(token: String, body: String): Message {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val parent = childParents[childId]?.keys?.firstOrNull() ?: throw ApiException(404, "Monitor not found")
        val m = Message("msg-${++seq}", "child", body, now)
        monitorMessagesByChild.getOrPut(childId) { mutableMapOf() }.getOrPut(parent) { mutableListOf() }.add(m)
        messagesByChild.getOrPut(childId) { mutableListOf() }.add(m)
        return m
    }

    override suspend fun monitorMessages(token: String, parentId: String, after: String?): MessagesResponse {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val parent = parentId.removePrefix("parent:")
        val list = monitorMessagesByChild[childId]?.get(parent)
        markReadFor(list, "parent")
        return pageFake(list?.toList() ?: emptyList(), after, null)
    }

    override suspend fun sendMonitorMessage(token: String, parentId: String, body: String): Message {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val parent = parentId.removePrefix("parent:")
        if (childParents[childId]?.containsKey(parent) != true) throw ApiException(404, "Monitor not found")
        val m = Message("msg-${++seq}", "child", body, now)
        monitorMessagesByChild.getOrPut(childId) { mutableMapOf() }.getOrPut(parent) { mutableListOf() }.add(m)
        messagesByChild.getOrPut(childId) { mutableListOf() }.add(m)
        return m
    }

    // ---- Kid device ----
    override suspend fun pair(code: String, platform: String, model: String?): PairResult {
        val childId = codeToChild.remove(code) ?: throw ApiException(400, "Code is invalid, expired, or already used")
        val deviceToken = "dev-${++seq}"
        deviceTokenToChild[deviceToken] = childId
        deviceByChild[childId] = Device(
            id = "device-$seq", platform = platform, model = model,
            batteryLevel = null, isCharging = null, lastSeenAt = now,
        )
        return PairResult(deviceToken, childId)
    }

    override suspend fun addParent(token: String, code: String, platform: String, model: String?): MonitoringInfo {
        val currentChildId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val sourceChildId = codeToChild.remove(code) ?: throw ApiException(400, "Code is invalid, expired, or already used")
        if (deviceByChild[sourceChildId] != null) throw ApiException(400, "This code belongs to a child that already has a paired device")
        val sourceLinks = childParents[sourceChildId].orEmpty()
        val parent = sourceLinks.keys.firstOrNull() ?: throw ApiException(400, "Code is invalid, expired, or already used")
        if (childParents[currentChildId]?.containsKey(parent) == true) {
            throw ApiException(400, "This parent already monitors this child")
        }
        childParents.getOrPut(currentChildId) { linkedMapOf() }[parent] = sourceLinks.getValue(parent)
        childParents.remove(sourceChildId)
        childName.remove(sourceChildId)
        return monitoring(token)
    }

    override suspend fun monitoring(token: String): MonitoringInfo {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val monitors = childParents[childId].orEmpty().map { (email, displayName) ->
            Monitor("parent:$email", email, displayName, "caregiver")
        }
        return MonitoringInfo(childId, monitors)
    }

    override suspend fun removeMonitor(token: String, parentId: String): MonitorUnpairResult {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val parent = parentId.removePrefix("parent:")
        val links = childParents[childId] ?: throw ApiException(404, "Monitor not found")
        if (links.remove(parent) == null) throw ApiException(404, "Monitor not found")
        monitorMessagesByChild[childId]?.remove(parent)
        if (links.isEmpty()) {
            deviceTokenToChild.remove(token)
            deviceByChild.remove(childId)
            return MonitorUnpairResult(childId, emptyList(), unpaired = true)
        }
        return MonitorUnpairResult(childId, monitoring(token).monitors, unpaired = false)
    }

    override suspend fun sendLocation(token: String, lat: Double, lng: Double, battery: Int): InsertResult {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        locations[childId] = CurrentLocation(lat, lng, now)
        deviceByChild[childId] = (deviceByChild[childId] ?: error("no device"))
            .copy(batteryLevel = battery, lastSeenAt = now)
        if (battery <= lowBatteryThreshold) {
            alertsByChild.getOrPut(childId) { mutableListOf() }
                .add(0, Alert("alert-${++seq}", "low_battery", now))
        }
        return InsertResult(1)
    }

    override suspend fun sendStatus(token: String, battery: Int, charging: Boolean) {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        deviceByChild[childId] = (deviceByChild[childId] ?: error("no device"))
            .copy(batteryLevel = battery, isCharging = charging, lastSeenAt = now)
    }
}
