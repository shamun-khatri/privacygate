package com.privacygate.app.protection

import com.privacygate.app.protection.adapters.PreviewSessionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionHardeningTest {
    @Test
    fun pendingPreviewReportsRemainingSettleTime() {
        val gate = PreviewGate(settleMs = 300)
        assertNull(gate.observe("preview-a", 100))
        assertEquals(300L, gate.millisUntilReady("preview-a", 100))
        assertEquals(150L, gate.millisUntilReady("preview-a", 250))
        assertEquals(0L, gate.millisUntilReady("preview-a", 400))
    }

    @Test
    fun evaluationScopeIsLimitedToWhatsApp() {
        assertTrue(ProtectionTargetPolicy.accepts("com.whatsapp"))
        assertTrue(ProtectionTargetPolicy.accepts("com.whatsapp.w4b"))
        assertFalse(ProtectionTargetPolicy.accepts("com.privacygate.app"))
        assertFalse(ProtectionTargetPolicy.accepts("com.instagram.android"))
    }

    @Test
    fun previewKeyIsStableAndTracksMaterialChanges() {
        val key = PreviewSessionKey.create("com.whatsapp.w4b", 7, 0, 120, 1080, 1800)
        assertEquals(key, PreviewSessionKey.create("com.whatsapp.w4b", 7, 0, 120, 1080, 1800))
        assertNotEquals(key, PreviewSessionKey.create("com.whatsapp.w4b", 8, 0, 120, 1080, 1800))
        assertNotEquals(key, PreviewSessionKey.create("com.whatsapp.w4b", 7, 0, 121, 1080, 1800))
    }

    @Test
    fun gallerySelectionCountDistinguishesSessionKeys() {
        val baseKey = PreviewSessionKey.create("com.whatsapp.w4b", 7, 0, 120, 1080, 1800)
        val singleSelectKey = "$baseKey:count=1"
        val multiSelectKey = "$baseKey:count=2"
        val cropsKey = "$singleSelectKey:crops=0_353_474_828"
        assertNotEquals(baseKey, singleSelectKey)
        assertNotEquals(singleSelectKey, multiSelectKey)
        assertNotEquals(singleSelectKey, cropsKey)
    }

    @Test
    fun testCroppedAadhaarDocumentDetection() {
        val engine = com.privacygate.app.ai.DynamicSemanticEngine()
        val ocr = com.privacygate.app.ai.ocr.OcrResult(
            fullText = "RNMENT OF INDIA\nIdentification Authority of India\nVikram Malhotra\naar No: 9999 4172 6384\nadhaar, Meri Pehchan",
            elements = listOf(
                com.privacygate.app.ai.ocr.OcrTextElement("RNMENT OF INDIA", android.graphics.Rect(10, 10, 200, 30)),
                com.privacygate.app.ai.ocr.OcrTextElement("Identification Authority of India", android.graphics.Rect(10, 35, 200, 55)),
                com.privacygate.app.ai.ocr.OcrTextElement("aar No: 9999 4172 6384", android.graphics.Rect(10, 60, 200, 80)),
                com.privacygate.app.ai.ocr.OcrTextElement("adhaar, Meri Pehchan", android.graphics.Rect(10, 85, 200, 105))
            )
        )
        val result = engine.analyze(ocr, com.privacygate.app.settings.PrivacyPreferencesState())
        assertTrue("Expected actionable result for Aadhaar document", result.isActionable)
        assertTrue("Expected IDENTITY_DOCUMENT category", result.detectedCategories.contains(com.privacygate.app.settings.SensitivityCategory.IDENTITY_DOCUMENT))
        assertEquals("Identity Document / ID Card", result.documentType)
    }
}
