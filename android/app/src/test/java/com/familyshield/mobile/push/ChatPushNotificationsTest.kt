package com.familyshield.mobile.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatPushNotificationsTest {
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
