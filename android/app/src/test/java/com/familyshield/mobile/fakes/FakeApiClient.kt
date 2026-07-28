package com.familyshield.mobile.fakes

import com.familyshield.mobile.net.*

/**
 * In-memory fake of [ApiClient] that mirrors the real FamilyShield backend's behaviour:
 * account creation/login, child management, pairing, location ingestion (kid GPS),
 * location read-back (parent), device status, and low-battery alerting. Lets the
 * ViewModels be unit-tested on the JVM without a network or a real server.
 */
class FakeApiClient(private val lowBatteryThreshold: Int = 15) : ApiClient {

    private val parents = mutableMapOf<String, String>()        // email -> password
    val parentPushTokens = mutableMapOf<String, String>()       // parent email -> FCM token
    private val childName = linkedMapOf<String, String>()        // childId -> name
    private val childAvatar = mutableMapOf<String, String>()     // childId -> avatar key
    private val childPhone = mutableMapOf<String, String?>()      // childId -> optional phone number
    private val childParents = mutableMapOf<String, MutableMap<String, String>>() // childId -> parent email -> display name
    private val childParentNames = mutableMapOf<String, MutableMap<String, String>>() // childId -> parent email -> child-facing parent name
    private val deviceByChild = mutableMapOf<String, Device>()   // childId -> its device
    private val locations = mutableMapOf<String, CurrentLocation>()
    private val alertsByChild = mutableMapOf<String, MutableList<Alert>>()
    private val codeToChild = mutableMapOf<String, String>()     // pairing code -> childId
    private val deviceTokenToChild = mutableMapOf<String, String>()
    private val sosByChild = mutableMapOf<String, SosState>()
    private val sosUsageByChildDay = mutableMapOf<String, Int>()
    private var seq = 0
    private val now get() = "2026-06-20T00:00:0${seq % 10}Z"
    private val avatarKeys = listOf(
        "fox", "panda", "tiger", "unicorn", "bunny", "koala",
        "monkey", "frog", "dog", "cat", "bear", "penguin",
        "lion", "duck", "chick", "hamster", "mouse", "pig",
        "cow", "horse", "wolf", "owl", "turtle", "dolphin",
    )

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

    override suspend fun registerParentPushToken(token: String, fcmToken: String) {
        parentPushTokens[parentEmail(token)] = fcmToken
    }

    /** When set, the next listChildren call rejects with 401 (simulates an expired access token). */
    var expireNextCall = false

    override suspend fun listChildren(token: String): List<Child> {
        if (expireNextCall) { expireNextCall = false; throw ApiException(401, "Invalid token") }
        val parent = parentEmail(token)
        return childName.mapNotNull { (id, fallbackName) ->
            val name = childParents[id]?.get(parent) ?: return@mapNotNull null
            Child(id, name, childAvatar[id] ?: "fox", childPhone[id], deviceByChild[id]?.let { listOf(it) } ?: emptyList())
        }
    }

    override suspend fun createChild(token: String, name: String, avatar: String?, phoneNumber: String?): Child {
        if (listChildren(token).size >= 5) throw ApiException(403, "Your free tier allows up to 5 monitored children")
        val id = "child-${++seq}"
        childName[id] = name
        childAvatar[id] = avatar ?: avatarKeys.firstOrNull { it !in childAvatar.values } ?: avatarKeys.first()
        childPhone[id] = phoneNumber?.trim()?.takeIf { it.isNotEmpty() }
        childParents.getOrPut(id) { linkedMapOf() }[parentEmail(token)] = name
        return Child(id, name, childAvatar[id] ?: "fox", childPhone[id], emptyList())
    }

    override suspend fun updateChild(token: String, childId: String, name: String, avatar: String?, phoneNumber: String?): Child {
        childParents.getOrPut(childId) { linkedMapOf() }[parentEmail(token)] = name
        childName[childId] = name
        avatar?.let { childAvatar[childId] = it }
        childPhone[childId] = phoneNumber?.trim()?.takeIf { it.isNotEmpty() }
        return Child(childId, name, childAvatar[childId] ?: "fox", childPhone[childId], deviceByChild[childId]?.let { listOf(it) } ?: emptyList())
    }

