package com.linzin.linzindeskclock

data class HourlyWeather(
    val hourOfDay: Int,
    val tempC: Float,
    val weatherCode: Int,
    val timestampMillis: Long,
    val timeZoneId: String
)

data class WeatherInfo(
    val cityName: String,
    val currentTempC: Float,
    val currentWeatherCode: Int,
    val hourlyList: List<HourlyWeather>,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
    val isStale: Boolean = false,
    val location: WeatherLocation? = null
)

internal fun weatherAfterRefresh(previous: WeatherInfo?, incoming: WeatherInfo?): WeatherInfo? =
    incoming ?: previous?.copy(isStale = true)
