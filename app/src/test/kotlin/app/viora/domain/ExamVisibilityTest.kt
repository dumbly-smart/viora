package app.viora.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamVisibilityTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun `active exam lasts for its configured duration`() {
        val start = 10 * day
        assertTrue(isExamActive(start, "CAT 2", start + 30 * 60_000))
        assertFalse(isExamActive(start, "CAT 2", start + 90 * 60_000))
    }

    @Test fun `completed exam is removed from schedule`() {
        val start = 10 * day
        assertFalse(shouldShowExamInSchedule(start, "CAT 1", start + 90 * 60_000))
    }

    @Test fun `every exam appears from seven days before`() {
        val start = 20 * day
        assertFalse(shouldShowExamInSchedule(start, "CAT 1", start - 8 * day))
        assertTrue(shouldShowExamInSchedule(start, "CAT 1", start - 7 * day))
        assertFalse(shouldShowExamInSchedule(start, "CAT-II", start - 8 * day))
        assertTrue(shouldShowExamInSchedule(start, "CAT 2", start - 7 * day))
        assertFalse(shouldShowExamInSchedule(start, "FAT", start - 8 * day))
        assertTrue(shouldShowExamInSchedule(start, "FAT", start - 7 * day))
    }

    @Test fun `exam period spans first exam start through final exam end`() {
        val exams = listOf(10 * day to "CAT 1", 13 * day to "CAT-I")
        assertFalse(isExamPeriodActive(exams, 10 * day - 1))
        assertTrue(isExamPeriodActive(exams, 11 * day))
        assertFalse(isExamPeriodActive(exams, 13 * day + 90 * 60_000))
    }

    @Test fun `different exam series do not create one long exam period`() {
        val exams = listOf(10 * day to "CAT 1", 40 * day to "CAT 2", 80 * day to "FAT")
        assertFalse(isExamPeriodActive(exams, 20 * day))
    }
}
