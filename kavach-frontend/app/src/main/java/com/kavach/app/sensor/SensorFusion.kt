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

    private var travelIdleThresholdMs = 3 * 60 * 1000L // default 3 minutes (configurable x minutes)
    private var lastMovementTimestamp = System.currentTimeMillis()
    private var travelIsDeviceInPocket = false

    // Timing constants for Travel Mode
    private val TRAVEL_ANALYSIS_WINDOW_MS = 5000L   // 5 seconds initial state analysis

    private var travelArmTimestamp = System.currentTimeMillis()
    private var travelBaseAnalysisDone = false
    private var travelAnalysisHadMotion = false
    private var travelAnalysisHadCloth = false
    private var stationarySinceTimestamp = 0L

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
        stationarySinceTimestamp = 0L
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
     *    - If motion detected during first 5s -> ACTIVE_USE (monitors continuously for x min stillness).
     *    - If motionless during first 5s:
     *      - In pocket/bag (proximity covered / 0 lux vertical) -> IN_POCKET logic.
     *      - Flat & motionless on surface (even with 1 lux in dark car) -> DESK_IDLE logic.
     * 2. ACTIVE_USE:
     *    - Phone is actively held/used: dormant, zero false alarms.
     *    - If placed inside pocket/bag -> transitions immediately to IN_POCKET.
     *    - Continuously monitors sensor data: if phone is stationary with constant values
     *      for the user-configured x number of minutes, auto-activates DESK_IDLE!
     *    - Any movement or non-constant values reset the x-minute timer immediately.
     * 3. Once in IN_POCKET or DESK_IDLE:
     *    - Runs 24x7 until triggered & authenticated, or authenticated without triggering.
     */
    private fun handleTravelDetection(x: Float, y: Float, z: Float, totalG: Float, accelDeviation: Float) {
        val now = System.currentTimeMillis()

        // Phase 1: 5-Second Initial Base State Analysis (Guaranteed ZERO alarm, purely silent observation)
        if (!travelBaseAnalysisDone) {
            _motionDetected.value = false // Strictly guarantee no alarm can ever trigger during the 5s analysis window
            _travelCurrentSubState.value = TravelSubState.CALIBRATING_BASE

            val verticalRatio = if (totalG > 0.1f) abs(y) / totalG else 0f
            val isClothOverScreen = currentProximityCovered || (currentLight < 0.5f && verticalRatio > 0.65f)
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
                if (travelAnalysisHadMotion) {
                    // Movement detected in first 5s -> user is holding/using phone!
                    _travelCurrentSubState.value = TravelSubState.ACTIVE_USE
                    stationarySinceTimestamp = 0L
                    travelDeskCalibrationSamples = 0
                    travelBaselineX = 0f
                    travelBaselineY = 0f
                    travelBaselineZ = 0f
                } else if (travelAnalysisHadCloth) {
                    // Phone was put into pocket/bag
                    _travelCurrentSubState.value = TravelSubState.IN_POCKET
                    travelDeskBaselineSet = false
                    pocketBaselineLight = currentLight
                    lightBaselineSet = true
                } else if (travelDeskCalibrationSamples >= 15) {
                    // Phone is at rest on a surface (even 1 lux in dark car) -> Desk Mode!
                    val mag = sqrt(travelBaselineX * travelBaselineX + travelBaselineY * travelBaselineY + travelBaselineZ * travelBaselineZ)
                    travelBaselineG = if (mag > 0.1f) mag else 9.81f
                    travelNormalX = travelBaselineX / travelBaselineG
                    travelNormalY = travelBaselineY / travelBaselineG
                    travelNormalZ = travelBaselineZ / travelBaselineG
                    travelConsecutiveVerticalSamples = 0
                    travelDeskBaselineSet = true
                    _travelCurrentSubState.value = TravelSubState.DESK_IDLE
                } else {
                    _travelCurrentSubState.value = TravelSubState.ACTIVE_USE
                    stationarySinceTimestamp = 0L
                    travelDeskCalibrationSamples = 0
                }
            }
            _sensorReading.value = _sensorReading.value.copy(motionMagnitude = accelDeviation)
            return
        }

        // Phase 2: Active Sub-State Handling
        when (_travelCurrentSubState.value) {
            TravelSubState.ACTIVE_USE, TravelSubState.CALIBRATING_BASE -> {
                _sensorReading.value = _sensorReading.value.copy(motionMagnitude = accelDeviation)

                // 1. If phone is placed in pocket / bag:
                val verticalRatio = if (totalG > 0.1f) abs(y) / totalG else 0f
                val isPutInPocket = currentProximityCovered || (currentLight < 0.5f && verticalRatio > 0.65f)

                if (isPutInPocket) {
                    _travelCurrentSubState.value = TravelSubState.IN_POCKET
                    travelDeskBaselineSet = false
                    travelDeskCalibrationSamples = 0
                    pocketBaselineLight = currentLight
                    lightBaselineSet = true
                    stationarySinceTimestamp = 0L
                    return
                }

                // 2. Continuously monitor sensor data for constant / stationary values:
                val isValuesConstant = (accelDeviation <= 0.20f)

                if (!isValuesConstant) {
                    // Phone is moving or handled: values are not constant!
                    // Reset stationary tracking — must be continuously constant for x minutes
                    stationarySinceTimestamp = 0L
                    travelDeskCalibrationSamples = 0
                    travelBaselineX = 0f
                    travelBaselineY = 0f
                    travelBaselineZ = 0f
                } else {
                    // Values are constant (stationary / at rest on surface):
                    if (stationarySinceTimestamp == 0L) {
                        stationarySinceTimestamp = now
                    }

                    // Accumulate surface normal calibration samples
                    travelBaselineX = ((travelBaselineX * travelDeskCalibrationSamples) + x) / (travelDeskCalibrationSamples + 1)
                    travelBaselineY = ((travelBaselineY * travelDeskCalibrationSamples) + y) / (travelDeskCalibrationSamples + 1)
                    travelBaselineZ = ((travelBaselineZ * travelDeskCalibrationSamples) + z) / (travelDeskCalibrationSamples + 1)
                    travelDeskCalibrationSamples++

                    val constantDurationMs = now - stationarySinceTimestamp

                    // If values stay constant for the user-configured x number of minutes:
                    if (constantDurationMs >= travelIdleThresholdMs && travelDeskCalibrationSamples >= 20) {
                        // Activate Desk Mode in Travel Mode!
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

            TravelSubState.IN_POCKET -> {
                // Strictly vertical extraction detection while inside pocket/bag (guarded 24x7)
                handlePocketDetection(x, y, z, totalG, accelDeviation)
            }

            TravelSubState.DESK_IDLE -> {
                // Strictly vertical lift with vehicle bump shock absorber (guarded 24x7)
                handleTravelDeskDetection(x, y, z)
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

        val isPocketActive = (_detectionMode == DetectionMode.POCKET) ||
            (_detectionMode == DetectionMode.TRAVEL && _travelCurrentSubState.value == TravelSubState.IN_POCKET)

        if (isPocketActive) {
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

        val isPocketActive = (_detectionMode == DetectionMode.POCKET) ||
            (_detectionMode == DetectionMode.TRAVEL && _travelCurrentSubState.value == TravelSubState.IN_POCKET)

        if (isPocketActive) {
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
        if (_detectionMode == DetectionMode.TRAVEL && _travelCurrentSubState.value != TravelSubState.IN_POCKET) {
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
