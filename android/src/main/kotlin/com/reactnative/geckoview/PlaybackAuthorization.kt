package com.reactnative.geckoview

/** Native authorization survives delayed or coalesced JavaScript events. Main-thread only. */
class PlaybackAuthorization {
    private var hasKnownState = false
    private var safe = false
    var resumeRequired = false
        private set
    val allowed: Boolean get() = safe && !resumeRequired

    fun update(available: Boolean, restricted: Boolean, foreground: Boolean) {
        if (restricted || (!available && hasKnownState)) resumeRequired = true
        if (available) hasKnownState = true
        safe = available && !restricted && foreground
    }

    fun background() {
        safe = false
        resumeRequired = true
    }

    fun authorize(): Boolean {
        if (!safe) return false
        resumeRequired = false
        return true
    }
}
