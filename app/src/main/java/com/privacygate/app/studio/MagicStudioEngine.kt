package com.privacygate.app.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class MagicStudioEngine {

    /**
     * Erases the masked region of the bitmap using a localized border diffusion inpainting algorithm.
     * @param source The original bitmap (immutable)
     * @param mask The binary mask bitmap where Color.WHITE indicates areas to erase
     * @return A newly created in-painted bitmap with the masked regions removed
     */
    suspend fun eraseObject(source: Bitmap, mask: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        val output = source.copy(Bitmap.Config.ARGB_8888, true)

        val srcPixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val outPixels = srcPixels.clone()

        // Find bounding box of mask to minimize work
        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0

        val isMasked = BooleanArray(width * height)
        var totalMasked = 0

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x
                val m = maskPixels[idx]
                val alpha = (m shr 24) and 0xFF
                val red = (m shr 16) and 0xFF
                if (alpha > 30 && red > 30) {
                    isMasked[idx] = true
                    totalMasked++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (totalMasked == 0 || minX > maxX || minY > maxY) {
            // Nothing masked
            return@withContext output
        }

        // Expand bounding box slightly for border sampling
        val pad = 16
        minX = max(0, minX - pad)
        maxX = min(width - 1, maxX + pad)
        minY = max(0, minY - pad)
        maxY = min(height - 1, maxY + pad)

        // Filled state: all unmasked pixels are initially valid/filled
        val filled = BooleanArray(width * height) { idx -> !isMasked[idx] }
        var remaining = totalMasked

        // Multi-pass boundary peel inpainting:
        // In each pass, pixels on the boundary of the unfilled mask are filled using adjacent valid pixels
        val newlyFilled = ArrayList<Int>(remaining.coerceAtMost(10000))
        var pass = 0
        val maxPasses = 80

        while (remaining > 0 && pass < maxPasses) {
            pass++
            newlyFilled.clear()

            for (y in minY..maxY) {
                val row = y * width
                for (x in minX..maxX) {
                    val idx = row + x
                    if (!filled[idx]) {
                        var sumR = 0
                        var sumG = 0
                        var sumB = 0
                        var count = 0

                        // Sample 5x5 neighborhood
                        for (dy in -2..2) {
                            val ny = y + dy
                            if (ny in 0 until height) {
                                val nRow = ny * width
                                for (dx in -2..2) {
                                    val nx = x + dx
                                    if (nx in 0 until width) {
                                        val nIdx = nRow + nx
                                        if (filled[nIdx]) {
                                            val c = outPixels[nIdx]
                                            sumR += (c shr 16) and 0xFF
                                            sumG += (c shr 8) and 0xFF
                                            sumB += c and 0xFF
                                            count++
                                        }
                                    }
                                }
                            }
                        }

                        if (count > 0) {
                            val r = (sumR / count).coerceIn(0, 255)
                            val g = (sumG / count).coerceIn(0, 255)
                            val b = (sumB / count).coerceIn(0, 255)
                            outPixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                            newlyFilled.add(idx)
                        }
                    }
                }
            }

            if (newlyFilled.isEmpty()) break
            for (idx in newlyFilled) {
                filled[idx] = true
            }
            remaining -= newlyFilled.size
        }

        // 2-pass smoothing filter over the erased region to blend boundaries seamlessly
        val smoothed = outPixels.clone()
        for (smoothPass in 0..1) {
            for (y in minY..maxY) {
                val row = y * width
                for (x in minX..maxX) {
                    val idx = row + x
                    if (isMasked[idx]) {
                        var sumR = 0
                        var sumG = 0
                        var sumB = 0
                        var count = 0

                        for (dy in -1..1) {
                            val ny = y + dy
                            if (ny in 0 until height) {
                                val nRow = ny * width
                                for (dx in -1..1) {
                                    val nx = x + dx
                                    if (nx in 0 until width) {
                                        val c = outPixels[nRow + nx]
                                        sumR += (c shr 16) and 0xFF
                                        sumG += (c shr 8) and 0xFF
                                        sumB += c and 0xFF
                                        count++
                                    }
                                }
                            }
                        }

                        if (count > 0) {
                            smoothed[idx] = (0xFF shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                        }
                    }
                }
            }
            System.arraycopy(smoothed, 0, outPixels, 0, outPixels.size)
        }

        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        output
    }

    suspend fun saveToCacheAndGetUri(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val studioDir = File(context.cacheDir, "magic_studio").apply { mkdirs() }
        val file = File(studioDir, "magic_edited_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
