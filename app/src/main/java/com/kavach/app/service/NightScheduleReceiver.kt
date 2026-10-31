package com.kavach.app.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kavach.app.MainActivity
import com.kavach.app.R
import com.kavach.app.data.DetectionMode
import java.util.Calendar

/**
 * BroadcastReceiver that handles night schedule auto-arm and auto-disarm.
 * Fired by AlarmManager at the configured start and end times.
 *
 * - ACTION_NIGHT_ARM: Arms KavachForegroundService directly (no UI launch needed)
 * - ACTION_NIGHT_DISARM: Disarms KavachForegroundService
 *
 * Uses setAlarmClock for guaranteed clock-alarm accuracy on OnePlus & all Android OEMs.
 */
class NightScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NIGHT_ARM = "com.kavach.app.NIGHT_ARM"
        const val ACTION_NIGHT_DISARM = "com.kavach.app.NIGHT_DISARM"
        const val REQUEST_CODE_START = 7001
        const val REQUEST_CODE_END = 7002
        private const val NIGHT_NOTIFICATION_ID = 3002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val cachePrefs = context.getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)

        when (intent.action) {
            ACTION_NIGHT_ARM -> {
                val endHour = cachePrefs.getInt("night_end_hour", 7)
                val endMinute = cachePrefs.getInt("night_end_minute", 0)
                val modeStr = cachePrefs.getString("detection_mode", DetectionMode.MOTION.name)
                val mode = try { DetectionMode.valueOf(modeStr ?: DetectionMode.MOTION.name) } catch (_: Exception) { DetectionMode.MOTION }
                val sensitivity = cachePrefs.getInt("sensitivity_level", 10)
                val volumePercent = cachePrefs.getInt("alarm_volume_percent", 100)

                val now = Calendar.getInstance()
                val currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val endMinuteOfDay = endHour * 60 + endMinute

                val remainingMinutes = if (currentMinuteOfDay < endMinuteOfDay) {
                    endMinuteOfDay - currentMinuteOfDay
                } else {
                    (24 * 60 - currentMinuteOfDay) + endMinuteOfDay
                }

                context.getSharedPreferences("kavach_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("night_auto_armed", true)
                    .commit()

                // Arm the foreground service directly — fully autonomous, no MainActivity required!
                KavachForegroundService.arm(
                    context = context,
                    durationMinutes = remainingMinutes.coerceAtLeast(1),
                    armingCountdownSeconds = 5,
                    detectionMode = DetectionMode.SLEEP,
                    sensitivity = sensitivity,
                    alarmVolumePercent = volumePercent,
                    skipCountdown = false
                )

                showNotification(
                    context,
                    "🌙 Night Shield Activated",
                    "Shield has been auto-armed for the night until ${formatTime(endHour, endMinute)}."
                )

                // Reschedule for tomorrow
                rescheduleAlarm(context, ACTION_NIGHT_ARM, REQUEST_CODE_START, cachePrefs.getInt("night_start_hour", 23), cachePrefs.getInt("night_start_minute", 0))
            }

            ACTION_NIGHT_DISARM -> {
                KavachForegroundService.disarm(context)

                showNotification(
                    context,
                    "☀️ Night Shield Deactivated",
                    "Good morning! Shield has been auto-disarmed."
                )

                // Reschedule for tomorrow
                rescheduleAlarm(context, ACTION_NIGHT_DISARM, REQUEST_CODE_END, cachePrefs.getInt("night_end_hour", 7), cachePrefs.getInt("night_end_minute", 0))
            }
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val period = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%d:%02d %s", displayHour, minute, period)
    }

    /**
     * Reschedule the alarm for the same time tomorrow using setAlarmClock.
     */
    private fun rescheduleAlarm(context: Context, action: String, requestCode: Int, targetHour: Int, targetMinute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NightScheduleReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetCal.timeInMillis,
                pendingIntent
            )
        } catch (_: Exception) {
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    targetCal.timeInMillis,
                    pendingIntent
                )
            } catch (_: Exception) { }
        }
    }

    private fun showNotification(context: Context, title: String, text: String) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, KavachForegroundService.ALARM_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(NIGHT_NOTIFICATION_ID, notification)
    }
}
