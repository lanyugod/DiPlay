package com.shilapi.xcertplay.transport

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LegacyUsbReadPumpTest {
    private class Driver : LegacyUsbDriver {
        val completions = LinkedBlockingQueue<ByteArray>()
        val queued = CountDownLatch(1)
        val released = CountDownLatch(1)
        val queues = AtomicInteger()
        val cancellations = AtomicInteger()
        var initializeResult = true
        var expected = true
        var queueResult = true
        var cancelWakes = true
        var cancelResult = true
        @Volatile var buffer: ByteBuffer? = null
        override fun initialize() = initializeResult
        override fun queue(buffer: ByteBuffer, length: Int): Boolean {
            assertEquals(16_384, length)
            this.buffer = buffer; queues.incrementAndGet(); queued.countDown()
            return queueResult
        }
        override fun waitForCompletion(): Boolean {
            buffer!!.put(completions.take())
            return expected
        }
        override fun cancel(): Boolean { cancellations.incrementAndGet(); if (cancelWakes && cancelResult) completions.offer(ByteArray(0)); return cancelResult }
        override fun closeConnection() { if (cancelWakes) completions.offer(ByteArray(0)) }
        override fun close() { released.countDown() }
    }

    @Test fun consumerTimeoutLeavesOneRequestAndDeliversLateBytes() {
        val driver = Driver()
        val pump = LegacyUsbReadPump(driver)
        try {
            assertTrue(driver.queued.await(1, TimeUnit.SECONDS))
            assertSame(UsbReadResult.Timeout, pump.read(10))
            assertSame(UsbReadResult.Timeout, pump.read(10))
            assertEquals(1, driver.queues.get()); assertEquals(0, driver.cancellations.get())
            driver.completions.put(byteArrayOf(1, 2, 3))
            assertArrayEquals(byteArrayOf(1, 2, 3), (pump.read(1000) as UsbReadResult.Data).bytes)
            assertEquals(2, driver.queues.get())
        } finally { pump.close() }
        assertTrue(driver.released.await(1, TimeUnit.SECONDS))
        assertTrue(pump.read(1) is UsbReadResult.Terminated)
    }

    @Test fun zeroLengthCompletionIsNotDetach() {
        val driver = Driver(); val pump = LegacyUsbReadPump(driver)
        try {
            assertTrue(driver.queued.await(1, TimeUnit.SECONDS))
            driver.completions.put(ByteArray(0)); driver.completions.put(byteArrayOf(7))
            assertArrayEquals(byteArrayOf(7), (pump.read(1000) as UsbReadResult.Data).bytes)
        } finally { pump.close() }
    }

    @Test fun unexpectedCompletionAndQueueFailureStayTerminal() {
        for (unexpected in listOf(true, false)) {
            val driver = Driver().apply { expected = !unexpected; queueResult = unexpected }
            val pump = LegacyUsbReadPump(driver)
            try {
                assertTrue(driver.queued.await(1, TimeUnit.SECONDS))
                if (unexpected) driver.completions.put(byteArrayOf(1))
                assertTrue(pump.read(1000) is UsbReadResult.Terminated)
                assertTrue(pump.read(1) is UsbReadResult.Terminated)
            } finally { pump.close() }
        }
    }

    @Test fun queueOverflowFailsWithoutDroppingStreamBytes() {
        val driver = Driver(); val pump = LegacyUsbReadPump(driver, maxQueuedBytes = 2)
        try {
            assertTrue(driver.queued.await(1, TimeUnit.SECONDS))
            driver.completions.put(byteArrayOf(1, 2, 3))
            assertTrue(pump.read(1000) is UsbReadResult.Terminated)
        } finally { pump.close() }
    }

    @Test fun nativeWaiterTimeoutRetainsRequestAndBlocksReconnectUntilExit() {
        val driver = Driver().apply { cancelWakes = false }
        val pump = LegacyUsbReadPump(driver, closeTimeoutMillis = 20)
        assertTrue(driver.queued.await(1, TimeUnit.SECONDS))
        try {
            pump.close()
            assertEquals(1L, driver.released.count)
            assertThrows(IllegalStateException::class.java) { LegacyUsbReadPump.assertReconnectAllowed() }
        } finally {
            driver.completions.offer(ByteArray(0))
            assertTrue(driver.released.await(1, TimeUnit.SECONDS))
        }
        // close() signals release before the worker clears quarantine; await its final diagnostic.
        val deadline = System.nanoTime() + 1_000_000_000
        while (runCatching { LegacyUsbReadPump.assertReconnectAllowed() }.isFailure && System.nanoTime() < deadline) Thread.yield()
        LegacyUsbReadPump.assertReconnectAllowed()
    }

    @Test fun failedCancelStillClosesTheConnectionAndReleasesAfterWaiterExit() {
        val driver = Driver().apply { cancelResult = false }
        val pump = LegacyUsbReadPump(driver)
        assertTrue(driver.queued.await(1, TimeUnit.SECONDS))
        pump.close()
        assertTrue(driver.released.await(1, TimeUnit.SECONDS))
        LegacyUsbReadPump.assertReconnectAllowed()
    }

    @Test fun immediateCloseCannotQueueOrPublishAfterTermination() {
        repeat(30) {
            val driver = Driver(); val pump = LegacyUsbReadPump(driver)
            pump.close()
            assertTrue(driver.released.await(1, TimeUnit.SECONDS))
            assertTrue(pump.read(1) is UsbReadResult.Terminated)
        }
    }
}
