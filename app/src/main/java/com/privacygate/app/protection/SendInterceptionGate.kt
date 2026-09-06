package com.privacygate.app.protection

/**
 * Tracks whether the send action for a preview session should be intercepted.
 *
 * Policy: **warn once per session, then get out of the way.**
 *
 * The first Send tap on a sensitive preview is intercepted and raises the warning.
 * From that moment the session is *acknowledged* — every later tap passes straight
 * through to the host app so the user can send without fighting the guard.
 *
 * Acknowledgement is keyed to the preview session, so a genuinely new selection
 * (new session key) re-arms and warns again.
 */
class SendInterceptionGate {
    private var armedKey: String? = null
    private var acknowledgedKey: String? = null

    /**
     * Arms the guard for [previewKey]. Re-arming the *same* key preserves an existing
     * acknowledgement, so repeated observations of one session never re-block the user.
     */
    @Synchronized
    fun arm(previewKey: String) {
        if (armedKey != previewKey) {
            acknowledgedKey = null
        }
        armedKey = previewKey
    }

    /**
     * Returns true when this tap must be blocked and the warning shown.
     *
     * Only the first tap of an armed session returns true; that call also marks the
     * session acknowledged, so all subsequent taps return false.
     */
    @Synchronized
    fun intercept(previewKey: String): Boolean {
        if (previewKey != armedKey) return false
        if (acknowledgedKey == previewKey) return false
        acknowledgedKey = previewKey
        return true
    }

    /** Marks [previewKey] acknowledged without consuming a tap (explicit user confirmation). */
    @Synchronized
    fun acknowledge(previewKey: String) {
        if (previewKey == armedKey) acknowledgedKey = previewKey
    }

    @Synchronized
    fun isArmed(previewKey: String): Boolean = armedKey == previewKey

    /** True once the user has been warned for [previewKey]; further taps are unguarded. */
    @Synchronized
    fun isAcknowledged(previewKey: String): Boolean = acknowledgedKey == previewKey

    @Synchronized
    fun clear() {
        armedKey = null
        acknowledgedKey = null
    }
}
