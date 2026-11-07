package dev.jdtech.jellyfin.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until

const val PACKAGE_NAME = "nl.midasvo.findroid.ce"

private const val SCREEN_TIMEOUT_MS = 5_000L

/**
 * Starts the app and drives it as far as the device's state allows.
 *
 * The screens carry no test tags, so this finds things by structure (the scrollable list, the
 * clickable cards in it) rather than by text, which is translated and changes with the server.
 * - Logged in: Home rows, scroll, open the first card, then the first card on that screen (a
 *   season, when the first card was a show), and back to Home.
 * - Not logged in: the setup screens are on screen instead; scroll what scrolls.
 *
 * Returns true when the logged-in journey ran, false when only the setup screens were seen.
 */
fun MacrobenchmarkScope.appJourney(): Boolean {
    pressHome()
    startActivityAndWait()

    val home = device.wait(Until.findObject(By.scrollable(true)), SCREEN_TIMEOUT_MS)
    if (home == null) {
        // Welcome / server setup: short screens. Let them settle and nudge them.
        device.waitForIdle()
        val x = device.displayWidth / 2
        repeat(2) { device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, 20) }
        device.waitForIdle()
        return false
    }

    // Swipe the screen instead of flinging the found list: Home recomposes while its rows load
    // and a held UiObject2 goes stale.
    device.waitForIdle()
    repeat(2) { swipeVertical(up = true) }
    device.waitForIdle()
    repeat(2) { swipeVertical(up = false) }
    device.waitForIdle()

    // First card (movie, show or episode) -> its detail screen, then the first card on that.
    if (!openFirstCard()) return true
    openFirstCard()
    device.pressBack()
    device.pressBack()
    device.waitForIdle()
    return true
}

private fun MacrobenchmarkScope.swipeVertical(up: Boolean) {
    val x = device.displayWidth / 2
    val high = device.displayHeight / 4
    val low = device.displayHeight * 3 / 4
    if (up) device.swipe(x, low, x, high, 25) else device.swipe(x, high, x, low, 25)
    device.waitForIdle()
}

private fun MacrobenchmarkScope.openFirstCard(): Boolean {
    repeat(3) {
        try {
            val list =
                device.wait(Until.findObject(By.scrollable(true)), SCREEN_TIMEOUT_MS) ?: return false
            val card =
                list.findObjects(By.clickable(true)).firstOrNull { it.visibleBounds.height() > 100 }
                    ?: return false
            card.click()
            device.waitForIdle()
            return true
        } catch (_: StaleObjectException) {
            device.waitForIdle()
        }
    }
    return false
}
