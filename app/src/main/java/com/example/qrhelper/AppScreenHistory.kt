package com.example.qrhelper

/**
 * Keeps the actual screen path so Back returns to the screen the user came from,
 * instead of guessing from a hard-coded screen-to-screen table.
 */
internal object AppScreenHistory {
    fun push(history: List<AppScreen>, current: AppScreen, next: AppScreen): List<AppScreen> =
        if (current == next) history else history + current

    fun pop(history: List<AppScreen>, current: AppScreen): Pair<List<AppScreen>, AppScreen> =
        if (history.isEmpty()) history to current
        else history.dropLast(1) to history.last()
}

/** Restore stable work screens only; transient screens need their in-memory arguments. */
internal fun screenToRestore(savedName: String?, hasActiveRoute: Boolean): AppScreen {
    if (!hasActiveRoute) return AppScreen.HUB
    val saved = savedName?.let { runCatching { AppScreen.valueOf(it) }.getOrNull() }
    return when (saved) {
        AppScreen.HUB -> AppScreen.HUB
        AppScreen.ROUTE_LIST -> AppScreen.ROUTE_LIST
        else -> AppScreen.ROUTE_LIST
    }
}
