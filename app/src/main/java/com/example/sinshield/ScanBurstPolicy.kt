package com.example.sinshield

/** Bounds polling after an accessibility event instead of scanning a quiet screen forever. */
internal class ScanBurstPolicy(private val followUpScans: Int = 2) {
    private var remaining = 0

    init {
        require(followUpScans >= 0)
    }

    fun noteEvent() {
        remaining = followUpScans
    }

    fun shouldSchedule(forceFast: Boolean, framePending: Boolean): Boolean {
        if (forceFast || framePending) return true
        if (remaining == 0) return false
        remaining--
        return true
    }
}
