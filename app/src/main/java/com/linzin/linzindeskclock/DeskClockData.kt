package com.linzin.linzindeskclock

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

const val PREFS_NAME = "desk_clock_prefs"
const val PREF_BYPASS_COUNT = "pref_non_fold_bypass_count"

/** 非摺疊機同意放行達此次數後，視為永久放行 */
const val PERMANENT_BYPASS_COUNT = 3

enum class ClockLayoutMode {
    SINGLE,
    DUAL_LEFT_LARGE,
    DUAL_RIGHT_LARGE
}

data class HourlyWeather(
    val timeLabel: String,
    val tempC: Float,
    val weatherCode: Int
)

data class WeatherInfo(
    val cityName: String,
    val currentTempC: Float,
    val currentWeatherCode: Int,
    val hourlyList: List<HourlyWeather>
)

data class BatteryState(
    val level: Int = 100,
    val isCharging: Boolean = false
)

data class UpcomingCalendarEvent(
    val title: String,
    val timeLabel: String
)

/**
 * 查詢未來 48 小時內的日曆行程（自動過濾已結束的事件與過去的全天行程）
 */
suspend fun fetchUpcomingCalendarEvents(context: Context): List<UpcomingCalendarEvent> = withContext(Dispatchers.IO) {
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission) return@withContext emptyList()

    val eventList = mutableListOf<UpcomingCalendarEvent>()
    val now = System.currentTimeMillis()
    val endWindow = now + (48 * 60 * 60 * 1000L)

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, now)
    ContentUris.appendId(builder, endWindow)

    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY
    )
    val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

    try {
        context.contentResolver.query(
            builder.build(),
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)

            val timeFormat = SimpleDateFormat("H:mm", Locale.US)
            val dayFormat = SimpleDateFormat("M/d", Locale.US)

            val nowCal = Calendar.getInstance().apply { timeInMillis = now }
            val currentYear = nowCal.get(Calendar.YEAR)
            val currentDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR)

            while (cursor.moveToNext() && eventList.size < 3) {
                val title = cursor.getString(titleIdx) ?: context.getString(R.string.event_untitled)
                val beginMillis = cursor.getLong(beginIdx)
                val endMillis = cursor.getLong(endIdx)
                val isAllDay = cursor.getInt(allDayIdx) == 1

                if (isAllDay) {
                    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = beginMillis
                    }
                    val eventYear = utcCal.get(Calendar.YEAR)
                    val eventDayOfYear = utcCal.get(Calendar.DAY_OF_YEAR)

                    if (eventYear < currentYear || (eventYear == currentYear && eventDayOfYear < currentDayOfYear)) {
                        continue
                    }

                    val isToday = (eventYear == currentYear && eventDayOfYear == currentDayOfYear)
                    val label = if (isToday) {
                        context.getString(R.string.event_all_day_today)
                    } else {
                        dayFormat.timeZone = TimeZone.getTimeZone("UTC")
                        "${dayFormat.format(Date(beginMillis))} ${context.getString(R.string.event_all_day)}"
                    }

                    eventList.add(UpcomingCalendarEvent(title = title, timeLabel = label))
                } else {
                    if (endMillis <= now) {
                        continue
                    }

                    val eventCal = Calendar.getInstance().apply { timeInMillis = beginMillis }
                    val isToday = (eventCal.get(Calendar.DAY_OF_YEAR) == currentDayOfYear &&
                            eventCal.get(Calendar.YEAR) == currentYear)

                    dayFormat.timeZone = TimeZone.getDefault()
                    timeFormat.timeZone = TimeZone.getDefault()

                    val label = if (isToday) {
                        timeFormat.format(Date(beginMillis))
                    } else {
                        "${dayFormat.format(Date(beginMillis))} ${timeFormat.format(Date(beginMillis))}"
                    }

                    eventList.add(UpcomingCalendarEvent(title = title, timeLabel = label))
                }
            }
        }
    } catch (e: Exception) {
        Log.e("DeskClockCalendar", "Error querying calendar instances", e)
    }

    eventList
}

/**
 * 查詢 Open-Meteo 天氣預報 API 並解析
 */
suspend fun fetchOpenMeteoWeather(lat: Double, lon: Double, cityName: String): WeatherInfo? = withContext(Dispatchers.IO) {
    try {
        // unixtime keeps hourly slots comparable with the device clock, whatever time zone the location is in
        val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&timezone=auto&timeformat=unixtime"
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000

        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(responseText)

        val currentObj = root.getJSONObject("current")
        val currentTemp = currentObj.getDouble("temperature_2m").toFloat()
        val currentCode = currentObj.getInt("weather_code")

        val hourlyObj = root.getJSONObject("hourly")
        val times = hourlyObj.getJSONArray("time")
        val temps = hourlyObj.getJSONArray("temperature_2m")
        val codes = hourlyObj.getJSONArray("weather_code")

        WeatherInfo(
            cityName = cityName,
            currentTempC = currentTemp,
            currentWeatherCode = currentCode,
            hourlyList = pickUpcomingHours(
                slotStartsSec = List(times.length()) { times.getLong(it) },
                tempsC = List(temps.length()) { temps.getDouble(it).toFloat() },
                weatherCodes = List(codes.length()) { codes.getInt(it) },
                nowMillis = System.currentTimeMillis()
            )
        )
    } catch (e: Exception) {
        Log.e("DeskClockWeather", "Failed to fetch Open-Meteo", e)
        null
    }
}

/**
 * 從逐時預報中取出「目前這一小時」起的連續 [count] 筆
 */
internal fun pickUpcomingHours(
    slotStartsSec: List<Long>,
    tempsC: List<Float>,
    weatherCodes: List<Int>,
    nowMillis: Long,
    count: Int = 4
): List<HourlyWeather> {
    val labelFormat = SimpleDateFormat("ha", Locale.US)
    val firstIdx = slotStartsSec.indexOfFirst { it * 1000L + 3_600_000L > nowMillis }
    if (firstIdx < 0) return emptyList()
    return (firstIdx until minOf(firstIdx + count, slotStartsSec.size)).map { i ->
        HourlyWeather(
            timeLabel = labelFormat.format(Date(slotStartsSec[i] * 1000L)).uppercase(Locale.US),
            tempC = tempsC[i],
            weatherCode = weatherCodes[i]
        )
    }
}

/**
 * 取得目前位置：先用系統快取的最後位置，沒有時再主動定位一次；都失敗則回傳 null
 */
suspend fun findCurrentLocation(context: Context): Location? {
    val locationManager = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(
        LocationManager.FUSED_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER
    ).filter { locationManager.isProviderEnabled(it) }

    try {
        providers.firstNotNullOfOrNull { locationManager.getLastKnownLocation(it) }?.let { return it }
        val provider = providers.firstOrNull() ?: return null
        return suspendCancellableCoroutine { cont ->
            val cancelSignal = CancellationSignal()
            cont.invokeOnCancellation { cancelSignal.cancel() }
            locationManager.getCurrentLocation(provider, cancelSignal, context.mainExecutor) { location ->
                cont.resume(location)
            }
        }
    } catch (e: SecurityException) {
        Log.e("DeskClockWeather", "Location permission missing", e)
        return null
    }
}

fun formatTemp(celsius: Float, isFahrenheit: Boolean): String {
    val temp = if (isFahrenheit) (celsius * 9f / 5f + 32f).roundToInt() else celsius.roundToInt()
    return "$temp°"
}
