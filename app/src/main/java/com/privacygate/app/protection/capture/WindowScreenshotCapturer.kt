package com.privacygate.app.protection.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class WindowScreenshotCapturer(private val service: AccessibilityService) {
    private val executor = Executors.newSingleThreadExecutor()

    suspend fun captureWindowCrop(windowId: Int, cropBounds: Rect): Bitmap? {
        return suspendCancellableCoroutine { cont ->
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        try {
                            val hwBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                            hwBuffer.close()

                            val softBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hwBitmap?.recycle()

                            if (softBitmap == null) {
                                cont.resume(null)
                                return
                            }

                            val clampedCrop = clampRect(cropBounds, softBitmap.width, softBitmap.height)
                            if (clampedCrop.isEmpty || clampedCrop.width() < 10 || clampedCrop.height() < 10) {
                                cont.resume(softBitmap)
                                return
                            }

                            val cropped = Bitmap.createBitmap(
                                softBitmap,
                                clampedCrop.left,
                                clampedCrop.top,
                                clampedCrop.width(),
                                clampedCrop.height()
                            )
                            softBitmap.recycle()
                            cont.resume(cropped)
                        } catch (e: Exception) {
                            android.util.Log.w("PrivacyGate", "Capture crop error: ${e.message}")
                            cont.resume(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        android.util.Log.w("PrivacyGate", "Display capture failed with error code $errorCode")
                        cont.resume(null)
                    }
                }
            )
        }
    }

    suspend fun captureWindowCrops(windowId: Int, cropBoundsList: List<Rect>): List<Bitmap> {
        if (cropBoundsList.isEmpty()) return emptyList()
        return suspendCancellableCoroutine { cont ->
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        try {
                            val hwBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                            hwBuffer.close()

                            val softBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hwBitmap?.recycle()

                            if (softBitmap == null) {
                                cont.resume(emptyList())
                                return
                            }

                            val resultList = mutableListOf<Bitmap>()
                            for (cropBounds in cropBoundsList) {
                                val clampedCrop = clampRect(cropBounds, softBitmap.width, softBitmap.height)
                                if (clampedCrop.isEmpty || clampedCrop.width() < 10 || clampedCrop.height() < 10) {
                                    continue
                                }
                                val cropped = Bitmap.createBitmap(
                                    softBitmap,
                                    clampedCrop.left,
                                    clampedCrop.top,
                                    clampedCrop.width(),
                                    clampedCrop.height()
                                )
                                resultList.add(cropped)
                            }
                            softBitmap.recycle()
                            cont.resume(resultList)
                        } catch (e: Exception) {
                            android.util.Log.w("PrivacyGate", "Capture multiple crops error: ${e.message}")
                            cont.resume(emptyList())
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        android.util.Log.w("PrivacyGate", "Display capture failed with error code $errorCode")
                        cont.resume(emptyList())
                    }
                }
            )
        }
    }

    private fun fallbackDisplayCapture(cropBounds: Rect, onComplete: (Bitmap?) -> Unit) {
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            executor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                    try {
                        val hwBuffer = screenshotResult.hardwareBuffer
                        val colorSpace = screenshotResult.colorSpace
                        val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                        hwBuffer.close()

                        val softBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hwBitmap?.recycle()

                        if (softBitmap == null) {
                            onComplete(null)
                            return
                        }

                        val clampedCrop = clampRect(cropBounds, softBitmap.width, softBitmap.height)
                        if (clampedCrop.isEmpty || clampedCrop.width() < 10 || clampedCrop.height() < 10) {
                            onComplete(softBitmap)
                            return
                        }

                        val cropped = Bitmap.createBitmap(
                            softBitmap,
                            clampedCrop.left,
                            clampedCrop.top,
                            clampedCrop.width(),
                            clampedCrop.height()
                        )
                        softBitmap.recycle()
                        onComplete(cropped)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onComplete(null)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    onComplete(null)
                }
            }
        )
    }

    private fun clampRect(rect: Rect, maxWidth: Int, maxHeight: Int): Rect {
        val left = rect.left.coerceIn(0, maxWidth)
        val top = rect.top.coerceIn(0, maxHeight)
        val right = rect.right.coerceIn(left, maxWidth)
        val bottom = rect.bottom.coerceIn(top, maxHeight)
        return Rect(left, top, right, bottom)
    }
}
