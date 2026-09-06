package com.privacygate.app.ai.gemma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaResponseParserTest {
    @Test
    fun parsesFencedJsonAndNormalizesDynamicLabels() {
        val raw = """
            ```json
            {
              "caption": " A white sedan parked outside a hotel. ",
              "objects": ["White sedan", "white sedan", "Hotel"],
              "scenes": ["Outdoor"],
              "activities": ["Parking"],
              "search_labels": ["Travel", "Vehicle"],
              "privacy_cues": ["Registration plate visible"],
              "incorrect_mlkit_labels": ["Food"],
              "needs_review": true,
              "vehicle_present": true,
              "registration_plate_visible": true,
              "registration_text": "DL 00 TEST"
            }
            ```
        """.trimIndent()

        val result = GemmaResponseParser.parse(raw, analyzedAtEpochMs = 123L)

        requireNotNull(result)
        assertEquals("A white sedan parked outside a hotel.", result.caption)
        assertEquals(listOf("White sedan", "Hotel"), result.objects)
        assertTrue(result.vehiclePresent)
        assertTrue(result.registrationPlateVisible)
        assertEquals(listOf("Food"), result.conflictingLabels)
        assertTrue(result.needsReview)
        assertTrue(result.searchLabels.contains("Vehicle with visible number plate"))
        assertFalse(result.allSearchableText().contains("DL 00 TEST"))
        assertEquals(123L, result.analyzedAtEpochMs)
    }

    @Test
    fun rejectsNonJsonAndBoundsGeneratedFields() {
        assertNull(GemmaResponseParser.parse("the image probably contains a car"))

        val labels = (1..40).joinToString(",") { "\"label-$it-with-an-unnecessarily-long-generated-suffix\"" }
        val result = GemmaResponseParser.parse(
            """{"caption":"${"x".repeat(400)}","objects":[$labels]}"""
        )

        requireNotNull(result)
        assertEquals(240, result.caption.length)
        assertEquals(20, result.objects.size)
        assertTrue(result.objects.all { it.length <= 48 })
    }
}
