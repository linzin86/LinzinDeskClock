package com.linzin.linzindeskclock

import android.content.Context
import android.location.Location
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WeatherLocationTest {
    private val prefs = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("weather_location_test", Context.MODE_PRIVATE)

    @Before fun reset() { prefs.edit().clear().commit() }

    @Test fun systemFixKeepsItsOwnTimeAndProviderAfterRepeatedReads() {
        val location = Location("network").apply { latitude = 25.0; longitude = 121.0; time = 1000 }
        val saved = rememberWeatherLocation(prefs, location, 5000)
        assertEquals(1000L, saved.fixTimeMillis)
        assertEquals("network", saved.provider)
        assertEquals(5000L, saved.observedAtMillis)
        assertEquals(saved, readWeatherLocation(prefs))
        assertEquals(saved, readWeatherLocation(prefs))
    }

    @Test fun failedActiveRequestKeepsOldFallbackFixTime() = runBlocking {
        val old = Location("gps").apply { latitude = 25.0; longitude = 121.0; time = 1000 }
        val result = awaitLegacyLocation(listOf("network"), old,
            register = { _, _ -> throw SecurityException("permission revoked") },
            remove = {}, onCleanupError = { throw it })
        val saved = rememberWeatherLocation(prefs, result!!, 99_000)
        assertEquals(1000L, saved.fixTimeMillis)
        assertEquals("gps", saved.provider)
        assertEquals(99_000L, saved.observedAtMillis)
    }

    @Test fun legacyCoordinatesStayUsableWithUnknownMetadata() {
        prefs.edit().putString("pref_last_valid_lat", "25.0")
            .putString("pref_last_valid_lon", "121.0").commit()
        repeat(2) {
            val saved = readWeatherLocation(prefs)!!
            assertEquals(25.0, saved.latitude, 0.0)
            assertNull(saved.fixTimeMillis)
            assertNull(saved.provider)
            assertNull(saved.observedAtMillis)
        }
    }

    @Test fun malformedMetadataDoesNotDiscardCoordinates() {
        prefs.edit().putString("pref_last_valid_lat", "25.0")
            .putString("pref_last_valid_lon", "121.0")
            .putString("pref_last_location_fix_time", "broken")
            .putString("pref_last_location_observed_at", "broken").commit()
        assertEquals(25.0, readWeatherLocation(prefs)!!.latitude, 0.0)
        assertNull(readWeatherLocation(prefs)!!.fixTimeMillis)
    }

    @Test fun invalidAndFutureTimesAreUnknownWithoutDiscardingTheRecord() {
        for (time in listOf(-1L, 0L, 10_001L)) {
            assertNull(WeatherLocation(25.0, 121.0, time, "gps", 10_000).knownFixTime(10_000))
        }
        assertNull(WeatherLocation(25.0, 121.0, 5000, "gps", 10_000).knownFixTime(9000))
        assertEquals(1000L, WeatherLocation(25.0, 121.0, 1000, "gps", 10_000).knownFixTime(10_000))
    }

    @Test fun arbitraryOldFixIsNotExpiredByAnInventedTtl() {
        val saved = WeatherLocation(25.0, 121.0, 1000, "network", 2000)
        assertEquals(1000L, saved.knownFixTime(100L * 24 * 60 * 60 * 1000))
    }
}
