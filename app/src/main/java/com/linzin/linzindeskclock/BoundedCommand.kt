package com.linzin.linzindeskclock

import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class CommandResult(
    val exitCode: Int?,
    val output: String = "",
    val timedOut: Boolean = false,
    val truncated: Boolean = false,
    val error: String? = null
) {
    val succeeded: Boolean get() = exitCode == 0 && !timedOut && error == null
}

internal suspend fun runBoundedCommand(
    command: List<String>,
    timeoutMillis: Long = 2000,
    start: (List<String>) -> Process = { ProcessBuilder(it).redirectErrorStream(true).start() }
): CommandResult = withContext(Dispatchers.IO) {
    ensureActive()
    val process = try { start(command) } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        return@withContext CommandResult(null, error = "Unable to start command")
    }
    val capture = ByteArrayOutputStream()
    val done = AtomicBoolean(false)
    val readFailed = AtomicBoolean(false)
    val truncated = AtomicBoolean(false)
    val drainer = Thread({
        try {
            process.inputStream.use { stream ->
                val bytes = ByteArray(1024)
                while (true) {
                    val count = stream.read(bytes)
                    if (count < 0) break
                    synchronized(capture) {
                        val retained = minOf(count, 8192 - capture.size())
                        capture.write(bytes, 0, retained)
                        if (retained < count) truncated.set(true)
                    }
                }
            }
        } catch (_: Exception) { readFailed.set(true) }
        finally { done.set(true) }
    }, "desk-clock-command-output").apply { isDaemon = true }
    try {
        drainer.start()
        val code = withTimeoutOrNull(timeoutMillis) {
            var exit: Int? = null
            while (exit == null || !done.get()) {
                if (exit == null) exit = try { process.exitValue() } catch (_: IllegalThreadStateException) { null }
                if (exit == null || !done.get()) delay(10)
            }
            exit
        }
        CommandResult(code, synchronized(capture) { capture.toString("UTF-8") },
            timedOut = code == null, truncated = truncated.get(),
            error = if (readFailed.get()) "Unable to read command output" else null)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        CommandResult(null, error = "Unable to execute command")
    } finally {
        try { process.destroy() } catch (_: Exception) { }
        try { process.inputStream.close() } catch (_: Exception) { }
        try { process.errorStream.close() } catch (_: Exception) { }
        try { process.outputStream.close() } catch (_: Exception) { }
        try { drainer.join(100) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }
}
