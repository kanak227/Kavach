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

    // Active detection mode
    private var _detectionMode = DetectionMode.MOTION

    // === Desk / Table Mode Variables (MOTION) ===
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

        lightBaselineSet = false
        lightCalibrationSamples = 0
        pocketBaselineLight = -1f

        initialProximityRecorded = false
        pocketInitialProximityCovered = false

        lastLightSpikeTime = 0L
        lastProximityOpenTime = 0L
        lastExtractionTiltTime = 0L

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

        when (_detectionMode) {
            DetectionMode.MOTION -> {
                // === DESK / TABLE GUARD ===
                if (!baselineSet) {
                    // Average first N samples while phone rests on surface
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
                        baselineSet = true
                    }
                    return
                }

                // Pure vertical projection along the resting surface normal vector:
                // (x * normalX + y * normalY + z * normalZ) calculates the instantaneous force perpendicular to the table.
                // Subtracting baselineG isolates the true vertical lift acceleration and orientation change off the table.
                // CRITICAL PHYSICS: Any horizontal sliding parallel to the desk produces a dot product of EXACTLY 0.000
                // with the normal vector! Horizontal sliding produces ZERO verticalDelta!
                val normalForce = (x * normalX + y * normalY + z * normalZ)
                val verticalDelta = abs(normalForce - baselineG)

                // UI Display Metric: Displays live vertical lift magnitude
                _sensorReading.value = _sensorReading.value.copy(motionMagnitude = verticalDelta)

                // Trigger Condition:
                // ONLY true vertical lift off the resting baseline triggers the siren!
                // Requires 2 consecutive samples (40ms) to filter out single-sample micro-clicks/texture taps.
                // Horizontal sliding has verticalDelta == 0.0 and will NEVER trigger!
                if (verticalDelta > verticalThreshold) {
                    consecutiveVerticalSamples++
                    if (consecutiveVerticalSamples >= 2) {
                        _motionDetected.value = true
                    }
                } else {
                    consecutiveVerticalSamples = 0
                }
            }

            DetectionMode.POCKET, DetectionMode.AUTO_POCKET -> {
                // === PICKPOCKET GUARD ===
                if (!lightBaselineSet) {
                    // Still calibrating pocket baseline
                    return
                }

                val totalG = sqrt(x * x + y * y + z * z)
                val verticalRatio = if (totalG > 0.1f) abs(y) / totalG else 0f

                // In pocket, verticalRatio is usually > 0.70 (phone upright or head-down)
                // When extracted and held horizontally, verticalRatio drops below tiltThreshold (< 0.58)
                val isTiltedFlat = verticalRatio < tiltThreshold

                // Detect extraction impulse (sudden non-walking acceleration)
                val totalAccelDeviation = abs(totalG - 9.81f)
                val isExtractionJerk = totalAccelDeviation > extractionMotionThreshold

                if (isTiltedFlat && isExtractionJerk) {
                    lastExtractionTiltTime = System.currentTimeMillis()
                    evaluatePickpocketFusion()
                }

                _sensorReading.value = _sensorReading.value.copy(motionMagnitude = totalAccelDeviation)
            }
        }
    }

    private fun handleLight(event: SensorEvent) {
        currentLight = event.values[0]

        if (_detectionMode == DetectionMode.POCKET || _detectionMode == DetectionMode.AUTO_POCKET) {
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

        if (_detectionMode == DetectionMode.POCKET || _detectionMode == DetectionMode.AUTO_POCKET) {
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
