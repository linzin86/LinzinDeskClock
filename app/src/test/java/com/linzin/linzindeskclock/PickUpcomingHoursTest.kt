package com.linzin.linzindeskclock

import org.junit.Assert.assertEquals
import org.junit.Test

class PickUpcomingHoursTest {
    // 48 hourly slots starting at an arbitrary local midnight; slot i has temperature i
    private val midnightSec = 1_794_009_600L
    private val slots = List(48) { midnightSec + it * 3600L }
    private val temps = List(48) { it.toFloat() }
    private val codes = List(48) { 0 }

    private fun pickAt(hoursAfterMidnight: Double) = pickUpcomingHours(
        slotStartsSec = slots,
        tempsC = temps,
        weatherCodes = codes,
        nowMillis = midnightSec * 1000L + (hoursAfterMidnight * 3_600_000).toLong()
    ).map { it.tempC }

    @Test
    fun lateEveningContinuesIntoTomorrowInsteadOfThisMorning() {
        assertEquals(listOf(22f, 23f, 24f, 25f), pickAt(22.5))
    }

    @Test
    fun startsWithTheCurrentHourSlot() {
        assertEquals(listOf(9f, 10f, 11f, 12f), pickAt(9.0))
        assertEquals(listOf(9f, 10f, 11f, 12f), pickAt(9.99))
    }

    @Test
    fun returnsWhatIsLeftAtTheEndOfTheForecast() {
        assertEquals(listOf(46f, 47f), pickAt(46.2))
        assertEquals(emptyList<Float>(), pickAt(48.0))
    }
}
