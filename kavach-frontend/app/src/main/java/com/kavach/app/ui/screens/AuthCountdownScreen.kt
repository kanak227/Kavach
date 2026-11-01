package com.kavach.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.ui.theme.CyberAmber
import com.kavach.app.ui.theme.CyberAmberGlow
import com.kavach.app.ui.theme.CyberRed
import com.kavach.app.ui.theme.CyberRedGlow
import com.kavach.app.ui.theme.DarkBackground
import com.kavach.app.ui.theme.OrbitronFamily

/**
 * Screen displayed during the authentication grace period (ShieldState.TRIGGERED).
 * Displays a large, urgent circular countdown timer giving the legitimate owner
 * time to authenticate via fingerprint, face, or PIN before the full 100% volume
 * alarm and strobe detonate.
 */
@Composable
fun AuthCountdownScreen(
    countdownSeconds: Int,
    totalSeconds: Int = 5,
    onDisarmAttempt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCritical = countdownSeconds <= 2
    val activeColor = if (isCritical) CyberRed else CyberAmber
    val glowColor = if (isCritical) CyberRedGlow else CyberAmberGlow

    val infiniteTransition = rememberInfiniteTransition(label = "authTimerPulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isCritical) 250 else 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isCritical) 250 else 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val progress = (countdownSeconds.toFloat() / totalSeconds.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        activeColor.copy(alpha = pulseAlpha * 0.35f),
                        DarkBackground,
                        activeColor.copy(alpha = pulseAlpha * 0.45f)
                    )
                )
            )
            .clickable { onDisarmAttempt() }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(activeColor.copy(alpha = 0.15f))
                    .border(1.5.dp, activeColor.copy(alpha = pulseAlpha), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LockClock,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCritical) "CRITICAL • ALARM IMMINENT" else "AUTHENTICATION REQUIRED",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp,
                    color = activeColor
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Big Circular Countdown Ring
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 12.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    val arcSize = Size(diameter, diameter)

                    // Track background ring
                    drawArc(
                        color = Color.White.copy(alpha = 0.10f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active progress sweep
                    drawArc(
                        color = activeColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Inner glow circle
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(glowColor.copy(alpha = 0.2f))
                        .border(2.dp, activeColor.copy(alpha = pulseAlpha * 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = countdownSeconds.coerceAtLeast(0).toString(),
                            fontFamily = OrbitronFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 68.sp,
                            color = activeColor,
                            lineHeight = 70.sp
                        )
                        Text(
                            text = "SECONDS",
                            fontFamily = OrbitronFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "MOTION DETECTED",
                fontFamily = OrbitronFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 2.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isCritical) {
                    "Hurry up! Alarm detonating at maximum volume in ${countdownSeconds}s!"
                } else {
                    "Disarm now with Fingerprint or PIN before full siren detonates"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCritical) CyberRed else Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                fontWeight = if (isCritical) FontWeight.Bold else FontWeight.Normal,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Prominent Authenticate Button
            Button(
                onClick = onDisarmAttempt,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeColor,
                    contentColor = DarkBackground
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .scale(pulseScale)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = DarkBackground
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "AUTHENTICATE TO DISARM",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                    color = DarkBackground
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Tap button or screen to verify identity",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

