package com.shilapi.xcertplay.media

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class CarPlayTouchMapperTest {
    @Test fun localSurfaceCornersAndDragDoNotSubtractTheSystemStrip() {
        for ((x, y) in listOf(0f to 0f, 580f to 360f, 1160f to 720f)) {
            val event = MotionEvent.obtain(0, 1, MotionEvent.ACTION_MOVE, x, y, 0)
            try {
                val contact = CarPlayTouchMapper.contacts(event, 1160, 720).single()
                assertEquals(x.toDouble() / 1160, contact.x, 0.000001)
                assertEquals(y.toDouble() / 720, contact.y, 0.000001)
                assertTrue(contact.down)
            } finally { event.recycle() }
        }
        val up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 580f, 360f, 0)
        try { assertFalse(CarPlayTouchMapper.contacts(up, 1160, 720).single().down) }
        finally { up.recycle() }
    }
}
