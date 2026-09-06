package com.privacygate.app.redaction

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartMaskPolicyTest {
    @Test
    fun aadhaarAndCardsPreserveOnlyLastFour() {
        assertEquals(MaskMode.KEEP_LAST_FOUR, SmartMaskPolicy.forFinding("Aadhaar Number").mode)
        assertEquals("XXXX XXXX", SmartMaskPolicy.forFinding("Aadhaar Number").replacement)
        assertEquals(MaskMode.KEEP_LAST_FOUR, SmartMaskPolicy.forFinding("Payment Card Number").mode)
    }

    @Test
    fun cvvAndContactFieldsAreFullyMasked() {
        assertEquals(MaskMode.FULL, SmartMaskPolicy.forFinding("Card CVV").mode)
        assertEquals(MaskMode.FULL, SmartMaskPolicy.forFinding("Phone Number").mode)
        assertEquals(MaskMode.FULL, SmartMaskPolicy.forFinding("Customer Address").mode)
    }
}
