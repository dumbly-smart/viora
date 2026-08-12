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

    @Test fun `cat two and fat appear from seven days before`() {
        val start = 20 * day
        assertFalse(shouldShowExamInSchedule(start, "CAT-II", start - 8 * day))
        assertTrue(shouldShowExamInSchedule(start, "CAT 2", start - 7 * day))
        assertFalse(shouldShowExamInSchedule(start, "FAT", start - 8 * day))
        assertTrue(shouldShowExamInSchedule(start, "FAT", start - 7 * day))
    }
}
