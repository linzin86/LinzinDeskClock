package com.linzin.linzindeskclock

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.linzin.clockcore.CityNames
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * 主動獲取最新定位（優先嘗試高時效快取，若無則在 Android 11+ 主動單次定位）
 */
private fun isValidLocation(loc: Location?): Boolean {
    if (loc == null) return false
    if (!loc.latitude.isFinite() || !loc.longitude.isFinite() ||
        loc.latitude !in -90.0..90.0 || loc.longitude !in -180.0..180.0) return false
    return kotlin.math.abs(loc.latitude) > 0.001 || kotlin.math.abs(loc.longitude) > 0.001
}

/**
 * 主動獲取最新定位（過濾 0.0 無效座標，並支援舊版系統主動喚醒定位）
 */
@SuppressLint("MissingPermission")
suspend fun fetchBestLocation(context: Context): Location? = withContext(Dispatchers.IO) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return@withContext null

    // 1. 抓取快取並過濾無效的 (0.0, 0.0) 假座標
    val candidates = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).mapNotNull { provider ->
        try {
            locationManager.getLastKnownLocation(provider)?.takeIf { isValidLocation(it) }
        } catch (_: Exception) {
            null
        }
    }
    val lastLocation = candidates.maxByOrNull { it.time }

    // 若有效快取在 10 分鐘內，直接返回以節省電量
    if (lastLocation != null && lastLocation.time > 0 &&
        (System.currentTimeMillis() - lastLocation.time) in 0 until 10 * 60 * 1000L) {
        return@withContext lastLocation
    }

    // 2. Android 11+ (API 30+) 發起主動單次定位
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            withTimeoutOrNull(5000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancellationSignal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { cancellationSignal.cancel() }

                    val provider = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        LocationManager.NETWORK_PROVIDER
                    } else {
                        LocationManager.GPS_PROVIDER
                    }

                    locationManager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        ContextCompat.getMainExecutor(context)
                    ) { loc ->
                        if (cont.isActive) {
                            cont.resume(if (isValidLocation(loc)) loc else lastLocation)
                        }
                    }
                }
            } ?: lastLocation
        } catch (_: Exception) {
            lastLocation
        }
    } else {
        // 3. Android 5.0 ~ 10：主動註冊一次性 LocationListener 喚醒定位
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { try { locationManager.isProviderEnabled(it) } catch (_: Exception) { false } }
        if (providers.isEmpty()) return@withContext lastLocation

        withTimeoutOrNull(6000L) {
            var listener: android.location.LocationListener? = null
            awaitLegacyLocation(
                providers, lastLocation,
                register = { provider, callback ->
                    if (listener == null) listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (isValidLocation(location)) callback(location)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    locationManager.requestLocationUpdates(
                        provider, 0L, 0f, listener!!, android.os.Looper.getMainLooper()
                    )
                },
                remove = { listener?.let { locationManager.removeUpdates(it) } },
                onCleanupError = { Log.w("DeskClockLocation", "Unable to remove location listener", it) }
            )
        } ?: lastLocation
    }
}

/**
 * 整合系統／歷史定位、反向地理編碼與 HTTPS 天氣；無安全 IP 服務時不使用 IP 備援
 */
