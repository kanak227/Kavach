package com.kavach.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kavach_settings")

/**
 * Persists user settings using Jetpack DataStore, with SharedPreferences sync
 * for instant, synchronous theme loading on cold start.
 */
class KavachPreferences(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)

    companion object {
        val TIMER_PRESET = stringPreferencesKey("timer_preset")
        val CUSTOM_TIMER_MINUTES = intPreferencesKey("custom_timer_minutes")
        val NIGHT_SCHEDULE_ENABLED = booleanPreferencesKey("night_schedule_enabled")
        val NIGHT_START_HOUR = intPreferencesKey("night_start_hour")
        val NIGHT_START_MINUTE = intPreferencesKey("night_start_minute")
        val NIGHT_END_HOUR = intPreferencesKey("night_end_hour")
        val NIGHT_END_MINUTE = intPreferencesKey("night_end_minute")
        val SELECTED_SIREN_PACK = stringPreferencesKey("selected_siren_pack")
        val POCKET_DETECTION = booleanPreferencesKey("pocket_detection")
        val LIGHT_THRESHOLD = floatPreferencesKey("light_threshold")
        val FLASHLIGHT_ENABLED = booleanPreferencesKey("flashlight_enabled")
        val SENSITIVITY_LEVEL = intPreferencesKey("sensitivity_level")
        val UNLOCKED_SIREN_PACKS = stringSetPreferencesKey("unlocked_siren_packs")
        val DETECTION_MODE = stringPreferencesKey("detection_mode")
        val DECOY_COUNTDOWN_SECONDS = intPreferencesKey("decoy_countdown_seconds")
        val ARMING_COUNTDOWN_SECONDS = intPreferencesKey("arming_countdown_seconds")
        val ALARM_VOLUME_PERCENT = intPreferencesKey("alarm_volume_percent")
        val ALARM_TIMEOUT_MINUTES = intPreferencesKey("alarm_timeout_minutes")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val TERMS_ACCEPTED = booleanPreferencesKey("terms_accepted")
        val POWER_BUTTON_COMBO = stringPreferencesKey("power_button_combo")
        val TELEGRAM_ALERT_ENABLED = booleanPreferencesKey("telegram_alert_enabled")
        val TELEGRAM_BOT_TOKEN = stringPreferencesKey("telegram_bot_token")
        val TELEGRAM_CHAT_ID = stringPreferencesKey("telegram_chat_id")
        val TELEGRAM_INTRUDER_PHOTOS = booleanPreferencesKey("telegram_intruder_photos")
        val TELEGRAM_BURST_COUNT = intPreferencesKey("telegram_burst_count")
        val SMS_FALLBACK_ENABLED = booleanPreferencesKey("sms_fallback_enabled")
        val EMERGENCY_PHONE_NUMBER = stringPreferencesKey("emergency_phone_number")
        val SMS_INTERVAL_SECONDS = intPreferencesKey("sms_interval_seconds")
        val TRAVEL_IDLE_MINUTES = intPreferencesKey("travel_idle_minutes")

        private const val KEY_CACHED_DARK_MODE = "cached_is_dark_mode"
        private const val KEY_TERMS_ACCEPTED = "cached_terms_accepted"
    }

    /**
     * Synchronously check if terms and conditions have been accepted.
     */
    fun isTermsAccepted(): Boolean {
        return sharedPrefs.getBoolean(KEY_TERMS_ACCEPTED, false)
    }

    suspend fun setTermsAccepted(accepted: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_TERMS_ACCEPTED, accepted).apply()
        context.dataStore.edit { prefs ->
            prefs[TERMS_ACCEPTED] = accepted
        }
    }

    /**
     * Synchronously read the cached settings on app launch so there is ZERO delay,
     * zero async race condition, and the user's saved volume is immediately active.
     */
    fun getInitialSettings(): KavachSettings {
        val isDark = sharedPrefs.getBoolean(KEY_CACHED_DARK_MODE, true)
        val vol = sharedPrefs.getInt("alarm_volume_percent", 100).coerceIn(0, 100)
        val modeStr = sharedPrefs.getString("detection_mode", DetectionMode.MOTION.name) ?: DetectionMode.MOTION.name
        val mode = try { DetectionMode.valueOf(modeStr) } catch (_: Exception) { DetectionMode.MOTION }
        val sens = sharedPrefs.getInt("sensitivity_level", 5)
        val armSecs = sharedPrefs.getInt("arming_countdown_seconds", 5)
        val decoySecs = sharedPrefs.getInt("decoy_countdown_seconds", 5)
        val pack = sharedPrefs.getString("selected_siren_pack_id", "default") ?: "default"
        val flash = sharedPrefs.getBoolean("flashlight_enabled", true)
        val comboStr = sharedPrefs.getString("power_button_combo", PowerButtonCombo.POWER_VOL_UP.name) ?: PowerButtonCombo.POWER_VOL_UP.name
        val combo = try { PowerButtonCombo.valueOf(comboStr) } catch (_: Exception) { PowerButtonCombo.POWER_VOL_UP }
        val nightEnabled = sharedPrefs.getBoolean("night_schedule_enabled", false)
        val nightStartH = sharedPrefs.getInt("night_start_hour", 23)
        val nightStartM = sharedPrefs.getInt("night_start_minute", 0)
        val nightEndH = sharedPrefs.getInt("night_end_hour", 7)
        val nightEndM = sharedPrefs.getInt("night_end_minute", 0)
        val timeoutMins = sharedPrefs.getInt("alarm_timeout_minutes", 0)
        val travelIdleMins = sharedPrefs.getInt("travel_idle_minutes", 3)
        val tgEnabled = sharedPrefs.getBoolean("telegram_alert_enabled", false)
        val tgBotToken = sharedPrefs.getString("telegram_bot_token", "") ?: ""
        val tgChatId = sharedPrefs.getString("telegram_chat_id", "") ?: ""
        val tgPhotos = sharedPrefs.getBoolean("telegram_intruder_photos", true)
        val tgBurst = sharedPrefs.getInt("telegram_burst_count", 5)
        val smsEnabled = sharedPrefs.getBoolean("sms_fallback_enabled", false)
        val emergencyPhone = sharedPrefs.getString("emergency_phone_number", "") ?: ""
        val smsInterval = sharedPrefs.getInt("sms_interval_seconds", 60).coerceIn(30, 600)

        return KavachSettings(
            isDarkMode = isDark,
            alarmVolumePercent = vol,
            alarmTimeoutMinutes = timeoutMins,
            detectionMode = mode,
            sensitivityLevel = sens,
            armingCountdownSeconds = armSecs,
            decoyCountdownSeconds = decoySecs,
            selectedSirenPackId = pack,
            flashlightEnabled = flash,
            powerButtonCombo = combo,
            nightSchedule = NightSchedule(
                enabled = nightEnabled,
                startHour = nightStartH,
                startMinute = nightStartM,
                endHour = nightEndH,
                endMinute = nightEndM
            ),
            telegramAlert = TelegramAlertConfig(
                isEnabled = tgEnabled,
                botToken = tgBotToken,
                chatId = tgChatId,
                intruderPhotosEnabled = tgPhotos,
                initialBurstCount = tgBurst,
                smsFallbackEnabled = smsEnabled,
                emergencyPhoneNumber = emergencyPhone,
                smsIntervalSeconds = smsInterval
            )
        )
    }

    val settingsFlow: Flow<KavachSettings> = context.dataStore.data.map { prefs ->
        val isDark = prefs[IS_DARK_MODE] ?: sharedPrefs.getBoolean(KEY_CACHED_DARK_MODE, true)
        val vol = prefs[ALARM_VOLUME_PERCENT] ?: sharedPrefs.getInt("alarm_volume_percent", 100).coerceIn(0, 100)
        val timeoutMins = prefs[ALARM_TIMEOUT_MINUTES] ?: sharedPrefs.getInt("alarm_timeout_minutes", 0)
        val travelIdleMins = prefs[TRAVEL_IDLE_MINUTES] ?: sharedPrefs.getInt("travel_idle_minutes", 3)
        val comboStr = prefs[POWER_BUTTON_COMBO] ?: sharedPrefs.getString("power_button_combo", PowerButtonCombo.POWER_VOL_UP.name) ?: PowerButtonCombo.POWER_VOL_UP.name
        val combo = try { PowerButtonCombo.valueOf(comboStr) } catch (_: Exception) { PowerButtonCombo.POWER_VOL_UP }
        val tgEnabled = prefs[TELEGRAM_ALERT_ENABLED] ?: sharedPrefs.getBoolean("telegram_alert_enabled", false)
        val tgBotToken = prefs[TELEGRAM_BOT_TOKEN] ?: sharedPrefs.getString("telegram_bot_token", "") ?: ""
        val tgChatId = prefs[TELEGRAM_CHAT_ID] ?: sharedPrefs.getString("telegram_chat_id", "") ?: ""
        val tgPhotos = prefs[TELEGRAM_INTRUDER_PHOTOS] ?: sharedPrefs.getBoolean("telegram_intruder_photos", true)
        val tgBurst = prefs[TELEGRAM_BURST_COUNT] ?: sharedPrefs.getInt("telegram_burst_count", 5)
        val smsEnabled = prefs[SMS_FALLBACK_ENABLED] ?: sharedPrefs.getBoolean("sms_fallback_enabled", false)
        val emergencyPhone = prefs[EMERGENCY_PHONE_NUMBER] ?: sharedPrefs.getString("emergency_phone_number", "") ?: ""
        val smsInterval = prefs[SMS_INTERVAL_SECONDS] ?: sharedPrefs.getInt("sms_interval_seconds", 60).coerceIn(30, 600)

        // Keep synchronous cache updated
        sharedPrefs.edit()
            .putBoolean(KEY_CACHED_DARK_MODE, isDark)
            .putInt("alarm_volume_percent", vol)
            .putInt("alarm_timeout_minutes", timeoutMins)
            .putString("power_button_combo", combo.name)
            .putBoolean("telegram_alert_enabled", tgEnabled)
            .putString("telegram_bot_token", tgBotToken)
            .putString("telegram_chat_id", tgChatId)
            .putBoolean("telegram_intruder_photos", tgPhotos)
            .putInt("telegram_burst_count", tgBurst)
            .putBoolean("sms_fallback_enabled", smsEnabled)
            .putString("emergency_phone_number", emergencyPhone)
            .putInt("sms_interval_seconds", smsInterval)
            .apply()

        KavachSettings(
            selectedTimerPreset = try {
                TimerPreset.valueOf(prefs[TIMER_PRESET] ?: TimerPreset.HOUR_2.name)
            } catch (e: Exception) {
                TimerPreset.HOUR_2
            },
            customTimerMinutes = prefs[CUSTOM_TIMER_MINUTES] ?: 120,
            nightSchedule = NightSchedule(
                enabled = prefs[NIGHT_SCHEDULE_ENABLED] ?: false,
                startHour = prefs[NIGHT_START_HOUR] ?: 23,
                startMinute = prefs[NIGHT_START_MINUTE] ?: 0,
                endHour = prefs[NIGHT_END_HOUR] ?: 7,
                endMinute = prefs[NIGHT_END_MINUTE] ?: 0
            ),
            powerButtonCombo = combo,
            selectedSirenPackId = prefs[SELECTED_SIREN_PACK] ?: "default",
            pocketDetectionEnabled = prefs[POCKET_DETECTION] ?: true,
            lightThresholdLux = prefs[LIGHT_THRESHOLD] ?: 8f,
            flashlightEnabled = prefs[FLASHLIGHT_ENABLED] ?: true,
            sensitivityLevel = prefs[SENSITIVITY_LEVEL] ?: 5,
            unlockedSirenPacks = prefs[UNLOCKED_SIREN_PACKS] ?: setOf("default", "police", "air_horn", "military", "nuclear", "tornado"),
            detectionMode = try {
                val modeStr = prefs[DETECTION_MODE] ?: DetectionMode.MOTION.name
                if (modeStr == "LIGHT") DetectionMode.POCKET else DetectionMode.valueOf(modeStr)
            } catch (e: Exception) {
                DetectionMode.MOTION
            },
            decoyCountdownSeconds = prefs[DECOY_COUNTDOWN_SECONDS] ?: 5,
            armingCountdownSeconds = prefs[ARMING_COUNTDOWN_SECONDS] ?: 5,
            alarmVolumePercent = prefs[ALARM_VOLUME_PERCENT] ?: 100,
            alarmTimeoutMinutes = timeoutMins,
            isDarkMode = isDark,
            travelIdleMinutes = travelIdleMins,
            telegramAlert = TelegramAlertConfig(
                isEnabled = tgEnabled,
                botToken = tgBotToken,
                chatId = tgChatId,
                intruderPhotosEnabled = tgPhotos,
                initialBurstCount = tgBurst,
                smsFallbackEnabled = smsEnabled,
                emergencyPhoneNumber = emergencyPhone,
                smsIntervalSeconds = smsInterval
            )
        )
    }

    suspend fun updateSettings(settings: KavachSettings) {
        // Update synchronous cache immediately with .commit() for zero-latency background receiver reads
        sharedPrefs.edit()
            .putBoolean(KEY_CACHED_DARK_MODE, settings.isDarkMode)
            .putBoolean("night_schedule_enabled", settings.nightSchedule.enabled)
            .putInt("night_start_hour", settings.nightSchedule.startHour)
            .putInt("night_start_minute", settings.nightSchedule.startMinute)
            .putInt("night_end_hour", settings.nightSchedule.endHour)
            .putInt("night_end_minute", settings.nightSchedule.endMinute)
            .putString("selected_siren_pack_id", settings.selectedSirenPackId)
            .putBoolean("flashlight_enabled", settings.flashlightEnabled)
            .putString("power_button_combo", settings.powerButtonCombo.name)
            .putInt("decoy_countdown_seconds", settings.decoyCountdownSeconds)
            .putString("detection_mode", settings.detectionMode.name)
            .putInt("sensitivity_level", settings.sensitivityLevel)
            .putInt("arming_countdown_seconds", settings.armingCountdownSeconds)
            .putInt("alarm_volume_percent", settings.alarmVolumePercent)
            .putInt("alarm_timeout_minutes", settings.alarmTimeoutMinutes)
            .putInt("travel_idle_minutes", settings.travelIdleMinutes)
            .putBoolean("telegram_alert_enabled", settings.telegramAlert.isEnabled)
            .putString("telegram_bot_token", settings.telegramAlert.botToken)
            .putString("telegram_chat_id", settings.telegramAlert.chatId)
            .putBoolean("telegram_intruder_photos", settings.telegramAlert.intruderPhotosEnabled)
            .putInt("telegram_burst_count", settings.telegramAlert.initialBurstCount)
            .putBoolean("sms_fallback_enabled", settings.telegramAlert.smsFallbackEnabled)
            .putString("emergency_phone_number", settings.telegramAlert.emergencyPhoneNumber)
            .putInt("sms_interval_seconds", settings.telegramAlert.smsIntervalSeconds)
            .commit()

        context.dataStore.edit { prefs ->
            prefs[TIMER_PRESET] = settings.selectedTimerPreset.name
            prefs[CUSTOM_TIMER_MINUTES] = settings.customTimerMinutes
            prefs[NIGHT_SCHEDULE_ENABLED] = settings.nightSchedule.enabled
            prefs[NIGHT_START_HOUR] = settings.nightSchedule.startHour
            prefs[NIGHT_START_MINUTE] = settings.nightSchedule.startMinute
            prefs[NIGHT_END_HOUR] = settings.nightSchedule.endHour
            prefs[NIGHT_END_MINUTE] = settings.nightSchedule.endMinute
            prefs[POWER_BUTTON_COMBO] = settings.powerButtonCombo.name
            prefs[SELECTED_SIREN_PACK] = settings.selectedSirenPackId
            prefs[POCKET_DETECTION] = settings.pocketDetectionEnabled
            prefs[LIGHT_THRESHOLD] = settings.lightThresholdLux
            prefs[FLASHLIGHT_ENABLED] = settings.flashlightEnabled
            prefs[SENSITIVITY_LEVEL] = settings.sensitivityLevel
            prefs[UNLOCKED_SIREN_PACKS] = settings.unlockedSirenPacks
            prefs[DETECTION_MODE] = settings.detectionMode.name
            prefs[TRAVEL_IDLE_MINUTES] = settings.travelIdleMinutes
            prefs[DECOY_COUNTDOWN_SECONDS] = settings.decoyCountdownSeconds
            prefs[ARMING_COUNTDOWN_SECONDS] = settings.armingCountdownSeconds
            prefs[ALARM_VOLUME_PERCENT] = settings.alarmVolumePercent
            prefs[ALARM_TIMEOUT_MINUTES] = settings.alarmTimeoutMinutes
            prefs[IS_DARK_MODE] = settings.isDarkMode
            prefs[TELEGRAM_ALERT_ENABLED] = settings.telegramAlert.isEnabled
            prefs[TELEGRAM_BOT_TOKEN] = settings.telegramAlert.botToken
            prefs[TELEGRAM_CHAT_ID] = settings.telegramAlert.chatId
            prefs[TELEGRAM_INTRUDER_PHOTOS] = settings.telegramAlert.intruderPhotosEnabled
            prefs[TELEGRAM_BURST_COUNT] = settings.telegramAlert.initialBurstCount
            prefs[SMS_FALLBACK_ENABLED] = settings.telegramAlert.smsFallbackEnabled
            prefs[EMERGENCY_PHONE_NUMBER] = settings.telegramAlert.emergencyPhoneNumber
            prefs[SMS_INTERVAL_SECONDS] = settings.telegramAlert.smsIntervalSeconds
        }
    }

    suspend fun unlockSirenPack(packId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[UNLOCKED_SIREN_PACKS] ?: setOf("default")
            prefs[UNLOCKED_SIREN_PACKS] = current + packId
        }
    }

    suspend fun updateTimerPreset(preset: TimerPreset) {
        context.dataStore.edit { prefs ->
            prefs[TIMER_PRESET] = preset.name
        }
    }

    suspend fun updateSensitivity(level: Int) {
        val lvl = level.coerceIn(1, 10)
        sharedPrefs.edit().putInt("sensitivity_level", lvl).commit()
        context.dataStore.edit { prefs ->
            prefs[SENSITIVITY_LEVEL] = lvl
        }
    }
}
