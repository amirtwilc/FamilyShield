package com.familyshield.mobile.kid

import com.familyshield.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringNotificationTest {
    @Test
    fun `uses default copy without optional access`() {
        assertEquals(
            R.string.kid_monitoring_notification_body,
            monitoringNotificationBodyRes(appUsageGranted = false, dndBypassAllowed = false),
        )
    }

    @Test
    fun `mentions app usage only when usage access is granted`() {
        assertEquals(
            R.string.kid_monitoring_notification_body_app_usage,
            monitoringNotificationBodyRes(appUsageGranted = true, dndBypassAllowed = false),
        )
    }

    @Test
    fun `mentions DND only when bypass is allowed`() {
        assertEquals(
            R.string.kid_monitoring_notification_body_dnd,
            monitoringNotificationBodyRes(appUsageGranted = false, dndBypassAllowed = true),
        )
    }

    @Test
    fun `mentions both optional capabilities when both are allowed`() {
        assertEquals(
            R.string.kid_monitoring_notification_body_app_usage_dnd,
            monitoringNotificationBodyRes(appUsageGranted = true, dndBypassAllowed = true),
        )
    }
}
