package com.shilapi.xcertplay.transport

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbInterface
import android.os.Parcelable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class IphoneCarPlayConfigurationTest {
    @Test fun theH6SnapshotSelectsCarPlayInsteadOfTheProbesFirstUsbMuxConfiguration() {
        val mux = face(0xff, 0xfe, 2)
        val ethernet = face(0xff, 0xfd, 1)
        val ncm = face(2, 0x0d, 0)
        val data = face(0x0a, 0, 0)
        val configurations = listOf(
            config(2, face(1, 1, 0), face(3, 0, 0)),
            config(3, mux), config(4, mux, ethernet),
            config(5, mux, ncm, data), config(6, mux, ncm, data, ethernet),
        )
        assertEquals(6, IphoneCarPlayConfiguration.find(configurations)!!.id)
    }

    @Test fun configurationNumbersAreNotHardcodedAndUsbMuxOnlyIsInsufficient() {
        val mux = face(0xff, 0xfe, 2)
        assertNull(IphoneCarPlayConfiguration.find(listOf(config(3, mux))))
        assertEquals(9, IphoneCarPlayConfiguration.find(listOf(
            config(3, mux), config(9, mux, face(2, 0x0d, 0), face(0x0a, 0, 0)),
        ))!!.id)
    }

    @Test fun occupiedAudioConfigurationDoesNotProceedToUsbMux() {
        val error = assertThrows(IphoneUsbException.ResourceUnavailable::class.java) {
            IphoneCarPlayConfiguration.select(5, { false }) { it[0] = 2; 1 }
        }
        assertTrue(error.message!!.contains("active=2"))
    }

    @Test fun failedSetterMayProceedOnlyWhenTheTargetIsAlreadyActive() {
        IphoneCarPlayConfiguration.select(6, { false }) { it[0] = 6; 1 }
    }

    @Test fun unreadableConfigurationNeverMasqueradesAsSelected() {
        for (length in listOf(-1, 0, 2)) {
            assertThrows(IphoneUsbException.ResourceUnavailable::class.java) {
                IphoneCarPlayConfiguration.select(5, { false }) { it[0] = 5; length }
            }
        }
    }

    @Test fun successfulSelectionDoesNotRequireAnAdditionalControlRequest() {
        IphoneCarPlayConfiguration.select(5, { true }) { fail("setter succeeded"); -1 }
    }

    private fun face(klass: Int, subclass: Int, protocol: Int): UsbInterface =
        UsbInterface::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
            .newInstance(0, 0, "test", klass, subclass, protocol).also {
                UsbInterface::class.java.getDeclaredMethod("setEndpoints", Array<Parcelable>::class.java)
                    .apply { isAccessible = true }.invoke(it, emptyArray<Parcelable>())
            }

    private fun config(id: Int, vararg faces: UsbInterface): UsbConfiguration =
        UsbConfiguration::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType,
            String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.newInstance(id, "test", 0, 0).also {
                UsbConfiguration::class.java.getDeclaredMethod("setInterfaces", Array<Parcelable>::class.java)
                    .apply { isAccessible = true }.invoke(it, arrayOf<Parcelable>(*faces))
            }
}
