package com.karelisio.reserve

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches the app on a device and visits every top-level screen, without depending on the
 * network (prices may never load on the emulator). Texts are matched in French or English,
 * whatever the device language. Run by the "UI tests" workflow.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    /** The runtime permissions asked at launch, so that no system dialog hides the app. */
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        *buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.toTypedArray(),
    )

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun waitForApp() {
        // The splash screen stays until the settings are read.
        compose.waitUntil(LAUNCH_TIMEOUT_MS) { exists(tab(TABS.first())) }
        dismissLaunchDialogs()
    }

    @Test
    fun launch_showsBottomNavigation() {
        TABS.forEach { label -> compose.onNode(tab(label)).assertExists() }
        compose.onNode(tab(TABS.first())).assertIsSelected()
        assertAlive()
    }

    @Test
    fun everyTopLevelTab_opensWithoutCrash() {
        // Around the tabs, then back to the first one.
        (TABS.drop(1) + TABS.first()).forEach { label ->
            compose.onNode(tab(label)).performClick()
            compose.waitForIdle()
            dismissLaunchDialogs()
            compose.waitUntil(SCREEN_TIMEOUT_MS) { exists(tab(label)) }
            compose.onNode(tab(label)).assertIsSelected()
            assertAlive()
        }
    }

    @Test
    fun settings_opensFromHomeTopBar() {
        compose.onNode(either(hasContentDescription("Réglages"), hasContentDescription("Settings")) and hasClickAction())
            .performClick()
        compose.waitUntil(SCREEN_TIMEOUT_MS) { exists(either(hasText("Réglages"), hasText("Settings"))) }
        // Settings is not a top-level screen: the navigation bar is hidden.
        compose.waitUntil(SCREEN_TIMEOUT_MS) { !exists(tab(TABS.first())) }
        assertAlive()

        device.pressBack()
        compose.waitUntil(SCREEN_TIMEOUT_MS) { exists(tab(TABS.first())) }
        compose.onNode(tab(TABS.first())).assertIsSelected()
        assertAlive()
    }

    /**
     * Dialogs that may open at launch: the background location explanation (debug builds skip
     * the update ones, BuildConfig.UPDATES_ENABLED is false) and the report of a previous crash.
     * They appear asynchronously, after the settings are read, hence the short polling.
     */
    private fun dismissLaunchDialogs() {
        val later = either(hasText("Plus tard"), hasText("Later")) and hasClickAction()
        val crashReport = either(hasText("Reserve s'est arrêtée"), hasText("Reserve stopped"))
        val deadline = System.currentTimeMillis() + DIALOG_GRACE_MS
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            when {
                exists(later) -> compose.onNode(later).performClick()
                // Its buttons open other apps: back closes it and acknowledges the crash.
                exists(crashReport) -> device.pressBack()
                else -> {
                    Thread.sleep(POLL_MS)
                    continue
                }
            }
            compose.waitForIdle()
        }
    }

    private fun assertAlive() {
        compose.waitForIdle()
        assertFalse("MainActivity finished", compose.activity.isFinishing)
    }

    private fun exists(matcher: SemanticsMatcher): Boolean =
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /** A navigation item: clickable, unlike the Favourites screen title with the same text. */
    private fun tab(label: Pair<String, String>) =
        either(hasText(label.first), hasText(label.second)) and hasClickAction()

    private fun either(a: SemanticsMatcher, b: SemanticsMatcher) = a or b

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
        const val POLL_MS = 250L
    }
}
