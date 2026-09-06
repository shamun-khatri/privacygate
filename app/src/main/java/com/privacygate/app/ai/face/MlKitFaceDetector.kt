package com.privacygate.app.ai.face

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

class MlKitFaceDetector {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.12f)
            .build()
    )

    suspend fun detectFaces(bitmap: Bitmap): List<Rect> {
        if (bitmap.isRecycled || bitmap.width < 40 || bitmap.height < 40) {
            return emptyList()
        }
        val minDimension = minOf(bitmap.width, bitmap.height)
        val minFacePx = (minDimension * 0.10f).toInt().coerceAtLeast(40)
        val totalArea = bitmap.width.toLong() * bitmap.height.toLong()

        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faceList = detector.process(inputImage).await()
            faceList.mapNotNull { face ->
                val rawBox = face.boundingBox
                val box = Rect(
                    rawBox.left.coerceAtLeast(0),
                    rawBox.top.coerceAtLeast(0),
                    rawBox.right.coerceAtMost(bitmap.width),
                    rawBox.bottom.coerceAtMost(bitmap.height)
                )
                val area = box.width().toLong() * box.height()
                // Reject tiny distant faces or background noise (must be >= 10% min dimension and >= 1.2% total image area)
                if (box.width() >= minFacePx && box.height() >= minFacePx && area >= (totalArea * 0.012f)) {
                    box
                } else null
            }
        } catch (t: Throwable) {
            android.util.Log.w("PrivacyGate", "ML Kit Face detection failed or cancelled: ${t.message}")
            emptyList()
        }
    }

    fun close() {
        try {
            detector.close()
        } catch (_: Throwable) {}
    }
}
