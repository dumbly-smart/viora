package app.viora.parser

import org.jsoup.nodes.Document

internal object VtopDocument {
    fun isAuthenticationPage(document: Document): Boolean {
        val title = document.title().lowercase()
        val body = document.body()?.text()?.lowercase().orEmpty()
        return document.selectFirst("#captchaBlock, input[type=password]") != null ||
            "session timed out" in body ||
            ("login" in title && "student" in body)
    }
}
