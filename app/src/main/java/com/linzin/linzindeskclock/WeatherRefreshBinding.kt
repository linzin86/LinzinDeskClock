package com.linzin.linzindeskclock

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable
internal fun rememberWeatherRefreshOwner(
    enabled: Boolean,
    input: WeatherRequestInput,
    permissionLevel: () -> Int,
    onPermissionNeeded: () -> Unit,
    load: suspend (WeatherRequestInput) -> WeatherInfo?,
    onResult: (WeatherInfo?) -> Unit,
    onRefreshing: (Boolean) -> Unit
): WeatherRefreshOwner {
    val scope = rememberCoroutineScope()
    val latestLoad by rememberUpdatedState(load)
    val latestResult by rememberUpdatedState(onResult)
    val latestRefreshing by rememberUpdatedState(onRefreshing)
    val latestPermission by rememberUpdatedState(permissionLevel)
    val latestPermissionRequest by rememberUpdatedState(onPermissionNeeded)
    val owner = remember(scope) {
        WeatherRefreshOwner(scope, { latestLoad(it) }, { latestResult(it) }, { latestRefreshing(it) },
            { Log.w("DeskClockWeather", "Weather refresh failed", it) })
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, enabled, input) {
        fun synchronize() {
            foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            val permission = latestPermission()
            owner.update(if (enabled && foreground && permission > 0) input.copy(permissionLevel = permission) else null)
        }
        val observer = LifecycleEventObserver { _, _ -> synchronize() }
        lifecycleOwner.lifecycle.addObserver(observer)
        synchronize()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            owner.update(null)
        }
    }
    val active = enabled && foreground
    LaunchedEffect(active, input.locationRevision) {
        if (active && latestPermission() == 0) latestPermissionRequest()
    }
    LaunchedEffect(active, input) {
        if (active) {
            while (true) {
                delay(30 * 60 * 1000L)
                owner.refresh()
            }
        }
    }
    return owner
}
