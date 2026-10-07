package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class UsbConnectionCloserTest {
    @Test fun theWaiterCannotFreeItsRequestWhileAnotherNativeCloseIsStillRunning() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondReturned = CountDownLatch(1)
        val closer = UsbConnectionCloser { entered.countDown(); release.await() }
        val owner = Thread { closer.close() }.apply { start() }
        entered.await()
        val waiter = Thread { closer.close(); secondReturned.countDown() }.apply { start() }
        try {
            org.junit.Assert.assertFalse(secondReturned.await(100, java.util.concurrent.TimeUnit.MILLISECONDS))
        } finally { release.countDown(); owner.join(2000); waiter.join(2000) }
        assertEquals(0L, secondReturned.count)
    }

    @Test fun cancellationAndWaiterFinallyNeverDoubleFreeTheNativeConnection() {
        val calls = AtomicInteger()
        val start = CountDownLatch(1)
        val closer = UsbConnectionCloser { calls.incrementAndGet() }
        val threads = (1..32).map { Thread { start.await(); repeat(20) { closer.close() } }.apply { start() } }
        start.countDown()
        threads.forEach { it.join(2000) }
        assertEquals(1, calls.get())
    }
}
