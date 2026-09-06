package com.privacygate.app.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SensitivityCategory(val displayName: String, val description: String, val icon: String) {
    IDENTITY_DOCUMENT("Identity & ID Cards", "Aadhaar, PAN, Passports, Driving Licenses", "🪪"),
    FINANCIAL_BANKING("Banking & Cards", "Credit/Debit cards, Bank accounts, IFSC, UPI", "💳"),
    SECRET_CREDENTIAL("Passwords & OTPs", "2FA codes, PINs, verification SMS", "🔑"),
    MEDICAL_HEALTH("Medical & Health", "Prescriptions, diagnoses, lab reports", "🩺"),
    INVOICE_RECEIPT("Bills & Invoices", "Tax invoices, payment receipts, order details", "🧾"),
    CONTACT_INFO("Phone & Email", "Personal mobile numbers and email addresses", "📞"),
    FACE_PORTRAIT("Faces & People", "Detect faces of women, kids, and personal portraits", "👤"),
    NSFW_SENSITIVE("Private & Sensitive Media", "Detect sensitive personal or private photos", "🔞")
}

enum class SensitivityLevel(val displayName: String, val minRiskScore: Float) {
    LOW("Low (Strict Confidence Only)", 0.7f),
    BALANCED("Balanced (Recommended)", 0.45f),
    STRICT("Strict (All Potential Risks)", 0.25f)
}

data class PrivacyPreferencesState(
    val enabledCategories: Set<SensitivityCategory> = SensitivityCategory.entries.toSet(),
    val sensitivityLevel: SensitivityLevel = SensitivityLevel.BALANCED,
    val customKeywords: Set<String> = emptySet()
)

class PrivacyPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("privacy_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<PrivacyPreferencesState> = _state.asStateFlow()

    private fun loadState(): PrivacyPreferencesState {
        val catNames = prefs.getStringSet("enabled_categories", null)
        val enabledCategories = if (catNames == null) {
            SensitivityCategory.entries.toSet()
        } else {
            catNames.mapNotNull { runCatching { SensitivityCategory.valueOf(it) }.getOrNull() }.toSet()
        }

        val levelName = prefs.getString("sensitivity_level", SensitivityLevel.BALANCED.name) ?: SensitivityLevel.BALANCED.name
        val level = runCatching { SensitivityLevel.valueOf(levelName) }.getOrDefault(SensitivityLevel.BALANCED)

        val keywords = prefs.getStringSet("custom_keywords", emptySet()) ?: emptySet()

        return PrivacyPreferencesState(
            enabledCategories = enabledCategories,
            sensitivityLevel = level,
            customKeywords = keywords
        )
    }

    fun toggleCategory(category: SensitivityCategory) {
        val current = _state.value.enabledCategories
        val updated = if (current.contains(category)) current - category else current + category
        prefs.edit().putStringSet("enabled_categories", updated.map { it.name }.toSet()).apply()
        _state.value = _state.value.copy(enabledCategories = updated)
    }

    fun setSensitivityLevel(level: SensitivityLevel) {
        prefs.edit().putString("sensitivity_level", level.name).apply()
        _state.value = _state.value.copy(sensitivityLevel = level)
    }

    fun addCustomKeyword(keyword: String) {
        val trimmed = keyword.trim().lowercase()
        if (trimmed.isNotBlank()) {
            val updated = _state.value.customKeywords + trimmed
            prefs.edit().putStringSet("custom_keywords", updated).apply()
            _state.value = _state.value.copy(customKeywords = updated)
        }
    }

    fun removeCustomKeyword(keyword: String) {
        val updated = _state.value.customKeywords - keyword.trim().lowercase()
        prefs.edit().putStringSet("custom_keywords", updated).apply()
        _state.value = _state.value.copy(customKeywords = updated)
    }
}
