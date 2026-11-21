package com.linzin.linzindeskclock

import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun <T : Any> awaitLegacyLocation(
    providers: List<String>,
    fallback: T?,
    register: (String, (T) -> Unit) -> Unit,
    remove: () -> Unit,
    onCleanupError: (Exception) -> Unit
): T? = suspendCancellableCoroutine { continuation ->
    val lock = Any()
    var closed = false
    var registering = false
    var removed = false

    fun cleanupIfReady() {
        val shouldRemove = synchronized(lock) {
            if (!closed || registering || removed) false else { removed = true; true }
        }
        if (shouldRemove) {
            try { remove() } catch (error: Exception) { onCleanupError(error) }
        }
    }

    fun finish(value: T?) {
        val shouldFinish = synchronized(lock) {
            if (closed) false else { closed = true; true }
        }
        if (shouldFinish) {
            cleanupIfReady()
            if (continuation.isActive) continuation.resume(value)
        }
    }

    continuation.invokeOnCancellation {
        synchronized(lock) { closed = true }
        cleanupIfReady()
    }
    for (provider in providers) {
        val shouldRegister = synchronized(lock) {
            if (closed || !continuation.isActive) false else { registering = true; true }
        }
        if (!shouldRegister) break
        try {
            register(provider) { finish(it) }
        } catch (cancelled: CancellationException) {
            continuation.cancel(cancelled)
        } catch (_: Exception) {
            finish(fallback)
        } finally {
            // Registration can finish after cancellation; remove it before allowing another provider.
            synchronized(lock) { registering = false }
            cleanupIfReady()
        }
    }
}
