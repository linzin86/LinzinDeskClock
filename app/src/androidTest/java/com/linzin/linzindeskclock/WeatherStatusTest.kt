package com.linzin.linzindeskclock

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class WeatherStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun weatherDoesNotPresentUnattributedCoordinatesAsLiveLocation() {
        compose.setContent {
            SideWeatherWidget(
                weatherEnabled = true,
                weatherInfo = WeatherInfo("Old city", 21f, 0, emptyList()),
                isFahrenheit = false
            )
        }
        compose.onNodeWithText("Last location · Time unknown · Source unknown").assertIsDisplayed()
    }

    @Test
    fun oldSystemLocationShowsItsFixTimeAndProvider() {
        val previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            compose.setContent {
                SideWeatherWidget(weatherEnabled = true, isFahrenheit = false,
                    weatherInfo = WeatherInfo("Old city", 21f, 0, emptyList(),
                        location = WeatherLocation(25.0, 121.0, 1000, "gps", 2000)))
            }
            compose.onNodeWithText("Last location · 1970/1/1 00:00 · GPS").assertIsDisplayed()
        } finally { TimeZone.setDefault(previousZone) }
    }

    @Test
    fun retainedWeatherCanRetryAndCannotDuplicateAnActiveRefresh() {
        var refreshes = 0
        val refreshing = mutableStateOf(false)
        compose.setContent {
            SideWeatherWidget(
                weatherEnabled = true,
                weatherInfo = WeatherInfo("Old city", 21f, 0, emptyList(), 1000, isStale = true),
                isRefreshing = refreshing.value,
                onRefresh = { refreshes++ },
                isFahrenheit = false
            )
        }
        compose.onNodeWithText("Update unavailable", substring = true).performClick()
        compose.runOnIdle { assertEquals(1, refreshes); refreshing.value = true }
        compose.onNodeWithText("Update unavailable", substring = true).assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, refreshes); refreshing.value = false }
        compose.onNodeWithText("Update unavailable", substring = true).performClick()
        compose.runOnIdle { assertEquals(2, refreshes) }
    }

    @Test
    fun retainedWeatherShowsItsOriginalTimeAndUnavailableUpdateStatus() {
        val previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val info = mutableStateOf(WeatherInfo("Old city", 21f, 0, emptyList(), 1000, isStale = true))
            compose.setContent {
                Box(Modifier.size(160.dp, 300.dp)) {
                    SideWeatherWidget(weatherEnabled = true, weatherInfo = info.value, isFahrenheit = false)
                }
            }
            compose.onNodeWithText("Old city").assertIsDisplayed()
            compose.onNodeWithText("Update unavailable · 1/1 00:00").assertIsDisplayed()
            if (Build.VERSION.SDK_INT >= 26) {
                val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                File(context.cacheDir, "weather-stale.png").outputStream().use {
                    check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                screenshot.recycle()
            }
            compose.runOnIdle { info.value = info.value.copy(isStale = false) }
            compose.onNodeWithText("Update unavailable", substring = true).assertDoesNotExist()
        } finally {
            TimeZone.setDefault(previousZone)
        }
    }
}
