package com.privacygate.app.ai.gemma

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import kotlinx.coroutines.flow.collect

data class GemmaRuntimeOutput(
    val response: String,
    val latencyMs: Long,
    val backend: String
)

/** Runs only in the :gemma process. Native failures therefore cannot terminate the main app. */
class GemmaRuntime(private val cacheDirectory: File) : AutoCloseable {
    private var engine: Engine? = null
    private var modelPath: String? = null
    private var backendName: String = "GPU"

    suspend fun analyze(model: File, image: File, prompt: String): GemmaRuntimeOutput {
        val start = System.currentTimeMillis()
        var activeEngine = engineFor(model, useGpu = true)
        return try {
            val response = infer(activeEngine, image, prompt)
            GemmaRuntimeOutput(response, System.currentTimeMillis() - start, backendName)
        } catch (first: Exception) {
            closeEngine()
            activeEngine = engineFor(model, useGpu = false)
            val response = infer(activeEngine, image, prompt)
            GemmaRuntimeOutput(response, System.currentTimeMillis() - start, backendName)
        }
    }

    private fun engineFor(model: File, useGpu: Boolean): Engine {
        val requestedBackend = if (useGpu) "GPU" else "CPU"
        engine?.takeIf { modelPath == model.absolutePath && backendName == requestedBackend }?.let {
            return it
        }

        closeEngine()
        val textBackend = if (useGpu) Backend.GPU() else Backend.CPU()
        val visionBackend = if (useGpu) Backend.GPU() else Backend.CPU()
        val created = Engine(
            EngineConfig(
                modelPath = model.absolutePath,
                backend = textBackend,
                visionBackend = visionBackend,
                audioBackend = Backend.CPU(),
                maxNumTokens = 2_048,
                maxNumImages = 1,
                cacheDir = cacheDirectory.absolutePath
            )
        )
        created.initialize()
        engine = created
        modelPath = model.absolutePath
        backendName = requestedBackend
        return created
    }

    private suspend fun infer(engine: Engine, image: File, prompt: String): String {
        val conversation = engine.createConversation(
            ConversationConfig(
                maxOutputToken = 320,
                thinkingConfig = ThinkingConfig(enableThinking = false)
            )
        )
        return conversation.use {
            val output = StringBuilder()
            val contents = Contents.of(
                Content.Text(prompt),
                Content.ImageFile(image.absolutePath)
            )
            it.sendMessageAsync(contents).collect { message ->
                message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .forEach { text -> output.append(text.text) }
            }
            output.toString()
        }
    }

    private fun closeEngine() {
        runCatching { engine?.close() }
        engine = null
        modelPath = null
    }

    override fun close() = closeEngine()
}
