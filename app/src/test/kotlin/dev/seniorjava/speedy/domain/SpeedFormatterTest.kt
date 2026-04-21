package dev.seniorjava.speedy.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeedFormatterTest {
    private val formatter = SpeedFormatter()

    // ---- compact (status-bar) --------------------------------------------

    @Test fun `compact zero returns 0b`() {
        assertThat(formatter.formatCompact(0L)).isEqualTo("0b")
    }

    @Test fun `compact negative is clamped to zero`() {
        assertThat(formatter.formatCompact(-123L)).isEqualTo("0b")
    }

    @Test fun `compact just under 1 Kbps stays in bps`() {
        assertThat(formatter.formatCompact(999L)).isEqualTo("999b")
    }

    @Test fun `compact rounds Kbps to integer`() {
        assertThat(formatter.formatCompact(1_000L)).isEqualTo("1K")
        assertThat(formatter.formatCompact(1_500L)).isEqualTo("1K") // integer rounding
        assertThat(formatter.formatCompact(14_900L)).isEqualTo("14K")
    }

    @Test fun `compact just under 1 Mbps stays in Kbps`() {
        assertThat(formatter.formatCompact(999_999L)).isEqualTo("999K")
    }

    @Test fun `compact rounds Mbps to integer`() {
        assertThat(formatter.formatCompact(1_000_000L)).isEqualTo("1M")
        assertThat(formatter.formatCompact(14_000_000L)).isEqualTo("14M")
        assertThat(formatter.formatCompact(123_456_789L)).isEqualTo("123M")
    }

    @Test fun `compact just under 1 Gbps stays in Mbps`() {
        assertThat(formatter.formatCompact(999_999_999L)).isEqualTo("999M")
    }

    @Test fun `compact Gbps allows one decimal below 10`() {
        assertThat(formatter.formatCompact(1_000_000_000L)).isEqualTo("1G")
        assertThat(formatter.formatCompact(1_200_000_000L)).isEqualTo("1.2G")
        assertThat(formatter.formatCompact(9_800_000_000L)).isEqualTo("9.8G")
    }

    @Test fun `compact Gbps drops decimal at 10 and above`() {
        assertThat(formatter.formatCompact(10_000_000_000L)).isEqualTo("10G")
        assertThat(formatter.formatCompact(12_300_000_000L)).isEqualTo("12G")
    }

    @Test fun `compact output never exceeds 4 characters`() {
        val values = longArrayOf(
            0L, 1L, 999L, 1_000L, 999_999L, 1_000_000L, 999_999_999L,
            1_000_000_000L, 1_200_000_000L, 9_900_000_000L, 999_000_000_000L,
        )
        for (v in values) {
            assertThat(formatter.formatCompact(v).length).isAtMost(4)
        }
    }

    // ---- full (notification) ---------------------------------------------

    @Test fun `full zero returns 0 bps`() {
        assertThat(formatter.formatFull(0L)).isEqualTo("0 bps")
    }

    @Test fun `full Kbps threshold`() {
        assertThat(formatter.formatFull(1_000L)).isEqualTo("1 Kbps")
        assertThat(formatter.formatFull(999_999L)).isEqualTo("999 Kbps")
    }

    @Test fun `full Mbps threshold`() {
        assertThat(formatter.formatFull(14_000_000L)).isEqualTo("14 Mbps")
    }

    @Test fun `full Gbps with decimal below 10`() {
        assertThat(formatter.formatFull(1_200_000_000L)).isEqualTo("1.2 Gbps")
    }
}
