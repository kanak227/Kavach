package com.kavach.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import com.kavach.app.data.PowerButtonCombo
import com.kavach.app.data.KavachSettings
import com.kavach.app.ui.components.TelegramAlertCard
import com.kavach.app.ui.theme.*
import com.kavach.app.util.DeviceSecurityHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: KavachSettings,
    onSettingsChanged: (KavachSettings) -> Unit,
    onTestAlarm: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSirenPacks: () -> Unit = {},
    onNavigateToUserManual: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val colors = LocalKavachColors.current
    val activeAccent = if (colors.isDark) KavachGold else Color(0xFF00758F)

    // Expandable accordion menu tabs
    var expandedTimers by remember { mutableStateOf(false) }
    var expandedAppearance by remember { mutableStateOf(false) }
    var expandedAlarm by remember { mutableStateOf(false) }
    var expandedTravelMode by remember { mutableStateOf(false) }
    var expandedTelegram by remember { mutableStateOf(false) }
    var expandedTamper by remember { mutableStateOf(false) }
    var expandedDocumentation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "PREFERENCES & CONFIGURATION",
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            color = colors.textSecondary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 1. TIMERS MENU TAB
        SettingsMenuHeader(
            icon = Icons.Outlined.Timer,
            title = "Timers & Countdowns",
            subtitle = "Arming countdown & auth decoy timers",
            isExpanded = expandedTimers,
            onToggle = { expandedTimers = !expandedTimers },
            colors = colors,
            activeColor = activeAccent
        )
        if (expandedTimers) {
            Spacer(modifier = Modifier.height(8.dp))
            TimerConfigCard(
                armingCountdown = settings.armingCountdownSeconds,
                decoyCountdown = settings.decoyCountdownSeconds,
                onArmingCountdownChanged = {
                    onSettingsChanged(settings.copy(armingCountdownSeconds = it))
                },
                onDecoyCountdownChanged = {
                    onSettingsChanged(settings.copy(decoyCountdownSeconds = it))
                },
                colors = colors
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. APPEARANCE MENU TAB
        SettingsMenuHeader(
            icon = if (settings.isDarkMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
            title = "Appearance",
            subtitle = if (settings.isDarkMode) "Dark Theme active" else "Light Theme active",
            isExpanded = expandedAppearance,
            onToggle = { expandedAppearance = !expandedAppearance },
            colors = colors,
            activeColor = activeAccent
        )
        if (expandedAppearance) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsToggleItem(
                icon = if (settings.isDarkMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                title = if (settings.isDarkMode) "Dark Mode" else "Light Mode",
                subtitle = "Switch between dark and light theme",
                isEnabled = settings.isDarkMode,
                onToggle = { onSettingsChanged(settings.copy(isDarkMode = it)) },
                colors = colors
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. ALARM MENU TAB
        SettingsMenuHeader(
            icon = Icons.Outlined.VolumeUp,
            title = "Alarm & Siren Output",
            subtitle = "Volume ${settings.alarmVolumePercent}%, sound pack, timeout & test",
            isExpanded = expandedAlarm,
            onToggle = { expandedAlarm = !expandedAlarm },
            colors = colors,
            activeColor = activeAccent
        )
        if (expandedAlarm) {
            Spacer(modifier = Modifier.height(8.dp))
            AlarmVolumeCard(
                volumePercent = settings.alarmVolumePercent,
                onVolumeChanged = { onSettingsChanged(settings.copy(alarmVolumePercent = it)) },
                colors = colors
            )

            Spacer(modifier = Modifier.height(10.dp))

            AlarmDurationCard(
                timeoutMinutes = settings.alarmTimeoutMinutes,
                onTimeoutChanged = { onSettingsChanged(settings.copy(alarmTimeoutMinutes = it)) },
                colors = colors
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Outlined.VolumeUp,
                title = "Test Alarm",
                subtitle = "Play 3-second siren preview at ${settings.alarmVolumePercent}% volume",
                onClick = onTestAlarm,
                colors = colors
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Outlined.MusicNote,
                title = "Siren Packs",
                subtitle = "Choose alert sound (Custom sirens or Phone Default)",
                onClick = onNavigateToSirenPacks,
                colors = colors
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. TRAVEL MODE CONFIG MENU TAB
        SettingsMenuHeader(
            icon = Icons.Outlined.DirectionsCar,
            title = "Travel Mode Inactivity",
            subtitle = "Auto-engages Desk Guard after ${settings.travelIdleMinutes} min stationary",
            isExpanded = expandedTravelMode,
            onToggle = { expandedTravelMode = !expandedTravelMode },
            colors = colors,
            activeColor = activeAccent
        )
        if (expandedTravelMode) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stationary Idle Threshold",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${settings.travelIdleMinutes} min",
                            style = MaterialTheme.typography.labelLarge,
                            color = activeAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = settings.travelIdleMinutes.toFloat(),
                        onValueChange = { onSettingsChanged(settings.copy(travelIdleMinutes = it.roundToInt())) },
                        valueRange = 1f..15f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = activeAccent,
                            activeTrackColor = activeAccent
                        )
                    )
                    Text(
                        text = "When in Travel Mode, if your phone is not in a pocket/bag and left still for this duration, Desk Guard automatically engages to guard against table theft.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. TELEGRAM EMERGENCY ALERT MENU TAB
        SettingsMenuHeader(
            icon = Icons.Outlined.Send,
            title = "Telegram Emergency SOS",
            subtitle = if (settings.telegramAlert.isEnabled) "Telegram & Offline SMS armed" else "Configure bot & offline emergency SMS",
            isExpanded = expandedTelegram,
            onToggle = { expandedTelegram = !expandedTelegram },
            colors = colors,
            activeColor = activeAccent
        )
        if (expandedTelegram) {
            Spacer(modifier = Modifier.height(8.dp))
            TelegramAlertCard(
                config = settings.telegramAlert,
                onConfigChanged = { updatedConfig ->
                    onSettingsChanged(settings.copy(telegramAlert = updatedConfig))
                },
                colors = colors
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 6. TAMPER RESISTANCE MENU TAB
        SettingsMenuHeader(
            icon = Icons.Outlined.Security,
            title = "Tamper Resistance & Security",
            subtitle = "Lock-screen lockdown & hardware power defense",
            isExpanded = expandedTamper,
            onToggle = { expandedTamper = !expandedTamper },
            colors = colors,
            activeColor = activeAccent
        )
        if (expandedTamper) {
            Spacer(modifier = Modifier.height(8.dp))
            TamperResistanceCard(colors = colors)
            Spacer(modifier = Modifier.height(10.dp))
            HardwareShutdownDefenseCard(colors = colors)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 7. HELP & DOCUMENTATION MENU TAB
        SettingsMenuHeader(
            icon = Icons.Outlined.MenuBook,
            title = "Help & Documentation",
            subtitle = "User manual, 4-mode guide & multi-language docs",
            isExpanded = expandedDocumentation,
            onToggle = { expandedDocumentation = !expandedDocumentation },
            colors = colors,
            activeColor = activeAccent
        )
        if (expandedDocumentation) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsItem(
                icon = Icons.Outlined.MenuBook,
                title = "User Manual & Guide",
                subtitle = "Complete guide for Travel, Sleep, Desk and Pocket modes",
                onClick = onNavigateToUserManual,
                colors = colors
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Item
        SettingsItem(
            icon = Icons.Outlined.Info,
            title = "Kavach v1.1.0 (Alternate)",
            subtitle = "4-Mode Adaptive Stealth Anti-Theft Shield",
            onClick = { },
            colors = colors
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsMenuHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    colors: KavachColors,
    activeColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (isExpanded) activeColor.copy(alpha = 0.8f) else colors.cardBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onToggle),
        color = if (isExpanded) activeColor.copy(alpha = 0.08f) else colors.card
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isExpanded) activeColor.copy(alpha = 0.2f) else colors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isExpanded) activeColor else colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpanded) activeColor else colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    fontSize = 11.5.sp
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = if (isExpanded) activeColor else colors.textMuted
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String, colors: KavachColors) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = colors.textSecondary
    )
}

@OptIn(ExperimentalMaterial3Api::class)

@Composable
private fun TimeBox(
    label: String,
    hour: Int,
    minute: Int,
    onClick: () -> Unit,
    colors: KavachColors
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, CyberPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            Text(
                text = String.format("%d:%02d %s", displayHour, minute, amPm),
                style = MaterialTheme.typography.displaySmall,
                color = CyberPurple
            )
        }
    }
}

@Composable
private fun TimerConfigCard(
    armingCountdown: Int,
    decoyCountdown: Int,
    onArmingCountdownChanged: (Int) -> Unit,
    onDecoyCountdownChanged: (Int) -> Unit,
    colors: KavachColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        val activeColor = if (colors.isDark) KavachGold else Color(0xFF00758F)
        val activeAmber = activeColor

        // Arming countdown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Arming Countdown",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = "Time before shield activates",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (armingCountdown > 2) onArmingCountdownChanged(armingCountdown - 1) }) {
                    Icon(Icons.Outlined.Remove, "Decrease", tint = activeColor)
                }
                Text(
                    text = "${armingCountdown}s",
                    style = MaterialTheme.typography.titleLarge,
                    color = activeColor,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { if (armingCountdown < 30) onArmingCountdownChanged(armingCountdown + 1) }) {
                    Icon(Icons.Outlined.Add, "Increase", tint = activeColor)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colors.cardBorder)
        Spacer(modifier = Modifier.height(12.dp))

        // Decoy countdown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auth Countdown",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = "Time to authenticate before alarm",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (decoyCountdown > 2) onDecoyCountdownChanged(decoyCountdown - 1) }) {
                    Icon(Icons.Outlined.Remove, "Decrease", tint = activeAmber)
                }
                Text(
                    text = "${decoyCountdown}s",
                    style = MaterialTheme.typography.titleLarge,
                    color = activeAmber,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { if (decoyCountdown < 15) onDecoyCountdownChanged(decoyCountdown + 1) }) {
                    Icon(Icons.Outlined.Add, "Increase", tint = activeAmber)
                }
            }
        }
    }
}

