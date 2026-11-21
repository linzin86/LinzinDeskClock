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
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class WeatherTransportTest {
    companion object {
        private val requests = Collections.synchronizedList(mutableListOf<URL>())
        private var failure: Exception? = null
        private var failDuringRead = false
        private var fixtureStatusCode = 200
        private var redirect: String? = null
        private var disconnects = 0

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
                                    else -> """{"current":{"temperature_2m":21,"weather_code":0},"hourly":{"time":[],"temperature_2m":[],"weather_code":[]}}"""
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
