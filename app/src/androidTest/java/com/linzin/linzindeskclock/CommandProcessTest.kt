package com.linzin.linzindeskclock

import android.os.Build
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommandProcessTest {
    private fun exited(process: Process): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 1000
        do {
            try { process.exitValue(); return true } catch (_: IllegalThreadStateException) { Thread.sleep(10) }
        } while (SystemClock.elapsedRealtime() < deadline)
        return false
    }

    @Test fun realPipesAreDrainedAfterTheCaptureLimit() = runBlocking {
        val script = "i=0; while [ ${'$'}i -lt 4000 ]; do echo 01234567890123456789; i=${'$'}((i + 1)); done"
        val result = runBoundedCommand(listOf("/system/bin/sh", "-c", script))
        assertTrue(result.succeeded)
        assertTrue(result.truncated)
        assertEquals(8192, result.output.toByteArray().size)
    }

    @Test fun stderrAndNonzeroExitAreReported() = runBlocking {
        val result = runBoundedCommand(listOf("/system/bin/sh", "-c", "echo denied >&2; exit 7"))
        assertEquals(7, result.exitCode)
        assertFalse(result.succeeded)
        assertTrue(result.output.contains("denied"))
    }

    @Test fun aRealHungProcessIsKilledOnTimeout() = runBlocking {
        var process: Process? = null
        val before = SystemClock.elapsedRealtime()
        val result = runBoundedCommand(listOf("/system/bin/sh", "-c", "while :; do :; done"), 100,
            start = { ProcessBuilder(it).redirectErrorStream(true).start().also { child -> process = child } })
        assertTrue(result.timedOut)
        assertTrue(exited(process!!))
        assertTrue("Timeout must remain bounded", SystemClock.elapsedRealtime() - before < 3000)
    }

    @Test fun aRealHungProcessIsKilledOnCancellation() = runBlocking {
        val started = CountDownLatch(1)
        var process: Process? = null
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            runBoundedCommand(listOf("/system/bin/sh", "-c", "while :; do :; done"), start = {
                ProcessBuilder(it).redirectErrorStream(true).start().also { child -> process = child; started.countDown() }
            })
        }
        assertTrue(started.await(1, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(exited(process!!))
    }

    @Test fun unavailableDeviceStateCommandFailsWithoutAffectingOrdinaryMode() = runBlocking {
        val result = runBoundedCommand(listOf("cmd", "device_state", "state"))
        if (Build.VERSION.SDK_INT >= 31) {
            assertTrue(result.succeeded)
            assertTrue(result.output.startsWith("Committed state:"))
        } else assertFalse(result.succeeded)
    }
}
