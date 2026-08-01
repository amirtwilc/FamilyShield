package com.familyshield.mobile.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentPushSessionTest {
    @Test
    fun `parent pushes require the matching signed in parent`() {
        val data = mapOf("recipient" to "parent", "parentId" to "parent-a")
        assertTrue(shouldDisplayParentPush(data, "parent-a"))
        assertFalse(shouldDisplayParentPush(data, "parent-b"))
        assertFalse(shouldDisplayParentPush(data, null))
        assertFalse(shouldDisplayParentPush(mapOf("parentId" to "parent-a"), null))
    }

    @Test
    fun `child pushes are not filtered by parent session`() {
        assertTrue(shouldDisplayParentPush(mapOf("recipient" to "child"), null))
    }
}
