package com.shilapi.xcertplay.transport

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class UsbMuxWriteCompatTest {
    @Test fun allLengthsAndShortWritesPreserveTheStream() {
        for (size in listOf(0, 1, 16_384, 16_385, 65_536)) {
            val bytes = ByteArray(size) { (it % 251).toByte() }
            val output = java.io.ByteArrayOutputStream()
            UsbMuxWriteCompat.write(bytes, 500) { offset, length, timeout ->
                assertTrue(length <= 16_384); assertTrue(timeout in 1..500)
                val count = minOf(length, 3000)
                output.write(bytes, offset, count)
                count
            }
            assertArrayEquals(bytes, output.toByteArray())
        }
    }

    @Test fun failureAfterPartialWriteNeverRestartsTheMessage() {
        val offsets = mutableListOf<Int>()
        assertThrows(IOException::class.java) {
            UsbMuxWriteCompat.write(ByteArray(20_000), 500) { offset, _, _ ->
                offsets.add(offset)
                if (offset == 0) 500 else -1
            }
        }
        assertEquals(listOf(0, 500), offsets)
    }

    @Test fun chunksShareOneDeadline() {
        var now = 0L
        val timeouts = mutableListOf<Int>()
        assertThrows(IOException::class.java) {
            UsbMuxWriteCompat.write(ByteArray(65_536), 100, nanoTime = { now }) { _, length, timeout ->
                timeouts.add(timeout); now += 60_000_000; length
            }
        }
        assertEquals(listOf(100, 40), timeouts)
    }
}
