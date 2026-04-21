package dev.seniorjava.speedy.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes bits-per-second from successive traffic byte counters with
 * drift-corrected delta (uses the real elapsed-ms, not the assumed 1000 ms).
 *
 * Edge cases per spec:
 *   - First tick: no previous sample → emit ZERO, store bytes.
 *   - currentBytes < previousBytes (counter reset / overflow) → ZERO, re-store.
 *   - elapsedMs <= 0 → ZERO (defensive).
 *
 * The class is stateful: one instance per monitoring session. Keep a single
 * Hilt-scoped instance for the service, and call [reset] when the service stops.
 */
@Singleton
class SpeedCalculator @Inject constructor() {

    private var lastRxBytes: Long = UNSET
    private var lastTxBytes: Long = UNSET
    private var lastTimestampMs: Long = UNSET

    /** Compute throughput from the current counter readings at [nowMs]. */
    fun sample(rxBytes: Long, txBytes: Long, nowMs: Long): SpeedSample {
        val previousRx = lastRxBytes
        val previousTx = lastTxBytes
        val previousMs = lastTimestampMs

        lastRxBytes = rxBytes
        lastTxBytes = txBytes
        lastTimestampMs = nowMs

        if (previousRx == UNSET || previousTx == UNSET || previousMs == UNSET) {
            return SpeedSample.ZERO
        }

        val elapsedMs = nowMs - previousMs
        if (elapsedMs <= 0L) return SpeedSample.ZERO

        val rxDelta = rxBytes - previousRx
        val txDelta = txBytes - previousTx
        if (rxDelta < 0L || txDelta < 0L) return SpeedSample.ZERO

        return SpeedSample(
            downloadBps = rxDelta.toBitsPerSecond(elapsedMs),
            uploadBps = txDelta.toBitsPerSecond(elapsedMs),
        )
    }

    fun reset() {
        lastRxBytes = UNSET
        lastTxBytes = UNSET
        lastTimestampMs = UNSET
    }

    private fun Long.toBitsPerSecond(elapsedMs: Long): Long =
        this * BITS_PER_BYTE * MS_PER_SECOND / elapsedMs

    private companion object {
        const val UNSET = -1L
        const val BITS_PER_BYTE = 8L
        const val MS_PER_SECOND = 1_000L
    }
}
