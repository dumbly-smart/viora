package app.viora.security

import app.viora.network.SessionCookieStore
import okhttp3.Cookie
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class EncryptedCookieStore(
    private val blobs: EncryptedPreferencesBlobStore,
) : SessionCookieStore {
    override fun load(): List<Cookie> {
        val bytes = blobs.get(KEY) ?: return emptyList()
        return runCatching {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val count = input.readInt()
                require(count in 0..MAX_COOKIES)
                List(count) { input.readCookie() }
            }
        }.getOrElse {
            clear()
            emptyList()
        }.also { bytes.fill(0) }
    }

    override fun save(cookies: List<Cookie>) {
        require(cookies.size <= MAX_COOKIES)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(cookies.size)
            cookies.forEach { cookie -> data.writeCookie(cookie) }
        }
        output.toByteArray().also {
            try { blobs.put(KEY, it) } finally { it.fill(0) }
        }
    }

    override fun clear() = blobs.remove(KEY)

    private fun DataOutputStream.writeCookie(cookie: Cookie) {
        writeUTF(cookie.name)
        writeUTF(cookie.value)
        writeLong(cookie.expiresAt)
        writeUTF(cookie.domain)
        writeUTF(cookie.path)
        writeBoolean(cookie.secure)
        writeBoolean(cookie.httpOnly)
        writeBoolean(cookie.persistent)
        writeBoolean(cookie.hostOnly)
    }

    private fun DataInputStream.readCookie(): Cookie {
        val name = readUTF()
        val value = readUTF()
        val expiresAt = readLong()
        val domain = readUTF()
        val path = readUTF()
        val secure = readBoolean()
        val httpOnly = readBoolean()
        val persistent = readBoolean()
        val hostOnly = readBoolean()
        return Cookie.Builder()
            .name(name)
            .value(value)
            .apply {
                if (persistent) expiresAt(expiresAt)
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                path(path)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .build()
    }

    private companion object {
        const val KEY = "cookies"
        const val MAX_COOKIES = 128
    }
}
