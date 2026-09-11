package app.viora.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AttendanceMilestoneTest {
    @Test
    fun `caps skips at classes available before milestone`() {
        assertEquals(2, maximumSkippableOccurrences(18, 20, 75, listOf(1, 1)))
    }

    @Test
    fun `lab blocks are skipped as a complete occurrence`() {
        assertEquals(2, maximumSkippableOccurrences(18, 20, 75, listOf(2, 2)))
    }

    @Test
    fun `future attended classes can recover a currently below target course`() {
        assertEquals(1, maximumSkippableOccurrences(14, 20, 75, List(10) { 1 }))
    }

    @Test
    fun `lab skip capacity preserves whole two unit occurrences`() {
        assertEquals(1, maximumSkippableOccurrences(14, 20, 75, List(6) { 2 }))
    }
}
