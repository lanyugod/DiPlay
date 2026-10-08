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
    private var optionalShortPacketPad = false

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
        while (true) {
            if (optionalShortPacketPad) {
                if (bufferedSize == 0) return
                if (buffered[0].toInt() == 0) {
                    consume(1)
                } else {
                    // USB ZLPs carry no bytes. A following NTB may start immediately,
                    // even when the previous block length is a multiple of 512.
                    val signature = byteArrayOf(0x4e, 0x43, 0x4d, 0x48)
                    require((0 until minOf(4, bufferedSize)).all { buffered[it] == signature[it] }) {
                        "Invalid NCM short-packet boundary next=" +
                            (0 until minOf(4, bufferedSize)).joinToString(",") { (buffered[it].toInt() and 255).toString(16) }
                    }
                }
                optionalShortPacketPad = false
            }
            if (bufferedSize < 12) return
            require(u32(0) == Ntb16Codec.NTH16_SIG) { "NCM stream does not begin with NTB16" }
            val length = u16(8)
            require(length >= 28) { "Invalid NTB16 block length $length" }
            if (bufferedSize < length) return
            for (frame in Ntb16Codec.parseStrict(buffered, 0, length)) {
                require(frames.size < 256 && frameBytes + frame.size <= 1024 * 1024) { "NCM frame queue overflow" }
                frames.addLast(frame); frameBytes += frame.size
            }
            consume(length)
            optionalShortPacketPad = length % 512 == 0
        }
    }
    private fun consume(length: Int) {
        buffered.copyInto(buffered, 0, length, bufferedSize)
        bufferedSize -= length
    }
    private fun u16(at: Int) = (buffered[at].toInt() and 255) or ((buffered[at + 1].toInt() and 255) shl 8)
    private fun u32(at: Int) = u16(at) or (u16(at + 2) shl 16)
}
