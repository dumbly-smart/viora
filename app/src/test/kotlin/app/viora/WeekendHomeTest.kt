package app.viora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekendHomeTest {
    @Test fun `friday night points to tarama`() {
        val friday = LocalDate.of(2026, 8, 14)
        assertNull(weekendHome(friday, 17))
        assertEquals("Go to Tarama", weekendHome(friday, 18)?.cardTitle)
    }

    @Test fun `weekend gets stable unserious copy`() {
        val saturday = LocalDate.of(2026, 8, 15)
        val first = weekendHome(saturday, 10)
        assertEquals(first, weekendHome(saturday, 23))
        assertTrue(first?.cardBody?.isNotBlank() == true)
    }

    @Test fun `weekday stays on normal home`() {
        assertNull(weekendHome(LocalDate.of(2026, 8, 12), 22))
    }
}
