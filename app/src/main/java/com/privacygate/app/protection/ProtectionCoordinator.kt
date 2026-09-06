package com.privacygate.app.protection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.privacygate.app.ai.PrivacyInferenceEngine
import com.privacygate.app.model.RiskLevel
import com.privacygate.app.protection.adapters.PreviewObservation
import com.privacygate.app.protection.capture.WindowScreenshotCapturer
import com.privacygate.app.protection.overlay.RiskOverlayController
import com.privacygate.app.redaction.RedactionEngine
import com.privacygate.app.settings.PrivacyPreferences
import kotlinx.coroutines.*

class ProtectionCoordinator(
    private val context: Context,
    private val capturer: WindowScreenshotCapturer,
    private val inferenceEngine: PrivacyInferenceEngine,
    private val overlayController: RiskOverlayController,
    private val sendActionExecutor: SendActionExecutor? = null,
    private val privacyPreferences: PrivacyPreferences = PrivacyPreferences(context),
    private val previewGate: PreviewGate = PreviewGate(settleMs = 120),
    private val sendGate: SendInterceptionGate = SendInterceptionGate()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settleJob: Job? = null
    private var pendingKey: String? = null
    private var scanJob: Job? = null
    private var redactionBitmap: Bitmap? = null
    private var currentObservation: PreviewObservation? = null

    fun onPreviewObserved(observation: PreviewObservation) {
        val previousObservation = currentObservation
        currentObservation = observation

        // If transitioning to a new preview session, immediately disarm any old guard
        if (previousObservation != null && previousObservation.sessionKey != observation.sessionKey) {
            overlayController.disarmSendGuard()
            overlayController.hide()
            sendGate.clear()
        } else if (overlayController.isGuardArmed && observation.sendBounds != null) {
            overlayController.updateGuardPosition(observation.sendBounds)
        }
        val now = SystemClock.uptimeMillis()
        previewGate.observe(observation.sessionKey, now)?.let {
            cancelSettle(); startScan(observation, it); return
        }
        val waitMs = previewGate.millisUntilReady(observation.sessionKey, now) ?: return
        if (pendingKey == observation.sessionKey && settleJob?.isActive == true) return
        cancelSettle()
        pendingKey = observation.sessionKey
        settleJob = scope.launch {
            delay(waitMs)
            val token = previewGate.observe(observation.sessionKey, SystemClock.uptimeMillis())
            pendingKey = null
            settleJob = null
            if (token != null) startScan(currentObservation ?: observation, token)
        }
    }

    private fun startScan(observation: PreviewObservation, token: Long) {
        PrivacyGateState.recordPreview(observation.packageName)
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.Default) {
            try {
                val targets = if (observation.targetCrops.isNotEmpty()) observation.targetCrops else listOf(observation.mediaBounds)
                android.util.Log.i("PrivacyGate", "ProtectionCoordinator: starting scan on ${targets.size} targets: $targets")
                val bitmaps = capturer.captureWindowCrops(observation.windowId, targets)
                android.util.Log.i("PrivacyGate", "ProtectionCoordinator: captured ${bitmaps.size} bitmaps for ${targets.size} targets")
                if (bitmaps.isEmpty() || !previewGate.isCurrent(token)) {
                    bitmaps.forEach { it.recycle() }
                    return@launch
                }

                var combinedRisk = RiskLevel.SAFE
                val combinedFindings = mutableListOf<com.privacygate.app.model.SensitiveItem>()
                val combinedRegions = mutableListOf<com.privacygate.app.ai.DetectedRegion>()
                var combinedDocType: String? = null
                var maxLatencyMs = 0L
                var actionableBitmap: Bitmap? = null

                for (bitmap in bitmaps) {
                    if (!previewGate.isCurrent(token)) {
                        bitmap.recycle()
                        continue
                    }
                    val result = inferenceEngine.scan(bitmap, privacyPreferences.state.value)
                    maxLatencyMs = maxOf(maxLatencyMs, result.latencyMs)
                    if (result.riskLevel == RiskLevel.ACTIONABLE) {
                        combinedRisk = RiskLevel.ACTIONABLE
                        combinedFindings.addAll(result.findings)
                        combinedRegions.addAll(result.regions)
                        if (combinedDocType == null) combinedDocType = result.documentType
                        if (actionableBitmap == null) {
                            actionableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        }
                    }
                    bitmap.recycle()
                }

                val finalResult = com.privacygate.app.model.PrivacyScanResult(
                    riskLevel = combinedRisk,
                    documentType = combinedDocType,
                    riskScore = if (combinedRisk == RiskLevel.ACTIONABLE) 1.0f else 0.0f,
                    findings = combinedFindings.distinctBy { it.label },
                    regions = combinedRegions,
                    fullText = "",
                    latencyMs = maxLatencyMs
                )

                PrivacyGateState.recordScanResult(finalResult)
                android.util.Log.i("PrivacyGate", "Scan complete: riskLevel=${finalResult.riskLevel}, docType=${finalResult.documentType}, findings=${finalResult.findings.map { it.label }}, latency=${finalResult.latencyMs}ms")

                if (finalResult.riskLevel == RiskLevel.ACTIONABLE) {
                    val targetObs = currentObservation ?: observation
                    redactionBitmap?.recycle()
                    redactionBitmap = actionableBitmap
                    val source = redactionBitmap
                    val showWarning = {
                        overlayController.show(
                            findings = finalResult.findings,
                            appLabel = targetObs.appLabel,
                            documentType = finalResult.documentType,
                            scanLatencyMs = finalResult.latencyMs,
                            onRedact = {
                                overlayController.disarmSendGuard()
                                overlayController.hide()
                                sendGate.clear()
                                if (source != null && !source.isRecycled) {
                                    scope.launch(Dispatchers.Default) {
                                        RedactionEngine.saveAndShareSmartMasked(context, source, finalResult.regions)
                                    }
                                }
                            },
                            onSendAnyway = {
                                overlayController.disarmSendGuard()
                                overlayController.hide()
                                sendGate.acknowledge(targetObs.sessionKey)
                                if (sendActionExecutor != null) {
                                    val execResult = sendActionExecutor.executeSend(targetObs)
                                    sendGate.clear()
                                    when (execResult) {
                                        is SendExecutionResult.Success -> {
                                            android.util.Log.i("PrivacyGate", "One-shot send executed successfully for ${targetObs.sessionKey}")
                                        }
                                        is SendExecutionResult.StaleSession -> {
                                            android.util.Log.w("PrivacyGate", "Programmatic send aborted: stale session (${execResult.reason})")
                                            overlayController.showFeedback("Preview changed — tap Send again")
                                        }
                                        is SendExecutionResult.TargetNotFound,
                                        is SendExecutionResult.ExecutionFailed -> {
                                            android.util.Log.w("PrivacyGate", "Programmatic send failed: $execResult")
                                            overlayController.showFeedback("Preview changed — tap Send again")
                                        }
                                    }
                                } else {
                                    sendGate.clear()
                                }
                            },
                            onDismiss = {
                                // Session already acknowledged by the first interception.
                                // Guard stays down so the next tap sends normally.
                                overlayController.disarmSendGuard()
                            }
                        )
                    }
                    sendGate.arm(targetObs.sessionKey)
                    val sendBounds = targetObs.sendBounds ?: observation.sendBounds
                    if (sendBounds != null) {
                        overlayController.armSendGuard(sendBounds) {
                            if (sendGate.intercept(targetObs.sessionKey)) {
                                // Warn once, then step aside: dropping the guard here means the
                                // user's next tap lands on WhatsApp's real Send button with no
                                // interception at all.
                                overlayController.disarmSendGuard()
                                showWarning()
                            } else {
                                overlayController.disarmSendGuard()
                            }
                        }
                    } else {
                        // In surfaces without a dedicated send button, show warning directly
                        showWarning()
                    }
                } else {
                    actionableBitmap?.recycle()
                    sendGate.clear()
                    overlayController.hide()
                    overlayController.disarmSendGuard()
                }
            } catch (t: Throwable) {
                android.util.Log.w("PrivacyGate", "startScan encountered error or cancellation: ${t.message}")
            }
        }
    }

    private fun cancelSettle() { settleJob?.cancel(); settleJob = null; pendingKey = null }
    fun onPreviewCleared() {
        cancelSettle(); scanJob?.cancel(); scanJob = null
        currentObservation = null
        redactionBitmap?.recycle(); redactionBitmap = null
        previewGate.observe(null, SystemClock.uptimeMillis()); sendGate.clear()
        overlayController.hide()
        overlayController.disarmSendGuard()
    }
    fun destroy() { onPreviewCleared(); scope.cancel(); inferenceEngine.close() }
}
