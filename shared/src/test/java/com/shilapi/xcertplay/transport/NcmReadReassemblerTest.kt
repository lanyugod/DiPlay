package com.shilapi.xcertplay.transport

import org.junit.Assert.*
import org.junit.Test

class NcmReadReassemblerTest {
    @Test fun ntbAcross16KiBChunksAndFollowingCoalescedBlocksRemainIntact() {
        val large = ByteArray(60_000) { it.toByte() }
        val small = ByteArray(100) { 7 }
        val stream = Ntb16Codec.build(large, 1) + Ntb16Codec.build(small, 2)
        val decoder = NcmReadReassembler()
        for (offset in stream.indices step 16_384) decoder.append(stream.copyOfRange(offset, minOf(offset + 16_384, stream.size)))
        assertArrayEquals(large, decoder.pollFrame()); assertArrayEquals(small, decoder.pollFrame())
        assertFalse(decoder.hasFrames())
        // One NDP carrying two datagrams, rather than two one-datagram blocks.
        val multiple = ByteArray(39)
        fun u16(at: Int, value: Int) { multiple[at] = value.toByte(); multiple[at + 1] = (value ushr 8).toByte() }
        fun u32(at: Int, value: Int) { u16(at, value); u16(at + 2, value ushr 16) }
        u32(0, Ntb16Codec.NTH16_SIG); u16(4, 12); u16(8, 39); u16(10, 12)
        u32(12, Ntb16Codec.NDP16_SIG); u16(16, 20)
        u16(20, 32); u16(22, 3); u16(24, 35); u16(26, 4)
        byteArrayOf(1, 2, 3, 4, 5, 6, 7).copyInto(multiple, 32)
        decoder.append(multiple)
        assertArrayEquals(byteArrayOf(1, 2, 3), decoder.pollFrame())
        assertArrayEquals(byteArrayOf(4, 5, 6, 7), decoder.pollFrame())
        assertFalse(decoder.hasFrames())
    }
    @Test fun exact512BytePadAndIncompleteHeaderSurviveAnApplicationTimeout() {
        val frame = ByteArray(484) { 3 }
        val block = Ntb16Codec.build(frame, 1)
        assertEquals(513, block.size)
        val decoder = NcmReadReassembler()
        decoder.append(block.copyOfRange(0, 5)); assertFalse(decoder.hasFrames())
        decoder.append(block.copyOfRange(5, 512)); assertArrayEquals(frame, decoder.pollFrame())
        decoder.append(block.copyOfRange(512, 513)); assertFalse(decoder.hasFrames())
        val next = byteArrayOf(8, 9)
        decoder.append(Ntb16Codec.build(next, 2)); assertArrayEquals(next, decoder.pollFrame())
    }
    @Test fun observed3584ByteBlockMayBeFollowedImmediatelyByNcmHeader() {
        val first = ByteArray(3556) { it.toByte() }
        val second = ByteArray(1544) { 7 }
        val unpadded = Ntb16Codec.build(first, 1).copyOf(3584)
        val wire = unpadded + Ntb16Codec.build(second, 2)
        assertEquals(5156, wire.size)
        val decoder = NcmReadReassembler()
        decoder.append(wire)
        assertArrayEquals(first, decoder.pollFrame())
        assertArrayEquals(second, decoder.pollFrame())
        assertFalse(decoder.hasFrames())
    }
    @Test fun optionalPadAndUnpaddedSuccessorSurviveEveryHeaderSplit() {
        for (blockLength in listOf(512, 1024, 3584, 16384)) {
            for (padded in listOf(false, true)) {
                for (headerBytes in 0..12) {
                    val first = ByteArray(blockLength - 28) { 9 }
                    val second = byteArrayOf(3, 4, 5)
                    val encoded = Ntb16Codec.build(first, 1)
                    val block = if (padded) encoded else encoded.copyOf(blockLength)
                    val successor = Ntb16Codec.build(second, 2)
                    val stream = block + successor
                    val split = block.size + headerBytes
                    val decoder = NcmReadReassembler()
                    for (offset in 0 until split step 16384) {
                        decoder.append(stream.copyOfRange(offset, minOf(offset + 16384, split)))
                    }
                    assertArrayEquals(first, decoder.pollFrame())
                    decoder.append(stream.copyOfRange(split, stream.size))
                    assertArrayEquals(second, decoder.pollFrame())
                    assertFalse(decoder.hasFrames())
                }
            }
        }
    }
    @Test fun corruptSuccessorAfterAnUnpaddedBlockIsTerminal() {
        val decoder = NcmReadReassembler()
        decoder.append(Ntb16Codec.build(ByteArray(484), 1).copyOf(512))
        decoder.append(byteArrayOf(0x4e))
        val corrupt = Ntb16Codec.build(byteArrayOf(1), 2).copyOfRange(1, 29)
        corrupt[0] = 0x42
        assertThrows(IllegalArgumentException::class.java) { decoder.append(corrupt) }
        assertThrows(IllegalArgumentException::class.java) { decoder.hasFrames() }
    }
    @Test fun invalidDatagramAndPadAreTerminalRatherThanSilentlyDropped() {
        for (pad in listOf(false, true)) {
            val block = Ntb16Codec.build(ByteArray(484), 1)
            if (pad) block[512] = 1 else { block[20] = 1; block[21] = 0 }
            val decoder = NcmReadReassembler()
            assertThrows(IllegalArgumentException::class.java) { decoder.append(block) }
            assertThrows(IllegalArgumentException::class.java) { decoder.append(Ntb16Codec.build(byteArrayOf(1), 2)) }
        }
    }
    @Test fun queuedFrameOverflowFailsTheStream() {
        val block = Ntb16Codec.build(byteArrayOf(1), 1)
        val decoder = NcmReadReassembler()
        repeat(256) { decoder.append(block) }
        assertThrows(IllegalArgumentException::class.java) { decoder.append(block) }
        assertThrows(IllegalArgumentException::class.java) { decoder.pollFrame() }
    }
}
