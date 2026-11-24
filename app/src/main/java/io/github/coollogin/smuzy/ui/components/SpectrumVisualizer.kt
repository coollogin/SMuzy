package io.github.coollogin.smuzy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import io.github.coollogin.smuzy.ui.queue.rememberAppColorScheme
import io.github.coollogin.smuzy.visualizer.SpectrumAnalyzer
import io.github.coollogin.smuzy.visualizer.SpectrumTapData
import io.github.coollogin.smuzy.visualizer.VisualizerMode
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val BANDS = 32
private const val ANALYSIS_INTERVAL_NANOS = 16_000_000L
private const val FRESH_DATA_MS = 400L
// Display window below the current frame peak. Music fills every band at once, so mapping the
// absolute level makes the whole strip glow; a relative window leaves only the bands that
// actually dominate right now standing out.
private const val DISPLAY_SPAN = 0.3f
private const val PEAK_ATTACK = 22f
private const val PEAK_RELEASE_SECONDS = 1.4f

@Composable
fun SpectrumVisualizer(
    isPlaying: Boolean,
    mode: VisualizerMode,
    modifier: Modifier = Modifier
) {
    val analyzer = remember { SpectrumAnalyzer(BANDS) }
    val capture = remember { FloatArray(SpectrumTapData.CAPTURE_SIZE) }
    val target = remember { FloatArray(BANDS) }
    val shown = remember { FloatArray(BANDS) }
    var displayed by remember { mutableStateOf(FloatArray(BANDS)) }
    var alpha by remember { mutableFloatStateOf(0f) }
    val playing = rememberUpdatedState(isPlaying)
    val accent = rememberAppColorScheme().primary

    DisposableEffect(Unit) {
        SpectrumTapData.addConsumer()
        onDispose { SpectrumTapData.removeConsumer() }
    }

    LaunchedEffect(Unit) {
        var lastFrame = 0L
        var lastAnalysis = 0L
        var peakRef = 0f
        while (isActive) {
            val playingNow = playing.value
            val fresh = SpectrumTapData.millisSinceLastWrite() < FRESH_DATA_MS
            if ((playingNow && fresh) || alpha > 0.01f) {
                withFrameNanos { now ->
                    val dt = if (lastFrame == 0L) 0.016f
                    else ((now - lastFrame) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    lastFrame = now

                    if (playingNow && fresh) {
                        if (now - lastAnalysis >= ANALYSIS_INTERVAL_NANOS) {
                            lastAnalysis = now
                            val count = SpectrumTapData.copyLatest(capture)
                            if (count > 0) {
                                analyzer.analyze(capture, count, SpectrumTapData.sampleRate, target)
                            } else {
                                target.fill(0f)
                            }
                        }
                    } else {
                        target.fill(0f)
                    }

                    val targetAlpha = if (playingNow && fresh) 1f else 0f
                    alpha += (targetAlpha - alpha) * (1f - exp(-dt * 9f))

                    var peak = 0f
                    for (band in 0 until BANDS) {
                        if (target[band] > peak) peak = target[band]
                    }
                    peakRef = if (peak > peakRef) {
                        peakRef + (peak - peakRef) * (1f - exp(-dt * PEAK_ATTACK))
                    } else {
                        peakRef * exp(-dt / PEAK_RELEASE_SECONDS)
                    }
                    val windowFloor = (peakRef - DISPLAY_SPAN).coerceAtLeast(0f)

                    for (band in 0 until BANDS) {
                        val level = ((target[band] - windowFloor) / DISPLAY_SPAN).coerceIn(0f, 1f)
                        val speed = if (level > shown[band]) 58f else 9f
                        shown[band] += (level - shown[band]) * (1f - exp(-dt * speed))
                    }
                    displayed = shown.copyOf()
                }
            } else {
                delay(200)
            }

            if (!playingNow && alpha < 0.01f && displayed.any { it > 0.01f }) {
                alpha = 0f
                peakRef = 0f
                shown.fill(0f)
                displayed = FloatArray(BANDS)
            }
        }
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        val bands = displayed
        if (bands.isEmpty()) return@Canvas
        when (mode) {
            VisualizerMode.PEDESTAL -> drawPedestal(bands, accent)
            VisualizerMode.WAVE -> drawWave(bands, accent)
        }
    }
}

private fun DrawScope.drawPedestal(bands: FloatArray, accent: Color) {
    val axis = size.height / 2f
    val maxAmplitude = size.height / 2f - size.height * 0.06f
    val gap = max(1.2f, size.width * 0.0105f)
    val barWidth = max(1.4f, (size.width - gap * (bands.size - 1)) / bands.size)
    val radius = min(barWidth / 2f, size.height * 0.09f)
    val glow = accent.copy(alpha = 0.07f)

    drawLine(
        brush = Brush.horizontalGradient(
            0f to accent.copy(alpha = 0f),
            0.5f to accent.copy(alpha = 0.3f),
            1f to accent.copy(alpha = 0f)
        ),
        start = Offset(0f, axis),
        end = Offset(size.width, axis),
        strokeWidth = max(1f, size.height * 0.016f)
    )

    val brush = Brush.verticalGradient(
        colors = listOf(
            accent.copy(alpha = 0.2f),
            accent,
            accent.copy(alpha = 0.2f)
        ),
        startY = axis - maxAmplitude,
        endY = axis + maxAmplitude
    )

    var x = 0f
    for (band in bands.indices) {
        val amplitude = max(1.2f, bands[band].coerceIn(0f, 1f) * maxAmplitude)
        drawRoundRect(
            color = glow,
            topLeft = Offset(x - 1f, axis - amplitude - 2f),
            size = Size(barWidth + 2f, amplitude * 2f + 4f),
            cornerRadius = CornerRadius(radius + 2f)
        )
        drawRoundRect(
            brush = brush,
            topLeft = Offset(x, axis - amplitude),
            size = Size(barWidth, amplitude * 2f),
            cornerRadius = CornerRadius(radius)
        )
        x += barWidth + gap
    }

    drawEdgeFade()
}

private fun DrawScope.drawWave(bands: FloatArray, accent: Color) {
    if (bands.size < 2) return
    val pad = 2f
    val axis = size.height / 2f
    val maxAmplitude = size.height / 2f - size.height * 0.09f
    val step = (size.width - pad * 2f) / (bands.size - 1)
    val xs = FloatArray(bands.size)
    val top = FloatArray(bands.size)
    val bottom = FloatArray(bands.size)
    for (band in bands.indices) {
        xs[band] = pad + step * band
        val amplitude = max(0.6f, bands[band].coerceIn(0f, 1f) * maxAmplitude)
        top[band] = axis - amplitude
        bottom[band] = axis + amplitude
    }

    drawPath(
        path = buildWaveFill(xs, top, bottom),
        brush = Brush.verticalGradient(
            colors = listOf(
                accent.copy(alpha = 0.08f),
                accent.copy(alpha = 0.55f),
                accent.copy(alpha = 0.08f)
            ),
            startY = axis - maxAmplitude,
            endY = axis + maxAmplitude
        )
    )

    val topLine = buildCurve(xs, top)
    drawLine(
        brush = Brush.horizontalGradient(
            0f to accent.copy(alpha = 0f),
            0.5f to accent.copy(alpha = 0.3f),
            1f to accent.copy(alpha = 0f)
        ),
        start = Offset(0f, axis),
        end = Offset(size.width, axis),
        strokeWidth = max(1f, size.height * 0.016f)
    )
    drawPath(
        path = topLine,
        color = accent.copy(alpha = 0.06f),
        style = Stroke(width = size.height * 0.24f)
    )
    drawPath(
        path = topLine,
        color = accent.copy(alpha = 0.12f),
        style = Stroke(width = size.height * 0.14f)
    )
    drawPath(
        path = topLine,
        color = accent.copy(alpha = 0.95f),
        style = Stroke(width = max(1.6f, size.height * 0.052f))
    )
    drawPath(
        path = buildCurve(xs, bottom),
        color = accent.copy(alpha = 0.3f),
        style = Stroke(width = max(1f, size.height * 0.035f))
    )

    drawEdgeFade(edge = 0.11f)
}

private fun DrawScope.drawEdgeFade(edge: Float = 0.1f) {
    drawRect(
        brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                edge to Color.Black,
                1f - edge to Color.Black,
                1f to Color.Transparent
            ),
            startX = 0f,
            endX = size.width
        ),
        size = size,
        blendMode = BlendMode.DstIn
    )
}

