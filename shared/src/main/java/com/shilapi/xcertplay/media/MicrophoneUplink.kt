package com.shilapi.xcertplay.media

import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import com.shilapi.xcertplay.airplay.MicrophoneCounters
import com.shilapi.xcertplay.airplay.MicrophonePacketizer
import com.shilapi.xcertplay.airplay.toHexString
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures one PCM microphone stream and sends it back to the phone as sealed CarPlay RTP.
 *
 * The recorder runs only while the matching audio stream is active, so callers start this after
 * the first downlink audio packet and close it on stream teardown.
 */
internal class MicrophoneUplink(private val config: MicrophoneConfig) : Closeable {
    private val running = AtomicBoolean(false)
    private val firstPacketLogged = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var opusEncoder: OpusEncoder? = null
    private var thread: Thread? = null

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        // This branch precedes every AudioRecord call, including buffer-size probing.
        if (config.silence) return startSilence()

        val channelMask = if (config.channels >= 2) {
            AndroidAudioFormat.CHANNEL_IN_STEREO
        } else {
            AndroidAudioFormat.CHANNEL_IN_MONO
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRate,
            channelMask,
            AndroidAudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.w(TAG, "microphone unavailable rate=${config.sampleRate} channels=${config.channels}")
            running.set(false)
            return false
        }

        val source = when (config.audioType) {
            "telephony" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            "speechrecognition" -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            else -> MediaRecorder.AudioSource.MIC
        }
        val nextEncoder = if (config.codec == AudioCodecKind.OPUS) {
            OpusEncoder(config.bitrate ?: 48_000).takeIf { it.available }
        } else {
            null
        }
        if (config.codec == AudioCodecKind.OPUS && nextEncoder == null) {
            Log.w(TAG, "microphone Opus encoder is unavailable")
            running.set(false)
            return false
        }
        val bufferSize = maxOf(minBuffer * 2, config.frameBytes * 4)
        val nextRecorder = try {
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AndroidAudioFormat.Builder()
                        .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(config.sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
        } catch (error: Exception) {
            Log.e(TAG, "microphone recorder creation failed", error)
            nextEncoder?.close()
            running.set(false)
            return false
        }
        if (nextRecorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "microphone recorder failed to initialize")
            nextRecorder.release()
            nextEncoder?.close()
            running.set(false)
            return false
        }

