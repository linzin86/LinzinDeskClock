package com.linzin.linzindeskclock

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal suspend fun readHttpsJson(address: String, timeoutMillis: Int = 6000): String =
    withContext(Dispatchers.IO) {
        ensureActive()
        val url = URL(address)
        require(url.protocol == "https") { "HTTPS is required" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            useCaches = false
            // Reject redirects rather than allowing a server to change the transport policy.
            instanceFollowRedirects = false
            setRequestProperty("Connection", "close")
            setRequestProperty("User-Agent", "LinzinDeskClock-Android")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Unexpected response status: $status")
            connection.inputStream.bufferedReader().use { it.readText() }.also { ensureActive() }
        } finally {
            connection.disconnect()
        }
    }
