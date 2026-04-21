package dev.seniorjava.speedy.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class SpeedCalculatorTest {

    private lateinit var calculator: SpeedCalculator

    @Before fun setUp() {
        calculator = SpeedCalculator()
    }

    @Test fun `first sample returns zero and stores baseline`() {
        val sample = calculator.sample(rxBytes = 1_000L, txBytes = 500L, nowMs = 0L)
        assertThat(sample).isEqualTo(SpeedSample.ZERO)
    }

    @Test fun `second sample at exact 1 second reports bytes times 8`() {
        calculator.sample(rxBytes = 0L, txBytes = 0L, nowMs = 0L)
        val sample = calculator.sample(rxBytes = 125L, txBytes = 50L, nowMs = 1_000L)
        // 125 bytes * 8 = 1_000 bps, 50 * 8 = 400 bps.
        assertThat(sample.downloadBps).isEqualTo(1_000L)
        assertThat(sample.uploadBps).isEqualTo(400L)
    }

    @Test fun `drift correction uses actual elapsed ms, not assumed 1000 ms`() {
        calculator.sample(rxBytes = 0L, txBytes = 0L, nowMs = 0L)
        // 1 kB arrived in 2050 ms → should report ~3902 bps, not 8000 bps.
        val sample = calculator.sample(rxBytes = 1_000L, txBytes = 0L, nowMs = 2_050L)
        assertThat(sample.downloadBps).isEqualTo(1_000L * 8L * 1_000L / 2_050L)
    }

    @Test fun `negative delta (counter reset) is treated as first tick`() {
        calculator.sample(rxBytes = 10_000L, txBytes = 5_000L, nowMs = 0L)
        // Counter rolls back (e.g. TrafficStats reset on interface flap).
        val sample = calculator.sample(rxBytes = 500L, txBytes = 200L, nowMs = 1_000L)
        assertThat(sample).isEqualTo(SpeedSample.ZERO)
        // After reset, calculator should use the new lower bytes as the baseline.
        val next = calculator.sample(rxBytes = 600L, txBytes = 200L, nowMs = 2_000L)
        assertThat(next.downloadBps).isEqualTo(100L * 8L) // 800 bps
    }

    @Test fun `zero elapsed ms yields zero`() {
        calculator.sample(rxBytes = 0L, txBytes = 0L, nowMs = 1_000L)
        val sample = calculator.sample(rxBytes = 500L, txBytes = 500L, nowMs = 1_000L)
        assertThat(sample).isEqualTo(SpeedSample.ZERO)
    }

    @Test fun `reset clears state so next sample is treated as first`() {
        calculator.sample(rxBytes = 0L, txBytes = 0L, nowMs = 0L)
        calculator.reset()
        val sample = calculator.sample(rxBytes = 1_000L, txBytes = 1_000L, nowMs = 1_000L)
        assertThat(sample).isEqualTo(SpeedSample.ZERO)
    }

    @Test fun `only one counter negative still returns zero`() {
        calculator.sample(rxBytes = 0L, txBytes = 10_000L, nowMs = 0L)
        val sample = calculator.sample(rxBytes = 1_000L, txBytes = 5_000L, nowMs = 1_000L)
        assertThat(sample).isEqualTo(SpeedSample.ZERO)
    }
}
