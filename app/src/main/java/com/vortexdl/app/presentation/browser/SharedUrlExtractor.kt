package com.vortexdl.app.presentation.browser

import android.content.Intent
import java.net.URI
import java.util.Locale

/**
 * Extracts the first web URL from an `ACTION_SEND` / `text/plain` payload.
 *
 * Sharing sheets often append surrounding text such as "Watch this: <url> !", so the
 * parser searches for an embedded http(s) URL before falling back to a bare domain.
 */
object SharedUrlExtractor {

    private val URL_PATTERN = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val TRAILING_PUNCTUATION = charArrayOf(
        '.', ',', ';', ':', '!', '?', ')', ']', '}', '>', '"', '\'',
    )

    fun extract(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (!type.startsWith("text/", ignoreCase = true)) return null
        return extractFromText(
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
        )
    }

    fun extractFromText(text: String?): String? {
        val content = text?.trim().orEmpty()
        if (content.isEmpty()) return null

        val embedded = URL_PATTERN.find(content)?.value
            ?.trimEnd(*TRAILING_PUNCTUATION)
            ?.trim()
        if (embedded != null && isValidWebUrl(embedded)) return embedded

        // Some apps share a bare domain or domain/path without a scheme.
        val candidate = content
            .substringBefore(' ')
            .substringBefore('\n')
            .trim()
            .trimEnd(*TRAILING_PUNCTUATION)

        if (candidate.contains('.') && !candidate.contains("://")) {
            val withScheme = "https://$candidate"
            if (isValidWebUrl(withScheme)) return withScheme
        }

        return null
    }

    private fun isValidWebUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }
}
