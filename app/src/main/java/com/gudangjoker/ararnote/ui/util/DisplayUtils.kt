package com.gudangjoker.ararnote.ui.util

import android.app.Activity
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

object DisplayUtils {

    /**
     * Unlocks and requests maximum supported display refresh rate (60Hz / 90Hz / 120Hz / 144Hz)
     * on Android 11+ (API 30+) devices for fluid scrolling and animations.
     */
    fun optimizeWindowForHighRefreshRate(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val display = activity.display ?: return
                val supportedModes = display.supportedModes ?: return

                // Find mode with highest refresh rate (e.g. 90Hz, 120Hz, 144Hz) matching current resolution
                val currentMode = display.mode
                val bestHighFpsMode = supportedModes
                    .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                    .maxByOrNull { it.refreshRate }

                if (bestHighFpsMode != null && bestHighFpsMode.refreshRate >= 60f) {
                    val layoutParams = activity.window.attributes
                    layoutParams.preferredDisplayModeId = bestHighFpsMode.modeId
                    activity.window.attributes = layoutParams
                }
            } catch (_: Exception) {
                // Graceful fallback if device vendor blocks programmatic display mode changes
            }
        }
    }

    /**
     * Subtle haptic feedback for user interactions (swipes, drag, selections).
     */
    fun triggerHapticFeedback(view: View) {
        try {
            view.performHapticFeedback(
                HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (_: Exception) {}
    }
}
