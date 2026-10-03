package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class Android6DiagnosticExportTest {
    @Test fun privateFallbackExposesOnlyItsReportThroughTheManifestProvider() {
        val context = RuntimeEnvironment.getApplication()
        val report = "API23 · report\n"
        val uri = DiagnosticExportStore.savePrivate(context, "DiPlay-test.txt", report)
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.diagnostics", uri.authority)
        assertEquals(report, context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        assertThrows(IllegalArgumentException::class.java) { DiagnosticExportStore.savePrivate(context, "../outside.txt", "report") }
    }
}
