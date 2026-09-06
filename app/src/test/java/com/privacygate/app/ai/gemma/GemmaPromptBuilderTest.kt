package com.privacygate.app.ai.gemma

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaPromptBuilderTest {
    @Test
    fun asksGemmaToReevaluateMlKitWithoutTranscribingSensitiveValues() {
        val prompt = GemmaPromptBuilder.build(
            MlKitIndexSummary(
                labels = listOf("Car", "Food"),
                segments = setOf("Vehicles"),
                documentType = "Identity Document",
                isSensitiveDocument = true
            )
        )

        assertTrue(prompt.contains("Car"))
        assertTrue(prompt.contains("Food"))
        assertTrue(prompt.contains("incorrect_mlkit_labels"))
        assertTrue(prompt.contains("registration_plate_visible"))
        assertTrue(prompt.contains("Identity Document"))
        assertTrue(prompt.contains("ML Kit marked sensitive: true"))
        assertTrue(prompt.contains("Do not transcribe"))
        assertFalse(prompt.contains("extractedText"))
    }
}
