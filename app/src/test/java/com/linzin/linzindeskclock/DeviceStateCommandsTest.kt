package com.linzin.linzindeskclock

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DeviceStateCommandsTest {
    private fun state(committed: Int = 0, override: Int? = null) = CommandResult(0,
        "Committed state: DeviceState{identifier=$committed, name='FIXTURE'}\n" +
            (override?.let { "Base state: DeviceState{identifier=0, name='BASE'}\n" +
                "Override state: DeviceState{identifier=$it, name='FIXTURE'}\n" } ?: ""))

    @Test fun successfulExitWithoutObservedOverrideIsNotActive() = runBlocking {
        var resets = 0
        assertFalse(switchAndConfirmDeviceState("3", confirmationTimeoutMillis = 30, run = { command ->
            if (command.last() == "reset") resets++
            if (command.size == 3) state() else CommandResult(0)
        }))
        assertEquals(1, resets)
    }

    @Test fun activeRequiresBothCommittedAndOverrideToMatch() = runBlocking {
        var queries = 0
        assertTrue(switchAndConfirmDeviceState("3", run = { command ->
            if (command.size == 3) { queries++; state(3, 3) } else CommandResult(0)
        }))
        assertEquals(1, queries)
    }

    @Test fun nonzeroSwitchFailsAndAttemptsRestoration() = runBlocking {
        var resets = 0
        assertFalse(switchAndConfirmDeviceState("3", run = { command ->
            when {
                command.last() == "3" -> CommandResult(1, "permission denied")
                command.last() == "reset" -> { resets++; CommandResult(0) }
                else -> state()
            }
        }))
        assertEquals(1, resets)
    }

    @Test fun resetExitZeroWithoutClearedOverrideIsNotSuccess() = runBlocking {
        assertFalse(resetAndConfirmDeviceState(confirmationTimeoutMillis = 30, run = { command ->
            if (command.size == 3) state(3, 3) else CommandResult(0)
        }))
        assertFalse(resetAndConfirmDeviceState(run = { CommandResult(7) }))
    }

    @Test fun honorResetRepeatsBeforeVerifying() = runBlocking {
        val commands = mutableListOf<List<String>>()
        assertTrue(resetAndConfirmDeviceState(repeatReset = true, run = { command ->
            commands.add(command)
            if (command.size == 3) state() else CommandResult(0)
        }))
        assertEquals(listOf("reset", "reset", "state"), commands.map { it.last() })
    }

    @Test fun partialOrUnexpectedStateOutputCannotConfirmRestoration() = runBlocking {
        for (suffix in listOf("Base state: DeviceState{identifier=0, name='BASE'}\n", "unknown extra state data\n")) {
            assertFalse(resetAndConfirmDeviceState(confirmationTimeoutMillis = 25, run = { command ->
                if (command.size == 3) state().copy(output = state().output + suffix) else CommandResult(0)
            }))
        }
    }

    @Test fun anOverrideWithoutItsBaseCannotConfirmSwitching() = runBlocking {
        val incomplete = state(3, 3).copy(output = state(3, 3).output.lineSequence()
            .filterNot { it.startsWith("Base state:") }.joinToString("\n"))
        assertFalse(switchAndConfirmDeviceState("3", confirmationTimeoutMillis = 25,
            run = { command -> if (command.size == 3) incomplete else CommandResult(0) }))
    }

    @Test fun cancelledSwitchRestoresBeforeItFinishes() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var resets = 0
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            switchAndConfirmDeviceState("3", run = { command ->
                when {
                    command.last() == "3" -> { entered.complete(Unit); awaitCancellation() }
                    command.last() == "reset" -> { resets++; CommandResult(0) }
                    else -> state()
                }
            })
        }
        entered.await()
        job.cancelAndJoin()
        assertEquals(1, resets)
    }

    @Test fun truncatedAndUnrecognizedResponsesCannotConfirmSuccess() = runBlocking {
        for (response in listOf(state(3, 3).copy(truncated = true), CommandResult(0, "unknown OEM format"))) {
            var resetFailed = false
            assertFalse(switchAndConfirmDeviceState("3", confirmationTimeoutMillis = 25,
                run = { command -> if (command.size == 3) response else CommandResult(0) },
                onResetFailure = { resetFailed = true }))
            assertTrue(resetFailed)
        }
    }
}