        val nextSocket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("::"), 0))
            }
        } catch (error: Exception) {
            Log.e(TAG, "microphone socket creation failed", error)
            nextRecorder.release()
            nextEncoder?.close()
            running.set(false)
            return false
        }

        recorder = nextRecorder
        socket = nextSocket
        opusEncoder = nextEncoder
        return try {
            nextRecorder.startRecording()
            thread = Thread({ capture(nextRecorder, nextSocket) }, "carplay-mic").apply {
                isDaemon = true
                start()
            }
            Log.i(
                TAG,
                "microphone uplink started type=${config.audioType} " +
                    "rate=${config.sampleRate} channels=${config.channels} " +
                    "frameMs=${config.frameMillis} port=${config.port}",
            )
            true
        } catch (error: Exception) {
            Log.e(TAG, "microphone recording failed", error)
            release()
            false
        }
    }

    private fun startSilence(): Boolean {
        if (config.codec != AudioCodecKind.LPCM) {
            running.set(false)
            return false
        }
        return try {
            val output = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("::"), 0))
            }
            socket = output
            thread = Thread({
                val frame = ByteArray(config.frameBytes)
                val counters = MicrophoneCounters()
                val intervalNs = config.samplesPerPacket * 1_000_000_000L / config.sampleRate
                var deadline = System.nanoTime()
                try {
                    while (running.get()) {
                        sendFrame(output, counters, frame)
                        deadline += intervalNs
                        val remaining = deadline - System.nanoTime()
                        if (remaining > 0) Thread.sleep(remaining / 1_000_000L, (remaining % 1_000_000L).toInt())
                        else deadline = System.nanoTime() // Do not burst after a scheduler stall.
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (error: Exception) {
                    if (running.get()) Log.w(TAG, "silent audio input failed", error)
                } finally {
                    release()
                }
            }, "carplay-silent-input").apply { isDaemon = true; start() }
            Log.i(TAG, "silent audio input started type=${config.audioType} rate=${config.sampleRate} channels=${config.channels}")
            true
        } catch (error: Exception) {
            Log.w(TAG, "silent audio input unavailable", error)
            release()
            false
        }
    }

    private fun capture(recorder: AudioRecord, socket: DatagramSocket) {
        val frame = ByteArray(config.frameBytes)
        val readBuffer = ByteArray(maxOf(frame.size, MIN_READ_BYTES))
        val counters = MicrophoneCounters()
        var filled = 0
        try {
            while (running.get()) {
                val count = recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                if (count < 0) {
                    if (running.get()) Log.e(TAG, "microphone read failed code=$count")
                    return
                }
                if (count == 0) {
                    continue
                }
                var offset = 0
                while (offset < count && running.get()) {
                    val copied = minOf(frame.size - filled, count - offset)
                    readBuffer.copyInto(frame, filled, offset, offset + copied)
                    filled += copied
                    offset += copied
                    if (filled == frame.size) {
                        sendFrame(socket, counters, frame)
                        filled = 0
                    }
                }
            }
        } catch (error: Exception) {
            if (running.get()) Log.e(TAG, "microphone capture failed", error)
        } finally {
            running.set(false)
            release()
        }
    }

    private fun sendFrame(socket: DatagramSocket, counters: MicrophoneCounters, frame: ByteArray) {
        val bodies = if (config.codec == AudioCodecKind.OPUS) {
            opusEncoder?.encode(frame).orEmpty()
        } else {
            listOf(MicrophonePacketizer.toWirePcm(frame))
        }
        bodies.forEach { body ->
            sendPacket(
                socket = socket,
                counters = counters,
                body = body,
                samples = config.samplesPerPacket,
            )
        }
    }

    private fun sendPacket(
        socket: DatagramSocket,
        counters: MicrophoneCounters,
        body: ByteArray,
        samples: Int,
    ) {
        val packet = MicrophonePacketizer.sealPacket(
            key = config.key,
            payloadType = config.payloadType,
            counters = counters,
            body = body,
            samples = samples,
        )
        try {
            socket.send(DatagramPacket(packet, packet.size, config.host, config.port))
            if (firstPacketLogged.compareAndSet(false, true)) {
                Log.i(
                    TAG,
                    "microphone first packet bytes=${packet.size} body=${body.size} " +
                        "head=${packet.copyOf(minOf(packet.size, 16)).toHexString()} " +
                        "port=${config.port}",
                )
            }
        } catch (error: Exception) {
            if (running.get()) throw error
        }
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) {
            release()
            return
        }
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // Best effort; release below is authoritative.
        }
        try {
            socket?.close()
        } catch (_: Exception) {
            // Best effort.
        }
        thread?.let { worker ->
            try {
                worker.join(CLOSE_JOIN_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (worker.isAlive) worker.interrupt()
        }
        release()
    }

    @Synchronized
    private fun release() {
        running.set(false)
        val currentRecorder = recorder
        recorder = null
        try {
            currentRecorder?.release()
        } catch (_: Exception) {
            // Best effort.
        }
        val currentSocket = socket
        socket = null
        try {
            currentSocket?.close()
        } catch (_: Exception) {
            // Best effort.
        }
        val currentEncoder = opusEncoder
        opusEncoder = null
        currentEncoder?.close()
    }

    private companion object {
        const val TAG = "xcertplay-usb"
        const val MIN_READ_BYTES = 2_048
        const val CLOSE_JOIN_MILLIS = 500L
    }
}