private fun buildCurve(xs: FloatArray, ys: FloatArray): Path {
    val path = Path()
    path.moveTo(xs[0], ys[0])
    for (i in 0 until xs.size - 1) {
        val first = if (i == 0) 0 else i - 1
        val second = i + 1
        val third = if (i + 2 < xs.size) i + 2 else xs.size - 1
        path.cubicTo(
            xs[i] + (xs[second] - xs[first]) / 6f,
            ys[i] + (ys[second] - ys[first]) / 6f,
            xs[second] - (xs[third] - xs[i]) / 6f,
            ys[second] - (ys[third] - ys[i]) / 6f,
            xs[second],
            ys[second]
        )
    }
    return path
}

private fun buildWaveFill(xs: FloatArray, top: FloatArray, bottom: FloatArray): Path {
    val path = Path()
    val last = xs.size - 1
    path.moveTo(xs[0], top[0])
    for (i in 0 until last) {
        val first = if (i == 0) 0 else i - 1
        val second = i + 1
        val third = if (i + 2 < xs.size) i + 2 else last
        path.cubicTo(
            xs[i] + (xs[second] - xs[first]) / 6f,
            top[i] + (top[second] - top[first]) / 6f,
            xs[second] - (xs[third] - xs[i]) / 6f,
            top[second] - (top[third] - top[i]) / 6f,
            xs[second],
            top[second]
        )
    }
    path.lineTo(xs[last], bottom[last])
    for (i in last downTo 1) {
        val second = i - 1
        val first = (i + 1).coerceAtMost(last)
        val third = (i - 2).coerceAtLeast(0)
        path.cubicTo(
            xs[i] + (xs[second] - xs[first]) / 6f,
            bottom[i] + (bottom[second] - bottom[first]) / 6f,
            xs[second] - (xs[third] - xs[i]) / 6f,
            bottom[second] - (bottom[third] - bottom[i]) / 6f,
            xs[second],
            bottom[second]
        )
    }
    path.close()
    return path
}
