package com.karelisio.reserve.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.data.model.SortMode
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.RankedStation
import com.karelisio.reserve.domain.t

@Composable
fun StationCard(
    item: RankedStation,
    priceRange: ClosedFloatingPointRange<Double>?,
    isFavorite: Boolean,
    sort: SortMode,
    isBest: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    detourLabel: String = t("aller-retour", "round-trip"),
) {
    val haptics = rememberHaptics()
    val station = item.station
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PriceBadge(
                price = item.effectivePrice,
                color = if (item.outOfStock) MaterialTheme.colorScheme.surfaceVariant else priceColor(item.effectivePrice, priceRange),
                highlighted = isBest,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    station.displayName,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    station.fullAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${Format.distance(item.distanceKm)} · ${Format.age(item.price.updatedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (sort == SortMode.REAL_COST && !item.outOfStock) {
                    val saving = item.savings
                    Text(
                        text = when {
                            kotlin.math.abs(saving) < 0.05 -> t("Plein ${Format.euros(item.totalCost)} · référence", "Full tank ${Format.euros(item.totalCost)} · baseline")
                            saving > 0 -> t(
                                "Plein ${Format.euros(item.totalCost)} · ${Format.euros(saving)} économisés",
                                "Full tank ${Format.euros(item.totalCost)} · ${Format.euros(saving)} saved",
                            )
                            else -> t(
                                "Plein ${Format.euros(item.totalCost)} · ${Format.euros(-saving)} de plus",
                                "Full tank ${Format.euros(item.totalCost)} · ${Format.euros(-saving)} more",
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (saving > 0.05) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        t("Détour ${Format.distance(item.detourKm)} $detourLabel inclus", "Includes ${Format.distance(item.detourKm)} $detourLabel detour"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val hasTags = item.outOfStock || item.stale || item.discount > 0
                if (hasTags) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (item.outOfStock) Tag(
                            t("Rupture", "Out of stock"), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer,
                            icon = Icons.Rounded.Block,
                        )
                        if (item.stale) Tag(
                            t("Prix de plus de 3 j", "Price over 3 days old"), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer,
                            icon = Icons.Rounded.HourglassBottom,
                        )
                        if (item.discount > 0) Tag(
                            t("−${Format.cents(item.discount)} fidélité", "−${Format.cents(item.discount)} loyalty"), MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer, icon = Icons.Rounded.LocalOffer,
                        )
                    }
                }
            }
            IconToggleButton(
                checked = isFavorite,
                onCheckedChange = { haptics.toggle(it); onToggleFavorite() },
            ) {
                AnimatedContent(isFavorite, transitionSpec = { scaleIn() togetherWith scaleOut() }, label = "fav") { fav ->
                    Icon(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (fav) t("Retirer des favoris", "Remove from favourites") else t("Ajouter aux favoris", "Add to favourites"),
                        tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
