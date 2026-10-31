package com.kavach.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kavach.app.data.TimerPreset
import com.kavach.app.ui.theme.*

/**
 * Horizontal row of timer preset chips including CUSTOM option.
 */
@Composable
fun TimerPresetRow(
    selectedPreset: TimerPreset,
    onPresetSelected: (TimerPreset) -> Unit,
    colors: KavachColors = LocalKavachColors.current,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // First row: fixed presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimerPreset.entries.filter { it != TimerPreset.CUSTOM }.forEach { preset ->
                TimerChip(
                    preset = preset,
                    isSelected = preset == selectedPreset,
                    onClick = { onPresetSelected(preset) },
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Custom option
        TimerChip(
            preset = TimerPreset.CUSTOM,
            isSelected = selectedPreset == TimerPreset.CUSTOM,
            onClick = { onPresetSelected(TimerPreset.CUSTOM) },
            colors = colors,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TimerChip(
    preset: TimerPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: KavachColors,
    modifier: Modifier = Modifier
) {
    val selectedTextColor = if (colors.isDark) KavachGold else Color(0xFF00758F)
    val selectedBgColor = if (colors.isDark) KavachGold.copy(alpha = 0.18f) else Color(0xFF00758F).copy(alpha = 0.12f)
    val selectedBorderColor = if (colors.isDark) KavachGold else Color(0xFF00838F)

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) selectedBgColor else colors.card,
        label = "chipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) selectedBorderColor else colors.cardBorder,
        label = "chipBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) selectedTextColor else colors.textSecondary,
        label = "chipText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = preset.label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}
