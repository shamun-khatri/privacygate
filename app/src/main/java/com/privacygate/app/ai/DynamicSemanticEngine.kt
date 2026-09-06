package com.privacygate.app.ai

import android.graphics.Rect
import com.privacygate.app.ai.ocr.OcrResult
import com.privacygate.app.settings.PrivacyPreferencesState
import com.privacygate.app.settings.SensitivityCategory

data class DetectedRegion(
    val boundingBox: Rect,
    val category: SensitivityCategory,
    val label: String,
    val snippet: String
)

data class DynamicAnalysisResult(
    val riskScore: Float,
    val isActionable: Boolean,
    val documentType: String?,
    val detectedCategories: Set<SensitivityCategory>,
    val regions: List<DetectedRegion>,
    val fullText: String
)

class DynamicSemanticEngine {

    // Verhoeff Algorithm Tables for Aadhaar verification
    private val verhoeffD = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
        intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
        intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
        intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
        intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
    )

    private val verhoeffP = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
        intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
        intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
    )

    private val verhoeffInv = intArrayOf(0, 4, 3, 2, 1, 5, 6, 7, 8, 9)

    // Regex patterns
    private val emailRegex = Regex("(?i)(?<![a-z0-9._%+-])[a-z0-9._%+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}(?![a-z0-9.-])")
    private val phonePrefixKeyword = Regex("(?i)\\b(call|phone|mobile|mob|cell|tel|whatsapp|contact|helpline|ph[:.]?)\\b")
    private val formattedPhoneRegex = Regex("(?<![\\w@])(?:\\+91[ -]?)?[6-9]\\d{4}[ -]\\d{5}(?![\\w@])")
    private val prefixedPhoneRegex = Regex("(?<![\\w@])(?:\\+91[ -]*|91[ -]+)[6-9](?:[ -]?\\d){9}(?![\\w@])")
    private val rawTenDigitRegex = Regex("(?<![\\w@])[6-9]\\d{9}(?![\\w@])")
    private val panRegex = Regex("(?<![A-Z0-9])[A-Z]{5}[0-9]{4}[A-Z](?![A-Z0-9])")
    private val aadhaarCandidateRegex = Regex("(?<!\\d)[2-9]\\d{3}[ -]?\\d{4}[ -]?\\d{4}(?!\\d)")
    private val ifscRegex = Regex("(?<![A-Z0-9])[A-Z]{4}0[A-Z0-9]{6}(?![A-Z0-9])")
    private val upiRegex = Regex("(?i)(?<![a-z0-9._-])[a-z0-9._-]{2,256}@(okhdfcbank|okaxis|oksbi|okicici|upi|paytm|ybl|ibl|axl|apl|barodampay|postbank|federal)(?![a-z0-9._-])")
    private val otpRegex = Regex("(?i)(?:(?:otp|code|verification|pin)\\s*(?:is|:|-)?\\s*([0-9]{4,8})|([0-9]{4,8})\\s*(?:is|as)?\\s*(?:your|the)?\\s*(?:secret)?\\s*(?:otp|code|pin|verification))")
    private val definiteMedicalRegex = Regex("(?i)\\b(prescription|diagnosis|pathology report|blood test|radiology|ultrasound|biopsy|mri report|ct scan|discharge summary|opd slip|dosage:)\\b")
    private val contextualMedicalRegex = Regex("(?i)\\b(rx\\b|patient|doctor|dosage|hospital|clinic|treatment|tablet|capsule|mg\\b|dr\\.)")
    private val definiteInvoiceRegex = Regex("(?i)\\b(tax invoice|bill to|gstin\\b|invoice no|invoice #|balance due|total amount|grand total)\\b")
    private val contextualInvoiceRegex = Regex("(?i)\\b(invoice|subtotal|amount due|order total|qty\\b|rate\\b|hsn\\b|sgst|cgst|igst)\\b")
    private val vehiclePlateRegex = Regex("(?i)\\b(?:[A-Z]{2}[ -]?[0-9]{1,2}[ -]?[A-Z]{1,3}[ -]?[0-9]{4}|[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4})\\b")

    fun validateAadhaar(rawNumber: String): Boolean {
        val digits = rawNumber.filter { it.isDigit() }
        if (digits.length != 12) return false
        // UIDAI specifications: Aadhaar numbers never start with 0 or 1
        if (digits[0] == '0' || digits[0] == '1') return false
        if (digits.all { it == digits[0] }) return false
        var c = 0
        val reversed = digits.reversed()
        for (i in reversed.indices) {
            val digit = reversed[i] - '0'
            c = verhoeffD[c][verhoeffP[i % 8][digit]]
        }
        return c == 0
    }

    fun validateLuhn(rawNumber: String): Boolean {
        val digits = rawNumber.filter { it.isDigit() }
        if (digits.length !in 15..19) return false
        if (digits.all { it == digits[0] }) return false
        // Standard card issuer identification number (IIN) prefixes:
        // 4 (Visa), 51-55 or 22-27 (Mastercard), 34/37 (Amex), 60/65 (RuPay/Discover)
        val validPrefix = digits.startsWith("4") ||
                digits.startsWith("51") || digits.startsWith("52") || digits.startsWith("53") ||
                digits.startsWith("54") || digits.startsWith("55") || digits.startsWith("34") ||
                digits.startsWith("37") || digits.startsWith("60") || digits.startsWith("65")
        if (!validPrefix) return false

        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n = (n % 10) + 1
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    fun analyze(
        ocr: OcrResult,
        prefs: PrivacyPreferencesState,
        visualRegions: List<DetectedRegion> = emptyList()
    ): DynamicAnalysisResult {
        val fullText = ocr.fullText
        val lowerText = fullText.lowercase()
        val regions = mutableListOf<DetectedRegion>()
        val categories = mutableSetOf<SensitivityCategory>()
        var highestScore = 0.0f

        // Include visual regions (Faces, NSFW/Sensitive Media) if enabled in settings
        for (vr in visualRegions) {
            if (prefs.enabledCategories.contains(vr.category)) {
                categories.add(vr.category)
                regions.add(vr)
                val score = when (vr.category) {
                    SensitivityCategory.NSFW_SENSITIVE -> 0.95f
                    SensitivityCategory.FACE_PORTRAIT -> 0.85f
                    else -> 0.8f
                }
                highestScore = maxOf(highestScore, score)
            }
        }

        for (element in ocr.elements) {
            val line = element.text
            val box = element.boundingBox ?: continue

            // 1. Check Custom Keywords
            for (kw in prefs.customKeywords) {
                if (kw.isNotBlank() && line.contains(kw, ignoreCase = true)) {
                    categories.add(SensitivityCategory.SECRET_CREDENTIAL)
                    regions.add(DetectedRegion(box, SensitivityCategory.SECRET_CREDENTIAL, "Custom Private Keyword", kw))
                    highestScore = maxOf(highestScore, 0.9f)
                }
            }

            // 2. Identity Documents (Aadhaar & PAN)
            if (prefs.enabledCategories.contains(SensitivityCategory.IDENTITY_DOCUMENT)) {
                val hasAadhaarDocContext = lowerText.contains("aadhaar") || lowerText.contains("adhaar") ||
                    lowerText.contains("aadhar") || lowerText.contains("adhar") ||
                    lowerText.contains("uidai") || lowerText.contains("unique identification") ||
                    lowerText.contains("identification authority") ||
                    lowerText.contains("mera aadhaar") || lowerText.contains("meri pehchan") ||
                    lowerText.contains("government of india") || lowerText.contains("govt of india") ||
                    lowerText.contains("rnment of india") || lowerText.contains("bharat sarkar") ||
                    lowerText.contains("aar no") || lowerText.contains("vid :")
                for (match in aadhaarCandidateRegex.findAll(line)) {
                    val isValidVerhoeff = validateAadhaar(match.value)
                    if (isValidVerhoeff || hasAadhaarDocContext) {
                        categories.add(SensitivityCategory.IDENTITY_DOCUMENT)
                        regions.add(DetectedRegion(box, SensitivityCategory.IDENTITY_DOCUMENT, "Aadhaar Number", match.value))
                        highestScore = maxOf(highestScore, if (isValidVerhoeff) 0.95f else 0.85f)
                    }
                }
                for (match in panRegex.findAll(line)) {
                    categories.add(SensitivityCategory.IDENTITY_DOCUMENT)
                    regions.add(DetectedRegion(box, SensitivityCategory.IDENTITY_DOCUMENT, "PAN Card Number", match.value))
                    highestScore = maxOf(highestScore, 0.9f)
                }
                val lowerLine = line.lowercase()
                if (lowerLine.contains("aadhaar") || lowerLine.contains("adhaar") ||
                    lowerLine.contains("aadhar") || lowerLine.contains("adhar") ||
                    lowerLine.contains("government of india") || lowerLine.contains("govt of india") ||
                    lowerLine.contains("rnment of india") || lowerLine.contains("bharat sarkar") ||
                    lowerLine.contains("identification authority") || lowerLine.contains("unique identification") ||
                    lowerLine.contains("meri pehchan") || lowerLine.contains("passport") ||
                    lowerLine.contains("driving licence") || lowerLine.contains("driving license") ||
                    lowerLine.contains("income tax department") || lowerLine.contains("election commission") ||
                    lowerLine.contains("voter id") || lowerLine.contains("aar no")) {
                    categories.add(SensitivityCategory.IDENTITY_DOCUMENT)
                    regions.add(DetectedRegion(box, SensitivityCategory.IDENTITY_DOCUMENT, "Government ID Document", line.take(30)))
                    highestScore = maxOf(highestScore, 0.85f)
                }
            }

            // 3. Financial & Banking
            if (prefs.enabledCategories.contains(SensitivityCategory.FINANCIAL_BANKING)) {
                for (match in ifscRegex.findAll(line)) {
                    categories.add(SensitivityCategory.FINANCIAL_BANKING)
                    regions.add(DetectedRegion(box, SensitivityCategory.FINANCIAL_BANKING, "IFSC Bank Code", match.value))
                    highestScore = maxOf(highestScore, 0.8f)
                }
                for (match in upiRegex.findAll(line)) {
                    categories.add(SensitivityCategory.FINANCIAL_BANKING)
                    regions.add(DetectedRegion(box, SensitivityCategory.FINANCIAL_BANKING, "UPI Payment ID", match.value))
                    highestScore = maxOf(highestScore, 0.75f)
                }
                val cardCandidate = line.filter { it.isDigit() }
                if (cardCandidate.length in 13..19 && validateLuhn(cardCandidate)) {
                    categories.add(SensitivityCategory.FINANCIAL_BANKING)
                    regions.add(DetectedRegion(box, SensitivityCategory.FINANCIAL_BANKING, "Payment Card Number", cardCandidate))
                    highestScore = maxOf(highestScore, 0.95f)
                }
            }

            // 4. Passwords & OTPs
            if (prefs.enabledCategories.contains(SensitivityCategory.SECRET_CREDENTIAL)) {
                for (match in otpRegex.findAll(line)) {
                    categories.add(SensitivityCategory.SECRET_CREDENTIAL)
                    regions.add(DetectedRegion(box, SensitivityCategory.SECRET_CREDENTIAL, "OTP / Verification Code", match.value))
                    highestScore = maxOf(highestScore, 0.95f)
                }
            }

            // 5. Medical & Health (requires definite medical terminology or multiple clinical indicators)
            if (prefs.enabledCategories.contains(SensitivityCategory.MEDICAL_HEALTH)) {
                val hasDefinite = definiteMedicalRegex.containsMatchIn(line)
                val contextualCount = contextualMedicalRegex.findAll(line).count()
                if (hasDefinite || contextualCount >= 2 || (contextualCount >= 1 && contextualMedicalRegex.findAll(lowerText).count() >= 2)) {
                    categories.add(SensitivityCategory.MEDICAL_HEALTH)
                    regions.add(DetectedRegion(box, SensitivityCategory.MEDICAL_HEALTH, "Medical Information", line.take(30)))
                    highestScore = maxOf(highestScore, if (hasDefinite) 0.85f else 0.65f)
                }
            }

            // 6. Bills & Invoices (requires definite invoice header/GSTIN or multiple invoice terms)
            if (prefs.enabledCategories.contains(SensitivityCategory.INVOICE_RECEIPT)) {
                val hasDefinite = definiteInvoiceRegex.containsMatchIn(line)
                val contextualCount = contextualInvoiceRegex.findAll(line).count()
                if (hasDefinite || contextualCount >= 2 || (contextualCount >= 1 && contextualInvoiceRegex.findAll(lowerText).count() >= 2)) {
                    categories.add(SensitivityCategory.INVOICE_RECEIPT)
                    regions.add(DetectedRegion(box, SensitivityCategory.INVOICE_RECEIPT, "Financial Invoice", line.take(30)))
                    highestScore = maxOf(highestScore, if (hasDefinite) 0.75f else 0.55f)
                }
            }

            // 7. Contact Details (Phone & Email with contextual validation)
            if (prefs.enabledCategories.contains(SensitivityCategory.CONTACT_INFO)) {
                // High confidence phone numbers (+91 prefix or grouped format XXXXX XXXXX)
                for (match in prefixedPhoneRegex.findAll(line)) {
                    categories.add(SensitivityCategory.CONTACT_INFO)
                    regions.add(DetectedRegion(box, SensitivityCategory.CONTACT_INFO, "Phone Number", match.value))
                    highestScore = maxOf(highestScore, 0.75f)
                }
                for (match in formattedPhoneRegex.findAll(line)) {
                    categories.add(SensitivityCategory.CONTACT_INFO)
                    regions.add(DetectedRegion(box, SensitivityCategory.CONTACT_INFO, "Phone Number", match.value))
                    highestScore = maxOf(highestScore, 0.70f)
                }
                // Raw 10-digit number only flagged if accompanied by phone keyword or strict mode
                if (prefixedPhoneRegex.findAll(line).none() && formattedPhoneRegex.findAll(line).none()) {
                    for (match in rawTenDigitRegex.findAll(line)) {
                        val hasPhoneContext = phonePrefixKeyword.containsMatchIn(line) || phonePrefixKeyword.containsMatchIn(lowerText)
                        if (hasPhoneContext) {
                            categories.add(SensitivityCategory.CONTACT_INFO)
                            regions.add(DetectedRegion(box, SensitivityCategory.CONTACT_INFO, "Phone Number", match.value))
                            highestScore = maxOf(highestScore, 0.70f)
                        } else if (prefs.sensitivityLevel == com.privacygate.app.settings.SensitivityLevel.STRICT) {
                            categories.add(SensitivityCategory.CONTACT_INFO)
                            regions.add(DetectedRegion(box, SensitivityCategory.CONTACT_INFO, "Potential Phone Number", match.value))
                            highestScore = maxOf(highestScore, 0.35f)
                        }
                    }
                }
                for (match in emailRegex.findAll(line)) {
                    categories.add(SensitivityCategory.CONTACT_INFO)
                    regions.add(DetectedRegion(box, SensitivityCategory.CONTACT_INFO, "Email Address", match.value))
                    highestScore = maxOf(highestScore, 0.75f)
                }
            }

            // 8. Vehicle Registration Plates
            if (prefs.enabledCategories.contains(SensitivityCategory.VEHICLE_PLATE)) {
                for (match in vehiclePlateRegex.findAll(line)) {
                    categories.add(SensitivityCategory.VEHICLE_PLATE)
                    regions.add(DetectedRegion(box, SensitivityCategory.VEHICLE_PLATE, "Vehicle Registration Plate", match.value))
                    highestScore = maxOf(highestScore, 0.85f)
                }
            }
        }

        val docType = when {
            categories.contains(SensitivityCategory.NSFW_SENSITIVE) -> "Private / Sensitive Media"
            categories.contains(SensitivityCategory.FACE_PORTRAIT) -> "Face / Personal Portrait"
            categories.contains(SensitivityCategory.IDENTITY_DOCUMENT) -> "Identity Document / ID Card"
            categories.contains(SensitivityCategory.SECRET_CREDENTIAL) -> "Security Credential / OTP"
            categories.contains(SensitivityCategory.FINANCIAL_BANKING) -> "Banking / Payment Record"
            categories.contains(SensitivityCategory.MEDICAL_HEALTH) -> "Medical Report / Prescription"
            categories.contains(SensitivityCategory.INVOICE_RECEIPT) -> "Invoice / Financial Bill"
            categories.contains(SensitivityCategory.CONTACT_INFO) -> "Contact Details"
            categories.contains(SensitivityCategory.VEHICLE_PLATE) -> "Vehicle Number Plate"
            else -> null
        }

        val isActionable = highestScore >= prefs.sensitivityLevel.minRiskScore && regions.isNotEmpty()

        return DynamicAnalysisResult(
            riskScore = highestScore,
            isActionable = isActionable,
            documentType = docType,
            detectedCategories = categories,
            regions = regions,
            fullText = fullText
        )
    }
}
