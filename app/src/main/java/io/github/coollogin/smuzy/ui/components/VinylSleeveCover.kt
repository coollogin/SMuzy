package io.github.coollogin.smuzy.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.signature.ObjectKey
import io.github.coollogin.smuzy.R
import io.github.coollogin.smuzy.glide.CustomGlideRequest
import io.github.coollogin.smuzy.util.Preferences
import io.github.coollogin.smuzy.util.RadioCoverArtDownloader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val REST_OUT = 0f
private const val PLAY_OUT = 0.50f
private const val DIP_OUT = 0.04f
private const val RECORD_FRACTION = 0.96f
private const val LABEL_FRACTION = 0.44f
private const val SPIN_DEG_PER_SEC = 45f
private const val SHUTTER_SEC = 1f / 25f
private val slideEasing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private val labelTypeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

private data class LabelState(
    val coverArtId: String?,
    val artworkUri: Uri?,
    val title: String?,
    val artist: String?
)

@Composable
fun VinylSleeveCover(
    coverArtId: String?,
    title: String?,
    isRadio: Boolean,
    radioArtworkUri: Uri?,
    isPlaying: Boolean,
    artist: String? = null,
    retractRequest: Int = 0,
    onRetracted: () -> Unit = {},
    expandRequest: Int = 0,
    layoutKey: Any? = null,
    modifier: Modifier = Modifier
) {
    var out by remember { mutableFloatStateOf(if (isPlaying) PLAY_OUT else REST_OUT) }
    var shown by remember { mutableStateOf(LabelState(coverArtId, radioArtworkUri, title, artist)) }

    // Frame-driven on purpose: Compose tweens are scaled by the system animator duration
    // setting (0 makes them instant), while the sleeve should move like the spin does.
    suspend fun slideTo(target: Float, durationMs: Int) {
        val start = out
        if (start == target) return
        val startNanos = withFrameNanos { it }
        while (true) {
            val finished = withFrameNanos { now ->
                val progress = ((now - startNanos) / 1_000_000f / durationMs).coerceIn(0f, 1f)
                out = start + (target - start) * slideEasing.transform(progress)
                progress >= 1f
            }
            if (finished) break
        }
    }

    // layoutKey changes when the visualizer strip appears or disappears. Re-apply the slide
    // target then, so a resize cannot leave the record stuck inside the sleeve.
    LaunchedEffect(isPlaying, coverArtId, radioArtworkUri, title, artist, layoutKey) {
        val incoming = LabelState(coverArtId, radioArtworkUri, title, artist)
        if (incoming != shown) {
            if (isPlaying) {
                // After a "next" request the record is already inside the sleeve, so there is
                // nothing to dip: slide the new record straight out once the data arrives.
                if (out > 0.001f) {
                    slideTo(DIP_OUT, 240)
                    delay(60)
                }
                shown = incoming
                slideTo(PLAY_OUT, 700)
            } else {
                slideTo(REST_OUT, 420)
                shown = incoming
            }
        } else {
            slideTo(if (isPlaying) PLAY_OUT else REST_OUT, if (isPlaying) 700 else 420)
        }
    }

    // "Next track": pull the record back into the sleeve before the skip is committed, so the
    // new artwork is revealed by a fresh slide-out instead of swapping mid-extension.
    LaunchedEffect(retractRequest) {
        if (retractRequest <= 0) return@LaunchedEffect
        slideTo(REST_OUT, 420)
        onRetracted()
    }

    // Fallback for a skip that changed nothing (e.g. a repeated single-track queue): the label
    // effect then never runs, so the caller explicitly asks for the record to come back out.
    LaunchedEffect(expandRequest) {
        if (expandRequest <= 0) return@LaunchedEffect
        if (isPlaying) slideTo(PLAY_OUT, 700)
    }

    var angle by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, layoutKey) {
        var last = 0L
        while (isActive && (isPlaying || speed > 0.5f)) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                val target = if (isPlaying) SPIN_DEG_PER_SEC else 0f
                speed += (target - speed) * (1f - exp(-dt * 2.5f))
                if (!isPlaying && speed < 0.5f) speed = 0f
                if (speed > 0f) angle = (angle + speed * dt) % 360f
            }
        }
    }

    val placeholderRes = if (isRadio) R.drawable.ic_placeholder_radio else R.drawable.ic_placeholder_song
    val cornerRadius = if (Preferences.isCornerRoundingEnabled()) {
        Preferences.getRoundedCornerSize().dp
    } else {
        6.dp
    }
    val sleeveShape = RoundedCornerShape(cornerRadius)
    val printText = remember(shown.artist, shown.title) {
        listOfNotNull(
            shown.artist?.trim()?.takeIf { it.isNotEmpty() },
            shown.title?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" · ").uppercase().take(32)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Sleeve + record at full extension must use exactly the cover area's footprint
        // (the same space as the non-animated cover).
        val case = min(maxWidth / (1f + PLAY_OUT), maxHeight * 0.98f)
        val record = case * RECORD_FRACTION
        val density = LocalDensity.current
        val casePx = with(density) { case.toPx() }
        val recordPx = with(density) { record.toPx() }.roundToInt()
        val marbling = remember(recordPx) { if (recordPx > 0) createMarblingBitmap(recordPx) else null }
        val labelPaint = remember {
            AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                textAlign = AndroidPaint.Align.CENTER
                typeface = labelTypeface
                letterSpacing = 0.06f
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(case)
                // Slides the whole sleeve left as the record comes out, keeping the
                // combined sleeve + record centered in the cover area.
                .graphicsLayer { translationX = -out * casePx * 0.5f }
        ) {
            Box(
                modifier = Modifier
                    .size(record)
                    .offset(x = case * 0.04f, y = case * 0.02f)
                    .graphicsLayer { translationX = out * casePx }
                    .clip(CircleShape)
            ) {
                VinylDisc(
                    angle = { angle },
                    speedDeg = { speed },
                    printText = printText,
                    paint = labelPaint,
                    marbling = marbling,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Artwork(
                        coverArtId = shown.coverArtId,
                        artworkUri = shown.artworkUri,
                        isRadio = isRadio,
                        placeholderRes = placeholderRes,
                        contentDescription = shown.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Keeps the record from showing through the sleeve's rounded corners.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF141414), sleeveShape)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(case)
                    .shadow(elevation = 12.dp, shape = sleeveShape)
                    .clip(sleeveShape)
            ) {
                Artwork(
                    coverArtId = shown.coverArtId,
                    artworkUri = shown.artworkUri,
                    isRadio = isRadio,
                    placeholderRes = placeholderRes,
                    contentDescription = shown.title,
                    modifier = Modifier.fillMaxSize()
                )
                SleeveShading(cornerRadius = cornerRadius)
            }
        }
    }
}

@Composable
private fun VinylDisc(
    angle: () -> Float,
    speedDeg: () -> Float,
    printText: String,
    paint: AndroidPaint,
    marbling: ImageBitmap?,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF262729),
                        0.40f to Color(0xFF262729),
                        0.52f to Color(0xFF2A2B2D),
                        0.70f to Color(0xFF303133),
                        0.86f to Color(0xFF38383B),
                        0.97f to Color(0xFF3E3D41),
                        0.984f to Color(0xFF242426),
                        0.993f to Color(0xFF545256),
                        1f to Color(0xFF545256)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius
            )
        }

        // Motion blur: the printed label is echoed a few times over the shutter.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spread = speedDeg() * SHUTTER_SEC
            if (spread > 0.02f) {
                val copies = 4
                for (k in 1 until copies) {
                    rotate(angle() - spread * k / (copies - 1)) {
                        drawLabelPrint(printText, paint, alpha = 0.30f / k)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle() }
        ) {
            if (marbling != null) {
                Image(
                    bitmap = marbling,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    alpha = 0.9f,
                    filterQuality = FilterQuality.Medium
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(LABEL_FRACTION)
                    .clip(CircleShape)
            ) {
                label()
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLabelPrint(printText, paint, alpha = 1f)
            }
        }

        // Grooves and the light on them stay put while the record turns.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGrooves()
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSpindle()
        }
    }
}

