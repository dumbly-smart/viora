package app.viora.network

import okhttp3.Cookie

class InMemoryCookieStore : SessionCookieStore {
    private var cookies = emptyList<Cookie>()
    override fun load(): List<Cookie> = cookies.toList()
    override fun save(cookies: List<Cookie>) { this.cookies = cookies.toList() }
    override fun clear() { cookies = emptyList() }
}
