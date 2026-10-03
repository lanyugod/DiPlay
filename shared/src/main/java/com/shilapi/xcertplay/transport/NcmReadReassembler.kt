package com.shilapi.xcertplay.transport

import java.util.ArrayDeque

/** A bounded byte stream; chunk boundaries carry no NTB meaning. Corruption is terminal. */
internal class NcmReadReassembler(private val maxChunkBytes: Int = 16_384) {
    private val maxBufferedBytes = 65_536 + maxChunkBytes
    private var buffered = ByteArray(0)
    private var bufferedSize = 0
    private val frames = ArrayDeque<ByteArray>()
    private var frameBytes = 0
    private var failure: IllegalArgumentException? = null

    fun hasFrames(): Boolean { failure?.let { throw it }; return frames.isNotEmpty() }
    fun pollFrame(): ByteArray {
        failure?.let { throw it }
        return frames.removeFirst().also { frameBytes -= it.size }
    }

    fun append(chunk: ByteArray, length: Int = chunk.size) {
        failure?.let { throw it }
        try {
            require(length in 0..minOf(chunk.size, maxChunkBytes)) { "Invalid NCM chunk length" }
            val required = bufferedSize + length
            require(required <= maxBufferedBytes) { "NCM reassembly exceeds $maxBufferedBytes bytes" }
            if (required > buffered.size) buffered = buffered.copyOf(maxOf(required, maxChunkBytes, buffered.size * 2).coerceAtMost(maxBufferedBytes))
            chunk.copyInto(buffered, bufferedSize, 0, length)
            bufferedSize = required
            drain()
        } catch (error: IllegalArgumentException) { failure = error; throw error }
    }

    private fun drain() {
        while (bufferedSize >= 12) {
            require(u32(0) == Ntb16Codec.NTH16_SIG) { "NCM stream does not begin with NTB16" }
            val length = u16(8)
            require(length >= 28) { "Invalid NTB16 block length $length" }
            val wireLength = length + if (length % 512 == 0) 1 else 0
            if (bufferedSize < wireLength) return
            if (wireLength > length) require(buffered[length].toInt() == 0) { "Invalid NCM short-packet pad" }
            for (frame in Ntb16Codec.parseStrict(buffered, 0, length)) {
                require(frames.size < 256 && frameBytes + frame.size <= 1024 * 1024) { "NCM frame queue overflow" }
                frames.addLast(frame); frameBytes += frame.size
            }
            buffered.copyInto(buffered, 0, wireLength, bufferedSize)
            bufferedSize -= wireLength
        }
    }
    private fun u16(at: Int) = (buffered[at].toInt() and 255) or ((buffered[at + 1].toInt() and 255) shl 8)
    private fun u32(at: Int) = u16(at) or (u16(at + 2) shl 16)
}
