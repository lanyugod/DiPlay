package com.shihab.diplay.android6probe

import android.net.VpnService
import android.os.Binder
import android.os.ParcelFileDescriptor

class ProbeVpnService : VpnService() {
    inner class Handle : Binder() { val service get() = this@ProbeVpnService }
    private var tun: ParcelFileDescriptor? = null
    override fun onBind(intent: android.content.Intent?) = Handle()
    fun establishProbe(): Boolean {
        stopProbe()
        // An isolated address with no routes: this probe does not capture the car's traffic.
        tun = Builder().setSession("DiPlay API23 probe").addAddress("fd00:23::1", 128)
            .setMtu(1500).establish()
        return tun != null
    }
    fun stopProbe() { tun?.close(); tun = null }
    override fun onRevoke() { stopProbe(); super.onRevoke() }
    override fun onDestroy() { stopProbe(); super.onDestroy() }
}
