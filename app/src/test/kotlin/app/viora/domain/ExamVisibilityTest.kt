package app.viora.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamVisibilityTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun `active exam uses the end time supplied by vtop`() {
        val start = 10 * day
        val end = start + 75 * 60_000
        assertTrue(isExamActive(start, end, start + 30 * 60_000))
        assertFalse(isExamActive(start, end, end))
    }

    @Test fun `completed exam is removed from schedule`() {
        val start = 10 * day
        val end = start + 75 * 60_000
        assertFalse(shouldShowExamInSchedule(start, end, end))
    }

    @Test fun `every exam appears from seven days before`() {
        val start = 20 * day
        val end = start + 75 * 60_000
        assertFalse(shouldShowExamInSchedule(start, end, start - 8 * day))
        assertTrue(shouldShowExamInSchedule(start, end, start - 7 * day))
    }

    @Test fun `exam period spans first exam start through final exam end`() {
        val exams = listOf(
            ExamWindow(10 * day, 10 * day + 75 * 60_000, "CAT 1"),
            ExamWindow(13 * day, 13 * day + 75 * 60_000, "CAT-I"),
        )
        assertFalse(isExamPeriodActive(exams, 10 * day - 1))
        assertTrue(isExamPeriodActive(exams, 11 * day))
        assertFalse(isExamPeriodActive(exams, 13 * day + 75 * 60_000))
    }

    @Test fun `different exam series do not create one long exam period`() {
        val exams = listOf(
            ExamWindow(10 * day, 10 * day + 75 * 60_000, "CAT 1"),
            ExamWindow(40 * day, 40 * day + 75 * 60_000, "CAT 2"),
            ExamWindow(80 * day, 80 * day + 75 * 60_000, "FAT"),
        )
        assertFalse(isExamPeriodActive(exams, 20 * day))
    }
}
