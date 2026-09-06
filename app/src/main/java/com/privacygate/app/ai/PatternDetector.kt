package com.privacygate.app.ai

enum class FindingType { PHONE, EMAIL }

class PatternDetector {
    private val email = Regex("(?i)(?<![a-z0-9._%+-])[a-z0-9._%+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}(?![a-z0-9.-])")
    private val phone = Regex("(?<![\\w@])(?:\\+91[ -]*|91[ -]+)?[6-9](?:[ -]?\\d){9}(?![\\w@])")

    fun detect(text: String): Set<FindingType> = buildSet {
        val emails = email.findAll(text).toList()
        if (emails.isNotEmpty()) add(FindingType.EMAIL)
        if (phone.findAll(text).any { number -> emails.none { number.range.first in it.range } }) {
            add(FindingType.PHONE)
        }
    }
}
