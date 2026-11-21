package com.linzin.linzindeskclock

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BoundedCommandTest {
    private open class FakeProcess(body: String = "", private val code: Int = 0, private val hangs: Boolean = false) : Process() {
        val destroyed = AtomicBoolean(false)
        val started = CountDownLatch(1)
        val outputClosed = AtomicBoolean(false)
        val input = object : ByteArrayInputStream(body.toByteArray()) {
            override fun close() { outputClosed.set(true); super.close() }
        }
        val stdin = ByteArrayOutputStream()
        override fun getInputStream() = input
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream() = stdin
        override fun waitFor(): Int { started.countDown(); if (hangs) Thread.sleep(100); return code }
        override fun exitValue(): Int {
            started.countDown()
            if (hangs && !destroyed.get()) throw IllegalThreadStateException()
            return code
        }
        override fun destroy() { destroyed.set(true) }
    }

    @Test fun successCapturesOutputAndReleasesTheProcess() = runBlocking {
        val process = FakeProcess("confirmed\n")
        val result = runBoundedCommand(listOf("fixture"), start = { process })
        assertTrue(result.succeeded)
        assertEquals("confirmed\n", result.output)
        assertTrue(process.destroyed.get())
        assertTrue(process.outputClosed.get())
    }

    @Test fun nonzeroExitIsNotSuccess() = runBlocking {
        val result = runBoundedCommand(listOf("fixture"), start = { FakeProcess("denied", 7) })
        assertFalse(result.succeeded)
        assertEquals(7, result.exitCode)
        assertEquals("denied", result.output)
    }

    @Test fun timeoutDestroysAndClosesTheProcess() = runBlocking {
        val process = FakeProcess(hangs = true)
        val result = runBoundedCommand(listOf("fixture"), timeoutMillis = 25, start = { process })
        assertTrue(result.timedOut)
        assertFalse(result.succeeded)
        assertTrue(process.destroyed.get())
        assertTrue(process.outputClosed.get())
    }

    @Test fun cancellationDestroysTheProcessWithoutReturningSuccess() = runBlocking {
        val process = FakeProcess(hangs = true)
        var returned = false
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            runBoundedCommand(listOf("fixture"), start = { process })
            returned = true
        }
        assertTrue(process.started.await(1, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertFalse(returned)
        assertTrue(process.destroyed.get())
        assertTrue(process.outputClosed.get())
    }

    @Test fun oversizedOutputIsDrainedButOnlyABoundedPrefixIsRetained() = runBlocking {
        val process = FakeProcess("x".repeat(100_000))
        val result = runBoundedCommand(listOf("fixture"), start = { process })
        assertTrue(result.succeeded)
        assertTrue(result.truncated)
        assertEquals(8192, result.output.length)
        assertEquals(0, process.input.available())
    }

    @Test fun missingExecutableIsAFailureResult() = runBlocking {
        val result = runBoundedCommand(listOf("missing"), start = { throw java.io.IOException("not found") })
        assertFalse(result.succeeded)
        assertNotNull(result.error)
    }

    @Test fun cleanupFailureCannotReplaceCancellationOrSkipStreamClosure() = runBlocking {
        val process = object : FakeProcess(hangs = true) {
            override fun destroy() { throw SecurityException("cleanup fixture") }
        }
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            runBoundedCommand(listOf("fixture"), start = { process })
        }
        assertTrue(process.started.await(1, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(process.outputClosed.get())
    }
}
