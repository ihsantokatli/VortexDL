package com.vortexdl.app.presentation.browser

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Converts whatever the user typed in the address bar (a URL, a bare domain or a
 * search query) into a URL that the WebView can load.
 */
object BrowserUrl {

    const val HOME_URL = "https://www.google.com/"
    const val BLANK_PAGE = "about:blank"

    private const val SEARCH_URL = "https://www.google.com/search?q="
    private val SCHEME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    fun toNavigableUrl(rawInput: String): String {
        val input = rawInput.trim()
        if (input.isEmpty()) return HOME_URL
        if (input.equals(BLANK_PAGE, ignoreCase = true)) return BLANK_PAGE

        if (input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true)
        ) {
            return if (isValidWebUrl(input)) input else searchUrl(input)
        }

        // Never execute javascript:, intent:, file: or other deep links from the address bar.
        if (SCHEME_PATTERN.containsMatchIn(input)) return searchUrl(input)

        val firstToken = input.substringBefore('/').substringBefore('?').substringBefore('#')
        val looksLikeHost = input.none { it.isWhitespace() } && firstToken.contains('.')
        return if (looksLikeHost) "https://$input" else searchUrl(input)
    }

    fun searchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        return SEARCH_URL + encoded
    }

    fun isValidWebUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.US)
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }
}