private fun DrawScope.drawLabelPrint(text: String, paint: AndroidPaint, alpha: Float) {
    val radius = size.minDimension / 2f
    val labelRadius = radius * LABEL_FRACTION
    val labelCenter = center
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.60f to Color.Transparent,
                0.82f to Color.Black.copy(alpha = 0.30f * alpha),
                1f to Color.Black.copy(alpha = 0.42f * alpha)
            ),
            center = labelCenter,
            radius = labelRadius
        ),
        radius = labelRadius
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.45f * alpha),
        radius = labelRadius * 0.93f,
        style = Stroke(width = radius * 0.005f)
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.22f * alpha),
        radius = labelRadius * 0.20f
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.14f * alpha),
        radius = labelRadius * 0.20f,
        style = Stroke(width = radius * 0.004f)
    )
    if (text.isNotEmpty()) {
        paint.color = android.graphics.Color.argb((255 * 0.88f * alpha).roundToInt().coerceIn(0, 255), 255, 255, 255)
        paint.textSize = labelRadius * 0.115f
        drawTextOnArc(text, paint, labelCenter, labelRadius * 0.81f)
    }
    paint.color = android.graphics.Color.argb((200 * alpha).roundToInt().coerceIn(0, 255), 255, 255, 255)
    paint.textSize = labelRadius * 0.16f
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText("33⅓", labelCenter.x, labelCenter.y + labelRadius * 0.72f, paint)
    }
}

