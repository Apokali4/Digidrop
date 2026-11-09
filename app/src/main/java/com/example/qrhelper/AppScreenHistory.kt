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
