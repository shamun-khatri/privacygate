package com.privacygate.app.protection

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.privacygate.app.ai.PrivacyInferenceEngine
import com.privacygate.app.protection.adapters.ProtectedAppAdapter
import com.privacygate.app.protection.adapters.SystemSharesheetAdapter
import com.privacygate.app.protection.adapters.UniversalSendAdapter
import com.privacygate.app.protection.adapters.WhatsAppAdapter
import com.privacygate.app.protection.capture.WindowScreenshotCapturer
import com.privacygate.app.protection.overlay.RiskOverlayController

class PrivacyGateAccessibilityService : AccessibilityService() {
    private var coordinator: ProtectionCoordinator? = null
    private val adapters: List<ProtectedAppAdapter> = listOf(
        WhatsAppAdapter(), SystemSharesheetAdapter(), UniversalSendAdapter()
    )
    private val rejectedPackages = setOf(
        "com.privacygate.app", "com.android.systemui", "com.android.settings",
        "com.google.android.inputmethod.latin"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.packageNames = null
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        serviceInfo = info
        if (coordinator == null) {
            val executor = ServiceSendActionExecutor(this, adapters)
            coordinator = ProtectionCoordinator(
                this, WindowScreenshotCapturer(this), PrivacyInferenceEngine(), RiskOverlayController(this), executor
            )
        }
        PrivacyGateState.updateServiceConnected(true)
        android.util.Log.i("PrivacyGate", "Sentinel connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val activeCoordinator = coordinator ?: return
        val packageName = event?.packageName?.toString() ?: return
        if (packageName in rejectedPackages) return

        val matchingAdapter = adapters.firstOrNull { it.matchesPackage(packageName) }
        if (matchingAdapter == null) {
            // Unrelated system/keyboard event — do not clear the active preview observation
            return
        }

        val root = rootInActiveWindow ?: event.source ?: windows.firstOrNull {
            it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
        }?.root
        if (root == null) {
            return
        }

        val observation = matchingAdapter.inspect(root, root.windowId)
        if (observation != null) {
            activeCoordinator.onPreviewObserved(observation)
        } else {
            activeCoordinator.onPreviewCleared()
        }
    }

    override fun onInterrupt() = coordinator?.onPreviewCleared() ?: Unit
    override fun onDestroy() {
        PrivacyGateState.updateServiceConnected(false)
        coordinator?.destroy()
        coordinator = null
        super.onDestroy()
    }
}
