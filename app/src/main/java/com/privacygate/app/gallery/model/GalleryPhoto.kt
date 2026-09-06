package com.privacygate.app.gallery.model

import android.net.Uri

enum class SmartCategory(val label: String, val icon: String) {
    ALL("All", "🖼️"),
    PEOPLE("People", "👥"),
    DOCUMENTS("Documents", "🛡️"),
    VEHICLES("Vehicles", "🚗"),
    FOOD("Food", "🍽️"),
    NATURE("Nature", "🌿"),
    SCREENSHOTS("Screenshots", "📱")
}

data class GalleryPhoto(
    val id: Long,
    val uri: Uri? = null,
    val name: String,
    val dateAdded: Long,
    val size: Long,
    val bucketName: String? = null,
    val labels: List<String> = emptyList(),
    val faceCount: Int = 0,
    val hasPerson: Boolean = false,
    val isPortrait: Boolean = false,
    val isSelfie: Boolean = false,
    val isSensitiveDocument: Boolean = false,
    val documentType: String? = null,
    val extractedText: String = "",
    val isIndexed: Boolean = false,
    val segments: Set<String> = emptySet()
) {
    fun matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return name.lowercase().contains(q) ||
                (bucketName?.lowercase()?.contains(q) == true) ||
                labels.any { it.lowercase().contains(q) } ||
                extractedText.lowercase().contains(q) ||
                (documentType?.lowercase()?.contains(q) == true) ||
                segments.any { it.lowercase().contains(q) } ||
                ((q == "face" || q == "people" || q == "person" || q == "portrait") && (faceCount > 0 || hasPerson)) ||
                ((q == "document" || q == "id" || q == "doc" || q == "aadhaar" || q == "invoice") && (isSensitiveDocument || documentType != null)) ||
                (q == "screenshot" && (bucketName?.contains("screenshot", ignoreCase = true) == true || name.contains("screenshot", ignoreCase = true)))
    }

    fun matchesCategory(category: SmartCategory): Boolean {
        return when (category) {
            SmartCategory.ALL -> true
            SmartCategory.PEOPLE -> faceCount > 0 || hasPerson || isPortrait || isSelfie || segments.contains("People")
            SmartCategory.DOCUMENTS -> isSensitiveDocument || documentType != null || (extractedText.length > 25) || segments.contains("Documents")
            SmartCategory.VEHICLES -> segments.contains("Vehicles") || labels.any {
                it.equals("Car", ignoreCase = true) ||
                it.equals("Vehicle", ignoreCase = true) ||
                it.equals("Motorcycle", ignoreCase = true) ||
                it.equals("Truck", ignoreCase = true) ||
                it.equals("Automobile", ignoreCase = true) ||
                it.equals("Automotive exterior", ignoreCase = true) ||
                it.equals("Bicycle", ignoreCase = true)
            }
            SmartCategory.FOOD -> segments.contains("Food") || labels.any {
                it.equals("Food", ignoreCase = true) ||
                it.equals("Meal", ignoreCase = true) ||
                it.equals("Dish", ignoreCase = true) ||
                it.equals("Cuisine", ignoreCase = true) ||
                it.equals("Fruit", ignoreCase = true) ||
                it.equals("Dessert", ignoreCase = true) ||
                it.equals("Beverage", ignoreCase = true)
            }
            SmartCategory.NATURE -> segments.contains("Nature") || labels.any {
                it.equals("Plant", ignoreCase = true) ||
                it.equals("Tree", ignoreCase = true) ||
                it.equals("Nature", ignoreCase = true) ||
                it.equals("Sky", ignoreCase = true) ||
                it.equals("Flower", ignoreCase = true) ||
                it.equals("Dog", ignoreCase = true) ||
                it.equals("Cat", ignoreCase = true) ||
                it.equals("Pet", ignoreCase = true) ||
                it.equals("Animal", ignoreCase = true)
            }
            SmartCategory.SCREENSHOTS -> segments.contains("Screenshots") ||
                bucketName?.contains("screenshot", ignoreCase = true) == true ||
                name.contains("screenshot", ignoreCase = true) ||
                labels.any { it.equals("Screenshot", ignoreCase = true) || it.equals("Font", ignoreCase = true) }
        }
    }
}
