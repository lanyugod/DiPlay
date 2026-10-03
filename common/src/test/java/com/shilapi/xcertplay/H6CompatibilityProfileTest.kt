package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class H6CompatibilityProfileTest {
    @Test fun explicitProfilePreservesRequestedSettingsAndRecordsEffectiveSettings() {
        val context = RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveWirelessEnabled(context, true)
        AirPlayPersistence.saveHevcEnabled(context, true)
        AirPlayPersistence.saveFps(context, 60)
        H6CompatibilityProfile.select(context, true)
        assertTrue(H6CompatibilityProfile.enabled(context))
        val summary = H6CompatibilityProfile.summary(context)
        assertTrue(summary.contains("hevc=true")); assertTrue(summary.contains("fps=60"))
        assertTrue(summary.contains("H.264/30fps")); assertTrue(summary.contains("microphone=false"))
        H6CompatibilityProfile.select(context, false)
        assertTrue(AirPlayPersistence.loadHevcEnabled(context))
        assertEquals(60, AirPlayPersistence.loadFps(context))
    }
}
