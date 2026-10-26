package com.karelisio.reserve.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Small price chart: [points] are (time, price) pairs. Prices are drawn as steps, since a
 * price stays valid until the next update. The line is revealed with an animation.
 */
@Composable
fun Sparkline(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    description: String = "Évolution du prix",
    endTime: Long = System.currentTimeMillis(),
    stepped: Boolean = true,
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val progress by animateFloatAsState(if (revealed) 1f else 0f, label = "sparkline")
    val surface = MaterialTheme.colorScheme.surface

    Canvas(modifier.semantics { contentDescription = description }) {
        if (points.isEmpty()) return@Canvas
        val sorted = points.sortedBy { it.first }
        val t0 = sorted.first().first
        val t1 = maxOf(endTime, sorted.last().first, t0 + 1)
        val minP = sorted.minOf { it.second }
        val maxP = sorted.maxOf { it.second }
        val spread = (maxP - minP).takeIf { it > 0.0005 } ?: 0.01
        val pad = 4.dp.toPx()
        fun x(t: Long) = pad + (size.width - 2 * pad) * ((t - t0).toFloat() / (t1 - t0))
        fun y(p: Double) = pad + (size.height - 2 * pad) * (1f - ((p - minP) / spread).toFloat())

        val line = Path()
        sorted.forEachIndexed { i, (t, p) ->
            if (i == 0) line.moveTo(x(t), y(p))
            else {
                if (stepped) line.lineTo(x(t), y(sorted[i - 1].second))
                line.lineTo(x(t), y(p))
            }
        }
        line.lineTo(x(t1), y(sorted.last().second))

        val area = Path().apply {
            addPath(line)
            lineTo(x(t1), size.height)
            lineTo(x(t0), size.height)
            close()
        }
        clipRect(right = size.width * progress) {
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
            drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val last = Offset(x(t1), y(sorted.last().second))
        if (progress > 0.98f) {
            drawCircle(surface, radius = 5.dp.toPx(), center = last)
            drawCircle(color, radius = 3.5.dp.toPx(), center = last)
        }
    }
}
