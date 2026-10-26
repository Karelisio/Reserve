package com.karelisio.reserve.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.Trend
import com.karelisio.reserve.domain.TrendDirection
import com.karelisio.reserve.ui.components.Sparkline

/** "Maintenant ou attendre ?" */
@Composable
fun TrendCard(trend: Trend, fuel: Fuel, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, onContainer) = when (trend.direction) {
        TrendDirection.DOWN -> colors.tertiaryContainer to colors.onTertiaryContainer
        TrendDirection.UP -> colors.errorContainer to colors.onErrorContainer
        TrendDirection.STABLE -> colors.secondaryContainer to colors.onSecondaryContainer
        null -> colors.surfaceContainerHigh to colors.onSurface
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    trend.direction,
                    transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() },
                    label = "trendIcon",
                ) { direction ->
                    val shape = when (direction) {
                        TrendDirection.DOWN -> MaterialShapes.Cookie6Sided
                        TrendDirection.UP -> MaterialShapes.Burst
                        TrendDirection.STABLE -> MaterialShapes.Pill
                        null -> MaterialShapes.Circle
                    }.toShape()
                    Box(
                        Modifier.size(48.dp).background(onContainer.copy(alpha = 0.12f), shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            when (direction) {
                                TrendDirection.DOWN -> Icons.AutoMirrored.Rounded.TrendingDown
                                TrendDirection.UP -> Icons.AutoMirrored.Rounded.TrendingUp
                                TrendDirection.STABLE -> Icons.AutoMirrored.Rounded.TrendingFlat
                                null -> Icons.Rounded.HourglassEmpty
                            },
                            contentDescription = null,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Maintenant ou attendre ?", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${fuel.label} : ${trend.label.lowercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(trend.advice, style = MaterialTheme.typography.bodyMedium)
            if (trend.points.size >= 2) {
                Spacer(Modifier.height(8.dp))
                Sparkline(
                    trend.points,
                    Modifier.fillMaxWidth().height(48.dp),
                    color = onContainer,
                    description = "Prix moyen de la zone sur 7 jours",
                    endTime = trend.points.last().first,
                    stepped = false,
                )
            }
            if (trend.today != null && trend.weekAverage != null) {
                Text(
                    "Moyenne de la zone : ${Format.price(trend.today)} aujourd'hui · ${Format.price(trend.weekAverage)} sur 7 jours",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
