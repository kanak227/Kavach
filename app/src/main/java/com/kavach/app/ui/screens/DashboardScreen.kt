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
    onNavigateToSettings: () -> Unit,
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
            DetectionMode.MOTION -> "🚨 DESK GUARD ACTIVE\n\nPlace your phone on a table, desk, or bedside.\nDo NOT touch or move it once armed.\n\nAny physical movement will trigger the alarm."
            DetectionMode.POCKET -> "🛡️ MANUAL POCKET GUARD ACTIVE\n\nSlide the phone into your pocket or bag during the countdown.\nWalking is ignored. Extraction triggers the alarm.\nDisarms permanently once you unlock."
            DetectionMode.AUTO_POCKET -> "🚇 SMART TRANSIT GUARD ACTIVE\n\nDesigned for metro, bus & train commutes!\nWhen you unlock your phone to check messages or calls, the guard remains dormant.\nAs soon as you turn off / lock your screen, it automatically re-arms!"
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
                        TimerPreset.HOUR_4 -> "4 hours"
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
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
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

        // === ARM / DISARM Button ===
        AnimatedContent(
            targetState = shieldState,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "armButton"
        ) { state ->
            when (state) {
                ShieldState.DISARMED -> {
                    Button(
                        onClick = { showArmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (colors.isDark) KavachGold.copy(alpha = 0.22f) else Color(0xFF00758F),
                            contentColor = if (colors.isDark) KavachGold else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = if (colors.isDark) androidx.compose.foundation.BorderStroke(1.2.dp, KavachGold.copy(alpha = 0.7f)) else null
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ARM SHIELD",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
                            text = "CANCEL",
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
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Detection Mode Toggle (only when disarmed) ===
        AnimatedVisibility(
            visible = shieldState == ShieldState.DISARMED,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                // === Pocket Guard (Auto-Loop Commuter) Active Session Card ===
        if (isAutoPocketSessionActive && shieldState == ShieldState.DISARMED) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (colors.isDark) Color(0xFF131A26) else Color(0xFFF1F5F9))
                    .border(1.2.dp, if (colors.isDark) KavachGold.copy(alpha = 0.6f) else Color(0xFF00758F).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Loop,
                            contentDescription = null,
                            tint = if (colors.isDark) KavachGold else Color(0xFF00758F),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "POCKET GUARD (AUTO-LOOP COMMUTER) ACTIVE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Phone is in use. Kavach remains dormant while your screen is on. As soon as you press the power button or turn off the screen, it will automatically re-arm in your pocket!",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onQuitAutoLoop() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (colors.isDark) CyberRed.copy(alpha = 0.22f) else CyberRed.copy(alpha = 0.12f),
                            contentColor = CyberRed
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AUTHENTICATE & CLOSE AUTO-LOOP FOR THIS SESSION", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

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

                // Custom timer input
                if (settings.selectedTimerPreset == TimerPreset.CUSTOM) {
                    Spacer(modifier = Modifier.height(10.dp))
                    CustomTimerInput(
                        currentMinutes = settings.customTimerMinutes,
                        onMinutesChanged = { onSettingsChanged(settings.copy(customTimerMinutes = it)) },
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

        // === Feature Toggles (only show when disarmed) ===
        AnimatedVisibility(
            visible = shieldState == ShieldState.DISARMED,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "FEATURES", colors = colors)
                // Spacer(modifier = Modifier.height(10.dp)) not needed here since verticalArrangement handles it

                FeatureToggleCard(
                    title = "Flashlight Strobe",
                    description = "Flash torch rapidly during alarm",
                    isEnabled = settings.flashlightEnabled,
                    onToggle = { onSettingsChanged(settings.copy(flashlightEnabled = it)) },
                    colors = colors
                )

                SensitivityCard(
                    level = settings.sensitivityLevel,
                    onLevelChange = onSensitivityChanged,
                    detectionMode = settings.detectionMode,
                    colors = colors
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Bottom spacing for ad banner
        Spacer(modifier = Modifier.height(70.dp))
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
    val isDeskSelected = selectedMode == DetectionMode.MOTION
    val isPocketSelected = selectedMode == DetectionMode.POCKET || selectedMode == DetectionMode.AUTO_POCKET
    val activeColor = if (colors.isDark) KavachGold else Color(0xFF00758F)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Two Primary Mode Cards: Desk Guard vs Pocket Guard
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Desk Guard Primary Card
            val deskBg = if (isDeskSelected) {
                if (colors.isDark) KavachGold.copy(alpha = 0.16f) else Color(0xFF00758F).copy(alpha = 0.12f)
            } else colors.card
            val deskBorder = if (isDeskSelected) activeColor else colors.cardBorder
            val deskTint = if (isDeskSelected) activeColor else colors.textSecondary

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(deskBg)
                    .border(1.dp, deskBorder, RoundedCornerShape(16.dp))
                    .clickable { onModeSelected(DetectionMode.MOTION) }
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Vibration,
                    contentDescription = "Desk Guard",
                    tint = deskTint,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "DESK GUARD",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isDeskSelected) activeColor else colors.textPrimary,
                    fontWeight = if (isDeskSelected) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Table & Bedside (Zero false alarms from horizontal sliding)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    fontSize = 10.5.sp
                )
            }

            // Pocket Guard Primary Card
            val pocketBg = if (isPocketSelected) {
                if (colors.isDark) KavachGold.copy(alpha = 0.16f) else Color(0xFF00758F).copy(alpha = 0.12f)
            } else colors.card
            val pocketBorder = if (isPocketSelected) activeColor else colors.cardBorder
            val pocketTint = if (isPocketSelected) activeColor else colors.textSecondary

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(pocketBg)
                    .border(1.dp, pocketBorder, RoundedCornerShape(16.dp))
                    .clickable {
                        // Keep current sub-mode or default to POCKET
                        if (!isPocketSelected) onModeSelected(DetectionMode.POCKET)
                    }
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.DirectionsWalk,
                    contentDescription = "Pocket Guard",
                    tint = pocketTint,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "POCKET GUARD",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isPocketSelected) activeColor else colors.textPrimary,
                    fontWeight = if (isPocketSelected) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pocket & Commuting (Walking rhythm filter + extraction detection)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    fontSize = 10.5.sp
                )
            }
        }

        // When Pocket Guard is selected: show the two operating types
        if (isPocketSelected) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = colors.card,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "CHOOSE POCKET GUARD TYPE:",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Type 1: Standard (Single-Arm)
                    val isType1 = selectedMode == DetectionMode.POCKET
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isType1) activeColor.copy(alpha = 0.12f) else Color.Transparent)
                            .border(1.dp, if (isType1) activeColor else colors.cardBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { onModeSelected(DetectionMode.POCKET) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isType1,
                            onClick = { onModeSelected(DetectionMode.POCKET) },
                            colors = RadioButtonDefaults.colors(selectedColor = activeColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Standard (Single-Arm)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isType1) activeColor else colors.textPrimary
                            )
                            Text(
                                text = "Arms once. Place in pocket. Disarms permanently upon biometric unlock.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Type 2: Auto-Loop Commuter
                    val isType2 = selectedMode == DetectionMode.AUTO_POCKET
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isType2) activeColor.copy(alpha = 0.12f) else Color.Transparent)
                            .border(1.dp, if (isType2) activeColor else colors.cardBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { onModeSelected(DetectionMode.AUTO_POCKET) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isType2,
                            onClick = { onModeSelected(DetectionMode.AUTO_POCKET) },
                            colors = RadioButtonDefaults.colors(selectedColor = activeColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Auto-Loop Commuter (Auto Re-Arm)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isType2) activeColor else colors.textPrimary
                                )
                            }
                            Text(
                                text = "Dormant while phone is on. As soon as you turn off the screen, it automatically re-arms in your pocket so you never need to re-arm manually!",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
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
            Text(
                text = "CUSTOM TIMER",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )

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
