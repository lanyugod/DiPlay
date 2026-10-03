package com.shilapi.xcertplay.compat

import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.*
import org.junit.Test

class PlatformCapabilitiesTest {
    @Test fun legacyHotspotChoicesAreManualAtTheSamePlatformCutpoints() {
        for (api in listOf(23, 26, 28, 29, 37)) {
            val platform = PlatformCapabilities(api)
            assertEquals(WirelessHotspotMode.MANUAL, platform.effectiveHotspotMode(WirelessHotspotMode.LOCAL_ONLY_HOTSPOT))
            assertEquals(if (api < 29) WirelessHotspotMode.MANUAL else WirelessHotspotMode.WIFI_P2P,
                platform.effectiveHotspotMode(WirelessHotspotMode.WIFI_P2P))
            assertEquals(api >= 26, platform.timedUsbRequests)
            assertEquals(api >= 24, platform.systemUnderrunCount)
            assertEquals(api >= 29, platform.audioTrackAttributes)
        }
    }
    @Test fun androidVersionAloneDoesNotSelectH6() {
        assertTrue(H6PlatformProfile.matches(23, "IHU01", "j6headunit"))
        assertFalse(H6PlatformProfile.matches(23, "other", "j6headunit"))
        assertFalse(H6PlatformProfile.matches(28, "IHU01", "j6headunit"))
    }
}
