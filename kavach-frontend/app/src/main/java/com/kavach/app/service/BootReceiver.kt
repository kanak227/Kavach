package com.kavach.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives BOOT_COMPLETED and QUICKBOOT_POWERON broadcasts.
 * If the shield was armed before reboot, restarts the foreground service.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Check if shield was armed (persisted in SharedPreferences)
            val prefs = context.getSharedPreferences("kavach_prefs", Context.MODE_PRIVATE)
            val wasArmed = prefs.getBoolean("shield_was_armed", false)
            if (wasArmed) {
                val endTime = prefs.getLong("shield_armed_end_time", 0L)
                val now = System.currentTimeMillis()
                val remainingMinutes = if (endTime > now) {
                    ((endTime - now) / 60000L).toInt().coerceAtLeast(1)
                } else {
                    120
                }
                KavachForegroundService.arm(
                    context = context,
                    durationMinutes = remainingMinutes,
                    armingCountdownSeconds = 0,
                    skipCountdown = true
                )
            }
        }
    }
}
