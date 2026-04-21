package dev.seniorjava.speedy.domain

/**
 * Snapshot of current upload/download throughput in bits-per-second.
 *
 * Zero means "first tick" or "no traffic" — both are represented identically
 * because the spec treats overflow/reset as a first tick.
 */
data class SpeedSample(
    val downloadBps: Long,
    val uploadBps: Long,
) {
    companion object {
        val ZERO = SpeedSample(downloadBps = 0L, uploadBps = 0L)
    }
}
