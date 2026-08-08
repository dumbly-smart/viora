package app.viora.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemesterRolloverTest {
    @Test fun `new remote semester becomes active and archives previous selection`() {
        val result = SemesterRollover.select(listOf("new", "old"), setOf("old"), "old")
        assertEquals("new", result.selectedId)
        assertTrue(result.rolloverDetected)
    }
    @Test fun `saved semester remains selected when no rollover occurred`() {
        val result = SemesterRollover.select(listOf("current", "old"), setOf("current", "old"), "old")
        assertEquals("old", result.selectedId)
        assertFalse(result.rolloverDetected)
    }
}
