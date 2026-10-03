package com.shilapi.xcertplay

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 26])
class Android6ServiceTest {
    @Test fun oldNotificationIncludesTheExplicitDisconnectAction() {
        val controller = Robolectric.buildService(DiPlaySessionService::class.java).create()
        val service = controller.get()
        try {
            val notification = service.buildSessionNotification()
            assertEquals("Disconnect", notification.actions.single().title)
            assertNotNull(notification.actions.single().actionIntent)
            assertEquals(android.app.Service.START_NOT_STICKY,
                service.onStartCommand(Intent(service, DiPlaySessionService::class.java), 0, 1))
        } finally { controller.destroy() }
    }
}
