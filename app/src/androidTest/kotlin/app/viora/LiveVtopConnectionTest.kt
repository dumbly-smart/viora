package app.viora

import androidx.test.platform.app.InstrumentationRegistry
import app.viora.network.*
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in probe: no credentials, response bodies, images or cookies are logged. */
class LiveVtopConnectionTest {
    @Test fun authenticatedSessionAndCacheSurviveProcessRestart() = kotlinx.coroutines.runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveVtopAuthenticated") == "true")
        val graph = VioraGraph(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue("Sign-in completed", graph.settings.getBoolean(VioraGraph.KEY_CONFIGURED, false))
        val database = graph.database.openHelper.readableDatabase
        for (table in listOf("semesters", "courses", "class_slots")) {
            database.query("SELECT EXISTS(SELECT 1 FROM $table)").use {
                assertTrue("Cached $table query", it.moveToFirst())
                assertTrue("Initial sync cached $table", it.getInt(0) == 1)
            }
        }
        var session: SessionState = SessionState.Missing
        for (attempt in 0..2) {
            try {
                session = graph.gateway.sessionState()
                break
            } catch (failure: java.io.IOException) {
                if (attempt == 2) throw failure
                kotlinx.coroutines.delay(1000)
            }
        }
        assertEquals("Persisted cookies authenticate after process restart", SessionState.Active, session)
    }

    @Test fun rootSessionCaptchaAndLocalSolver() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveVtopProbe") == "true")
        val store = object : SessionCookieStore {
            var values = emptyList<Cookie>()
            override fun load() = values
            override fun save(cookies: List<Cookie>) { values = cookies }
            override fun clear() { values = emptyList() }
        }
        val client = VioraHttpClient.create(store)
        repeat(3) {
            store.clear()
            val root = client.newCall(Request.Builder().url("https://vtop.vit.ac.in/").build()).execute().use {
                assertEquals("Root session HTTP status", 200, it.code)
                Jsoup.parse(it.body.string())
            }
            val token = Regex("""var csrfValue = /\*(.*?)\*/'.*';""").find(root.html())?.groupValues?.get(1)
                ?: root.selectFirst("input[name=_csrf]")?.attr("value")
                ?: root.selectFirst("meta[name=_csrf]")?.attr("content")
            assertFalse("Root session provides CSRF", token.isNullOrBlank())
            val setup = client.newCall(Request.Builder().url("https://vtop.vit.ac.in/vtop/prelogin/setup")
                .post(FormBody.Builder().add("_csrf", requireNotNull(token)).add("flag", "VTOP").build()).build()).execute().use {
                assertEquals("Prelogin setup HTTP status", 200, it.code)
                Jsoup.parse(it.body.string())
            }
            val image = setup.select("#captchaBlock img[src], img[src]").map { it.attr("src") }
                .firstOrNull { it.startsWith("data:image/") }
            if (image == null) {
                val recaptcha = setup.selectFirst(".g-recaptcha, #recaptcha") != null
                val password = setup.selectFirst("input[type=password]") != null
                if (it < 2) return@repeat
                fail("No text CAPTCHA after 3 fresh sessions: recaptcha=$recaptcha passwordForm=$password images=${setup.select("img").size}")
            }
            val answer = VtopCaptchaSolver.fromAssets(InstrumentationRegistry.getInstrumentation().targetContext.assets)
                .solve(requireNotNull(image))
            assertEquals("Local solver produces six characters", 6, answer.length)
            store.clear()
            return
        }
    }
}