@Composable
private fun AlarmVolumeCard(
    volumePercent: Int,
    onVolumeChanged: (Int) -> Unit,
    colors: KavachColors
) {
    val activeAccent = if (colors.isDark) KavachGold else Color(0xFF00758F)
    val activeDim = if (colors.isDark) KavachGoldDim else Color(0xFF00758F)
    val inactiveTrack = if (colors.isDark) DarkSurfaceElevated else Color(0xFFCBD5E1)

    Column(
        modifier = Modifier
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ALARM VOLUME",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = "Set volume level from silent test (0%) to blast (100%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
            Text(
                text = "$volumePercent%",
                style = MaterialTheme.typography.headlineSmall,
                color = if (volumePercent == 0) CyberRed else activeAccent,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Slider(
            value = volumePercent.toFloat(),
            onValueChange = { 
                val rounded = (Math.round(it / 5.0) * 5).toInt().coerceIn(0, 100)
                onVolumeChanged(rounded)
            },
            valueRange = 0f..100f,
            steps = 19, // 0%, 5%, 10% ... 100%
            colors = SliderDefaults.colors(
                thumbColor = if (volumePercent == 0) CyberRed else activeAccent,
                activeTrackColor = if (volumePercent == 0) CyberRed else activeAccent,
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
                text = "0% (Min)",
                style = MaterialTheme.typography.labelSmall,
                color = if (volumePercent == 0) CyberRed else colors.textMuted,
                fontWeight = if (volumePercent == 0) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = "50%",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted
            )
            Text(
                text = "100% (Max Siren)",
                style = MaterialTheme.typography.labelSmall,
                color = activeAccent,
                fontWeight = FontWeight.Bold
            )
        }

        if (volumePercent == 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyberRed.copy(alpha = 0.12f))
                    .border(1.dp, CyberRed.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = CyberRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Silent mode active: Sirens will NOT play during an alarm breach. Strongly recommended for testing purposes only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberRed,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AlarmDurationCard(
    timeoutMinutes: Int,
    onTimeoutChanged: (Int) -> Unit,
    colors: KavachColors
) {
    val activeAccent = if (colors.isDark) KavachGold else Color(0xFF00758F)
    val isEndless = timeoutMinutes == 0
    var sliderValue by remember {
        mutableFloatStateOf(if (timeoutMinutes > 0) timeoutMinutes.toFloat() else 5f)
    }
    LaunchedEffect(timeoutMinutes) {
        if (timeoutMinutes > 0) {
            sliderValue = timeoutMinutes.toFloat()
        }
    }

    Column(
        modifier = Modifier
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ALARM DURATION / TIMEOUT",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = if (isEndless) "Continuous siren until authenticated" else "Auto-silence after $timeoutMinutes ${if (timeoutMinutes == 1) "minute" else "minutes"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isEndless) activeAccent.copy(alpha = 0.15f) else activeAccent.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isEndless) activeAccent.copy(alpha = 0.5f) else activeAccent.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = if (isEndless) "ENDLESS" else "${timeoutMinutes} MIN",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isEndless) activeAccent else activeAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Security Advice Banner
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = (if (isEndless) activeAccent else activeAccent).copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isEndless) Icons.Outlined.Security else Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = if (isEndless) activeAccent else activeAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEndless) {
                        "Recommended for Maximum Security: Siren blares endlessly until disarmed. Thieves cannot wait out the alarm."
                    } else {
                        "Auto-Silence: Siren will automatically shut off after $timeoutMinutes minute${if (timeoutMinutes > 1) "s" else ""}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEndless) (if (colors.isDark) KavachGoldDim else colors.textPrimary) else activeAccent,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Toggle Option: Endless vs Custom Duration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Endless Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isEndless) activeAccent else colors.background,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isEndless) activeAccent else colors.cardBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTimeoutChanged(0) }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AllInclusive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isEndless) (if (colors.isDark) Color(0xFF041018) else Color.White) else colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Endless",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isEndless) FontWeight.Bold else FontWeight.Medium,
                        color = if (isEndless) (if (colors.isDark) Color(0xFF041018) else Color.White) else colors.textPrimary
                    )
                }
            }

            // Timed Mode Button (Defaults to 5 min if currently endless)
            val isTimed = !isEndless
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isTimed) activeAccent else colors.background,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isTimed) activeAccent else colors.cardBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (isEndless) onTimeoutChanged(5)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isTimed) (if (colors.isDark) Color(0xFF041018) else Color.White) else colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTimed) "$timeoutMinutes min" else "Timed Limit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isTimed) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTimed) (if (colors.isDark) Color(0xFF041018) else Color.White) else colors.textPrimary
                    )
                }
            }
        }

        // Interactive Slider (active when Timed is selected)
        if (!isEndless) {
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceElevated.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Exact Timeout Duration:",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val current = sliderValue.roundToInt()
                                if (current > 1) {
                                    val next = current - 1
                                    sliderValue = next.toFloat()
                                    onTimeoutChanged(next)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Remove,
                                contentDescription = "Decrease 1 Minute",
                                tint = activeAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = activeAccent.copy(alpha = 0.18f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, activeAccent.copy(alpha = 0.7f))
                        ) {
                            val mins = sliderValue.roundToInt().coerceIn(1, 60)
                            Text(
                                text = "$mins " + if (mins == 1) "Minute" else "Minutes",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = activeAccent
                            )
                        }
                        IconButton(
                            onClick = {
                                val current = sliderValue.roundToInt()
                                if (current < 60) {
                                    val next = current + 1
                                    sliderValue = next.toFloat()
                                    onTimeoutChanged(next)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "Increase 1 Minute",
                                tint = activeAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        onTimeoutChanged(sliderValue.roundToInt().coerceIn(1, 60))
                    },
                    valueRange = 1f..60f,
                    colors = SliderDefaults.colors(
                        thumbColor = activeAccent,
                        activeTrackColor = activeAccent,
                        inactiveTrackColor = colors.cardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 min", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, fontSize = 10.sp)
                    Text("15 min", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, fontSize = 10.sp)
                    Text("30 min", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, fontSize = 10.sp)
                    Text("45 min", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, fontSize = 10.sp)
                    Text("60 min", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    colors: KavachColors
) {
    val activeColor = if (colors.isDark) KavachGold else Color(0xFF00758F)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = activeColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    colors: KavachColors
) {
    val activeColor = if (colors.isDark) KavachGold else Color(0xFF00758F)
    val activeTrack = if (colors.isDark) KavachGoldDim.copy(alpha = 0.5f) else Color(0xFF00758F).copy(alpha = 0.4f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .clickable { onToggle(!isEnabled) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = activeColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = activeColor,
                checkedTrackColor = activeTrack,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceElevated
            )
        )
    }
}

@Composable
private fun TamperResistanceCard(
    colors: KavachColors
) {
    val accentColor = if (colors.isDark) KavachGold else Color(0xFF00758F)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        // 1. Header with Live Status Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = CyberGreen,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tamper Resistance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Active • Multi-Layer Device Guard",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }

            // Live status badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CyberGreen.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    CyberGreen.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(CyberGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyberGreen,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Kavach utilizes specialized system services and hardware locks to protect your phone against tampering and unauthorized silencing while armed.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Security Features Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TamperFeatureRow(
                icon = Icons.Outlined.Lock,
                title = "Volume Floor Lock",
                description = "Prevents thieves from pressing Volume Down to mute the siren.",
                colors = colors,
                accentColor = accentColor
            )
            TamperFeatureRow(
                icon = Icons.Outlined.RestartAlt,
                title = "Reboot Auto-Shield",
                description = "Automatically resumes active protection if the device is restarted.",
                colors = colors,
                accentColor = accentColor
            )
            TamperFeatureRow(
                icon = Icons.Outlined.NotificationsActive,
                title = "Persistent Foreground Guard",
                description = "Runs independently of recent apps so closing the app never drops protection.",
                colors = colors,
                accentColor = accentColor
            )
            TamperFeatureRow(
                icon = Icons.Outlined.Fingerprint,
                title = "Biometric Disarm Protocol",
                description = "Only your fingerprint or system screen PIN can silence the alarm.",
                colors = colors,
                accentColor = accentColor
            )
        }
    }
}

@Composable
private fun TamperFeatureRow(
    icon: ImageVector,
    title: String,
    description: String,
    colors: KavachColors,
    accentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.background.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}


@Composable
private fun HardwareShutdownDefenseCard(
    colors: KavachColors
) {
    val context = LocalContext.current
    val accentColor = if (colors.isDark) KavachGold else Color(0xFF00758F)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberGreen.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PowerSettingsNew,
                    contentDescription = null,
                    tint = CyberGreen,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hardware Anti-Shutdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "System Security Defense",
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CyberGreen.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberGreen.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "RECOMMENDED",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CyberGreen,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Require Lock Screen PIN / Password to Power Off your device (defeats thieves trying to turn off your phone instantly).",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = colors.background.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = CyberGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Enable 'Require Password to Power Off' in your phone's Security & Privacy settings to lock down power off buttons.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { DeviceSecurityHelper.openSecuritySettings(context) },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = if (colors.isDark) Color(0xFF041018) else Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "OPEN PHONE SETTINGS",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

