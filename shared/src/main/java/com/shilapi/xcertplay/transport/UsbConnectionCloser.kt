package com.shilapi.xcertplay.transport

/** Serializes native close as well as making it idempotent on the measured API23 ROM. */
internal class UsbConnectionCloser(private val closeNative: () -> Unit) {
    private var closed = false
    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        closeNative()
    }
}
