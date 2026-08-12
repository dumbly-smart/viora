package app.viora.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamScheduleTest {
    @Test fun `classes overlapping an exam window are hidden`() {
        assertTrue(overlapsExam(600, 650, 570, 660))
        assertTrue(overlapsExam(650, 700, 570, 660))
    }

    @Test fun `classes touching but outside exam window remain`() {
        assertFalse(overlapsExam(480, 540, 540, 660))
        assertFalse(overlapsExam(660, 710, 570, 660))
    }
}
