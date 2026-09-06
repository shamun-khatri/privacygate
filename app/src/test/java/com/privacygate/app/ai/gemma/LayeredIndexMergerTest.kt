package com.privacygate.app.ai.gemma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayeredIndexMergerTest {
    @Test
    fun recordsConfirmedAddedAndConflictingLabels() {
        val gemma = GemmaEnrichment(
            objects = listOf("car", "hotel"),
            conflictingLabels = listOf("Food", "not-from-ml-kit"),
            privacyCues = listOf("Registration plate visible"),
            registrationPlateVisible = true
        )

        val merged = LayeredIndexMerger.merge(
            mlKitLabels = listOf("Car", "Food"),
            mlKitSensitive = false,
            gemma = gemma
        )

        assertEquals(listOf("Car"), merged.enrichment.confirmedMlKitLabels)
        assertTrue(merged.enrichment.addedLabels.contains("hotel"))
        assertEquals(listOf("Food"), merged.enrichment.conflictingLabels)
        assertTrue(merged.enrichment.needsReview)
        assertFalse(merged.mlKitSensitive)
    }

    @Test
    fun gemmaCannotDowngradeExistingSensitiveVerdict() {
        val merged = LayeredIndexMerger.merge(
            mlKitLabels = listOf("Document"),
            mlKitSensitive = true,
            gemma = GemmaEnrichment()
        )

        assertTrue(merged.mlKitSensitive)
    }
}
