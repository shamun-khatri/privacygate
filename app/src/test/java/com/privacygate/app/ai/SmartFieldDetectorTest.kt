package com.privacygate.app.ai

import android.graphics.Rect
import com.privacygate.app.ai.ocr.OcrResult
import com.privacygate.app.ai.ocr.OcrTextElement
import com.privacygate.app.settings.PrivacyPreferencesState
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartFieldDetectorTest {
    private val empty = DynamicAnalysisResult(0f, false, null, emptySet(), emptyList(), "")

    @Test fun detectsCardCvv() {
        val ocr = OcrResult("Card CVV 123", listOf(OcrTextElement("CVV: 123", Rect(1, 2, 20, 8))))
        val result = SmartFieldDetector.enhance(empty, ocr, PrivacyPreferencesState())
        assertTrue(result.regions.any { it.label == "Card CVV" && it.snippet == "123" })
    }

    @Test fun detectsCustomerAddressOnlyInDocumentContext() {
        val ocr = OcrResult("Tax invoice Bill to", listOf(OcrTextElement("Bill to: 14 Lake Road Mumbai", Rect(1, 2, 100, 8))))
        val result = SmartFieldDetector.enhance(empty, ocr, PrivacyPreferencesState())
        assertTrue(result.regions.any { it.label == "Customer Address" })
    }
}
