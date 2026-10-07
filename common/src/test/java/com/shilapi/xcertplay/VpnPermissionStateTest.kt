package com.shilapi.xcertplay

import android.view.View
import android.widget.Button
import java.util.concurrent.ExecutorService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class VpnPermissionStateTest {
    private lateinit var activity: CarPlayHostActivity
    private lateinit var recheck: Button

    @Before fun prepare() {
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        recheck = Button(activity)
        field("vpnRecoveryButton").set(activity, recheck)
    }

    @After fun closeWorkers() {
        for (name in listOf("teardownExecutor", "airPlayCommandExecutor")) {
            (field(name).get(activity) as ExecutorService).shutdownNow()
        }
    }

    @Test fun missingConsentClearsStaleReadinessAndAllowsRecheck() {
        field("vpnReady").setBoolean(activity, true)
        field("awaitingVpnConsent").setBoolean(activity, true)
        apply(VpnConsentRequest.Result.Unavailable(SecurityException("Missing consent")))
        assertFalse(field("vpnReady").getBoolean(activity))
        assertFalse(field("awaitingVpnConsent").getBoolean(activity))
        assertEquals(View.VISIBLE, recheck.visibility)
    }

    @Test fun anExternalGrantForThisAppClearsTheBlockedState() {
        apply(VpnConsentRequest.verifyGranted { null })
        assertTrue(field("vpnReady").getBoolean(activity))
        assertFalse(field("awaitingVpnConsent").getBoolean(activity))
        assertEquals(View.GONE, recheck.visibility)
    }

    @Test fun openingAConsentDialogDoesNotAuthorizeVpn() {
        field("vpnReady").setBoolean(activity, true)
        apply(VpnConsentRequest.Result.Requested)
        assertFalse(field("vpnReady").getBoolean(activity))
    }

    private fun apply(result: VpnConsentRequest.Result) {
        activity.javaClass.getDeclaredMethod("applyVpnConsentResult", VpnConsentRequest.Result::class.java)
            .apply { isAccessible = true }.invoke(activity, result)
    }

    private fun field(name: String) = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }
}
