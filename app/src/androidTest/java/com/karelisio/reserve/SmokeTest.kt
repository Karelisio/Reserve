package com.karelisio.reserve

import android.Manifest
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Launches the app on a device and visits every top-level screen, without depending on the
 * network (prices may never load on the emulator). Texts are matched in French or English,
 * whatever the device language. Run by the "UI tests" workflow.
 *
 * Driven through UiAutomator rather than the Compose test rule: the map keeps an endless
 * animation (the pulse around the user), so Compose would never report the app as idle.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    /** A stuck test fails instead of hanging the whole run. */
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.seconds(120)

    /** The runtime permissions asked at launch, so that no system dialog hides the app. */
    @get:Rule(order = 1)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        *buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.toTypedArray(),
    )

    private val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // The splash screen stays until the settings are read; launch dialogs may cover the app
        // (UiAutomator then only sees the dialog window), so they are closed while waiting.
        val deadline = System.currentTimeMillis() + LAUNCH_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && device.findObject(tab(TABS.first())) == null) {
            closeDialog()
            device.waitForIdle(POLL_MS)
            Thread.sleep(POLL_MS)
        }
        assertNotNull("Navigation bar not shown. On screen: ${screenSummary()}", device.findObject(tab(TABS.first())))
        dismissLaunchDialogs()
    }

    @After
    fun close() {
        scenario.close()
    }

    @Test
    fun launch_showsBottomNavigation() {
        TABS.forEach { label -> assertNotNull("Tab $label missing", find(tab(label))) }
        assertAlive()
    }

    @Test
    fun everyTopLevelTab_opensWithoutCrash() {
        (TABS.drop(1) + TABS.first()).forEach { label ->
            val item = find(tab(label))
            assertNotNull("Tab $label missing", item)
            item!!.click()
            device.waitForIdle(IDLE_MS)
            dismissLaunchDialogs()
            assertNotNull("Tab $label gone after opening it", find(tab(label)))
            assertAlive()
        }
    }

    @Test
    fun settings_opensFromHomeTopBar() {
        val settings = device.wait(Until.findObject(By.desc(words("Réglages", "Settings")).clickable(true)), SCREEN_TIMEOUT_MS)
        assertNotNull("Settings button missing", settings)
        settings!!.click()
        assertNotNull("Settings screen not shown", device.wait(Until.findObject(By.text(words("Réglages", "Settings"))), SCREEN_TIMEOUT_MS))
        assertAlive()

        device.pressBack()
        assertNotNull("Back from settings failed", device.wait(Until.findObject(tab(TABS.first())), SCREEN_TIMEOUT_MS))
        assertAlive()
    }

    /**
     * Dialogs that may open at launch: the background location explanation (debug builds skip
     * the update ones) and the report of a previous crash. They appear after the settings are
     * read, hence the short wait.
     */
    private fun dismissLaunchDialogs() {
        val deadline = System.currentTimeMillis() + DIALOG_GRACE_MS
        while (System.currentTimeMillis() < deadline) {
            if (!closeDialog()) Thread.sleep(POLL_MS)
        }
    }

    /** Closes one launch dialog if shown; true when something was closed. */
    private fun closeDialog(): Boolean {
        val later = device.findObject(By.text(words("Plus tard", "Later")))
        // A slow emulator sometimes shows "<System app> isn't responding" over everything.
        val systemWait = if (device.hasObject(By.textContains("isn't responding")) || device.hasObject(By.textContains("ne répond pas"))) {
            device.findObject(By.text(words("Attendre", "Wait")))
        } else null
        return when {
            systemWait != null -> { systemWait.click(); true }
            later != null -> { later.click(); true }
            // Its buttons open other apps: back closes it and acknowledges the crash.
            device.hasObject(By.text(Pattern.compile("Reserve (s'est arrêtée|stopped).*"))) -> { device.pressBack(); true }
            else -> false
        }
    }

    /** Texts and descriptions on screen, to understand a failure from the CI log alone. */
    private fun screenSummary(): String {
        val out = java.io.ByteArrayOutputStream()
        runCatching { device.dumpWindowHierarchy(out) }
        val xml = out.toString()
        val values = Regex("""(?:text|content-desc|package)="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.distinct()
        return "[current=${device.currentPackageName}] " + values.joinToString(" | ").take(1500)
    }

    private fun assertAlive() {
        assertTrue("App no longer in front", device.wait(Until.hasObject(By.pkg(packageName).depth(0)), SCREEN_TIMEOUT_MS))
    }

    private fun find(selector: BySelector): UiObject2? = device.wait(Until.findObject(selector), SCREEN_TIMEOUT_MS)

    /**
     * A navigation item. Compose merges the label into the clickable item, so the text is on
     * the item itself or on a child, depending on the version.
     */
    private fun tab(label: Pair<String, String>): BySelector = By.text(words(label.first, label.second))

    private fun words(fr: String, en: String): Pattern = Pattern.compile("${Pattern.quote(fr)}|${Pattern.quote(en)}")

    private companion object {
        /** French and English labels of the navigation items (see ReserveNavHost). */
        val TABS = listOf(
            "Autour" to "Nearby",
            "Carte" to "Map",
            "Trajet" to "Trip",
            "Favoris" to "Favourites",
            "Carnet" to "Log",
        )
        const val LAUNCH_TIMEOUT_MS = 30_000L
        const val SCREEN_TIMEOUT_MS = 10_000L
        const val DIALOG_GRACE_MS = 3_000L
        const val IDLE_MS = 2_000L
        const val POLL_MS = 250L
    }
}
