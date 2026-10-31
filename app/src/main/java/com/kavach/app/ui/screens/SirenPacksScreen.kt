package com.kavach.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kavach.app.data.SirenPack
import com.kavach.app.ui.theme.*

/**
 * Siren Packs screen — shows available siren sounds.
 * Locked packs can be unlocked by watching a rewarded video ad.
 */
@Composable
fun SirenPacksScreen(
    selectedPackId: String,
    unlockedPacks: Set<String>,
    onPackSelected: (String) -> Unit,
    onWatchAdToUnlock: (SirenPack) -> Unit,
    onPreviewSound: (String) -> Unit,
    onStopPreview: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKavachColors.current
    var currentlyPlayingId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            onStopPreview()
        }
    }

    val sirenPacks = remember {
        listOf(
            SirenPack("police", "Police Interceptor", "Rapid alternating high-speed patrol siren", isUnlocked = true, isDefault = true),
            SirenPack("air_raid", "Cold War Air Raid", "Heavy rising mechanical civil defense siren", isUnlocked = true),
            SirenPack("biohazard", "Biohazard Protocol", "Pulsing high-urgency containment klaxon", isUnlocked = true),
            SirenPack("air_horn", "Stadium Air Horn", "Massive multi-harmonic train & stadium blast", isUnlocked = true),
            SirenPack("laser_alarm", "Cyberpunk Laser Grid", "Piercing sci-fi frequency sweep chirps", isUnlocked = true),
            SirenPack("sub_dive", "Submarine Dive Horn", "Deep resonant emergency ahooga klaxon", isUnlocked = true),
            SirenPack("bank_heist", "Vault Heist Alarm", "Industrial rapid-pulse metallic alarm bell", isUnlocked = true),
            SirenPack("alien_invasion", "Alien Mothership", "Deep modulating extraterrestrial beam", isUnlocked = true),
            SirenPack("fire_truck", "Euro Fire Klaxon", "Dual-tone punchy high-lo emergency siren", isUnlocked = true),
            SirenPack("system_alarm", "Device Default Alarm", "Standard Android system phone alarm ringtone", isUnlocked = true)
        )
    }

    val scrollState = rememberScrollState()

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
                text = "SIREN PACKS",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select any siren sound for your alarm. All premium siren packs are fully unlocked.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        sirenPacks.forEach { pack ->
            val isPlaying = currentlyPlayingId == pack.id
            SirenPackCard(
                pack = pack,
                isSelected = pack.id == selectedPackId,
                isPlaying = isPlaying,
                onSelect = { onPackSelected(pack.id) },
                onUnlock = { },
                onTogglePlay = {
                    if (isPlaying) {
                        onStopPreview()
                        currentlyPlayingId = null
                    } else {
                        onPreviewSound(pack.id)
                        currentlyPlayingId = pack.id
                    }
                },
                colors = colors
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun SirenPackCard(
    pack: SirenPack,
    isSelected: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onUnlock: () -> Unit,
    onTogglePlay: () -> Unit,
    colors: KavachColors
) {
    val activeGold = if (colors.isDark) KavachGold else Color(0xFF00758F)
    val activePurple = if (colors.isDark) CyberPurple else Color(0xFF7C3AED)
    val activeAmber = if (colors.isDark) CyberAmber else Color(0xFFB45309)

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> activeGold
            pack.isUnlocked -> colors.cardBorder
            else -> activePurple.copy(alpha = 0.4f)
        },
        label = "sirenBorder"
    )

    val bgColor = when {
        isSelected -> if (colors.isDark) KavachGold.copy(alpha = 0.10f) else Color(0xFF00758F).copy(alpha = 0.06f)
        else -> colors.card
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable {
                if (pack.isUnlocked) onSelect() else onUnlock()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon / Play button
        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isPlaying) activeAmber.copy(alpha = 0.18f)
                    else if (pack.isUnlocked) activeGold.copy(alpha = 0.14f)
                    else activePurple.copy(alpha = 0.14f)
                )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = if (isPlaying) "Stop Preview" else "Preview Siren",
                tint = if (isPlaying) activeAmber else if (pack.isUnlocked) activeGold else activePurple,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pack.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = activeGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = pack.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Selection / Unlock indicator
        if (pack.isUnlocked) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Selected",
                    tint = activeGold,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                TextButton(onClick = onSelect) {
                    Text(
                        text = "USE",
                        color = activeGold,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Watch ad button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyberPurple.copy(alpha = 0.15f))
                    .border(1.dp, CyberPurple.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { onUnlock() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        tint = CyberPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "UNLOCK",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberPurple
                    )
                }
            }
        }
    }
}
