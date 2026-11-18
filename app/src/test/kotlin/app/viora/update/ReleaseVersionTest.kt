package app.viora.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun `stable numeric versions compare without downloading old releases`() {
        assertTrue(UpdatePolicy.newerRelease("0.1.10", "0.1.9"))
        assertTrue(UpdatePolicy.newerRelease("1.0.0", "0.99.99"))
        assertFalse(UpdatePolicy.newerRelease("0.1.4", "0.1.10"))
        assertFalse(UpdatePolicy.newerRelease("0.1.10", "0.1.10"))
        assertFalse(UpdatePolicy.newerRelease("0.2.0-beta", "0.1.10"))
        assertFalse(UpdatePolicy.newerRelease("999999999999999.0.0", "0.1.10"))
    }
}
