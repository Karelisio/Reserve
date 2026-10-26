package com.karelisio.reserve

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.karelisio.reserve.diagnostics.Diagnostics
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DiagnosticsTest {
    @Test
    fun logsAppearInReportAndIssueUrl() {
        Diagnostics.install(ApplicationProvider.getApplicationContext())
        Diagnostics.log("bornes", "IRVE indisponible", IllegalStateException("HTTP 404"))
        val report = Diagnostics.report(crash = null)
        assertTrue(report.contains("[bornes] IRVE indisponible — IllegalStateException: HTTP 404"))
        assertNull(Diagnostics.pendingCrash())
        val url = Diagnostics.githubIssueIntent(null).data.toString()
        assertTrue(url.startsWith("https://github.com/Karelisio/Reserve/issues/new?title="))
    }
}
