package io.github.coollogin.smuzy.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

class SpectrumAnalyzerTest {

    private val sampleRate = 48_000
    private val bandCount = 32

    @Test
    fun sineFallsIntoMatchingBand() {
        val analyzer = SpectrumAnalyzer(bandCount)
        val samples = FloatArray(1024) { index ->
            sin(2.0 * PI * 1000.0 * index / sampleRate).toFloat()
        }
        val bands = FloatArray(bandCount)

        analyzer.analyze(samples, samples.size, sampleRate, bands)

        val expectedBand = expectedBandFor(1000.0, sampleRate)
        val peakBand = bands.indices.maxBy { bands[it] }
        assertEquals(expectedBand, peakBand)
        assertTrue("peak level ${bands[peakBand]}", bands[peakBand] > 0.7f)
    }

    @Test
    fun silenceStaysFlat() {
        val analyzer = SpectrumAnalyzer(bandCount)
        val samples = FloatArray(1024)
        val bands = FloatArray(bandCount) { 1f }

        analyzer.analyze(samples, samples.size, sampleRate, bands)

        assertTrue(bands.all { it < 0.01f })
    }

    @Test
    fun outputStaysNormalized() {
        val analyzer = SpectrumAnalyzer(bandCount)
        val samples = FloatArray(1024) { index ->
            (sin(2.0 * PI * 440.0 * index / sampleRate) * 0.9 +
                sin(2.0 * PI * 6000.0 * index / sampleRate) * 0.9).toFloat()
        }
        val bands = FloatArray(bandCount)

        analyzer.analyze(samples, samples.size, sampleRate, bands)

        assertTrue(bands.all { it in 0f..1f })
        assertTrue(bands.any { it > 0.5f })
    }

    private fun expectedBandFor(frequency: Double, sampleRate: Int): Int {
        val maxFrequency = minOf(16_000.0, sampleRate * 0.49)
        val position = ln(frequency / 40.0) / ln(maxFrequency / 40.0) * bandCount
        return position.toInt().coerceIn(0, bandCount - 1)
    }
}
