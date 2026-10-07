package com.shilapi.xcertplay

import android.content.Intent

/** Some head units omit the system VPN consent activity. Never treat that as consent. */
internal object VpnConsentRequest {
    sealed interface Result {
        data object Ready : Result
        data object Requested : Result
        data class Unavailable(val cause: RuntimeException) : Result
    }

    fun request(prepare: () -> Intent?, launch: (Intent) -> Unit): Result = try {
        val consent = prepare()
        if (consent == null) Result.Ready else {
            launch(consent)
            Result.Requested
        }
    } catch (error: RuntimeException) {
        Result.Unavailable(error)
    }

    /** A vendor dialog's RESULT_OK alone must not authorize the transport. */
    fun verifyGranted(prepare: () -> Intent?): Result = try {
        if (prepare() == null) Result.Ready else Result.Unavailable(
            SecurityException("VPN consent returned without granting this application"),
        )
    } catch (error: RuntimeException) {
        Result.Unavailable(error)
    }
}
