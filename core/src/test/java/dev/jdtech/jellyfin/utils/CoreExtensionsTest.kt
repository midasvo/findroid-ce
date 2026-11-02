package dev.jdtech.jellyfin.utils

import java.util.Locale
import org.jellyfin.sdk.model.DateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreExtensionsTest {
    @Test
    fun `format formats date following provided locale`() {
        val dateTime = DateTime.of(1961, 1, 25, 0, 0)
        val formattedUs = dateTime.format(Locale.US)
        val formattedNl = dateTime.format(Locale.forLanguageTag("nl-NL"))

        assertEquals("1/25/61", formattedUs)
        assertEquals("25-01-1961", formattedNl)
    }
}
