package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceModeTest {
    @Test fun systemModeFollowsAndroidNightSetting() {
        assertEquals(true, AppearanceMode.SYSTEM.resolve(systemDark = true))
        assertEquals(false, AppearanceMode.SYSTEM.resolve(systemDark = false))
    }

    @Test fun explicitModesOverrideAndroidSetting() {
        assertEquals(true, AppearanceMode.DARK.resolve(systemDark = false))
        assertEquals(false, AppearanceMode.LIGHT.resolve(systemDark = true))
    }

    @Test fun unknownSavedValueFallsBackToSystem() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromPreference("broken"))
    }
}
