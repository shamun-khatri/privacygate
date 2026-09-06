package com.privacygate.app.ai

import android.graphics.Bitmap
import android.os.SystemClock
import com.privacygate.app.ai.face.MlKitFaceDetector
import com.privacygate.app.ai.nsfw.NsfwDetector
import com.privacygate.app.ai.ocr.MlKitOcrDetector
import com.privacygate.app.model.PrivacyScanResult
import com.privacygate.app.model.RiskLevel
import com.privacygate.app.model.SensitiveItem
import com.privacygate.app.settings.PrivacyPreferencesState
import com.privacygate.app.settings.SensitivityCategory
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrivacyInferenceEngine(
    private val ocrDetector: MlKitOcrDetector = MlKitOcrDetector(),
    private val faceDetector: MlKitFaceDetector = MlKitFaceDetector(),
    private val nsfwDetector: NsfwDetector = NsfwDetector(),
    private val dynamicEngine: DynamicSemanticEngine = DynamicSemanticEngine()
) {
    suspend fun scan(bitmap: Bitmap, prefs: PrivacyPreferencesState = PrivacyPreferencesState()): PrivacyScanResult = withContext(Dispatchers.Default) {
        val started = SystemClock.elapsedRealtime()
        try {
            if (bitmap.isRecycled || bitmap.width < 10 || bitmap.height < 10) {
                return@withContext PrivacyScanResult(
                    riskLevel = RiskLevel.SAFE,
                    documentType = null,
                    riskScore = 0f,
                    findings = emptyList(),
                    regions = emptyList(),
                    fullText = "",
                    latencyMs = SystemClock.elapsedRealtime() - started,
                    backend = "Bundled ML Kit (OCR + Vision) + local validators"
                )
            }

            val visualRegions = mutableListOf<DetectedRegion>()

            // 1. Face Detection (women, girls, kids, personal portraits)
            val shouldDetectFaces = prefs.enabledCategories.contains(SensitivityCategory.FACE_PORTRAIT) ||
                    prefs.enabledCategories.contains(SensitivityCategory.NSFW_SENSITIVE)
            val detectedFaces = if (shouldDetectFaces) faceDetector.detectFaces(bitmap) else emptyList()

            if (prefs.enabledCategories.contains(SensitivityCategory.FACE_PORTRAIT)) {
                for (faceBox in detectedFaces) {
                    visualRegions.add(
                        DetectedRegion(
                            boundingBox = faceBox,
                            category = SensitivityCategory.FACE_PORTRAIT,
                            label = "Human Face / Portrait",
                            snippet = "Visible Face"
                        )
                    )
                }
            }

            // 2. Sensitive / NSFW Media Detection (excluding facial skin)
            if (prefs.enabledCategories.contains(SensitivityCategory.NSFW_SENSITIVE)) {
                val nsfwResult = nsfwDetector.analyze(bitmap, detectedFaces)
                if (nsfwResult.isSensitive) {
                    val box = nsfwResult.bounds ?: Rect(0, 0, bitmap.width, bitmap.height)
                    visualRegions.add(
                        DetectedRegion(
                            boundingBox = box,
                            category = SensitivityCategory.NSFW_SENSITIVE,
                            label = "Sensitive / Private Content",
                            snippet = "Exposed Body (${(nsfwResult.exposedSkinRatio * 100).toInt()}%)"
                        )
                    )
                }
            }

            // 3. OCR Text Recognition
            val ocr = ocrDetector.recognize(bitmap)
            val analysis = SmartFieldDetector.enhance(dynamicEngine.analyze(ocr, prefs, visualRegions), ocr, prefs)
            val findings = analysis.regions.map {
                SensitiveItem(it.category, it.label, it.snippet)
            }.distinctBy { it.label + it.snippet }
            PrivacyScanResult(
                riskLevel = if (analysis.isActionable) RiskLevel.ACTIONABLE else RiskLevel.SAFE,
                documentType = analysis.documentType,
                riskScore = analysis.riskScore,
                findings = findings,
                regions = analysis.regions,
                fullText = ocr.fullText,
                latencyMs = SystemClock.elapsedRealtime() - started,
                backend = "Bundled ML Kit (OCR + Vision) + local validators"
            )
        } catch (t: Throwable) {
            android.util.Log.w("PrivacyGate", "Privacy scan failed or cancelled: ${t.message}")
            PrivacyScanResult(
                riskLevel = RiskLevel.SAFE,
                documentType = null,
                riskScore = 0f,
                findings = emptyList(),
                regions = emptyList(),
                fullText = "",
                latencyMs = SystemClock.elapsedRealtime() - started,
                backend = "Bundled ML Kit (OCR + Vision) + local validators"
            )
        }
    }
    fun close() {
        ocrDetector.close()
        faceDetector.close()
    }
}
