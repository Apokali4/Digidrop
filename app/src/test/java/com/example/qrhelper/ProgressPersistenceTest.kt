package com.example.qrhelper

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressPersistenceTest {
    @Test
    fun doesNotPersistProgressWhenAutoSaveIsDisabled() = runBlocking {
        var writes = 0
        persistProgressIfEnabled(autoSaveEnabled = false) { writes++ }
        assertEquals(0, writes)
    }

    @Test
    fun persistsProgressWhenAutoSaveIsEnabled() = runBlocking {
        var writes = 0
        persistProgressIfEnabled(autoSaveEnabled = true) { writes++ }
        assertEquals(1, writes)
    }
}
