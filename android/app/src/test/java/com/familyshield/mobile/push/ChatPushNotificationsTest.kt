package com.familyshield.mobile.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatPushNotificationsTest {
    @Test
    fun `parses parent chat destination from push data`() {
        val destination = chatPushDestinationFromData(
            mapOf(
                "type" to "chat_message",
                "recipient" to "parent",
                "childId" to "child-1",
                "parentId" to "parent-1",
                "messageId" to "message-1",
            ),
        )

        assertEquals(ChatPushDestination("parent", "child-1", "parent-1", "message-1"), destination)
    }

    @Test
    fun `parses child chat destination from push data`() {
        val destination = chatPushDestinationFromData(
            mapOf(
                "type" to "chat_message",
                "recipient" to "child",
                "childId" to "child-1",
                "parentId" to "parent-1",
            ),
        )

        assertEquals(ChatPushDestination("child", "child-1", "parent-1"), destination)
    }

    @Test
    fun `ignores non-chat push data`() {
        assertEquals(null, chatPushDestinationFromData(mapOf("type" to "low_battery")))
    }

    @Test
    fun `recognizes urgent push data`() {
        assertEquals(true, isUrgentPushData(mapOf("type" to "kid_sos_started", "childId" to "child-1")))
        assertEquals(true, isUrgentPushData(mapOf("type" to "urgent_alert", "priority" to "urgent")))
        assertEquals(false, isUrgentPushData(mapOf("type" to "chat_message")))
    }

    @Test
    fun `recognizes safety alert push data`() {
        assertEquals(true, isSafetyAlertPushData(mapOf("type" to "low_battery", "childId" to "child-1")))
        assertEquals(true, isSafetyAlertPushData(mapOf("type" to "safe_zone_exit", "zoneName" to "School")))
        assertEquals(true, isSafetyAlertPushData(mapOf("type" to "app_usage_limit_exceeded", "limitId" to "limit-1")))
        assertEquals(false, isSafetyAlertPushData(mapOf("type" to "chat_message")))
    }

    @Test
    fun `notification id is stable for the same message`() {
        val data = mapOf("messageId" to "msg-1", "childId" to "child-1")

        assertEquals(chatPushNotificationId(data), chatPushNotificationId(data))
    }

    @Test
    fun `notification id varies by message when possible`() {
        assertNotEquals(
            chatPushNotificationId(mapOf("messageId" to "msg-1")),
            chatPushNotificationId(mapOf("messageId" to "msg-2")),
        )
    }

    @Test
    fun `urgent notification id is stable and varies by event`() {
        assertEquals(
            urgentPushNotificationId(mapOf("eventId" to "sos-1", "childId" to "child-1")),
            urgentPushNotificationId(mapOf("eventId" to "sos-1", "childId" to "child-1")),
        )
        assertNotEquals(
            urgentPushNotificationId(mapOf("eventId" to "sos-1")),
            urgentPushNotificationId(mapOf("eventId" to "sos-2")),
        )
    }

    @Test
    fun `safety alert notification id is stable and varies by zone`() {
        assertEquals(
            safetyAlertNotificationId(mapOf("type" to "safe_zone_enter", "childId" to "child-1", "zoneId" to "zone-1")),
            safetyAlertNotificationId(mapOf("type" to "safe_zone_enter", "childId" to "child-1", "zoneId" to "zone-1")),
        )
        assertNotEquals(
            safetyAlertNotificationId(mapOf("type" to "safe_zone_enter", "childId" to "child-1", "zoneId" to "zone-1")),
            safetyAlertNotificationId(mapOf("type" to "safe_zone_enter", "childId" to "child-1", "zoneId" to "zone-2")),
        )
        assertNotEquals(
            safetyAlertNotificationId(mapOf("type" to "app_usage_limit_exceeded", "childId" to "child-1", "limitId" to "limit-1")),
            safetyAlertNotificationId(mapOf("type" to "app_usage_limit_exceeded", "childId" to "child-1", "limitId" to "limit-2")),
        )
    }
}
