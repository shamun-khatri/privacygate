package com.privacygate.app.protection.overlay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.privacygate.app.model.SensitiveItem

class RiskOverlayController(private val context: Context) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var pillView: View? = null
    private var guardView: View? = null
    private var pulseAnimator: ObjectAnimator? = null

    val isShowing get() = pillView != null
    val isGuardArmed get() = guardView != null

    fun updateGuardPosition(sendBounds: Rect) = main.post {
        val guard = guardView ?: return@post
        val density = context.resources.displayMetrics.density
        fun dp(value: Float) = (value * density).toInt()

        val pad = dp(12f)
        val guardLeft = (sendBounds.left - pad).coerceAtLeast(0)
        val guardTop = (sendBounds.top - pad).coerceAtLeast(0)
        val guardWidth = (sendBounds.width() + pad * 2).coerceAtLeast(dp(64f))
        val guardHeight = (sendBounds.height() + pad * 2).coerceAtLeast(dp(64f))

        val params = guard.layoutParams as? WindowManager.LayoutParams ?: return@post
        if (params.x != guardLeft || params.y != guardTop || params.width != guardWidth || params.height != guardHeight) {
            params.x = guardLeft
            params.y = guardTop
            params.width = guardWidth
            params.height = guardHeight
            try {
                manager.updateViewLayout(guard, params)
                android.util.Log.d("PrivacyGate", "Send guard position updated to $sendBounds")
            } catch (e: Exception) {
                android.util.Log.w("PrivacyGate", "Failed to update guard layout", e)
            }
        }
    }

    fun armSendGuard(sendBounds: Rect, onAttempt: () -> Unit) = main.post {
        removeGuardInternal()
        val density = context.resources.displayMetrics.density
        fun dp(value: Float) = (value * density).toInt()

        val pad = dp(12f)
        val guardLeft = (sendBounds.left - pad).coerceAtLeast(0)
        val guardTop = (sendBounds.top - pad).coerceAtLeast(0)
        val guardWidth = (sendBounds.width() + pad * 2).coerceAtLeast(dp(64f))
        val guardHeight = (sendBounds.height() + pad * 2).coerceAtLeast(dp(64f))

        val container = FrameLayout(context).apply {
            isClickable = true
            isFocusable = false
        }

        // Layer 1: Ambient Pulsing Halo
        val outerRing = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#22D7FC70"))
                setStroke(dp(1.5f), Color.parseColor("#44D7FC70"))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(outerRing)

        pulseAnimator = ObjectAnimator.ofFloat(outerRing, View.ALPHA, 0.25f, 0.80f).apply {
            duration = 1000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Layer 2: Main Shield Button with Tactile Physics
        val shieldBtn = TextView(context).apply {
            text = "🛡"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#D7FC70"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E6121814"))
                setStroke(dp(2f), Color.parseColor("#D7FC70"))
            }
            elevation = dp(8f).toFloat()
            isClickable = true
            isFocusable = false
            contentDescription = "PrivacyGate protected send"

            val buttonMargin = dp(6f)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(buttonMargin, buttonMargin, buttonMargin, buttonMargin)
            }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(70).start()
                    }
                    MotionEvent.ACTION_UP -> {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        v.animate().scaleX(1.0f).scaleY(1.0f)
                            .setInterpolator(OvershootInterpolator(2.2f))
                            .setDuration(220).start()
                        android.util.Log.i("PrivacyGate", "Send guard intercepted tap at bounds $sendBounds")
                        onAttempt()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    }
                }
                true // Always consume touch events to prevent WhatsApp touch leakage!
            }
        }
        container.addView(shieldBtn)

        val params = WindowManager.LayoutParams(
            guardWidth,
            guardHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = guardLeft
            y = guardTop
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        try {
            manager.addView(container, params)
            guardView = container
            android.util.Log.i("PrivacyGate", "Pulsing send guard armed at $sendBounds")
        } catch (error: Exception) {
            android.util.Log.e("PrivacyGate", "Send guard failed to attach", error)
        }
    }

    fun disarmSendGuard() = main.post { removeGuardInternal() }

    fun show(
        findings: List<SensitiveItem>,
        appLabel: String = "WhatsApp",
        documentType: String? = null,
        scanLatencyMs: Long = 42L,
        onRedact: (() -> Unit)? = null,
        onSendAnyway: (() -> Unit)? = null,
        onDismiss: () -> Unit
    ) = main.post {
        removePillInternal()
        val density = context.resources.displayMetrics.density
        fun dp(value: Float) = (value * density).toInt()

        val (titleText, subtitleText) = resolveTitles(documentType, findings)
        val accent = accentFor(documentType, findings)

        // Apple Dynamic Island Inspired Container
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(15f), dp(14f), dp(15f), dp(13f))
            background = GradientDrawable().apply {
                colors = intArrayOf(
                    Color.parseColor("#FA141B16"),
                    Color.parseColor("#FA0B0F0C")
                )
                orientation = GradientDrawable.Orientation.TL_BR
                cornerRadius = dp(28f).toFloat()
                setStroke(dp(1.2f), withAlpha(accent, 0x4D))
            }
            elevation = dp(24f).toFloat()
            // Initial animation state for fluid drop-down spring
            alpha = 0f
            translationY = -dp(90f).toFloat()
            scaleX = 0.92f
            scaleY = 0.92f
        }

        // Row 1: Header (Glowing Shield Icon + Title & Subtitle + Close 'X')
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val iconBadge = TextView(context).apply {
            text = findings.firstOrNull()?.category?.icon ?: "🛡"
            gravity = Gravity.CENTER
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(withAlpha(accent, 0x24))
                setStroke(dp(1.2f), withAlpha(accent, 0x59))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f)).apply {
                marginEnd = dp(11f)
            }
        }
        headerRow.addView(iconBadge)

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(context).apply {
            text = titleText
            setTextColor(Color.parseColor("#F4F7F5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = -0.005f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textCol.addView(titleView)

        val subView = TextView(context).apply {
            text = "$subtitleText • $appLabel"
            setTextColor(Color.parseColor("#8FA396"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(1f) }
        }
        textCol.addView(subView)

        headerRow.addView(textCol)

        // Close / Cancel Button with Feedback
        val closeBtn = TextView(context).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#9AA9A0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1AFFFFFF"))
            }
            isClickable = true
            isFocusable = false
            contentDescription = "Dismiss"
            layoutParams = LinearLayout.LayoutParams(dp(30f), dp(30f)).apply {
                marginStart = dp(8f)
            }
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN ->
                        v.animate().alpha(0.55f).scaleX(0.9f).scaleY(0.9f).setDuration(70).start()
                    MotionEvent.ACTION_UP -> {
                        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start()
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        dismissWithAnimation(onDismiss)
                    }
                    MotionEvent.ACTION_CANCEL ->
                        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start()
                }
                true
            }
        }
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        // Row 2: Visual Findings Pill Chips (Shows exactly what was detected)
        if (findings.isNotEmpty()) {
            val chipsScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(10f)
                    marginStart = dp(47f)
                }
            }

            val chipsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            findings.take(3).forEach { finding ->
                val chip = TextView(context).apply {
                    // Surface the masked snippet so the user can see *what* was found.
                    val detail = finding.snippet?.takeIf { it.isNotBlank() }
                    text = if (detail != null) "${finding.label} · $detail" else finding.label
                    setTextColor(withAlpha(accent, 0xF2))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(dp(9f), dp(4f), dp(9f), dp(4f))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    background = GradientDrawable().apply {
                        setColor(withAlpha(accent, 0x1F))
                        cornerRadius = dp(11f).toFloat()
                        setStroke(dp(1f), withAlpha(accent, 0x3D))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(6f) }
                }
                chipsContainer.addView(chip)
            }

            if (findings.size > 3) {
                val moreChip = TextView(context).apply {
                    text = "+${findings.size - 3} more"
                    setTextColor(Color.parseColor("#8FA396"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                    setPadding(dp(9f), dp(4f), dp(9f), dp(4f))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#16FFFFFF"))
                        cornerRadius = dp(11f).toFloat()
                    }
                }
                chipsContainer.addView(moreChip)
            }

            chipsScroll.addView(chipsContainer)
            card.addView(chipsScroll)
        }

        // Row 3: Trust & Latency Badge
        val trustRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(9f)
                marginStart = dp(47f)
            }
        }

        val dotView = TextView(context).apply {
            text = "●"
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 7f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6f) }
        }
        trustRow.addView(dotView)

        val trustText = TextView(context).apply {
            text = "Checked on-device in ${scanLatencyMs} ms • never uploaded"
            setTextColor(Color.parseColor("#6F8578"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        trustRow.addView(trustText)
        card.addView(trustRow)

        // Tell the user plainly that we step aside from here on.
        val onceNotice = TextView(context).apply {
            text = "Heads-up shown once — your next tap on Send goes through"
            setTextColor(Color.parseColor("#5E7266"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(5f)
                marginStart = dp(47f)
            }
        }
        card.addView(onceNotice)

        // Row 4: Modern Action Buttons
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12f)
            }
        }

        if (onRedact != null) {
            val maskButton = Button(context).apply {
                text = "Mask & send"
                setTextColor(Color.parseColor("#0C120D"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTypeface(typeface, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(accent)
                    cornerRadius = dp(21f).toFloat()
                }
                isAllCaps = false
                stateListAnimator = null
                elevation = dp(4f).toFloat()
                layoutParams = LinearLayout.LayoutParams(0, dp(42f), 1.2f).apply {
                    marginEnd = dp(8f)
                }
                attachPressFeedback(HapticFeedbackConstants.CONFIRM) {
                    removeGuardInternal()
                    dismissWithAnimation(onRedact)
                }
            }
            actionsRow.addView(maskButton)
        }

        if (onSendAnyway != null) {
            val sendAnywayButton = Button(context).apply {
                text = "Send as-is"
                setTextColor(Color.parseColor("#DCE4DE"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTypeface(typeface, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E2621"))
                    cornerRadius = dp(21f).toFloat()
                    setStroke(dp(1f), Color.parseColor("#3A4C40"))
                }
                isAllCaps = false
                stateListAnimator = null
                layoutParams = LinearLayout.LayoutParams(0, dp(42f), 1f)
                attachPressFeedback(HapticFeedbackConstants.VIRTUAL_KEY) {
                    removeGuardInternal()
                    dismissWithAnimation(onSendAnyway)
                }
            }
            actionsRow.addView(sendAnywayButton)
        }

        card.addView(actionsRow)

        val screenWidth = context.resources.displayMetrics.widthPixels
        val cardWidth = dp(364f).coerceAtMost((screenWidth * 0.94f).toInt())

        val params = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(52f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        try {
            manager.addView(card, params)
            pillView = card
            // Fluid Spring Entrance Animation
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(340)
                .setInterpolator(OvershootInterpolator(1.18f))
                .start()
            android.util.Log.i("PrivacyGate", "Privacy Island fluid entrance displayed: $titleText")
        } catch (error: Exception) {
            android.util.Log.e("PrivacyGate", "Privacy Island failed to show", error)
        }
    }

    private fun dismissWithAnimation(onComplete: () -> Unit = {}) {
        val pill = pillView
        if (pill == null) {
            onComplete()
            return
        }
        pill.animate()
            .alpha(0f)
            .translationY(-120f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(190)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                removePillInternal()
                onComplete()
            }
            .start()
    }

    fun showFeedback(message: String) = main.post {
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            android.util.Log.w("PrivacyGate", "Could not show toast: $message", error)
        }
    }

    fun hide() = main.post {
        dismissWithAnimation()
        removeGuardInternal()
    }

    private fun removePillInternal() {
        pillView?.let {
            try {
                manager.removeView(it)
            } catch (_: Exception) {}
        }
        pillView = null
    }

    private fun removeGuardInternal() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        guardView?.let {
            try {
                manager.removeView(it)
            } catch (_: Exception) {}
        }
        guardView = null
    }

    /** Applies [alpha] (0..255) to [color], keeping its RGB channels. */
    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /**
     * Picks an accent colour per risk family so the warning reads at a glance:
     * amber for hard identity/financial documents, violet for private imagery,
     * signature lime otherwise.
     */
    private fun accentFor(documentType: String?, findings: List<SensitiveItem>): Int {
        val combined = (documentType.orEmpty() + " " + findings.joinToString(" ") { it.label }).lowercase()
        return when {
            combined.contains("aadhaar") || combined.contains("pan") || combined.contains("passport") ||
                combined.contains("identity") || combined.contains("id card") || combined.contains("voter") ||
                combined.contains("driving") || combined.contains("payment") || combined.contains("credit") ||
                combined.contains("debit") || combined.contains("bank") || combined.contains("otp") ||
                combined.contains("password") -> Color.parseColor("#FFB454")
            combined.contains("nsfw") || combined.contains("exposed body") ||
                combined.contains("private") || combined.contains("sensitive") -> Color.parseColor("#C79BFF")
            else -> Color.parseColor("#D7FC70")
        }
    }

    /** Scale + fade press response with haptics, consuming the touch. */
    private fun View.attachPressFeedback(hapticOnRelease: Int, onRelease: () -> Unit) {
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.9f).setDuration(70).start()
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setInterpolator(OvershootInterpolator(1.8f))
                        .setDuration(180).start()
                    v.performHapticFeedback(hapticOnRelease)
                    onRelease()
                }
                MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start()
            }
            true
        }
    }

    private fun resolveTitles(documentType: String?, findings: List<SensitiveItem>): Pair<String, String> {
        val rawDoc = documentType?.lowercase().orEmpty()
        val rawLabel = findings.firstOrNull()?.label?.lowercase().orEmpty()
        val combined = "$rawDoc $rawLabel"

        return when {
            combined.contains("face") || combined.contains("portrait") ->
                Pair("Face / Portrait detected", "Personal face visible • Protect before sending")
            combined.contains("nsfw") || combined.contains("sensitive / private") || combined.contains("exposed body") || combined.contains("private / sensitive") ->
                Pair("Sensitive photo detected", "Private photo detected • Confirm before sending")
            combined.contains("aadhaar") ->
                Pair("Aadhaar detected", "12-digit identity number is visible")
            combined.contains("pan") ->
                Pair("PAN card detected", "Permanent Account Number visible")
            combined.contains("passport") ->
                Pair("Passport detected", "Passport number and personal details visible")
            combined.contains("identity") || combined.contains("id card") || combined.contains("voter") || combined.contains("driving") || combined.contains("government id") ->
                Pair("Identity document detected", "Official ID and personal details visible")
            combined.contains("payment") || combined.contains("credit") || combined.contains("debit") || combined.contains("bank card") ->
                Pair("Payment card detected", "Card number and sensitive details visible")
            combined.contains("invoice") || combined.contains("gstin") || combined.contains("bill") ->
                Pair("Invoice / Bill detected", "Financial transaction and tax details visible")
            combined.contains("medical") || combined.contains("prescription") || combined.contains("rx") ->
                Pair("Medical record detected", "Health diagnosis and prescription visible")
            combined.contains("otp") || combined.contains("password") || combined.contains("secret") ->
                Pair("Security code detected", "Confidential OTP or credentials visible")
            else -> {
                val title = documentType?.substringBefore(" /") ?: findings.firstOrNull()?.label ?: "Sensitive document"
                Pair("$title detected", "Private personal information visible")
            }
        }
    }
}
