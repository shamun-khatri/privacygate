package com.privacygate.app.ai

import com.privacygate.app.ai.ocr.OcrResult
import com.privacygate.app.settings.PrivacyPreferencesState
import com.privacygate.app.settings.SensitivityCategory

object SmartFieldDetector {
    private val cvv = Regex("(?i)\\b(?:cvv|cvc|security code)\\s*[:\\-]?\\s*([0-9]{3,4})\\b")
    private val address = Regex("(?i)\\b(?:bill to|ship to|customer address|patient address|address)\\s*[:\\-]?\\s+.{5,}")
    private val documentContext = Regex("(?i)\\b(invoice|bill to|prescription|patient|clinic|hospital|rx)\\b")

    fun enhance(base: DynamicAnalysisResult, ocr: OcrResult, prefs: PrivacyPreferencesState): DynamicAnalysisResult {
        val extra = mutableListOf<DetectedRegion>()
        for (element in ocr.elements) {
            val box = element.boundingBox ?: continue
            if (SensitivityCategory.FINANCIAL_BANKING in prefs.enabledCategories) {
                cvv.findAll(element.text).forEach { match ->
                    extra += DetectedRegion(box, SensitivityCategory.FINANCIAL_BANKING, "Card CVV", match.groupValues[1])
                }
            }
            if (SensitivityCategory.CONTACT_INFO in prefs.enabledCategories &&
                documentContext.containsMatchIn(ocr.fullText) && address.containsMatchIn(element.text)) {
                extra += DetectedRegion(box, SensitivityCategory.CONTACT_INFO, "Customer Address", element.text.take(80))
            }
        }
        if (extra.isEmpty()) return base
        val categories = base.detectedCategories + extra.map { it.category }
        val score = maxOf(base.riskScore, if (extra.any { it.label == "Card CVV" }) 0.98f else 0.80f)
        val documentType = base.documentType ?: when {
            SensitivityCategory.FINANCIAL_BANKING in categories -> "Banking / Payment Record"
            SensitivityCategory.CONTACT_INFO in categories -> "Contact Details"
            else -> null
        }
        return base.copy(
            riskScore = score,
            isActionable = score >= prefs.sensitivityLevel.minRiskScore,
            documentType = documentType,
            detectedCategories = categories,
            regions = (base.regions + extra).distinctBy { "${it.label}:${it.boundingBox}" }
        )
    }
}
