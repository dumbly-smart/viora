package app.viora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpaPlannerTest {
    @Test fun `manual sgpa uses grade points weighted by credits`() {
        val result = manualSgpa(listOf(
            ManualSubjectInput("Math", "A", "4"),
            ManualSubjectInput("Physics", "S", "3"),
            ManualSubjectInput("Programming", "B", "3"),
        ))

        assertEquals(9.0, result!!, 0.0001)
    }

    @Test fun `manual cgpa weights semester gpa by credits`() {
        val result = manualCgpa(listOf(
            ManualSemesterInput("8.6", "24"),
            ManualSemesterInput("8.9", "25"),
            ManualSemesterInput("9.0", "24"),
        ))

        assertEquals(8.83, result!!, 0.01)
    }

    @Test fun `spreading target across more semesters lowers required average`() {
        val required = (1..4).map { requiredFutureSgpa(8.5, 80.0, 20.0, 9.0, it)!! }

        assertTrue(required.zipWithNext().all { (earlier, later) -> later < earlier })
    }
}
