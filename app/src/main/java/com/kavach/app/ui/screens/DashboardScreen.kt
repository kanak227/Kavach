package com.kavach.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.data.DetectionMode
import com.kavach.app.data.ShieldState
import com.kavach.app.data.SensorReading
import com.kavach.app.data.KavachSettings
import com.kavach.app.data.TimerPreset
import com.kavach.app.ui.components.*
import com.kavach.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    shieldState: ShieldState,
    remainingSeconds: Long,
    armingCountdown: Int,
    decoyCountdown: Int,
    sensorReading: SensorReading,
    settings: KavachSettings,
    hourlyUsage: Map<Int, Map<DetectionMode, Int>> = emptyMap(),
    isNightGuardDismissedForTonight: Boolean = false,
    isNightWindowActive: Boolean = false,
    onArmShield: () -> Unit,
    onDisarmShield: () -> Unit,
    onCancelNightGuardForTonight: () -> Unit = {},
    onResumeNightGuard: () -> Unit = {},
    isAutoPocketSessionActive: Boolean = false,
    onPauseTransitSession: () -> Unit = {},
    onResumeTransitSession: () -> Unit = {},
    onQuitAutoLoop: () -> Unit = {},
    onTimerPresetSelected: (TimerPreset) -> Unit,
    onSensitivityChanged: (Int) -> Unit,
    onSettingsChanged: (KavachSettings) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val colors = LocalKavachColors.current
    val context = LocalContext.current

    val remainingTime = remember(remainingSeconds) {
        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60
        if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }

    val countdownValue = when (shieldState) {
        ShieldState.ARMING -> armingCountdown
        ShieldState.TRIGGERED -> decoyCountdown
        else -> 0
    }

    // Pre-arm confirmation dialog
    var showArmDialog by remember { mutableStateOf(false) }

    if (showArmDialog) {
        val modeText = when (settings.detectionMode) {
            DetectionMode.TRAVEL -> "🚗 TRAVEL MODE ACTIVE\n\nDynamic adaptive multi-sensor protection.\n• Inside pocket/bag: Pocket Guard engages\n• Idle/stationary: Desk Guard auto-engages\n• Active hand use: dormant, no false alarms"
            DetectionMode.SLEEP -> "🌙 SLEEP MODE ACTIVE\n\nHigh-tolerance nocturnal protection.\nBed tosses, dreams, and mattress movements are completely ignored.\nAlarms only if lifted sky-high from baseline."
            DetectionMode.DESK -> "🚨 DESK MODE ACTIVE\n\nPlace phone on table or desk.\nInstant vertical lift detection. Horizontal surface sliding produces 0 force and will never trigger."
            DetectionMode.POCKET -> "🛡️ POCKET GUARD ACTIVE\n\nSlide phone into your pocket or bag during countdown.\nWalking rhythm is filtered out. Extraction jerk & light spike triggers the alarm.\nDisarm manually upon unlock."
        }
        AlertDialog(
            onDismissRequest = { showArmDialog = false },
            containerColor = colors.card,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = "⚠️ ARMING SHIELD",
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (colors.isDark) KavachGold else Color(0xFF00758F)
                )
            },
            text = {
                Column {
                    Text(
                        text = modeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val durationText = when (settings.selectedTimerPreset) {
                        TimerPreset.CUSTOM -> {
                            val h = settings.customTimerMinutes / 60
                            val m = settings.customTimerMinutes % 60
                            when {
                                h > 0 && m > 0 -> "$h hr $m min"
                                h > 0 -> if (h == 1) "1 hour" else "$h hours"
                                else -> "$m min"
                            }
                        }
                        TimerPreset.MIN_30 -> "30 min"
                        TimerPreset.HOUR_1 -> "1 hour"
                        TimerPreset.HOUR_2 -> "2 hours"
                    }
                    Text(
                        text = "Duration: $durationText",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyberAmber
                    )
                    Text(
                        text = "Countdown: ${settings.armingCountdownSeconds}s to place phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showArmDialog = false
                        onArmShield()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (colors.isDark) KavachGold else Color(0xFF00758F),
                        contentColor = if (colors.isDark) DarkBackground else Color.White
                    )
                ) {
                    Text("ARM NOW", fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showArmDialog = false }) {
                    Text("CANCEL", color = colors.textSecondary)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top bar — Premium App Name
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Exact Kavach Hawk Shield Circular Emblem - Borderless inside app
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = ImageBitmap.imageResource(id = com.kavach.app.R.drawable.kavach_logo_header),
                        contentDescription = "Kavach",
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "KAVA",
                            fontFamily = OrbitronFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = 2.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "CH",
                            fontFamily = OrbitronFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = 2.sp,
                            color = if (colors.isDark) KavachGold else Color(0xFFB45309)
                        )
                    }
                    Text(
                        text = "STEALTH SHIELD",
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 4.sp,
                        color = if (colors.isDark) KavachGold.copy(alpha = 0.85f) else Color(0xFF00758F)
                    )
                }
            }
            Row {
                // Theme toggle
                IconButton(onClick = {
                    onSettingsChanged(settings.copy(isDarkMode = !settings.isDarkMode))
                }) {
                    Icon(
                        imageVector = if (settings.isDarkMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = "Toggle Theme",
                        tint = colors.textSecondary
                    )
                }
            }
        }


        Spacer(modifier = Modifier.height(16.dp))

        // === HERO: Glowing Status Ring ===
        GlowingStatusRing(
            shieldState = shieldState,
            remainingTime = remainingTime,
            countdownValue = countdownValue,
            detectionMode = settings.detectionMode,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    when (shieldState) {
                        ShieldState.DISARMED -> showArmDialog = true
                        ShieldState.ARMED, ShieldState.ALARM_ACTIVE -> onDisarmShield()
                        else -> { /* Do nothing during transitions */ }
                    }
                }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // === ARM / DISARM Action (Shown during active states; Tap to Arm is on the ring) ===
        AnimatedVisibility(
            visible = shieldState != ShieldState.DISARMED,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            when (shieldState) {
                ShieldState.ARMING -> {
                    val cancelColor = if (colors.isDark) CyberAmber else LightAmber
                    OutlinedButton(
                        onClick = onDisarmShield,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = cancelColor
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, cancelColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "CANCEL ARMING",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                ShieldState.ARMED, ShieldState.TRIGGERED, ShieldState.ALARM_ACTIVE -> {
                    Button(
                        onClick = onDisarmShield,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (colors.isDark) CyberRed.copy(alpha = 0.2f) else CyberRed,
                            contentColor = if (colors.isDark) CyberRed else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = if (colors.isDark) androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.6f)) else null
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DISARM SHIELD",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                ShieldState.AUTHENTICATED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CyberGreen.copy(alpha = 0.15f))
                            .border(1.dp, CyberGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "IDENTITY VERIFIED",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyberGreen
                        )
                    }
                }
                else -> {}
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Detection Mode Selector (only when disarmed) ===
        AnimatedVisibility(
            visible = shieldState == ShieldState.DISARMED,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                SectionHeader(title = "DETECTION MODE", colors = colors)
                Spacer(modifier = Modifier.height(10.dp))
                DetectionModeSelector(
                    selectedMode = settings.detectionMode,
                    onModeSelected = { onSettingsChanged(settings.copy(detectionMode = it)) },
                    colors = colors
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // === Timer Presets (only show when disarmed) ===
        AnimatedVisibility(
            visible = shieldState == ShieldState.DISARMED,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                SectionHeader(title = "TIMER DURATION", colors = colors)
                Spacer(modifier = Modifier.height(10.dp))
                TimerPresetRow(
                    selectedPreset = settings.selectedTimerPreset,
                    onPresetSelected = onTimerPresetSelected,
                    colors = colors
                )

                // Custom timer input (with Close / Unsee option)
                if (settings.selectedTimerPreset == TimerPreset.CUSTOM) {
                    Spacer(modifier = Modifier.height(10.dp))
                    CustomTimerInput(
                        currentMinutes = settings.customTimerMinutes,
                        onMinutesChanged = { onSettingsChanged(settings.copy(customTimerMinutes = it)) },
                        onCloseCustom = { onTimerPresetSelected(TimerPreset.HOUR_2) },
                        colors = colors
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // === Sensor Status & Recommended Guidance (Only shows after guard starts / armed) ===
        AnimatedVisibility(
            visible = shieldState == ShieldState.ARMED || shieldState == ShieldState.TRIGGERED,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                SectionHeader(title = "SENSOR STATUS", colors = colors)
                Spacer(modifier = Modifier.height(10.dp))
                SensorStatusBar(
                    sensorReading = sensorReading,
                    isArmed = true,
                    detectionMode = settings.detectionMode,
                    colors = colors
                )
                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "RECOMMENDED USAGE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "DO NOT LOCK YOUR PHONE WHEN SHIELD IS ACTIVE. Keeping the screen on and app open is strongly recommended for instant decoy and trap defense.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "If you choose to keep your phone locked after activating the shield: when picking up your phone, immediately tap the \"Kavach Security Alert\" notification banner on your lock screen to authenticate. The verification window will be accessible directly without unlocking your phone first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // === Daily Guard Activity Graph (Moved from profile to bottom of home page) ===
        Spacer(modifier = Modifier.height(10.dp))
        SectionHeader(title = "DAILY GUARD ACTIVITY (9 AM – 9 PM)", colors = colors)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Hourly breakdown of all 4 Guard Modes active on your device today.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.height(12.dp))
        DailyGuardUsageGraph(
            hourlyUsage = hourlyUsage,
            colors = colors,
            isShieldArmed = (shieldState == ShieldState.ARMED)
        )

        // Bottom spacing for bottom navigation bar
        Spacer(modifier = Modifier.height(90.dp))
    }
}

@Composable
private fun SectionHeader(title: String, colors: KavachColors) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = colors.textSecondary,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DetectionModeSelector(
    selectedMode: DetectionMode,
    onModeSelected: (DetectionMode) -> Unit,
    colors: KavachColors
) {
    val activeColor = if (colors.isDark) KavachGold else Color(0xFF00758F)

    data class ModeItem(
        val mode: DetectionMode,
        val title: String,
        val subtitle: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
    )

    val modes = listOf(
        ModeItem(
            mode = DetectionMode.TRAVEL,
            title = "TRAVEL MODE",
            subtitle = "Dynamic pocket & idle auto-guard",
            icon = Icons.Outlined.DirectionsCar
        ),
        ModeItem(
            mode = DetectionMode.SLEEP,
            title = "SLEEP MODE",
            subtitle = "Bed-safe, ignores dream movement",
            icon = Icons.Outlined.Bedtime
        ),
        ModeItem(
            mode = DetectionMode.DESK,
            title = "DESK MODE",
            subtitle = "Instant Y-lift, zero slide trigger",
            icon = Icons.Outlined.Vibration
        ),
        ModeItem(
            mode = DetectionMode.POCKET,
            title = "POCKET GUARD",
            subtitle = "Single-arm pocket/bag extraction",
            icon = Icons.AutoMirrored.Outlined.DirectionsWalk
        )
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 2x2 Square Grid
        for (rowIndex in 0..1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (colIndex in 0..1) {
                    val item = modes[rowIndex * 2 + colIndex]
                    val isSelected = selectedMode == item.mode
                    val cardBg = if (isSelected) {
                        if (colors.isDark) KavachGold.copy(alpha = 0.16f) else Color(0xFF00758F).copy(alpha = 0.12f)
                    } else colors.card
                    val cardBorder = if (isSelected) activeColor else colors.cardBorder
                    val cardTint = if (isSelected) activeColor else colors.textSecondary

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f) // Makes each card a perfect square!
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .border(1.2.dp, cardBorder, RoundedCornerShape(16.dp))
                            .clickable { onModeSelected(item.mode) }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = cardTint,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.title,
                            fontFamily = OrbitronFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (isSelected) activeColor else colors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 13.sp,
                            fontSize = 10.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomTimerInput(
    currentMinutes: Int,
    onMinutesChanged: (Int) -> Unit,
    onCloseCustom: () -> Unit = {},
    colors: KavachColors
) {
    val activeColor = if (colors.isDark) KavachGold else Color(0xFF00758F)
    val inactiveTrackColor = if (colors.isDark) DarkSurfaceElevated else Color(0xFFCBD5E1)
    val chipBgColor = if (colors.isDark) KavachGold.copy(alpha = 0.15f) else Color(0xFF00758F).copy(alpha = 0.12f)

    // Discrete steps: 1 min, 5 min, then 15m increments up to 24 hours (1440 min)
    val customSteps = remember {
        listOf(1, 5) + (15..1440 step 15).toList()
    }

    val currentIndex = remember(currentMinutes) {
        val idx = customSteps.indexOfFirst { it >= currentMinutes }
        if (idx != -1) idx else customSteps.lastIndex
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CUSTOM TIMER",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Formatted hours & minutes
                val hours = currentMinutes / 60
                val mins = currentMinutes % 60
                val formatted = when {
                    hours > 0 && mins > 0 -> "${hours} hr ${mins} min"
                    hours > 0 -> if (hours == 1) "1 hour" else "$hours hours"
                    mins == 1 -> "1 minute"
                    else -> "$mins min"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(chipBgColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.labelLarge,
                        color = activeColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Close / Unsee Button
                IconButton(
                    onClick = onCloseCustom,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close Custom Timer Slider",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Precision Stepper Buttons & Slider (1m, 5m, 15m... up to 24 hours max)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val prevIndex = (currentIndex - 1).coerceAtLeast(0)
                    onMinutesChanged(customSteps[prevIndex])
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = "Decrease",
                    tint = activeColor
                )
            }

            Slider(
                value = currentIndex.toFloat(),
                onValueChange = { floatVal ->
                    val snappedIndex = Math.round(floatVal).toInt().coerceIn(0, customSteps.lastIndex)
                    onMinutesChanged(customSteps[snappedIndex])
                },
                valueRange = 0f..customSteps.lastIndex.toFloat(),
                steps = customSteps.size - 2,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                colors = SliderDefaults.colors(
                    thumbColor = activeColor,
                    activeTrackColor = activeColor,
                    inactiveTrackColor = inactiveTrackColor
                )
            )

            IconButton(
                onClick = {
                    val nextIndex = (currentIndex + 1).coerceAtMost(customSteps.lastIndex)
                    onMinutesChanged(customSteps[nextIndex])
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Increase",
                    tint = activeColor
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1m", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            Text("6h", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            Text("12h", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            Text("24h max", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
    }
}
