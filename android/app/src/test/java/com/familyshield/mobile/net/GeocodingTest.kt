package com.familyshield.mobile.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GeocodingTest {
    @Test
    fun `coordinate cache key absorbs small GPS jitter`() {
        assertEquals(
            coordinateKey(50.934953, 6.974577),
            coordinateKey(50.934954, 6.974578),
        )
    }

    @Test
    fun `coordinate cache key keeps meaningfully different points separate`() {
        assertNotEquals(
            coordinateKey(50.934953, 6.974577),
            coordinateKey(50.935953, 6.975577),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `concurrent callers share one independent lookup`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val release = CompletableDeferred<Unit>()
        val pool = IndependentLookupPool<String?>(scope)

        val first = pool.getOrStart("place") {
            release.await()
            "The Gym"
        }
        val second = pool.getOrStart("place") { "wrong duplicate" }

        assertFalse(first.shared)
        assertTrue(second.shared)
        assertSame(first.deferred, second.deferred)

        release.complete(Unit)
        advanceUntilIdle()
        assertEquals("The Gym", first.deferred.await())
        scope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `lookup continues after its UI waiter is cancelled`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val release = CompletableDeferred<Unit>()
        val pool = IndependentLookupPool<String?>(scope)
        var completed = false
        val lookup = pool.getOrStart("place") {
            release.await()
            completed = true
            "The Gym"
        }
        val uiWaiter = launch { lookup.deferred.await() }

        runCurrent()
        uiWaiter.cancel()
        release.complete(Unit)
        advanceUntilIdle()

        assertTrue(completed)
        assertEquals("The Gym", lookup.deferred.await())
        scope.cancel()
    }
}
