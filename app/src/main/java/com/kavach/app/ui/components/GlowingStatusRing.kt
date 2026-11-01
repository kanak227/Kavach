package com.kavach.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.data.DetectionMode
import com.kavach.app.data.ShieldState
import com.kavach.app.ui.theme.*

/**
 * The hero visual component — a glowing animated ring that shows the shield state.
 *
 * DISARMED: Dim grey ring, "DISARMED" text
 * ARMING: Pulsing amber ring with countdown
 * ARMED: Solid glowing cyan ring, rotating scanner line
 * TRIGGERED: Pulsing amber → showing decoy countdown
 * ALARM_ACTIVE: Aggressive flashing red ring
 * AUTHENTICATED: Brief green flash with checkmark properly centered
 */
@Composable
fun GlowingStatusRing(
    shieldState: ShieldState,
    remainingTime: String,
    countdownValue: Int,
    detectionMode: DetectionMode = DetectionMode.MOTION,
    modifier: Modifier = Modifier
) {
    val colors = LocalKavachColors.current

    // Determine colors based on state
    val (primaryColor, glowColor, statusText) = remember(shieldState, colors.isDark) {
        when (shieldState) {
            ShieldState.DISARMED -> Triple(
                if (colors.isDark) TextMuted else Color(0xFF475569),
                (if (colors.isDark) TextMuted else Color(0xFF475569)).copy(alpha = 0.25f),
                "DISARMED"
            )
            ShieldState.ARMING -> Triple(if (colors.isDark) CyberAmber else LightAmber, if (colors.isDark) CyberAmberGlow else LightAmberGlow, "ARMING")
            ShieldState.ARMED -> Triple(
                if (colors.isDark) KavachGold else Color(0xFF00758F),
                if (colors.isDark) KavachGoldGlow else Color(0x3500758F),
                "ARMED"
            )
            ShieldState.TRIGGERED -> Triple(if (colors.isDark) CyberAmber else LightAmber, if (colors.isDark) CyberAmberGlow else LightAmberGlow, "VERIFYING")
            ShieldState.ALARM_ACTIVE -> Triple(CyberRed, CyberRedGlow, "ALERT")
            ShieldState.AUTHENTICATED -> Triple(CyberGreen, CyberGreenGlow, "VERIFIED")
        }
    }

    val animatedColor by animateColorAsState(
        targetValue = primaryColor,
        animationSpec = tween(500),
        label = "ringColor"
    )

    // Pulse animation for ARMING, TRIGGERED, ALARM states
    val infiniteTransition = rememberInfiniteTransition(label = "ringPulse")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (shieldState) {
                    ShieldState.ALARM_ACTIVE -> 200  // Fast flash for alarm
                    ShieldState.TRIGGERED -> 500     // Medium pulse for triggered
                    ShieldState.ARMING -> 800        // Slow pulse for arming
                    else -> 2000                     // Very slow breathing for armed
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Rotation for the scanner line when ARMED
    val scannerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scannerRotation"
    )

    // Glow radius animation
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = when (shieldState) {
            ShieldState.ALARM_ACTIVE -> 40f
            ShieldState.TRIGGERED -> 25f
            ShieldState.ARMED -> 20f
            else -> 15f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadius"
    )

    Box(
        modifier = modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        // Canvas for the ring + glow + scanner
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - 20f
            val strokeWidth = 5f

            // Outer glow ring
            val shouldPulse = shieldState != ShieldState.DISARMED
            val effectiveAlpha = if (shouldPulse) pulseAlpha * 0.4f else 0.1f

            drawCircle(
                color = animatedColor.copy(alpha = effectiveAlpha),
                radius = radius + glowRadius,
                center = center,
            )

            // Main ring - arc
            val arcSize = Size(radius * 2, radius * 2)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)

            drawArc(
                color = animatedColor.copy(
                    alpha = if (shieldState == ShieldState.ALARM_ACTIVE) pulseAlpha else 1f
                ),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Inner thin ring
            drawCircle(
                color = animatedColor.copy(alpha = 0.15f),
                radius = radius - 20f,
                center = center,
                style = Stroke(width = 1f)
            )

            // Scanner line (only when ARMED)
            if (shieldState == ShieldState.ARMED) {
                rotate(scannerRotation, pivot = center) {
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                animatedColor.copy(alpha = 0.6f),
                                animatedColor
                            ),
                            start = Offset(center.x, center.y),
                            end = Offset(center.x, center.y - radius)
                        ),
                        start = center,
                        end = Offset(center.x, center.y - radius + 10),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Tick marks around the ring
            for (i in 0 until 60) {
                val angle = Math.toRadians((i * 6).toDouble())
                val isMajor = i % 5 == 0
                val innerR = radius - if (isMajor) 15f else 8f
                val outerR = radius - 3f

                val startPt = Offset(
                    (center.x + innerR * Math.sin(angle)).toFloat(),
                    (center.y - innerR * Math.cos(angle)).toFloat()
                )
                val endPt = Offset(
                    (center.x + outerR * Math.sin(angle)).toFloat(),
                    (center.y - outerR * Math.cos(angle)).toFloat()
                )

                drawLine(
                    color = animatedColor.copy(alpha = if (isMajor) 0.5f else 0.2f),
                    start = startPt,
                    end = endPt,
                    strokeWidth = if (isMajor) 2f else 1f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Center text — with proper padding for all states
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = statusText,
                fontFamily = OrbitronFamily,
                fontWeight = FontWeight.Bold,
                fontSize = when (shieldState) {
                    ShieldState.DISARMED -> 22.sp
                    ShieldState.AUTHENTICATED -> 20.sp
                    else -> 24.sp
                },
                letterSpacing = 2.5.sp,
                color = animatedColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            when (shieldState) {
                ShieldState.ARMING -> {
                    Text(
                        text = countdownValue.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (colors.isDark) CyberAmber else LightAmber,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "CALIBRATING POSITION",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                ShieldState.ARMED -> {
                    Text(
                        text = remainingTime,
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (colors.isDark) KavachGoldLight else Color(0xFF00758F),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = detectionMode.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                ShieldState.TRIGGERED -> {
                    Text(
                        text = countdownValue.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (colors.isDark) CyberAmber else LightAmber,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "AUTHENTICATE NOW",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (colors.isDark) CyberAmber else LightAmber,
                        textAlign = TextAlign.Center
                    )
                }
                ShieldState.ALARM_ACTIVE -> {
                    Text(
                        text = "! ! !",
                        style = MaterialTheme.typography.headlineLarge,
                        color = CyberRed.copy(alpha = pulseAlpha),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "INTRUDER DETECTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberRed,
                        textAlign = TextAlign.Center
                    )
                }
                ShieldState.AUTHENTICATED -> {
                    Text(
                        text = "✓",
                        fontSize = 30.sp,
                        color = CyberGreen,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "WELCOME BACK",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberGreen,
                        textAlign = TextAlign.Center
                    )
                }
                ShieldState.DISARMED -> {
                    Text(
                        text = "TAP TO ARM",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
