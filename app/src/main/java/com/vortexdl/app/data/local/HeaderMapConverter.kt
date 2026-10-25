package com.vortexdl.app.data.local

import androidx.room.TypeConverter

/**
 * Pure-Kotlin TypeConverter to serialize and deserialize HTTP headers map to/from JSON.
 * Does not depend on Android's stubbed JSONObject so it runs identically on both JVM and device.
 */
class HeaderMapConverter {

    @TypeConverter
    fun fromHeaderMap(headers: Map<String, String>?): String {
        if (headers.isNullOrEmpty()) return "{}"
        val builder = StringBuilder("{")
        var first = true
        for ((key, value) in headers) {
            if (!first) builder.append(",")
            first = false
            builder.append("\"").append(escapeJson(key)).append("\":\"")
                .append(escapeJson(value)).append("\"")
        }
        builder.append("}")
        return builder.toString()
    }

    @TypeConverter
    fun toHeaderMap(jsonStr: String?): Map<String, String> {
        val trimmed = jsonStr?.trim() ?: return emptyMap()
        if (trimmed.length < 2 || !trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return emptyMap()
        }

        val result = LinkedHashMap<String, String>()
        val content = trimmed.substring(1, trimmed.length - 1).trim()
        if (content.isEmpty()) return emptyMap()

        var i = 0
        val length = content.length

        while (i < length) {
            while (i < length && content[i] != '"') i++
            if (i >= length) break
            i++ // skip opening quote

            val keyBuilder = StringBuilder()
            while (i < length) {
                val c = content[i]
                if (c == '\\' && i + 1 < length) {
                    keyBuilder.append(unescapeChar(content[i + 1]))
                    i += 2
                } else if (c == '"') {
                    i++ // skip closing quote
                    break
                } else {
                    keyBuilder.append(c)
                    i++
                }
            }

            while (i < length && content[i] != ':') i++
            if (i >= length) break
            i++ // skip colon

            while (i < length && content[i] != '"') i++
            if (i >= length) break
            i++ // skip opening quote

            val valueBuilder = StringBuilder()
            while (i < length) {
                val c = content[i]
                if (c == '\\' && i + 1 < length) {
                    valueBuilder.append(unescapeChar(content[i + 1]))
                    i += 2
                } else if (c == '"') {
                    i++ // skip closing quote
                    break
                } else {
                    valueBuilder.append(c)
                    i++
                }
            }

            result[keyBuilder.toString()] = valueBuilder.toString()

            while (i < length && (content[i] == ',' || content[i].isWhitespace())) i++
        }

        return result
    }

    private fun escapeJson(value: String): String {
        val builder = StringBuilder()
        for (c in value) {
            when (c) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\u000c' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> builder.append(c)
            }
        }
        return builder.toString()
    }

    private fun unescapeChar(c: Char): Char = when (c) {
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'b' -> '\b'
        'f' -> '\u000c'
        '"' -> '"'
        '\\' -> '\\'
        '/' -> '/'
        else -> c
    }
}
