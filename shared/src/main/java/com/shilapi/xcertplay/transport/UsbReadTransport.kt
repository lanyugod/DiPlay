package com.shilapi.xcertplay.transport

import java.io.Closeable
import java.nio.ByteBuffer

internal sealed class UsbReadResult {
    data class Data(val bytes: ByteArray) : UsbReadResult()
    data object Timeout : UsbReadResult()
    data class Terminated(val cause: Throwable) : UsbReadResult()
}

internal interface UsbReadTransport : Closeable {
    fun read(timeoutMillis: Long): UsbReadResult
}

/** Injected boundary also lets JVM tests exercise native-wait ownership and cancellation races. */
internal interface LegacyUsbDriver : Closeable {
    fun initialize(): Boolean
    fun queue(buffer: ByteBuffer, length: Int): Boolean
    fun waitForCompletion(): Boolean
    fun cancel(): Boolean
    fun closeConnection()
}

