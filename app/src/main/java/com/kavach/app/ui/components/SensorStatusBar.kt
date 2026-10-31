package com.kavach.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kavach.app.data.DetectionMode
import com.kavach.app.data.SensorReading
import com.kavach.app.ui.theme.*

/**
 * Shows live sensor status indicators in a horizontal row.
 */
@Composable
fun SensorStatusBar(
    sensorReading: SensorReading,
    isArmed: Boolean,
    detectionMode: DetectionMode = DetectionMode.MOTION,
    colors: KavachColors = LocalKavachColors.current,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (detectionMode == DetectionMode.MOTION) {
            SensorIndicator(
                icon = Icons.Outlined.Vibration,
                label = "MOTION",
                value = String.format("%.1f", sensorReading.motionMagnitude),
                isActive = isArmed,
                colors = colors,
                modifier = Modifier.weight(1f)
            )
        } else {
            SensorIndicator(
                icon = Icons.Outlined.Speed,
                label = "FORCE",
                value = String.format("%.1f", sensorReading.motionMagnitude),
                isActive = isArmed,
                colors = colors,
                modifier = Modifier.weight(1f)
            )
        }
        SensorIndicator(
            icon = Icons.Outlined.LightMode,
            label = "LIGHT",
            value = "${sensorReading.lightLevel.toInt()} lx",
            isActive = isArmed,
            colors = colors,
            modifier = Modifier.weight(1f)
        )
        SensorIndicator(
            icon = if (sensorReading.isInPocket) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            label = if (sensorReading.isInPocket) "POCKET" else "OPEN",
            value = if (sensorReading.proximityCovered) "NEAR" else "FAR",
            isActive = isArmed,
            isWarning = sensorReading.isInPocket,
            colors = colors,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SensorIndicator(
    icon: ImageVector,
    label: String,
    value: String,
    isActive: Boolean,
    colors: KavachColors,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false
) {
    val accentColor = when {
        isWarning -> CyberAmber
        isActive -> if (colors.isDark) KavachGold else Color(0xFF00758F)
        else -> colors.textMuted
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = accentColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted
        )
    }
}
