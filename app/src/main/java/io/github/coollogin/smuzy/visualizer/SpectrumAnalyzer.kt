package io.github.coollogin.smuzy.visualizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns the small mono capture from [SpectrumTapData] into logarithmic band levels. It is pure
 * Kotlin so the band mapping and FFT can be unit tested on the JVM.
 */
class SpectrumAnalyzer(private val bandCount: Int = 32) {
    private companion object {
        const val FFT_SIZE = 1024
        const val MIN_FREQUENCY = 40.0
        const val MAX_FREQUENCY = 16_000.0
        const val DB_FLOOR = -60.0
    }

    private val window = DoubleArray(FFT_SIZE) {
        0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1))
    }
    private val re = DoubleArray(FFT_SIZE)
    private val im = DoubleArray(FFT_SIZE)
    private val bandStart = IntArray(bandCount)
    private val bandEnd = IntArray(bandCount)
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

        val bands = min(bandCount, out.size)
        for (band in 0 until bands) {
            var peakPower = 0.0
            for (bin in bandStart[band]..bandEnd[band]) {
                val power = re[bin] * re[bin] + im[bin] * im[bin]
                if (power > peakPower) peakPower = power
            }
            // The Hann window halves the coherent gain; 4/N restores a full scale sine to 0 dB.
            val magnitude = sqrt(peakPower) * 4.0 / FFT_SIZE
            val db = 20.0 * log10(magnitude + 1e-9)
            out[band] = ((db - DB_FLOOR) / -DB_FLOOR).coerceIn(0.0, 1.0).pow(0.8).toFloat()
        }
        for (band in bands until out.size) out[band] = 0f
    }

    private fun rebuildBands(sampleRate: Int) {
        val maxFrequency = min(MAX_FREQUENCY, sampleRate * 0.49)
        val ratio = maxFrequency / MIN_FREQUENCY
        // Bands must not share a boundary bin: a loud bin on the edge would otherwise light two
        // neighbouring bands equally and the peak level would become ambiguous.
        val boundaries = IntArray(bandCount + 1)
        for (i in 0..bandCount) {
            val frequency = MIN_FREQUENCY * ratio.pow(i.toDouble() / bandCount)
            boundaries[i] = (frequency / sampleRate * FFT_SIZE).roundToInt().coerceIn(1, FFT_SIZE / 2)
        }
        for (band in 0 until bandCount) {
            val start = boundaries[band]
            bandStart[band] = start
            bandEnd[band] = (boundaries[band + 1] - 1).coerceAtLeast(start)
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
