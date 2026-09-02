package app.viora.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentStatusTest {
    @Test fun `submitted server status wins over a stale negative upload field`() {
        assertTrue(isAssignmentSubmitted("Submitted", "File Not Uploaded"))
    }

    @Test fun `real upload timestamp is submitted when status is pending`() {
        assertTrue(isAssignmentSubmitted("Pending", "18-Aug-2026 10:15 PM"))
    }

    @Test fun `explicitly unsubmitted assignment remains pending`() {
        assertFalse(isAssignmentSubmitted("Pending", "File Not Uploaded"))
    }
}
