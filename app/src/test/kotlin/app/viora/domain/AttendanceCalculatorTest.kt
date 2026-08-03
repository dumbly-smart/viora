package app.viora.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AttendanceCalculatorTest {
    @Test fun `calculates exact seventy five percent boundary`() {
        val result = AttendanceCalculator.calculate(attended = 9, held = 12)
        assertEquals(0, result.skippableClasses)
        assertEquals(0, result.classesToRecover)
    }

    @Test fun `calculates safe projection without floating point rounding`() {
        val result = AttendanceCalculator.calculate(attended = 18, held = 20)
        assertEquals(4, result.skippableClasses)
        assertEquals(0, result.classesToRecover)
    }

    @Test fun `calculates classes required to recover`() {
        val result = AttendanceCalculator.calculate(attended = 6, held = 10)
        assertEquals(0, result.skippableClasses)
        assertEquals(6, result.classesToRecover)
    }

    @Test fun `rejects impossible attendance`() {
        assertThrows(IllegalArgumentException::class.java) {
            AttendanceCalculator.calculate(attended = 11, held = 10)
        }
    }
}
