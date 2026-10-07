package com.shilapi.xcertplay

import android.view.View
import android.widget.Button
import com.shilapi.xcertplay.orchestration.CarPlayStatus
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
class UsbRecoveryStateTest {
    private lateinit var activity: CarPlayHostActivity
    private lateinit var retry: Button
    private lateinit var report: (CarPlayStatus) -> Unit

    @Before fun prepare() {
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        retry = Button(activity).apply { visibility = View.GONE }
        field("usbRecoveryButton").set(activity, retry)
        @Suppress("UNCHECKED_CAST")
        report = activity.javaClass.getDeclaredMethod("createStatusReporter", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, 0) as (CarPlayStatus) -> Unit
    }

    @After fun closeWorkers() {
        for (name in listOf("teardownExecutor", "airPlayCommandExecutor")) {
            (field(name).get(activity) as ExecutorService).shutdownNow()
        }
    }

    @Test fun occupiedUsbWaitsForTheUserRatherThanSchedulingAnotherAttempt() {
        report(CarPlayStatus.Failed("active configuration=2", usbRecoveryRequired = true))
        assertTrue(field("usbRecoveryRequired").getBoolean(activity))
        assertEquals(View.VISIBLE, retry.visibility)
        assertFalse(field("reconnectScheduled").getBoolean(activity))
        assertEquals(0, field("reconnectAttempts").getInt(activity))
    }

    @Test fun staleFailureCannotBlockANewerSession() {
        field("restartGeneration").setInt(activity, 1)
        report(CarPlayStatus.Failed("old configuration", usbRecoveryRequired = true))
        assertFalse(field("usbRecoveryRequired").getBoolean(activity))
        assertEquals(View.GONE, retry.visibility)
    }

    @Test fun h6FailureOffersManualReleaseButNeverGrantsItAutomatically() {
        activity.getSharedPreferences("diplay_compatibility", 0).edit().putBoolean("h6_wired_phase_one", true).commit()
        val release = Button(activity).apply { visibility = View.GONE }
        field("usbReleaseButton").set(activity, release)
        report(CarPlayStatus.Failed("active=2", usbRecoveryRequired = true))
        assertEquals(View.VISIBLE, release.visibility)
        assertFalse(field("releaseUsbOnNextStart").getBoolean(activity))
    }

    @Test fun otherProfilesDoNotOfferTheH6Takeover() {
        activity.getSharedPreferences("diplay_compatibility", 0).edit().clear().commit()
        val release = Button(activity).apply { visibility = View.GONE }
        field("usbReleaseButton").set(activity, release)
        report(CarPlayStatus.Failed("active=2", usbRecoveryRequired = true))
        assertEquals(View.GONE, release.visibility)
        assertFalse(field("releaseUsbOnNextStart").getBoolean(activity))
    }

    private fun field(name: String) = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }
}
