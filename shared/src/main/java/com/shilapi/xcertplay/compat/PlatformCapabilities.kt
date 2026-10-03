package com.shilapi.xcertplay.compat

import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/** Platform facts; these never identify a vehicle or prove an OEM driver works. */
data class PlatformCapabilities(val api: Int) {
    val timedUsbRequests: Boolean get() = api >= 26
    val systemUnderrunCount: Boolean get() = api >= 24
    val audioTrackAttributes: Boolean get() = api >= 29
    fun effectiveHotspotMode(requested: WirelessHotspotMode): WirelessHotspotMode =
        if (requested == WirelessHotspotMode.LOCAL_ONLY_HOTSPOT ||
            (api < 29 && requested == WirelessHotspotMode.WIFI_P2P)) WirelessHotspotMode.MANUAL else requested
}
