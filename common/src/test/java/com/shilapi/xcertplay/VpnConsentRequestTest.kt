package com.shilapi.xcertplay

import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class VpnConsentRequestTest {
    @Test fun existingConsentDoesNotOpenAnotherDialog() {
        val result = VpnConsentRequest.request({ null }) { fail("Already authorized") }
        assertSame(VpnConsentRequest.Result.Ready, result)
    }

    @Test fun launchingDialogDoesNotGrantConsentBeforeItsResult() {
        val intent = Intent("vpn-consent")
        var launched: Intent? = null
        val result = VpnConsentRequest.request({ intent }) { launched = it }
        assertSame(intent, launched)
        assertSame(VpnConsentRequest.Result.Requested, result)
    }

    @Test fun missingHeadUnitDialogRemainsUnavailableRatherThanAuthorized() {
        val error = ActivityNotFoundException("com.android.vpndialogs/.ConfirmDialog")
        val result = VpnConsentRequest.request({ Intent("vpn-consent") }) { throw error }
        assertSame(error, (result as VpnConsentRequest.Result.Unavailable).cause)
    }

    @Test fun deniedPreparationDoesNotTryToLaunchOrGrantConsent() {
        val error = SecurityException("VPN service denied preparation")
        val result = VpnConsentRequest.request({ throw error }) { fail("Preparation failed") }
        assertSame(error, (result as VpnConsentRequest.Result.Unavailable).cause)
    }
}
