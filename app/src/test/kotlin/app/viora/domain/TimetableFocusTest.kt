package app.viora.domain

import app.viora.database.SlotWithCourse
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableFocusTest {
    @Test fun `focus shows only live class`() {
        val slots = listOf(slot("past", 480, 530), slot("live", 600, 650), slot("next", 660, 710))
        assertEquals(listOf("live"), focusedSlots(slots, 620).map(SlotWithCourse::slotId))
    }

    @Test fun `focus advances to the next class between periods`() {
        val slots = listOf(slot("past", 480, 530), slot("next", 600, 650))
        assertEquals(listOf("next"), focusedSlots(slots, 570).map(SlotWithCourse::slotId))
    }

    @Test fun `focus is empty after the final class`() {
        assertEquals(emptyList<SlotWithCourse>(), focusedSlots(listOf(slot("past", 480, 530)), 700))
    }

    private fun slot(id: String, start: Int, end: Int) = SlotWithCourse(
        slotId = id,
        courseId = "course-$id",
        dayOfWeek = 1,
        startMinute = start,
        endMinute = end,
        venue = "AB1",
        type = "THEORY",
        code = "CSE1001",
        title = "Example",
        faculty = "Faculty",
    )
}
