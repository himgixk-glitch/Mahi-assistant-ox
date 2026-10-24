package com.example

import com.example.data.gemini.MahiPersonality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testLanguageSelectionAvailable() {
        val languages = MahiPersonality.AVAILABLE_LANGUAGES
        assertTrue(languages.isNotEmpty())
        assertNotNull(languages.find { it.id == "hinglish" })
        assertNotNull(languages.find { it.id == "hi" })
        assertNotNull(languages.find { it.id == "en" })
    }

    @Test
    fun testHinglishInstructionGenerated() {
        val prompt = MahiPersonality.buildSystemInstruction(sassLevel = 3, languageId = "hinglish")
        assertTrue(prompt.contains("Hinglish"))
        assertTrue(prompt.contains("sassy"))
    }

    @Test
    fun testLocalizedQuickPrompts() {
        val hinglishPrompts = MahiPersonality.getQuickPrompts("hinglish")
        assertTrue(hinglishPrompts.any { it.contains("roast", ignoreCase = true) })

        val hindiPrompts = MahiPersonality.getQuickPrompts("hi")
        assertTrue(hindiPrompts.any { it.contains("रोस्ट") })
    }
}
