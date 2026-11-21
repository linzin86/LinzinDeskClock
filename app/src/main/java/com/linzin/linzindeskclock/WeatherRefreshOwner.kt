package com.linzin.linzindeskclock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class WeatherRequestInput(
    val languageTag: String,
    val locationLabel: String,
    val locationRevision: Int = 0,
    val permissionLevel: Int = 0
)

internal class WeatherRefreshOwner(
    private val scope: CoroutineScope,
    private val load: suspend (WeatherRequestInput) -> WeatherInfo?,
    private val onResult: (WeatherInfo?) -> Unit,
    private val onRefreshing: (Boolean) -> Unit,
    private val onError: (Exception) -> Unit = {}
) {
    private var input: WeatherRequestInput? = null
    private var job: Job? = null
    private var generation = 0L

    fun update(input: WeatherRequestInput?) {
        if (this.input == input) return
        generation++
        job?.cancel()
        job = null
        this.input = input
        if (input != null) refresh() else onRefreshing(false)
    }

    fun refresh() {
        val request = input ?: return
        if (!scope.isActive || job?.isActive == true) return
        val ticket = ++generation
        onRefreshing(true)
        job = scope.launch {
            try {
                val incoming = try { load(request) } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    coroutineContext.ensureActive()
                    if (ticket == generation) onError(error)
                    null
                }
                coroutineContext.ensureActive()
                // A cancelled platform request may still return; only the current generation may publish.
                if (ticket == generation) onResult(incoming)
            } finally {
                if (ticket == generation) onRefreshing(false)
            }
        }
    }
}
