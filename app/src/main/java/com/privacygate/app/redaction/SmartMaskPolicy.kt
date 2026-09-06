package com.privacygate.app.redaction

enum class MaskMode { FULL, KEEP_LAST_FOUR }

data class MaskInstruction(
    val mode: MaskMode,
    val replacement: String
)

object SmartMaskPolicy {
    fun forFinding(label: String): MaskInstruction = when (label) {
        "Aadhaar Number" -> MaskInstruction(MaskMode.KEEP_LAST_FOUR, "XXXX XXXX")
        "Payment Card Number" -> MaskInstruction(MaskMode.KEEP_LAST_FOUR, "•••• •••• ••••")
        "Card CVV" -> MaskInstruction(MaskMode.FULL, "CVV •••")
        "Phone Number", "Email Address", "Customer Address" -> MaskInstruction(MaskMode.FULL, "PRIVATE")
        "Human Face / Portrait" -> MaskInstruction(MaskMode.FULL, "[PROTECTED FACE]")
        "Sensitive / Private Content" -> MaskInstruction(MaskMode.FULL, "[PRIVATE]")
        else -> MaskInstruction(MaskMode.FULL, "PROTECTED")
    }
}
