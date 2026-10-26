package com.karelisio.reserve.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.RangeEstimate
import com.karelisio.reserve.domain.t
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Estimated range, drawn as the app's fuel gauge. */
@Composable
fun RangeCard(estimate: RangeEstimate, vehicleName: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = if (estimate.onReserve) colors.errorContainer to colors.onErrorContainer
    else colors.surfaceContainerHigh to colors.onSurface
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Gauge(level = estimate.level.toFloat(), modifier = Modifier.size(72.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t("Autonomie estimée", "Estimated range") + vehicleName?.let { " · $it" }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "≈ ${estimate.remainingKm.roundToInt()} km",
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                )
                val days = estimate.daysLeft
                Text(
                    if (estimate.onReserve) t("Vous êtes probablement sur la réserve : faites le plein bientôt.", "You're probably on reserve: fill up soon.")
                    else t(
                        "≈ ${Format.number(estimate.remainingLiters, 0)} L, environ ${if (days < 1.5) "1 jour" else "${days.roundToInt()} jours"} au rythme habituel",
                        "≈ ${Format.number(estimate.remainingLiters, 0)} L, about ${if (days < 1.5) "1 day" else "${days.roundToInt()} days"} at your usual pace",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    t(
                        "D'après votre carnet, depuis le plein du ${Format.date(estimate.lastFillUpDate)}",
                        "From your fuel log, since the fill-up on ${Format.date(estimate.lastFillUpDate)}",
                    ) + if (!estimate.fromFullTank) t(" (partiel : estimation moins sûre)", " (partial: less reliable estimate)") else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun Gauge(level: Float, modifier: Modifier) {
    val animated by animateFloatAsState(level.coerceIn(0f, 1f), label = "gauge")
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    val reserve = MaterialTheme.colorScheme.error
    val needle = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.semantics { contentDescription = t("Réservoir à ${(level * 100).roundToInt()} %", "Tank ${(level * 100).roundToInt()}% full") }) {
        val stroke = size.minDimension * 0.11f
        val inset = stroke / 2 + 2
        val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
        val topLeft = Offset(inset, inset)
        // 240° gauge, from bottom left (empty) to bottom right (full), like the app icon.
        drawArc(track, 150f, 240f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(reserve, 150f, 240f * 0.12f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (animated > 0.12f) {
            drawArc(fill, 150f + 240f * 0.12f, 240f * (animated - 0.12f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val angle = Math.toRadians((150.0 + 240.0 * animated))
        val center = Offset(size.width / 2, size.height / 2)
        val length = size.minDimension * 0.36f
        drawLine(
            needle, center,
            Offset(center.x + (length * cos(angle)).toFloat(), center.y + (length * sin(angle)).toFloat()),
            strokeWidth = stroke * 0.6f, cap = StrokeCap.Round,
        )
        drawCircle(needle, radius = stroke * 0.55f, center = center)
    }
}
