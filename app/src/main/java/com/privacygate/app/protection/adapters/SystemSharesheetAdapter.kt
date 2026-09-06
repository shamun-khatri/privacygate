package com.privacygate.app.protection.adapters

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class SystemSharesheetAdapter : ProtectedAppAdapter {

    override val targetPackages: Set<String> = setOf(
        "com.android.intentresolver",
        "com.vivo.share",
        "com.vivo.easyshare",
        "android"
    )

    override fun inspect(root: AccessibilityNodeInfo, windowId: Int): PreviewObservation? {
        val packageName = root.packageName?.toString() ?: return null
        if (!targetPackages.contains(packageName)) return null

        val windowBounds = Rect()
        root.getBoundsInScreen(windowBounds)
        if (windowBounds.isEmpty) return null

        var largestMediaNodeBounds: Rect? = null
        var maxMediaArea = 0L

        val nodesToProcess = ArrayDeque<AccessibilityNodeInfo>()
        nodesToProcess.add(root)

        var processedCount = 0
        val maxNodes = 200

        while (nodesToProcess.isNotEmpty() && processedCount < maxNodes) {
            val node = nodesToProcess.removeFirst()
            processedCount++

            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val className = node.className?.toString() ?: ""

            // Sharesheet media preview candidate
            if (className.contains("ImageView") || viewId.contains("preview") ||
                viewId.contains("thumbnail") || viewId.contains("image")
            ) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val area = bounds.width().toLong() * bounds.height().toLong()

                // Significant thumbnail on screen
                if (bounds.width() > 100 && bounds.height() > 100 && area > maxMediaArea) {
                    maxMediaArea = area
                    largestMediaNodeBounds = bounds
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    nodesToProcess.add(child)
                }
            }
        }

        // Require a visible media preview in the sharesheet
        val mediaBounds = largestMediaNodeBounds ?: return null

        val sessionKey = "sharesheet:$packageName:$windowId:${mediaBounds.toShortString()}"

        return PreviewObservation(
            sessionKey = sessionKey,
            mediaBounds = mediaBounds,
            windowId = windowId,
            packageName = packageName,
            appLabel = "System Sharesheet"
        )
    }
}
