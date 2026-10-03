package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertEquals
import org.junit.Test

class CarPlayDisplayScaleTest {
    @Test
    fun scalesHandshakeDisplayAtSupportedSteps() {
        val native = AirPlayDisplayConfig(widthPixels = 1080, heightPixels = 2160)

        val half = CarPlayDisplayScale.apply(native, 5)

        assertEquals(540, half.widthPixels)
        assertEquals(1080, half.heightPixels)
        assertEquals("0.5x", CarPlayDisplayScale.label(5))
    }

    @Test
    fun h6AppSurfaceKeepsItsAspectAndNeverSubtractsTheSystemStripTwice() {
        val surface = AirPlayDisplayConfig(widthPixels = 1160, heightPixels = 720, fps = 30)
        assertEquals(surface, CarPlayDisplayScale.apply(surface, 10))
        val scaled = CarPlayDisplayScale.apply(surface, 8)
        assertEquals(928, scaled.widthPixels)
        assertEquals(576, scaled.heightPixels)
        val physicalSurface = surface.copy(widthPixels = 1280)
        assertEquals(1280, CarPlayDisplayScale.apply(physicalSurface, 10).widthPixels)
    }

    @Test
    fun clampsScaleToTheUiRange() {
        assertEquals(3, CarPlayDisplayScale.sanitize(0))
        assertEquals(10, CarPlayDisplayScale.sanitize(20))
    }

    @Test
    fun alignsScaledDisplayDimensionsToEvenPixels() {
        val native = AirPlayDisplayConfig(widthPixels = 1920, heightPixels = 978)

        val scaled = CarPlayDisplayScale.apply(native, 7)

        assertEquals(1344, scaled.widthPixels)
        assertEquals(686, scaled.heightPixels)
    }
}
