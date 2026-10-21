package app.mafya

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Собственный набор иконок в стиле лица Мафи: белая линия, круглые концы. Сетка 24×24. */
enum class Ic { BRAIN, BOOK, MOON, SUN, GEAR, MIC, STOP, SEND, CLOSE }

@Composable
fun MIcon(kind: Ic, modifier: Modifier = Modifier, tint: Color = Color.White, size: Dp = 26.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val st = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, o(x1, y1), o(x2, y2), strokeWidth = 1.9f * u, cap = StrokeCap.Round)

        when (kind) {
            Ic.BRAIN -> {
                // два полушария (второе — зеркальное) + извилины
                for (mirror in listOf(false, true)) {
                    fun x(v: Float) = (if (mirror) 24f - v else v) * u
                    val lobe = Path().apply {
                        moveTo(x(12f), 5.5f * u)
                        cubicTo(x(10.5f), 3.5f * u, x(6.5f), 3.8f * u, x(5.8f), 7f * u)
                        cubicTo(x(3.5f), 7.5f * u, x(3f), 11f * u, x(4.8f), 12.2f * u)
                        cubicTo(x(3.2f), 13.8f * u, x(4.2f), 17.2f * u, x(6.6f), 17.3f * u)
                        cubicTo(x(7.6f), 19.8f * u, x(11f), 20f * u, x(12f), 18.2f * u)
                    }
                    val fold = Path().apply {
                        moveTo(x(7.2f), 10f * u)
                        cubicTo(x(8.8f), 9.4f * u, x(10f), 10.4f * u, x(10.4f), 12.4f * u)
                    }
                    drawPath(lobe, tint, style = st)
                    drawPath(fold, tint, style = st)
                }
                line(12f, 5.5f, 12f, 18.2f)
            }
            Ic.BOOK -> {
                val left = Path().apply {
                    moveTo(12f * u, 6.5f * u)
                    cubicTo(10f * u, 4.8f * u, 6.5f * u, 4.6f * u, 3f * u, 5.6f * u)
                    lineTo(3f * u, 19f * u)
                    cubicTo(6.5f * u, 18f * u, 10f * u, 18.2f * u, 12f * u, 19.8f * u)
                }
                val right = Path().apply {
                    moveTo(12f * u, 6.5f * u)
                    cubicTo(14f * u, 4.8f * u, 17.5f * u, 4.6f * u, 21f * u, 5.6f * u)
                    lineTo(21f * u, 19f * u)
                    cubicTo(17.5f * u, 18f * u, 14f * u, 18.2f * u, 12f * u, 19.8f * u)
                }
                drawPath(left, tint, style = st)
                drawPath(right, tint, style = st)
                line(12f, 6.5f, 12f, 19.8f)
            }
            Ic.MOON -> {
                val a = Path().apply { addOval(Rect(4f * u, 4f * u, 20f * u, 20f * u)) }
                val b = Path().apply { addOval(Rect(9f * u, 0.5f * u, 25f * u, 16.5f * u)) }
                val crescent = Path.combine(PathOperation.Difference, a, b)
                drawPath(crescent, tint, style = st)
            }
            Ic.SUN -> {
                drawCircle(tint, 4.2f * u, o(12f, 12f), style = st)
                for (i in 0 until 8) {
                    val t = Math.toRadians(i * 45.0)
                    val c = cos(t).toFloat()
                    val s = sin(t).toFloat()
                    line(12f + c * 7.5f, 12f + s * 7.5f, 12f + c * 10f, 12f + s * 10f)
                }
            }
            Ic.GEAR -> {
                // «настройки» в виде трёх ползунков: не путается с солнцем
                line(4f, 6f, 6.2f, 6f); line(11.8f, 6f, 20f, 6f)
                drawCircle(tint, 2.8f * u, o(9f, 6f), style = st)
                line(4f, 12f, 13.2f, 12f); line(18.8f, 12f, 20f, 12f)
                drawCircle(tint, 2.8f * u, o(16f, 12f), style = st)
                line(4f, 18f, 7.2f, 18f); line(12.8f, 18f, 20f, 18f)
                drawCircle(tint, 2.8f * u, o(10f, 18f), style = st)
            }
            Ic.MIC -> {
                drawRoundRect(tint, o(9f, 2.5f), Size(6f * u, 11f * u), CornerRadius(3f * u), style = st)
                drawArc(tint, 0f, 180f, false, o(5.5f, 7.5f), Size(13f * u, 11f * u), style = st)
                line(12f, 18.5f, 12f, 21.5f)
                line(8.5f, 21.5f, 15.5f, 21.5f)
            }
            Ic.STOP -> {
                drawRoundRect(tint, o(6f, 6f), Size(12f * u, 12f * u), CornerRadius(2.5f * u), style = Fill)
            }
            Ic.SEND -> {
                val plane = Path().apply {
                    moveTo(3f * u, 11f * u)
                    lineTo(21f * u, 3f * u)
                    lineTo(14f * u, 21f * u)
                    lineTo(11.5f * u, 13f * u)
                    close()
                }
                drawPath(plane, tint, style = st)
                line(21f, 3f, 11.5f, 13f)
            }
            Ic.CLOSE -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
        }
    }
}
