package com.linzin.linzindeskclock

import android.content.Context
import android.location.Geocoder
import java.util.Locale
import kotlinx.coroutines.CancellationException

internal fun systemCityName(context: Context, latitude: Double, longitude: Double): String? =
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val address = geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
        (address?.locality ?: address?.subAdminArea ?: address?.subLocality)
            ?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
