package app.viora.calendar

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitedInputReaderTest {
    @Test fun `reader accepts content at the limit`() {
        assertArrayEquals(byteArrayOf(1, 2, 3), readAtMost(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3))
    }

    @Test fun `reader rejects content beyond the limit`() {
        val result = runCatching { readAtMost(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3) }
        assertTrue(result.isFailure)
    }
}
