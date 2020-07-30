package com.app.missednotificationsreminder.settings

import com.app.missednotificationsreminder.util.BatteryUtils

data class SettingsViewState(
        val accessInitialized: Boolean = false,
        val accessEnabled: Boolean = false,
        val batteryOptimizationDisabled: Boolean = false,
        val advancedSettingsVisible: Boolean = false,
        val vibrationSettingsAvailable: Boolean = false,
        val missingPermissions: String = "") {
    /**
     * Data binding method used to determine whether to display battery optimization settings
     */
    val isBatteryOptimizationSettingsVisible: Boolean by lazy {
        BatteryUtils.isBatteryOptimizationSettingsAvailable()
    }
}