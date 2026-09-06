package com.privacygate.app.ai.gemma

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object GemmaResponseParser {
    private const val MAX_CAPTION_CHARS = 240
    private const val MAX_LABEL_CHARS = 48
    private const val MAX_LABELS_PER_FIELD = 20

    fun parse(raw: String, analyzedAtEpochMs: Long = System.currentTimeMillis()): GemmaEnrichment? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        val root = runCatching {
            JsonParser.parseString(raw.substring(start, end + 1)).asJsonObject
        }.getOrNull() ?: return null

        val plateVisible = root.boolean("registration_plate_visible")
        val vehiclePresent = root.boolean("vehicle_present") || plateVisible
        val searchLabels = root.labels("search_labels").toMutableList()
        if (plateVisible) searchLabels.addNormalized("Vehicle with visible number plate")

        return GemmaEnrichment(
            caption = root.string("caption").take(MAX_CAPTION_CHARS),
            objects = root.labels("objects"),
            scenes = root.labels("scenes"),
            activities = root.labels("activities"),
            searchLabels = searchLabels.normalized(),
            privacyCues = root.labels("privacy_cues"),
            vehiclePresent = vehiclePresent,
            registrationPlateVisible = plateVisible,
            conflictingLabels = root.labels("incorrect_mlkit_labels"),
            needsReview = root.boolean("needs_review") || root.labels("privacy_cues").isNotEmpty(),
            analyzedAtEpochMs = analyzedAtEpochMs
        )
    }

    private fun JsonObject.string(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()

    private fun JsonObject.boolean(key: String): Boolean =
        runCatching { get(key)?.asBoolean == true }.getOrDefault(false)

    private fun JsonObject.labels(key: String): List<String> =
        (get(key) as? JsonArray)?.mapNotNull { item ->
            runCatching { item.asString }.getOrNull()
        }.orEmpty().normalized()

    private fun MutableList<String>.addNormalized(value: String) {
        if (none { it.equals(value, ignoreCase = true) }) add(value)
    }

    private fun List<String>.normalized(): List<String> {
        val seen = mutableSetOf<String>()
        return asSequence()
            .map { it.trim().replace(Regex("\\s+"), " ").take(MAX_LABEL_CHARS) }
            .filter { it.isNotBlank() }
            .filter { seen.add(it.lowercase()) }
            .take(MAX_LABELS_PER_FIELD)
            .toList()
    }
}
