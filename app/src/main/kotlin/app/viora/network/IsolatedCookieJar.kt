package app.viora.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A cookie jar owned only by Viora. It never reads Chrome/WebView/official-app cookies,
 * ensuring Viora cannot mutate another client's local session.
 *
 * Persistence is deliberately abstracted so the production store can encrypt values
 * with Android Keystore without coupling OkHttp to Android APIs.
 */
class IsolatedCookieJar(
    private val store: SessionCookieStore,
) : CookieJar {
    private val lock = ReentrantLock()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = lock.withLock {
        val now = System.currentTimeMillis()
        val merged = store.load()
            .filter { it.expiresAt > now }
            .associateBy { it.identity() }
            .toMutableMap()

        cookies.forEach { cookie ->
            if (cookie.expiresAt <= now) merged.remove(cookie.identity())
            else merged[cookie.identity()] = cookie
        }
        store.save(merged.values.toList())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = lock.withLock {
        val now = System.currentTimeMillis()
        val stored = store.load()
        val valid = stored.filter { it.expiresAt > now }
        if (valid.size != stored.size) store.save(valid)
        valid.filter { it.matches(url) }
    }

    fun clear() = lock.withLock { store.clear() }

    fun replace(cookies: List<Cookie>) = lock.withLock { store.save(cookies) }

    private fun Cookie.identity() = "$name|$domain|$path"
}

interface SessionCookieStore {
    fun load(): List<Cookie>
    fun save(cookies: List<Cookie>)
    fun clear()
}
