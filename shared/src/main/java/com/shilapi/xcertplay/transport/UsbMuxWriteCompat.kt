package com.shilapi.xcertplay.transport

import java.io.IOException

/** Caller holds its whole-message write lock. Never restarts a partly transmitted message. */
internal object UsbMuxWriteCompat {
    fun write(data: ByteArray, timeoutMillis: Int, maxTransferBytes: Int = 16_384,
        nanoTime: () -> Long = System::nanoTime,
        transfer: (offset: Int, length: Int, timeoutMillis: Int) -> Int) {
        require(timeoutMillis > 0 && maxTransferBytes > 0)
        val deadline = nanoTime() + timeoutMillis * 1_000_000L
        var offset = 0
        while (offset < data.size) {
            val remaining = deadline - nanoTime()
            if (remaining <= 0) throw IOException("USBMUX write deadline expired after $offset/${data.size} bytes")
            val length = minOf(maxTransferBytes, data.size - offset)
            val count = transfer(offset, length, ((remaining + 999_999) / 1_000_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            if (count <= 0 || count > length) throw IOException("USBMUX write failed after $offset/${data.size} bytes (result=$count)")
            offset += count
        }
    }
}
