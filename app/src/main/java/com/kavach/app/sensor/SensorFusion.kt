package com.kavach.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.kavach.app.data.DetectionMode
import com.kavach.app.data.SensorReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Fuses accelerometer, light, and proximity sensors to provide two distinct security modes:
 *
 * 1. DESK / TABLE GUARD (DetectionMode.MOTION):
 *    - Phone rests on a table, desk, nightstand, or charging pad.
 *    - Detects any physical movement, touch, or pickup.
 *    - NEVER suppressed at night or when face-down.
 *
 * 2. PICKPOCKET GUARD (DetectionMode.POCKET):
 *    - Phone is placed in a pocket (jeans, sweatpants, shorts, jacket) or bag.
 *    - Normal walking rhythm (1-2 Hz leg pendulum) is filtered out.
 *    - Detects extraction via 2-out-of-3 sensor fusion:
 *      * Signal A: Light spike (phone emerges from dark pocket into open light)
 *      * Signal B: Proximity uncovered (pocket fabric pulls away)
 *      * Signal C: Extraction jerk + tilt flip (phone pulled upright -> tilted flat in hand)
 */
class SensorFusion(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val _sensorReading = MutableStateFlow(SensorReading())
    val sensorReading: StateFlow<SensorReading> = _sensorReading.asStateFlow()

    private val _motionDetected = MutableStateFlow(false)
    val motionDetected: StateFlow<Boolean> = _motionDetected.asStateFlow()

    // Active detection mode (defaults to TRAVEL)
    private var _detectionMode = DetectionMode.TRAVEL

    // Dynamic Travel Mode Sub-State
    enum class TravelSubState {
        CALIBRATING_BASE, // Initial 5s analysis to determine Pocket vs Desk vs Hand
        IN_POCKET,        // Cloth detected -> Pocket extraction logic active
        DESK_IDLE,        // Surface baseline locked -> Desk Guard with vehicle bump immunity
        ACTIVE_USE        // In hand/moving -> Dormant; 1-min / 30-sec stillness polling active
    }

    private val _travelCurrentSubState = MutableStateFlow(TravelSubState.CALIBRATING_BASE)
    val travelCurrentSubState: StateFlow<TravelSubState> = _travelCurrentSubState.asStateFlow()

    private var travelIdleThresholdMs = 3 * 60 * 1000L // default 3 minutes
    private var lastMovementTimestamp = System.currentTimeMillis()
    private var travelIsDeviceInPocket = false

    // Timing constants for Travel Mode
    private val TRAVEL_ANALYSIS_WINDOW_MS = 5000L   // 5 seconds initial state analysis
    private val TRAVEL_HAND_INTERVAL_MS = 60000L    // 1 minute polling interval
    private val TRAVEL_STILLNESS_WINDOW_MS = 15000L // 15 seconds stillness window (as requested)

    private var travelArmTimestamp = System.currentTimeMillis()
    private var travelBaseAnalysisDone = false
    private var travelAnalysisHadMotion = false
    private var travelAnalysisHadCloth = false
    private var travelHandCycleStart = System.currentTimeMillis()

    // Vehicle bump shock absorber variables
    private var lastOpposingShockTime = 0L
    private var lastVerticalSign = 0

    // === Desk / Table Mode Variables ===
    private var baselineX = 0f
    private var baselineY = 0f
    private var baselineZ = 9.81f
    private var baselineG = 9.81f
    private var normalX = 0f
    private var normalY = 0f
    private var normalZ = 1f
    private var baselineSet = false
    private var calibrationSamples = 0
    private val calibrationCount = 20
    private var consecutiveVerticalSamples = 0

    // Dynamic Desk Baseline for Travel Mode (calibrated fresh when placed idle on each surface)
    private var travelDeskBaselineSet = false
    private var travelDeskCalibrationSamples = 0
    private var travelBaselineX = 0f
    private var travelBaselineY = 0f
    private var travelBaselineZ = 0f
    private var travelBaselineG = 9.81f
    private var travelNormalX = 0f
    private var travelNormalY = 0f
    private var travelNormalZ = 1f
    private var travelConsecutiveVerticalSamples = 0

    // === Pickpocket Guard Mode Variables (POCKET) ===
    private var pocketBaselineLight = -1f
    private var lightCalibrationSamples = 0
    private val lightCalibrationCount = 12
    private var lightBaselineSet = false

    private var initialProximityRecorded = false
    private var pocketInitialProximityCovered = false

    // Timing of recent trigger signals for 2-out-of-3 fusion (within 1.5s window)
    private var lastLightSpikeTime = 0L
    private var lastProximityOpenTime = 0L
    private var lastExtractionTiltTime = 0L
    private val signalWindowMs = 1500L

    // Current live sensor values
    private var currentLight = 0f
    private var currentProximityCovered = false
    private var currentAccelX = 0f
    private var currentAccelY = 0f
    private var currentAccelZ = 9.81f

    // === Thresholds (Adjusted by Sensitivity 1-10) ===
    // Desk mode thresholds (Separates pure vertical lift & tilt from horizontal sliding)
    var verticalThreshold = 2.0f      // m/s² vertical lift off surface
    var tiltAngleThreshold = 11.0f    // degrees tilt from resting angle
    var motionThreshold = 2.0f        // legacy compatibility

    // Pickpocket mode thresholds
    private var lightSpikeThreshold = 30f         // Lux delta above pocket baseline
    private var extractionMotionThreshold = 2.8f  // Jerk/pull force in m/s²
    private var tiltThreshold = 0.58f             // |Y| / Total gravity ratio threshold for horizontal tilt

    fun setDetectionMode(mode: DetectionMode) {
        _detectionMode = mode
    }

    fun setTravelIdleMinutes(minutes: Int) {
        travelIdleThresholdMs = minutes.coerceIn(1, 30) * 60 * 1000L
    }

    fun notifyUserActivity() {
        lastMovementTimestamp = System.currentTimeMillis()
    }

    /**
     * Start listening to all sensors.
     */
    fun startListening() {
        recalibrate()
        _motionDetected.value = false

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /**
     * Stop listening to all sensors.
     */
    fun stopListening() {
        sensorManager.unregisterListener(this)
        _motionDetected.value = false
    }

    /**
     * Reset calibration baselines (called on arming or after disarm).
     */
    fun recalibrate() {
        baselineSet = false
        calibrationSamples = 0
        baselineX = 0f
        baselineY = 0f
        baselineZ = 0f
        baselineG = 9.81f
        normalX = 0f
        normalY = 0f
        normalZ = 1f
        consecutiveVerticalSamples = 0

        travelDeskBaselineSet = false
        travelDeskCalibrationSamples = 0
        travelBaselineX = 0f
        travelBaselineY = 0f
        travelBaselineZ = 0f
        travelBaselineG = 9.81f
        travelNormalX = 0f
        travelNormalY = 0f
        travelNormalZ = 1f
        travelConsecutiveVerticalSamples = 0

        lightBaselineSet = false
        lightCalibrationSamples = 0
        pocketBaselineLight = -1f

        initialProximityRecorded = false
        pocketInitialProximityCovered = false

        lastLightSpikeTime = 0L
        lastProximityOpenTime = 0L
        lastExtractionTiltTime = 0L

        // Reset Travel Mode state
        travelArmTimestamp = System.currentTimeMillis()
        travelBaseAnalysisDone = false
        travelAnalysisHadMotion = false
        travelAnalysisHadCloth = false
        travelHandCycleStart = travelArmTimestamp
        _travelCurrentSubState.value = TravelSubState.CALIBRATING_BASE
        lastOpposingShockTime = 0L
        lastVerticalSign = 0

        _motionDetected.value = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
            Sensor.TYPE_LIGHT -> handleLight(event)
            Sensor.TYPE_PROXIMITY -> handleProximity(event)
        }

        // Live Pocket Status indicator (is phone currently in dark/vertical enclosure)
        val totalG = sqrt(currentAccelX * currentAccelX + currentAccelY * currentAccelY + currentAccelZ * currentAccelZ)
        val verticalRatio = if (totalG > 0.1f) abs(currentAccelY) / totalG else 0f
        val isInPocketVisual = (currentLight < 35f && (currentProximityCovered || verticalRatio > 0.65f))

        _sensorReading.value = _sensorReading.value.copy(
            lightLevel = currentLight,
            proximityCovered = currentProximityCovered,
            isInPocket = isInPocketVisual
        )
    }

    private fun handleAccelerometer(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        currentAccelX = x
        currentAccelY = y
        currentAccelZ = z

        val totalG = sqrt(x * x + y * y + z * z)
        val accelDeviation = abs(totalG - 9.81f)

        // Reset movement timestamp whenever noticeable motion occurs
        if (accelDeviation > 0.4f) {
            lastMovementTimestamp = System.currentTimeMillis()
        }

        when (_detectionMode) {
            DetectionMode.DESK -> {
                handleDeskDetection(x, y, z, false)
            }

            DetectionMode.SLEEP -> {
                handleSleepDetection(x, y, z)
            }

            DetectionMode.POCKET -> {
                handlePocketDetection(x, y, z, totalG, accelDeviation)
            }

            DetectionMode.TRAVEL -> {
                handleTravelDetection(x, y, z, totalG, accelDeviation)
            }
        }
    }

    /**
     * Desk Mode: Pure vertical projection with strict Horizontal Zig-Zag rejection.
     * Instant detection on upward lift off table. Horizontal sliding/zig-zag produces
     * high tangential forces that are isolated and rejected completely.
     */
    private fun handleDeskDetection(x: Float, y: Float, z: Float, isSecondary: Boolean) {
        if (!baselineSet) {
            baselineX = ((baselineX * calibrationSamples) + x) / (calibrationSamples + 1)
            baselineY = ((baselineY * calibrationSamples) + y) / (calibrationSamples + 1)
            baselineZ = ((baselineZ * calibrationSamples) + z) / (calibrationSamples + 1)
            calibrationSamples++
            if (calibrationSamples >= calibrationCount) {
                val mag = sqrt(baselineX * baselineX + baselineY * baselineY + baselineZ * baselineZ)
                baselineG = if (mag > 0.1f) mag else 9.81f
                normalX = baselineX / baselineG
                normalY = baselineY / baselineG
                normalZ = baselineZ / baselineG
                consecutiveVerticalSamples = 0

        travelDeskBaselineSet = false
        travelDeskCalibrationSamples = 0
        travelBaselineX = 0f
        travelBaselineY = 0f
        travelBaselineZ = 0f
        travelBaselineG = 9.81f
        travelNormalX = 0f
        travelNormalY = 0f
        travelNormalZ = 1f
        travelConsecutiveVerticalSamples = 0
                baselineSet = true
            }
            return
        }

        val normalForce = (x * normalX + y * normalY + z * normalZ)
        val verticalDelta = normalForce - baselineG
        val absVerticalDelta = abs(verticalDelta)

        // Calculate tangential horizontal acceleration vector along the surface plane
        val tanX = x - normalForce * normalX
        val tanY = y - normalForce * normalY
        val tanZ = z - normalForce * normalZ
        val horizontalForce = sqrt(tanX * tanX + tanY * tanY + tanZ * tanZ)

        // Strict horizontal sliding/zig-zag immunity:
        // When sliding horizontally, horizontal force dominates vertical lift.
        val isHorizontalSlide = horizontalForce > 1.25f * absVerticalDelta || (horizontalForce > 2.0f && absVerticalDelta < verticalThreshold * 1.5f)

        if (!isSecondary) {
            _sensorReading.value = _sensorReading.value.copy(motionMagnitude = absVerticalDelta)
        }

        // Instant lift off surface (strictly true upward lift, zero trigger on horizontal zig-zag)
        if (!isHorizontalSlide && absVerticalDelta > verticalThreshold) {
            consecutiveVerticalSamples++
            if (consecutiveVerticalSamples >= 2) {
                _motionDetected.value = true
            }
        } else {
            consecutiveVerticalSamples = 0

        travelDeskBaselineSet = false
        travelDeskCalibrationSamples = 0
        travelBaselineX = 0f
        travelBaselineY = 0f
        travelBaselineZ = 0f
        travelBaselineG = 9.81f
        travelNormalX = 0f
        travelNormalY = 0f
        travelNormalZ = 1f
        travelConsecutiveVerticalSamples = 0
        }
    }

    /**
     * Sleep Mode: High-tolerance nocturnal protection.
     * Filters dream-twitching, rolling in bed, mattress bounce, and horizontal sliding.
     * Triggers strictly when lifted sky-high into the air.
     */
    private fun handleSleepDetection(x: Float, y: Float, z: Float) {
        if (!baselineSet) {
            baselineX = ((baselineX * calibrationSamples) + x) / (calibrationSamples + 1)
            baselineY = ((baselineY * calibrationSamples) + y) / (calibrationSamples + 1)
            baselineZ = ((baselineZ * calibrationSamples) + z) / (calibrationSamples + 1)
            calibrationSamples++
            if (calibrationSamples >= calibrationCount) {
                val mag = sqrt(baselineX * baselineX + baselineY * baselineY + baselineZ * baselineZ)
                baselineG = if (mag > 0.1f) mag else 9.81f
                normalX = baselineX / baselineG
                normalY = baselineY / baselineG
                normalZ = baselineZ / baselineG
                consecutiveVerticalSamples = 0

        travelDeskBaselineSet = false
        travelDeskCalibrationSamples = 0
        travelBaselineX = 0f
        travelBaselineY = 0f
        travelBaselineZ = 0f
        travelBaselineG = 9.81f
        travelNormalX = 0f
        travelNormalY = 0f
        travelNormalZ = 1f
        travelConsecutiveVerticalSamples = 0
                baselineSet = true
            }
            return
        }

        val normalForce = (x * normalX + y * normalY + z * normalZ)
        val verticalDelta = normalForce - baselineG
        val absVerticalDelta = abs(verticalDelta)

        // Tangential acceleration on bed surface
        val tanX = x - normalForce * normalX
        val tanY = y - normalForce * normalY
        val tanZ = z - normalForce * normalZ
        val horizontalForce = sqrt(tanX * tanX + tanY * tanY + tanZ * tanZ)

        // Filter mattress movement, bed sheet sliding, and horizontal zig-zags
        val isHorizontalOrMattressBounce = horizontalForce > 1.20f * absVerticalDelta || (horizontalForce > 1.8f && absVerticalDelta < 5.5f)

        _sensorReading.value = _sensorReading.value.copy(motionMagnitude = absVerticalDelta)

        // High sky-high threshold (6.5 m/s² default) + sustained 6 consecutive samples (~120ms)
        val sleepThreshold = verticalThreshold * 2.5f + 2.0f
        if (!isHorizontalOrMattressBounce && absVerticalDelta > sleepThreshold) {
            consecutiveVerticalSamples++
            if (consecutiveVerticalSamples >= 6) {
                _motionDetected.value = true
            }
        } else {
            consecutiveVerticalSamples = 0

        travelDeskBaselineSet = false
        travelDeskCalibrationSamples = 0
        travelBaselineX = 0f
        travelBaselineY = 0f
        travelBaselineZ = 0f
        travelBaselineG = 9.81f
        travelNormalX = 0f
        travelNormalY = 0f
        travelNormalZ = 1f
        travelConsecutiveVerticalSamples = 0
        }
    }

    /**
     * Pocket Mode: Standard single-arm pickpocket extraction detection.
     */
    private fun handlePocketDetection(x: Float, y: Float, z: Float, totalG: Float, accelDeviation: Float) {
        if (!lightBaselineSet) {
            return
        }

        val verticalRatio = if (totalG > 0.1f) abs(y) / totalG else 0f
        val isTiltedFlat = verticalRatio < tiltThreshold
        val isExtractionJerk = accelDeviation > extractionMotionThreshold

        if (isTiltedFlat && isExtractionJerk) {
            lastExtractionTiltTime = System.currentTimeMillis()
            evaluatePickpocketFusion()
        }

        _sensorReading.value = _sensorReading.value.copy(motionMagnitude = accelDeviation)
    }

    /**
     * Travel Mode: Dynamically shifts behavior based on phone environment:
     * 1. 5-Second Base State Analysis on start:
     *    - Cloth detected over screen -> IN_POCKET logic (vertical extraction trigger)
     *    - Flat & motionless on surface -> DESK_IDLE logic (vertical lift with car bump immunity)
     *    - Micro-movements in hand -> ACTIVE_USE (silent/dormant)
     * 2. When in ACTIVE_USE: 1-minute interval with a 30-second Stillness Window.
     *    If movement detected during 30s, 1-min timer restarts.
     *    If motionless for full 30s, locks fresh desk baseline and shifts to DESK_IDLE.
     * 3. Parameter of trigger in both desk and pocket is strictly vertical movement.
     */
    private fun handleTravelDetection(x: Float, y: Float, z: Float, totalG: Float, accelDeviation: Float) {
        val now = System.currentTimeMillis()

        // Phase 1: 5-Second Initial Base State Analysis (Guaranteed ZERO alarm, purely silent observation)
        if (!travelBaseAnalysisDone) {
            _motionDetected.value = false // Strictly guarantee no alarm can ever trigger during the 5s analysis window
            _travelCurrentSubState.value = TravelSubState.CALIBRATING_BASE

            val isClothOverScreen = (currentLight < 25f && currentProximityCovered)
            if (isClothOverScreen) {
                travelAnalysisHadCloth = true
            }
            if (accelDeviation > 0.20f) { // Detect even the slightest movement
                travelAnalysisHadMotion = true
            }

            // Accumulate candidate desk baseline while still
            if (!travelAnalysisHadMotion) {
                travelBaselineX = ((travelBaselineX * travelDeskCalibrationSamples) + x) / (travelDeskCalibrationSamples + 1)
                travelBaselineY = ((travelBaselineY * travelDeskCalibrationSamples) + y) / (travelDeskCalibrationSamples + 1)
                travelBaselineZ = ((travelBaselineZ * travelDeskCalibrationSamples) + z) / (travelDeskCalibrationSamples + 1)
                travelDeskCalibrationSamples++
            }

            if (now - travelArmTimestamp >= TRAVEL_ANALYSIS_WINDOW_MS) {
                travelBaseAnalysisDone = true
                if (travelAnalysisHadCloth) {
                    _travelCurrentSubState.value = TravelSubState.IN_POCKET
                    travelDeskBaselineSet = false
                    pocketBaselineLight = currentLight
                    lightBaselineSet = true
                } else if (!travelAnalysisHadMotion && travelDeskCalibrationSamples >= 15) {
                    val mag = sqrt(travelBaselineX * travelBaselineX + travelBaselineY * travelBaselineY + travelBaselineZ * travelBaselineZ)
                    travelBaselineG = if (mag > 0.1f) mag else 9.81f
                    travelNormalX = travelBaselineX / travelBaselineG
                    travelNormalY = travelBaselineY / travelBaselineG
                    travelNormalZ = travelBaselineZ / travelBaselineG
                    travelConsecutiveVerticalSamples = 0
                    travelDeskBaselineSet = true
                    _travelCurrentSubState.value = TravelSubState.DESK_IDLE
                } else {
                    // Slightest movement detected -> user is using or holding phone!
                    _travelCurrentSubState.value = TravelSubState.ACTIVE_USE
                    travelHandCycleStart = now // Start 1-minute countdown
                    travelDeskBaselineSet = false
                }
            }
            _sensorReading.value = _sensorReading.value.copy(motionMagnitude = accelDeviation)
            return
        }

        // Phase 2: Active Sub-State Handling
        when (_travelCurrentSubState.value) {
            TravelSubState.IN_POCKET -> {
                // If phone is taken out of pocket into open room/day light
                val isOutOfPocket = (currentLight >= 35f && !currentProximityCovered)
                if (isOutOfPocket) {
                    // Evaluate pickpocket extraction fusion
                    handlePocketDetection(x, y, z, totalG, accelDeviation)

                    // If no theft was triggered and phone has been in light beyond the fusion window, transition to ACTIVE_USE
                    if (!_motionDetected.value && (now - lastProximityOpenTime) > signalWindowMs) {
                        _travelCurrentSubState.value = TravelSubState.ACTIVE_USE
                        travelHandCycleStart = now
                        travelDeskBaselineSet = false
                        travelDeskCalibrationSamples = 0
                    }
                } else {
                    // Strictly vertical extraction detection while inside pocket/bag
                    handlePocketDetection(x, y, z, totalG, accelDeviation)
                }
            }

            TravelSubState.DESK_IDLE -> {
                // If user puts phone into pocket from desk
                val isPutInPocket = (currentLight < 25f && currentProximityCovered)
                if (isPutInPocket) {
                    _travelCurrentSubState.value = TravelSubState.IN_POCKET
                    travelDeskBaselineSet = false
                    travelDeskCalibrationSamples = 0
                    pocketBaselineLight = currentLight
                    lightBaselineSet = true
                    handlePocketDetection(x, y, z, totalG, accelDeviation)
                } else {
                    // Strictly vertical lift with vehicle bump shock absorber
                    handleTravelDeskDetection(x, y, z)
                }
            }

            TravelSubState.ACTIVE_USE, TravelSubState.CALIBRATING_BASE -> {
                // 1. Check if user put phone inside pocket or bag!
                val verticalRatio = if (totalG > 0.1f) abs(y) / totalG else 0f
                val isPutInPocket = (currentLight < 25f && currentProximityCovered) || (currentLight < 15f && verticalRatio > 0.65f)

                if (isPutInPocket) {
                    _travelCurrentSubState.value = TravelSubState.IN_POCKET
                    travelDeskBaselineSet = false
                    travelDeskCalibrationSamples = 0
                    pocketBaselineLight = currentLight
                    lightBaselineSet = true
                    handlePocketDetection(x, y, z, totalG, accelDeviation)
                    return
                }

                // 2. Phone is actively with user in hand: dormant, never false alarms
                _sensorReading.value = _sensorReading.value.copy(motionMagnitude = accelDeviation)

                val elapsedInCycle = now - travelHandCycleStart
                if (elapsedInCycle < TRAVEL_HAND_INTERVAL_MS) {
                    // Still in the 1-minute waiting period
                    if (accelDeviation > 0.35f) {
                        lastMovementTimestamp = now
                    }
                } else {
                    // Inside the 15-second Stillness Window!
                    val elapsedInStillness = elapsedInCycle - TRAVEL_HAND_INTERVAL_MS
                    if (accelDeviation > 0.30f) {
                        // Movement detected during the 15s window! Restart the 1-minute timer
                        travelHandCycleStart = now
                        travelDeskBaselineSet = false
                        travelDeskCalibrationSamples = 0
                    } else {
                        // Stillness sample collection
                        travelBaselineX = ((travelBaselineX * travelDeskCalibrationSamples) + x) / (travelDeskCalibrationSamples + 1)
                        travelBaselineY = ((travelBaselineY * travelDeskCalibrationSamples) + y) / (travelDeskCalibrationSamples + 1)
                        travelBaselineZ = ((travelBaselineZ * travelDeskCalibrationSamples) + z) / (travelDeskCalibrationSamples + 1)
                        travelDeskCalibrationSamples++

                        if (elapsedInStillness >= TRAVEL_STILLNESS_WINDOW_MS && travelDeskCalibrationSamples >= 15) {
                            // 15 seconds of stillness complete: user set down phone on table/desk! Switch to DESK_IDLE!
                            val mag = sqrt(travelBaselineX * travelBaselineX + travelBaselineY * travelBaselineY + travelBaselineZ * travelBaselineZ)
                            travelBaselineG = if (mag > 0.1f) mag else 9.81f
                            travelNormalX = travelBaselineX / travelBaselineG
                            travelNormalY = travelBaselineY / travelBaselineG
                            travelNormalZ = travelBaselineZ / travelBaselineG
                            travelConsecutiveVerticalSamples = 0
                            travelDeskBaselineSet = true
                            _travelCurrentSubState.value = TravelSubState.DESK_IDLE
                        }
                    }
                }
            }
        }
    }

    /**
     * Travel Mode Desk Guard:
     * When resting on a surface, monitors strictly for true vertical lift with vehicle bump shock absorber.
     * Vehicle road shocks (potholes/car jumps) produce rapid bidirectional rebound (< 250ms), which is filtered out.
     */
    private fun handleTravelDeskDetection(x: Float, y: Float, z: Float) {
        if (!travelDeskBaselineSet) return

        val normalForce = (x * travelNormalX + y * travelNormalY + z * travelNormalZ)
        val verticalDelta = normalForce - travelBaselineG
        val absVerticalDelta = abs(verticalDelta)

        val tanX = x - normalForce * travelNormalX
        val tanY = y - normalForce * travelNormalY
        val tanZ = z - normalForce * travelNormalZ
        val horizontalForce = sqrt(tanX * tanX + tanY * tanY + tanZ * tanZ)

        // Strict horizontal sliding/zig-zag immunity
        val isHorizontalSlide = horizontalForce > 1.25f * absVerticalDelta || (horizontalForce > 2.0f && absVerticalDelta < 5.0f)

        // Vehicle Bump Shock Absorber:
        // Road bumps and potholes produce rapid bidirectional vertical oscillations (+ then - within < 250ms).
        val now = System.currentTimeMillis()
        val currentSign = if (verticalDelta > 0.5f) 1 else if (verticalDelta < -0.5f) -1 else 0
        if (currentSign != 0 && lastVerticalSign != 0 && currentSign != lastVerticalSign) {
            lastOpposingShockTime = now
        }
        if (currentSign != 0) {
            lastVerticalSign = currentSign
        }
        val isVehicleOscillation = (now - lastOpposingShockTime) < 250L

        _sensorReading.value = _sensorReading.value.copy(motionMagnitude = absVerticalDelta)

        // Lift off surface threshold: higher than regular desk mode to absorb vehicle motion (4.2 m/s²)
        val travelDeskLiftThreshold = (verticalThreshold * 1.8f + 1.2f).coerceAtLeast(4.0f)

        // Must be true upward vertical lift (> 0), sustained for 3 consecutive samples, without horizontal slide or road shock
        if (!isHorizontalSlide && !isVehicleOscillation && verticalDelta > travelDeskLiftThreshold) {
            travelConsecutiveVerticalSamples++
            if (travelConsecutiveVerticalSamples >= 3) {
                _motionDetected.value = true
            }
        } else {
            travelConsecutiveVerticalSamples = 0
        }
    }

    private fun handleLight(event: SensorEvent) {
        currentLight = event.values[0]

        if (_detectionMode == DetectionMode.POCKET || _detectionMode == DetectionMode.TRAVEL) {
            if (_detectionMode == DetectionMode.TRAVEL && !travelBaseAnalysisDone) {
                return // Absolutely zero reaction or triggers during initial 5s baseline analysis
            }

            if (!lightBaselineSet) {
                // Calibrate baseline light inside pocket
                if (pocketBaselineLight < 0f) {
                    pocketBaselineLight = currentLight
                } else {
                    pocketBaselineLight = ((pocketBaselineLight * lightCalibrationSamples) + currentLight) / (lightCalibrationSamples + 1)
                }
                lightCalibrationSamples++
                if (lightCalibrationSamples >= lightCalibrationCount) {
                    lightBaselineSet = true
                }
                return
            }

            val lightDelta = currentLight - pocketBaselineLight
            val isSpike = lightDelta >= lightSpikeThreshold || (pocketBaselineLight > 0f && currentLight > pocketBaselineLight * 3.5f && currentLight >= 25f)

            if (isSpike) {
                lastLightSpikeTime = System.currentTimeMillis()
                // If massive light spike (pulled out into daylight / lit room > 75 lux jump), trigger immediately
                if (lightDelta > 75f) {
                    _motionDetected.value = true
                    return
                }
                evaluatePickpocketFusion()
            }
        }
    }

    private fun handleProximity(event: SensorEvent) {
        val maxRange = event.sensor.maximumRange
        val isCovered = event.values[0] < maxRange
        val wasCovered = currentProximityCovered
        currentProximityCovered = isCovered

        if (_detectionMode == DetectionMode.POCKET || _detectionMode == DetectionMode.TRAVEL) {
            if (_detectionMode == DetectionMode.TRAVEL && !travelBaseAnalysisDone) {
                return // Absolutely zero reaction or triggers during initial 5s baseline analysis
            }

            if (!initialProximityRecorded) {
                pocketInitialProximityCovered = isCovered
                initialProximityRecorded = true
            }

            // Proximity uncovered event (was covered, now open)
            if (wasCovered && !isCovered) {
                lastProximityOpenTime = System.currentTimeMillis()
                evaluatePickpocketFusion()
            }
        }
    }

    /**
     * Smart 2-out-of-3 Sensor Fusion for Pickpocket Extraction:
     * - Signal A: Light Spike (phone exposed to open light)
     * - Signal B: Proximity Opened (fabric pulled away)
     * - Signal C: Extraction Jerk + Tilt Flat (phone pulled out and leveled in hand)
     *
     * Walking produces 0 or at most 1 signal.
     * Extraction produces at least 2 signals within the 1.5s window.
     */
    private fun evaluatePickpocketFusion() {
        if (_detectionMode == DetectionMode.TRAVEL && !travelBaseAnalysisDone) {
            return
        }

        val now = System.currentTimeMillis()
        var score = 0

        val lightActive = (now - lastLightSpikeTime) <= signalWindowMs
        val proximityActive = (now - lastProximityOpenTime) <= signalWindowMs
        val extractionTiltActive = (now - lastExtractionTiltTime) <= signalWindowMs

        if (lightActive) score++
        if (proximityActive) score++
        if (extractionTiltActive) score++

        if (score >= 2) {
            _motionDetected.value = true
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    /**
     * Backwards-compatible no-op (pocket detection is now a dedicated mode).
     */
    fun setPocketDetectionEnabled(enabled: Boolean) {
        // Retained for compatibility
    }

    /**
     * Update detection thresholds based on sensitivity level (1-10).
     * Level 1: Firm movements only
     * Level 5: Balanced
     * Level 10: Ultra-sensitive
     */
    fun setSensitivity(level: Int) {
        val lvl = level.coerceIn(1, 10)
        val t = (lvl - 1) / 9.0f // 0.0 at level 1, 1.0 at level 10

        // Desk mode thresholds (Pure vertical lift off surface):
        // Level 1 (Lowest / Bedside Sleep Guard):
        //   Requires a firm, deliberate vertical lift of 3.8 m/s² off the table.
        //   Bed mattress movements, table vibrations, and horizontal sliding are 100% ignored.
        // Level 5 (Medium / Balanced Default):
        //   Requires 1.8 m/s² vertical lift. Standard hand pickup triggers smoothly.
        // Level 10 (Highest / Feather-Touch):
        //   Requires 0.35 m/s² vertical lift. Triggers on the slightest upward lift or disturbance.
        verticalThreshold = 3.8f - (t * 3.45f)
        motionThreshold = verticalThreshold

        // Pickpocket mode thresholds:
        // Level 1: needs 50 lux jump, 4.0 m/s² pull
        // Level 5: needs 28 lux jump, 2.7 m/s² pull
        // Level 10: needs 10 lux jump, 1.6 m/s² pull
        lightSpikeThreshold = (50f - (t * 40f)).coerceAtLeast(10f)
        extractionMotionThreshold = 4.0f - (t * 2.4f)
        tiltThreshold = 0.50f + (t * 0.18f)
    }

    init {
        setSensitivity(5)
    }

    fun resetMotionFlag() {
        _motionDetected.value = false
    }
}
