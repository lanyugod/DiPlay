package com.shilapi.xcertplay

import android.app.Activity
import android.view.View
import android.view.Gravity
import android.provider.Settings
import android.view.WindowManager.LayoutParams
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class H6SystemBarsTest {
    @Suppress("DEPRECATION")
    @Test fun returningFromAnImmersiveWindowLeavesTheFactoryNavigationBarAvailable() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val window = activity.get().window
        window.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON or
            LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        H6SystemBars.apply(window, hideStatusBar = true)
        H6SystemBars.apply(window, hideStatusBar = true)
        assertEquals(0, window.decorView.systemUiVisibility and
            (View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY))
        assertEquals(0, window.attributes.flags and LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        assertTrue(window.attributes.flags and LayoutParams.FLAG_FULLSCREEN != 0)
        assertTrue(window.attributes.flags and LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        H6SystemBars.apply(window, hideStatusBar = false)
        assertEquals(0, window.attributes.flags and LayoutParams.FLAG_FULLSCREEN)
        assertEquals(0, window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        activity.pause().stop().destroy()
    }

    @Test fun aFactoryReportedUsableWidthDeterminesTheWindowWithoutAHardcodedInset() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val window = activity.get().window
        val resources = activity.get().resources
        val configuration = android.content.res.Configuration(resources.configuration)
        configuration.screenWidthDp = 1160
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        H6SystemBars.apply(window, hideStatusBar = true)
        assertEquals((1160 * resources.displayMetrics.density).toInt(), window.attributes.width)
        assertEquals(Gravity.TOP or Gravity.RIGHT, window.attributes.gravity)
        configuration.screenWidthDp = 900
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        H6SystemBars.apply(window, hideStatusBar = true)
        assertEquals((900 * resources.displayMetrics.density).toInt(), window.attributes.width)
        Settings.Global.putString(activity.get().contentResolver, "policy_control",
            "immersive.navigation=${activity.get().packageName},com.autonavi.amapauto")
        H6SystemBars.apply(window, hideStatusBar = true)
        assertEquals(LayoutParams.MATCH_PARENT, window.attributes.width)
        Settings.Global.putString(activity.get().contentResolver, "policy_control", null)
        H6SystemBars.apply(window, hideStatusBar = true)
        assertEquals((900 * resources.displayMetrics.density).toInt(), window.attributes.width)
        activity.pause().stop().destroy()
    }
}
