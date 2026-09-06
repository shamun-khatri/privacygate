package com.privacygate.app.protection.adapters

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class WhatsAppAdapter : ProtectedAppAdapter {
    override val targetPackages = setOf("com.whatsapp", "com.whatsapp.w4b")

    override fun inspect(root: AccessibilityNodeInfo, windowId: Int): PreviewObservation? {
        val packageName = root.packageName?.toString() ?: return null
        if (packageName !in targetPackages) return null
        val window = Rect().also(root::getBoundsInScreen)
        if (window.isEmpty) return null

        var isMediaComposer = false
        var isCameraViewfinder = false
        var isChatConversation = false
        var isContactPicker = false
        var isGalleryPicker = false
        var hasSelectedGalleryMedia = false
        var selectedMediaCount = 0
        var galleryGridBounds: Rect? = null
        var sendBounds: Rect? = null
        var media: Rect? = null
        var mediaArea = 0L
        var topChromeBottom = 0
        var bottomChromeTop = window.bottom
        val selectedGridItemBounds = mutableListOf<Rect>()
        val selectedTrayThumbBounds = mutableListOf<Rect>()

        // Direct fast-path lookup for WhatsApp Send FAB (:id/send in composer or :id/send_media_btn in gallery)
        val directSendNodes = root.findAccessibilityNodeInfosByViewId("${packageName}:id/send")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("${packageName}:id/send_media_btn") }
        val directSend = directSendNodes.firstOrNull { node ->
            val r = Rect().also(node::getBoundsInScreen)
            !r.isEmpty && r.width() > 0 && r.height() > 0 && r.left in 0..window.right
        }
        if (directSend != null) {
            val candidate = Rect().also(directSend::getBoundsInScreen)
            if (!candidate.isEmpty && candidate.width() > 0 && candidate.height() > 0 && candidate.left in 0..window.right) {
                sendBounds = candidate
                bottomChromeTop = minOf(bottomChromeTop, candidate.top)
            }
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 500) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val kind = node.className?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()

            // 1. Camera viewfinder signals (must reject immediately)
            if (id.contains("camera_coordinator") || id.contains("camera_actions") ||
                id.contains("camera_layout") || id.contains("shutter") ||
                id.contains("camera_mode_tab") || id.contains("switch_camera") ||
                id.contains("gallery_strip") || id.contains("recent_media") ||
                id.contains("close_camera") || id.contains("camera_view")
            ) {
                isCameraViewfinder = true
            }

            if (id.contains("contactpicker") || id.contains("contact_picker") || id.contains("contact_selector")) {
                isContactPicker = true
            }

            if (id.contains("conversation_layout") || id.contains("conversation_root") ||
                id.contains("voice_note") || id == "android:id/list" || id.contains("message_list") ||
                id.contains("conversation_row")
            ) {
                isChatConversation = true
            }

            if (id.contains("media_composer") || id.contains("preview_decoration") ||
                id.contains("media_tools") || id.contains("filter_swipe") ||
                id.contains("photo_editor") || (id.contains("caption_layout") && !id.contains("gallery"))
            ) {
                isMediaComposer = true
            }

            // Gallery picker detection
            if (id.contains("gallery_picker") || id.contains("media_picker") ||
                id.contains("gallery_container") || id.contains("gallery_view_pager")
            ) {
                isGalleryPicker = true
            }

            // Check if node represents a selected item in the gallery grid
            val isSelectedNode = node.isSelected
            val isNumericBadge = text.matches(Regex("^\\d{1,2}$")) && !id.contains("send_media_counter") && !id.contains("send")
            if ((isSelectedNode && (id.contains("media_item") || id.contains("thumb") || kind.contains("ImageView"))) ||
                (isNumericBadge && (id.contains("badge") || id.contains("counter") || id.contains("select") || id.contains("media")))) {
                val r = Rect().also(node::getBoundsInScreen)
                if (r.width() > 50 && r.height() > 50 && r.top >= (window.height() * 0.10f).toInt() && r.bottom <= bottomChromeTop) {
                    if (selectedGridItemBounds.none { it.contains(r) || r.contains(it) }) {
                        selectedGridItemBounds.add(r)
                    }
                }
                hasSelectedGalleryMedia = true
            }

            if (id.contains("gallery_selected_media") || id.contains("selected_media_item") ||
                id.contains("send_media_btn") || id.contains("selection_container")
            ) {
                isGalleryPicker = true
                hasSelectedGalleryMedia = true
            }

            if (id.contains("send_media_counter")) {
                val count = text.toIntOrNull() ?: 1
                selectedMediaCount = maxOf(selectedMediaCount, count)
                hasSelectedGalleryMedia = true
            }

            if (id.contains("selected_media_item_thumbnail")) {
                val r = Rect().also(node::getBoundsInScreen)
                if (r.width() > 20 && r.height() > 20) {
                    selectedTrayThumbBounds.add(r)
                }
                selectedMediaCount++
                hasSelectedGalleryMedia = true
            }

            if (desc.contains("send") && desc.contains("media")) {
                Regex("\\d+").find(desc)?.value?.toIntOrNull()?.let {
                    selectedMediaCount = maxOf(selectedMediaCount, it)
                }
                hasSelectedGalleryMedia = true
            }

            if (id.endsWith(":id/grid") || (id.contains("grid") && isGalleryPicker)) {
                val r = Rect().also(node::getBoundsInScreen)
                if (r.height() > window.height() / 4) {
                    galleryGridBounds = r
                }
            }

            // Track top and bottom UI chrome to isolate pure image from WhatsApp UI
            if (id.contains("title_bar") || id.contains("media_tools") || id.contains("header")) {
                val r = Rect().also(node::getBoundsInScreen)
                if (r.bottom > topChromeBottom && r.bottom < window.height() / 2) {
                    topChromeBottom = r.bottom
                }
            }

            if (id.contains("caption_layout") || id.contains("caption_input") ||
                id.contains("recipients_container") || id.contains("view_footer") ||
                id.contains("media_recipients")
            ) {
                val r = Rect().also(node::getBoundsInScreen)
                if (r.top in (window.height() / 3) until bottomChromeTop) {
                    bottomChromeTop = r.top
                }
            }

            if (sendBounds == null) {
                val isSendFab = (id.endsWith(":id/send") || id.endsWith(":id/send_media_btn") || desc == "send" || desc.startsWith("send ")) &&
                    !id.contains("payment") && !id.contains("pickfiletype") &&
                    (node.isClickable || kind.contains("Button") || kind.contains("ImageView") || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
                if (isSendFab) {
                    val candidate = Rect().also(node::getBoundsInScreen)
                    if (!candidate.isEmpty && candidate.width() > 0 && candidate.height() > 0 && candidate.left in 0..window.right) {
                        sendBounds = candidate
                        bottomChromeTop = minOf(bottomChromeTop, candidate.top)
                    }
                }
            }

            if (id.contains("crop") || id.contains("doodle") || id.contains("filter_swipe") || id.contains("filter_bottom_sheet")) {
                isMediaComposer = true
            }

            if (kind.contains("ImageView") || kind.contains("PhotoView") || kind.contains("TextureView") || id.contains("preview") || id.contains("photo")) {
                val bounds = Rect().also(node::getBoundsInScreen)
                val area = bounds.width().toLong() * bounds.height()
                if (area > window.width().toLong() * window.height() / 6 && area > mediaArea) {
                    media = bounds
                    mediaArea = area
                }
            }

            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
        }

        // When gallery picker is actively showing with selected media (send button visible),
        // it overlays the chat conversation in the foreground.
        val isActivelyInGalleryWithSelection = isGalleryPicker && (hasSelectedGalleryMedia || sendBounds != null)
        val isEligibleSurface = isMediaComposer || isActivelyInGalleryWithSelection

        // Strictly reject camera viewfinder (unless gallery picker is open over it), contact picker, or plain chat message list
        if ((isCameraViewfinder && !isActivelyInGalleryWithSelection) || isContactPicker || (!isActivelyInGalleryWithSelection && isChatConversation) || !isEligibleSurface) {
            return null
        }

        // Require genuine send button — never create a fake send button over arbitrary coordinates
        val finalSendBounds = sendBounds ?: return null

        val bounds: Rect
        val sessionKey: String
        val targetCrops: List<Rect>

        if (isActivelyInGalleryWithSelection) {
            // Priority 1: Use exact bounds of selected items in the grid (isolating them completely from unselected neighbours)
            // Priority 2: Use bottom tray thumbnails if grid items scrolled off
            // Priority 3: Bounding box fallback
            targetCrops = when {
                selectedGridItemBounds.isNotEmpty() -> selectedGridItemBounds
                selectedTrayThumbBounds.isNotEmpty() -> selectedTrayThumbBounds
                else -> emptyList()
            }
            android.util.Log.i("PrivacyGate", "WhatsAppAdapter: selectedGrid=$selectedGridItemBounds, tray=$selectedTrayThumbBounds, count=$selectedMediaCount")

            val grid = galleryGridBounds ?: Rect(window.left, (window.height() * 0.40f).toInt(), window.right, finalSendBounds.top)
            val effectiveTop = maxOf(grid.top, (window.height() * 0.20f).toInt())
            val effectiveBottom = minOf(grid.bottom, finalSendBounds.top)

            bounds = if (targetCrops.isNotEmpty()) {
                targetCrops.first()
            } else {
                if (effectiveBottom <= effectiveTop + 100) return null
                Rect(grid.left, effectiveTop, grid.right, effectiveBottom)
            }

            val countSuffix = if (selectedMediaCount > 0) ":count=$selectedMediaCount" else ""
            val cropsSuffix = if (targetCrops.isNotEmpty()) {
                ":crops=" + targetCrops.joinToString(",") { "${it.left}_${it.top}_${it.right}_${it.bottom}" }
            } else ""
            sessionKey = PreviewSessionKey.create(packageName, windowId, bounds.left, bounds.top, bounds.right, bounds.bottom) + countSuffix + cropsSuffix
        } else {
            targetCrops = emptyList()
            // In media composer: crop bounds strictly isolates the media photo canvas from WhatsApp's UI chrome:
            // Excludes top title toolbar and excludes bottom caption / recipient phone number chips
            val rawMedia = media ?: Rect(window.left, (window.height() * 0.12f).toInt(), window.right, (window.height() * 0.85f).toInt())
            val effectiveTop = if (topChromeBottom > 0) maxOf(rawMedia.top, topChromeBottom) else maxOf(rawMedia.top, (window.height() * 0.10f).toInt())
            val effectiveBottom = if (bottomChromeTop < window.bottom) minOf(rawMedia.bottom, bottomChromeTop) else minOf(rawMedia.bottom, finalSendBounds.top)

            if (effectiveBottom <= effectiveTop + 100) return null
            bounds = Rect(rawMedia.left, effectiveTop, rawMedia.right, effectiveBottom)
            sessionKey = PreviewSessionKey.create(packageName, windowId, bounds.left, bounds.top, bounds.right, bounds.bottom)
        }

        return PreviewObservation(
            sessionKey,
            bounds, windowId, packageName,
            if (packageName == "com.whatsapp.w4b") "WhatsApp Business" else "WhatsApp",
            finalSendBounds,
            targetCrops
        )
    }
}
