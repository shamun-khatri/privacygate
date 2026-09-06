package com.privacygate.app.ai.gemma

data class MlKitIndexSummary(
    val labels: List<String>,
    val segments: Set<String>,
    val documentType: String?,
    val isSensitiveDocument: Boolean
)

object GemmaPromptBuilder {
    fun build(summary: MlKitIndexSummary): String = """
        Analyze this photo entirely on device. Re-evaluate the existing fast ML Kit index and return
        only one valid JSON object matching this schema:
        {
          "caption": "short factual description",
          "objects": ["object"],
          "scenes": ["scene"],
          "activities": ["activity"],
          "search_labels": ["useful dynamic label"],
          "privacy_cues": ["privacy cue without its value"],
          "vehicle_present": false,
          "registration_plate_visible": false,
          "incorrect_mlkit_labels": ["only labels from the supplied ML Kit list that are clearly wrong"],
          "needs_review": false
        }

        Fast ML Kit labels: ${summary.labels.joinToString(", ").ifBlank { "none" }}
        Fast ML Kit segments: ${summary.segments.joinToString(", ").ifBlank { "none" }}
        ML Kit document type: ${summary.documentType ?: "none"}
        ML Kit marked sensitive: ${summary.isSensitiveDocument}

        Confirm correct observations through your object, scene, activity, and search labels. Add useful
        concepts ML Kit missed. Put a supplied ML Kit label in incorrect_mlkit_labels only with strong
        visual evidence. A visible vehicle registration plate means the plate area can be seen; set the
        flag even when the characters are unreadable. Do not transcribe, repeat, infer, or return Aadhaar,
        PAN, payment-card, phone, address, password, OTP, QR payload, or registration-plate values.
        Do not include markdown or text outside the JSON object.
    """.trimIndent()
}
