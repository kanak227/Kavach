package com.kavach.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.data.DetectionMode
import com.kavach.app.ui.theme.*

/**
 * Feature toggle cards for the dashboard.
 */
@Composable
fun FeatureToggleCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    colors: KavachColors = LocalKavachColors.current,
    modifier: Modifier = Modifier
) {
    val activeAccent = if (colors.isDark) KavachGold else Color(0xFF00758F)
    val activeTrack = if (colors.isDark) KavachGoldDim.copy(alpha = 0.5f) else Color(0xFF00758F).copy(alpha = 0.4f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .clickable { onToggle(!isEnabled) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isEnabled) colors.textPrimary else colors.textSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = activeAccent,
                checkedTrackColor = activeTrack,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceElevated
            )
        )
    }
}

/**
 * Sensitivity slider card.
 */
@Composable
fun SensitivityCard(
    level: Int,
    onLevelChange: (Int) -> Unit,
    detectionMode: DetectionMode = DetectionMode.MOTION,
    colors: KavachColors = LocalKavachColors.current,
    modifier: Modifier = Modifier
) {
    val label = when (detectionMode) {
        DetectionMode.DESK -> "DESK SENSITIVITY"
        DetectionMode.SLEEP -> "SLEEP SENSITIVITY"
        DetectionMode.TRAVEL -> "TRAVEL SENSITIVITY"
        DetectionMode.POCKET -> "POCKET SENSITIVITY"
    }
    val activeAccent = if (colors.isDark) KavachGold else Color(0xFF00758F)
    val activeDim = if (colors.isDark) KavachGoldDim else Color(0xFF00758F)
    val inactiveTrack = if (colors.isDark) DarkSurfaceElevated else Color(0xFFCBD5E1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text = "$level/10",
                style = MaterialTheme.typography.labelLarge,
                color = activeAccent,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = level.toFloat(),
            onValueChange = { onLevelChange(it.toInt()) },
            valueRange = 1f..10f,
            steps = 8,
            colors = SliderDefaults.colors(
                thumbColor = activeAccent,
                activeTrackColor = activeAccent,
                inactiveTrackColor = inactiveTrack,
                activeTickColor = activeDim,
                inactiveTickColor = colors.textMuted
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "LOW (Sleep / Table Safe)",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted
            )
            Text(
                text = "HIGH (Feather-Touch)",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        val hintText = when (level) {
            1 -> "Level 1 (Sleep Guard): Requires deliberate vertical lift (4.5 m/s²) or 22° tilt. Horizontal sliding across table is 100% ignored."
            in 2..3 -> "Level $level (High Slide Resistance): Firm pickup required. Ideal for bedside nightstands."
            in 4..6 -> "Level $level (Balanced Guard): Normal hand pickup triggers siren. Horizontal table brushing is ignored."
            in 7..8 -> "Level $level (High Sensitivity): Responsive to slight lifts and gentle angle movements."
            else -> "Level $level (Feather-Touch): Maximum security. Triggers on the slightest lift (0.45 m/s²) or 3° tilt."
        }
        Text(
            text = hintText,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )
    }
}
