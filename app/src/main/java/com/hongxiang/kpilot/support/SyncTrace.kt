package com.hongxiang.kpilot.support

import android.util.Log

/**
 * Stage timing for Kindle sync paths. Logs one line per stage to the KPilotPerf tag,
 * with both the stage duration and the elapsed time since the sync started, so a run
 * can be compared against another build without relying on logcat timestamps.
 *
 * Uses System.nanoTime so the same code is safe in local JVM tests; logcat writes are
 * best-effort so plain unit tests without Android logging stubs never fail here.
 */
class SyncTrace private constructor(private val name: String) {
    private val startedAt = now()
    private var lastAt = startedAt

    /** Marks the section that just finished, measured from the previous mark. */
    fun mark(stage: String, details: String = "") {
        val current = now()
        emit(stage, current - lastAt, current - startedAt, details)
        lastAt = current
    }

    fun <T> stage(stage: String, block: () -> T): T {
        val before = now()
        try {
            return block()
        } finally {
            val current = now()
            emit(stage, current - before, current - startedAt, "")
            lastAt = current
        }
    }

    fun end(details: String = "") {
        val current = now()
        emit("total", current - startedAt, current - startedAt, details)
    }

    private fun emit(stage: String, ms: Long, elapsed: Long, details: String) {
        val line = buildString {
            append("sync=").append(name)
            append(" stage=").append(stage)
            append(" ms=").append(ms)
            append(" elapsed=").append(elapsed)
            if (details.isNotBlank()) append(' ').append(details)
        }
        runCatching { Log.i(TAG, line) }
    }

    companion object {
        const val TAG = "KPilotPerf"

        fun begin(name: String) = SyncTrace(name)

        private fun now(): Long = System.nanoTime() / 1_000_000
    }
}
