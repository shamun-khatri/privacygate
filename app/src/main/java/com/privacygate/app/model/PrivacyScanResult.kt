package com.privacygate.app.model

import com.privacygate.app.ai.DetectedRegion
import com.privacygate.app.settings.SensitivityCategory

enum class RiskLevel {
    SAFE,
    ACTIONABLE
}

data class SensitiveItem(
    val category: SensitivityCategory,
    val label: String,
    val snippet: String = ""
)

data class PrivacyScanResult(
    val riskLevel: RiskLevel,
    val documentType: String?,
    val riskScore: Float,
    val findings: List<SensitiveItem>,
    val regions: List<DetectedRegion>,
    val fullText: String,
    val latencyMs: Long,
    val backend: String = "Bundled ML Kit + Dynamic Semantic Engine"
)
