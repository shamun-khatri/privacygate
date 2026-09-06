package com.privacygate.app.redaction

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import com.privacygate.app.ai.DetectedRegion
import java.io.File
import java.io.FileOutputStream

object RedactionEngine {
    fun redactBitmap(source: Bitmap, regions: List<DetectedRegion>): Bitmap = smartMaskBitmap(source, regions)

    fun smartMaskBitmap(
        source: Bitmap,
        regions: List<DetectedRegion>,
        enabledLabels: Set<String>? = null
    ): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#171B18"); style = Paint.Style.FILL }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D7FC70"); style = Paint.Style.STROKE; strokeWidth = 2f
        }
        for (region in regions.distinctBy { "${it.label}:${it.boundingBox}" }) {
            if (enabledLabels != null && region.label !in enabledLabels) continue
            val box = region.boundingBox
            val full = RectF(
                (box.left - 4).coerceAtLeast(0).toFloat(),
                (box.top - 4).coerceAtLeast(0).toFloat(),
                (box.right + 4).coerceAtMost(result.width).toFloat(),
                (box.bottom + 4).coerceAtMost(result.height).toFloat()
            )
            val instruction = SmartMaskPolicy.forFinding(region.label)
            val isFaceOrSensitive = region.label.contains("Face") || region.label.contains("Sensitive")
            val masked = if (instruction.mode == MaskMode.KEEP_LAST_FOUR) {
                val totalDigits = region.snippet.count(Char::isDigit).coerceAtLeast(12)
                val hiddenFraction = ((totalDigits - 4f) / totalDigits).coerceIn(0.55f, 0.80f)
                RectF(full.left, full.top, full.left + full.width() * hiddenFraction, full.bottom)
            } else full

            val cornerRadius = if (isFaceOrSensitive) 24f else 8f
            canvas.drawRoundRect(masked, cornerRadius, cornerRadius, fill)
            canvas.drawRoundRect(masked, cornerRadius, cornerRadius, border)

            if (isFaceOrSensitive && masked.height() > 90f) {
                // Draw a sleek centered cyber-security badge over the masked face
                val badgeWidth = (masked.width() * 0.82f).coerceIn(160f, 320f)
                val badgeHeight = (masked.height() * 0.26f).coerceIn(38f, 62f)
                val badgeRect = RectF(
                    masked.centerX() - badgeWidth / 2f,
                    masked.centerY() - badgeHeight / 2f,
                    masked.centerX() + badgeWidth / 2f,
                    masked.centerY() + badgeHeight / 2f
                )
                val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E60D120E"); style = Paint.Style.FILL }
                val badgeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D7FC70"); style = Paint.Style.STROKE; strokeWidth = 2f }
                canvas.drawRoundRect(badgeRect, 18f, 18f, badgeFill)
                canvas.drawRoundRect(badgeRect, 18f, 18f, badgeBorder)
                drawReplacement(canvas, badgeRect, instruction.replacement)
            } else {
                drawReplacement(canvas, masked, instruction.replacement)
            }
        }
        return result
    }

    private fun drawReplacement(canvas: Canvas, bounds: RectF, text: String) {
        if (bounds.width() < 30 || bounds.height() < 12) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D7FC70")
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = (bounds.height() * 0.44f).coerceIn(12f, 32f)
            letterSpacing = 0.04f
        }
        while (paint.measureText(text) > bounds.width() - 12 && paint.textSize > 10f) paint.textSize -= 1f
        val x = bounds.centerX() - (paint.measureText(text) / 2f)
        val y = bounds.centerY() - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, x, y, paint)
    }

    fun saveAndShareRedacted(context: Context, source: Bitmap, regions: List<DetectedRegion>): Uri? =
        saveAndShareSmartMasked(context, source, regions)

    fun saveAndShareSmartMasked(
        context: Context,
        source: Bitmap,
        regions: List<DetectedRegion>,
        enabledLabels: Set<String>? = null
    ): Uri? {
        val masked = smartMaskBitmap(source, regions, enabledLabels)
        val directory = File(context.cacheDir, "redacted_images").apply { mkdirs() }
        val file = File(directory, "privacygate_masked_${System.currentTimeMillis()}.jpg")
        return try {
            FileOutputStream(file).use { masked.compress(Bitmap.CompressFormat.JPEG, 94, it) }
            masked.recycle()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Masked on-device with PrivacyGate")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(send, "Share masked copy").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            uri
        } catch (error: Exception) {
            masked.recycle()
            android.util.Log.e("PrivacyGate", "Could not export masked image", error)
            null
        }
    }
}
