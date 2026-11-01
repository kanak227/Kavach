package com.kavach.app.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

object SilentCameraHelper {

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Silently captures a single JPEG snapshot in the background.
     * Uses offscreen SurfaceTexture preview to ensure hardware Auto-Exposure (AE)
     * and Auto-White-Balance (AWB) lock on to room lighting before capture.
     *
     * @param context Application context
     * @param frontCamera True for front lens (intruder face), false for rear lens
     * @return JPEG byte array or null on error/timeout
     */
    suspend fun captureSnapshot(context: Context, frontCamera: Boolean = true): ByteArray? =
        withContext(Dispatchers.IO) {
            var result: ByteArray? = null
            captureBurst(context, count = 1, intervalMs = 0L, frontCamera = frontCamera) { bytes, _ ->
                result = bytes
            }
            result
        }

    /**
     * Silently captures a rapid sequence of JPEG photos in a SINGLE hardware camera session.
     * Continuously runs hardware 3A exposure pipeline via offscreen SurfaceTexture to guarantee
     * bright, clear, well-exposed photos across all Android device manufacturers (Samsung, Xiaomi,
     * OnePlus, Oppo, Vivo, Pixel, Motorola, Realme, Transsion).
     *
     * Includes dual-tier lightness correction:
     * 1. Hardware-level: Offscreen preview surface + low-light dynamic FPS range + AE compensation boost.
     * 2. Software-level: Automatic luminance evaluation with adaptive brightness curve for dim rooms.
     *
     * @param context Application context
     * @param count Number of snapshots to capture in the burst
     * @param intervalMs Delay between consecutive shutter snaps
     * @param frontCamera True for front lens, false for rear lens
     * @param onPhotoReady Callback invoked immediately as each photo is acquired
     */
    @SuppressLint("MissingPermission")
    suspend fun captureBurst(
        context: Context,
        count: Int = 5,
        intervalMs: Long = 700L,
        frontCamera: Boolean = true,
        onPhotoReady: suspend (ByteArray, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!hasCameraPermission(context)) return@withContext

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return@withContext

        // Ensure flash torch is off so camera hardware has 100% exclusive access
        try {
            for (id in cameraManager.cameraIdList) {
                cameraManager.setTorchMode(id, false)
            }
        } catch (_: Exception) {}

        val targetFacing = if (frontCamera) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        val cameraIdList = cameraManager.cameraIdList
        var selectedCameraId: String? = null
        for (id in cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == targetFacing) {
                selectedCameraId = id
                break
            }
        }
        if (selectedCameraId == null && cameraIdList.isNotEmpty()) {
            selectedCameraId = cameraIdList[0]
        }
        if (selectedCameraId == null) return@withContext

        val characteristics = cameraManager.getCameraCharacteristics(selectedCameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        val targetSize = pickOptimalSize(sizes)
        val aeRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)

        // Select the optimal low-light FPS range (prefer lowest floor like [7, 30] or [15, 30] so hardware shutter can stay open longer in dark rooms)
        val availableFpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        val lowLightFpsRange: Range<Int>? = availableFpsRanges
            ?.filter { it.upper >= 24 }
            ?.minByOrNull { it.lower }
            ?: availableFpsRanges?.firstOrNull()

        val handlerThread = HandlerThread("KavachSilentBurst").apply { start() }
        val backgroundHandler = Handler(handlerThread.looper)

        var dummyTexture: SurfaceTexture? = null
        var previewSurface: Surface? = null
        var imageReader: ImageReader? = null
        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null

