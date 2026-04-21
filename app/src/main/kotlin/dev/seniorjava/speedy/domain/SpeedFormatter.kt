package dev.seniorjava.speedy.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Formats bits-per-second values into human-readable forms.
 *
 * Thresholds per spec:
 *   - `< 1 000 bps`                         → bps   (integer)
 *   - `1 000 .. 999 999 bps`                → Kbps  (integer)
 *   - `1 000 000 .. 999 999 999 bps`        → Mbps  (integer)
 *   - `>= 1 000 000 000 bps`                → Gbps  (1 decimal allowed, e.g. "1.2G")
 *
 * Two output shapes:
 *   - [formatCompact] → status-bar icon ("14M", "1.2G", max 4 chars)
 *   - [formatFull]    → notification body ("14 Mbps")
 */
@Singleton
class SpeedFormatter @Inject constructor() {

    fun formatCompact(bitsPerSecond: Long): String {
        val bps = bitsPerSecond.coerceAtLeast(0L)
        return when {
            bps < KILO -> "${bps}b"
            bps < MEGA -> "${bps / KILO}K"
            bps < GIGA -> "${bps / MEGA}M"
            else -> formatGigaCompact(bps)
        }
    }

    fun formatFull(bitsPerSecond: Long): String {
        val bps = bitsPerSecond.coerceAtLeast(0L)
        return when {
            bps < KILO -> "$bps bps"
            bps < MEGA -> "${bps / KILO} Kbps"
            bps < GIGA -> "${bps / MEGA} Mbps"
            else -> "${formatGigaValue(bps)} Gbps"
        }
    }

    /**
     * Gbps compact form — "1G", "1.2G". Capped at 4 characters (drops the
     * decimal once the integer part is >= 10) so the status bar Bitmap stays
     * legible at 48x48 px.
     */
    private fun formatGigaCompact(bps: Long): String = "${formatGigaValue(bps)}G"

    private fun formatGigaValue(bps: Long): String {
        val integer = bps / GIGA
        return if (integer >= 10L) {
            // 10G+ → drop decimal to keep 4-char ceiling (e.g. "10G", "999G").
            integer.toString()
        } else {
            // 0..9 Gbps — keep one decimal from the tenths slot (rounded down).
            val tenths = (bps % GIGA) / (GIGA / 10L)
            if (tenths == 0L) integer.toString() else "$integer.$tenths"
        }
    }

    private companion object {
        const val KILO = 1_000L
        const val MEGA = 1_000_000L
        const val GIGA = 1_000_000_000L
    }
}
