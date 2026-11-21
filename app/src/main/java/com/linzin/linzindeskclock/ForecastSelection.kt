package com.linzin.linzindeskclock

import com.linzin.clockcore.ForecastTimeline

internal fun forecastHour(timestampMillis: Long, timeZoneId: String): Int =
    ForecastTimeline.hour(timestampMillis, timeZoneId)

internal fun selectUpcomingForecast(
    hours: List<HourlyWeather>, nowMillis: Long, timeZoneId: String
): List<HourlyWeather> = ForecastTimeline.upcomingIndices(
    hours.map { it.timestampMillis }.toLongArray(), nowMillis, timeZoneId
).map { hours[it] }
