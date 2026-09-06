package com.privacygate.app.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewGateTest {
    @Test
    fun repeatedEventsRequireAStablePreviewBeforeIssuingToken() {
        val gate = PreviewGate(settleMs = 350)
        assertNull(gate.observe("preview-a", 0))
        assertNull(gate.observe("preview-a", 100))
        assertNull(gate.observe("preview-a", 349))
        val token = gate.observe("preview-a", 350)
        assertNotNull(token)
        assertTrue(gate.isCurrent(token!!))
    }

    @Test
    fun settleDelayIsMeasuredFromTheFirstObservation() {
        val gate = PreviewGate(settleMs = 100)
        assertNull(gate.observe("preview-a", 1))
        assertNull(gate.observe("preview-a", 100))
        assertNotNull(gate.observe("preview-a", 101))
    }

    @Test
    fun nullPreviewInvalidatesAnOldToken() {
        val gate = PreviewGate(settleMs = 0)
        val token = gate.observe("preview-a", 0)
        assertNotNull(token)
        assertNull(gate.observe(null, 1))
        assertFalse(gate.isCurrent(token!!))
    }

    @Test
    fun newPreviewInvalidatesAnOldToken() {
        val gate = PreviewGate(settleMs = 0)
        val oldToken = gate.observe("preview-a", 0)
        assertNotNull(oldToken)
        val newToken = gate.observe("preview-b", 1)
        assertNotNull(newToken)
        assertFalse(gate.isCurrent(oldToken!!))
        assertTrue(gate.isCurrent(newToken!!))
    }

    @Test
    fun repeatedSamePreviewAfterScanDoesNotRescan() {
        val gate = PreviewGate(settleMs = 0)
        val token = gate.observe("preview-a", 0)
        assertNotNull(token)
        assertNull(gate.observe("preview-a", 1))
        assertNull(gate.observe("preview-a", 2))
        assertTrue(gate.isCurrent(token!!))
    }

    @Test
    fun reenteringAfterLeavingAllowsTheSamePreviewToScanAgain() {
        val gate = PreviewGate(settleMs = 0)
        val firstToken = gate.observe("preview-a", 0)
        assertNotNull(firstToken)
        gate.observe(null, 1)
        assertFalse(gate.isCurrent(firstToken!!))
        val secondToken = gate.observe("preview-a", 2)
        assertNotNull(secondToken)
        assertTrue(secondToken != firstToken)
        assertTrue(gate.isCurrent(secondToken!!))
    }

    @Test
    fun resetInvalidatesCurrentToken() {
        val gate = PreviewGate(settleMs = 0)
        val token = gate.observe("preview-a", 0)
        gate.reset()
        assertFalse(gate.isCurrent(token!!))
    }

    @Test
    fun resetInvalidatesPendingPreview() {
        val gate = PreviewGate(settleMs = 100)
        assertNull(gate.observe("preview-a", 0))
        gate.reset()
        assertNull(gate.observe("preview-a", 101))
        assertNotNull(gate.observe("preview-a", 201))
    }
}
