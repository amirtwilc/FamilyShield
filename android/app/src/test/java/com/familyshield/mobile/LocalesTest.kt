package com.familyshield.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LocalesTest {
    @Test
    fun hebrewLocaleIsRtl() {
        assertTrue(Locales.isRtl(Locale.forLanguageTag("he")))
    }

    @Test
    fun englishLocaleIsLtr() {
        assertFalse(Locales.isRtl(Locale.forLanguageTag("en")))
    }

    @Test
    fun switchingFromEnglishToHebrewRequiresActivityRecreation() {
        assertTrue(localeChangeRequiresActivityRecreation("en", "he"))
    }

    @Test
    fun switchingFromHebrewToEnglishRequiresActivityRecreation() {
        assertTrue(localeChangeRequiresActivityRecreation("he", "en"))
    }

    @Test
    fun keepingTheAttachedLanguageDoesNotRecreateActivity() {
        assertFalse(localeChangeRequiresActivityRecreation("he", "he"))
    }
}