internal suspend fun loadWeather(
    context: Context,
    currentLocationStr: String,
    languageTag: String = if (currentAppLocaleTag == "system") getSystemDefaultLocale().toLanguageTag() else currentAppLocaleTag,
    systemGeocode: (Double, Double) -> String? = { latitude, longitude ->
        systemCityName(context, latitude, longitude)
    }
): WeatherInfo? = withContext(Dispatchers.IO) {
    try {
        val prefs = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
        val location = fetchBestLocation(context)

        val position = if (location != null && isValidLocation(location)) {
            rememberWeatherLocation(prefs, location, System.currentTimeMillis())
        } else readWeatherLocation(prefs)

        val finalLat = position?.latitude
        val finalLon = position?.longitude
        if (finalLat == null || finalLon == null || !finalLat.isFinite() || !finalLon.isFinite() ||
            finalLat !in -90.0..90.0 || finalLon !in -180.0..180.0) {
            return@withContext null
        }

        var detectedCity = systemGeocode(finalLat, finalLon)

        // 2. 若仍無具體城市名稱，使用線上反向地理編碼取得 Level 5~8 行政區
        if (detectedCity.isNullOrBlank()) {
            detectedCity = fetchCityNameFallback(finalLat, finalLon, languageTag)
        }

        val finalCityName = detectedCity?.takeIf { it.isNotBlank() } ?: currentLocationStr
        val data = fetchOpenMeteoWeather(finalLat, finalLon, finalCityName)
        data?.copy(location = position)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("DeskClockWeather", "Failed to load weather", e)
        null
    }
}

/**
 * 線上反向地理編碼備援（嚴格限定為城市／鄉鎮區層級，排除國家與一級省州層級）
 */
private suspend fun fetchCityNameFallback(lat: Double, lon: Double, languageTag: String): String? {
    return try {
        val urlStr = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=$languageTag"
        val responseText = readHttpsJson(urlStr)
        val json = JSONObject(responseText)

        val countryName = json.optString("countryName", "").trim()
        val subdivision = json.optString("principalSubdivision", "").trim()

        val adminArray = json.optJSONObject("localityInfo")?.optJSONArray("administrative")
        val levels = IntArray(adminArray?.length() ?: 0)
        val names = arrayOfNulls<String>(levels.size)
        for (i in levels.indices) {
            val item = adminArray?.optJSONObject(i) ?: continue
            levels[i] = item.optInt("adminLevel", 0)
            names[i] = item.optString("name", "").trim()
        }
        CityNames.select(json.optString("locality", "").trim(), json.optString("city", "").trim(),
            countryName, subdivision, levels, names)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/**
 * 查詢並解析 HTTPS 天氣；傳輸失敗不降級明文
 */
suspend fun fetchOpenMeteoWeather(
    lat: Double, lon: Double, cityName: String, now: () -> Long = System::currentTimeMillis
): WeatherInfo? = withContext(Dispatchers.IO) {
    // 加上 forecast_days=2 只抓今明兩天（48小時），大幅縮小封包體積並加快回應速度
    val httpsUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&forecast_days=2&timezone=auto&timeformat=unixtime"

    try {
        val responseText = readHttpsJson(httpsUrl)

        val root = JSONObject(responseText)

        val currentObj = root.getJSONObject("current")
        val currentTemp = currentObj.getDouble("temperature_2m").toFloat()
        val currentCode = currentObj.getInt("weather_code")

        val hourlyObj = root.getJSONObject("hourly")
        val times = hourlyObj.getJSONArray("time")
        val temps = hourlyObj.getJSONArray("temperature_2m")
        val codes = hourlyObj.getJSONArray("weather_code")

        require(times.length() == temps.length() && times.length() == codes.length())
        val timeZoneId = root.getString("timezone")
        val hours = (0 until times.length()).map { index ->
            val seconds = times.getLong(index)
            require(seconds in Long.MIN_VALUE / 1000..Long.MAX_VALUE / 1000)
            val timestamp = seconds * 1000
            val hour = forecastHour(timestamp, timeZoneId)
            val temperature = temps.getDouble(index).toFloat()
            require(temperature.isFinite())
            HourlyWeather(hour, temperature, codes.getInt(index), timestamp, timeZoneId)
        }
        val hourlyResult = selectUpcomingForecast(hours, now(), timeZoneId)

        WeatherInfo(
            cityName = cityName,
            currentTempC = currentTemp,
            currentWeatherCode = currentCode,
            hourlyList = hourlyResult
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("DeskClockWeather", "Failed to fetch Open-Meteo", e)
        null
    }
}
