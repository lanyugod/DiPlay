package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 26, 28, 29], manifest = Config.NONE)
class AudioFocusCompatTest {
    @Test fun requestAndAbandonPreserveTheListenerOnBothBackends() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(AudioManager::class.java)
        val listener = AudioManager.OnAudioFocusChangeListener { }
        val focus = AudioFocusCompat(manager, AudioManager.AUDIOFOCUS_GAIN,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(), listener, Handler(Looper.getMainLooper()))
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, focus.request())
        val requested = shadowOf(manager).lastAudioFocusRequest
        focus.close()
        if (android.os.Build.VERSION.SDK_INT < 26) {
            assertSame(listener, requested.listener)
            assertSame(listener, shadowOf(manager).lastAbandonedAudioFocusListener)
            assertEquals(AudioManager.STREAM_MUSIC, requested.streamType)
        } else assertSame(requested.audioFocusRequest, shadowOf(manager).lastAbandonedAudioFocusRequest)
    }
}
