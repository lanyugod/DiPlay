package com.shilapi.xcertplay.compat

import android.content.Context
import android.os.Build

/** Exact measured target or a user-selected profile, never all Android 6 devices. */
object H6PlatformProfile {
    const val PREFS = "diplay_compatibility"
    const val KEY_H6 = "h6_wired_phase_one"
    fun matches(api: Int, model: String, device: String): Boolean =
        api == 23 && model == "IHU01" && device == "j6headunit"
    fun enabled(context: Context): Boolean =
        matches(Build.VERSION.SDK_INT, Build.MODEL, Build.DEVICE) ||
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_H6, false)
}