        try {
            // Offscreen OpenGL texture to feed hardware ISP Auto-Exposure pipeline across all OEM drivers
            dummyTexture = SurfaceTexture(10).apply {
                setDefaultBufferSize(640, 480)
            }
            previewSurface = Surface(dummyTexture)

            imageReader = ImageReader.newInstance(
                targetSize.width,
                targetSize.height,
                ImageFormat.JPEG,
                2
            )

            val frameChannel = Channel<ByteArray>(Channel.CONFLATED)

            imageReader.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage() ?: reader.acquireNextImage() ?: return@setOnImageAvailableListener
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    frameChannel.trySend(bytes)
                } catch (_: Exception) {}
            }, backgroundHandler)

            // 1. Open Hardware Camera Device
            cameraDevice = suspendCancellableCoroutine { cont ->
                cameraManager.openCamera(selectedCameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cont.resume(camera)
                    }
                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        if (cont.isActive) cont.resume(null)
                    }
                }, backgroundHandler)
            } ?: return@withContext

            // 2. Create Dual-Surface Session (previewSurface for AE convergence + imageReader for JPEG)
            captureSession = suspendCancellableCoroutine { cont ->
                @Suppress("DEPRECATION")
                cameraDevice.createCaptureSession(
                    listOf(previewSurface, imageReader.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            cont.resume(session)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                    backgroundHandler
                )
            } ?: return@withContext

            // 3. Start Repeating Request on previewSurface to run hardware 3A Auto-Exposure
            val previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                lowLightFpsRange?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
                if (aeRange != null && aeRange.upper > 0) {
                    set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, minOf(3, aeRange.upper))
                }
            }
            captureSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler)

            // Warm-up wait: 450ms for ISP exposure and gain to lock onto room ambient light
            delay(450L)

            // 4. Capture burst snapshots with converged exposure
            val stillBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageReader.surface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                lowLightFpsRange?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
                if (aeRange != null && aeRange.upper > 0) {
                    set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, minOf(3, aeRange.upper))
                }
                set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                set(CaptureRequest.JPEG_QUALITY, 90.toByte())
            }

            for (i in 1..count) {
                // Drain any stale frame from previous iteration
                frameChannel.tryReceive()

                captureSession.capture(stillBuilder.build(), null, backgroundHandler)

                val capturedBytes = withTimeoutOrNull(2500L) {
                    frameChannel.receive()
                }

                if (capturedBytes != null && capturedBytes.isNotEmpty()) {
                    // Universal lightness correction: evaluates exposure and brightens dark frames
                    val brightenedBytes = enhanceLowLightImage(capturedBytes)
                    onPhotoReady(brightenedBytes, i)
                }

                if (i < count) {
                    delay(intervalMs)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { captureSession?.stopRepeating() } catch (_: Exception) {}
            try { captureSession?.close() } catch (_: Exception) {}
            try { cameraDevice?.close() } catch (_: Exception) {}
            try { imageReader?.close() } catch (_: Exception) {}
            try { previewSurface?.release() } catch (_: Exception) {}
            try { dummyTexture?.release() } catch (_: Exception) {}
            try { handlerThread.quitSafely() } catch (_: Exception) {}
        }
    }

    /**
     * Universal lightness correction across all Android manufacturers (Samsung, Xiaomi, Oppo, Pixel, etc.).
     * Samples image luminance across the center subject area. If the image is underexposed or in a dark room,
     * dynamically applies an adaptive gamma and contrast curve so facial evidence is clear and identifiable.
     */
    private fun enhanceLowLightImage(jpegBytes: ByteArray): ByteArray {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return jpegBytes
            val width = bitmap.width
            val height = bitmap.height

            // Sample the center region (where the intruder's face appears)
            val startX = width / 4
            val endX = (width * 3) / 4
            val startY = height / 4
            val endY = (height * 3) / 4
            val step = 16

            var totalLum = 0L
            var samples = 0

            for (x in startX until endX step step) {
                for (y in startY until endY step step) {
                    val p = bitmap.getPixel(x, y)
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    // Rec. 601 perceived luminance
                    totalLum += (r * 299 + g * 587 + b * 114) / 1000
                    samples++
                }
            }

            val avgLum = if (samples > 0) totalLum / samples else 128

            // If already well-illuminated (>= 75), keep raw original image untouched
            if (avgLum >= 75) {
                bitmap.recycle()
                return jpegBytes
            }

            // Adaptive boost factor based on ambient darkness level
            val (boostMultiplier, brightnessOffset) = when {
                avgLum < 25 -> Pair(2.4f, 25f) // Night / dark room
                avgLum < 50 -> Pair(1.8f, 18f) // Dim indoor room
                else -> Pair(1.35f, 10f)       // Slight underexposure
            }

            val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(enhancedBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val colorMatrix = ColorMatrix(floatArrayOf(
                boostMultiplier, 0f, 0f, 0f, brightnessOffset,
                0f, boostMultiplier, 0f, 0f, brightnessOffset,
                0f, 0f, boostMultiplier, 0f, brightnessOffset,
                0f, 0f, 0f, 1f, 0f
            ))

            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            val outputStream = ByteArrayOutputStream()
            enhancedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)

            bitmap.recycle()
            enhancedBitmap.recycle()

            outputStream.toByteArray()
        } catch (_: Exception) {
            jpegBytes
        }
    }

    private fun pickOptimalSize(sizes: Array<Size>): Size {
        if (sizes.isEmpty()) return Size(1280, 720)

        // Preferred resolution range: around 1280x720 (width between 960 and 1920)
        val sorted = sizes.sortedByDescending { it.width * it.height }
        val ideal = sorted.firstOrNull { it.width in 960..1920 && it.height in 720..1080 }
        if (ideal != null) return ideal

        // Fallback: pick the closest above 640x480
        val acceptable = sorted.lastOrNull { it.width >= 640 && it.height >= 480 }
        return acceptable ?: sorted.first()
    }
}
