package com.shilapi.xcertplay.transport

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class IphoneCarPlayConfigurationTest {
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
}
