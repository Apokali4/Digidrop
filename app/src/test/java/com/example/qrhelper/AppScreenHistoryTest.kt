package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class AppScreenHistoryTest {
    @Test
    fun backReturnsToTheExactPreviousScreenAcrossDifferentFlows() {
        var history = emptyList<AppScreen>()
        history = AppScreenHistory.push(history, AppScreen.HUB, AppScreen.INVOICE_IMPORT)
        history = AppScreenHistory.push(history, AppScreen.INVOICE_IMPORT, AppScreen.PHOTO_ROUTE)
        history = AppScreenHistory.push(history, AppScreen.PHOTO_ROUTE, AppScreen.ROUTE_LIST)

        val (afterFirstBack, firstBack) = AppScreenHistory.pop(history, AppScreen.ROUTE_LIST)
        assertEquals(AppScreen.PHOTO_ROUTE, firstBack)

        val (afterSecondBack, secondBack) = AppScreenHistory.pop(afterFirstBack, firstBack)
        assertEquals(AppScreen.INVOICE_IMPORT, secondBack)

        val (_, thirdBack) = AppScreenHistory.pop(afterSecondBack, secondBack)
        assertEquals(AppScreen.HUB, thirdBack)
    }

    @Test
    fun navigatingToCurrentScreenDoesNotCreateADeadBackEntry() {
        val history = AppScreenHistory.push(
            listOf(AppScreen.HUB),
            AppScreen.PHOTO_ROUTE,
            AppScreen.PHOTO_ROUTE
        )
        assertEquals(listOf(AppScreen.HUB), history)
    }

    @Test
    fun emptyHistoryKeepsCurrentScreen() {
        assertEquals(
            Pair(emptyList<AppScreen>(), AppScreen.ROUTE_LIST),
            AppScreenHistory.pop(emptyList(), AppScreen.ROUTE_LIST)
        )
    }
}
