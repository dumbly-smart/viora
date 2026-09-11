package app.viora.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExamScheduleTest {
    @Test fun `classes overlapping an exam window are hidden`() {
        assertTrue(overlapsExam(600, 650, 570, 660))
        assertTrue(overlapsExam(650, 700, 570, 660))
    }

    @Test fun `classes touching but outside exam window remain`() {
        assertFalse(overlapsExam(480, 540, 540, 660))
        assertFalse(overlapsExam(660, 710, 570, 660))
    }

    @Test fun `fallback resumes on explicit day order and is labelled estimated`() {
        val zone = ZoneId.of("Asia/Kolkata")
        fun at(date: LocalDate) = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val lastExam = LocalDate.of(2026, 8, 19)

        val window = examSuppressionWindows(
            exams = listOf(ExamWindow(at(LocalDate.of(2026, 8, 17)), at(lastExam) + 3_600_000, "CAT-I")),
            calendar = listOf(ExamCalendarDate(LocalDate.of(2026, 8, 20), "Monday order", "Instruction")),
        ).single()

        assertEquals(LocalDate.of(2026, 8, 17), window.startDate)
        assertEquals(LocalDate.of(2026, 8, 20), window.resumeDate)
        assertTrue(window.estimated)
    }
}
