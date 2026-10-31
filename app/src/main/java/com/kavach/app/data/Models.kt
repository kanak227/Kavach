package com.kavach.app.data

/**
 * Represents the current state of the Kavach shield.
 */
enum class ShieldState {
    /** Shield is off / not armed */
    DISARMED,
    /** Shield is in the process of arming (countdown) */
    ARMING,
    /** Shield is fully armed and monitoring sensors */
    ARMED,
    /** Motion detected — decoy screen showing, waiting for auth */
    TRIGGERED,
    /** Authentication failed — full alarm blasting */
    ALARM_ACTIVE,
    /** User authenticated successfully — disarming */
    AUTHENTICATED
}

/**
 * Detection mode — how the shield detects unauthorized access.
 */
enum class DetectionMode(
    val title: String,
    val typeName: String,
    val description: String
) {
    /** Stationary protection for tables, desks, bedside nightstand, charging */
    MOTION(
        title = "Desk Guard",
        typeName = "Stationary",
        description = "Guards phones on tables or bedside. Uses vertical lift sensors to 100% ignore horizontal sliding."
    ),
    /** Standard one-time pocket protection: arms once, disarms upon unlock */
    POCKET(
        title = "Pocket Guard",
        typeName = "Single-Arm (Standard)",
        description = "Arms once. Place in pocket/bag. Disarms permanently upon biometric unlock."
    ),
    /** Continuous transit commute mode: stays dormant while phone is in use, auto re-arms on screen-off */
    AUTO_POCKET(
        title = "Pocket Guard",
        typeName = "Auto-Loop Commuter",
        description = "Arms once in pocket. Dormant while phone is on. Auto-re-arms whenever screen is locked."
    );

    val label: String get() = if (this == MOTION) title else "$title ($typeName)"
}

/**
 * Timer preset durations for quick arming.
 */
enum class TimerPreset(val label: String, val minutes: Int) {
    MIN_30("30 MIN", 30),
    HOUR_1("1 HOUR", 60),
    HOUR_2("2 HOURS", 120),
    HOUR_4("4 HOURS", 240),
    CUSTOM("CUSTOM", -1)
}

/**
 * Available siren sound packs.
 */
data class SirenPack(
    val id: String,
    val name: String,
    val description: String,
    val isUnlocked: Boolean = false,
    val isDefault: Boolean = false
)

/**
 * Night schedule configuration.
 * Start range: 8:00 PM (20:00) to 11:59 PM (23:59)
 * End range: 12:00 AM (0:00) to 11:59 AM (11:59)
 */
data class NightSchedule(
    val enabled: Boolean = false,
    val startHour: Int = 23,  // 11 PM
    val startMinute: Int = 0,
    val endHour: Int = 7,     // 7 AM
    val endMinute: Int = 0
)

/**
 * Hardware power menu button combinations used by different phone manufacturers
 * to trigger the power off / restart menu.
 */
enum class PowerButtonCombo(val label: String, val description: String) {
    POWER_VOL_UP("Power + Volume Up", "Default for OnePlus, Pixel, Oppo, Realme, Xiaomi"),
    POWER_VOL_DOWN("Power + Volume Down", "Common for Samsung, Motorola"),
    BOTH("Both (Vol Up & Vol Down)", "Blocks all volume buttons with power"),
    NONE("Disabled", "Do not intercept volume keys")
}

/**
 * Sensor reading for pocket detection.
 */
data class SensorReading(
    val lightLevel: Float = 0f,         // Lux
    val proximityCovered: Boolean = false,
    val isInPocket: Boolean = false,
    val motionMagnitude: Float = 0f
)

/**
 * Overall app settings.
 */
data class KavachSettings(
    val selectedTimerPreset: TimerPreset = TimerPreset.HOUR_2,
    val customTimerMinutes: Int = 120,
    val nightSchedule: NightSchedule = NightSchedule(),
    val powerButtonCombo: PowerButtonCombo = PowerButtonCombo.POWER_VOL_UP,
    val selectedSirenPackId: String = "default",
    val pocketDetectionEnabled: Boolean = true,
    val lightThresholdLux: Float = 8f,
    val flashlightEnabled: Boolean = true,
    val sensitivityLevel: Int = 5,  // 1-10 scale
    val unlockedSirenPacks: Set<String> = setOf("system_alarm", "default", "police", "air_horn", "military", "nuclear", "tornado"),
    val detectionMode: DetectionMode = DetectionMode.MOTION,
    val decoyCountdownSeconds: Int = 5,  // User-configurable decoy timer (3-30s)
    val armingCountdownSeconds: Int = 5, // User-configurable arming countdown (3-15s)
    val alarmVolumePercent: Int = 100,   // Alarm sound volume level (5% - 100%)
    val alarmTimeoutMinutes: Int = 0,    // 0 = Endless / Until Disarmed (default), 1 to 60 minutes
    val isDarkMode: Boolean = true,      // Dark mode by default
    val telegramAlert: TelegramAlertConfig = TelegramAlertConfig()
)

/**
 * Configuration for Telegram emergency location & intruder photo alerts.
 */
data class TelegramAlertConfig(
    val isEnabled: Boolean = false,
    val botToken: String = "",
    val chatId: String = "",
    val intruderPhotosEnabled: Boolean = true,
    val initialBurstCount: Int = 5,
    val smsFallbackEnabled: Boolean = false,
    val emergencyPhoneNumber: String = "",
    val smsIntervalSeconds: Int = 60
)
