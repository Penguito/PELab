package com.penguito.effectlab.render.core.camera

import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.penguito.effectlab.render.sdk.PreviewResolution
import java.io.Closeable
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Opens a Camera2 stream based on the configuration and writes frames to the Surface.
 */
class Camera2Manager(
    context: Context,
    private val listener: Camera2Listener,
) : Closeable {

    private val cameraManager = context.applicationContext.getSystemService(CameraManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val configurationProvider = Camera2ConfigurationProvider(context) { error ->
        mainHandler.post {
            listener.onCameraError(error)
        }
    }
    private val cameraThread = HandlerThread("PELab-Camera").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var outputSurface: Surface? = null
    private var cameraConfiguration: CameraConfiguration? = null
    private var cameraCallback: CameraDevice.StateCallback? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var zoomRatio = MIN_ZOOM_RATIO
    private var exposureCompensation = DEFAULT_EXPOSURE_COMPENSATION
    private var isClosed = false

    fun createConfiguration(
        lensFacing: LensFacing,
        previewResolution: PreviewResolution = PreviewResolution.P720,
    ): CameraConfiguration? {
        return configurationProvider.createConfiguration(
            lensFacing = lensFacing,
            previewResolution = previewResolution,
        )
    }

    fun start(
        outputSurface: Surface,
        configuration: CameraConfiguration,
    ) {
        cameraHandler.post {
            this.outputSurface = outputSurface
            cameraConfiguration = configuration
            releaseCamera()
            openCamera(
                outputSurface = outputSurface,
                configuration = configuration,
            )
        }
    }

    fun switchCamera() {
        cameraHandler.post {
            val surface = outputSurface ?: return@post
            val configuration = cameraConfiguration ?: return@post
            val lensFacing = when (configuration.lensFacing) {
                LensFacing.FRONT -> LensFacing.BACK
                LensFacing.BACK -> LensFacing.FRONT
            }
            val switchedConfiguration = configurationProvider.createConfiguration(
                lensFacing = lensFacing,
                previewResolution = configuration.previewResolution,
            ) ?: return@post

            resetControls()
            releaseCamera()
            cameraConfiguration = switchedConfiguration
            openCamera(
                outputSurface = surface,
                configuration = switchedConfiguration,
            )
        }
    }

    fun setZoomRatio(zoomRatio: Float) {
        if (!zoomRatio.isFinite()) return

        cameraHandler.post {
            this.zoomRatio = zoomRatio.coerceIn(MIN_ZOOM_RATIO, MAX_ZOOM_RATIO)
            updatePreviewRequest()
        }
    }

    fun focusAt(
        normalizedX: Float,
        normalizedY: Float,
    ) {
        if (!normalizedX.isFinite() || !normalizedY.isFinite()) return

        cameraHandler.post {
            startFocus(
                normalizedX = normalizedX.coerceIn(0F, 1F),
                normalizedY = normalizedY.coerceIn(0F, 1F),
            )
        }
    }

    fun setExposureCompensation(exposureCompensation: Float) {
        if (!exposureCompensation.isFinite()) return

        cameraHandler.post {
            this.exposureCompensation = exposureCompensation.coerceIn(
                MIN_EXPOSURE_COMPENSATION,
                MAX_EXPOSURE_COMPENSATION,
            )
            updatePreviewRequest()
        }
    }

    fun stop() {
        if (isClosed) return
        cameraHandler.post(::releaseCamera)
    }

    private fun openCamera(
        outputSurface: Surface,
        configuration: CameraConfiguration,
    ) {
        val callback = createCameraCallback(
            outputSurface = outputSurface,
            configuration = configuration,
        )
        cameraCallback = callback

        try {
            cameraManager.openCamera(
                configuration.cameraId,
                callback,
                cameraHandler,
            )
        } catch (error: CameraAccessException) {
            reportError(
                code = CameraErrorCode.ACCESS_FAILED,
                configuration = configuration,
                cause = error,
            )
            releaseCamera()
        } catch (error: SecurityException) {
            reportError(
                code = CameraErrorCode.PERMISSION_MISSING,
                configuration = configuration,
                cause = error,
            )
            releaseCamera()
        } catch (error: IllegalArgumentException) {
            reportError(
                code = CameraErrorCode.CAMERA_NOT_FOUND,
                configuration = configuration,
                cause = error,
            )
            releaseCamera()
        }
    }

    private fun createCameraCallback(
        outputSurface: Surface,
        configuration: CameraConfiguration,
    ): CameraDevice.StateCallback {
        return object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                if (cameraCallback !== this) {
                    camera.close()
                    return
                }

                cameraDevice = camera
                createPreviewSession(
                    cameraCallback = this,
                    camera = camera,
                    outputSurface = outputSurface,
                    configuration = configuration,
                )
            }

            override fun onDisconnected(camera: CameraDevice) {
                if (cameraCallback !== this) {
                    camera.close()
                    return
                }

                camera.close()
                cameraDevice = null
                reportError(
                    code = CameraErrorCode.CAMERA_DISCONNECTED,
                    configuration = configuration,
                )
                releaseCamera()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                if (cameraCallback !== this) {
                    camera.close()
                    return
                }

                Log.e("PELabCamera", "Camera device error: $error")
                camera.close()
                cameraDevice = null
                reportError(
                    code = CameraErrorCode.CAMERA_DEVICE_FAILED,
                    configuration = configuration,
                )
                releaseCamera()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun createPreviewSession(
        cameraCallback: CameraDevice.StateCallback,
        camera: CameraDevice,
        outputSurface: Surface,
        configuration: CameraConfiguration,
    ) {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(configuration.cameraId)
            val previewRequestBuilder = camera
                .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply {
                    addTarget(outputSurface)
                    applyPreviewControls(this, characteristics)
                }

            camera.createCaptureSession(
                listOf(outputSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (this@Camera2Manager.cameraCallback !== cameraCallback) {
                            session.close()
                            return
                        }

                        captureSession = session
                        this@Camera2Manager.previewRequestBuilder = previewRequestBuilder
                        cameraCharacteristics = characteristics
                        applyPreviewControls(previewRequestBuilder, characteristics)
                        startPreviewRequest(
                            session = session,
                            previewRequest = previewRequestBuilder.build(),
                            configuration = configuration,
                        )
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        if (this@Camera2Manager.cameraCallback !== cameraCallback) return

                        reportError(
                            code = CameraErrorCode.CAPTURE_SESSION_FAILED,
                            configuration = configuration,
                        )
                        releaseCamera()
                    }
                },
                cameraHandler,
            )
        } catch (error: CameraAccessException) {
            reportError(
                code = CameraErrorCode.ACCESS_FAILED,
                configuration = configuration,
                cause = error,
            )
            releaseCamera()
        } catch (error: IllegalArgumentException) {
            reportError(
                code = CameraErrorCode.CAPTURE_SESSION_FAILED,
                configuration = configuration,
                cause = error,
            )
            releaseCamera()
        }
    }

    private fun updatePreviewRequest() {
        val session = captureSession ?: return
        val requestBuilder = previewRequestBuilder ?: return
        val characteristics = cameraCharacteristics ?: return
        val configuration = cameraConfiguration ?: return

        applyPreviewControls(requestBuilder, characteristics)
        try {
            session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
        } catch (error: CameraAccessException) {
            reportError(
                code = CameraErrorCode.ACCESS_FAILED,
                configuration = configuration,
                cause = error,
            )
            releaseCamera()
        } catch (error: IllegalStateException) {
            reportError(
                code = CameraErrorCode.CAPTURE_SESSION_FAILED,
                configuration = configuration,
                cause = error,
            )
            releaseCamera()
        }
    }

    private fun applyPreviewControls(
        requestBuilder: CaptureRequest.Builder,
        characteristics: CameraCharacteristics,
    ) {
        applyZoom(requestBuilder, characteristics)
        applyExposureCompensation(requestBuilder, characteristics)
    }

    private fun applyZoom(
        requestBuilder: CaptureRequest.Builder,
        characteristics: CameraCharacteristics,
    ) {
        val activeArray = characteristics[CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE] ?: return
        val maximumDigitalZoom = characteristics[
            CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM
        ] ?: MIN_ZOOM_RATIO
        val appliedZoomRatio = zoomRatio.coerceAtMost(maximumDigitalZoom)
        requestBuilder.set(
            CaptureRequest.SCALER_CROP_REGION,
            createCropRegion(activeArray, appliedZoomRatio),
        )
    }

    private fun applyExposureCompensation(
        requestBuilder: CaptureRequest.Builder,
        characteristics: CameraCharacteristics,
    ) {
        val range = characteristics[
            CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE
        ] ?: return
        val compensation = if (exposureCompensation >= DEFAULT_EXPOSURE_COMPENSATION) {
            (range.upper * exposureCompensation).roundToInt()
        } else {
            (range.lower * exposureCompensation.absoluteValue).roundToInt()
        }
        requestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, compensation)
    }

    private fun startFocus(
        normalizedX: Float,
        normalizedY: Float,
    ) {
        val session = captureSession ?: return
        val requestBuilder = previewRequestBuilder ?: return
        val characteristics = cameraCharacteristics ?: return
        val configuration = cameraConfiguration ?: return

        // create a metering region inside the current zoom crop
        val activeArray = characteristics[CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE] ?: return
        val cropRegion = requestBuilder.get(CaptureRequest.SCALER_CROP_REGION) ?: activeArray
        val sensorPoint = toSensorCoordinates(
            normalizedX = normalizedX,
            normalizedY = normalizedY,
            sensorOrientation = characteristics[CameraCharacteristics.SENSOR_ORIENTATION] ?: 0,
            lensFacing = configuration.lensFacing,
        )
        val meteringRegion = createMeteringRegion(
            cropRegion = cropRegion,
            normalizedX = sensorPoint.first,
            normalizedY = sensorPoint.second,
        )

        // check the focus and exposure regions supported by the current camera
        val supportsAutoFocus = (characteristics[CameraCharacteristics.CONTROL_MAX_REGIONS_AF] ?: 0) > 0
        val supportsAutoExposure = (characteristics[CameraCharacteristics.CONTROL_MAX_REGIONS_AE] ?: 0) > 0
        if (!supportsAutoFocus && !supportsAutoExposure) return

        // apply the metering region to autofocus and auto exposure
        if (supportsAutoFocus) {
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            requestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRegion))
        }
        if (supportsAutoExposure) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRegion))
        }

        try {
            // cancel the previous focus and trigger a new autofocus request
            if (supportsAutoFocus) {
                requestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL,
                )
                val cancelRequest = requestBuilder.build()
                requestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START,
                )
                val focusRequest = requestBuilder.build()
                requestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE,
                )
                session.capture(cancelRequest, null, cameraHandler)
                session.capture(focusRequest, null, cameraHandler)
            }

            // keep preview running with the new focus and exposure state
            session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
        } catch (error: CameraAccessException) {
            reportError(
                code = CameraErrorCode.ACCESS_FAILED,
                configuration = configuration,
                cause = error,
            )
            releaseCamera()
        } catch (error: IllegalStateException) {
            reportError(
                code = CameraErrorCode.CAPTURE_SESSION_FAILED,
                configuration = configuration,
                cause = error,
            )
            releaseCamera()
        }
    }

    private fun createCropRegion(
        activeArray: Rect,
        zoomRatio: Float,
    ): Rect {
        val cropWidth = (activeArray.width() / zoomRatio).roundToInt()
        val cropHeight = (activeArray.height() / zoomRatio).roundToInt()
        val left = activeArray.left + (activeArray.width() - cropWidth) / 2
        val top = activeArray.top + (activeArray.height() - cropHeight) / 2
        return Rect(
            left,
            top,
            left + cropWidth,
            top + cropHeight,
        )
    }

    private fun toSensorCoordinates(
        normalizedX: Float,
        normalizedY: Float,
        sensorOrientation: Int,
        lensFacing: LensFacing,
    ): Pair<Float, Float> {
        val previewX = if (lensFacing == LensFacing.FRONT) 1F - normalizedX else normalizedX
        val rotation = if (lensFacing == LensFacing.FRONT) {
            (360 - sensorOrientation) % 360
        } else {
            sensorOrientation
        }
        return when (rotation) {
            90 -> normalizedY to 1F - previewX
            180 -> 1F - previewX to 1F - normalizedY
            270 -> 1F - normalizedY to previewX
            else -> previewX to normalizedY
        }
    }

    private fun createMeteringRegion(
        cropRegion: Rect,
        normalizedX: Float,
        normalizedY: Float,
    ): MeteringRectangle {
        val regionSize = (minOf(cropRegion.width(), cropRegion.height()) * FOCUS_REGION_RATIO)
            .roundToInt()
            .coerceAtLeast(1)
        val centerX = cropRegion.left + (cropRegion.width() * normalizedX).roundToInt()
        val centerY = cropRegion.top + (cropRegion.height() * normalizedY).roundToInt()
        val left = (centerX - regionSize / 2).coerceIn(
            cropRegion.left,
            cropRegion.right - regionSize,
        )
        val top = (centerY - regionSize / 2).coerceIn(
            cropRegion.top,
            cropRegion.bottom - regionSize,
        )
        return MeteringRectangle(
            left,
            top,
            regionSize,
            regionSize,
            MeteringRectangle.METERING_WEIGHT_MAX,
        )
    }

    private fun resetControls() {
        zoomRatio = MIN_ZOOM_RATIO
        exposureCompensation = DEFAULT_EXPOSURE_COMPENSATION
    }

    // write continuous frames
    private fun startPreviewRequest(
        session: CameraCaptureSession,
        previewRequest: CaptureRequest,
        configuration: CameraConfiguration,
    ) {
        try {
            session.setRepeatingRequest(previewRequest, null, cameraHandler)
            mainHandler.post {
                listener.onCameraStarted(configuration)
            }
        } catch (error: CameraAccessException) {
            reportError(
                code = CameraErrorCode.ACCESS_FAILED,
                configuration = configuration,
                cause = error,
            )
            releaseCamera()
        } catch (error: IllegalStateException) {
            reportError(
                code = CameraErrorCode.CAPTURE_SESSION_FAILED,
                configuration = configuration,
                cause = error,
            )
            releaseCamera()
        }
    }

    private fun releaseCamera() {
        val shouldNotifyStopped = cameraCallback != null
        cameraCallback = null

        captureSession?.close()
        captureSession = null
        previewRequestBuilder = null
        cameraCharacteristics = null

        cameraDevice?.close()
        cameraDevice = null

        if (shouldNotifyStopped) {
            mainHandler.post(listener::onCameraStopped)
        }
    }

    private fun reportError(
        code: CameraErrorCode,
        configuration: CameraConfiguration,
        cause: Throwable? = null,
    ) {
        val error = CameraError(
            code = code,
            cameraId = configuration.cameraId,
            lensFacing = configuration.lensFacing,
            cause = cause,
        )
        mainHandler.post {
            listener.onCameraError(error)
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        cameraHandler.post {
            releaseCamera()
            outputSurface = null
            cameraConfiguration = null
            cameraThread.quitSafely()
        }
    }

    companion object {
        const val MIN_ZOOM_RATIO = 1F
        const val MAX_ZOOM_RATIO = 3F
        const val MIN_EXPOSURE_COMPENSATION = -1F
        const val MAX_EXPOSURE_COMPENSATION = 1F

        private const val DEFAULT_EXPOSURE_COMPENSATION = 0F
        private const val FOCUS_REGION_RATIO = 0.15F
    }
}
