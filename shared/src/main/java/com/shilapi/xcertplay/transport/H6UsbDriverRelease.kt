package com.shilapi.xcertplay.transport

import android.content.Context
import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

/** One controller-scoped capability, granted only by the H6 foreground recovery action. */
class H6UsbDriverRelease(context: Context, private val diagnostic: (String) -> Unit) {
    private val manager = context.getSystemService(UsbManager::class.java)
    private var target: UsbDevice? = null
    private var original: UsbConfiguration? = null

    @Synchronized
    fun release(device: UsbDevice) {
        if (device.vendorId != 0x05ac || (target != null && !sameDevice(target!!, device))) {
            unavailable("iPhone changed during recovery; reconnect and retry manually")
        }
        if (!manager.hasPermission(device)) unavailable("USB permission is required to release the selected iPhone")
        val connection = manager.openDevice(device) ?: unavailable("Could not open selected iPhone for interface release")
        try {
            val configuration = activeConfiguration(connection, device)
            if (original == null) { target = device; original = configuration }
            detach(connection, configuration)
            diagnostic("H6 USB interfaces released for selected iPhone; active=${configuration.id}")
        } finally { connection.close() }
    }

    /** After the controller worker and all native USB readers have exited. */
    @Synchronized
    fun restore() {
        val saved = target ?: return
        val configuration = original ?: return
        LegacyUsbReadPump.assertReconnectAllowed()
        val current = manager.deviceList[saved.deviceName]
        if (current == null || !sameDevice(saved, current)) {
            diagnostic("H6 USB restore: original iPhone detached; no new device touched")
            target = null; original = null
            return
        }
        val connection = manager.openDevice(current) ?: unavailable("USB restore unavailable; reconnect iPhone cable")
        try {
            detach(connection, activeConfiguration(connection, current))
            if (!connection.setConfiguration(configuration)) {
                unavailable("Could not restore iPhone USB configuration; reconnect the cable")
            }
            check(activeConfiguration(connection, current).id == configuration.id)
            diagnostic("H6 USB restore: original configuration=${configuration.id}")
            target = null; original = null
        } finally { connection.close() }
    }

    private fun activeConfiguration(connection: UsbDeviceConnection, device: UsbDevice): UsbConfiguration {
        val value = ByteArray(1)
        if (connection.controlTransfer(0x80, 8, 0, 0, value, 1, 1000) != 1) {
            unavailable("Could not read active iPhone configuration; reconnect the cable")
        }
        return (0 until device.configurationCount).map(device::getConfiguration)
            .firstOrNull { it.id == (value[0].toInt() and 255) }
            ?: unavailable("Unknown active iPhone configuration; no interfaces released")
    }

    private fun detach(connection: UsbDeviceConnection, configuration: UsbConfiguration) {
        for (face in recoveryInterfaces(configuration)) {
            val status = H6UsbRecoveryNative.disconnect(connection.fileDescriptor, face.id)
            if (!disconnectSucceeded(status)) {
                unavailable("Could not detach iPhone interface ${face.id} (errno=$status); reconnect the cable")
            }
        }
    }

    private fun unavailable(message: String): Nothing = throw IphoneUsbException.ResourceUnavailable(message)

    internal companion object {
        // USBDEVFS_IOCTL returns ENODATA when no driver is bound (including a paired CDC interface).
        fun disconnectSucceeded(status: Int): Boolean = status == 0 || status == 61

        fun sameDevice(first: UsbDevice, second: UsbDevice): Boolean =
            first.deviceName == second.deviceName && first.vendorId == second.vendorId &&
                first.productId == second.productId

        fun isRecoveryInterface(klass: Int, subclass: Int): Boolean =
            klass == 1 || klass == 3 || (klass == 2 && subclass == 0x0d) || klass == 0x0a

        fun recoveryInterfaces(configuration: UsbConfiguration): List<UsbInterface> =
            (0 until configuration.interfaceCount).map(configuration::getInterface)
                .filter { isRecoveryInterface(it.interfaceClass, it.interfaceSubclass) }
                .distinctBy { it.id }
    }
}
