package app.viora.security

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class StoredCredentials(val username: String, val password: CharArray) {
    fun destroy() = password.fill('\u0000')
}

interface CredentialStore {
    fun save(username: String, password: CharArray)
    fun load(): StoredCredentials?
    fun clear()
}

class CredentialVault(private val blobs: EncryptedPreferencesBlobStore) : CredentialStore {
    override fun save(username: String, password: CharArray) {
        require(username.isNotBlank())
        val usernameBytes = username.toByteArray(StandardCharsets.UTF_8)
        val passwordBytes = String(password).toByteArray(StandardCharsets.UTF_8)
        val payload = ByteBuffer.allocate(8 + usernameBytes.size + passwordBytes.size)
            .putInt(usernameBytes.size).put(usernameBytes)
            .putInt(passwordBytes.size).put(passwordBytes)
            .array()
        try {
            blobs.put(KEY, payload)
        } finally {
            payload.fill(0)
            passwordBytes.fill(0)
        }
    }

    override fun load(): StoredCredentials? {
        val payload = blobs.get(KEY) ?: return null
        return try {
            val buffer = ByteBuffer.wrap(payload)
            val username = buffer.readUtf8()
            val password = buffer.readUtf8().toCharArray()
            StoredCredentials(username, password)
        } catch (_: RuntimeException) {
            clear()
            null
        } finally {
            payload.fill(0)
        }
    }

    override fun clear() = blobs.remove(KEY)

    private fun ByteBuffer.readUtf8(): String {
        val size = int
        require(size in 0..remaining())
        return ByteArray(size).also { get(it) }.toString(StandardCharsets.UTF_8)
    }

    private companion object { const val KEY = "credentials" }
}
