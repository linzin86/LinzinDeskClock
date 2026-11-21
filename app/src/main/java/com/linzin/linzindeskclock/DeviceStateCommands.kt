package com.linzin.linzindeskclock

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private data class ObservedDeviceState(val committed: String, val override: String?)

private fun observedDeviceState(result: CommandResult): ObservedDeviceState? {
    if (!result.succeeded || result.truncated) return null
    val pattern = Regex("^(Committed|Base|Override) state: DeviceState\\{identifier=(\\d+)(?:,[^\\r\\n]*)?\\}$")
    val entries = result.output.lineSequence().map { it.trimEnd('\r') }
        .filter { it.isNotBlank() && !it.all { character -> character == '-' } }
        .toList().map { line -> pattern.matchEntire(line)?.groupValues ?: return null }
    if (entries.map { it[1] }.distinct().size != entries.size) return null
    val states = entries.associate { it[1] to it[2] }
    val committed = states["Committed"] ?: return null
    if ((states["Base"] == null) != (states["Override"] == null)) return null
    return ObservedDeviceState(committed, states["Override"])
}

private suspend fun awaitDeviceState(
    timeoutMillis: Long,
    run: suspend (List<String>) -> CommandResult,
    matches: (ObservedDeviceState) -> Boolean
): Boolean = withTimeoutOrNull(timeoutMillis) {
    var confirmed = false
    while (!confirmed) {
        confirmed = observedDeviceState(run(listOf("cmd", "device_state", "state")))?.let(matches) == true
        if (!confirmed) delay(50)
    }
    true
} ?: false

internal suspend fun switchAndConfirmDeviceState(
    target: String,
    repeatReset: Boolean = false,
    confirmationTimeoutMillis: Long = 2000,
    run: suspend (List<String>) -> CommandResult = { runBoundedCommand(it) },
    onResetFailure: () -> Unit = {}
): Boolean {
    var confirmed = false
    try {
        if (run(listOf("cmd", "device_state", "state", target)).succeeded) {
            confirmed = awaitDeviceState(confirmationTimeoutMillis, run) {
                it.committed == target && it.override == target
            }
        }
        return confirmed
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        return false
    } finally {
        // A cancelled or unconfirmed command may already have changed the global display state.
        if (!confirmed) withContext(NonCancellable) {
            if (!resetAndConfirmDeviceState(repeatReset, confirmationTimeoutMillis, run)) onResetFailure()
        }
    }
}

internal suspend fun resetAndConfirmDeviceState(
    repeatReset: Boolean = false,
    confirmationTimeoutMillis: Long = 2000,
    run: suspend (List<String>) -> CommandResult = { runBoundedCommand(it) }
): Boolean = try {
    var succeeded = run(listOf("cmd", "device_state", "state", "reset")).succeeded
    if (repeatReset) {
        delay(120)
        succeeded = run(listOf("cmd", "device_state", "state", "reset")).succeeded && succeeded
    }
    succeeded && awaitDeviceState(confirmationTimeoutMillis, run) { it.override == null }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}
