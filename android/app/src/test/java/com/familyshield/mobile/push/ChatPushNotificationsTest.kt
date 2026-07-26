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
}
