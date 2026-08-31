package com.sysadmindoc.nono.runtime

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred

/** The exact self-posted notification the listener may consume instead of rejecting. */
internal data class CaptureSelfTestKey(
    val packageName: String,
    val tag: String,
    val notificationId: Int,
)

private data class PendingCaptureSelfTest(
    val key: CaptureSelfTestKey,
    val observed: CompletableDeferred<Unit>,
)

/**
 * One process-wide, one-shot handoff between the notification poster and listener service.
 *
 * The package, random tag, and fixed id must all match. A successful acknowledgement clears the
 * pending value before completing it, so a duplicate callback or another NoNo notification goes
 * through the normal self-package rejection path.
 */
internal object CaptureSelfTestProbe {
    private val pending = AtomicReference<PendingCaptureSelfTest?>(null)

    fun arm(key: CaptureSelfTestKey): CompletableDeferred<Unit>? {
        val next = PendingCaptureSelfTest(key, CompletableDeferred())
        return if (pending.compareAndSet(null, next)) next.observed else null
    }

    fun acknowledge(key: CaptureSelfTestKey): Boolean {
        val current = pending.get() ?: return false
        if (current.key != key || !pending.compareAndSet(current, null)) return false
        current.observed.complete(Unit)
        return true
    }

    fun cancel(key: CaptureSelfTestKey) {
        val current = pending.get() ?: return
        if (current.key == key && pending.compareAndSet(current, null)) {
            current.observed.cancel()
        }
    }

    fun resetForTest() {
        pending.getAndSet(null)?.observed?.cancel()
    }
}
