package com.privacygate.app.gallery.model

import com.privacygate.app.ai.gemma.GemmaEnrichment
import com.privacygate.app.ai.gemma.GemmaEnrichmentStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryPhotoGemmaIndexTest {
    private val photo = GalleryPhoto(
        id = 7,
        name = "IMG_7.jpg",
        dateAdded = 1,
        size = 2,
        labels = listOf("Food"),
        isIndexed = true,
        gemmaStatus = GemmaEnrichmentStatus.READY,
        gemmaEnrichment = GemmaEnrichment(
            caption = "A blue sedan parked near a hotel",
            searchLabels = listOf("road trip"),
            vehiclePresent = true,
            registrationPlateVisible = true,
            conflictingLabels = listOf("Food")
        )
    )

    @Test
    fun secondLayerLabelsAndCaptionAreSearchable() {
        assertTrue(photo.matchesQuery("blue sedan"))
        assertTrue(photo.matchesQuery("road trip"))
    }

    @Test
    fun visiblePlateHasItsOwnCategoryAndReevaluatedConflictDoesNotEraseFirstLayer() {
        assertTrue(photo.matchesCategory(SmartCategory.VEHICLES))
        assertTrue(photo.matchesCategory(SmartCategory.PLATES))
        assertTrue(photo.matchesQuery("food"))
        assertFalse(photo.matchesCategory(SmartCategory.DOCUMENTS))
    }
}
