package io.github.coollogin.smuzy.visualizer

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns the small mono capture from [SpectrumTapData] into logarithmic band levels. It is pure
 * Kotlin so the band mapping and FFT can be unit tested on the JVM.
 */
class SpectrumAnalyzer(private val bandCount: Int = 32) {
    private companion object {
        const val FFT_SIZE = 2048
        const val MIN_FREQUENCY = 40.0
        const val MAX_FREQUENCY = 16_000.0
        const val DB_FLOOR = -60.0
        // Headroom above 0 dBFS so the band tilt cannot clip a loud tone at the ceiling.
        const val DB_CEILING = 20.0
        // A Hann window spreads a sine over three main-lobe bins carrying ~1.5x the peak power.
        const val LOBE_POWER_GAIN = 1.5
        // Music loses roughly 1 dB per band towards the top; the tilt puts instruments on the
        // same footing so a hi-hat can stand above a bass line.
        const val TILT_DB_PER_BAND = 0.8
    }

    private val window = DoubleArray(FFT_SIZE) {
        0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1))
    }
    private val re = DoubleArray(FFT_SIZE)
    private val im = DoubleArray(FFT_SIZE)
    private val power = DoubleArray(FFT_SIZE / 2)
    private val bandLow = DoubleArray(bandCount)
    private val bandHigh = DoubleArray(bandCount)
    private var cachedRate = 0

    init {
        require(bandCount > 0) { "bandCount must be positive" }
    }

    fun analyze(samples: FloatArray, count: Int, sampleRate: Int, out: FloatArray) {
        if (count <= 0 || sampleRate <= 0 || out.isEmpty()) {
            out.fill(0f)
            return
        }
        if (sampleRate != cachedRate) {
            rebuildBands(sampleRate)
            cachedRate = sampleRate
        }

        val used = min(count, FFT_SIZE)
        val offset = count - used
        val padding = FFT_SIZE - used
        for (i in 0 until FFT_SIZE) {
            val sample = if (i >= padding) samples[offset + (i - padding)] else 0f
            re[i] = sample * window[i]
            im[i] = 0.0
        }
        fft(re, im)

        val half = FFT_SIZE / 2
        for (bin in 1 until half) {
            power[bin] = re[bin] * re[bin] + im[bin] * im[bin]
        }
        power[0] = 0.0

        val bands = min(bandCount, out.size)
        val scale = 4.0 / (FFT_SIZE * sqrt(LOBE_POWER_GAIN))
        for (band in 0 until bands) {
            val low = bandLow[band]
            val high = bandHigh[band]
            // The strongest bin, not the band total: a tonal peak should read the same in a wide
            // high band as in a narrow low one, and the broadband "carpet" should stay low.
            val magnitude = sqrt(bandPeak(low, high)) * scale
            val db = 20.0 * log10(magnitude + 1e-9) + band * TILT_DB_PER_BAND
            out[band] = ((db - DB_FLOOR) / (DB_CEILING - DB_FLOOR)).coerceIn(0.0, 1.0).toFloat()
        }
        for (band in bands until out.size) out[band] = 0f
    }

    private fun bandPeak(low: Double, high: Double): Double {
        var peak = 0.0
        var bin = ceil(low).toInt()
        while (bin < high) {
            val p = power[bin.coerceIn(0, power.size - 1)]
            if (p > peak) peak = p
            bin++
        }
        // Narrow low bands can be thinner than a single bin.
        if (peak == 0.0) peak = powerAt((low + high) * 0.5)
        return peak
    }

    private fun powerAt(position: Double): Double {
        val bin = position.toInt().coerceIn(0, power.size - 2)
        val fraction = position - bin
        return power[bin] + (power[bin + 1] - power[bin]) * fraction
    }

    private fun rebuildBands(sampleRate: Int) {
        val maxFrequency = min(MAX_FREQUENCY, sampleRate * 0.49)
        val ratio = maxFrequency / MIN_FREQUENCY
        val binsPerHz = FFT_SIZE.toDouble() / sampleRate
        for (band in 0 until bandCount) {
            bandLow[band] = MIN_FREQUENCY * ratio.pow(band.toDouble() / bandCount) * binsPerHz
            bandHigh[band] = MIN_FREQUENCY * ratio.pow((band + 1).toDouble() / bandCount) * binsPerHz
        }
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var tmp = re[i]
                re[i] = re[j]
                re[j] = tmp
                tmp = im[i]
                im[i] = im[j]
                im[j] = tmp
            }
        }

        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val stepRe = cos(angle)
            val stepIm = sin(angle)
            var start = 0
            while (start < n) {
                var wRe = 1.0
                var wIm = 0.0
                val half = length / 2
                for (k in 0 until half) {
                    val evenRe = re[start + k]
                    val evenIm = im[start + k]
                    val oddRe = re[start + k + half] * wRe - im[start + k + half] * wIm
                    val oddIm = re[start + k + half] * wIm + im[start + k + half] * wRe
                    re[start + k] = evenRe + oddRe
                    im[start + k] = evenIm + oddIm
                    re[start + k + half] = evenRe - oddRe
                    im[start + k + half] = evenIm - oddIm
                    val nextRe = wRe * stepRe - wIm * stepIm
                    wIm = wRe * stepIm + wIm * stepRe
                    wRe = nextRe
                }
                start += length
            }
            length = length shl 1
        }
    }
}
