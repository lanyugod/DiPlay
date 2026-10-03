package com.shilapi.xcertplay.media

import android.media.AudioTrack
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 29], manifest = Config.NONE)
class Android6AudioSmokeTest {
    @Test fun pcmActuallyCreatesWritesAndStartsTheTrack() {
        val ready = CountDownLatch(1)
        val stats = CountDownLatch(1)
        val diagnostics = java.util.Collections.synchronizedList(mutableListOf<String>())
        val sink = AndroidMediaSink(context = RuntimeEnvironment.getApplication(), onAudioDiagnostic = {
            diagnostics.add(it); if (it.startsWith("Audio: ready")) ready.countDown()
            if (it.startsWith("audio stats")) stats.countDown()
        })
        val id = AudioStreamId(100, "media")
        val format = AudioFormat(AudioCodecKind.LPCM, 48000, 2, 96)
        try {
            sink.onAudioStarted(id, format, 0)
            assertTrue("Track must be built, rather than only instantiating a sink: $diagnostics", ready.await(5, TimeUnit.SECONDS))
            val field = AndroidMediaSink::class.java.getDeclaredField("audioRenderers").apply { isAccessible = true }
            val renderer = (field.get(sink) as Map<*, *>)[id]!!
            val trackField = renderer.javaClass.getDeclaredField("track").apply { isAccessible = true }
            val track = trackField.get(renderer) as AudioTrack
            repeat(10) { sink.onAudioRtp(id, format, ByteArray(12 + 8192), it * 2048) }
            val deadline = System.nanoTime() + 5_000_000_000
            while (track.playState != AudioTrack.PLAYSTATE_PLAYING && System.nanoTime() < deadline) Thread.yield()
            assertEquals(AudioTrack.PLAYSTATE_PLAYING, track.playState)
        } finally { sink.close() }
        assertTrue("Final audio statistics must be emitted: $diagnostics", stats.await(5, TimeUnit.SECONDS))
        assertFalse(diagnostics.any { it.contains("renderer failed") })
        assertTrue(diagnostics.any { it.contains("underrunSource=${if (android.os.Build.VERSION.SDK_INT < 24) "estimated" else "system"}") })
    }
}
