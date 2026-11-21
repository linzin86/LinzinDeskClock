package com.linzin.linzindeskclock

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class WeatherRefreshOwnerTest {
    private val a = WeatherRequestInput("en", "Current location")
    private val b = WeatherRequestInput("ja", "現在地", locationRevision = 1)
    private fun weather(city: String) = WeatherInfo(city, 20f, 0, emptyList())
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test fun resumeManualAndPollingMergeWhileTheSameInputIsLoading() = runBlocking {
        val scope = scope()
        val gate = CompletableDeferred<WeatherInfo?>()
        var requests = 0
        var refreshing = false
        val results = mutableListOf<WeatherInfo?>()
        val owner = WeatherRefreshOwner(scope, { requests++; gate.await() }, results::add, { refreshing = it })
        try {
            owner.update(a)
            repeat(3) { owner.refresh(); owner.update(a) }
            assertEquals(1, requests)
            assertTrue(refreshing)
            gate.complete(weather("A"))
            assertEquals(listOf("A"), results.map { it?.cityName })
            assertFalse(refreshing)
        } finally { gate.complete(null); scope.cancel() }
    }

    @Test fun changedInputCancelsTheOldRequestAndIgnoresItsLateResult() = runBlocking {
        val scope = scope()
        val gate = CompletableDeferred<Unit>()
        var oldCancelled = false
        var refreshing = false
        val results = mutableListOf<WeatherInfo?>()
        val owner = WeatherRefreshOwner(scope, { input ->
            if (input == a) {
                try { withContext(NonCancellable) { gate.await() }; weather("A") }
                finally { oldCancelled = !kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]!!.isActive }
            } else weather("B")
        }, results::add, { refreshing = it })
        try {
            owner.update(a)
            owner.update(b)
            assertEquals(listOf("B"), results.map { it?.cityName })
            assertFalse(refreshing)
            gate.complete(Unit)
            assertTrue(oldCancelled)
            assertEquals(listOf("B"), results.map { it?.cityName })
        } finally { gate.complete(Unit); scope.cancel() }
    }

    @Test fun disablingIgnoresLateResultsAndFurtherTriggers() = runBlocking {
        val scope = scope()
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        var refreshing = false
        val results = mutableListOf<WeatherInfo?>()
        val owner = WeatherRefreshOwner(scope, {
            requests++
            withContext(NonCancellable) { gate.await() }
            weather("A")
        }, results::add, { refreshing = it })
        try {
            owner.update(a)
            owner.update(null)
            owner.refresh()
            assertFalse(refreshing)
            gate.complete(Unit)
            assertTrue(results.isEmpty())
            assertEquals(1, requests)
        } finally { gate.complete(Unit); scope.cancel() }
    }

    @Test fun lateOldCompletionCannotClearTheNewRefreshingFlag() = runBlocking {
        val scope = scope()
        val old = CompletableDeferred<Unit>()
        val next = CompletableDeferred<Unit>()
        var refreshing = false
        val results = mutableListOf<WeatherInfo?>()
        val owner = WeatherRefreshOwner(scope, { request ->
            if (request == a) withContext(NonCancellable) { old.await() } else next.await()
            weather(request.languageTag)
        }, results::add, { refreshing = it })
        try {
            owner.update(a)
            owner.update(b)
            old.complete(Unit)
            assertTrue(refreshing)
            assertTrue(results.isEmpty())
            next.complete(Unit)
            assertFalse(refreshing)
            assertEquals(listOf("ja"), results.map { it?.cityName })
        } finally { old.complete(Unit); next.complete(Unit); scope.cancel() }
    }

    @Test fun scopeCancellationClearsRefreshingAndNeverPublishes() = runBlocking {
        val scope = scope()
        var refreshing = false
        var cancelled = false
        val results = mutableListOf<WeatherInfo?>()
        val errors = mutableListOf<Exception>()
        val owner = WeatherRefreshOwner(scope, {
            try { awaitCancellation() } finally { cancelled = true }
        }, results::add, { refreshing = it }, errors::add)
        owner.update(a)
        scope.cancel()
        assertTrue(cancelled)
        assertFalse(refreshing)
        assertTrue(results.isEmpty())
        assertTrue("Cancellation must not become an ordinary error", errors.isEmpty())
    }

    @Test fun aFailedRefreshDeliversUnavailableAndCanRetry() = runBlocking {
        val scope = scope()
        var refreshing = false
        var attempts = 0
        val results = mutableListOf<WeatherInfo?>()
        val errors = mutableListOf<Exception>()
        val owner = WeatherRefreshOwner(scope, {
            if (++attempts == 1) throw IllegalStateException("fixture") else weather("Recovered")
        }, results::add, { refreshing = it }, errors::add)
        try {
            owner.update(a)
            assertEquals(listOf<WeatherInfo?>(null), results)
            assertFalse(refreshing)
            owner.refresh()
            assertEquals("Recovered", results.last()!!.cityName)
            assertEquals(1, errors.size)
        } finally { scope.cancel() }
    }

    @Test fun permissionOrLocationChangeIsANewInput() = runBlocking {
        val scope = scope()
        var attempts = 0
        val owner = WeatherRefreshOwner(scope, { attempts++; awaitCancellation() }, {}, {})
        try {
            owner.update(a)
            owner.update(a.copy(permissionLevel = 2))
            owner.update(a.copy(permissionLevel = 2, locationRevision = 1))
            assertEquals(3, attempts)
        } finally { scope.cancel() }
    }
}
