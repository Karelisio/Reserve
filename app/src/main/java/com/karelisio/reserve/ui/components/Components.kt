package com.karelisio.reserve.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.theme.PriceColors
import com.karelisio.reserve.ui.theme.isDarkColors

val SOURCE_MENTION: String
    get() = t(
        "Source : DGCCRF, prix des carburants, data.economie.gouv.fr (Licence Ouverte 2.0)",
        "Source: DGCCRF, fuel prices, data.economie.gouv.fr (Licence Ouverte 2.0)",
    )

/** Light haptic feedback for the main actions. */
@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return Haptics { type -> feedback.performHapticFeedback(type) }
}

fun interface Haptics {
    fun perform(type: HapticFeedbackType)
    fun confirm() = perform(HapticFeedbackType.Confirm)
    fun tick() = perform(HapticFeedbackType.SegmentTick)
    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
}

/** Color of a price on the cheapest (green) → most expensive (red) scale. */
@Composable
fun priceColor(price: Double, range: ClosedFloatingPointRange<Double>?): Color {
    val fraction = if (range == null || range.endInclusive - range.start < 0.0005) 0f
    else ((price - range.start) / (range.endInclusive - range.start)).toFloat()
    return PriceColors.scale(fraction, MaterialTheme.colorScheme.primary, isDarkColors)
}

/** Price in an expressive "cookie" shape, tinted along the price scale. */
@Composable
fun PriceBadge(price: Double, color: Color, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val animated by animateColorAsState(color, label = "priceColor")
    val scale by animateFloatAsState(if (highlighted) 1.06f else 1f, label = "priceScale")
    val shape = (if (highlighted) MaterialShapes.Cookie9Sided else MaterialShapes.Square).toShape()
    Box(
        modifier = modifier
            .scale(scale)
            .size(width = 76.dp, height = 64.dp)
            .background(animated, shape),
        contentAlignment = Alignment.Center,
    ) {
        val label = String.format(com.karelisio.reserve.domain.Lang.locale, "%.3f", price)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clearAndSetSemantics { contentDescription = t("$label euros le litre", "$label euros per litre") },
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = PriceColors.onColor(animated),
                maxLines = 1,
            )
            Text(
                text = "€/L",
                style = MaterialTheme.typography.labelSmall,
                color = PriceColors.onColor(animated).copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
fun FuelSelector(
    selected: Fuel,
    onSelect: (Fuel) -> Unit,
    modifier: Modifier = Modifier,
    /** Shows a last "Bornes" chip opening the electric charging stations. */
    onCharging: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Fuel.selectableEntries.forEach { fuel ->
            val isSelected = fuel == selected
            FilterChip(
                selected = isSelected,
                onClick = { haptics.tick(); onSelect(fuel) },
                label = { Text(fuel.label) },
                leadingIcon = {
                    AnimatedVisibility(isSelected) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize))
                    }
                },
                shape = MaterialTheme.shapes.small,
            )
        }
        if (onCharging != null) {
            AssistChip(
                onClick = { haptics.tick(); onCharging() },
                label = { Text(t("Bornes", "EV charging")) },
                leadingIcon = { Icon(Icons.Rounded.EvStation, null, Modifier.size(AssistChipDefaults.IconSize)) },
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}

/** Connected button group (M3 Expressive) for a single choice. */
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { haptics.tick(); onSelect(option) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                if (icon != null) {
                    Icon(icon(option), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label(option), maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = LocalTextStyle.current.fontSize))
            }
        }
    }
}

@Composable
fun SourceFooter(modifier: Modifier = Modifier) {
    Text(
        text = SOURCE_MENTION + t("\nNoms et enseignes : © contributeurs OpenStreetMap (ODbL)", "\nNames and brands: © OpenStreetMap contributors (ODbL)"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    icon: ImageVector = Icons.Rounded.Info,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(color = container, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            action?.invoke()
        }
    }
}

/** Small rounded label, e.g. "Prix ancien" or "Rupture". */
@Composable
fun Tag(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraSmall, modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
