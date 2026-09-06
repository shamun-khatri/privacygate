package com.privacygate.app.ai.gemma

data class LayeredIndexResult(
    val mlKitSensitive: Boolean,
    val enrichment: GemmaEnrichment
)

object LayeredIndexMerger {
    fun merge(
        mlKitLabels: List<String>,
        mlKitSensitive: Boolean,
        gemma: GemmaEnrichment
    ): LayeredIndexResult {
        val generated = buildList {
            addAll(gemma.objects)
            addAll(gemma.scenes)
            addAll(gemma.activities)
            addAll(gemma.searchLabels)
        }.distinctCaseInsensitive()

        val confirmed = mlKitLabels.filter { mlLabel ->
            generated.any { it.equals(mlLabel, ignoreCase = true) }
        }.distinctCaseInsensitive()

        val conflicts = mlKitLabels.filter { mlLabel ->
            gemma.conflictingLabels.any { it.equals(mlLabel, ignoreCase = true) }
        }.distinctCaseInsensitive()

        val added = generated.filter { generatedLabel ->
            confirmed.none { it.equals(generatedLabel, ignoreCase = true) }
        }.distinctCaseInsensitive()

        val merged = gemma.copy(
            confirmedMlKitLabels = confirmed,
            addedLabels = added,
            conflictingLabels = conflicts,
            needsReview = gemma.needsReview || conflicts.isNotEmpty() ||
                gemma.registrationPlateVisible || gemma.privacyCues.isNotEmpty()
        )

        return LayeredIndexResult(
            mlKitSensitive = mlKitSensitive,
            enrichment = merged
        )
    }

    private fun List<String>.distinctCaseInsensitive(): List<String> {
        val seen = mutableSetOf<String>()
        return filter { seen.add(it.lowercase()) }
    }
}