private fun DrawScope.drawTextOnArc(text: String, paint: AndroidPaint, pivot: Offset, radius: Float) {
    val widths = FloatArray(text.length)
    var totalWidth = 0f
    for (i in text.indices) {
        widths[i] = paint.measureText(text, i, i + 1)
        totalWidth += widths[i]
    }
    if (totalWidth <= 0f) return
    var cursor = 270f - (totalWidth / radius) * (180f / PI.toFloat()) / 2f
    for (i in text.indices) {
        val widthDeg = (widths[i] / radius) * (180f / PI.toFloat())
        rotate(degrees = cursor + widthDeg / 2f + 90f, pivot = pivot) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(text, i, i + 1, pivot.x, pivot.y - radius, paint)
            }
        }
        cursor += widthDeg
    }
}

private fun DrawScope.drawGrooves() {
    val radius = size.minDimension / 2f
    val labelRadius = radius * LABEL_FRACTION
    var ring = 0
    var ringRadius = labelRadius + radius * 0.012f
    while (ringRadius < radius * 0.965f) {
        val color = if (ring % 2 == 0) {
            Color.White.copy(alpha = 0.028f)
        } else {
            Color.Black.copy(alpha = 0.045f)
        }
        drawCircle(color = color, radius = ringRadius, style = Stroke(width = 1.1f))
        ringRadius += radius * 0.006f
        ring++
    }
    for (fraction in floatArrayOf(0.61f, 0.71f, 0.81f, 0.90f)) {
        drawCircle(Color.Black.copy(alpha = 0.30f), radius = radius * fraction, style = Stroke(width = radius * 0.010f))
        drawCircle(Color.White.copy(alpha = 0.07f), radius = radius * (fraction + 0.006f), style = Stroke(width = radius * 0.004f))
    }
    drawCircle(Color.Black.copy(alpha = 0.24f), radius = radius * 0.968f, style = Stroke(width = radius * 0.018f))
    val inner = labelRadius
    val arcSize = Size(2 * (radius - inner), 2 * (radius - inner))
    drawArc(
        color = Color.White.copy(alpha = 0.055f),
        startAngle = 34f,
        sweepAngle = 46f,
        useCenter = false,
        topLeft = Offset(inner, inner),
        size = arcSize,
        style = Stroke(width = radius - inner)
    )
    drawArc(
        color = Color.White.copy(alpha = 0.040f),
        startAngle = 214f,
        sweepAngle = 46f,
        useCenter = false,
        topLeft = Offset(inner, inner),
        size = arcSize,
        style = Stroke(width = radius - inner)
    )
    drawCircle(Color.White.copy(alpha = 0.06f), radius = radius * 0.985f, style = Stroke(width = 1.5f))
}

