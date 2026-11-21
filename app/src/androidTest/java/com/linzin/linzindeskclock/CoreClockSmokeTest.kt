package com.linzin.linzindeskclock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoreClockSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun ordinaryPhoneCanEnterClockRotateAndResume() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
            .putString("pref_app_language", "en")
            .putBoolean("pref_24_hour", true)
            .putBoolean("pref_weather_enabled", false)
            .putBoolean("pref_show_calendar_events", false)
            .putBoolean("pref_dismiss_gesture_tutorial", true)
            .putBoolean("pref_dismiss_layout_tip", true)
            .commit()
        val previousProxy = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> =
                listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 9)))
            override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) {}
        })
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                if (Build.VERSION.SDK_INT <= 32) {
                    compose.onNodeWithText("Legacy Android Compatibility Notice").assertIsDisplayed()
                    compose.onNodeWithText("Got It").performScrollTo().performClick()
                }
                compose.onNodeWithText("Not a book-style foldable?").performScrollTo().performClick()
                compose.onNodeWithText("Please, I'll do anything you want!")
                    .performScrollTo().performClick()
                assertClockContent()

                waitForOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE)
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                waitForOrientation(scenario, Configuration.ORIENTATION_PORTRAIT)
                assertClockContent()

                assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation
                    .performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
                compose.waitUntil(10_000) { scenario.state == Lifecycle.State.CREATED }
                val launch = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
                        "-n com.linzin.linzindeskclock/.MainActivity"
                )
                ParcelFileDescriptor.AutoCloseInputStream(launch).use { it.readBytes() }
                compose.waitUntil(10_000) { scenario.state == Lifecycle.State.RESUMED }
                // The ordinary-phone entry is intentionally reset by onStop until permanently accepted.
                compose.onNodeWithText("Not a book-style foldable?").performScrollTo().performClick()
                compose.onNodeWithText("Please, I'll do anything you want!")
                    .performScrollTo().performClick()
                assertClockContent()
            }
        } finally {
            ProxySelector.setDefault(previousProxy)
        }
    }

    private fun waitForOrientation(scenario: ActivityScenario<MainActivity>, orientation: Int) {
        compose.waitUntil(10_000) {
            var matches = false
            scenario.onActivity { matches = it.resources.configuration.orientation == orientation }
            matches
        }
    }

    private fun assertClockContent() {
        compose.onAllNodesWithContentDescription(":").onFirst().assertIsDisplayed()
        val digits = compose.onAllNodes(SemanticsMatcher("single clock digit") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.any { it.length == 1 && it[0].isDigit() } == true
        }).fetchSemanticsNodes().sortedBy { it.boundsInRoot.left }
            .joinToString("") { it.config[SemanticsProperties.ContentDescription].first() }
        val now = System.currentTimeMillis()
        val format = SimpleDateFormat("HHmm", Locale.US)
        assertTrue("Expected visible clock time, found $digits", digits in listOf(
            format.format(Date(now)), format.format(Date(now - 60_000))
        ))
    }
}
