package com.shilapi.xcertplay.transport

import org.junit.Assert.*
import org.junit.Test

class H6UsbDriverReleaseTest {
    @Test fun alreadyUnboundIsAcceptedButPermissionAndDeviceFailuresAreNot() {
        assertTrue(H6UsbDriverRelease.disconnectSucceeded(0))
        assertTrue(H6UsbDriverRelease.disconnectSucceeded(61))
        for (errno in listOf(1, 9, 13, 16, 19, 22)) {
            assertFalse(H6UsbDriverRelease.disconnectSucceeded(errno))
        }
    }

    @Test fun onlyAudioHidAndCdcNcmInterfacesAreEligibleForExplicitRecovery() {
        assertTrue(H6UsbDriverRelease.isRecoveryInterface(1, 1))
        assertTrue(H6UsbDriverRelease.isRecoveryInterface(3, 0))
        assertTrue(H6UsbDriverRelease.isRecoveryInterface(2, 0x0d))
        assertTrue(H6UsbDriverRelease.isRecoveryInterface(0x0a, 0))
        assertFalse(H6UsbDriverRelease.isRecoveryInterface(0xff, 0xfe))
        assertFalse(H6UsbDriverRelease.isRecoveryInterface(0xff, 0xfd))
        assertFalse(H6UsbDriverRelease.isRecoveryInterface(6, 1))
        assertFalse(H6UsbDriverRelease.isRecoveryInterface(2, 6))
    }
}
