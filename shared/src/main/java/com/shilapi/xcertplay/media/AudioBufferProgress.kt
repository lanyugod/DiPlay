package com.shilapi.xcertplay.media

/** Progress belongs to one track generation; a reset must never masquerade as a 2^32 wrap. */
internal class AudioBufferProgress(private val frameBytes: Int) {
    private var writtenBytes = 0L
    private var playedFrames = 0L
    private var lastHead = 0L
    private var progressAvailable = true
    var generation: Long = 0
        private set

    fun reset() {
        generation++
        writtenBytes = 0; playedFrames = 0; lastHead = 0; progressAvailable = true
    }

    fun written(bytes: Int) { if (bytes > 0) writtenBytes += bytes }

    fun queuedBytes(rawHead: Int): Long {
        val head = rawHead.toLong() and 0xffff_ffffL
        val delta = (head - lastHead) and 0xffff_ffffL
        if (head < lastHead && !(lastHead >= 0xf000_0000L && head <= 0x0fff_ffffL)) {
            // Rebase an unexpected head reset, preserving pending data conservatively.
            progressAvailable = false
        } else {
            playedFrames += delta
            progressAvailable = true
        }
        lastHead = head
        return (writtenBytes / frameBytes - playedFrames).coerceAtLeast(0) * frameBytes
    }

    fun shouldRebuffer(isMedia: Boolean, playing: Boolean, underrunSinceStart: Boolean,
        compressedQueueEmpty: Boolean, rawHead: Int): Boolean {
        // Always sample, including speech, paused tracks and non-underrun checks.
        val queued = queuedBytes(rawHead)
        return progressAvailable && isMedia && playing && underrunSinceStart && compressedQueueEmpty && queued == 0L
    }
}
