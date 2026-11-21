package io.github.coollogin.smuzy.visualizer

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Process wide bridge between the playback thread that taps PCM and the player UI that analyses it.
 * Both live in the same process, so no binder or service plumbing is needed.
 */
object SpectrumTapData {
    const val CAPTURE_SIZE = 1024
    private const val RING_SIZE = 8192

    private val lock = Any()
    private val ring = FloatArray(RING_SIZE)
    private var writePos = 0
    private var available = 0
    @Volatile private var sampleRateHz = 48_000
    @Volatile private var lastWriteMs = 0L
    private val consumers = AtomicInteger(0)

    val sampleRate: Int get() = sampleRateHz

    fun addConsumer() {
        consumers.incrementAndGet()
    }

    fun removeConsumer() {
        consumers.decrementAndGet()
    }

    fun hasConsumers(): Boolean = consumers.get() > 0

    fun onFormat(sampleRate: Int) {
        synchronized(lock) {
            if (sampleRateHz != sampleRate) {
                sampleRateHz = sampleRate
                writePos = 0
                available = 0
                lastWriteMs = 0L
            }
        }
    }

    fun write(samples: FloatArray, count: Int) {
        if (count <= 0) return
        synchronized(lock) {
            val n = minOf(count, RING_SIZE)
            val start = count - n
            for (i in 0 until n) {
                ring[(writePos + i) % RING_SIZE] = samples[start + i]
            }
            writePos = (writePos + n) % RING_SIZE
            available = minOf(available + n, RING_SIZE)
            lastWriteMs = SystemClock.elapsedRealtime()
        }
    }

    fun copyLatest(out: FloatArray): Int {
        synchronized(lock) {
            val count = minOf(out.size, available)
            if (count == 0) return 0
            var pos = writePos - count
            if (pos < 0) pos += RING_SIZE
            val first = minOf(count, RING_SIZE - pos)
            System.arraycopy(ring, pos, out, 0, first)
            if (first < count) {
                System.arraycopy(ring, 0, out, first, count - first)
            }
            return count
        }
    }

    fun millisSinceLastWrite(): Long = SystemClock.elapsedRealtime() - lastWriteMs

    fun clear() {
        synchronized(lock) {
            writePos = 0
            available = 0
            lastWriteMs = 0L
        }
    }
}

/**
 * Copies the decoded PCM through untouched while the visualizer is wanted. It only downmixes the
 * frames; analysis happens on the UI side at a display friendly rate.
 */
@UnstableApi
class SpectrumTapProcessor : BaseAudioProcessor() {
    @Volatile var enabled = false

    private var mono = FloatArray(2048)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            return AudioFormat.NOT_SET
        }
        SpectrumTapData.onFormat(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun onReset() {
        SpectrumTapData.clear()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val output = replaceOutputBuffer(remaining)
        if (!enabled || !SpectrumTapData.hasConsumers()) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())
        val channels = inputAudioFormat.channelCount
        if (channels <= 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> tapShorts(inputBuffer, output, channels)
            C.ENCODING_PCM_FLOAT -> tapFloats(inputBuffer, output, channels)
            else -> output.put(inputBuffer)
        }
        output.flip()
    }

    private fun tapShorts(input: ByteBuffer, output: ByteBuffer, channels: Int) {
        val frames = input.remaining() / (2 * channels)
        ensureMonoBuffer(frames)
        var channel = 0
        var sum = 0f
        var monoIndex = 0
        while (input.remaining() >= 2) {
            val sample = input.getShort()
            output.putShort(sample)
            sum += sample
            if (++channel == channels) {
                mono[monoIndex++] = sum / (channels * 32768f)
                sum = 0f
                channel = 0
            }
        }
        while (input.hasRemaining()) output.put(input.get())
        SpectrumTapData.write(mono, monoIndex)
    }

    private fun tapFloats(input: ByteBuffer, output: ByteBuffer, channels: Int) {
        val frames = input.remaining() / (4 * channels)
        ensureMonoBuffer(frames)
        var channel = 0
        var sum = 0f
        var monoIndex = 0
        while (input.remaining() >= 4) {
            val sample = input.getFloat()
            output.putFloat(sample)
            sum += sample
            if (++channel == channels) {
                mono[monoIndex++] = sum / channels
                sum = 0f
                channel = 0
            }
        }
        while (input.hasRemaining()) output.put(input.get())
        SpectrumTapData.write(mono, monoIndex)
    }

    private fun ensureMonoBuffer(size: Int) {
        if (mono.size < size) mono = FloatArray(max(size, mono.size * 2))
    }
}
