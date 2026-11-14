package app.viora

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineFocusTest {
    @Test
    fun focusedTimelineCourseIsClosestToViewportCenter() {
        val key = focusedTimelineCourseKey(
            viewportStart = 100,
            viewportEnd = 500,
            courses = listOf(
                TimelineViewportCourse("early", top = 80, height = 120),
                TimelineViewportCourse("center", top = 260, height = 120),
                TimelineViewportCourse("late", top = 430, height = 120),
            ),
        )

        assertEquals("center", key)
    }
}
