package com.privacygate.app.gallery

import com.privacygate.app.gallery.model.GalleryPhoto
import com.privacygate.app.gallery.model.SmartCategory
import org.junit.Assert.*
import org.junit.Test

class GalleryTest {

    @Test
    fun testGalleryPhotoSemanticQueryMatching() {
        val photo = GalleryPhoto(
            id = 101L,
            name = "trip_to_goa.jpg",
            dateAdded = 1700000000L,
            size = 2048000L,
            labels = listOf("Beach", "Sunset", "Ocean", "Sand"),
            faceCount = 2,
            isSensitiveDocument = false,
            extractedText = ""
        )

        // Matches label
        assertTrue(photo.matchesQuery("sunset"))
        assertTrue(photo.matchesQuery("beach"))

        // Matches face count query
        assertTrue(photo.matchesQuery("people"))
        assertTrue(photo.matchesQuery("face"))

        // Matches filename
        assertTrue(photo.matchesQuery("goa"))

        // Does not match unrelated query
        assertFalse(photo.matchesQuery("passport"))
        assertFalse(photo.matchesQuery("invoice"))
    }

    @Test
    fun testSensitiveDocumentQueryMatching() {
        val docPhoto = GalleryPhoto(
            id = 102L,
            name = "scan_001.jpg",
            dateAdded = 1700000050L,
            size = 512000L,
            labels = listOf("Document", "Paper"),
            faceCount = 0,
            isSensitiveDocument = true,
            documentType = "Identity Document / ID Card",
            extractedText = "Government of India Unique Identification Authority of India Aadhaar 9999 4172 6388"
        )

        assertTrue(docPhoto.matchesQuery("document"))
        assertTrue(docPhoto.matchesQuery("aadhaar"))
        assertTrue(docPhoto.matchesQuery("identity"))
        assertTrue(docPhoto.matchesCategory(SmartCategory.DOCUMENTS))
        assertFalse(docPhoto.matchesCategory(SmartCategory.FOOD))
    }

    @Test
    fun testSmartCategoryFiltering() {
        val foodPhoto = GalleryPhoto(
            id = 103L,
            name = "dinner.jpg",
            dateAdded = 1700000100L,
            size = 1024000L,
            labels = listOf("Food", "Plate", "Meal", "Delicious"),
            faceCount = 0
        )

        assertTrue(foodPhoto.matchesCategory(SmartCategory.ALL))
        assertTrue(foodPhoto.matchesCategory(SmartCategory.FOOD))
        assertFalse(foodPhoto.matchesCategory(SmartCategory.PEOPLE))
        assertFalse(foodPhoto.matchesCategory(SmartCategory.NATURE))
    }

    @Test
    fun testVehicleAndScreenshotCategoryFiltering() {
        val carPhoto = GalleryPhoto(
            id = 104L,
            name = "car_front.jpg",
            dateAdded = 1700000200L,
            size = 1500000L,
            labels = listOf("Car", "Vehicle", "Automotive exterior"),
            segments = setOf("Vehicles")
        )
        assertTrue(carPhoto.matchesCategory(SmartCategory.VEHICLES))
        assertTrue(carPhoto.matchesQuery("car"))

        val screenshotPhoto = GalleryPhoto(
            id = 105L,
            name = "Screenshot_20260905.png",
            dateAdded = 1700000300L,
            size = 800000L,
            bucketName = "Screenshots",
            labels = listOf("Font", "Screenshot"),
            segments = setOf("Screenshots")
        )
        assertTrue(screenshotPhoto.matchesCategory(SmartCategory.SCREENSHOTS))
        assertTrue(screenshotPhoto.matchesQuery("screenshot"))
    }
}
