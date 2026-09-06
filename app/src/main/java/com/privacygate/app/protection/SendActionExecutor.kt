package com.privacygate.app.protection

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.privacygate.app.protection.adapters.PreviewObservation
import com.privacygate.app.protection.adapters.ProtectedAppAdapter

sealed class SendExecutionResult {
    object Success : SendExecutionResult()
    data class StaleSession(val reason: String) : SendExecutionResult()
    data class TargetNotFound(val reason: String) : SendExecutionResult()
    data class ExecutionFailed(val reason: String) : SendExecutionResult()
}

interface SendActionExecutor {
    fun executeSend(observation: PreviewObservation): SendExecutionResult
}

class ServiceSendActionExecutor(
    private val service: AccessibilityService,
    private val adapters: List<ProtectedAppAdapter>
) : SendActionExecutor {

    override fun executeSend(observation: PreviewObservation): SendExecutionResult {
        val root = service.rootInActiveWindow
        val targetBounds = observation.sendBounds

        if (root != null) {
            val currentPackage = root.packageName?.toString().orEmpty()
            if (currentPackage.isNotEmpty() && currentPackage != observation.packageName && !currentPackage.contains("whatsapp")) {
                return SendExecutionResult.StaleSession(
                    "Active package '$currentPackage' does not match target '${observation.packageName}'"
                )
            }

            // 1. Direct fast-path by exact view ID (:id/send or :id/send_media_btn)
            val directNodes = root.findAccessibilityNodeInfosByViewId("${observation.packageName}:id/send")
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("${observation.packageName}:id/send_media_btn") }
            val directSend = directNodes.firstOrNull { (it.isClickable || it.actionList.any { a -> a.id == AccessibilityNodeInfo.ACTION_CLICK }) && it.isEnabled }
            if (directSend != null) {
                val clicked = directSend.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    android.util.Log.i("PrivacyGate", "Direct ACTION_CLICK executed on Send node: ${directSend.viewIdResourceName}")
                    return SendExecutionResult.Success
                }
            }

            val sendNode = findClickableSendNode(root, targetBounds)
            if (sendNode != null) {
                val clicked = sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    android.util.Log.i("PrivacyGate", "ACTION_CLICK executed on Send node: ${sendNode.viewIdResourceName ?: sendNode.contentDescription}")
                    return SendExecutionResult.Success
                }
            }
        }

        return fallbackGestureSend(observation, "AccessibilityNodeInfo.ACTION_CLICK failed or node not found")
    }

    private fun fallbackGestureSend(observation: PreviewObservation, fallbackReason: String): SendExecutionResult {
        val bounds = observation.sendBounds
            ?: return SendExecutionResult.TargetNotFound(fallbackReason)

        val clickX = bounds.centerX().toFloat()
        val clickY = bounds.centerY().toFloat()

        val path = Path().apply {
            moveTo(clickX, clickY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = service.dispatchGesture(gesture, null, null)
        return if (dispatched) {
            android.util.Log.i("PrivacyGate", "Fallback click gesture dispatched successfully at ($clickX, $clickY)")
            SendExecutionResult.Success
        } else {
            SendExecutionResult.ExecutionFailed("service.dispatchGesture returned false: $fallbackReason")
        }
    }

    private fun findClickableSendNode(root: AccessibilityNodeInfo, targetBounds: Rect?): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        var bestCandidate: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty() && visited++ < 300) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()

            val isClickable = node.isClickable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            val isSend = (id.contains("send") || desc == "send" || desc.startsWith("send ") || id.contains("fab")) &&
                !id.contains("payment") && !id.contains("pickfiletype")
            if (isSend) {
                if (isClickable && node.isEnabled) {
                    if (targetBounds != null) {
                        val bounds = Rect().also(node::getBoundsInScreen)
                        if (Rect.intersects(bounds, targetBounds) || bounds == targetBounds) {
                            return node
                        }
                    }
                    if (bestCandidate == null) bestCandidate = node
                } else {
                    val parent = node.parent
                    val isParentClickable = parent != null && (parent.isClickable || parent.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
                    if (isParentClickable && parent!!.isEnabled) {
                        if (targetBounds != null) {
                            val bounds = Rect().also(parent::getBoundsInScreen)
                            if (Rect.intersects(bounds, targetBounds) || bounds == targetBounds) {
                                return parent
                            }
                        }
                        if (bestCandidate == null) bestCandidate = parent
                    }
                }
            }

            if (targetBounds != null && isClickable && node.isEnabled) {
                val bounds = Rect().also(node::getBoundsInScreen)
                if (bounds == targetBounds || (Rect.intersects(bounds, targetBounds) && bounds.width() > 0 && bounds.height() > 0)) {
                    if (id.contains("send") || desc.contains("send") || bestCandidate == null) {
                        bestCandidate = node
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return bestCandidate
    }
}
