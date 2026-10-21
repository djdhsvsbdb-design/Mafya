package app.mafya

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

enum class Emotion(val glow: Color) {
    NEUTRAL(Color(0xFF8A5A1F)),
    HAPPY(Color(0xFF8A7A22)),
    STARS(Color(0xFF8A7A22)),
    WINK(Color(0xFF8A5A1F)),
    SURPRISED(Color(0xFF1F7A7A)),
    HEART(Color(0xFFA02A6A)),
    THINKING(Color(0xFF1F6A8A)),
    LISTENING(Color(0xFF1F7A6A)),
    SLEEP(Color(0xFF1A1F4A))
}

private enum class Eye { CIRCLE, ARCH, STAR, HEART, DOWN }
private enum class Mouth { SMILE, OPEN, SMALL_OPEN, O, DOT, TINY }

private fun eyes(e: Emotion): Pair<Eye, Eye> = when (e) {
    Emotion.NEUTRAL, Emotion.THINKING, Emotion.LISTENING, Emotion.SURPRISED -> Eye.CIRCLE to Eye.CIRCLE
    Emotion.HAPPY -> Eye.ARCH to Eye.ARCH
    Emotion.STARS -> Eye.STAR to Eye.STAR
    Emotion.WINK -> Eye.CIRCLE to Eye.ARCH
    Emotion.HEART -> Eye.HEART to Eye.HEART
    Emotion.SLEEP -> Eye.DOWN to Eye.DOWN
}

private fun mouth(e: Emotion): Mouth = when (e) {
    Emotion.NEUTRAL -> Mouth.SMILE
    Emotion.HAPPY, Emotion.STARS -> Mouth.OPEN
    Emotion.WINK, Emotion.LISTENING -> Mouth.TINY
    Emotion.SURPRISED -> Mouth.O
    Emotion.HEART -> Mouth.SMALL_OPEN
    Emotion.THINKING -> Mouth.DOT
    Emotion.SLEEP -> Mouth.TINY
}

private fun starPath(cx: Float, cy: Float, s: Float) = Path().apply {
    moveTo(cx, cy - s)
    quadraticBezierTo(cx, cy, cx + s, cy)
    quadraticBezierTo(cx, cy, cx, cy + s)
    quadraticBezierTo(cx, cy, cx - s, cy)
    quadraticBezierTo(cx, cy, cx, cy - s)
    close()
}

private fun heartPath(cx: Float, cy: Float, s: Float) = Path().apply {
    moveTo(cx, cy + s)
    cubicTo(cx - s * 1.6f, cy - s * 0.1f, cx - s * 0.9f, cy - s * 1.2f, cx, cy - s * 0.35f)
    cubicTo(cx + s * 0.9f, cy - s * 1.2f, cx + s * 1.6f, cy - s * 0.1f, cx, cy + s)
    close()
}

