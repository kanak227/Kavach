package com.kavach.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Decoy Clock has been completely replaced by AlarmActiveScreen
 * so that when triggered, it immediately displays the emergency disarm screen directly.
 */
@Composable
fun DecoyClockScreen(
    onScreenTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlarmActiveScreen(
        onDisarmAttempt = onScreenTapped,
        modifier = modifier
    )
}
