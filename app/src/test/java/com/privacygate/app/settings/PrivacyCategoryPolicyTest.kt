package com.privacygate.app.settings

import com.privacygate.app.ai.gemma.GemmaEnrichment
import com.privacygate.app.ai.gemma.GemmaPlatePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyCategoryPolicyTest {
    private val platePhoto = GemmaEnrichment(registrationPlateVisible = true)

    @Test
    fun vehiclePlateRuleDefaultsOnAndCanBeDisabled() {
        assertTrue(SensitivityCategory.VEHICLE_PLATE in PrivacyPreferencesState().enabledCategories)
        assertTrue(GemmaPlatePolicy.shouldWarn(platePhoto, PrivacyPreferencesState()))

        val disabled = PrivacyPreferencesState(
            enabledCategories = SensitivityCategory.entries.toSet() - SensitivityCategory.VEHICLE_PLATE
        )
        assertFalse(GemmaPlatePolicy.shouldWarn(platePhoto, disabled))
    }
}
