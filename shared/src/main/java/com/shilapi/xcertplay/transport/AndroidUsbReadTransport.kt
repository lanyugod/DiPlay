package com.shilapi.xcertplay.transport

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbRequest
import java.nio.ByteBuffer

internal fun legacyUsbTransport(connection: UsbDeviceConnection, endpoint: UsbEndpoint): LegacyUsbReadPump =
    LegacyUsbReadPump(object : LegacyUsbDriver {
        private val request = UsbRequest()
        override fun initialize() = request.initialize(connection, endpoint)
        @Suppress("DEPRECATION")
        override fun queue(buffer: ByteBuffer, length: Int) = request.queue(buffer, length)
        override fun waitForCompletion() = connection.requestWait() === request
        override fun cancel() = request.cancel()
        override fun closeConnection() { connection.close() }
        override fun close() { request.close() }
    }, diagnostic = { android.util.Log.i(IphoneCarPlayConfiguration.TAG, it) })

internal fun UsbReadResult.dataOrThrow(): ByteArray? = when (this) {
    is UsbReadResult.Data -> bytes
    UsbReadResult.Timeout -> null
    is UsbReadResult.Terminated -> throw IphoneUsbException.DeviceUnavailable("Legacy USB read terminated: ${cause.message}", cause)
}
