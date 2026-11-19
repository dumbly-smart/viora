package app.viora.update

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun `only newer compatible signed packages can replace the app`() {
        assertTrue(UpdatePolicy.accepts("app.viora", 12, setOf("key"), "app.viora", 13, setOf("key")))
        assertFalse(UpdatePolicy.accepts("app.viora.debug", 12, setOf("key"), "app.viora", 13, setOf("key")))
        assertFalse(UpdatePolicy.accepts("app.viora", 12, setOf("key"), "app.viora", 13, setOf("other")))
        assertFalse(UpdatePolicy.accepts("app.viora", 12, setOf("key"), "app.viora", 12, setOf("key")))
        assertFalse(UpdatePolicy.accepts("app.viora", 12, emptySet(), "app.viora", 13, emptySet()))
    }

    @Test fun `only forward single signer rotation with current key in history is allowed`() {
        assertTrue(UpdatePolicy.accepts("app.viora", 16, setOf("old"), "app.viora", 17, setOf("new"), setOf("old", "new")))
        assertFalse(UpdatePolicy.accepts("app.viora", 16, setOf("old"), "app.viora", 17, setOf("new"), setOf("new")))
        assertFalse(UpdatePolicy.accepts("app.viora", 16, setOf("old"), "app.viora", 17, setOf("new"), setOf("old")))
        assertFalse(UpdatePolicy.accepts("app.viora", 16, setOf("old", "other"), "app.viora", 17, setOf("new"), setOf("old", "other", "new")))
        assertFalse(UpdatePolicy.accepts("app.viora", 17, setOf("new"), "app.viora", 18, setOf("old"), setOf("old")))
    }

    @Test fun `release downloads must belong to this repository`() {
        assertTrue(UpdatePolicy.releaseUrl("https://github.com/dumbly-smart/viora/releases/download/v0.2.0/Viora-0.2.0.apk"))
        listOf("http://github.com/dumbly-smart/viora/releases/download/v1/a.apk", "https://github.com/evil/viora/releases/download/v1/a.apk", "https://github.com.evil.com/dumbly-smart/viora/releases/download/v1/a.apk", "https://github.com/dumbly-smart/viora/releases/download/../a.apk").forEach {
            assertFalse(it, UpdatePolicy.releaseUrl(it))
        }
    }

    @Test fun `checksum and size must match complete download`() {
        val bytes = "abc".toByteArray()
        assertTrue(UpdatePolicy.matches(bytes.inputStream(), 3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
        assertFalse(UpdatePolicy.matches(bytes.inputStream(), 4, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
        assertFalse(UpdatePolicy.matches(bytes.inputStream(), 3, "0".repeat(64)))
    }
}
