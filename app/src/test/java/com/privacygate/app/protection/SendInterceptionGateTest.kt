package com.privacygate.app.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendInterceptionGateTest {
    @Test
    fun firstTapIsBlockedAndEveryLaterTapPassesThrough() {
        val gate = SendInterceptionGate()
        gate.arm("preview-a")
        // Only the first tap warns.
        assertTrue(gate.intercept("preview-a"))
        // Everything after that goes straight to the host app.
        assertFalse(gate.intercept("preview-a"))
        assertFalse(gate.intercept("preview-a"))
        assertFalse(gate.intercept("preview-a"))
    }

    @Test
    fun interceptingMarksSessionAcknowledged() {
        val gate = SendInterceptionGate()
        gate.arm("preview-a")
        assertFalse(gate.isAcknowledged("preview-a"))
        assertTrue(gate.intercept("preview-a"))
        assertTrue(gate.isAcknowledged("preview-a"))
    }

    @Test
    fun stalePreviewCannotConsumeOrAcknowledgeCurrentGuard() {
        val gate = SendInterceptionGate()
        gate.arm("preview-b")
        gate.acknowledge("preview-a")
        assertFalse(gate.intercept("preview-a"))
        assertFalse(gate.isAcknowledged("preview-a"))
        assertTrue(gate.intercept("preview-b"))
    }

    @Test
    fun clearDisarmsGuard() {
        val gate = SendInterceptionGate()
        gate.arm("preview-a")
        gate.clear()
        assertFalse(gate.intercept("preview-a"))
        assertFalse(gate.isArmed("preview-a"))
        assertFalse(gate.isAcknowledged("preview-a"))
    }

    @Test
    fun explicitConfirmationSuppressesAllFurtherBlocking() {
        val gate = SendInterceptionGate()
        gate.arm("session-1")
        gate.acknowledge("session-1")
        assertTrue(gate.isAcknowledged("session-1"))
        // User already confirmed — never block this session again.
        assertFalse(gate.intercept("session-1"))
        assertFalse(gate.intercept("session-1"))
    }

    @Test
    fun dismissingWarningStillLetsTheNextTapSend() {
        val gate = SendInterceptionGate()
        gate.arm("session-1")
        // Warning raised by the first tap.
        assertTrue(gate.intercept("session-1"))
        // User dismisses without confirming; the guard must not block again.
        assertFalse(gate.intercept("session-1"))
    }

    @Test
    fun reobservingSameSessionDoesNotRearmBlocking() {
        val gate = SendInterceptionGate()
        gate.arm("session-1")
        assertTrue(gate.intercept("session-1"))
        // The a11y service re-observes the same preview many times.
        gate.arm("session-1")
        gate.arm("session-1")
        assertTrue(gate.isAcknowledged("session-1"))
        assertFalse(gate.intercept("session-1"))
    }

    @Test
    fun repeatedCallbacksDoNotCorruptState() {
        val gate = SendInterceptionGate()
        gate.arm("session-1")
        gate.arm("session-1")
        assertTrue(gate.isArmed("session-1"))
        gate.acknowledge("session-1")
        gate.acknowledge("session-1")
        assertTrue(gate.isAcknowledged("session-1"))
        assertFalse(gate.intercept("session-1"))
    }

    @Test
    fun newSessionClearsAcknowledgementAndWarnsAgain() {
        val gate = SendInterceptionGate()
        gate.arm("session-1")
        assertTrue(gate.intercept("session-1"))
        assertTrue(gate.isAcknowledged("session-1"))
        // A genuinely different selection must warn once more.
        gate.arm("session-2")
        assertFalse(gate.isAcknowledged("session-1"))
        assertFalse(gate.isAcknowledged("session-2"))
        assertTrue(gate.intercept("session-2"))
        assertFalse(gate.intercept("session-2"))
    }
}
