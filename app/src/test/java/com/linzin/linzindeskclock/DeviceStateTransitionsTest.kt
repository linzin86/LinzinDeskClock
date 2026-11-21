package com.linzin.linzindeskclock

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class DeviceStateTransitionsTest {
    @Test fun oldActivityCleanupMustFinishBeforeTheNewActivityCanSwitch() = runBlocking {
        val cleanup = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val oldOwner = Any()
        val newOwner = Any()
        val old = async(start = CoroutineStart.UNDISPATCHED) {
            DeviceStateTransitions.request(oldOwner) {
                withContext(NonCancellable) {
                    cleanup.complete(Unit)
                    releaseCleanup.await()
                    events.add("old reset")
                }
                false
            }
        }
        cleanup.await()
        val next = async(start = CoroutineStart.UNDISPATCHED) {
            DeviceStateTransitions.request(newOwner) { events.add("new switch"); true }
        }
        try {
            assertTrue("New override must wait for old compensation", events.isEmpty())
            releaseCleanup.complete(Unit)
            old.await(); next.await()
            assertEquals(listOf("old reset", "new switch"), events)
        } finally {
            releaseCleanup.complete(Unit)
            old.await(); next.await()
            DeviceStateTransitions.release(newOwner) { true }
        }
    }

    @Test fun lateOldActivityReleaseCannotResetANewerOwner() = runBlocking {
        val oldOwner = Any()
        val newOwner = Any()
        var resets = 0
        DeviceStateTransitions.request(oldOwner) { true }
        DeviceStateTransitions.request(newOwner) { true }
        try {
            assertTrue(DeviceStateTransitions.release(oldOwner) { resets++; true })
            assertEquals(0, resets)
            assertTrue(DeviceStateTransitions.release(newOwner) { resets++; true })
            assertEquals(1, resets)
        } finally { DeviceStateTransitions.release(newOwner) { true } }
    }
}
