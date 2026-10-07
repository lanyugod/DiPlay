package com.shilapi.xcertplay.transport

/** Uses only the fd opened by UsbManager after USB permission; no root or local ADB. */
internal object H6UsbRecoveryNative {
    init { System.loadLibrary("h6_usb_recovery") }
    external fun disconnect(fd: Int, interfaceId: Int): Int
}
