package com.example.qrhelper

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressPersistenceTest {
    @Test
    fun persistsRouteProgressRegardlessOfLegacyAutoSavePreference() = runBlocking {
        var writes = 0
        persistRouteProgress { writes++ }
        assertEquals(1, writes)
    }
}
