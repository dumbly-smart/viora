package app.viora.storage

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VioraFileStoreTest {
    private val sandbox = Files.createTempDirectory("viora-file-store").toFile()

    @After
    fun cleanUp() {
        sandbox.deleteRecursively()
    }

    @Test
    fun `material is stored below the private Viora course folder`() {
        val store = VioraFileStore(sandbox)

        val saved = store.writeMaterial(
            courseName = "Data Structures",
            requestedName = "Lecture 1.pdf",
            bytes = "lesson".toByteArray(),
        )

        assertEquals(
            File(sandbox, "Viora/materials/Data Structures/Lecture 1.pdf").canonicalPath,
            saved.file.canonicalPath,
        )
        assertArrayEquals("lesson".toByteArray(), saved.file.readBytes())
    }

    @Test
    fun `colliding material names remain organized without overwriting`() {
        val store = VioraFileStore(sandbox)
        store.writeMaterial("Algorithms", "Notes.pdf", "first".toByteArray())

        val second = store.writeMaterial("Algorithms", "Notes.pdf", "second".toByteArray())

        assertEquals("Notes (2).pdf", second.file.name)
        assertArrayEquals("second".toByteArray(), second.file.readBytes())
    }

    @Test
    fun `legacy material is deleted only after a verified private copy`() {
        val legacy = File(sandbox, "legacy.pdf").apply { writeText("legacy") }
        val store = VioraFileStore(File(sandbox, "private"))

        val migrated = store.migrateLegacyMaterial(legacy, "Networks", "legacy.pdf").getOrThrow()

        assertFalse(legacy.exists())
        assertTrue(migrated.file.isFile)
        assertArrayEquals("legacy".toByteArray(), migrated.file.readBytes())
    }

    @Test
    fun `failed legacy migration leaves the source untouched`() {
        val legacyDirectory = File(sandbox, "legacy-directory").apply { mkdirs() }
        val store = VioraFileStore(File(sandbox, "private"))

        val result = store.migrateLegacyMaterial(legacyDirectory, "Networks", "legacy.pdf")

        assertTrue(result.isFailure)
        assertTrue(legacyDirectory.isDirectory)
    }

    @Test
    fun `content backed legacy material is removed after private bytes are verified`() {
        val store = VioraFileStore(sandbox)
        var sourceDeleted = false

        val migrated = store.migrateLegacyMaterial(
            bytes = "legacy content".toByteArray(),
            courseName = "Legacy materials",
            requestedName = "legacy.pdf",
            deleteSource = {
                val privateCopy = File(sandbox, "Viora/materials/Legacy materials/legacy.pdf")
                assertArrayEquals("legacy content".toByteArray(), privateCopy.readBytes())
                sourceDeleted = true
                true
            },
        ).getOrThrow()

        assertTrue(sourceDeleted)
        assertTrue(migrated.file.isFile)
    }

    @Test
    fun `generated share files stay below the private Viora folder`() {
        val store = VioraFileStore(sandbox)

        val shared = store.sharedFile("timetable.png")

        assertEquals(
            File(sandbox, "Viora/shared/timetable.png").canonicalPath,
            shared.canonicalPath,
        )
        assertTrue(shared.parentFile?.isDirectory == true)
    }

    @Test
    fun `calendar working files stay below the private calendar folder`() {
        val file = VioraFileStore(sandbox).calendarFile("../Fall:2026.ics")

        assertEquals(File(sandbox, "Viora/calendar/Fall_2026.ics").canonicalPath, file.canonicalPath)
    }

    @Test
    fun `clearing Viora files preserves Android managed siblings`() {
        val store = VioraFileStore(sandbox)
        store.writeMaterial("Algorithms", "Notes.pdf", "notes".toByteArray())
        val androidManagedSibling = File(sandbox, "unrelated.db").apply { writeText("keep") }

        assertTrue(store.deleteAll())

        assertFalse(File(sandbox, "Viora").exists())
        assertTrue(androidManagedSibling.isFile)
    }
}
