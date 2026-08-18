package app.viora

import app.viora.database.SlotWithCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttendanceSlotMatchingTest {
    @Test fun `lab slot uses lab attendance when theory row appears first`() {
        val state = VioraUiState(
            attendance = listOf(
                attendance("theory", "Embedded Theory", 18, 20),
                attendance("lab", "Embedded Lab", 8, 10),
            ),
        )

        val match = state.attendanceFor(slot("LAB"))

        assertEquals("lab", match?.id)
        assertEquals(8, match?.attended)
        assertEquals(10, match?.held)
    }

    @Test fun `theory slot uses theory attendance when lab row appears first`() {
        val state = VioraUiState(
            attendance = listOf(
                attendance("lab", "Lab Only", 8, 10),
                attendance("theory", "Theory Only", 18, 20),
            ),
        )

        assertEquals("theory", state.attendanceFor(slot("THEORY"))?.id)
    }

    @Test fun `known lab slot never falls back to known theory attendance`() {
        val state = VioraUiState(attendance = listOf(attendance("theory", "Theory Only", 18, 20)))

        assertNull(state.attendanceFor(slot("LAB")))
    }

    @Test fun `untyped VTOP attendance remains a safe fallback`() {
        val state = VioraUiState(attendance = listOf(attendance("legacy", "", 18, 20)))

        assertEquals("legacy", state.attendanceFor(slot("LAB"))?.id)
    }

    private fun attendance(id: String, type: String, attended: Int, held: Int) = AttendanceUi(
        id = id,
        courseCode = "BEEE101",
        courseTitle = "Embedded Systems",
        courseType = type,
        faculty = "Faculty A",
        attended = attended,
        sourceHeld = held,
        held = held,
        percentage = attended * 100.0 / held,
        skippable = 0,
        recovery = 0,
        blockSize = if (type.contains("lab", true)) 2 else 1,
        skippableBlocks = 0,
        recoveryBlocks = 0,
    )

    private fun slot(type: String) = SlotWithCourse(
        slotId = "slot-$type",
        courseId = "course",
        code = "BEEE101",
        title = "Embedded Systems",
        faculty = "Faculty A",
        dayOfWeek = 1,
        startMinute = 8 * 60,
        endMinute = 8 * 60 + 50,
        venue = "Room",
        type = type,
    )
}
