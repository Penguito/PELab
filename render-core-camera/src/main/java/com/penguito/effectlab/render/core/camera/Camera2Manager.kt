package com.penguito.effectlab.render.core.camera

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.io.Closeable

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
    private var isClosed = false

    fun createConfiguration(
        lensFacing: LensFacing,
        targetPreviewSize: PreviewSize,
    ): CameraConfiguration? {
        return configurationProvider.createConfiguration(
            lensFacing = lensFacing,
            targetPreviewSize = targetPreviewSize,
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
                targetPreviewSize = configuration.previewSize,
            ) ?: return@post

            releaseCamera()
            cameraConfiguration = switchedConfiguration
            openCamera(
                outputSurface = surface,
                configuration = switchedConfiguration,
            )
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
            val previewRequest = camera
                .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply { addTarget(outputSurface) }
                .build()

            camera.createCaptureSession(
                listOf(outputSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (this@Camera2Manager.cameraCallback !== cameraCallback) {
                            session.close()
                            return
                        }

                        captureSession = session
                        startPreviewRequest(
                            session = session,
                            previewRequest = previewRequest,
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
}
