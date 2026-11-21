package com.linzin.linzindeskclock

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object DeviceStateTransitions {
    private val mutex = Mutex()
    private var currentOwner: Any? = null

    suspend fun request(owner: Any, action: suspend () -> Boolean): Boolean = mutex.withLock {
        currentOwner = owner
        action()
    }

    suspend fun release(owner: Any, action: suspend () -> Boolean): Boolean = mutex.withLock {
        if (currentOwner !== owner) true else action().also { released ->
            if (released) currentOwner = null
        }
    }
}
