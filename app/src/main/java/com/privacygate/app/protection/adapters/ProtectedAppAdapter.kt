package com.privacygate.app.protection.adapters

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class PreviewObservation(
    val sessionKey: String,
    val mediaBounds: Rect,
    val windowId: Int,
    val packageName: String,
    val appLabel: String = packageName,
    val sendBounds: Rect? = null,
    val targetCrops: List<Rect> = emptyList()
)

interface ProtectedAppAdapter {
    val targetPackages: Set<String>
    val isUniversalFallback: Boolean
        get() = false

    fun matchesPackage(packageName: String): Boolean {
        return isUniversalFallback || targetPackages.contains(packageName)
    }

    fun inspect(root: AccessibilityNodeInfo, windowId: Int): PreviewObservation?
}
