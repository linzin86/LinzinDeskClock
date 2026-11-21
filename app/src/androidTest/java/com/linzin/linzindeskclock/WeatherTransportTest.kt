package com.linzin.linzindeskclock

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.TimeZone
import java.text.SimpleDateFormat
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class WeatherTransportTest {
    companion object {
        private val requests = Collections.synchronizedList(mutableListOf<URL>())
        private var failure: Exception? = null
        private var failDuringRead = false
        private var fixtureStatusCode = 200
        private var redirect: String? = null
        private var disconnects = 0
        private var weatherResponse: ((URL) -> String)? = null

        @BeforeClass @JvmStatic
        fun interceptNetworkInThisInstrumentationProcess() {
            URL.setURLStreamHandlerFactory { protocol ->
                if (protocol !in listOf("http", "https")) null else object : URLStreamHandler() {
                    override fun openConnection(url: URL): URLConnection {
                        requests.add(url)
                        return object : HttpURLConnection(url) {
                            override fun connect() { if (!failDuringRead) failure?.let { throw it } }
                            override fun disconnect() { disconnects++ }
                            override fun usingProxy() = false
                            override fun getResponseCode(): Int { connect(); return fixtureStatusCode }
                            override fun getHeaderField(name: String) = if (name == "Location") redirect else null
                            override fun getInputStream(): ByteArrayInputStream {
                                connect()
                                failure?.let { throw it }
                                val body = when (url.host) {
                                    "ip-api.com" -> """{"status":"fail"}"""
                                    "api.bigdatacloud.net" -> """{"city":"Fixture City"}"""
                                    else -> weatherResponse?.invoke(url) ?: """{"timezone":"UTC","current":{"temperature_2m":21,"weather_code":0},"hourly":{"time":[],"temperature_2m":[],"weather_code":[]}}"""
                                }
                                return ByteArrayInputStream(body.toByteArray())
                            }
                        }
                    }
                }
            }
        }
    }

    @Before
    fun resetFixture() {
        requests.clear()
        failure = null
        failDuringRead = false
        fixtureStatusCode = 200
        redirect = null
        disconnects = 0
        weatherResponse = null
    }

    @Test
    fun httpsSuccessParsesWeather() = runBlocking {
        assertEquals(21f, fetchOpenMeteoWeather(25.0, 121.0, "Fixture City")!!.currentTempC)
        assertEquals(listOf("https"), requests.map { it.protocol })
        assertEquals(1, disconnects)
    }

    @Test
    fun certificateFailureNeverRetriesOverHttp() = runBlocking {
        failure = SSLHandshakeException("untrusted fixture certificate")
        assertNull(fetchOpenMeteoWeather(25.0, 121.0, "Fixture City"))
        assertEquals(listOf("https"), requests.map { it.protocol })
        assertEquals(1, disconnects)
    }

    @Test
    fun timeoutNeverRetriesOverHttp() = runBlocking {
        failure = SocketTimeoutException("fixture timeout")
        assertNull(fetchOpenMeteoWeather(25.0, 121.0, "Fixture City"))
        assertEquals(listOf("https"), requests.map { it.protocol })
    }

    @Test
    fun readTimeoutNeverRetriesOverHttp() = runBlocking {
        failure = SocketTimeoutException("fixture read timeout")
        failDuringRead = true
        assertNull(fetchOpenMeteoWeather(25.0, 121.0, "Fixture City"))
        assertEquals(listOf("https"), requests.map { it.protocol })
        assertEquals(1, disconnects)
    }

    @Test
    fun cancellationPropagatesWithoutRetry() = runBlocking {
        failure = CancellationException("fixture cancellation")
        try {
            fetchOpenMeteoWeather(25.0, 121.0, "Fixture City")
            fail("Cancellation must reach the owner")
        } catch (_: CancellationException) {
            assertEquals(listOf("https"), requests.map { it.protocol })
        }
    }

    @Test
    fun noSystemOrSavedLocationNeverUsesCleartextIpLookup() = runBlocking {
        fetchAndApplyWeather(contextWithNoLocation(), "Current location", systemGeocode = { _, _ -> null }) {}
        assertTrue("No request may use cleartext", requests.none { it.protocol == "http" })
    }

    @Test
    fun reverseGeocodingCertificateFailureNeverUsesHttp() = runBlocking {
        failure = SSLHandshakeException("untrusted reverse-geocoding certificate")
        fetchAndApplyWeather(contextWithNoLocation(savedLocation = true), "Current location", systemGeocode = { _, _ -> null }) {}
        assertTrue(requests.any { it.host == "api.bigdatacloud.net" })
        assertTrue("No request may use cleartext", requests.all { it.protocol == "https" })
    }

    @Test
    fun reverseGeocodingCancellationReachesOwner() = runBlocking {
        failure = CancellationException("cancelled reverse-geocoding")
        try {
            fetchAndApplyWeather(contextWithNoLocation(savedLocation = true), "Current location", systemGeocode = { _, _ -> null }) {}
            fail("Cancellation must reach the owner")
        } catch (_: CancellationException) {
            assertEquals(listOf("api.bigdatacloud.net"), requests.map { it.host })
        }
    }

    @Test
    fun redirectToCleartextIsRejected() = runBlocking {
        fixtureStatusCode = 302
        redirect = "http://example.invalid/weather"
        assertNull(fetchOpenMeteoWeather(25.0, 121.0, "Fixture City"))
        assertTrue(requests.all { it.protocol == "https" })
        assertEquals(1, disconnects)
    }

    @Test
    fun noLocationStopsAllRemoteLookups() = runBlocking {
        var replied = false
        var result: WeatherInfo? = null
        fetchAndApplyWeather(contextWithNoLocation(), "Current location", systemGeocode = { _, _ ->
            fail("Geocoding must not run without a location")
            null
        }) { replied = true; result = it }
        assertTrue(replied)
        assertNull(result)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun invalidSavedCoordinatesStopRemoteLookups() = runBlocking {
        val context = contextWithNoLocation(savedLocation = true)
        context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit()
            .putString("pref_last_valid_lat", "NaN").commit()
        var result: WeatherInfo? = null
        fetchAndApplyWeather(context, "Current location", systemGeocode = { _, _ -> null }) { result = it }
        assertNull(result)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun validSavedCoordinatesStillLoadWeather() = runBlocking {
        var result: WeatherInfo? = null
        fetchAndApplyWeather(contextWithNoLocation(savedLocation = true), "Current location",
            systemGeocode = { _, _ -> null }) { result = it }
        assertEquals("Fixture City", result!!.cityName)
        assertEquals(21f, result!!.currentTempC)
        assertEquals(25.0, result!!.location!!.latitude, 0.0)
        assertNull(result!!.location!!.fixTimeMillis)
        assertNull(result!!.location!!.provider)
        assertTrue(requests.any { it.host == "api.open-meteo.com" })
    }

    @Test
    fun savedMetadataReachesWeatherWithoutRefreshingItsFixTime() = runBlocking {
        val context = contextWithNoLocation(savedLocation = true)
        val prefs = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("pref_last_location_fix_time", "1000")
            .putString("pref_last_location_provider", "gps")
            .putString("pref_last_location_observed_at", "2000").commit()
        repeat(2) {
            var result: WeatherInfo? = null
            fetchAndApplyWeather(context, "Current location", systemGeocode = { _, _ -> null }) { result = it }
            assertEquals(1000L, result!!.location!!.fixTimeMillis)
            assertEquals("gps", result!!.location!!.provider)
            assertEquals(2000L, result!!.location!!.observedAtMillis)
        }
        assertEquals("1000", prefs.getString("pref_last_location_fix_time", null))
    }

    @Test
    fun forecastContinuesAcrossMidnightAndMonthEnd() = runBlocking {
        assertNightForecast("2026-01-30 16:00")
    }

    @Test
    fun forecastContinuesAcrossTheYearBoundary() = runBlocking {
        assertNightForecast("2026-12-30 16:00")
    }

    @Test
    fun forecastUsesTheServiceZoneInsteadOfTheDeviceZone() = runBlocking<Unit> {
        val start = utc("2026-11-15 16:00")
        val rows = (0 until 48).map { start + it * 3_600_000L }
        assertForecast(rows, start + 12 * 3_600_000L + 600_000L, "Asia/Taipei", "UTC", rows.subList(12, 16))
    }

    @Test
    fun forecastNeverPadsShortResponsesWithThePast() = runBlocking<Unit> {
        val start = utc("2026-11-15 16:00")
        val rows = listOf(start, start + 23 * 3_600_000L)
        assertForecast(rows, start + 23 * 3_600_000L + 600_000L, "Asia/Taipei", "Asia/Taipei", rows.takeLast(1))
    }

    @Test
    fun forecastSortsTheFullTimeline() = runBlocking<Unit> {
        val start = utc("2026-11-15 16:00")
        val rows = listOf(26, 23, 24, 0, 25).map { start + it * 3_600_000L }
        val expected = listOf(23, 24, 25, 26).map { start + it * 3_600_000L }
        assertForecast(rows, start + 23 * 3_600_000L + 600_000L, "Asia/Taipei", "Asia/Taipei", expected)
    }

    @Test
    fun ordinaryDaytimeForecastIsUnchanged() = runBlocking<Unit> {
        val start = utc("2026-11-15 16:00")
        val rows = (0 until 48).map { start + it * 3_600_000L }
        assertForecast(rows, start + 12 * 3_600_000L + 600_000L, "Asia/Taipei", "Asia/Taipei", rows.subList(12, 16))
    }

    @Test
    fun forecastAcceptsKyivAndPreservesTheSourceId() = runBlocking {
        val start = utc("2026-01-15 11:00")
        val rows = (0 until 6).map { start + it * 3_600_000L }
        val result = assertForecast(rows, utc("2026-01-15 12:10"), "Europe/Kyiv", "UTC",
            rows.subList(1, 5), sourceOffsetMillis = 7_200_000)
        assertEquals(listOf(14, 15, 16, 17), result.map { it.hourOfDay })
    }

    @Test
    fun forecastAcceptsNuukAndUsesItsCurrentRules() = runBlocking {
        val start = utc("2026-01-15 11:00")
        val rows = (0 until 6).map { start + it * 3_600_000L }
        val result = assertForecast(rows, utc("2026-01-15 12:10"), "America/Nuuk", "UTC",
            rows.subList(1, 5), sourceOffsetMillis = -7_200_000)
        assertEquals(listOf(10, 11, 12, 13), result.map { it.hourOfDay })
    }

    @Test
    fun malformedForecastDataRemainsUnavailable() = runBlocking {
        val valid = """{"timezone":"UTC","current":{"temperature_2m":21,"weather_code":0},"hourly":{"time":[0],"temperature_2m":[21],"weather_code":[0]}}"""
        val invalid = listOf(
            JSONObject(valid).put("timezone", "Invalid/Fixture"),
            JSONObject(valid).apply {
                getJSONObject("hourly").put("time", JSONArray().put(Long.MAX_VALUE))
            },
            JSONObject(valid).apply {
                getJSONObject("hourly").put("temperature_2m", JSONArray())
            },
            JSONObject(valid).apply {
                getJSONObject("hourly").put("temperature_2m", JSONArray().put("NaN"))
            }
        )
        invalid.forEachIndexed { index, body ->
            weatherResponse = { body.toString() }
            assertNull("Malformed fixture $index", fetchOpenMeteoWeather(25.0, 121.0, "Fixture City"))
        }
    }

    @Test
    fun forecastPreservesBothRepeatedDstHours() = runBlocking {
        val start = utc("2026-11-01 04:00")
        val rows = (0 until 6).map { start + it * 3_600_000L }
        val result = assertForecast(rows, utc("2026-11-01 05:10"), "America/New_York", "America/New_York", rows.subList(1, 5))
        assertEquals(listOf(1, 1, 2, 3), result.map { it.hourOfDay })
    }

    @Test
    fun forecastSkipsTheMissingDstHour() = runBlocking {
        val start = utc("2026-03-08 05:00")
        val rows = (0 until 6).map { start + it * 3_600_000L }
        val result = assertForecast(rows, utc("2026-03-08 06:10"), "America/New_York", "America/New_York", rows.subList(1, 5))
        assertEquals(listOf(1, 3, 4, 5), result.map { it.hourOfDay })
    }

    private suspend fun assertNightForecast(startUtc: String) {
        val start = utc(startUtc)
        val rows = (0 until 48).map { start + it * 3_600_000L }
        assertForecast(rows, start + 23 * 3_600_000L + 600_000L, "Asia/Taipei", "Asia/Taipei", rows.subList(23, 27))
    }

    private suspend fun assertForecast(
        rows: List<Long>, nowMillis: Long, serviceZone: String, deviceZone: String, expected: List<Long>,
        sourceOffsetMillis: Int? = null
    ): List<HourlyWeather> {
        val previousZone = TimeZone.getDefault()
        val sourceZone = TimeZone.getTimeZone(serviceZone)
        val sourceOffset = sourceOffsetMillis ?: sourceZone.getOffset(nowMillis)
        TimeZone.setDefault(TimeZone.getTimeZone(deviceZone))
        try {
            weatherResponse = { url ->
                // ISO responses use the request-time fixed offset; Unix responses preserve instants.
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
                    timeZone = SimpleTimeZone(sourceOffset, "response offset")
                }
                val times = if (url.query.contains("timeformat=unixtime")) {
                    JSONArray(rows.map { it / 1000 })
                } else {
                    JSONArray(rows.map { format.format(Date(it)) })
                }
                JSONObject().put("timezone", serviceZone)
                    .put("utc_offset_seconds", sourceOffset / 1000)
                    .put("current", JSONObject().put("temperature_2m", 21).put("weather_code", 0))
                    .put("hourly", JSONObject().put("time", times)
                        .put("temperature_2m", JSONArray(rows.mapIndexed { index, _ -> index + 10 }))
                        .put("weather_code", JSONArray(rows.map { 0 }))).toString()
            }
            val weather = fetchOpenMeteoWeather(25.0, 121.0, "Fixture City", now = { nowMillis })
            assertNotNull("Valid source zone $serviceZone must load", weather)
            val result = weather!!.hourlyList
            assertEquals("Forecast must preserve the ordered instants", expected, result.map { it.timestampMillis })
            assertEquals("Values must stay attached to their source instants",
                expected.map { rows.indexOf(it) + 10f }, result.map { it.tempC })
            assertTrue("Forecast must preserve its source zone", result.all { it.timeZoneId == serviceZone })
            return result
        } finally {
            TimeZone.setDefault(previousZone)
        }
    }

    private fun utc(value: String): Long = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(value)!!.time

    private fun contextWithNoLocation(savedLocation: Boolean = false): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        target.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        if (savedLocation) {
            target.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit()
                .putString("pref_last_valid_lat", "25.0")
                .putString("pref_last_valid_lon", "121.0").commit()
        }
        return object : ContextWrapper(target) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.LOCATION_SERVICE) null else super.getSystemService(name)
        }
    }
}
