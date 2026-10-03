package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import com.shilapi.xcertplay.media.AudioFocusCompat
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.orchestration.CarPlayController

/**
 * Steering-wheel and other hardware media buttons for CarPlay.
 *
 * Android delivers media keys to a media session; BYD picks the session of the audio-focus
 * owner. Once CarPlay plays music, DiPlay holds audio focus and an active session until the
 * CarPlay session ends, so play also works after a pause. Keys go to the iPhone as CarPlay media
 * HID presses ([CarPlayMediaButton]).
 */
internal object CarPlayMediaKeys {
    private const val TAG = "DiPlay-MediaKeys"
    private const val ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var controller: CarPlayController? = null
    private var session: MediaSession? = null
    private var focusRequest: AudioFocusCompat? = null
    private var focusHeld = false
    private var appContext: Context? = null
    private var h6FocusPolicy = false
    private var focusChange: (Int) -> Unit = {}
    @Volatile private var mediaFocusToken: Any? = null

    @Synchronized
    fun attach(context: Context, next: CarPlayController,
        h6FocusPolicy: Boolean = false, onFocusChange: (Int) -> Unit = {}, mediaFocusToken: Any? = null) {
        if (controller !== next) releaseLocked()
        appContext = context.applicationContext
        controller = next
        this.h6FocusPolicy = h6FocusPolicy
        focusChange = onFocusChange
        this.mediaFocusToken = mediaFocusToken
        next.playbackListener = { playing ->
            synchronized(this) { if (controller === next) onIphonePlaying(playing) }
        }
    }

    /** Ends key handling for [expected]; a newer controller's state is left alone. */
    @Synchronized
    fun detach(expected: CarPlayController?) {
        if (expected == null || controller !== expected) return
        expected.playbackListener = null
        controller = null
        releaseLocked()
    }

    /** Called when CarPlay music starts or stops; may run on any thread. */
    fun onMediaAudioChanged(active: Boolean, token: Any? = null) {
        // The sink holds its lifecycle lock here. Never take the focus lock until posted:
        // focus callbacks apply volume in the opposite direction (focus -> sink).
        if (mediaFocusToken !== token) return
        val expected = controller
        mainHandler.post { synchronized(this) { if (controller === expected && mediaFocusToken === token) updateLocked(active) } }
    }

    /** The iPhone started or stopped playing; may run on any thread. */
    fun onIphonePlaying(playing: Boolean) {
        val expected = synchronized(this) { controller }
        if (playing) mainHandler.post { synchronized(this) { if (controller === expected) regainFocusLocked() } }
    }

    // Another car app (its own Spotify, the radio) took audio focus and with it the steering-wheel
    // keys. When CarPlay starts playing again it becomes the car's media source again, as any player
    // would; only the start counts, so a car source picked while the iPhone plays on is not undone.
    private fun regainFocusLocked() {
        val request = focusRequest ?: return
        if (focusHeld) return
        focusHeld = request.request() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (h6FocusPolicy && focusHeld) focusChange(AudioManager.AUDIOFOCUS_GAIN)
        Log.i(TAG, "audio focus regained=$focusHeld")
    }

    private fun updateLocked(active: Boolean) {
        val context = appContext ?: return
        if (controller == null) return
        if (active && session == null) start(context) else if (active) regainFocusLocked()
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(ACTIONS)
                .setState(if (active) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
    }

    private fun start(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        val owner = controller
        val request = AudioFocusCompat(audio, AudioManager.AUDIOFOCUS_GAIN,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            AudioManager.OnAudioFocusChangeListener { change ->
                synchronized(this) {
                    if (controller !== owner) return@OnAudioFocusChangeListener
                    Log.i(TAG, "audio focus change=$change")
                    if (change == AudioManager.AUDIOFOCUS_LOSS) focusHeld = false
                    if (h6FocusPolicy) {
                        focusChange(change)
                        if (change == AudioManager.AUDIOFOCUS_LOSS) owner?.sendMediaButton(CarPlayMediaButton.PAUSE)
                    }
                }
            }, mainHandler)
        val granted = request.request() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (h6FocusPolicy) focusChange(if (granted) AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        focusRequest = request
        focusHeld = granted
        session = MediaSession(context, "DiPlay CarPlay").apply {
            setCallback(callback, mainHandler)
            isActive = true
        }
        Log.i(TAG, "media keys active focusGranted=$granted")
    }

    private fun releaseLocked() {
        session?.let {
            it.isActive = false
            it.release()
        }
        session = null
        focusRequest?.close()
        focusRequest = null
        focusHeld = false
        focusChange = {}
        mediaFocusToken = null
    }

    private fun send(index: Int, source: String) {
        val sent = synchronized(this) { controller }?.sendMediaButton(index) ?: false
        Log.i(TAG, "media key $source -> CarPlay $index sent=$sent")
    }

    private val callback = CarPlayMediaCallback(::send)
}

/**
 * Media-session input → CarPlay presses. Hardware keys arrive as button events and keep the toggle;
 * media controllers (not hardware keys) call [onPlay] and [onPause] with an explicit intent.
 */
internal class CarPlayMediaCallback(private val send: (index: Int, source: String) -> Unit) : MediaSession.Callback() {
    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
        @Suppress("DEPRECATION")
        val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
        val index = CarPlayMediaButton.forKeyCode(event.keyCode) ?: return super.onMediaButtonEvent(mediaButtonIntent)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            send(index, KeyEvent.keyCodeToString(event.keyCode))
        }
        return true
    }

    override fun onPlay() = send(CarPlayMediaButton.PLAY, "play")
    override fun onPause() = send(CarPlayMediaButton.PAUSE, "pause")
    override fun onSkipToNext() = send(CarPlayMediaButton.NEXT, "next")
    override fun onSkipToPrevious() = send(CarPlayMediaButton.PREVIOUS, "previous")
}
