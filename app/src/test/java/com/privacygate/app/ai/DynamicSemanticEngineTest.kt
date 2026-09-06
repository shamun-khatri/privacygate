package com.privacygate.app.ai

import android.graphics.Rect
import com.privacygate.app.ai.ocr.OcrResult
import com.privacygate.app.ai.ocr.OcrTextElement
import com.privacygate.app.settings.PrivacyPreferencesState
import com.privacygate.app.settings.SensitivityCategory
import com.privacygate.app.settings.SensitivityLevel
import org.junit.Assert.*
import org.junit.Test

class DynamicSemanticEngineTest {

    private val engine = DynamicSemanticEngine()

    @Test
    fun testVerhoeffAadhaarValidation() {
        // Known valid Aadhaar test numbers with valid Verhoeff checksums
        // 2345 6789 0120 is a sample constructed with valid Verhoeff digit
        assertTrue(engine.validateAadhaar("234567890120") || engine.validateAadhaar("999941057058"))
        // Invalid length or corrupted checksum
        assertFalse(engine.validateAadhaar("123456789012"))
        assertFalse(engine.validateAadhaar("123"))
    }

    @Test
    fun testLuhnCardValidation() {
        // Test standard 16-digit card numbers
        assertTrue(engine.validateLuhn("4532 0151 1283 0366"))
        assertFalse(engine.validateLuhn("4532 0151 1283 0367")) // Altered last digit
    }

    @Test
    fun testPanCardDetection() {
        val ocr = OcrResult(
            fullText = "Income Tax Department PAN: ABCDE1234F",
            elements = listOf(
                OcrTextElement("Income Tax Department", Rect(10, 10, 200, 30)),
                OcrTextElement("PAN: ABCDE1234F", Rect(10, 40, 200, 60))
            )
        )
        val result = engine.analyze(ocr, PrivacyPreferencesState())
        assertTrue(result.isActionable)
        assertTrue(result.detectedCategories.contains(SensitivityCategory.IDENTITY_DOCUMENT))
        assertEquals("Identity Document / ID Card", result.documentType)
    }

    @Test
    fun testMedicalPrescriptionDetection() {
        val ocr = OcrResult(
            fullText = "City Clinic Rx: Paracetamol 500mg Dosage: 1 tablet daily Diagnosis: Acute Pharyngitis",
            elements = listOf(
                OcrTextElement("City Clinic Rx: Paracetamol 500mg", Rect(10, 10, 300, 30)),
                OcrTextElement("Diagnosis: Acute Pharyngitis", Rect(10, 40, 300, 60))
            )
        )
        val result = engine.analyze(ocr, PrivacyPreferencesState())
        assertTrue(result.isActionable)
        assertTrue(result.detectedCategories.contains(SensitivityCategory.MEDICAL_HEALTH))
    }

    @Test
    fun testOtpDetection() {
        val ocr = OcrResult(
            fullText = "Your verification OTP is 849201. Do not share this code.",
            elements = listOf(
                OcrTextElement("Your verification OTP is 849201.", Rect(10, 10, 300, 30))
            )
        )
        val result = engine.analyze(ocr, PrivacyPreferencesState())
        assertTrue(result.isActionable)
        assertTrue(result.detectedCategories.contains(SensitivityCategory.SECRET_CREDENTIAL))
    }

    @Test
    fun testCategoryToggleSuppression() {
        val ocr = OcrResult(
            fullText = "Call support at +91 98765 43210 or email help@example.com",
            elements = listOf(
                OcrTextElement("Call support at +91 98765 43210", Rect(10, 10, 300, 30))
            )
        )

        // When CONTACT_INFO is enabled
        val enabledResult = engine.analyze(ocr, PrivacyPreferencesState())
        assertTrue(enabledResult.isActionable)

        // When CONTACT_INFO is toggled OFF by user
        val disabledPrefs = PrivacyPreferencesState(
            enabledCategories = setOf(SensitivityCategory.IDENTITY_DOCUMENT, SensitivityCategory.FINANCIAL_BANKING)
        )
        val disabledResult = engine.analyze(ocr, disabledPrefs)
        assertFalse(disabledResult.isActionable)
        assertTrue(disabledResult.regions.isEmpty())
    }

