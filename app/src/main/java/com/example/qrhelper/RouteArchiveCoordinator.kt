package com.example.qrhelper

/**
 * Clear the compatibility snapshot before deactivating the Room route.
 * If the process stops between these operations, startup can still recover
 * an active route from Room without resurrecting an already archived route.
 */
internal suspend fun archiveRouteSafely(
    clearLegacySnapshot: suspend () -> Boolean,
    archiveInDatabase: suspend () -> Boolean
): Boolean {
    check(clearLegacySnapshot()) { "Не удалось очистить снимок активного рейса" }
    return archiveInDatabase()
}
