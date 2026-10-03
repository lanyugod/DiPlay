package com.shilapi.xcertplay.transport

import java.io.IOException
import java.nio.ByteBuffer
import java.util.ArrayDeque

/** One native waiter per connection. Consumer timeouts never touch the pending request. */
internal class LegacyUsbReadPump(
    private val driver: LegacyUsbDriver,
    private val maxQueuedBytes: Int = 256 * 1024,
    private val closeTimeoutMillis: Long = 1_000,
    private val diagnostic: (String) -> Unit = {},
) : UsbReadTransport {
    private val lock = Object()
    private val completed = ArrayDeque<ByteArray>()
    private var queuedBytes = 0
    private var highWaterBytes = 0
    private var terminated: Throwable? = null
    private var closed = false
    private var quarantined = false
    private var exited = false
    private val worker: Thread

    init {
        require(maxQueuedBytes > 0 && closeTimeoutMillis > 0)
        try { assertReconnectAllowed() } catch (error: Exception) {
            runCatching { driver.closeConnection() }; runCatching { driver.close() }
            throw error
        }
        worker = Thread(::pump, "legacy-usb-in").apply { start() }
    }

    override fun read(timeoutMillis: Long): UsbReadResult = synchronized(lock) {
        require(timeoutMillis > 0)
        val deadline = System.nanoTime() + timeoutMillis.coerceAtMost(Long.MAX_VALUE / 1_000_000) * 1_000_000
        while (true) {
            terminated?.let { return@synchronized UsbReadResult.Terminated(it) }
            if (completed.isNotEmpty()) {
                val data = completed.removeFirst()
                queuedBytes -= data.size
                return@synchronized UsbReadResult.Data(data)
            }
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) return@synchronized UsbReadResult.Timeout
            lock.wait(remaining / 1_000_000, (remaining % 1_000_000).toInt())
        }
        @Suppress("UNREACHABLE_CODE") UsbReadResult.Timeout
    }

    private fun pump() {
        try {
            val buffer = ByteBuffer.allocateDirect(CHUNK_BYTES)
            synchronized(lock) {
                if (closed) return
                check(driver.initialize()) { "USB request initialization failed" }
                check(driver.queue(buffer, CHUNK_BYTES)) { "USB queue failed" }
            }
            var emptyCompletions = 0
            while (true) {
                val expected = driver.waitForCompletion()
                synchronized(lock) {
                    if (closed) return
                    check(expected) { "Unexpected or missing USB completion" }
                    val length = buffer.position()
                    check(length in 0..CHUNK_BYTES) { "Invalid USB completion length $length" }
                    val data = ByteArray(length)
                    buffer.flip(); buffer.get(data); buffer.clear()
                    // Requeue before publishing; there is no consumer timeout gap in bulk IN.
                    check(driver.queue(buffer, CHUNK_BYTES)) { "USB requeue failed" }
                    if (length > 0) {
                        check(queuedBytes + length <= maxQueuedBytes) { "USB completed queue overflow" }
                        completed.addLast(data); queuedBytes += length
                        if (queuedBytes > highWaterBytes) highWaterBytes = queuedBytes
                        lock.notifyAll()
                    }
                    emptyCompletions = if (length == 0) emptyCompletions + 1 else 0
                }
                if (emptyCompletions >= 8) {
                    if (emptyCompletions == 8) diagnostic("Legacy USB zero-byte completions throttled")
                    Thread.sleep(4)
                }
            }
        } catch (error: Exception) {
            synchronized(lock) {
                if (terminated == null) terminated = error
                lock.notifyAll()
            }
        } finally {
            // Only the waiter frees UsbRequest, after it has left native requestWait.
            runCatching { driver.cancel() }
            runCatching { driver.closeConnection() }
            runCatching { driver.close() }
            synchronized(lock) {
                if (terminated == null) terminated = IOException("USB pump stopped")
                exited = true
                if (quarantined) synchronized(reconnectLock) { strandedWaiters-- }
                lock.notifyAll()
            }
            diagnostic("Legacy USB stopped highWaterBytes=$highWaterBytes")
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            if (terminated == null) terminated = IOException("USB transport closed")
            completed.clear(); queuedBytes = 0
            // Serializes cancel with queue; no request can be queued after cancellation.
            runCatching { driver.cancel() }.onSuccess { cancelled ->
                if (!cancelled) diagnostic("Legacy USB cancel returned false; closing connection to wake waiter")
            }
            lock.notifyAll()
        }
        runCatching { driver.closeConnection() }
        if (Thread.currentThread() !== worker) {
            try { worker.join(closeTimeoutMillis) }
            catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        synchronized(lock) {
            if (!exited && worker.isAlive && !quarantined) {
                quarantined = true
                synchronized(reconnectLock) { strandedWaiters++ }
                diagnostic("Legacy USB close exceeded ${closeTimeoutMillis}ms; reconnect blocked until waiter exits")
            }
        }
    }

    companion object {
        const val CHUNK_BYTES = 16_384
        private val reconnectLock = Any()
        private var strandedWaiters = 0
        fun assertReconnectAllowed() = synchronized(reconnectLock) {
            check(strandedWaiters == 0) { "Previous USB waiter has not exited; reconnect is blocked" }
        }
    }
}
