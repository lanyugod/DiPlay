package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import java.io.Closeable

/** A request and its release always retain the same listener (including the legacy API). */
class AudioFocusCompat(
    private val manager: AudioManager,
    private val gain: Int,
    private val attributes: AudioAttributes,
    private val listener: AudioManager.OnAudioFocusChangeListener,
    handler: Handler,
    private val legacyStream: Int = AudioManager.STREAM_MUSIC,
) : Closeable {
    private val modern = if (Build.VERSION.SDK_INT >= 26) Modern(manager, gain, attributes, listener, handler) else null
    @Suppress("DEPRECATION")
    fun request(): Int = if (Build.VERSION.SDK_INT >= 26) modern!!.request() else manager.requestAudioFocus(listener, legacyStream, gain)
    @Suppress("DEPRECATION")
    override fun close() { if (Build.VERSION.SDK_INT >= 26) modern!!.close() else manager.abandonAudioFocus(listener) }

    @androidx.annotation.RequiresApi(26)
    private class Modern(private val manager: AudioManager, gain: Int, attributes: AudioAttributes,
        listener: AudioManager.OnAudioFocusChangeListener, handler: Handler) : Closeable {
        private val request = android.media.AudioFocusRequest.Builder(gain)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener(listener, handler).build()
        fun request() = manager.requestAudioFocus(request)
        override fun close() { manager.abandonAudioFocusRequest(request) }
    }
}
