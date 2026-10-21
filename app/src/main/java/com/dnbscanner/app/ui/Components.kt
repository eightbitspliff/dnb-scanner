package com.dnbscanner.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dnbscanner.app.model.MusicLink

/** Ein Beat bei 174 BPM in Millisekunden. */
private const val BEAT_MS = 345
private val BAR_DURATIONS = listOf(BEAT_MS, BEAT_MS * 3 / 2, BEAT_MS * 3 / 4, BEAT_MS * 2, BEAT_MS * 5 / 4, BEAT_MS * 7 / 8, BEAT_MS * 7 / 4)

val NeonGradient = Brush.linearGradient(listOf(Neon.Acid, Neon.Cyan))

/** Animierte Equalizer-Balken im Takt von 174 BPM. */
@Composable
fun EqualizerBars(
    modifier: Modifier = Modifier,
    bars: Int = 7,
    barWidth: Dp = 4.dp,
    height: Dp = 24.dp,
    brush: Brush = NeonGradient,
) {
    val transition = rememberInfiniteTransition(label = "eq")
    Row(
        modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(barWidth * 0.6f),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 0 until bars) {
            val level by transition.animateFloat(
                initialValue = 0.15f + (i % 3) * 0.1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(BAR_DURATIONS[i % BAR_DURATIONS.size], easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(barWidth)
                    .fillMaxHeight(level)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(brush),
            )
        }
    }
}

/** Schmaler, durchlaufender Neon-Ladebalken. */
@Composable
fun ScanningBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "x",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Neon.SurfaceHi)
            .drawBehind {
                val w = size.width
                val start = (x * 1.6f - 0.6f) * w
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Neon.Acid, Neon.Cyan, Color.Transparent),
                        startX = start,
                        endX = start + w * 0.6f,
                    ),
                )
            },
    )
}

@Composable
fun TagPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text.uppercase(),
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.2.sp,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun NeonChip(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) color else Neon.Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) color else Neon.Outline),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = if (selected) Neon.Text else Neon.TextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun StatTile(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Neon.Surface)
            .border(1.dp, color.copy(alpha = 0.25f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value.toString(), color = color, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Text(label, color = Neon.TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, maxLines = 1)
    }
}

/** Großer Button für YouTube Music bzw. Spotify: gefüllt = direkter Treffer, umrandet = Suche. */
@Composable
fun ServiceButton(
    label: String,
    color: Color,
    contentOnColor: Color,
    link: MusicLink,
    loading: Boolean,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val content = if (link.direct) contentOnColor else color
    val base = modifier.clip(shape)
    val styled = if (link.direct) base.background(color) else base.background(color.copy(alpha = 0.08f)).border(1.dp, color.copy(alpha = 0.6f), shape)
    Row(
        styled.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = content, strokeWidth = 2.dp)
            else icon(content)
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(label, color = content, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    link.direct -> "Direkt öffnen"
                    loading -> "Wird gesucht …"
                    else -> "In App suchen"
                },
                color = content.copy(alpha = 0.75f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun YouTubeMusicIcon(tint: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawCircle(tint, radius = w / 2 - w * 0.06f, style = Stroke(width = w * 0.11f))
        val play = Path().apply {
            moveTo(w * 0.40f, w * 0.30f)
            lineTo(w * 0.72f, w * 0.50f)
            lineTo(w * 0.40f, w * 0.70f)
            close()
        }
        drawPath(play, tint)
    }
}

@Composable
fun SpotifyIcon(tint: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawCircle(tint, radius = w / 2)
        val cut = if (tint == Neon.SpotifyGreen) Neon.Bg else Neon.SpotifyGreen
        for (i in 0..2) {
            val inset = w * (0.20f + 0.07f * i)
            val top = w * (0.26f + 0.13f * i)
            drawArc(
                color = cut,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(inset, top),
                size = Size(w - 2 * inset, w * 0.55f),
                style = Stroke(width = w * (0.10f - 0.015f * i), cap = StrokeCap.Round),
            )
        }
    }
}
