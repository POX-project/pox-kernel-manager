package com.pox.kernelmanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pox.kernelmanager.ui.theme.PoxCard
import kotlin.math.roundToInt

@Composable
fun FeatureSliderCard(
    title: String,
    valueLabel: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit = {},
    onValueChangeFinished: () -> Unit = {},
    onValueConfirmed: ((Float) -> Unit)? = null,
    formatLabel: ((Float) -> String)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    // Decoupled transient slider position ensures buttery smooth 120Hz/60Hz dragging
    // with zero stutter or state-fighting from asynchronous background kernel I/O.
    var internalValue by remember(value) { mutableFloatStateOf(value) }
    var lastHapticStep by remember { mutableIntStateOf(value.roundToInt()) }

    // Synchronize from upstream hardware state only when user is NOT actively dragging
    LaunchedEffect(value, isDragged) {
        if (!isDragged) {
            internalValue = value
            lastHapticStep = value.roundToInt()
        }
    }

    val displayLabel = if (isDragged && formatLabel != null) {
        formatLabel(internalValue)
    } else {
        valueLabel
    }

    val badgeColor by animateColorAsState(
        targetValue = if (isDragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        label = "badgeColor"
    )
    val badgeTextColor by animateColorAsState(
        targetValue = if (isDragged) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        label = "badgeTextColor"
    )

    PoxCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            Slider(
                value = internalValue,
                onValueChange = { newVal ->
                    internalValue = newVal
                    val currentStep = newVal.roundToInt()
                    if (currentStep != lastHapticStep) {
                        lastHapticStep = currentStep
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onValueChange(newVal)
                },
                onValueChangeFinished = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onValueConfirmed?.invoke(internalValue)
                    onValueChangeFinished()
                },
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
