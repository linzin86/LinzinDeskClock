package com.linzin.linzindeskclock

import org.threeten.bp.Instant
import org.threeten.bp.ZoneId

internal fun forecastHour(timestampMillis: Long, timeZoneId: String): Int =
    Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.of(timeZoneId)).hour

internal fun selectUpcomingForecast(
    hours: List<HourlyWeather>, nowMillis: Long, timeZoneId: String
): List<HourlyWeather> {
    val zone = ZoneId.of(timeZoneId)
    val instant = Instant.ofEpochMilli(nowMillis)
    val current = instant.atZone(zone)
    val floor = current.withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
    // A partial DST hour begins at the transition, including a half-hour repeated segment.
    val transition = zone.rules.previousTransition(instant.plusNanos(1))?.instant?.toEpochMilli()
    val hourStart = maxOf(floor, transition ?: Long.MIN_VALUE)
    return hours.filter { it.timestampMillis >= hourStart }.sortedBy { it.timestampMillis }.take(4)
}
