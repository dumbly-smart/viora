package app.viora.assignment

import app.viora.network.AssignmentUploadLocator
import app.viora.network.DigitalAssignmentRecord
import app.viora.network.accepts
import app.viora.network.requireAssignmentUploadLocator
import app.viora.network.resolveAssignmentUploadAction
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentUploadFileTest {
    @Test fun `reader stops immediately after the ten MiB cap`() {
        val input = CountingInputStream(AssignmentUploadFile.MAX_BYTES + 8_192)

        val result = runCatching {
            AssignmentUploadFile.read(input, "answer.pdf", "application/pdf", advertisedMaxBytes = null)
        }

        assertTrue(result.isFailure)
        assertTrue(input.bytesRead <= AssignmentUploadFile.MAX_BYTES + 1)
    }

    @Test fun `advertised limit below app cap is enforced`() {
        val result = runCatching {
            AssignmentUploadFile.read(CountingInputStream(5), "answer.pdf", "application/pdf", advertisedMaxBytes = 4)
        }

        assertTrue(result.isFailure)
    }

    @Test fun `unsafe display name is reduced to a safe single filename`() {
        val upload = AssignmentUploadFile.read(
            byteArrayOf(1).inputStream(),
            "../../DA final (1).pdf",
            "application/pdf",
            advertisedMaxBytes = null,
        )

        assertEquals("DA_final_1_.pdf", upload.fileName)
    }

    @Test fun `missing fresh upload metadata fails safely`() {
        val assignment = DigitalAssignmentRecord("a1", "CSE1001", "Synthetic Course", "DA", null, "N/A", "Pending")

        val result = runCatching { requireAssignmentUploadLocator(listOf(assignment), "a1") }

        assertTrue(result.isFailure)
        assertEquals("VTOP did not provide a current upload form for this assessment", result.exceptionOrNull()?.message)
    }

    @Test fun `upload action must resolve to HTTPS VTOP`() {
        assertEquals(
            "https://vtop.vit.ac.in/vtop/examinations/upload",
            resolveAssignmentUploadAction("/vtop/examinations/upload").toString(),
        )
        assertTrue(runCatching { resolveAssignmentUploadAction("http://vtop.vit.ac.in/vtop/upload") }.isFailure)
        assertTrue(runCatching { resolveAssignmentUploadAction("https://files.example/vtop/upload") }.isFailure)
        assertTrue(runCatching { resolveAssignmentUploadAction("//files.example/vtop/upload") }.isFailure)
    }

    @Test fun `accepted MIME contract permits declared MIME or extension only`() {
        val locator = AssignmentUploadLocator("/vtop/upload", emptyMap(), "file", setOf("application/pdf", ".docx"), null)

        assertTrue(locator.accepts("application/pdf", "answer.bin"))
        assertTrue(locator.accepts("application/octet-stream", "answer.docx"))
        assertTrue(!locator.accepts("image/png", "answer.png"))
    }

    private class CountingInputStream(private val totalBytes: Int) : InputStream() {
        var bytesRead: Int = 0
            private set

        override fun read(): Int = if (bytesRead >= totalBytes) -1 else 0.also { bytesRead++ }

        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            if (bytesRead >= totalBytes) return -1
            val count = minOf(length, totalBytes - bytesRead)
            target.fill(0, offset, offset + count)
            bytesRead += count
            return count
        }
    }
}
