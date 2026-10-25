package com.vortexdl.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderMapConverterTest {

    private val converter = HeaderMapConverter()

    @Test
    fun `converts map to json and back accurately`() {
        val original = mapOf(
            "User-Agent" to "VortexDL/1.0",
            "Referer" to "https://example.com/watch",
            "Cookie" to "session=xyz123",
        )

        val json = converter.fromHeaderMap(original)
        val restored = converter.toHeaderMap(json)

        assertEquals(original, restored)
    }

    @Test
    fun `handles empty or null inputs gracefully`() {
        assertEquals("{}", converter.fromHeaderMap(emptyMap()))
        assertEquals("{}", converter.fromHeaderMap(null))
        assertTrue(converter.toHeaderMap(null).isEmpty())
        assertTrue(converter.toHeaderMap("").isEmpty())
        assertTrue(converter.toHeaderMap("{}").isEmpty())
        assertTrue(converter.toHeaderMap("invalid json").isEmpty())
    }
}
