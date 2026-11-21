package com.linzin.linzindeskclock

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastSelectionTest {
    @Test
    fun fractionalOffsetKeepsTheCurrentLocalHour() {
        val zone = TimeZone.getTimeZone("Asia/Kathmandu")
        val start = utc("2026-11-16 16:15")
        val rows = (0 until 6).map { row(start + it * 3_600_000L, zone) }.reversed()
        assertEquals(listOf(1, 2, 3, 4).map { start + it * 3_600_000L },
            selectUpcomingForecast(rows, utc("2026-11-16 18:10"), zone.id).map { it.timestampMillis })
    }

    @Test
    fun firstRepeatedHourDoesNotJumpToItsSecondOccurrence() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val start = utc("2026-11-01 04:00")
        val rows = (0 until 6).map { row(start + it * 3_600_000L, zone) }
        assertEquals(rows.subList(1, 5), selectUpcomingForecast(rows, utc("2026-11-01 05:10"), zone.id))
    }

    @Test
    fun halfHourDstGapExcludesThePreviousWallClockHour() {
        val zone = TimeZone.getTimeZone("Australia/Lord_Howe")
        val rows = (0 until 5).map { row(utc("2026-10-03 15:00") + it * 3_600_000L, zone) }
        assertEquals(rows.drop(1), selectUpcomingForecast(rows, utc("2026-10-03 15:40"), zone.id))
    }

    @Test
    fun halfHourDstRepeatStartsAtTheLatestTransition() {
        val zone = TimeZone.getTimeZone("Australia/Lord_Howe")
        val rows = listOf("2026-04-04 14:30", "2026-04-04 15:00", "2026-04-04 16:00",
            "2026-04-04 17:00", "2026-04-04 18:00").map { row(utc(it), zone) }
        assertEquals(rows.drop(1), selectUpcomingForecast(rows, utc("2026-04-04 15:10"), zone.id))
    }

    private fun row(timestamp: Long, zone: TimeZone) = HourlyWeather(0, 21f, 0, timestamp, zone.id)

    private fun utc(value: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(value)!!.time
}
