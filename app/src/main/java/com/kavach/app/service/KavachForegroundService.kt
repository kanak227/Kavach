package com.kavach.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.kavach.app.MainActivity
import com.kavach.app.R
import com.kavach.app.alarm.AlarmController
import com.kavach.app.data.DetectionMode
import com.kavach.app.data.SensorReading
import com.kavach.app.data.ShieldState
import com.kavach.app.data.UserProfileRepository
import com.kavach.app.sensor.SensorFusion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service providing continuous background shield monitoring.
 * Runs independently of the UI like a system clock alarm.
 *
 * KEY FEATURES:
 * 1. Instant hand-off: Tap "ARM NOW" -> synchronous disk commit -> service starts in 0ms.
 * 2. Background arming countdown: 5s countdown runs in background even if app is swiped away.
 * 3. Autonomous sensor monitoring & alarm trigger in background.
 * 4. WakeLock keeps CPU active; AlarmManager alarm provides resilient restart backup.
 * 5. Works like a clock alarm: app does not need to stay open in recent apps.
 */
class KavachForegroundService : Service() {

    companion object {
        @Volatile
        var isAppInForeground: Boolean = false

        const val CHANNEL_ID = "kavach_service_channel_v2"
        const val ALARM_CHANNEL_ID = "kavach_alarm_channel_v2"
        const val NOTIFICATION_ID = 1001
        const val ALARM_NOTIFICATION_ID = 1002

        const val ACTION_START = "com.kavach.app.START_SERVICE"
        const val ACTION_STOP = "com.kavach.app.STOP_SERVICE"
        const val ACTION_RESTART = "com.kavach.app.RESTART_SERVICE"
        const val ACTION_DISMISS_ALERT_NOTIFICATION = "com.kavach.app.DISMISS_ALERT_NOTIFICATION"

        const val EXTRA_FORCE_NEW_ARM = "extra_force_new_arm"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val EXTRA_COUNTDOWN_SECONDS = "extra_countdown_seconds"
        const val EXTRA_DETECTION_MODE = "extra_detection_mode"
        const val EXTRA_SENSITIVITY = "extra_sensitivity"
        const val EXTRA_VOLUME_PERCENT = "extra_volume_percent"
        const val EXTRA_SKIP_COUNTDOWN = "extra_skip_countdown"
        const val EXTRA_AUTH_COUNTDOWN_SECONDS = "extra_auth_countdown_seconds"

        private const val RESTART_REQUEST_CODE = 9999
        private const val PREFS_NAME = "kavach_prefs"
        private const val KEY_SHIELD_ARMED = "shield_was_armed"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        // Shared reactive state for UI / ViewModel
        private val _serviceShieldState = MutableStateFlow(ShieldState.DISARMED)
        val serviceShieldState: StateFlow<ShieldState> = _serviceShieldState.asStateFlow()

        private val _serviceArmingCountdown = MutableStateFlow(0)
        val serviceArmingCountdown: StateFlow<Int> = _serviceArmingCountdown.asStateFlow()

        private val _serviceAuthCountdown = MutableStateFlow(0)
        val serviceAuthCountdown: StateFlow<Int> = _serviceAuthCountdown.asStateFlow()

        private val _serviceRemainingSeconds = MutableStateFlow(0L)
        val serviceRemainingSeconds: StateFlow<Long> = _serviceRemainingSeconds.asStateFlow()

        private val _serviceSensorReading = MutableStateFlow(SensorReading())
        val serviceSensorReading: StateFlow<SensorReading> = _serviceSensorReading.asStateFlow()

        @Volatile
        var activeInstance: KavachForegroundService? = null

        fun updateLiveSensitivity(level: Int) {
            activeInstance?.let { service ->
                service.sensorFusion?.setSensitivity(level)
            }
        }

        fun closeAutoPocketSession(context: Context) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean("auto_pocket_session_active", false)
                .commit()
        }

