package com.privacygate.app.ai.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class OcrTextElement(
    val text: String,
    val boundingBox: Rect?
)

data class OcrResult(
    val fullText: String,
    val elements: List<OcrTextElement>
)

class MlKitOcrDetector {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        if (bitmap.isRecycled || bitmap.width < 10 || bitmap.height < 10) {
            return OcrResult("", emptyList())
        }
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(inputImage).await()

            val elements = mutableListOf<OcrTextElement>()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    elements.add(OcrTextElement(line.text, line.boundingBox))
                }
            }
            OcrResult(
                fullText = visionText.text,
                elements = elements
            )
        } catch (t: Throwable) {
            android.util.Log.w("PrivacyGate", "ML Kit OCR failed or cancelled: ${t.message}")
            OcrResult("", emptyList())
        }
    }

    fun close() {
        try {
            recognizer.close()
        } catch (t: Throwable) {
            // ignore
        }
    }
}
