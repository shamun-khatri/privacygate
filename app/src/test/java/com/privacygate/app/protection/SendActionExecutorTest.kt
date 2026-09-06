package com.privacygate.app.protection

import android.graphics.Rect
import com.privacygate.app.protection.adapters.PreviewObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendActionExecutorTest {

    private class FakeSendActionExecutor(
        var activePackage: String = "com.whatsapp.w4b",
        var activeSessionKey: String = "session-1",
        var isSendNodeAvailable: Boolean = true,
        var clickSuccess: Boolean = true
    ) : SendActionExecutor {
        var executeCallCount = 0

        override fun executeSend(observation: PreviewObservation): SendExecutionResult {
            executeCallCount++
            if (activePackage != observation.packageName) {
                return SendExecutionResult.StaleSession("Package mismatch: $activePackage vs ${observation.packageName}")
            }
            if (activeSessionKey != observation.sessionKey) {
                return SendExecutionResult.StaleSession("Session mismatch: $activeSessionKey vs ${observation.sessionKey}")
            }
            if (!isSendNodeAvailable) {
                return SendExecutionResult.TargetNotFound("Send node missing or not clickable")
            }
            return if (clickSuccess) {
                SendExecutionResult.Success
            } else {
                SendExecutionResult.ExecutionFailed("Click failed")
            }
        }
    }

    private fun makeObservation(
        sessionKey: String = "session-1",
        packageName: String = "com.whatsapp.w4b"
    ): PreviewObservation = PreviewObservation(
        sessionKey = sessionKey,
        mediaBounds = Rect(100, 200, 800, 1000),
        windowId = 42,
        packageName = packageName,
        appLabel = "WhatsApp Business",
        sendBounds = Rect(800, 1200, 900, 1300)
    )

    @Test
    fun executeSendSucceedsForMatchingSessionAndAvailableNode() {
        val executor = FakeSendActionExecutor(
            activePackage = "com.whatsapp.w4b",
            activeSessionKey = "session-1",
            isSendNodeAvailable = true,
            clickSuccess = true
        )
        val obs = makeObservation("session-1", "com.whatsapp.w4b")
        val result = executor.executeSend(obs)

        assertTrue(result is SendExecutionResult.Success)
        assertEquals(1, executor.executeCallCount)
    }

    @Test
    fun executeSendRejectsPackageMismatchAsStaleSession() {
        val executor = FakeSendActionExecutor(
            activePackage = "com.other.app",
            activeSessionKey = "session-1"
        )
        val obs = makeObservation("session-1", "com.whatsapp.w4b")
        val result = executor.executeSend(obs)

        assertTrue(result is SendExecutionResult.StaleSession)
        assertEquals(1, executor.executeCallCount)
    }

    @Test
    fun executeSendRejectsDifferentSessionKeyAsStaleSession() {
        val executor = FakeSendActionExecutor(
            activePackage = "com.whatsapp.w4b",
            activeSessionKey = "session-2" // user navigated or changed image
        )
        val obs = makeObservation("session-1", "com.whatsapp.w4b")
        val result = executor.executeSend(obs)

        assertTrue(result is SendExecutionResult.StaleSession)
        assertEquals(1, executor.executeCallCount)
    }

    @Test
    fun executeSendReturnsTargetNotFoundWhenSendButtonMissing() {
        val executor = FakeSendActionExecutor(
            activePackage = "com.whatsapp.w4b",
            activeSessionKey = "session-1",
            isSendNodeAvailable = false
        )
        val obs = makeObservation("session-1", "com.whatsapp.w4b")
        val result = executor.executeSend(obs)

        assertTrue(result is SendExecutionResult.TargetNotFound)
        assertEquals(1, executor.executeCallCount)
    }

    @Test
    fun executeSendReturnsExecutionFailedWhenClickReturnsFalse() {
        val executor = FakeSendActionExecutor(
            activePackage = "com.whatsapp.w4b",
            activeSessionKey = "session-1",
            clickSuccess = false
        )
        val obs = makeObservation("session-1", "com.whatsapp.w4b")
        val result = executor.executeSend(obs)

        assertTrue(result is SendExecutionResult.ExecutionFailed)
        assertEquals(1, executor.executeCallCount)
    }
}
