package com.shilapi.xcertplay

import android.view.View
import android.view.Gravity
import android.view.Window
import android.view.WindowManager.LayoutParams
import android.provider.Settings
import androidx.core.view.WindowCompat

/** The H6's persistent left navigation bar must remain outside the app window. */
internal object H6SystemBars {
    @Suppress("DEPRECATION")
    fun apply(window: Window, hideStatusBar: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        // This firmware lays windows drawing bar backgrounds across the left navigation bar.
        // Use the opaque legacy window policy, as the factory navigation app does.
        window.clearFlags(LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
            LayoutParams.FLAG_TRANSLUCENT_STATUS or LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY).inv()
        if (hideStatusBar) window.addFlags(LayoutParams.FLAG_FULLSCREEN)
        else window.clearFlags(LayoutParams.FLAG_FULLSCREEN)
        // The OEM reports 1160dp of usable width but still gives unlisted apps a 1280px
        // window with zero insets. Right-align to that configuration width; if the OEM
        // already offsets the window, the same width fits without reserving the bar twice.
        val policy = Settings.Global.getString(window.context.contentResolver, "policy_control")
        val navigationForcedHidden = navigationForcedHidden(policy, window.context.packageName)
        val configuration = window.context.resources.configuration
        if (configuration.screenWidthDp > 0) {
            val usableWidth = (configuration.screenWidthDp *
                window.context.resources.displayMetrics.density).toInt()
            val attributes = window.attributes
            attributes.width = if (navigationForcedHidden) LayoutParams.MATCH_PARENT else usableWidth
            attributes.height = LayoutParams.MATCH_PARENT
            attributes.gravity = Gravity.TOP or Gravity.RIGHT
            window.attributes = attributes
        }
    }

    /** Recognize the explicit package filters used by the optional owner-applied ADB policy. */
    fun navigationForcedHidden(policy: String?, packageName: String): Boolean =
        policy?.split(':')?.any { entry ->
            val key = entry.substringBefore('=')
            val packages = entry.substringAfter('=', "").split(',').map { it.trim() }
            (key == "immersive.navigation" || key == "immersive.full") &&
                packageName in packages && "-$packageName" !in packages
        } == true
}
