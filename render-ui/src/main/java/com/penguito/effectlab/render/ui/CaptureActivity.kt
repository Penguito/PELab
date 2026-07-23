package com.penguito.effectlab.render.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.penguito.effectlab.render.core.camera.Camera2ConfigurationProvider
import com.penguito.effectlab.render.core.camera.CameraConfiguration
import com.penguito.effectlab.render.core.camera.CameraError
import com.penguito.effectlab.render.core.camera.CameraErrorCode
import com.penguito.effectlab.render.core.camera.LensFacing
import com.penguito.effectlab.render.core.camera.PreviewSize
import com.penguito.effectlab.render.core.permission.CameraPermissionGate

class CaptureActivity : Activity() {
    private val permissionGate by lazy { CameraPermissionGate(this) }
    private val cameraConfigurationProvider by lazy {
        Camera2ConfigurationProvider(
            context = this,
            errorListener = ::onCameraError,
        )
    }
    private var lifecycleStatus: TextView? = null
    private var cameraConfiguration: CameraConfiguration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!permissionGate.isGranted()) {
            finish()
            return
        }

        setContentView(R.layout.activity_capture)
        lifecycleStatus = findViewById(R.id.capture_status)
    }

    override fun onResume() {
        super.onResume()
        if (!permissionGate.isGranted()) {
            finish()
            return
        }
        resumeCapture()
    }

    override fun onPause() {
        pauseCapture()
        super.onPause()
    }

    private fun resumeCapture() {
        Log.d(LOG_TAG, "Capture lifecycle resumed")
        val configuration = cameraConfiguration
            ?: cameraConfigurationProvider.createConfiguration(
                lensFacing = LensFacing.BACK,
                targetPreviewSize = PreviewSize(width = 1280, height = 720),
            )?.also { cameraConfiguration = it }
        configuration?.let(::showCameraConfiguration)
    }

    private fun pauseCapture() {
        Log.d(LOG_TAG, "Capture lifecycle paused")
        lifecycleStatus?.setText(R.string.capture_paused)
    }

    private fun showCameraConfiguration(configuration: CameraConfiguration) {
        val lensFacingName = when (configuration.lensFacing) {
            LensFacing.FRONT -> getString(R.string.capture_lens_front)
            LensFacing.BACK -> getString(R.string.capture_lens_back)
        }
        lifecycleStatus?.text = getString(
            R.string.capture_camera_ready,
            configuration.cameraId,
            lensFacingName,
            configuration.previewSize.width,
            configuration.previewSize.height,
        )
    }

    private fun onCameraError(error: CameraError) {
        Log.e(
            LOG_TAG,
            "Camera configuration error: " + "code=${error.code}, cameraId=${error.cameraId}, lensFacing=${error.lensFacing}",
            null,
        )
        val messageResId = when (error.code) {
            CameraErrorCode.ACCESS_FAILED -> R.string.capture_camera_access_failed
            CameraErrorCode.PERMISSION_MISSING -> R.string.capture_camera_permission_missing
            CameraErrorCode.CAMERA_NOT_FOUND -> R.string.capture_camera_not_found
            CameraErrorCode.PREVIEW_CONFIGURATION_MISSING -> R.string.capture_preview_configuration_missing
        }
        lifecycleStatus?.setText(messageResId)
    }

    companion object {
        private const val LOG_TAG = "PELabCapture"

        fun createIntent(context: Context): Intent = Intent(context, CaptureActivity::class.java)
    }
}
