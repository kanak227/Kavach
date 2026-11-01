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
    /** Travel mode: dynamic sensor shifts between pocket, desk (when idle), and dormant (when in active use) */
    TRAVEL(
        title = "Travel Mode",
        typeName = "Dynamic Travel",
        description = "Adaptive transit defense. Auto-activates Pocket Guard when stowed, Desk Guard if left stationary/idle, and stays silent when you actively use the device."
    ),
    /** Sleep mode: Y-axis vertical elevation detection requiring high, deliberate theft lift while filtering bed tosses and dreams */
    SLEEP(
        title = "Sleep Mode",
        typeName = "Bed & Nightstand",
        description = "High-tolerance nocturnal protection. Absorbs normal bed tosses, dreams, and mattress shifts; alarms only when lifted sky-high from baseline."
    ),
    /** Desk mode: instant Y-axis vertical lift detection off surface with zero horizontal slide triggers */
    DESK(
        title = "Desk Mode",
        typeName = "Table & Office",
        description = "Instant vertical lift monitoring for tables and desks. Triggers instantly upon pickup while 100% ignoring surface sliding."
    ),
    /** Pocket mode: single-arm standard pocket/bag protection with anti-walking filter and extraction fusion */
    POCKET(
        title = "Pocket Guard",
        typeName = "Pocket & Bag",
        description = "Arms once. Slide into pocket or bag. Filters walking rhythms and triggers upon extraction. Disarms manually with biometric auth."
    );

    val label: String get() = title

    companion object {
        // Compatibility alias for legacy MOTION references
        val MOTION: DetectionMode get() = DESK
    }
}

/**
 * Timer preset durations for quick arming.
 */
enum class TimerPreset(val label: String, val minutes: Int) {
    MIN_30("30 MIN", 30),
    HOUR_1("1 HOUR", 60),
    HOUR_2("2 HOURS", 120),
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
    val flashlightEnabled: Boolean = false, // By default OFF as requested
    val sensitivityLevel: Int = 5,  // 1-10 scale (default 5)
    val unlockedSirenPacks: Set<String> = setOf("system_alarm", "default", "police", "air_horn", "military", "nuclear", "tornado"),
    val detectionMode: DetectionMode = DetectionMode.TRAVEL,
    val decoyCountdownSeconds: Int = 5,  // User-configurable decoy timer (3-30s)
    val armingCountdownSeconds: Int = 5, // User-configurable arming countdown (3-15s)
    val alarmVolumePercent: Int = 0,     // Default 0 percent volume as requested
    val alarmTimeoutMinutes: Int = 0,    // 0 = Endless / Until Disarmed (default), 1 to 60 minutes
    val isDarkMode: Boolean = true,      // Dark mode by default
    val travelIdleMinutes: Int = 3,      // Configurable minutes of inactivity before Desk Guard activates in Travel Mode
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
