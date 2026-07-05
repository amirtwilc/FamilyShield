package com.familyshield.app.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tokens(val accessToken: String, val refreshToken: String)

@Serializable
data class Device(
    val id: String,
    val platform: String,
    val model: String? = null,
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null,
    val lastSeenAt: String? = null,
    val revokedAt: String? = null,
)

@Serializable
data class Child(
    val id: String,
    val displayName: String,
    val avatar: String = "fox",
    val devices: List<Device> = emptyList(),
)

@Serializable
data class ChildrenResponse(val children: List<Child>)

@Serializable
data class CurrentLocation(
    val lat: Double,
    val lng: Double,
    val recordedAt: String,
)

@Serializable
data class Alert(
    val id: String,
    val type: String,
    val createdAt: String,
)

@Serializable
data class AlertsResponse(val alerts: List<Alert>)

@Serializable
data class Zone(
    val id: String,
    val name: String,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    @SerialName("radius_m") val radiusM: Int,
    @SerialName("notify_on_enter") val notifyOnEnter: Boolean = true,
    @SerialName("notify_on_exit") val notifyOnExit: Boolean = true,
)

@Serializable
data class ZonesResponse(val zones: List<Zone>)

@Serializable
data class CreateZoneBody(val name: String, val lat: Double, val lng: Double, val radiusM: Int)

@Serializable
data class HistoryPoint(
    val lat: Double,
    val lng: Double,
    val recordedAt: String,
    val speed: Double? = null,
    val accuracy: Double? = null,
)

@Serializable
data class HistoryResponse(val points: List<HistoryPoint>, val nextCursor: String? = null)

@Serializable
data class Geo(val lat: Double, val lng: Double)

@Serializable
data class FrequentRoute(
    val from: Geo,
    val to: Geo,
    val count: Int,
    val lastAt: String,
    val avgMinutes: Double,
    val avgKm: Double,
)

@Serializable
data class RouteTrip(
    val from: Geo,
    val to: Geo,
    val departAt: String,
    val arriveAt: String,
    val durationMin: Double,
    val distanceKm: Double,
)

@Serializable
data class Stop(
    val lat: Double,
    val lng: Double,
    val arriveAt: String,
    val departAt: String,
    val dwellMin: Double = 0.0,
)

@Serializable
data class RoutesResponse(
    val frequent: List<FrequentRoute> = emptyList(),
    val trips: List<RouteTrip> = emptyList(),
    val stops: List<Stop> = emptyList(),
)

@Serializable
data class Message(
    val id: String,
    val sender: String,            // "parent" | "child"
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null,
)

@Serializable
data class MessagesResponse(
    val messages: List<Message> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class ConversationSummary(
    val childId: String,
    val unread: Int = 0,
    val last: Message? = null,
)

@Serializable
data class ConversationsResponse(val conversations: List<ConversationSummary> = emptyList())

@Serializable
data class SendMessageBody(val body: String)

@Serializable
data class AppUsageEntry(
    val app: String,
    val category: String,
    val min: Int,
    val packageName: String? = null,
    val hiddenReason: String? = null,
)

@Serializable
data class UsageDay(val day: String, val dow: String, val min: Int)

@Serializable
data class AppUsageSummary(
    val totalTodayMin: Int = 0,
    val yesterdayMin: Int = 0,
    val avgWeekMin: Int = 0,
    val week: List<UsageDay> = emptyList(),
    val apps: List<AppUsageEntry> = emptyList(),
    val hiddenApps: List<AppUsageEntry> = emptyList(),
    val hiddenTodayMin: Int = 0,
    val hiddenActivityCount: Int = 0,
    val lastUpdatedAt: String? = null,
    val appUsageAccessGranted: Boolean? = null,
)

@Serializable
data class AppUsageReportItem(
    val app: String,
    @SerialName("package_name") val packageName: String,
    val category: String,
    val minutes: Int,
    val day: String? = null,
    @SerialName("is_relevant") val isRelevant: Boolean = true,
    @SerialName("hidden_reason") val hiddenReason: String? = null,
)

@Serializable
data class AppUsageReportBody(val items: List<AppUsageReportItem>)

@Serializable
data class AppUsageTelemetryBody(
    @SerialName("access_granted") val accessGranted: Boolean,
    val items: List<AppUsageReportItem> = emptyList(),
)

@Serializable
data class DeviceTelemetryBody(
    val status: StatusBody? = null,
    val location: LocationPoint? = null,
    @SerialName("app_usage") val appUsage: AppUsageTelemetryBody? = null,
)

@Serializable
data class DeviceTelemetryResult(
    val ok: Boolean = true,
    val locationInserted: Int = 0,
    val appUsageInserted: Int = 0,
)

@Serializable
data class PairingCode(val code: String, val expiresAt: String)

@Serializable
data class PairResult(val deviceToken: String, val childId: String)

@Serializable
data class Monitor(
    val parentId: String,
    val email: String,
    val displayName: String,
    val role: String,
)

@Serializable
data class MonitoringInfo(
    val childId: String,
    val monitors: List<Monitor> = emptyList(),
)

@Serializable
data class MonitorUnpairResult(
    val childId: String? = null,
    val monitors: List<Monitor> = emptyList(),
    val unpaired: Boolean = false,
)

@Serializable
data class InsertResult(val inserted: Int)

// ---- request bodies ----
@Serializable
data class Credentials(val email: String, val password: String)

@Serializable
data class RefreshBody(val refreshToken: String)

@Serializable
data class GoogleLoginBody(val idToken: String)

@Serializable
data class PushTokenBody(@SerialName("fcm_token") val fcmToken: String)

@Serializable
data class CreateChildBody(val displayName: String, val avatar: String? = null)

@Serializable
data class PairBody(val code: String, val platform: String, val model: String? = null)

@Serializable
data class LocationPoint(
    val lat: Double,
    val lng: Double,
    @SerialName("recorded_at") val recordedAt: String,
    @SerialName("battery_level") val batteryLevel: Int? = null,
)

@Serializable
data class LocationBatch(val points: List<LocationPoint>)

@Serializable
data class StatusBody(
    @SerialName("battery_level") val batteryLevel: Int,
    @SerialName("is_charging") val isCharging: Boolean,
)
