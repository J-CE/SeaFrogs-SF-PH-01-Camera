package de.jce.seafrogs

/** Only distinct, fresh preview frames can confirm focus. No image sharpness claim. */
class AutofocusStability {
    private var lastTimestamp: Long? = null
    private var lastReceivedAt: Long? = null
    private var focusedSince: Long? = null
    private var frames = 0

    fun reset() {
        lastTimestamp = null
        lastReceivedAt = null
        focusedSince = null
        frames = 0
    }

    fun frame(focused: Boolean?, timestamp: Long?, now: Long) {
        if (timestamp == null) { reset(); return }
        if (timestamp == lastTimestamp) return
        if (lastReceivedAt?.let { now - it > MAX_AGE_MS } == true) {
            focusedSince = null
            frames = 0
        }
        lastTimestamp = timestamp
        lastReceivedAt = now
        if (focused != true) { focusedSince = null; frames = 0; return }
        if (focusedSince == null) focusedSince = now
        frames++
    }

    fun stable(now: Long): Boolean = focusedSince?.let { start ->
        frames >= 3 && now - start >= STABLE_MS &&
            lastReceivedAt?.let { now - it <= MAX_AGE_MS } == true
    } == true

    companion object {
        const val STABLE_MS = 400L
        const val MAX_AGE_MS = 500L
    }
}
