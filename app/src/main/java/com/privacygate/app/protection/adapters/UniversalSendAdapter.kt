package com.privacygate.app.protection.adapters

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class UniversalSendAdapter : ProtectedAppAdapter {

    override val targetPackages: Set<String> = emptySet()
    override val isUniversalFallback: Boolean = true

    private val excludedPackages = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.inputmethod.latin",
        "com.google.android.apps.nexuslauncher",
        "com.bbk.launcher2",
        "com.vivo.upslide",
        "com.privacygate.app",
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    private val sendActionKeywords = setOf(
        "send", "post", "share", "tweet", "upload", "publish"
    )

    override fun inspect(root: AccessibilityNodeInfo, windowId: Int): PreviewObservation? {
        val packageName = root.packageName?.toString() ?: return null
        if (packageName in excludedPackages) return null

        val windowBounds = Rect()
        root.getBoundsInScreen(windowBounds)
        if (windowBounds.isEmpty || windowBounds.width() < 200 || windowBounds.height() < 200) return null

        val screenArea = windowBounds.width().toLong() * windowBounds.height().toLong()
        val minMediaArea = screenArea / 5 // At least 20% of screen

        var hasSendAction = false
        var sendActionBounds: Rect? = null
        var hasComposerInput = false
        var feedItemCount = 0

        var largestMediaNodeBounds: Rect? = null
        var maxMediaArea = 0L

        val nodesToProcess = ArrayDeque<AccessibilityNodeInfo>()
        nodesToProcess.add(root)

        var processedCount = 0
        val maxNodes = 250

        while (nodesToProcess.isNotEmpty() && processedCount < maxNodes) {
            val node = nodesToProcess.removeFirst()
            processedCount++

            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val className = node.className?.toString() ?: ""

            // Feed rejection signals
            if (viewId.contains("comment") || viewId.contains("like_button") ||
                desc.contains("comment") || desc.contains("like") || desc.contains("retweet")
            ) {
                feedItemCount++
            }

            // Affirmative send action signals
            val isClickableAction = node.isClickable || className.contains("Button") || className.contains("ImageView")
            if (isClickableAction) {
                if (sendActionKeywords.any { text == it || desc == it || desc.startsWith("$it ") } ||
                    viewId.contains("send") || viewId.contains("post_button") || viewId.contains("share_button")
                ) {
                    hasSendAction = true
                    val bounds = Rect().also(node::getBoundsInScreen)
                    if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0) {
                        sendActionBounds = bounds
                    }
                }
            }

            // Input / composer context
            if (className.contains("EditText")) {
                hasComposerInput = true
            }

            // Media preview candidate
            if (className.contains("ImageView") || className.contains("TextureView") ||
                className.contains("PhotoView") || viewId.contains("preview") || viewId.contains("media")
            ) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val area = bounds.width().toLong() * bounds.height().toLong()
                if (area >= minMediaArea && area > maxMediaArea) {
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

        // Rejection: if it looks like a scrolling feed with multiple comments/likes, ignore
        if (feedItemCount >= 3) {
            return null
        }

        // Must have an actionable Send/Post/Share trigger AND a large media canvas
        if (!hasSendAction || largestMediaNodeBounds == null) {
            return null
        }

        val mediaBounds = largestMediaNodeBounds
        val sessionKey = PreviewSessionKey.create(
            packageName, windowId, mediaBounds.left, mediaBounds.top, mediaBounds.right, mediaBounds.bottom
        )

        // Derive user-friendly app label from package name (e.g. com.instagram.android -> Instagram)
        val appLabel = deriveAppLabel(packageName)

        return PreviewObservation(
            sessionKey = sessionKey,
            mediaBounds = mediaBounds,
            windowId = windowId,
            packageName = packageName,
            appLabel = appLabel,
            sendBounds = sendActionBounds
        )
    }

    private fun deriveAppLabel(packageName: String): String {
        return when {
            packageName.contains("instagram") -> "Instagram"
            packageName.contains("telegram") -> "Telegram"
            packageName.contains("signal") -> "Signal"
            packageName.contains("discord") -> "Discord"
            packageName.contains("slack") -> "Slack"
            packageName.contains("gm") || packageName.contains("email") -> "Email"
            packageName.contains("twitter") || packageName.contains("x.android") -> "X / Twitter"
            packageName.contains("facebook") || packageName.contains("katana") -> "Facebook"
            packageName.contains("linkedin") -> "LinkedIn"
            packageName.contains("snapchat") -> "Snapchat"
            else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}