        /**
         * Millisecond-instant arming hand-off:
         * 1. Synchronously commits shield_was_armed = true to disk
         * 2. Starts foreground service with countdown & sensor configuration
         */
        fun arm(
            context: Context,
            durationMinutes: Int,
            armingCountdownSeconds: Int = 5,
            authCountdownSeconds: Int = 5,
            detectionMode: DetectionMode = DetectionMode.MOTION,
            sensitivity: Int = 5,
            alarmVolumePercent: Int = 100,
            skipCountdown: Boolean = false
        ) {
            val totalSeconds = (durationMinutes * 60L) + (if (skipCountdown) 0 else armingCountdownSeconds)
            val endTime = System.currentTimeMillis() + (totalSeconds * 1000L)
            val armingEndTime = System.currentTimeMillis() + (if (skipCountdown) 0L else armingCountdownSeconds * 1000L)

            // CRITICAL: Synchronous commit to disk BEFORE anything else happens!
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHIELD_ARMED, true)
                .putLong("shield_armed_end_time", endTime)
                .putLong("shield_arming_end_time", armingEndTime)
                .putInt("shield_duration_minutes", durationMinutes)
                .putInt("auth_countdown_seconds", authCountdownSeconds)
                .putString("detection_mode", detectionMode.name)
                .putBoolean("auto_pocket_session_active", false)
                .putInt("arming_countdown_seconds", armingCountdownSeconds)
                .putInt("sensitivity_level", sensitivity)
                .putInt("alarm_volume_percent", alarmVolumePercent)
                .putBoolean("shield_is_alarming", false)
                .commit()

            _serviceShieldState.value = if (skipCountdown) ShieldState.ARMED else ShieldState.ARMING
            _serviceArmingCountdown.value = if (skipCountdown) 0 else armingCountdownSeconds
            _serviceAuthCountdown.value = authCountdownSeconds
            _serviceRemainingSeconds.value = durationMinutes * 60L

            val intent = Intent(context, KavachForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FORCE_NEW_ARM, true)
                putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
                putExtra(EXTRA_COUNTDOWN_SECONDS, armingCountdownSeconds)
                putExtra(EXTRA_AUTH_COUNTDOWN_SECONDS, authCountdownSeconds)
                putExtra(EXTRA_DETECTION_MODE, detectionMode.name)
                putExtra(EXTRA_SENSITIVITY, sensitivity)
                putExtra(EXTRA_VOLUME_PERCENT, alarmVolumePercent)
                putExtra(EXTRA_SKIP_COUNTDOWN, skipCountdown)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun disarm(context: Context) {
            // CRITICAL: Synchronous commit to disk
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHIELD_ARMED, false)
                .putLong("shield_armed_end_time", 0L)
                .putLong("shield_arming_end_time", 0L)
                .putBoolean("shield_is_alarming", false)
                .putBoolean("night_auto_armed", false)
                .commit()

            _serviceShieldState.value = ShieldState.DISARMED
            _serviceRemainingSeconds.value = 0L
            _serviceArmingCountdown.value = 0

            val intent = Intent(context, KavachForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /**
         * Update state to triggered (e.g. from UI or external trigger)
         */
        fun markTriggered() {
            _serviceShieldState.value = ShieldState.TRIGGERED
        }
    }

