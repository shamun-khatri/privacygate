package com.privacygate.app.ai.gemma

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class GemmaEnrichmentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var runtime: GemmaRuntime

    override fun onCreate() {
        super.onCreate()
        runtime = GemmaRuntime(File(cacheDir, "gemma-runtime").apply { mkdirs() })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val receiver = intent?.receiverExtra(EXTRA_RECEIVER)
        val modelPath = intent?.getStringExtra(EXTRA_MODEL_PATH)
        val imagePath = intent?.getStringExtra(EXTRA_IMAGE_PATH)
        val prompt = intent?.getStringExtra(EXTRA_PROMPT)

        if (receiver == null || modelPath == null || imagePath == null || prompt == null) {
            receiver?.send(RESULT_ERROR, Bundle().apply { putString(EXTRA_ERROR, "Invalid Gemma request") })
            stopSelf(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            val result = runCatching {
                val model = File(modelPath)
                val image = File(imagePath)
                require(model.isFile && model.name == GemmaModelLocator.MODEL_FILE_NAME) { "Gemma model unavailable" }
                require(image.isFile && image.canonicalPath.startsWith(cacheDir.canonicalPath + File.separator)) {
                    "Invalid image path"
                }
                withTimeout(INFERENCE_TIMEOUT_MS) {
                    runtime.analyze(model, image, prompt)
                }
            }

            result.onSuccess { output ->
                receiver.send(RESULT_OK, Bundle().apply {
                    putString(EXTRA_RESPONSE, output.response)
                    putLong(EXTRA_LATENCY_MS, output.latencyMs)
                    putString(EXTRA_BACKEND, output.backend)
                })
            }.onFailure { error ->
                receiver.send(RESULT_ERROR, Bundle().apply {
                    putString(EXTRA_ERROR, error.message ?: error.javaClass.simpleName)
                })
            }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runtime.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    private fun Intent.receiverExtra(key: String): ResultReceiver? =
        getParcelableExtra(key) as? ResultReceiver

    companion object {
        const val EXTRA_RECEIVER = "gemma_receiver"
        const val EXTRA_MODEL_PATH = "gemma_model_path"
        const val EXTRA_IMAGE_PATH = "gemma_image_path"
        const val EXTRA_PROMPT = "gemma_prompt"
        const val EXTRA_RESPONSE = "gemma_response"
        const val EXTRA_ERROR = "gemma_error"
        const val EXTRA_LATENCY_MS = "gemma_latency_ms"
        const val EXTRA_BACKEND = "gemma_backend"
        const val RESULT_OK = 1
        const val RESULT_ERROR = 2
        const val INFERENCE_TIMEOUT_MS = 180_000L
    }
}
