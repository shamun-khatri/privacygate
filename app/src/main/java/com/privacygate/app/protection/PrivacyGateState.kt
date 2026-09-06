package com.privacygate.app.protection

import com.privacygate.app.model.PrivacyScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiagnosticsState(
    val isServiceConnected: Boolean = false,
    val lastPackageObserved: String? = null,
    val totalScans: Int = 0,
    val actionableWarnings: Int = 0,
    val safeScans: Int = 0,
    val lastScanResult: PrivacyScanResult? = null
)

object PrivacyGateState {
    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    fun updateServiceConnected(connected: Boolean) {
        _state.value = _state.value.copy(isServiceConnected = connected)
    }

    fun recordPreview(packageName: String) {
        _state.value = _state.value.copy(lastPackageObserved = packageName)
    }

    fun recordScanResult(result: PrivacyScanResult) {
        val current = _state.value
        val isActionable = result.riskLevel == com.privacygate.app.model.RiskLevel.ACTIONABLE
        _state.value = current.copy(
            totalScans = current.totalScans + 1,
            actionableWarnings = if (isActionable) current.actionableWarnings + 1 else current.actionableWarnings,
            safeScans = if (!isActionable) current.safeScans + 1 else current.safeScans,
            lastScanResult = result
        )
    }
}
