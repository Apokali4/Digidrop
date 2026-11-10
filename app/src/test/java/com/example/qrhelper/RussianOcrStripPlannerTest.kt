package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianOcrStripPlannerTest {
    @Test fun stripsCoverWholePageAndOverlapWithoutGaps() {
        val strips = RussianOcrStripPlanner.ranges(height = 2400)

        assertEquals(3, strips.size)
        assertEquals(0, strips.first().first)
        assertEquals(2399, strips.last().last)
        strips.zipWithNext().forEach { (upper, lower) ->
            assertTrue("Adjacent OCR strips must overlap", upper.last >= lower.first)
        }
    }

    @Test fun shortPageUsesOneStrip() {
        assertEquals(listOf(0..499), RussianOcrStripPlanner.ranges(height = 500))
    }

    @Test fun invalidHeightIsRejected() {
        runCatching { RussianOcrStripPlanner.ranges(height = 0) }
            .onSuccess { throw AssertionError("Expected invalid image height to be rejected") }
    }
}
