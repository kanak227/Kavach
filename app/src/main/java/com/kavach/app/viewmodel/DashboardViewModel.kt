package com.kavach.app.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.R
import com.kavach.app.alarm.AlarmController
import com.kavach.app.data.*
import com.kavach.app.sensor.SensorFusion
import com.kavach.app.service.NightScheduleReceiver
import com.kavach.app.service.KavachForegroundService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Calendar

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = KavachPreferences(application)
    private val sensorFusion = SensorFusion(application)
    val alarmController = AlarmController(application)
    val profileRepository = UserProfileRepository(application)

    val userProfile: StateFlow<UserProfile> = profileRepository.userProfileFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            profileRepository.getInitialProfile()
        )

    private val _hourlyUsage = MutableStateFlow(profileRepository.getTodayHourlyUsage())
    val hourlyUsage: StateFlow<Map<Int, Map<DetectionMode, Int>>> = _hourlyUsage.asStateFlow()

    fun saveUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            profileRepository.saveProfile(profile)
        }
    }

    fun refreshUsageStats() {
        _hourlyUsage.value = profileRepository.getTodayHourlyUsage()
    }

    // === UI State ===
    private val _shieldState = MutableStateFlow(ShieldState.DISARMED)
    val shieldState: StateFlow<ShieldState> = _shieldState.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _decoyCountdown = MutableStateFlow(5)
    val decoyCountdown: StateFlow<Int> = _decoyCountdown.asStateFlow()

    private val _armingCountdown = MutableStateFlow(5)
    val armingCountdown: StateFlow<Int> = _armingCountdown.asStateFlow()

    val sensorReading: StateFlow<SensorReading> = sensorFusion.sensorReading

    val settings: StateFlow<KavachSettings> = preferences.settingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            preferences.getInitialSettings()
        )

    // Internal jobs
    private var timerJob: Job? = null
    private var monitorJob: Job? = null
    private var decoyJob: Job? = null
    private var armingJob: Job? = null
    private var nightScheduleJob: Job? = null
    private var alarmTimeoutJob: Job? = null

    // Track if user authenticated during decoy
    private val _authenticationSuccess = MutableStateFlow(false)

    private var userDisarmedNightWindow: Boolean = false
    private var isTransitSessionPausedByUser: Boolean = false
    private var screenReceiver: android.content.BroadcastReceiver? = null

    private val _isNightGuardDismissedForTonight = MutableStateFlow(false)
    val isNightGuardDismissedForTonight: StateFlow<Boolean> = _isNightGuardDismissedForTonight.asStateFlow()

    private val _isNightWindowActive = MutableStateFlow(false)
    val isNightWindowActive: StateFlow<Boolean> = _isNightWindowActive.asStateFlow()

    private val _isAutoPocketSessionActive = MutableStateFlow(false)
    val isAutoPocketSessionActive: StateFlow<Boolean> = _isAutoPocketSessionActive.asStateFlow()

    init {
        val app = getApplication<Application>()
        val prefs = app.getSharedPreferences("kavach_prefs", Context.MODE_PRIVATE)
        val wasArmed = prefs.getBoolean("shield_was_armed", false)
        val isAlarming = prefs.getBoolean("shield_is_alarming", false)
        val endTime = prefs.getLong("shield_armed_end_time", 0L)
        val armingEndTime = prefs.getLong("shield_arming_end_time", 0L)
        val now = System.currentTimeMillis()
        _isAutoPocketSessionActive.value = prefs.getBoolean("auto_pocket_session_active", false)

        // 1. If service is ALREADY running, do not re-arm or skip countdown!
        if (KavachForegroundService.isServiceRunning) {
            _shieldState.value = KavachForegroundService.serviceShieldState.value
            _armingCountdown.value = KavachForegroundService.serviceArmingCountdown.value
            _remainingSeconds.value = KavachForegroundService.serviceRemainingSeconds.value
        } else if (isAlarming) {
            _shieldState.value = ShieldState.ALARM_ACTIVE
            val cachePrefs = app.getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)
            val savedVolume = cachePrefs.getInt("alarm_volume_percent", 100)
            val selectedPack = cachePrefs.getString("selected_siren_pack_id", "default") ?: "default"
            val flashlight = cachePrefs.getBoolean("flashlight_enabled", true)
            alarmController.activateFullAlarm(selectedPack, flashlight, savedVolume)
        } else if (wasArmed && (endTime == 0L || endTime > now)) {
            val remaining = if (endTime > 0L) ((endTime - now) / 1000L).coerceAtLeast(30L) else 7200L
            _remainingSeconds.value = remaining
            val minutes = (remaining / 60L).toInt().coerceAtLeast(1)

            // If still in the middle of arming countdown, restore countdown!
            if (armingEndTime > now) {
                val remainingArmingSecs = ((armingEndTime - now) / 1000L).toInt().coerceAtLeast(1)
                _shieldState.value = ShieldState.ARMING
                _armingCountdown.value = remainingArmingSecs
                KavachForegroundService.arm(
                    context = app,
                    durationMinutes = minutes,
                    armingCountdownSeconds = remainingArmingSecs,
                    detectionMode = settings.value.detectionMode,
                    sensitivity = settings.value.sensitivityLevel,
                    alarmVolumePercent = settings.value.alarmVolumePercent,
                    skipCountdown = false
                )
            } else {
                _shieldState.value = ShieldState.ARMED
                KavachForegroundService.arm(
                    context = app,
                    durationMinutes = minutes,
                    armingCountdownSeconds = 0,
                    detectionMode = settings.value.detectionMode,
                    sensitivity = settings.value.sensitivityLevel,
                    alarmVolumePercent = settings.value.alarmVolumePercent,
                    skipCountdown = true
                )
            }
        } else if (wasArmed && endTime in 1..now) {
            // Armed duration expired while device was off
            prefs.edit().putBoolean("shield_was_armed", false).commit()
            _shieldState.value = ShieldState.DISARMED
        }

        // 2. Reactively mirror KavachForegroundService state
        viewModelScope.launch {
            KavachForegroundService.serviceShieldState.collect { state ->
                if (KavachForegroundService.isServiceRunning) {
                    _shieldState.value = state
                } else if (state != ShieldState.DISARMED) {
                    _shieldState.value = state
                }
                if (state == ShieldState.ALARM_ACTIVE) {
                    scheduleAlarmTimeoutIfConfigured()
                } else if (state == ShieldState.DISARMED) {
                    alarmTimeoutJob?.cancel()
                    alarmTimeoutJob = null
                }
            }
        }
        viewModelScope.launch {
            KavachForegroundService.serviceRemainingSeconds.collect { sec ->
                if (KavachForegroundService.isServiceRunning || sec > 0L) {
                    _remainingSeconds.value = sec
                    if (_shieldState.value == ShieldState.ARMED) {
                        val cal = java.util.Calendar.getInstance()
                        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        val mode = settings.value.detectionMode
                        val cur = _hourlyUsage.value.toMutableMap()
                        val hourMap = (cur[hour] ?: emptyMap()).toMutableMap()
                        hourMap[mode] = (hourMap[mode] ?: 0) + 1
                        cur[hour] = hourMap
                        _hourlyUsage.value = cur
                    }
                }
            }
        }
        viewModelScope.launch {
            KavachForegroundService.serviceArmingCountdown.collect { count ->
                _armingCountdown.value = count
            }
        }
        viewModelScope.launch {
            KavachForegroundService.serviceAuthCountdown.collect { count ->
                if (count > 0 || _shieldState.value == ShieldState.TRIGGERED) {
                    _decoyCountdown.value = count
                }
            }
        }

        // Apply sensitivity, detection mode, and pocket guard whenever settings change
        viewModelScope.launch {
            settings.collect { s ->
                sensorFusion.setSensitivity(s.sensitivityLevel)
                sensorFusion.setDetectionMode(s.detectionMode)
                sensorFusion.setPocketDetectionEnabled(s.pocketDetectionEnabled)
                sensorFusion.setTravelIdleMinutes(s.travelIdleMinutes)
                KavachForegroundService.updateLiveSensitivity(s.sensitivityLevel)

                // Schedule/cancel night auto-arm whenever settings change
                // Night schedule removed
                checkAndTriggerNightScheduleNow()
            }
        }
        registerScreenStateReceiver()

        // 3. Immediately evaluate night schedule on app start without needing to navigate anywhere
        viewModelScope.launch {
            checkAndTriggerNightScheduleNow()
        }
    }

    /**
     * ARM the shield with the selected timer preset.
     * Shows arming countdown first so user can place phone down.
     */
    fun armShield() {
        val totalMinutes = when (settings.value.selectedTimerPreset) {
            TimerPreset.CUSTOM -> settings.value.customTimerMinutes
            else -> settings.value.selectedTimerPreset.minutes
        }
        armShieldWithDuration(totalMinutes)
    }

    /**
     * Instant Background Hand-off:
     * Synchronously commits shield_was_armed = true to disk in 0ms, starts
     * KavachForegroundService, and lets the 5s countdown & sensors run inside the service.
     */
    fun armShieldWithDuration(minutes: Int, isNightAutoArm: Boolean = false, overrideMode: DetectionMode? = null) {
        val app = getApplication<Application>()
        val s = settings.value
        val armingSeconds = s.armingCountdownSeconds
        val authSeconds = s.decoyCountdownSeconds

        _shieldState.value = ShieldState.ARMING
        _armingCountdown.value = armingSeconds
        _decoyCountdown.value = authSeconds
        _remainingSeconds.value = minutes * 60L

        isTransitSessionPausedByUser = false
        _isAutoPocketSessionActive.value = false
        // If user manually initiated ARM SHIELD, clear the night cancel flag so user manual action takes precedence.
        // But if night auto-arm initiated it, do NOT wipe userDisarmedNightWindow!
        if (!isNightAutoArm) {
            userDisarmedNightWindow = false
            app.getSharedPreferences("kavach_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("night_disarmed_by_user", false)
                .putString("night_cancelled_window_id", "")
                .apply()
            _isNightGuardDismissedForTonight.value = false
        }

        val modeToUse = overrideMode ?: if (isNightAutoArm) DetectionMode.SLEEP else s.detectionMode

        KavachForegroundService.arm(
            context = app,
            durationMinutes = minutes,
            armingCountdownSeconds = armingSeconds,
            authCountdownSeconds = authSeconds,
            detectionMode = modeToUse,
            sensitivity = s.sensitivityLevel,
            alarmVolumePercent = s.alarmVolumePercent,
            skipCountdown = false
        )
        refreshUsageStats()
    }

    /**
     * Compute a unique ID for the current night window (e.g. "2026-09-30_23:00")
     * so that once cancelled during this window, it NEVER re-runs until that entire window has passed.
     */
    private fun getCurrentNightWindowId(schedule: NightSchedule): String {
        val now = Calendar.getInstance()
        val curDayOfYear = now.get(Calendar.DAY_OF_YEAR)
        val curYear = now.get(Calendar.YEAR)
        val curHour = now.get(Calendar.HOUR_OF_DAY)
        // If window spans midnight and we are in early morning (curHour < endHour), the window belongs to yesterday's start
        val windowYear = if (schedule.startHour > schedule.endHour && curHour < schedule.endHour) {
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            "${yesterday.get(Calendar.YEAR)}_${yesterday.get(Calendar.DAY_OF_YEAR)}"
        } else {
            "${curYear}_${curDayOfYear}"
        }
        return "${windowYear}_${schedule.startHour}:${schedule.startMinute}"
    }

    private fun markNightScheduleDisarmedByUser() {
        val app = getApplication<Application>()
        val prefs = app.getSharedPreferences("kavach_prefs", Context.MODE_PRIVATE)
        val wasNightAutoArmed = prefs.getBoolean("night_auto_armed", false)
        val schedule = settings.value.nightSchedule

        val now = Calendar.getInstance()
        val currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinuteOfDay = schedule.startHour * 60 + schedule.startMinute
        val endMinuteOfDay = schedule.endHour * 60 + schedule.endMinute
        val isInWindow = if (startMinuteOfDay < endMinuteOfDay) {
            currentMinuteOfDay in startMinuteOfDay until endMinuteOfDay
        } else if (startMinuteOfDay > endMinuteOfDay) {
            currentMinuteOfDay >= startMinuteOfDay || currentMinuteOfDay < endMinuteOfDay
        } else {
            true
        }

        // ONE CANCEL MEANS NO RERUN:
        // If night schedule is enabled and we are in the night window (or was triggered/armed by night guard),
        // permanently stamp this night window as cancelled!
        if (schedule.enabled && (wasNightAutoArmed || isInWindow)) {
            val windowId = getCurrentNightWindowId(schedule)
            userDisarmedNightWindow = true
            _isNightGuardDismissedForTonight.value = true
            prefs.edit()
                .putBoolean("night_auto_armed", false)
                .putBoolean("night_disarmed_by_user", true)
                .putString("night_cancelled_window_id", windowId)
                .commit()
        } else {
            prefs.edit()
                .putBoolean("night_auto_armed", false)
                .commit()
        }
    }

    /**
     * User explicitly taps "Turn Off for Tonight" / "Dismiss Night Guard" on the Dashboard
     */
    fun cancelNightGuardForTonight() {
        val schedule = settings.value.nightSchedule
        val app = getApplication<Application>()
        val prefs = app.getSharedPreferences("kavach_prefs", Context.MODE_PRIVATE)
        val windowId = getCurrentNightWindowId(schedule)

        userDisarmedNightWindow = true
        _isNightGuardDismissedForTonight.value = true

        prefs.edit()
            .putBoolean("night_auto_armed", false)
            .putBoolean("night_disarmed_by_user", true)
            .putString("night_cancelled_window_id", windowId)
            .commit()

        // Disarm shield if currently armed
        disarmShield()
    }

    /**
     * User explicitly resumes Night Guard for tonight
     */
    fun resumeNightGuard() {
        val app = getApplication<Application>()
        val prefs = app.getSharedPreferences("kavach_prefs", Context.MODE_PRIVATE)

        userDisarmedNightWindow = false
        _isNightGuardDismissedForTonight.value = false

        prefs.edit()
            .putBoolean("night_disarmed_by_user", false)
            .putString("night_cancelled_window_id", "")
            .commit()

        checkAndTriggerNightScheduleNow()
    }

    /**
     * Disarm the shield completely.
     */
    fun disarmShield() {
        armingJob?.cancel()
        timerJob?.cancel()
        monitorJob?.cancel()
        decoyJob?.cancel()
        alarmTimeoutJob?.cancel()
        alarmTimeoutJob = null
        sensorFusion.stopListening()
        alarmController.deactivateAlarm()
        alarmController.unlockVolume()
        _shieldState.value = ShieldState.DISARMED
        _remainingSeconds.value = 0
        _authenticationSuccess.value = false

        val app = getApplication<Application>()
        KavachForegroundService.disarm(app)
        markNightScheduleDisarmedByUser()
        refreshUsageStats()
    }

    /**
     * Called when user successfully authenticates (fingerprint/PIN/face).
     *
     * PERMANENTLY DISARMS the shield:
     * Guard stops at this EXACT MOMENT in 0ms — not even waiting for green animation!
     */
    fun onAuthenticationSuccess() {
        _authenticationSuccess.value = true
        _shieldState.value = ShieldState.DISARMED
        decoyJob?.cancel()
        monitorJob?.cancel()
        timerJob?.cancel()
        armingJob?.cancel()
        alarmTimeoutJob?.cancel()
        alarmTimeoutJob = null
        alarmController.deactivateAlarm()
        alarmController.unlockVolume()

        val app = getApplication<Application>()
        // CRITICAL: Immediately disarm KavachForegroundService to halt background siren and strobes in 0ms!
        KavachForegroundService.disarm(app)

        // Stop sensor monitoring IMMEDIATELY
        sensorFusion.stopListening()
        sensorFusion.resetMotionFlag()

        // Guard stops at that exact moment without any delay
        _shieldState.value = ShieldState.DISARMED
        refreshUsageStats()
        _remainingSeconds.value = 0

        // Prevent night schedule from re-arming after verification
        markNightScheduleDisarmedByUser()
        // If in Smart Transit Guard, user unlocked phone: guard remains dormant while phone is in use,
        // and will automatically re-arm as soon as the user turns off/closes the screen!
    }

    // ========== Night Auto-Arm Schedule ==========

    /**
     * Checks if current time is within the night schedule window and auto-arms
     * if the shield is currently disarmed, without requiring navigation to Settings.
     */
    fun checkAndTriggerNightScheduleNow() {
        val currentSettings = settings.value
        val schedule = currentSettings.nightSchedule
        val app = getApplication<Application>()
        val prefs = app.getSharedPreferences("kavach_prefs", Context.MODE_PRIVATE)

        if (!schedule.enabled) {
            _isNightWindowActive.value = false
            _isNightGuardDismissedForTonight.value = false
            userDisarmedNightWindow = false
            prefs.edit()
                .putBoolean("night_disarmed_by_user", false)
                .putString("night_cancelled_window_id", "")
                .commit()
            // If night auto-armed or currently armed in SLEEP mode, disarm concurrently!
            if (prefs.getBoolean("night_auto_armed", false) || (_shieldState.value == ShieldState.ARMED && settings.value.detectionMode == DetectionMode.SLEEP)) {
                prefs.edit().putBoolean("night_auto_armed", false).commit()
                disarmShield()
            }
            return
        }

        val now = Calendar.getInstance()
        val currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinuteOfDay = schedule.startHour * 60 + schedule.startMinute
        val endMinuteOfDay = schedule.endHour * 60 + schedule.endMinute

        val isInWindow = if (startMinuteOfDay < endMinuteOfDay) {
            currentMinuteOfDay in startMinuteOfDay until endMinuteOfDay
        } else if (startMinuteOfDay > endMinuteOfDay) {
            currentMinuteOfDay >= startMinuteOfDay || currentMinuteOfDay < endMinuteOfDay
        } else {
            true // 24-hour coverage
        }

        _isNightWindowActive.value = isInWindow

        val currentWindowId = getCurrentNightWindowId(schedule)
        val cancelledWindowId = prefs.getString("night_cancelled_window_id", "") ?: ""

        val isCancelledForThisWindow = (cancelledWindowId == currentWindowId) || userDisarmedNightWindow || prefs.getBoolean("night_disarmed_by_user", false)
        _isNightGuardDismissedForTonight.value = isInWindow && isCancelledForThisWindow

        val currentState = _shieldState.value
        val serviceState = KavachForegroundService.serviceShieldState.value
        val isAlreadyGuarding = currentState != ShieldState.DISARMED || serviceState != ShieldState.DISARMED

        if (isInWindow && !isAlreadyGuarding && !isCancelledForThisWindow) {
            val remainingMinutes = if (startMinuteOfDay < endMinuteOfDay) {
                endMinuteOfDay - currentMinuteOfDay
            } else {
                if (currentMinuteOfDay < endMinuteOfDay) {
                    endMinuteOfDay - currentMinuteOfDay
                } else {
                    (24 * 60 - currentMinuteOfDay) + endMinuteOfDay
                }
            }

            prefs.edit()
                .putBoolean("night_auto_armed", true)
                .commit()

            // Arm shield immediately with the remaining minutes in SLEEP mode
            armShieldWithDuration(remainingMinutes.coerceAtLeast(1), isNightAutoArm = true, overrideMode = DetectionMode.SLEEP)
        } else if (!isInWindow) {
            // Reset disarmed flag once current time leaves the window so tomorrow's window will auto-arm
            if (userDisarmedNightWindow || prefs.getBoolean("night_disarmed_by_user", false) || cancelledWindowId.isNotEmpty()) {
                userDisarmedNightWindow = false
                _isNightGuardDismissedForTonight.value = false
                prefs.edit()
                    .putBoolean("night_disarmed_by_user", false)
                    .putString("night_cancelled_window_id", "")
                    .commit()
            }
            if (_shieldState.value != ShieldState.DISARMED && prefs.getBoolean("night_auto_armed", false)) {
                prefs.edit().putBoolean("night_auto_armed", false).commit()
                disarmShield()
            }
        }
    }

    /**
     * Schedule or cancel the night auto-arm feature.
     * Uses AlarmManager to fire at the configured start time,
     * and auto-disarms at the configured end time.
     */
    private fun scheduleNightAutoArm(schedule: NightSchedule) {
        return // Night schedule removed
    }
    private fun unused_scheduleNightAutoArm(schedule: NightSchedule) {
        val app = getApplication<Application>()
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Intent for START alarm
        val startIntent = Intent(app, NightScheduleReceiver::class.java).apply {
            action = NightScheduleReceiver.ACTION_NIGHT_ARM
        }
        val startPending = PendingIntent.getBroadcast(
            app,
            NightScheduleReceiver.REQUEST_CODE_START,
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for END alarm
        val endIntent = Intent(app, NightScheduleReceiver::class.java).apply {
            action = NightScheduleReceiver.ACTION_NIGHT_DISARM
        }
        val endPending = PendingIntent.getBroadcast(
            app,
            NightScheduleReceiver.REQUEST_CODE_END,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!schedule.enabled) {
            // Cancel both alarms
            alarmManager.cancel(startPending)
            alarmManager.cancel(endPending)
            startPending.cancel()
            endPending.cancel()

            // If currently night auto-armed, disarm immediately
            val prefs = app.getSharedPreferences("kavach_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("night_auto_armed", false)) {
                disarmShield()
            }

            // Cancel any running night schedule monitoring
            nightScheduleJob?.cancel()
            return
        }

        // Check if current time is ALREADY inside the schedule window right now
        checkAndTriggerNightScheduleNow()

        // Calculate next start time
        val startCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, schedule.startHour)
            set(Calendar.MINUTE, schedule.startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // If this time already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Calculate next end time
        val endCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, schedule.endHour)
            set(Calendar.MINUTE, schedule.endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Schedule start alarm with setAlarmClock for 100% reliability on OnePlus & all Android OEMs
        try {
            val startClockInfo = AlarmManager.AlarmClockInfo(startCalendar.timeInMillis, startPending)
            alarmManager.setAlarmClock(startClockInfo, startPending)
        } catch (_: Exception) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    startCalendar.timeInMillis,
                    startPending
                )
            } catch (_: Exception) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    startCalendar.timeInMillis,
                    startPending
                )
            }
        }

        // Schedule end alarm with setAlarmClock
        try {
            val endClockInfo = AlarmManager.AlarmClockInfo(endCalendar.timeInMillis, endPending)
            alarmManager.setAlarmClock(endClockInfo, endPending)
        } catch (_: Exception) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endCalendar.timeInMillis,
                    endPending
                )
            } catch (_: Exception) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    endCalendar.timeInMillis,
                    endPending
                )
            }
        }

        // Show notification confirming schedule
        showNightScheduleNotification(schedule)

        // Also start in-app schedule monitor (immediate check + 10s interval)
        startNightScheduleMonitor(schedule)
    }

    /**
     * In-app coroutine that checks whether the current time
     * is within the night schedule window. Checks immediately and every 10 seconds.
     */
    private fun startNightScheduleMonitor(schedule: NightSchedule) {
        nightScheduleJob?.cancel()
        if (!schedule.enabled) return

        nightScheduleJob = viewModelScope.launch {
            while (isActive) {
                checkAndTriggerNightScheduleNow()
                delay(10_000) // Check every 10 seconds
            }
        }
    }

    /**
     * Show a notification confirming the night schedule is set.
     */
    private fun showNightScheduleNotification(schedule: NightSchedule) {
        return // Night notification removed
    }
    private fun unused_showNightScheduleNotification(schedule: NightSchedule) {
        val app = getApplication<Application>()
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val startTime = formatTime(schedule.startHour, schedule.startMinute)
        val endTime = formatTime(schedule.endHour, schedule.endMinute)

        val notification = NotificationCompat.Builder(app, KavachForegroundService.CHANNEL_ID)
            .setContentTitle("🌙 Night Auto-Arm Scheduled")
            .setContentText("Shield will auto-arm at $startTime and disarm at $endTime")
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        nm.notify(3001, notification)
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

    // ========== Timer & Sensor Monitoring ==========

    /**
     * Update timer preset selection.
     */
    fun selectTimerPreset(preset: TimerPreset) {
        viewModelScope.launch {
            preferences.updateTimerPreset(preset)
        }
    }

    /**
     * Update sensitivity level.
     */
    fun updateSensitivity(level: Int) {
        viewModelScope.launch {
            preferences.updateSensitivity(level)
            sensorFusion.setSensitivity(level)
            KavachForegroundService.updateLiveSensitivity(level)
        }
    }

    /**
     * Update full settings.
     */
    fun updateSettings(settings: KavachSettings) {
        val app = getApplication<Application>()
        KavachForegroundService.closeAutoPocketSession(app)
        _isAutoPocketSessionActive.value = false
        val prefs = app.getSharedPreferences("kavach_prefs", Context.MODE_PRIVATE)
        if (settings.nightSchedule.enabled) {
            userDisarmedNightWindow = false
            prefs.edit()
                .putBoolean("night_disarmed_by_user", false)
                .putString("night_cancelled_window_id", "")
                .commit()
        }
        viewModelScope.launch {
            preferences.updateSettings(settings)
            checkAndTriggerNightScheduleNow()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            if (settings.value.detectionMode == DetectionMode.TRAVEL) {
                // In Travel Mode: runs 24x7 indefinitely without timer auto-disarm
                _remainingSeconds.value = -1L
                while (_shieldState.value != ShieldState.DISARMED) {
                    delay(1000)
                }
            } else {
                while (_remainingSeconds.value > 0 && _shieldState.value != ShieldState.DISARMED) {
                    delay(1000)
                    _remainingSeconds.value = (_remainingSeconds.value - 1).coerceAtLeast(0)
                }
                // Guard timer expired without user authenticating in between — auto switch to 24x7 Travel Mode!
                if (_shieldState.value != ShieldState.DISARMED) {
                    val s = settings.value
                    updateSettings(s.copy(detectionMode = DetectionMode.TRAVEL))
                    sensorFusion.setDetectionMode(DetectionMode.TRAVEL)
                    sensorFusion.recalibrate()
                    _remainingSeconds.value = -1L
                    KavachForegroundService.arm(
                        context = getApplication(),
                        durationMinutes = 0,
                        armingCountdownSeconds = 0,
                        authCountdownSeconds = s.decoyCountdownSeconds,
                        detectionMode = DetectionMode.TRAVEL,
                        sensitivity = s.sensitivityLevel,
                        alarmVolumePercent = s.alarmVolumePercent,
                        skipCountdown = true
                    )
                }
            }
        }
    }

    private fun startMotionMonitoring() {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            sensorFusion.motionDetected.collect { detected ->
                if (detected && _shieldState.value == ShieldState.ARMED) {
                    if (settings.value.detectionMode == DetectionMode.SLEEP && KavachForegroundService.isSleepCallActive()) {
                        sensorFusion.resetMotionFlag()
                        return@collect
                    }
                    triggerDecoyTrap()
                }
            }
        }
    }

    /**
     * The core innovation: Stealth decoy trap.
     * Shows calm clock screen + haptic alert → configurable countdown for auth → ALARM
     */
    private fun triggerDecoyTrap() {
        _shieldState.value = ShieldState.TRIGGERED
        val decoySeconds = settings.value.decoyCountdownSeconds
        _decoyCountdown.value = decoySeconds
        _authenticationSuccess.value = false

        // Gentle haptic to alert real owner
        alarmController.emitOwnerHaptic()

        decoyJob?.cancel()
        decoyJob = viewModelScope.launch {
            for (i in decoySeconds downTo 1) {
                if (_authenticationSuccess.value) return@launch
                _decoyCountdown.value = i
                delay(1000)
            }

            // Countdown expired without authentication — ALARM!
            if (!_authenticationSuccess.value) {
                _shieldState.value = ShieldState.ALARM_ACTIVE
                alarmController.activateFullAlarm(
                    sirenPackId = settings.value.selectedSirenPackId,
                    enableFlashlight = settings.value.flashlightEnabled,
                    volumePercent = settings.value.alarmVolumePercent
                )
                scheduleAlarmTimeoutIfConfigured()
            }
        }
    }

    private fun scheduleAlarmTimeoutIfConfigured() {
        val timeoutMinutes = settings.value.alarmTimeoutMinutes
        alarmTimeoutJob?.cancel()
        if (timeoutMinutes in 1..60) {
            alarmTimeoutJob = viewModelScope.launch {
                delay(timeoutMinutes * 60 * 1000L)
                if (_shieldState.value == ShieldState.ALARM_ACTIVE) {
                    disarmShield()
                }
            }
        }
    }

    /**
     * Test the alarm sound without arming (for settings screen).
     * Plays sound at current phone volume so user can safely test even at lowest volume.
     */
    fun testAlarm() {
        viewModelScope.launch {
            alarmController.playTestAlarm(
                sirenPackId = settings.value.selectedSirenPackId
            )
            delay(3000)
            alarmController.stopPreview()
        }
    }

    /**
     * Preview a siren pack sound.
     */
    fun previewSiren(packId: String) {
        alarmController.previewSiren(packId)
    }

    fun stopPreview() {
        alarmController.stopPreview()
    }

    /**
     * Unlock a siren pack and persist to preferences.
     */
    fun unlockSirenPack(packId: String) {
        viewModelScope.launch {
            preferences.unlockSirenPack(packId)
        }
    }

    private val _isTermsAccepted = MutableStateFlow(preferences.isTermsAccepted())
    val isTermsAccepted: StateFlow<Boolean> = _isTermsAccepted.asStateFlow()

    fun acceptTerms() {
        _isTermsAccepted.value = true
        viewModelScope.launch {
            preferences.setTermsAccepted(true)
        }
    }

    
    private fun registerScreenStateReceiver() {
        // Auto-loop commuter feature removed - Pocket Guard is manual one-time single-arm
    }

    /**
     * Toggles whether the transit auto re-arm session is permanently closed for this session.
     */
    fun pauseTransitSession() {
        isTransitSessionPausedByUser = true
        disarmShield()
    }

    fun quitAutoLoopSession() {
        isTransitSessionPausedByUser = true
        _isAutoPocketSessionActive.value = false
        val app = getApplication<Application>()
        KavachForegroundService.closeAutoPocketSession(app)
        updateSettings(settings.value.copy(detectionMode = DetectionMode.POCKET))
        disarmShield()
        android.widget.Toast.makeText(
            app,
            "Auto-Loop Pocket Guard ended. Switched to Standard Pocket Guard.",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    fun resumeTransitSession() {
        isTransitSessionPausedByUser = false
    }

    override fun onCleared() {
        super.onCleared()
        nightScheduleJob?.cancel()
        sensorFusion.stopListening()
        try {
            if (screenReceiver != null) {
                getApplication<Application>().unregisterReceiver(screenReceiver)
                screenReceiver = null
            }
        } catch (_: Exception) {}
        // CRITICAL: NEVER stop the alarm or volume lock if shield is armed or alarming!
        // When a thief or user swipes the app away from recent apps, onCleared() is invoked.
        // The background foreground service must continue blaring the siren without interruption!
        val prefs = getApplication<Application>().getSharedPreferences("kavach_prefs", Context.MODE_PRIVATE)
        val isArmed = prefs.getBoolean("shield_was_armed", false) || KavachForegroundService.isServiceRunning
        val isAlarming = prefs.getBoolean("shield_is_alarming", false) || _shieldState.value == ShieldState.ALARM_ACTIVE
        if (!isArmed && !isAlarming) {
            alarmController.cleanup()
        }
    }
}
