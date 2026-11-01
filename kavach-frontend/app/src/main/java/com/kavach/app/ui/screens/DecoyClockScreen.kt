package com.kavach.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Authentication Grace Period Screen (formerly DecoyClockScreen).
 * When motion is detected while armed, this displays the urgent Auth Countdown Timer
 * allowing the owner to disarm with biometric or PIN credentials before the siren detonates.
 */
@Composable
fun DecoyClockScreen(
    countdownSeconds: Int = 5,
    totalSeconds: Int = 5,
    onScreenTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    AuthCountdownScreen(
        countdownSeconds = countdownSeconds,
        totalSeconds = totalSeconds,
        onDisarmAttempt = onScreenTapped,
        modifier = modifier
    )
}