private fun DrawScope.drawSpindle() {
    val radius = size.minDimension / 2f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                Color(0xFFCFCFD3),
                Color(0xFF8C8C92),
                Color(0xFF4B4B50),
                Color(0xFF2B2B2E)
            ),
            center = Offset(center.x - radius * 0.006f, center.y - radius * 0.008f),
            radius = radius * 0.03f
        ),
        radius = radius * 0.022f
    )
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = radius * 0.022f, style = Stroke(width = 1.5f))
}

private fun createMarblingBitmap(sizePx: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val random = Random(9)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    val half = sizePx / 2f
    fun blob(color: Int, minRadius: Float, maxRadius: Float) {
        paint.color = color
        val blobRadius = half * (minRadius + random.nextFloat() * (maxRadius - minRadius))
        val blobAngle = random.nextFloat() * 2f * PI.toFloat()
        val distance = half * 0.85f * sqrt(random.nextFloat())
        canvas.drawCircle(
            half + cos(blobAngle) * distance,
            half + sin(blobAngle) * distance,
            blobRadius,
            paint
        )
    }
    paint.maskFilter = BlurMaskFilter(sizePx * 0.02f, BlurMaskFilter.Blur.NORMAL)
    repeat(24) {
        blob(android.graphics.Color.argb(12 + random.nextInt(16), 255, 255, 255), 0.08f, 0.30f)
    }
    repeat(20) {
        blob(android.graphics.Color.argb(30 + random.nextInt(35), 0, 0, 0), 0.06f, 0.24f)
    }
    paint.maskFilter = BlurMaskFilter(sizePx * 0.008f, BlurMaskFilter.Blur.NORMAL)
    repeat(14) {
        blob(android.graphics.Color.argb(60 + random.nextInt(40), 0, 0, 0), 0.015f, 0.05f)
    }
    return bitmap.asImageBitmap()
}

@Composable
private fun SleeveShading(cornerRadius: Dp) {
    val cornerPx = with(LocalDensity.current) { cornerRadius.toPx() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val spineWidth = size.width * 0.055f
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent),
                startX = 0f,
                endX = spineWidth
            ),
            size = Size(spineWidth, size.height)
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.12f
            ),
            size = Size(size.width, size.height * 0.12f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.12f),
            cornerRadius = CornerRadius(cornerPx),
            style = Stroke(width = 1.5f)
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun Artwork(
    coverArtId: String?,
    artworkUri: Uri?,
    isRadio: Boolean,
    placeholderRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val model: Any? = if (isRadio) {
        artworkUri
    } else {
        coverArtId?.takeIf { it.isNotEmpty() }?.let {
            CustomGlideRequest.createUrl(it, Preferences.getImageSize())
        }
    }

    if (model == null) {
        Image(
            painter = painterResource(id = placeholderRes),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
        return
    }

    val dataSaving = rememberDataSavingMode()

    GlideImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        requestBuilderTransform = { request ->
            var transformed = if (isRadio) {
                request.signature(ObjectKey(artworkUri.toString()))
            } else {
                request.signature(CustomGlideRequest.createSignature(coverArtId!!))
            }
            transformed = transformed.diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
            if (isRadio) {
                transformed = RadioCoverArtDownloader.applyLocalFileSignature(transformed, artworkUri!!)
            } else if (dataSaving) {
                transformed = transformed.onlyRetrieveFromCache(true)
            }
            transformed
        },
        loading = placeholder {
            Image(
                painter = painterResource(id = placeholderRes),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        },
        failure = placeholder {
            Image(
                painter = painterResource(id = placeholderRes),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    )
}
