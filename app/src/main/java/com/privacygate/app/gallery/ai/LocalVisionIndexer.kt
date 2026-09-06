package com.privacygate.app.gallery.ai

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.privacygate.app.ai.DynamicSemanticEngine
import com.privacygate.app.ai.ocr.MlKitOcrDetector
import com.privacygate.app.gallery.model.GalleryPhoto
import com.privacygate.app.settings.PrivacyPreferencesState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class LocalVisionIndexer(
    private val ocrDetector: MlKitOcrDetector = MlKitOcrDetector(),
    private val dynamicEngine: DynamicSemanticEngine = DynamicSemanticEngine()
) {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.45f)
            .build()
    )

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    private val selfieSegmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
    )

    // In-memory cache of indexed photo metadata
    private val indexCache = mutableMapOf<Long, GalleryPhoto>()

    fun getCachedPhoto(id: Long): GalleryPhoto? = indexCache[id]

    fun prefillCache(cachedPhotos: Collection<GalleryPhoto>) {
        for (p in cachedPhotos) {
            indexCache[p.id] = p
        }
    }

    suspend fun segmentPersonMask(bitmap: Bitmap): SegmentationMask? = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            selfieSegmenter.process(inputImage).await()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun indexPhoto(photo: GalleryPhoto, bitmap: Bitmap): GalleryPhoto = withContext(Dispatchers.Default) {
        indexCache[photo.id]?.let { return@withContext it }

        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            // 1. Image Labeling (400+ Categories)
            val labelsResult = labeler.process(inputImage).await()
            val labels = labelsResult.map { it.text }

            // 2. Face Detection
            val facesResult = faceDetector.process(inputImage).await()
            val faceCount = facesResult.size

            // 3. Person Segmentation check
            var hasPerson = faceCount > 0 || labels.any {
                it.equals("Person", ignoreCase = true) ||
                it.equals("Human", ignoreCase = true) ||
                it.equals("Selfie", ignoreCase = true)
            }

            // Quick check with selfie segmenter if faces weren't detected but person labels exist
            if (!hasPerson && labels.any { it.contains("Portrait", ignoreCase = true) || it.contains("Fashion", ignoreCase = true) }) {
                try {
                    val mask = selfieSegmenter.process(inputImage).await()
                    if (mask != null && mask.width > 0) {
                        hasPerson = true
                    }
                } catch (_: Exception) {}
            }

            // 4. OCR & Dynamic Document Classification
            val ocr = ocrDetector.recognize(bitmap)
            val analysis = dynamicEngine.analyze(ocr, PrivacyPreferencesState())

            // 5. Multi-modal segment categorization
            val detectedSegments = mutableSetOf<String>()

            if (hasPerson || faceCount > 0) {
                detectedSegments.add("People")
            }

            if (analysis.isActionable || analysis.documentType != null || ocr.fullText.length > 30 ||
                labels.any { it.equals("Document", ignoreCase = true) || it.equals("Paper", ignoreCase = true) }) {
                detectedSegments.add("Documents")
            }

            if (labels.any {
                it.equals("Car", ignoreCase = true) ||
                it.equals("Vehicle", ignoreCase = true) ||
                it.equals("Motorcycle", ignoreCase = true) ||
                it.equals("Truck", ignoreCase = true) ||
                it.equals("Automobile", ignoreCase = true) ||
                it.equals("Automotive exterior", ignoreCase = true) ||
                it.equals("Bicycle", ignoreCase = true)
            }) {
                detectedSegments.add("Vehicles")
            }

            if (labels.any {
                it.equals("Food", ignoreCase = true) ||
                it.equals("Meal", ignoreCase = true) ||
                it.equals("Dish", ignoreCase = true) ||
                it.equals("Cuisine", ignoreCase = true) ||
                it.equals("Fruit", ignoreCase = true) ||
                it.equals("Dessert", ignoreCase = true) ||
                it.equals("Beverage", ignoreCase = true)
            }) {
                detectedSegments.add("Food")
            }

            if (labels.any {
                it.equals("Plant", ignoreCase = true) ||
                it.equals("Tree", ignoreCase = true) ||
                it.equals("Nature", ignoreCase = true) ||
                it.equals("Sky", ignoreCase = true) ||
                it.equals("Flower", ignoreCase = true) ||
                it.equals("Dog", ignoreCase = true) ||
                it.equals("Cat", ignoreCase = true) ||
                it.equals("Pet", ignoreCase = true) ||
                it.equals("Animal", ignoreCase = true)
            }) {
                detectedSegments.add("Nature")
            }

            if (photo.bucketName?.contains("screenshot", ignoreCase = true) == true ||
                photo.name.contains("screenshot", ignoreCase = true) ||
                labels.any { it.equals("Screenshot", ignoreCase = true) || it.equals("Font", ignoreCase = true) }) {
                detectedSegments.add("Screenshots")
            }

            val indexedPhoto = photo.copy(
                labels = labels,
                faceCount = faceCount,
                hasPerson = hasPerson,
                isPortrait = faceCount == 1,
                isSelfie = faceCount == 1 && (photo.bucketName?.contains("selfie", ignoreCase = true) == true || labels.any { it.equals("Selfie", ignoreCase = true) }),
                isSensitiveDocument = analysis.isActionable,
                documentType = analysis.documentType,
                extractedText = ocr.fullText.take(500),
                isIndexed = true,
                segments = detectedSegments
            )

            indexCache[photo.id] = indexedPhoto
            indexedPhoto
        } catch (e: Exception) {
            e.printStackTrace()
            photo.copy(isIndexed = true)
        }
    }

    fun close() {
        labeler.close()
        faceDetector.close()
        selfieSegmenter.close()
        ocrDetector.close()
    }
}