    override suspend fun deleteChild(token: String, childId: String) {
        childParents[childId]?.remove(parentEmail(token))
        if (childParents[childId].isNullOrEmpty()) {
            childParents.remove(childId)
            childParentNames.remove(childId)
            childName.remove(childId)
            childAvatar.remove(childId)
            childPhone.remove(childId)
            deviceByChild.remove(childId)
            locations.remove(childId)
            alertsByChild.remove(childId)
            sosByChild.remove(childId)
            sosUsageByChildDay.keys.removeIf { it.startsWith("$childId:") }
            deviceTokenToChild.entries.removeIf { it.value == childId }
        }
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

    private val zonesByParent = mutableMapOf<String, MutableList<Zone>>()
    private val zoneState = mutableMapOf<String, Boolean>()

    override suspend fun listZones(token: String, childId: String): List<Zone> =
        zonesByParent[parentEmail(token)]?.toList() ?: emptyList()

    override suspend fun createZone(token: String, childId: String, name: String, lat: Double, lng: Double, radiusM: Int, active: Boolean): Zone {
        val z = Zone("zone-${++seq}", name, lat, lng, radiusM, active)
        zonesByParent.getOrPut(parentEmail(token)) { mutableListOf() }.add(0, z)
        return z
    }

    override suspend fun updateZone(
        token: String,
        childId: String,
        zoneId: String,
        name: String?,
        radiusM: Int?,
        active: Boolean?,
    ): Zone {
        val zones = zonesByParent[parentEmail(token)] ?: throw ApiException(404, "Zone not found")
        val index = zones.indexOfFirst { it.id == zoneId }
        if (index < 0) throw ApiException(404, "Zone not found")
        val updated = zones[index].copy(
            name = name ?: zones[index].name,
            radiusM = radiusM ?: zones[index].radiusM,
            active = active ?: zones[index].active,
        )
        zones[index] = updated
        return updated
    }

    override suspend fun deleteZone(token: String, childId: String, zoneId: String) {
        zonesByParent[parentEmail(token)]?.removeAll { it.id == zoneId }
    }

    override suspend fun locationHistory(token: String, childId: String, date: String): List<HistoryPoint> =
        locations[childId]?.let { listOf(HistoryPoint(it.lat, it.lng, it.recordedAt)) } ?: emptyList()

    var historyDaysResult: List<String> = emptyList()
    override suspend fun locationHistoryDays(token: String, childId: String, days: Int): List<String> =
        historyDaysResult

    /** Test-controllable routes payload. */
    var routesResult = RoutesResponse()
    override suspend fun routes(token: String, childId: String): RoutesResponse = routesResult

    var appUsageResult = AppUsageSummary()
    val appUsageRequests = mutableListOf<Pair<String, String?>>()
    private val reportedAppUsage = mutableMapOf<String, List<AppUsageReportItem>>()
    private val appUsageAccessByChild = mutableMapOf<String, Boolean>()
    private val usageLimitsByChild = mutableMapOf<String, MutableList<AppUsageLimit>>()

    override suspend fun appUsage(token: String, childId: String, date: String?): AppUsageSummary {
        appUsageRequests.add(childId to date)
        val limits = usageLimitsByChild[childId].orEmpty()
        val reported = reportedAppUsage[childId]
        return if (reported == null) appUsageResult.copy(selectedDay = date, limits = limits) else AppUsageSummary(
            selectedDay = date,
            totalTodayMin = reported.filter { it.minutes >= 5 }.sumOf { it.minutes },
            apps = reported.filter { it.minutes >= 5 }.map {
                AppUsageEntry(it.app, it.category, it.minutes, it.packageName)
            },
            limits = limits,
            lastUpdatedAt = now,
            appUsageAccessGranted = appUsageAccessByChild[childId],
        )
    }

    override suspend fun appUsageLimits(token: String, childId: String): List<AppUsageLimit> =
        usageLimitsByChild[childId].orEmpty()

    override suspend fun saveAppUsageLimit(token: String, childId: String, body: AppUsageLimitBody): AppUsageLimit {
        val limits = usageLimitsByChild.getOrPut(childId) { mutableListOf() }
        val existing = limits.firstOrNull {
            if (body.type == "total") {
                it.type == "total"
            } else {
                it.type == "app" && if (body.packageName != null) it.packageName == body.packageName else it.packageName == null && it.app == body.app
            }
        }
        val updated = AppUsageLimit(
            id = existing?.id ?: "limit-${++seq}",
            childId = childId,
            type = body.type,
            packageName = if (body.type == "app") body.packageName else null,
            app = if (body.type == "app") body.app else null,
            category = if (body.type == "app") body.category else null,
            limitMinutes = body.limitMinutes,
            active = body.active ?: true,
        )
        if (existing == null) limits.add(updated) else limits[limits.indexOf(existing)] = updated
        return updated
    }

    override suspend fun updateAppUsageLimit(token: String, childId: String, limitId: String, body: UpdateAppUsageLimitBody): AppUsageLimit {
        val limits = usageLimitsByChild[childId] ?: throw ApiException(404, "Limit not found")
        val index = limits.indexOfFirst { it.id == limitId }
        if (index < 0) throw ApiException(404, "Limit not found")
        val updated = limits[index].copy(
            limitMinutes = body.limitMinutes ?: limits[index].limitMinutes,
            active = body.active ?: limits[index].active,
        )
        limits[index] = updated
        return updated
    }

    override suspend fun deleteAppUsageLimit(token: String, childId: String, limitId: String) {
        usageLimitsByChild[childId]?.removeAll { it.id == limitId }
    }

    override suspend fun sendAppUsage(token: String, items: List<AppUsageReportItem>): InsertResult {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val visibleItems = items.filter { it.minutes >= 5 }
        reportedAppUsage[childId] = visibleItems
        return InsertResult(visibleItems.size)
    }

    override suspend fun sendTelemetry(token: String, body: DeviceTelemetryBody): DeviceTelemetryResult {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        body.appUsage?.let {
            appUsageAccessByChild[childId] = it.accessGranted
            if (it.items.isNotEmpty()) reportedAppUsage[childId] = it.items.filter { item -> item.minutes >= 5 }
        }
        body.status?.let { status ->
            val current = deviceByChild[childId] ?: error("no device")
            deviceByChild[childId] = (deviceByChild[childId] ?: error("no device"))
                .copy(
                    batteryLevel = status.batteryLevel ?: current.batteryLevel,
                    isCharging = status.isCharging ?: current.isCharging,
                    lastSeenAt = now,
                    permissionStatus = status.permissionStatus ?: current.permissionStatus,
                    permissionStatusCheckedAt = if (status.permissionStatus != null) now else current.permissionStatusCheckedAt,
                )
            status.permissionStatus?.let { appUsageAccessByChild[childId] = (it.m and 16) != 0 }
            if ((status.batteryLevel ?: current.batteryLevel ?: 100) <= lowBatteryThreshold) {
                alertsByChild.getOrPut(childId) { mutableListOf() }
                    .add(0, Alert("alert-${++seq}", "low_battery", now))
            }
        }
        val telemetryLocations = (body.locations + listOfNotNull(body.location))
            .distinctBy { it.recordedAt }
            .sortedBy { it.recordedAt }
        telemetryLocations.lastOrNull()?.let { location ->
            locations[childId] = CurrentLocation(location.lat, location.lng, location.recordedAt)
            fireSafeZoneAlerts(childId, location.lat, location.lng)
        }
        return DeviceTelemetryResult(
            ok = true,
            locationInserted = telemetryLocations.size,
            appUsageInserted = body.appUsage?.items?.count { it.minutes >= 5 } ?: 0,
        )
    }

    override suspend fun startSos(token: String, timezone: String, localDay: String, location: LocationPoint?): SosState {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val existing = sosByChild[childId]
        if (existing?.active == true) return existing
        location?.let {
            locations[childId] = CurrentLocation(it.lat, it.lng, it.recordedAt)
        }
        val event = SosEvent(
            id = "sos-${++seq}",
            childId = childId,
            status = "active",
            startedAt = now,
            timezone = timezone,
            localDay = localDay,
            lastLocation = location?.let { SosLocation(it.lat, it.lng) },
            lastLocationAt = location?.recordedAt,
            lastBatteryLevel = location?.batteryLevel,
        )
        val state = sosStateFor(childId, localDay, event)
        sosByChild[childId] = state
        alertsByChild.getOrPut(childId) { mutableListOf() }.add(0, Alert("alert-${++seq}", "kid_sos_started", now))
        return state
    }

    override suspend fun sendSosLocation(token: String, timezone: String, localDay: String, location: LocationPoint): SosLocationResult {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val event = sosByChild[childId]?.event
            ?: return SosLocationResult(active = false, accepted = false, reason = "no_active_sos")
        val key = "$childId:$localDay"
        val used = sosUsageByChildDay[key] ?: 0
        if (used >= 3600) {
            return SosLocationResult(active = true, accepted = false, reason = "quota_exhausted", state = sosStateFor(childId, localDay, event))
        }
        val charged = minOf(60, 3600 - used)
        sosUsageByChildDay[key] = used + charged
        locations[childId] = CurrentLocation(location.lat, location.lng, location.recordedAt)
        val updatedEvent = event.copy(
            timezone = timezone,
            localDay = localDay,
            lastLocation = SosLocation(location.lat, location.lng),
            lastLocationAt = location.recordedAt,
            lastBatteryLevel = location.batteryLevel,
        )
        val state = sosStateFor(childId, localDay, updatedEvent)
        sosByChild[childId] = state
        return SosLocationResult(active = true, accepted = true, chargedSeconds = charged, state = state)
    }

    override suspend fun endSos(token: String, reason: String): SosState {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val event = sosByChild[childId]?.event
        if (event != null) {
            sosByChild[childId] = SosState(
                active = false,
                dailyUsedSeconds = sosUsageByChildDay["$childId:${event.localDay}"] ?: 0,
            )
            alertsByChild.getOrPut(childId) { mutableListOf() }.add(0, Alert("alert-${++seq}", "kid_sos_ended", now))
        }
        return sosByChild[childId] ?: SosState()
    }

    override suspend fun sosState(token: String, localDay: String?): SosState {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val state = sosByChild[childId] ?: return SosState()
        val day = localDay ?: state.event?.localDay
        val used = day?.let { sosUsageByChildDay["$childId:$it"] } ?: 0
        return state.copy(dailyUsedSeconds = used, remainingSeconds = (3600 - used).coerceAtLeast(0))
    }

    private fun sosStateFor(childId: String, localDay: String, event: SosEvent): SosState {
        val used = sosUsageByChildDay["$childId:$localDay"] ?: 0
        return SosState(
            active = true,
            event = event,
            dailyUsedSeconds = used,
            dailyLimitSeconds = 3600,
            highRateIntervalSeconds = 60,
            remainingSeconds = (3600 - used).coerceAtLeast(0),
        )
    }

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

    override suspend fun sendUrgentAlert(token: String, childId: String, body: String): UrgentAlertResult {
        val parent = parentEmail(token)
        val m = Message("msg-${++seq}", "parent", body, now, priority = "urgent")
        monitorMessagesByChild.getOrPut(childId) { mutableMapOf() }.getOrPut(parent) { mutableListOf() }.add(m)
        messagesByChild.getOrPut(childId) { mutableListOf() }.add(m)
        alertsByChild.getOrPut(childId) { mutableListOf() }.add(0, Alert("alert-${++seq}", "urgent_alert", now))
        return UrgentAlertResult(m, delivered = true)
    }

    override suspend fun currentSos(token: String, childId: String): SosState =
        sosByChild[childId] ?: SosState()

    override suspend fun acknowledgeSos(token: String, childId: String, eventId: String): SosAckResult =
        SosAckResult(ok = true, delivered = true)

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
    override suspend fun pair(code: String, platform: String, model: String?, parentDisplayName: String): PairResult {
        val childId = codeToChild.remove(code) ?: throw ApiException(400, "Code is invalid, expired, or already used")
        val parent = childParents[childId]?.keys?.firstOrNull()
        if (parent != null) childParentNames.getOrPut(childId) { linkedMapOf() }[parent] = parentDisplayName
        val deviceToken = "dev-${++seq}"
        deviceTokenToChild[deviceToken] = childId
        deviceByChild[childId] = Device(
            id = "device-$seq", platform = platform, model = model,
            batteryLevel = null, isCharging = null, lastSeenAt = now, revokedAt = null,
        )
        return PairResult(deviceToken, childId)
    }

    override suspend fun addParent(token: String, code: String, platform: String, model: String?, parentDisplayName: String): MonitoringInfo {
        val currentChildId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val sourceChildId = codeToChild.remove(code) ?: throw ApiException(400, "Code is invalid, expired, or already used")
        if (deviceByChild[sourceChildId] != null) throw ApiException(400, "This code belongs to a child that already has a paired device")
        val sourceLinks = childParents[sourceChildId].orEmpty()
        val parent = sourceLinks.keys.firstOrNull() ?: throw ApiException(400, "Code is invalid, expired, or already used")
        if (childParents[currentChildId]?.containsKey(parent) == true) {
            throw ApiException(400, "This parent already monitors this child")
        }
        childParents.getOrPut(currentChildId) { linkedMapOf() }[parent] = sourceLinks.getValue(parent)
        childParentNames.getOrPut(currentChildId) { linkedMapOf() }[parent] = parentDisplayName
        childParents.remove(sourceChildId)
        childParentNames.remove(sourceChildId)
        childName.remove(sourceChildId)
        childAvatar.remove(sourceChildId)
        return monitoring(token)
    }

    override suspend fun monitoring(token: String): MonitoringInfo {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val monitors = childParents[childId].orEmpty().map { (email, displayName) ->
            Monitor("parent:$email", email, displayName, childParentNames[childId]?.get(email))
        }
        return MonitoringInfo(childId, monitors)
    }

    override suspend fun updateMonitorName(token: String, parentId: String, parentDisplayName: String): MonitoringInfo {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val parent = parentId.removePrefix("parent:")
        if (childParents[childId]?.containsKey(parent) != true) throw ApiException(404, "Monitor not found")
        childParentNames.getOrPut(childId) { linkedMapOf() }[parent] = parentDisplayName
        return monitoring(token)
    }

    override suspend fun removeMonitor(token: String, parentId: String): MonitorUnpairResult {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        val parent = parentId.removePrefix("parent:")
        val links = childParents[childId] ?: throw ApiException(404, "Monitor not found")
        if (!links.containsKey(parent)) throw ApiException(404, "Monitor not found")
        if (links.size <= 1) {
            deviceTokenToChild.remove(token)
            deviceByChild[childId] = deviceByChild[childId]?.copy(revokedAt = now) ?: error("no device")
            alertsByChild.getOrPut(childId) { mutableListOf() }.add(Alert("alert-${++seq}", "child_unpaired", now))
            return MonitorUnpairResult(childId, emptyList(), unpaired = true)
        }
        links.remove(parent)
        childParentNames[childId]?.remove(parent)
        monitorMessagesByChild[childId]?.remove(parent)
        return MonitorUnpairResult(childId, monitoring(token).monitors, unpaired = false)
    }

    override suspend fun sendLocation(token: String, lat: Double, lng: Double, battery: Int): InsertResult {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        locations[childId] = CurrentLocation(lat, lng, now)
        deviceByChild[childId] = (deviceByChild[childId] ?: error("no device"))
            .copy(batteryLevel = battery, lastSeenAt = now)
        fireSafeZoneAlerts(childId, lat, lng)
        if (battery <= lowBatteryThreshold) {
            alertsByChild.getOrPut(childId) { mutableListOf() }
                .add(0, Alert("alert-${++seq}", "low_battery", now))
        }
        return InsertResult(1)
    }

    override suspend fun sendStatus(token: String, battery: Int, charging: Boolean, permissionStatus: PermissionStatus?) {
        val childId = deviceTokenToChild[token] ?: throw ApiException(401, "Invalid device token")
        deviceByChild[childId] = (deviceByChild[childId] ?: error("no device"))
            .copy(
                batteryLevel = battery,
                isCharging = charging,
                permissionStatus = permissionStatus,
                permissionStatusCheckedAt = if (permissionStatus != null) now else deviceByChild[childId]?.permissionStatusCheckedAt,
                lastSeenAt = now,
            )
        permissionStatus?.let { appUsageAccessByChild[childId] = (it.m and 16) != 0 }
    }

    private fun fireSafeZoneAlerts(childId: String, lat: Double, lng: Double) {
        childParents[childId].orEmpty().keys.forEach { parent ->
            zonesByParent[parent].orEmpty().filter { it.active }.forEach { zone ->
                val inside = distanceM(lat, lng, zone.lat, zone.lng) <= zone.radiusM
                val key = "$parent:$childId:${zone.id}"
                val previous = zoneState[key]
                zoneState[key] = inside
                val type = when {
                    previous == null && inside -> "safe_zone_enter"
                    previous == true && !inside -> "safe_zone_exit"
                    previous == false && inside -> "safe_zone_enter"
                    else -> null
                }
                if (type != null) {
                    alertsByChild.getOrPut(childId) { mutableListOf() }.add(0, Alert("alert-${++seq}", type, now))
                }
            }
        }
    }

    private fun distanceM(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
    }
}
