package com.kavach.app.alarm

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*

/**
 * Controls the alarm siren, flashlight strobe, haptic feedback, and volume lock.
 */
class AlarmController(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var strobeJob: Job? = null
    private var volumeLockJob: Job? = null
    private var isAlarmActive = false
    private var isVolumeLocked = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Camera ID for flashlight
    private val flashCameraId: String? by lazy {
        try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        }
    }

    private var baselineAlarmVolume: Int = -1
    private var baselineMusicVolume: Int = -1
    private var baselineRingVolume: Int = -1

    fun getSavedVolumePercent(): Int {
        return try {
            context.getSharedPreferences("kavach_cache_prefs", Context.MODE_PRIVATE)
                .getInt("alarm_volume_percent", 100)
        } catch (_: Exception) {
            100
        }
    }

    private var targetAlarmVolumePercent: Int = getSavedVolumePercent()

    /**
     * Lock volume baseline when armed.
     * Keeps what user currently has set as minimum floor, preventing thief from lowering or muting it.
     * User can still increase volume, but it will never be allowed to decrease below the baseline.
     */
    fun lockVolume(volumePercent: Int = -1) {
        val effectiveVolume = getSavedVolumePercent()
        targetAlarmVolumePercent = effectiveVolume
        if (isVolumeLocked) return
        isVolumeLocked = true

        try {
            baselineAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            baselineMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            baselineRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)

            // Ensure baseline respects the volume percent floor (at least 1 step)
            val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)

            val minAlarmFloor = ((maxAlarm * targetAlarmVolumePercent) / 100f).toInt().coerceAtLeast(0)
            val minMusicFloor = ((maxMusic * targetAlarmVolumePercent) / 100f).toInt().coerceAtLeast(0)
            val minRingFloor = ((maxRing * targetAlarmVolumePercent) / 100f).toInt().coerceAtLeast(0)

            if (baselineAlarmVolume < minAlarmFloor) baselineAlarmVolume = minAlarmFloor
            if (baselineMusicVolume < minMusicFloor) baselineMusicVolume = minMusicFloor
            if (baselineRingVolume < minRingFloor) baselineRingVolume = minRingFloor
        } catch (e: Exception) {
            val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            baselineAlarmVolume = ((maxAlarm * targetAlarmVolumePercent) / 100f).toInt().coerceAtLeast(0)
            baselineMusicVolume = ((maxMusic * targetAlarmVolumePercent) / 100f).toInt().coerceAtLeast(0)
            baselineRingVolume = ((maxRing * targetAlarmVolumePercent) / 100f).toInt().coerceAtLeast(0)
        }

        // Maintain the configured volume floor — volume can be adjusted down to the floor, but never below it
        volumeLockJob?.cancel()
        volumeLockJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isAlarmActive && isVolumeLocked) {
                try {
                    val minAlarm = getMinVolumeFloor(AudioManager.STREAM_ALARM)
                    val minMusic = getMinVolumeFloor(AudioManager.STREAM_MUSIC)

                    val curAlarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    if (curAlarm < minAlarm) {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, minAlarm, 0)
                    }

                    val curMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (curMusic < minMusic) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, minMusic, 0)
                    }
                } catch (_: Exception) { }
                delay(300L)
            }
        }
    }

    /**
     * Calculates the minimum volume step for a given stream based on the configured volume percent.
     */
    fun getMinVolumeFloor(streamType: Int): Int {
        val maxVol = audioManager.getStreamMaxVolume(streamType)
        val percent = if (targetAlarmVolumePercent > 0) targetAlarmVolumePercent else getSavedVolumePercent()
        return ((maxVol * percent) / 100f).toInt().coerceAtLeast(0)
    }

    /**
     * Unlock volume — allow normal volume changes again.
     */
    fun unlockVolume() {
        isVolumeLocked = false
        volumeLockJob?.cancel()
        volumeLockJob = null
        baselineAlarmVolume = -1
        baselineMusicVolume = -1
        baselineRingVolume = -1
    }

    /**
     * Emit a subtle single haptic tick to alert owner stealthily without vibrating repeatedly.
     */
    fun emitOwnerHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }

    /**
     * BLAST the full alarm siren at configured volume percent (5-100%).
     * Overrides silent/vibrate mode. Locks volume to prevent silencing.
     */
    fun activateFullAlarm(
        sirenPackId: String = "default",
        enableFlashlight: Boolean = true,
        volumePercent: Int = -1
    ) {
        if (isAlarmActive && mediaPlayer?.isPlaying == true) return
        isAlarmActive = true
        val effectiveVolume = getSavedVolumePercent()
        targetAlarmVolumePercent = effectiveVolume

        // Lock volume to desired percent and keep it locked
        lockVolume(effectiveVolume)

        // Force desired volume regardless of current mode
        try {
            val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val targetVolume = ((maxAlarm * targetAlarmVolumePercent) / 100f).toInt().coerceAtLeast(0)
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                targetVolume,
                0
            )
        } catch (e: Exception) { /* Some devices restrict this */ }

        // Play alarm sound on loop
        startAlarmAudio(sirenPackId, loop = true)

        // Aggressive haptic vibration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 500, 100, 500, 100, 500)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 100, 500, 100, 500), 0)
        }

        // Flashlight strobe
        if (enableFlashlight) {
            startFlashlightStrobe()
        }

        // Trigger emergency Telegram dispatch (GPS location + 5 intruder snapshots + 2-way remote listener)
        try {
            com.kavach.app.telegram.TelegramEmergencyCoordinator.onAlarmDetonated(context.applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val isPlaying: Boolean
        get() = isAlarmActive && (mediaPlayer?.isPlaying == true)

    private fun getRawResForPack(sirenPackId: String): Int? {
        return when (sirenPackId) {
            "default", "police" -> com.kavach.app.R.raw.siren_police
            "system_alarm" -> null // Uses device default alarm sound via RingtoneManager
            "air_raid" -> com.kavach.app.R.raw.siren_air_raid
            "biohazard" -> com.kavach.app.R.raw.siren_biohazard
            "air_horn" -> com.kavach.app.R.raw.siren_air_horn
            "laser_alarm" -> com.kavach.app.R.raw.siren_laser_alarm
            "sub_dive" -> com.kavach.app.R.raw.siren_sub_dive
            "bank_heist" -> com.kavach.app.R.raw.siren_bank_heist
            "alien_invasion" -> com.kavach.app.R.raw.siren_alien_invasion
            "fire_truck" -> com.kavach.app.R.raw.siren_fire_truck
            else -> com.kavach.app.R.raw.siren_police
        }
    }

    private fun startAlarmAudio(sirenPackId: String, loop: Boolean) {
        try {
            mediaPlayer?.release()
            mediaPlayer = null

            val volRatio = (targetAlarmVolumePercent / 100f).coerceIn(0.0f, 1.0f)

            if (sirenPackId != "system_alarm") {
                val rawRes = getRawResForPack(sirenPackId)
                if (rawRes != null) {
                    try {
                        val afd = context.resources.openRawResourceFd(rawRes)
                        if (afd != null) {
                            mediaPlayer = MediaPlayer().apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_ALARM)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                        .build()
                                )
                                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                afd.close()
                                isLooping = loop
                                prepare()
                                setVolume(volRatio, volRatio)
                                start()
                            }
                            if (mediaPlayer != null) return
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Fallback or explicit system alarm: strictly use TYPE_ALARM or TYPE_RINGTONE (never notification chime)
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(context, alarmUri)
                isLooping = loop
                prepare()
                setVolume(volRatio, volRatio)
                start()
            }
        } catch (e: Exception) {
            // Fallback: use ringtone
            try {
                val volRatio = (targetAlarmVolumePercent / 100f).coerceIn(0.0f, 1.0f)
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(context, ringtoneUri)
                    isLooping = loop
                    prepare()
                    setVolume(volRatio, volRatio)
                    start()
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    /**
     * Test the alarm sound at current phone volume without forcing max or locking volume.
     * Allows user to safely hear what it sounds like even at the lowest volume setting.
     */
    fun playTestAlarm(sirenPackId: String) {
        stopPreview()
        try {
            val volRatio = (getSavedVolumePercent() / 100f).coerceIn(0.0f, 1.0f)
            if (sirenPackId != "system_alarm") {
                val rawRes = getRawResForPack(sirenPackId)
                if (rawRes != null) {
                    try {
                        val afd = context.resources.openRawResourceFd(rawRes)
                        if (afd != null) {
                            mediaPlayer = MediaPlayer().apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .build()
                                )
                                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                afd.close()
                                prepare()
                                setVolume(volRatio, volRatio)
                                start()
                            }
                            if (mediaPlayer != null) return
                        }
                    } catch (_: Exception) { }
                }
            }
            if (mediaPlayer == null) {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(context, alarmUri)
                    prepare()
                    setVolume(volRatio, volRatio)
                    start()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Preview a siren sound for 3 seconds without full lock.
     */
    fun previewSiren(sirenPackId: String) {
        stopPreview()
        try {
            val volRatio = (getSavedVolumePercent() / 100f).coerceIn(0.0f, 1.0f)
            if (sirenPackId != "system_alarm") {
                val rawRes = getRawResForPack(sirenPackId)
                if (rawRes != null) {
                    try {
                        val afd = context.resources.openRawResourceFd(rawRes)
                        if (afd != null) {
                            mediaPlayer = MediaPlayer().apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                        .build()
                                )
                                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                afd.close()
                                prepare()
                                setVolume(volRatio, volRatio)
                                start()
                            }
                            if (mediaPlayer != null) return
                        }
                    } catch (_: Exception) { }
                }
            }
            if (mediaPlayer == null) {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, alarmUri)
                    prepare()
                    setVolume(volRatio, volRatio)
                    start()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopPreview() {
        if (!isAlarmActive) {
            try {
                mediaPlayer?.apply {
                    if (isPlaying) stop()
                    release()
                }
            } catch (e: Exception) { /* ignore */ }
            mediaPlayer = null
        }
    }

    /**
     * Stop all alarm outputs.
     */
    fun deactivateAlarm() {
        isAlarmActive = false

        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) { /* ignore */ }
        mediaPlayer = null

        vibrator.cancel()
        stopFlashlightStrobe()
        unlockVolume()

        // Notify emergency contact that device was safely disarmed
        try {
            com.kavach.app.telegram.TelegramEmergencyCoordinator.onAlarmDeactivated(context.applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startFlashlightStrobe() {
        val camId = flashCameraId ?: return
        strobeJob = CoroutineScope(Dispatchers.Default).launch {
            var on = false
            while (isActive && isAlarmActive) {
                try {
                    cameraManager.setTorchMode(camId, !on)
                    on = !on
                } catch (e: Exception) { /* ignore */ }
                delay(250) // Safe 2 Hz optical strobe
            }
            // Ensure torch is off when done
            try { cameraManager.setTorchMode(camId, false) } catch (e: Exception) {}
        }
    }

    private fun stopFlashlightStrobe() {
        strobeJob?.cancel()
        strobeJob = null
        flashCameraId?.let { camId ->
            try { cameraManager.setTorchMode(camId, false) } catch (e: Exception) {}
        }
    }

    fun cleanup() {
        deactivateAlarm()
    }
}

