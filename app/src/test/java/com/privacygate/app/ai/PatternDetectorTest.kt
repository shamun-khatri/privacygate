package com.privacygate.app.ai

import org.junit.Assert.*
import org.junit.Test

class PatternDetectorTest {
    private val detector = PatternDetector()
    @Test fun detectsEmailWithoutKnowingDemoValue() {
        assertEquals(setOf(FindingType.EMAIL), detector.detect("Email: sample.person+demo@example.org"))
    }
    @Test fun detectsIndianPhoneWithCountryCodeAndSpaces() {
        assertEquals(setOf(FindingType.PHONE), detector.detect("Mobile: +91 90000 00001"))
    }
    @Test fun deduplicatesFindings() {
        assertEquals(setOf(FindingType.EMAIL), detector.detect("a@example.com and b@example.org"))
    }
    @Test fun ignoresLongNumberAndShortNumber() {
        assertTrue(detector.detect("123456789012345678 12345 42").isEmpty())
    }
    @Test fun ignoresUpiWithoutDomainAndNormalText() {
        assertTrue(detector.detect("demo@okbank\nOrder received. Total 2500").isEmpty())
    }
    @Test fun detectsBothKinds() {
        assertEquals(setOf(FindingType.PHONE, FindingType.EMAIL), detector.detect("9000000001 demo@example.com"))
    }
}
