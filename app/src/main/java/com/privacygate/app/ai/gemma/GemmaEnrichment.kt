package com.privacygate.app.ai.gemma

import com.privacygate.app.settings.PrivacyPreferencesState
import com.privacygate.app.settings.SensitivityCategory

enum class GemmaEnrichmentStatus {
    NOT_REQUESTED,
    QUEUED,
    RUNNING,
    READY,
    UNAVAILABLE,
    FAILED
}

data class GemmaEnrichment(
    val caption: String = "",
    val objects: List<String> = emptyList(),
    val scenes: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val searchLabels: List<String> = emptyList(),
    val privacyCues: List<String> = emptyList(),
    val vehiclePresent: Boolean = false,
    val registrationPlateVisible: Boolean = false,
    val confirmedMlKitLabels: List<String> = emptyList(),
    val addedLabels: List<String> = emptyList(),
    val conflictingLabels: List<String> = emptyList(),
    val needsReview: Boolean = false,
    val modelVersion: String = MODEL_VERSION,
    val analyzedAtEpochMs: Long = 0L
) {
    fun allSearchableText(): String = buildList {
        add(caption)
        addAll(objects)
        addAll(scenes)
        addAll(activities)
        addAll(searchLabels)
        addAll(privacyCues)
        addAll(confirmedMlKitLabels)
        addAll(addedLabels)
        addAll(conflictingLabels)
    }.joinToString(" ")

    companion object {
        const val MODEL_VERSION = "gemma-4-e2b-it-litertlm-v1"
    }
}

object GemmaPlatePolicy {
    fun shouldWarn(enrichment: GemmaEnrichment, preferences: PrivacyPreferencesState): Boolean =
        enrichment.registrationPlateVisible &&
            SensitivityCategory.VEHICLE_PLATE in preferences.enabledCategories
}

sealed interface GemmaEnrichmentResult {
    data class Ready(val enrichment: GemmaEnrichment, val latencyMs: Long) : GemmaEnrichmentResult
    data class Unavailable(val reason: String) : GemmaEnrichmentResult
    data class Failed(val reason: String) : GemmaEnrichmentResult
}
