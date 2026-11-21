package com.linzin.linzindeskclock

import org.junit.Assert.*
import org.junit.Test

class WeatherInfoTest {
    @Test
    fun failureKeepsTheLastSuccessfulWeatherAndItsTimestamp() {
        val previous = WeatherInfo("Old city", 21f, 0, emptyList(), fetchedAtMillis = 1000)
        val retained = weatherAfterRefresh(previous, null)
        assertNotNull(retained)
        assertTrue(retained!!.isStale)
        assertEquals("Old city", retained.cityName)
        assertEquals(1000L, retained.fetchedAtMillis)
        assertFalse(previous.isStale)
    }

    @Test
    fun failureWithoutPreviousSuccessRemainsUnavailable() {
        assertNull(weatherAfterRefresh(null, null))
    }

    @Test
    fun successReplacesStaleDataAndUsesItsOwnTimestamp() {
        val previous = WeatherInfo("Old city", 21f, 0, emptyList(), 1000, isStale = true)
        val incoming = WeatherInfo("New city", 25f, 1, emptyList(), 2000)
        assertSame(incoming, weatherAfterRefresh(previous, incoming))
        assertFalse(incoming.isStale)
        assertEquals(2000L, incoming.fetchedAtMillis)
    }
}
