package com.privacygate.app.ai.gemma

import java.io.File

sealed interface GemmaModelAvailability {
    data object Missing : GemmaModelAvailability
    data class Invalid(val actualBytes: Long, val minimumBytes: Long) : GemmaModelAvailability
    data class Ready(val file: File) : GemmaModelAvailability
}

class GemmaModelLocator(
    private val modelDirectory: File,
    private val minimumModelBytes: Long = MINIMUM_MODEL_BYTES
) {
    fun modelFile(): File = File(modelDirectory, MODEL_FILE_NAME)

    fun availability(): GemmaModelAvailability {
        val file = modelFile()
        if (!file.isFile) return GemmaModelAvailability.Missing
        if (file.length() < minimumModelBytes) {
            return GemmaModelAvailability.Invalid(file.length(), minimumModelBytes)
        }
        return GemmaModelAvailability.Ready(file)
    }

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val MINIMUM_MODEL_BYTES = 2_500_000_000L
    }
}
