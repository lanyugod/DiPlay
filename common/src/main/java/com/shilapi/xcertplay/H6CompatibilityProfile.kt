package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.compat.H6PlatformProfile

/** Effective settings are derived; the saved choices survive switching back to another device. */
internal object H6CompatibilityProfile {
    fun enabled(context: Context) = H6PlatformProfile.enabled(context)
    fun wirelessEnabled(context: Context) = !enabled(context) && AirPlayPersistence.loadWirelessEnabled(context)
    fun select(context: Context, enabled: Boolean) {
        context.getSharedPreferences(H6PlatformProfile.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(H6PlatformProfile.KEY_H6, enabled).apply()
    }
    fun summary(context: Context): String =
        if (enabled(context)) "H6 phase1 requested: wireless=${AirPlayPersistence.loadWirelessEnabled(context)}, " +
            "hevc=${AirPlayPersistence.loadHevcEnabled(context)}, fps=${AirPlayPersistence.loadFps(context)}, " +
            "location=${AirPlayPersistence.loadLocationReportingEnabled(context)}; " +
            "effective: USB, H.264/30fps, one Surface, microphone=false, mobile audio, BYD=false, location=false"
        else "Compatibility profile: default"
}
