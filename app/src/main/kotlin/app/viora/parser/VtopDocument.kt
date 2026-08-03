package app.viora.parser

import org.jsoup.nodes.Document

internal object VtopDocument {
    fun requiresVerification(document: Document): Boolean =
        document.selectFirst("#captchaBlock, img[src*=captcha], input[name=captchaStr]") != null

    fun isAuthenticationPage(document: Document): Boolean {
        val title = document.title().lowercase()
        val body = document.body()?.text()?.lowercase().orEmpty()
        return requiresVerification(document) || document.selectFirst("input[type=password]") != null ||
            "session timed out" in body ||
            ("login" in title && "student" in body)
    }
}
