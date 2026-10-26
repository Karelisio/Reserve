package com.karelisio.reserve.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.RankedStation

/** The best choice around (real cost), highlighted at the top of the list. */
@Composable
fun BestChoiceCard(item: RankedStation, onOpen: () -> Unit, onGo: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            // Expressive decoration in the corner.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 36.dp, y = (-36).dp)
                    .size(150.dp)
                    .rotate(18f)
                    .background(colors.primary.copy(alpha = 0.12f), MaterialShapes.Cookie12Sided.toShape()),
            )
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Savings, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Meilleur choix · ${item.price.fuel.label}", style = MaterialTheme.typography.labelLargeEmphasized)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        Format.number(item.effectivePrice, 3),
                        style = MaterialTheme.typography.displaySmallEmphasized,
                    )
                    Text(" €/L", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    item.station.displayName,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${Format.distance(item.distanceKm)} · ${Format.age(item.price.updatedAt)} · plein ${Format.euros(item.totalCost)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (item.savings > 0.05) {
                    Text(
                        "${Format.euros(item.savings)} économisés par rapport à la station la plus proche",
                        style = MaterialTheme.typography.bodySmallEmphasized,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Button(onClick = onGo, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(Icons.Rounded.Directions, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Y aller")
                    }
                    FilledTonalButton(onClick = onOpen) {
                        Text("Fiche")
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(ButtonDefaults.IconSize))
                    }
                }
            }
        }
    }
}
