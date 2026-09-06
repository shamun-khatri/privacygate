package com.privacygate.app.protection

class PreviewGate(private val settleMs: Long = 350) {
    private var currentKey: String? = null
    private var generation = 0L
    private var enteredAt = 0L
    private var issued = false
    init { require(settleMs >= 0) }

    @Synchronized fun observe(key: String?, nowMs: Long): Long? {
        if (key != currentKey) {
            generation++
            currentKey = key
            enteredAt = nowMs
            issued = false
        }
        if (key == null || issued || nowMs - enteredAt < settleMs) return null
        issued = true
        return generation
    }

    @Synchronized fun millisUntilReady(key: String, nowMs: Long): Long? {
        if (key != currentKey || issued) return null
        return (settleMs - (nowMs - enteredAt)).coerceAtLeast(0L)
    }

    @Synchronized fun isCurrent(token: Long): Boolean = currentKey != null && issued && token == generation
    @Synchronized fun reset() { generation++; currentKey = null; issued = false }
}
