package com.linzin.linzindeskclock

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WeatherRefreshBindingTest {
    @get:Rule val compose = createComposeRule()
    private val input = WeatherRequestInput("en", "Current location")
    private fun weather(name: String) = WeatherInfo(name, 20f, 0, emptyList())
    private class ScreenLifecycle : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun initialSetupStartsOnlyOneRequestEvenIfTheLoaderReturnsImmediately() {
        var requests = 0
        compose.setContent {
            rememberWeatherRefreshOwner(true, input, { 2 }, {}, { requests++; weather("A") }, {}, {})
        }
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun manualTriggersMergeAndLanguageChangeRejectsTheLateOldResponse() {
        val requestInput = mutableStateOf(input)
        val gate = CompletableDeferred<Unit>()
        val requests = mutableListOf<String>()
        val result = mutableStateOf<WeatherInfo?>(null)
        val refreshing = mutableStateOf(false)
        try {
            compose.setContent {
                val owner = rememberWeatherRefreshOwner(true, requestInput.value, { 2 }, {}, { request ->
                    requests.add(request.languageTag)
                    if (request.languageTag == "en") withContext(NonCancellable) { gate.await() }
                    weather(request.languageTag)
                }, { result.value = it }, { refreshing.value = it })
                Button(onClick = owner::refresh) { Text("Refresh") }
                Text(result.value?.cityName ?: "Loading")
            }
            repeat(3) { compose.onNodeWithText("Refresh").performClick() }
            compose.runOnIdle {
                assertEquals(listOf("en"), requests)
                assertTrue(refreshing.value)
                requestInput.value = input.copy(languageTag = "ja")
            }
            compose.onNodeWithText("ja").assertExists()
            compose.runOnIdle { gate.complete(Unit) }
            compose.runOnIdle {
                assertEquals("ja", result.value!!.cityName)
                assertEquals(listOf("en", "ja"), requests)
                assertFalse(refreshing.value)
            }
        } finally { gate.complete(Unit) }
    }

    @Test fun backgroundAndDisablingCancelTheirWorkAndClearRefreshing() {
        val lifecycle = ScreenLifecycle()
        val enabled = mutableStateOf(true)
        val requests = AtomicInteger()
        val cancellations = AtomicInteger()
        val refreshing = mutableStateOf(false)
        compose.runOnUiThread { lifecycle.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                rememberWeatherRefreshOwner(enabled.value, input, { 2 }, {}, {
                    requests.incrementAndGet()
                    try { awaitCancellation() } finally { cancellations.incrementAndGet() }
                }, { fail("Cancelled work must not publish") }, { refreshing.value = it })
            }
        }
        compose.runOnIdle {
            assertEquals(1, requests.get())
            assertTrue(refreshing.value)
            lifecycle.registry.currentState = Lifecycle.State.STARTED
        }
        compose.runOnIdle {
            assertEquals(1, cancellations.get())
            assertFalse(refreshing.value)
            lifecycle.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.runOnIdle {
            assertEquals(2, requests.get())
            enabled.value = false
        }
        compose.runOnIdle {
            assertEquals(2, cancellations.get())
            assertFalse(refreshing.value)
            lifecycle.registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    @Test fun missingPermissionRequestsPermissionWithoutStartingTheLoader() {
        var permissionRequests = 0
        compose.setContent {
            rememberWeatherRefreshOwner(true, input, { 0 }, { permissionRequests++ },
                { fail("Missing permission must not load"); null }, {}, {})
        }
        compose.runOnIdle { assertEquals(1, permissionRequests) }
    }

    @Test fun removingTheScreenCancelsTheOwnedRequest() {
        val visible = mutableStateOf(true)
        var cancelled = false
        var refreshing = false
        compose.setContent {
            if (visible.value) rememberWeatherRefreshOwner(true, input, { 2 }, {}, {
                try { awaitCancellation() } finally { cancelled = true }
            }, { fail("Disposed screen must not receive results") }, { refreshing = it })
        }
        compose.runOnIdle { assertTrue(refreshing); visible.value = false }
        compose.runOnIdle { assertTrue(cancelled); assertFalse(refreshing) }
    }
}
