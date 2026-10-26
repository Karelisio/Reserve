package com.karelisio.reserve.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code paths of startup and of the main screens, compiled ahead of time on
 * install (faster launch and smoother scrolling). Run by the "Baseline profile" workflow.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        // Permissions are granted up front so that no dialog hides the screens.
        listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.BLUETOOTH_CONNECT",
        ).forEach { device.executeShellCommand("pm grant $PACKAGE $it") }
        device.executeShellCommand("appops set $PACKAGE REQUEST_INSTALL_PACKAGES allow")

        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // Scroll the list, then visit the other tabs.
        device.wait(Until.hasObject(By.scrollable(true)), 10_000)
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            list.fling(Direction.UP)
        }
        listOf("Carte", "Trajet", "Favoris", "Carnet", "Autour").forEach { tab ->
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "com.karelisio.reserve"
    }
}