    @Volatile
    private var alertNotificationSwipedByUser = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorFusion: SensorFusion? = null
    private var alarmController: AlarmController? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var countdownJob: Job? = null
    private var authCountdownJob: Job? = null
    private var monitoringJob: Job? = null
    private var timerJob: Job? = null
    private var alarmTimeoutJob: Job? = null

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Screen off handling (Auto-loop removed as requested: pocket guard is standard single-arm manual only)
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannels()
        try {
            val screenFilter = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenOffReceiver, screenFilter)
        } catch (_: Exception) {}
        sensorFusion = SensorFusion(applicationContext)
        alarmController = AlarmController(applicationContext)

        // Stream sensor readings
        serviceScope.launch {
            sensorFusion?.sensorReading?.collect { reading ->
                _serviceSensorReading.value = reading
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val action = intent?.action
        val isForceNewArm = intent?.getBooleanExtra(EXTRA_FORCE_NEW_ARM, false) == true
        val isAlarming = prefs.getBoolean("shield_is_alarming", false) || _serviceShieldState.value == ShieldState.ALARM_ACTIVE

        // CRITICAL CHECK: If shield was alarming and this is NOT an explicit new arm, NEVER cancel alarm!
        if (isAlarming && !isForceNewArm && action != ACTION_STOP) {
            isServiceRunning = true
            acquireWakeLock()
            _serviceShieldState.value = ShieldState.ALARM_ACTIVE

            val cachePrefs = getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)
            val cachedVolume = cachePrefs.getInt("alarm_volume_percent", 100)
            val selectedPack = cachePrefs.getString("selected_siren_pack_id", "default") ?: "default"
            val flashlight = cachePrefs.getBoolean("flashlight_enabled", true)

            startForeground(NOTIFICATION_ID, buildNotification())
            showAlarmNotification()
            alarmController?.activateFullAlarm(selectedPack, flashlight, cachedVolume)
            scheduleAlarmTimeoutIfConfigured()
            return START_STICKY
        }

        when (action) {
            ACTION_DISMISS_ALERT_NOTIFICATION -> {
                alertNotificationSwipedByUser = true
                try {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm?.cancel(NOTIFICATION_ID)
                    nm?.cancel(ALARM_NOTIFICATION_ID)
                } catch (_: Exception) {}
                return START_STICKY
            }
            ACTION_START -> {
                alertNotificationSwipedByUser = false
                isServiceRunning = true
                acquireWakeLock()

                val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 120)
                val countdownSecs = intent.getIntExtra(EXTRA_COUNTDOWN_SECONDS, 5)
                val modeStr = intent.getStringExtra(EXTRA_DETECTION_MODE) ?: DetectionMode.MOTION.name
                val mode = try { DetectionMode.valueOf(modeStr) } catch (_: Exception) { DetectionMode.MOTION }
                val sensitivity = intent.getIntExtra(EXTRA_SENSITIVITY, prefs.getInt("sensitivity_level", 5))
                val volumePercent = intent.getIntExtra(EXTRA_VOLUME_PERCENT, 100)
                val skipCountdown = intent.getBooleanExtra(EXTRA_SKIP_COUNTDOWN, false)
                val authCountdownSecs = intent.getIntExtra(EXTRA_AUTH_COUNTDOWN_SECONDS, prefs.getInt("auth_countdown_seconds", 5))

                val armingEndTime = System.currentTimeMillis() + (if (skipCountdown) 0L else countdownSecs * 1000L)

                // Persist armed state synchronously
                prefs.edit()
                    .putBoolean(KEY_SHIELD_ARMED, true)
                    .putLong("shield_arming_end_time", armingEndTime)
                    .putInt("auth_countdown_seconds", authCountdownSecs)
                    .putBoolean("shield_is_alarming", false)
                    .commit()

                // Cancel existing jobs
                countdownJob?.cancel()
                monitoringJob?.cancel()
                timerJob?.cancel()

                if (skipCountdown) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    startGuarding(durationMinutes, mode, sensitivity, volumePercent, authCountdownSecs)
                } else {
                    _serviceShieldState.value = ShieldState.ARMING
                    _serviceArmingCountdown.value = countdownSecs
                    startForeground(NOTIFICATION_ID, buildNotification())

                    countdownJob = serviceScope.launch {
                        for (i in countdownSecs downTo 1) {
                            _serviceArmingCountdown.value = i
                            // Silent countdown - no notification spam
                            delay(1000)
                        }
                        _serviceArmingCountdown.value = 0
                        startGuarding(durationMinutes, mode, sensitivity, volumePercent, authCountdownSecs)
                    }
                }
            }
            ACTION_STOP -> {
                alertNotificationSwipedByUser = false
                performDisarmCleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                try {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm?.cancel(NOTIFICATION_ID)
                    nm?.cancel(ALARM_NOTIFICATION_ID)
                } catch (_: Exception) {}
                stopSelf()
            }
            else -> {
                // ACTION_RESTART or restarted by system (START_STICKY)
                val shouldBeArmed = prefs.getBoolean(KEY_SHIELD_ARMED, false)
                if (shouldBeArmed) {
                    isServiceRunning = true
                    acquireWakeLock()
                    val duration = prefs.getInt("shield_duration_minutes", 120)
                    startForeground(NOTIFICATION_ID, buildNotification())

                    val cachePrefs = getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)
                    val cachedVolume = cachePrefs.getInt("alarm_volume_percent", 100)
                    val selectedPack = cachePrefs.getString("selected_siren_pack_id", "default") ?: "default"
                    val flashlight = cachePrefs.getBoolean("flashlight_enabled", true)

                    if (isAlarming) {
                        _serviceShieldState.value = ShieldState.ALARM_ACTIVE
                        alarmController?.activateFullAlarm(selectedPack, flashlight, cachedVolume)
                        launchMainActivity(isTriggered = true)
                    } else {
                        val armingEndTime = prefs.getLong("shield_arming_end_time", 0L)
                        val now = System.currentTimeMillis()
                        if (now < armingEndTime) {
                            val remainingCountdown = ((armingEndTime - now) / 1000L).toInt().coerceAtLeast(1)
                            _serviceShieldState.value = ShieldState.ARMING
                            _serviceArmingCountdown.value = remainingCountdown
                            countdownJob = serviceScope.launch {
                                for (i in remainingCountdown downTo 1) {
                                    _serviceArmingCountdown.value = i
                                    // Silent countdown - no notification spam
                                    delay(1000)
                                }
                                _serviceArmingCountdown.value = 0
                                startGuarding(duration, DetectionMode.MOTION, 10, cachedVolume)
                            }
                        } else {
                            startGuarding(duration, DetectionMode.MOTION, 10, cachedVolume)
                        }
                    }
                } else {
                    try {
                        startForeground(NOTIFICATION_ID, buildNotification())
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } catch (_: Exception) {}
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startGuarding(
        durationMinutes: Int,
        mode: DetectionMode,
        sensitivity: Int,
        volumePercent: Int,
        configuredAuthCountdownSecs: Int = 5
    ) {
        _serviceShieldState.value = ShieldState.ARMED
        // Guarding silently - no noisy notifications during normal armed state

        // Volume floor is activated during an active alarm trigger

        // Configure and start sensor listening
        sensorFusion?.apply {
            setDetectionMode(mode)
            setSensitivity(sensitivity)
            val cachePrefs = getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)
            val idleMins = try { cachePrefs.getInt("travel_idle_minutes", 3) } catch (_: Exception) { 3 }
            setTravelIdleMinutes(idleMins)
            recalibrate()
            startListening()
        }

        // Monitor for motion/theft triggers — stealth decoy countdown first, NOT immediate alarm!
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            sensorFusion?.motionDetected?.collect { detected ->
                if (detected && _serviceShieldState.value == ShieldState.ARMED) {
                    val authCountdownSecs = if (configuredAuthCountdownSecs in 1..60) {
                        configuredAuthCountdownSecs
                    } else {
                        try {
                            getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)
                                .getInt("decoy_countdown_seconds", 5)
                        } catch (_: Exception) { 5 }
                    }

                    val savedVolume = try {
                        getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)
                            .getInt("alarm_volume_percent", volumePercent)
                    } catch (_: Exception) { volumePercent }

                    onTheftDetected(authCountdownSecs, savedVolume)
                }
            }
        }

        // Timer duration countdown
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            if (mode == DetectionMode.TRAVEL || durationMinutes <= 0) {
                // In Travel Mode: runs 24x7 indefinitely until user explicitly authenticates
                _serviceRemainingSeconds.value = -1L
                while (isActive && _serviceShieldState.value != ShieldState.DISARMED) {
                    delay(1000)
                    if (_serviceShieldState.value == ShieldState.ARMED) {
                        try {
                            val cal = java.util.Calendar.getInstance()
                            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                            UserProfileRepository(applicationContext).recordModeUsageSeconds(hour, DetectionMode.TRAVEL, 1)
                        } catch (_: Exception) {}
                    }
                }
            } else {
                var remaining = durationMinutes * 60L
                _serviceRemainingSeconds.value = remaining

                while (isActive && remaining > 0 && _serviceShieldState.value != ShieldState.DISARMED) {
                    delay(1000)
                    remaining--
                    _serviceRemainingSeconds.value = remaining
                    if (_serviceShieldState.value == ShieldState.ARMED) {
                        try {
                            val cal = java.util.Calendar.getInstance()
                            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                            UserProfileRepository(applicationContext).recordModeUsageSeconds(hour, mode, 1)
                        } catch (_: Exception) {}
                    }
                }

                // If timer expired without user authenticating in between, automatically switch to 24x7 Travel Mode!
                if (remaining <= 0 && _serviceShieldState.value != ShieldState.DISARMED) {
                    val prefs = getSharedPreferences("kavach_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("detection_mode", DetectionMode.TRAVEL.name).apply()
                    sensorFusion?.setDetectionMode(DetectionMode.TRAVEL)
                    sensorFusion?.recalibrate()
                    _serviceRemainingSeconds.value = -1L
                    val nm = getSystemService(NotificationManager::class.java)
                    nm?.notify(NOTIFICATION_ID, buildNotification())

                    while (isActive && _serviceShieldState.value != ShieldState.DISARMED) {
                        delay(1000)
                        if (_serviceShieldState.value == ShieldState.ARMED) {
                            try {
                                val cal = java.util.Calendar.getInstance()
                                val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                                UserProfileRepository(applicationContext).recordModeUsageSeconds(hour, DetectionMode.TRAVEL, 1)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    private fun wakeUpScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val screenLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "Kavach:AlarmWakeUp"
            )
            screenLock.acquire(10000L)
        } catch (_: Exception) { }
    }

    private fun onTheftDetected(authCountdownSecs: Int, volumePercent: Int) {
        if (_serviceShieldState.value != ShieldState.ARMED) return
        _serviceShieldState.value = ShieldState.TRIGGERED

        // 1. Wake up the screen so user can see and authenticate
        wakeUpScreen()

        // 2. Show single persistent auth notification in tray (user taps to redirect, or swipes to dismiss)
        showAlarmNotification()

        // 3. Wall-clock accurate auth countdown
        val totalMillis = authCountdownSecs * 1000L
        val targetEndTime = android.os.SystemClock.elapsedRealtime() + totalMillis
        _serviceAuthCountdown.value = authCountdownSecs

        authCountdownJob?.cancel()
        authCountdownJob = serviceScope.launch {
            while (isActive && _serviceShieldState.value == ShieldState.TRIGGERED) {
                val remainingMillis = targetEndTime - android.os.SystemClock.elapsedRealtime()
                if (remainingMillis <= 0L) {
                    _serviceAuthCountdown.value = 0
                    break
                }
                val remainingSecs = ((remainingMillis + 999L) / 1000L).toInt()
                _serviceAuthCountdown.value = remainingSecs
                delay(100L) // 100ms polling for exact, sub-second accuracy
            }

            // Countdown expired without authentication — BLAST ALARM at configured volume!
            if (_serviceShieldState.value == ShieldState.TRIGGERED) {
                _serviceShieldState.value = ShieldState.ALARM_ACTIVE
                wakeUpScreen()

                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean("shield_is_alarming", true)
                    .commit()

                val prefs = getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)
                val savedVolume = try {
                    prefs.getInt("alarm_volume_percent", volumePercent)
                } catch (_: Exception) { volumePercent }
                val selectedPack = try {
                    prefs.getString("selected_siren_pack_id", "default") ?: "default"
                } catch (_: Exception) { "default" }
                val flashlight = try {
                    prefs.getBoolean("flashlight_enabled", true)
                } catch (_: Exception) { true }

                alarmController?.activateFullAlarm(
                    sirenPackId = selectedPack,
                    enableFlashlight = flashlight,
                    volumePercent = savedVolume
                )
                showAlarmNotification()
                scheduleAlarmTimeoutIfConfigured()
            }
        }
    }

    private fun performDisarmCleanup() {
        isServiceRunning = false
        _serviceShieldState.value = ShieldState.DISARMED
        _serviceRemainingSeconds.value = 0L
        _serviceArmingCountdown.value = 0
        _serviceAuthCountdown.value = 0

        countdownJob?.cancel()
        authCountdownJob?.cancel()
        alarmTimeoutJob?.cancel()
        alarmTimeoutJob = null
        monitoringJob?.cancel()
        timerJob?.cancel()

        sensorFusion?.stopListening()
        alarmController?.deactivateAlarm()
        alarmController?.unlockVolume()

        releaseWakeLock()
        cancelRestartAlarm()

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHIELD_ARMED, false)
            .putBoolean("shield_is_alarming", false)
            .putLong("shield_armed_end_time", 0L)
            .putLong("shield_arming_end_time", 0L)
            .putBoolean("night_auto_armed", false)
            .commit()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called when the app task is removed from recent apps.
     * Behavior: just like a clock alarm, the guard continues running silently in background.
     * If the siren is sounding, it continues to sound and relaunches the auth screen!
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isArmed = prefs.getBoolean(KEY_SHIELD_ARMED, false) || isServiceRunning
        val isAlarming = prefs.getBoolean("shield_is_alarming", false) ||
            _serviceShieldState.value == ShieldState.ALARM_ACTIVE ||
            _serviceShieldState.value == ShieldState.TRIGGERED

        if (isArmed || isAlarming) {
            prefs.edit()
                .putBoolean(KEY_SHIELD_ARMED, true)
                .putBoolean("shield_is_alarming", isAlarming)
                .commit()
            acquireWakeLock()
            scheduleRestartAlarm(200)

            // If alarming or triggered, keep siren playing and relaunch authentication UI immediately!
            if (isAlarming) {
                _serviceShieldState.value = ShieldState.ALARM_ACTIVE
                val cachePrefs = getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)
                val savedVolume = cachePrefs.getInt("alarm_volume_percent", 100)
                val selectedPack = cachePrefs.getString("selected_siren_pack_id", "default") ?: "default"
                val flashlight = cachePrefs.getBoolean("flashlight_enabled", true)

                // Force-keep alarm blaring through service's alarmController
                alarmController?.activateFullAlarm(
                    sirenPackId = selectedPack,
                    enableFlashlight = flashlight,
                    volumePercent = savedVolume
                )
                showAlarmNotification()
                launchMainActivity(isTriggered = true)
                scheduleAlarmTimeoutIfConfigured()
            }
        }
    }

    private fun scheduleAlarmTimeoutIfConfigured() {
        val prefs = getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)
        val timeoutMinutes = try {
            prefs.getInt("alarm_timeout_minutes", 0)
        } catch (_: Exception) { 0 }

        alarmTimeoutJob?.cancel()
        if (timeoutMinutes in 1..60) {
            alarmTimeoutJob = serviceScope.launch {
                delay(timeoutMinutes * 60 * 1000L)
                if (_serviceShieldState.value == ShieldState.ALARM_ACTIVE) {
                    performDisarmCleanup()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    try {
                        val nm = getSystemService(NotificationManager::class.java)
                        nm?.cancel(NOTIFICATION_ID)
                        nm?.cancel(ALARM_NOTIFICATION_ID)
                    } catch (_: Exception) {}
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        if (activeInstance == this) {
            activeInstance = null
        }
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val shouldBeArmed = prefs.getBoolean(KEY_SHIELD_ARMED, false)
        if (shouldBeArmed && _serviceShieldState.value != ShieldState.DISARMED) {
            // Service killed by OS while armed or alarming — DO NOT DISARM! Schedule immediate restart
            scheduleRestartAlarm(200)
            serviceScope.cancel()
            super.onDestroy()
            return
        }
        performDisarmCleanup()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null || wakeLock?.isHeld != true) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Kavach::ShieldMonitoring"
            ).apply {
                acquire(8 * 60 * 60 * 1000L) // Bounded safety limit (released immediately on disarm)
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
        } catch (_: Exception) { }
    }

    private fun scheduleRestartAlarm(delayMs: Long) {
        val intent = Intent(this, KavachForegroundService::class.java).apply {
            action = ACTION_RESTART
        }
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                RESTART_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                RESTART_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMs,
                pendingIntent
            )
        } catch (_: Exception) {
            try {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + delayMs,
                    pendingIntent
                )
            } catch (_: Exception) { }
        }
    }

    private fun cancelRestartAlarm() {
        val intent = Intent(this, KavachForegroundService::class.java)
        val pendingIntent = PendingIntent.getService(
            this,
            RESTART_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun launchMainActivity(isTriggered: Boolean = false) {
        try {
            val relaunchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                if (isTriggered) putExtra("SHIELD_TRIGGERED", true)
            }
            startActivity(relaunchIntent)
        } catch (_: Exception) { }
    }

    private fun createNotificationChannels() {
        val monitorChannel = NotificationChannel(
            CHANNEL_ID,
            "Kavach Shield Monitor",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Maintains background shield protection"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }

        val alarmSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
            .build()

        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Kavach Security Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical security alerts from Kavach"
            setShowBadge(true)
            setSound(alarmSound, audioAttributes)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(monitorChannel)
        manager?.createNotificationChannel(alarmChannel)
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Kavach Shield Active")
            .setContentText("Continuous anti-theft motion protection is active")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun showAlarmNotification() {
        // If swiped away by user or app is in foreground, DO NOT show notification!
        if (alertNotificationSwipedByUser || isAppInForeground) {
            return
        }
        val relaunchIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra("SHIELD_TRIGGERED", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            relaunchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, KavachForegroundService::class.java).apply {
            action = ACTION_DISMISS_ALERT_NOTIFICATION
        }
        val deletePendingIntent = PendingIntent.getService(
            this,
            2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle("Kavach Security Alert")
            .setContentText("Tap to authenticate")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setOngoing(false) // Allows user to swipe it away
            .setAutoCancel(true) // Clears on touch and redirects
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        // Overwrite NOTIFICATION_ID directly so there is NEVER more than 1 notification in the entire system
        // Persistent until user taps it to open app or swipes it away
        manager?.notify(NOTIFICATION_ID, notification)
    }
}
