package com.example.qrhelper

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class RouteArchiveCoordinatorTest {
    @Test fun clearsLegacySnapshotBeforeArchivingRoomRoute() = runBlocking {
        val events = mutableListOf<String>()
        val archived = archiveRouteSafely(
            clearLegacySnapshot = { events += "clear"; true },
            archiveInDatabase = { events += "archive"; true }
        )
        assertEquals(listOf("clear", "archive"), events)
        assertEquals(true, archived)
    }

    @Test fun doesNotArchiveRoomRouteWhenLegacySnapshotCouldNotBeCleared() = runBlocking {
        var archiveCalled = false
        try {
            archiveRouteSafely(
                clearLegacySnapshot = { false },
                archiveInDatabase = { archiveCalled = true; true }
            )
            fail("Ожидался отказ при неудачной очистке снимка")
        } catch (expected: IllegalStateException) {
            assertFalse(archiveCalled)
        }
    }

    @Test fun reportsWhenRoomHasNoActiveRouteToArchive() = runBlocking {
        val archived = archiveRouteSafely(
            clearLegacySnapshot = { true },
            archiveInDatabase = { false }
        )
        assertEquals(false, archived)
    }
}
