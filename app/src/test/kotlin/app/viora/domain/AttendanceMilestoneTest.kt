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
    fun `does not allow skips when current attendance is below target`() {
        assertEquals(0, maximumSkippableOccurrences(14, 20, 75, listOf(1, 1)))
    }
}
