package com.privacygate.app.ai.gemma

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class GemmaProcessClient(private val context: Context) {
    private val modelDirectory: File
        get() = requireNotNull(context.getExternalFilesDir("models"))

    fun modelAvailability(): GemmaModelAvailability =
        GemmaModelLocator(modelDirectory).availability()

    suspend fun reEvaluate(image: File, summary: MlKitIndexSummary): GemmaEnrichmentResult {
        val availability = modelAvailability()
        if (availability !is GemmaModelAvailability.Ready) {
            return GemmaEnrichmentResult.Unavailable(
                when (availability) {
                    GemmaModelAvailability.Missing -> "Gemma 4 model is not installed"
                    is GemmaModelAvailability.Invalid -> "Gemma 4 model file is incomplete"
                    is GemmaModelAvailability.Ready -> error("handled above")
                }
            )
        }

        val serviceIntent = Intent(context, GemmaEnrichmentService::class.java)
        val response = withTimeoutOrNull(CLIENT_TIMEOUT_MS) {
            suspendCancellableCoroutine<ServiceResponse> { continuation ->
                val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
                        if (!continuation.isActive) return
                        if (resultCode == GemmaEnrichmentService.RESULT_OK) {
                            continuation.resume(
                                ServiceResponse.Success(
                                    raw = resultData.getString(GemmaEnrichmentService.EXTRA_RESPONSE).orEmpty(),
                                    latencyMs = resultData.getLong(GemmaEnrichmentService.EXTRA_LATENCY_MS)
                                )
                            )
                        } else {
                            continuation.resume(
                                ServiceResponse.Error(
                                    resultData.getString(GemmaEnrichmentService.EXTRA_ERROR)
                                        ?: "Gemma process failed"
                                )
                            )
                        }
                    }
                }

                val started = runCatching {
                    serviceIntent.apply {
                        putExtra(GemmaEnrichmentService.EXTRA_RECEIVER, receiver)
                        putExtra(GemmaEnrichmentService.EXTRA_MODEL_PATH, availability.file.absolutePath)
                        putExtra(GemmaEnrichmentService.EXTRA_IMAGE_PATH, image.absolutePath)
                        putExtra(GemmaEnrichmentService.EXTRA_PROMPT, GemmaPromptBuilder.build(summary))
                    }
                    context.startService(serviceIntent)
                }
                if (started.isFailure && continuation.isActive) {
                    continuation.resume(ServiceResponse.Error(started.exceptionOrNull()?.message ?: "Cannot start Gemma"))
                }
            }
        }

        if (response == null) {
            context.stopService(serviceIntent)
            return GemmaEnrichmentResult.Failed("Gemma timed out; fast ML Kit results are still active")
        }
        return when (response) {
            is ServiceResponse.Error -> GemmaEnrichmentResult.Failed(response.reason)
            is ServiceResponse.Success -> {
                val parsed = GemmaResponseParser.parse(response.raw)
                    ?: return GemmaEnrichmentResult.Failed("Gemma returned an invalid structured result")
                val merged = LayeredIndexMerger.merge(
                    mlKitLabels = summary.labels,
                    mlKitSensitive = summary.isSensitiveDocument,
                    gemma = parsed
                )
                GemmaEnrichmentResult.Ready(merged.enrichment, response.latencyMs)
            }
        }
    }

    private sealed interface ServiceResponse {
        data class Success(val raw: String, val latencyMs: Long) : ServiceResponse
        data class Error(val reason: String) : ServiceResponse
    }

    companion object {
        private const val CLIENT_TIMEOUT_MS = GemmaEnrichmentService.INFERENCE_TIMEOUT_MS + 10_000L
    }
}
