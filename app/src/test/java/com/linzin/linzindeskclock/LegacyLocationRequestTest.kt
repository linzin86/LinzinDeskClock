package com.linzin.linzindeskclock

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class LegacyLocationRequestTest {
    private val providers = listOf("network", "gps")

    @Test
    fun partialRegistrationFailureRemovesTheRegisteredListener() = runBlocking<Unit> {
        var removals = 0
        val result = awaitLegacyLocation(providers, 7,
            register = { provider, _ -> if (provider == "gps") throw SecurityException("fixture") },
            remove = { removals++ }, onCleanupError = ::unexpectedCleanup)
        assertEquals(7, result)
        assertEquals(1, removals)
    }

    @Test
    fun synchronousFixStopsFurtherRegistration() = runBlocking<Unit> {
        val registered = mutableListOf<String>()
        var removals = 0
        val result = awaitLegacyLocation(providers, 7,
            register = { provider, callback -> registered.add(provider); callback(42) },
            remove = { removals++ }, onCleanupError = ::unexpectedCleanup)
        assertEquals(42, result)
        assertEquals(listOf("network"), registered)
        assertEquals(1, removals)
    }

    @Test
    fun normalFixAndLateCallbacksCleanOnlyOnce() = runBlocking<Unit> {
        var callback: ((Int) -> Unit)? = null
        var removals = 0
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            awaitLegacyLocation(providers, 7, register = { _, receive -> callback = receive },
                remove = { removals++ }, onCleanupError = ::unexpectedCleanup)
        }
        callback!!(42)
        assertEquals(42, request.await())
        callback!!(43)
        assertEquals(1, removals)
    }

    @Test
    fun timeoutCleansTheListener() = runBlocking<Unit> {
        var removals = 0
        val result = withTimeoutOrNull(30) {
            awaitLegacyLocation(providers, 7, register = { _, _ -> },
                remove = { removals++ }, onCleanupError = ::unexpectedCleanup)
        }
        assertNull(result)
        assertEquals(1, removals)
    }

    @Test
    fun cancellationAndLateCallbackCleanOnlyOnce() = runBlocking<Unit> {
        var callback: ((Int) -> Unit)? = null
        var removals = 0
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            awaitLegacyLocation(providers, 7, register = { _, receive -> callback = receive },
                remove = { removals++ }, onCleanupError = ::unexpectedCleanup)
        }
        request.cancelAndJoin()
        callback!!(42)
        assertEquals(1, removals)
    }

    @Test
    fun cancellationDuringRegistrationStopsFurtherProviders() = runBlocking<Unit> {
        val owner = Job()
        val registered = mutableListOf<String>()
        var removals = 0
        val request = CoroutineScope(owner + Dispatchers.Unconfined).async {
            awaitLegacyLocation(providers, 7,
                register = { provider, _ -> registered.add(provider); owner.cancel() },
                remove = { removals++ }, onCleanupError = ::unexpectedCleanup)
        }
        request.join()
        assertEquals(listOf("network"), registered)
        assertEquals(1, removals)
    }

    @Test
    fun cleanupFailureIsReportedWithoutCrashingTheLocationCallback() = runBlocking<Unit> {
        var callback: ((Int) -> Unit)? = null
        var reported: Exception? = null
        val failure = IOException("fixture cleanup failure")
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            awaitLegacyLocation(providers, 7, register = { _, receive -> callback = receive },
                remove = { throw failure }, onCleanupError = { reported = it })
        }
        try {
            callback!!(42)
        } catch (error: Exception) {
            request.cancelAndJoin()
            throw error
        }
        assertEquals(42, request.await())
        assertSame(failure, reported)
    }

    @Test
    fun registrationCancellationIsNotConvertedIntoCachedSuccess() = runBlocking<Unit> {
        var removals = 0
        try {
            awaitLegacyLocation(providers, 7,
                register = { _, _ -> throw CancellationException("fixture cancellation") },
                remove = { removals++ }, onCleanupError = ::unexpectedCleanup)
            fail("Cancellation must reach the owner")
        } catch (_: CancellationException) {
            assertEquals(1, removals)
        }
    }

    @Test
    fun cancellationDoesNotWaitForAnInFlightRegistration() = runBlocking<Unit> {
        val owner = Job()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val removals = AtomicInteger()
        val request = CoroutineScope(owner + Dispatchers.Default).async {
            awaitLegacyLocation(providers, 7,
                register = { _, _ -> entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) },
                remove = { removals.incrementAndGet() }, onCleanupError = ::unexpectedCleanup)
        }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val cancellation = async(Dispatchers.Default) { request.cancel() }
            withTimeout(1000) { cancellation.await() }
        } finally {
            release.countDown()
            request.join()
            owner.cancel()
        }
        assertEquals(1, removals.get())
    }

    private fun unexpectedCleanup(error: Exception): Unit = throw AssertionError(error)
}
