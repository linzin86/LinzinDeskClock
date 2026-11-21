package com.linzin.linzindeskclock

import android.content.SharedPreferences
import android.location.Location
import com.linzin.clockcore.LocationAge

data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val fixTimeMillis: Long? = null,
    val provider: String? = null,
    val observedAtMillis: Long? = null
) {
    fun knownFixTime(nowMillis: Long): Long? = LocationAge.knownFixTime(fixTimeMillis, observedAtMillis, nowMillis)
}

internal fun readWeatherLocation(prefs: SharedPreferences): WeatherLocation? {
    val values = prefs.all
    val lat = (values["pref_last_valid_lat"] as? String)?.toDoubleOrNull() ?: return null
    val lon = (values["pref_last_valid_lon"] as? String)?.toDoubleOrNull() ?: return null
    if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return WeatherLocation(
        lat, lon,
        (values["pref_last_location_fix_time"] as? String)?.toLongOrNull()?.takeIf { it > 0 },
        (values["pref_last_location_provider"] as? String)?.takeIf { it.isNotBlank() },
        (values["pref_last_location_observed_at"] as? String)?.toLongOrNull()?.takeIf { it > 0 }
    )
}

internal fun rememberWeatherLocation(
    prefs: SharedPreferences, location: Location, observedAtMillis: Long
): WeatherLocation {
    val record = WeatherLocation(location.latitude, location.longitude,
        location.time.takeIf { it > 0 }, location.provider, observedAtMillis)
    prefs.edit()
        .putString("pref_last_valid_lat", location.latitude.toString())
        .putString("pref_last_valid_lon", location.longitude.toString())
        .putString("pref_last_location_fix_time", record.fixTimeMillis?.toString())
        .putString("pref_last_location_provider", record.provider)
        .putString("pref_last_location_observed_at", record.observedAtMillis?.toString())
        .apply()
    return record
}