    @Test
    fun testCustomUserKeywords() {
        val ocr = OcrResult(
            fullText = "Top secret project codename: ProjectApollo",
            elements = listOf(
                OcrTextElement("Top secret project codename: ProjectApollo", Rect(10, 10, 300, 30))
            )
        )
        val prefs = PrivacyPreferencesState(
            customKeywords = setOf("projectapollo")
        )
        val result = engine.analyze(ocr, prefs)
        assertTrue(result.isActionable)
        assertTrue(result.regions.any { it.label == "Custom Private Keyword" })
    }

    @Test
    fun testAmbiguousTabletWordDoesNotTriggerMedical() {
        val ocr = OcrResult(
            fullText = "Review of modern Android tablet with sleek aluminum body and 120Hz display",
            elements = listOf(
                OcrTextElement("Review of modern Android tablet with sleek aluminum body", Rect(10, 10, 400, 30))
            )
        )
        val result = engine.analyze(ocr, PrivacyPreferencesState())
        assertFalse("Single ambiguous word 'tablet' must not trigger medical alert", result.isActionable)
        assertFalse(result.detectedCategories.contains(SensitivityCategory.MEDICAL_HEALTH))
    }

    @Test
    fun testRawOrderNumberDoesNotTriggerPhoneOnBalanced() {
        val ocr = OcrResult(
            fullText = "Amazon Delivery Tracking Order ID: 9823412345 status shipped",
            elements = listOf(
                OcrTextElement("Order ID: 9823412345 status shipped", Rect(10, 10, 300, 30))
            )
        )
        val result = engine.analyze(ocr, PrivacyPreferencesState())
        assertFalse("Raw unformatted 10-digit order ID must not trigger phone alert on balanced mode", result.isActionable)
    }

    @Test
    fun testPhoneWithContextTriggersCorrectly() {
        val ocr = OcrResult(
            fullText = "Call our representative directly at 9823412345",
            elements = listOf(
                OcrTextElement("Call our representative directly at 9823412345", Rect(10, 10, 350, 30))
            )
        )
        val result = engine.analyze(ocr, PrivacyPreferencesState())
        assertTrue(result.isActionable)
        assertTrue(result.detectedCategories.contains(SensitivityCategory.CONTACT_INFO))
    }

    @Test
    fun testAadhaarInvalidPrefixesRejected() {
        // Starts with 0 - invalid for UIDAI Aadhaar
        assertFalse(engine.validateAadhaar("012345678901"))
        // Starts with 1 - invalid for UIDAI Aadhaar
        assertFalse(engine.validateAadhaar("112345678901"))
        // All identical digits
        assertFalse(engine.validateAadhaar("222222222222"))
    }

    @Test
    fun testAadhaarWithDocContextFallback() {
        // Aadhaar candidate with 1-digit OCR error that fails Verhoeff, but has Aadhaar header
        val ocr = OcrResult(
            fullText = "Government of India Unique Identification Authority of India Aadhaar 2345 6789 0129",
            elements = listOf(
                OcrTextElement("Government of India", Rect(10, 10, 200, 30)),
                OcrTextElement("Aadhaar 2345 6789 0129", Rect(10, 40, 250, 60))
            )
        )
        val result = engine.analyze(ocr, PrivacyPreferencesState())
        assertTrue("Aadhaar with header context must trigger even if single digit fails Verhoeff", result.isActionable)
        assertTrue(result.detectedCategories.contains(SensitivityCategory.IDENTITY_DOCUMENT))
    }

    @Test
    fun testCleanNeutralImageTextNotActionable() {
        val ocr = OcrResult(
            fullText = "Good morning everyone have a nice day ahead at office",
            elements = listOf(
                OcrTextElement("Good morning everyone have a nice day", Rect(10, 10, 300, 30)),
                OcrTextElement("ahead at office", Rect(10, 40, 150, 60))
            )
        )
        val result = engine.analyze(ocr, PrivacyPreferencesState())
        assertFalse("Neutral message should not trigger any sensitivity", result.isActionable)
        assertEquals(0, result.regions.size)
    }
}
