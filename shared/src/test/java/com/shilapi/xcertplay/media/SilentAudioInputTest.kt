package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 29], manifest = Config.NONE)
class SilentAudioInputTest {
    @Test fun silentInputSendsAuthenticatedZeroSamplesAndStopsOnCloseWithoutRecording() {
        val key = ByteArray(32) { (it + 1).toByte() }
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { receiver ->
            receiver.soTimeout = 1000
            val config = MicrophoneConfig("default", 16000, 1, 100, 20,
                InetAddress.getLoopbackAddress(), receiver.localPort, key, silence = true)
            val uplink = MicrophoneUplink(config)
            try {
                assertTrue(uplink.start())
                repeat(2) { index ->
                    val packet = DatagramPacket(ByteArray(4096), 4096)
                    receiver.receive(packet)
                    val wire = packet.data.copyOf(packet.length)
                    val tail = wire.size - MicrophonePacketizer.NONCE_LEN
                    val nonce = ByteArray(12).also { wire.copyInto(it, 4, tail, wire.size) }
                    val body = AirPlayCrypto.chachaOpen(key, nonce,
                        wire.copyOfRange(12, tail), wire.copyOfRange(4, 12))
                    assertArrayEquals(ByteArray(config.frameBytes), body)
                    assertEquals(index, ((wire[2].toInt() and 255) shl 8) or (wire[3].toInt() and 255))
                    val timestamp = (4..7).fold(0) { value, offset -> (value shl 8) or (wire[offset].toInt() and 255) }
                    assertEquals(index * config.samplesPerPacket, timestamp)
                }
                val recorder = MicrophoneUplink::class.java.getDeclaredField("recorder").apply { isAccessible = true }
                assertNull(recorder.get(uplink))
            } finally {
                uplink.close()
                uplink.close()
            }
            receiver.soTimeout = 80
            // Drain any packet already queued before close, then prove the producer has stopped.
            var drained = 0
            try {
                while (true) {
                    receiver.receive(DatagramPacket(ByteArray(4096), 4096))
                    assertTrue("uplink must stop after close", ++drained < 20)
                }
            } catch (_: SocketTimeoutException) { }
        }
    }

    @Test fun silentInputRejectsAnUnadvertisedCompressedCodec() {
        val config = MicrophoneConfig("default", 48000, 1, 100, 20,
            InetAddress.getLoopbackAddress(), 12345, ByteArray(32), codec = AudioCodecKind.OPUS, silence = true)
        MicrophoneUplink(config).use { assertFalse(it.start()) }
    }

    @Test fun silentCapabilityKeepsOutputAndDoesNotAdvertiseCompressedInputOrMediaCapture() {
        val config = AirPlayConfig("test", "02:00:00:00:00:02", "02:00:00:00:00:01", "1.0",
            AirPlayDisplayConfig(800, 480))
        val regular = AirPlayInfoPlist.build(config)["audioFormats"] as List<*>
        val silent = AirPlayInfoPlist.build(config.copy(silentAudioInput = true))["audioFormats"] as List<*>
        regular.zip(silent).forEach { (before, after) ->
            before as Map<*, *>; after as Map<*, *>
            assertEquals(before["audioOutputFormats"], after["audioOutputFormats"])
            if (after["type"] == 100 && after["audioType"] in setOf("compatibility", "default", "telephony", "speechRecognition")) {
                assertEquals(0x4154, after["audioInputFormats"])
            } else assertFalse(after.containsKey("audioInputFormats"))
        }
        assertFalse(AirPlayInfoPlist.build(config.copy(silentAudioInput = true, disableAudioOutput = true)).containsKey("audioFormats"))
    }
}
