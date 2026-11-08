package com.frameglass.data.meta

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Takes the lock if it comes free within [timeoutMs], and says whether it did. A caller cancelled after lock() resumed
 * but before withTimeoutOrNull returned would otherwise keep the lock forever, so that path gives it back.
 * [afterLock] lets a test cancel the caller at exactly that point.
 */
internal suspend fun Mutex.lockWithin(timeoutMs: Long, afterLock: () -> Unit = {}): Boolean {
    var locked = false
    try {
        withTimeoutOrNull(timeoutMs) {
            lock()
            locked = true
            afterLock()
        }
    } catch (e: CancellationException) {
        if (locked) unlock()
        throw e
    }
    return locked
}