@Composable
fun Face(emotion: Emotion, speaking: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "face")
    val blink by t.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes<Float> {
                durationMillis = 4000
                1f at 0
                1f at 3700
                0.08f at 3830
                1f at 3960
            }
        ),
        label = "blink"
    )
    val talk by t.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(170), RepeatMode.Reverse),
        label = "talk"
    )
    val bob by t.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )

    Canvas(modifier.fillMaxWidth().aspectRatio(1.1f)) {
        val w = size.width
        val h = size.height
        val r = w * 0.13f
        val sw = w * 0.032f
        val ey = h * 0.40f + bob
        val lx = w * 0.24f
        val rx = w * 0.76f
        val sleeping = emotion == Emotion.SLEEP
        val ink = if (sleeping) Color.White.copy(alpha = 0.55f) else Color.White

        // мягкое синее свечение за лицом
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF2A5BFF).copy(alpha = if (sleeping) 0.2f else 0.45f), Color.Transparent),
                center = Offset(w / 2, h * 0.5f),
                radius = w * 0.45f
            ),
            radius = w * 0.45f,
            center = Offset(w / 2, h * 0.5f)
        )

        val (le, re) = eyes(emotion)
        val big = if (emotion == Emotion.SURPRISED) 1.15f else 1f
        drawEye(le, lx, ey, r * big, sw, ink, blink, left = true)
        drawEye(re, rx, ey, r * big, sw, ink, blink, left = false)

        // румянец
        if (emotion in listOf(Emotion.NEUTRAL, Emotion.HAPPY, Emotion.WINK, Emotion.HEART, Emotion.LISTENING, Emotion.STARS)) {
            val pink = Color(0xFFFF7AB8)
            for (cx in listOf(lx + r * 0.1f, rx - r * 0.1f)) {
                drawOval(
                    pink.copy(alpha = 0.25f),
                    topLeft = Offset(cx - r * 0.65f, ey + r * 1.35f),
                    size = Size(r * 1.3f, r * 0.45f)
                )
                for (k in 0..2) {
                    val x = cx - r * 0.35f + k * r * 0.3f
                    drawLine(
                        pink.copy(alpha = 0.7f),
                        Offset(x + r * 0.08f, ey + r * 1.15f),
                        Offset(x - r * 0.04f, ey + r * 1.4f),
                        strokeWidth = sw * 0.45f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // рот
        var m = mouth(emotion)
        if (speaking && (m == Mouth.SMILE || m == Mouth.TINY || m == Mouth.DOT)) m = Mouth.OPEN
        val k = if (speaking) talk else 1f
        drawMouth(m, w / 2, h * 0.76f + bob, w * 0.075f, sw, ink, k)
    }
}

private fun DrawScope.drawEye(kind: Eye, cx: Float, cy: Float, r: Float, sw: Float, ink: Color, blink: Float, left: Boolean) {
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    var lashColor = ink
    var lashR = r
    when (kind) {
        Eye.CIRCLE -> drawOval(ink, Offset(cx - r, cy - r * blink), Size(2 * r, 2 * r * blink), style = stroke)
        Eye.ARCH -> drawArc(ink, 180f, 180f, false, Offset(cx - r, cy - r * 0.2f), Size(2 * r, 2 * r), style = stroke)
        Eye.DOWN -> {
            drawArc(ink, 0f, 180f, false, Offset(cx - r, cy - r * 1.4f), Size(2 * r, 2 * r), style = stroke)
            lashR = r * 0.2f
        }
        Eye.STAR -> {
            drawPath(starPath(cx, cy, r * 1.15f), ink, style = stroke)
            lashColor = ink.copy(alpha = 0.4f)
            lashR = r * 1.25f
        }
        Eye.HEART -> {
            drawPath(heartPath(cx, cy, r * 0.95f), ink, style = stroke)
            lashColor = ink.copy(alpha = 0.4f)
            lashR = r * 1.25f
        }
    }
    // ресницы
    val base = if (kind == Eye.ARCH) cy + r * 0.8f else cy
    val angles = listOf(-10f, -35f, -60f)
    for (a in angles) {
        val rad = Math.toRadians(a.toDouble())
        val dx = (if (left) -cos(rad) else cos(rad)).toFloat()
        val dy = sin(rad).toFloat()
        val start = Offset(cx + dx * (lashR + sw * 0.9f), base + dy * (lashR + sw * 0.9f))
        val end = Offset(start.x + dx * r * 0.6f, start.y + dy * r * 0.6f)
        if (kind != Eye.DOWN || a == -10f) {
            drawLine(lashColor, start, end, strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawMouth(kind: Mouth, cx: Float, my: Float, mw: Float, sw: Float, ink: Color, talk: Float) {
    when (kind) {
        Mouth.SMILE -> {
            val rr = mw * 1.1f
            drawArc(ink, 30f, 120f, false, Offset(cx - rr, my - rr * 1.2f), Size(2 * rr, 2 * rr), style = Stroke(sw * 0.9f, cap = StrokeCap.Round))
        }
        Mouth.TINY -> {
            val rr = mw * 0.55f
            drawArc(ink, 30f, 120f, false, Offset(cx - rr, my - rr * 1.2f), Size(2 * rr, 2 * rr), style = Stroke(sw * 0.8f, cap = StrokeCap.Round))
        }
        Mouth.OPEN ->
            drawArc(ink, 0f, 180f, true, Offset(cx - mw * 1.6f, my - mw * talk), Size(mw * 3.2f, 2 * mw * talk), style = Fill)
        Mouth.SMALL_OPEN ->
            drawArc(Color(0xFFD0D0D0), 0f, 180f, true, Offset(cx - mw * 0.8f, my - mw * 0.5f), Size(mw * 1.6f, mw), style = Fill)
        Mouth.O ->
            drawCircle(ink, mw * 0.45f, Offset(cx, my), style = Stroke(sw * 0.8f))
        Mouth.DOT ->
            drawCircle(ink, sw * 0.7f, Offset(cx - mw * 3f, my))
    }
}
