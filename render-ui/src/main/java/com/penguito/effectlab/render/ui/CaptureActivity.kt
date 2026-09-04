package com.penguito.effectlab.render.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.penguito.effectlab.render.core.camera.Camera2Listener
import com.penguito.effectlab.render.core.camera.Camera2Manager
import com.penguito.effectlab.render.core.camera.CameraConfiguration
import com.penguito.effectlab.render.core.camera.CameraError
import com.penguito.effectlab.render.core.camera.CameraErrorCode
import com.penguito.effectlab.render.core.camera.LensFacing
import com.penguito.effectlab.render.core.material.FilterMaterialManager
import com.penguito.effectlab.render.core.permission.CameraPermissionGate
import com.penguito.effectlab.render.sdk.PreviewResolution
import com.penguito.effectlab.render.sdk.RenderEngine
import com.penguito.effectlab.render.sdk.RenderMode
import java.io.File
import java.io.IOException

class CaptureActivity : FragmentActivity(), SurfaceHolder.Callback, Camera2Listener, RenderEngine.InitListener, RenderEngine.DebugInfoListener {
    private val permissionGate by lazy { CameraPermissionGate(this) }
    private val cameraManager by lazy { Camera2Manager(this, this) }
    private val filterMaterialManager by lazy { FilterMaterialManager(this) }
    private val renderEngine by lazy { RenderEngine() }

    private var previewView: SurfaceView? = null
    private var lifecycleStatus: TextView? = null
    private var debugInfo: TextView? = null
    private var filterButton: ImageButton? = null
    private var switchCameraButton: ImageButton? = null
    private var captureButton: ImageButton? = null
    private var outputSurface: Surface? = null
    private var cameraConfiguration: CameraConfiguration? = null
    private var selectedFilterId: String? = null
    private var filterIconPadding = 0
    private var isCaptureResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!permissionGate.isGranted()) {
            finish()
            return
        }

        setContentView(R.layout.activity_capture)
        lifecycleStatus = findViewById(R.id.capture_status)
        previewView = findViewById<SurfaceView>(R.id.capture_preview).also {
            it.holder.addCallback(this)
        }
        debugInfo = findViewById(R.id.capture_debug_info)
        renderEngine.setDebugInfoListener(this)
        filterButton = findViewById<ImageButton>(R.id.capture_filter_button).also {
            filterIconPadding = it.paddingLeft
        }
        findViewById<ImageButton>(R.id.capture_back).setOnClickListener { finish() }
        switchCameraButton = findViewById<ImageButton>(R.id.capture_switch_camera).also {
            it.setOnClickListener { cameraManager.switchCamera() }
        }
        captureButton = findViewById<ImageButton>(R.id.capture_photo).also {
            it.setOnClickListener { captureImage() }
        }
        setupFilterPanel()
        setupAlgorithmPanel()
    }

    override fun onResume() {
        super.onResume()
        if (!permissionGate.isGranted()) {
            finish()
            return
        }
        isCaptureResumed = true
        resumeCapture()
    }

    override fun onPause() {
        isCaptureResumed = false
        pauseCapture()
        super.onPause()
    }

    override fun onDestroy() {
        previewView?.holder?.removeCallback(this)
        cameraManager.close()
        renderEngine.close()
        super.onDestroy()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        outputSurface = holder.surface
        resumeCapture()
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        outputSurface = null
        pauseCapture()
    }

    private fun resumeCapture() {
        if (!isCaptureResumed) return
        val surface = outputSurface ?: return

        Log.d(LOG_TAG, "Capture lifecycle resumed")
        val configuration = cameraConfiguration
            ?: cameraManager.createConfiguration(
                lensFacing = LensFacing.FRONT,
                previewResolution = PreviewResolution.P720,
            )?.also {
                cameraConfiguration = it
            }
        configuration?.let {
            showCameraConfiguration(it)

            // init render  engine
            renderEngine.init(surface, it.previewResolution, RenderMode.CAMERA, this)
        }
    }

    private fun pauseCapture() {
        Log.d(LOG_TAG, "Capture lifecycle paused")
        cameraManager.stop()
        renderEngine.stop()
        // adjust buttons
        switchCameraButton?.isEnabled = false
        captureButton?.isEnabled = false
        // adjust textViews
        debugInfo?.setText(R.string.capture_debug_info_empty)
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

    private fun captureImage() {
        captureButton?.isEnabled = false
        renderEngine.captureFrame(object : RenderEngine.CaptureCallback {
            override fun onCaptureCompleted(jpegData: ByteArray) {
                val imageFile = saveCapturedImage(jpegData)
                if (imageFile == null) {
                    showCaptureError()
                    return
                }

                // jump to editor activity
                startActivity(
                    EditorActivity.createIntent(
                        context = this@CaptureActivity,
                        imageSource = ImageSource.CAPTURE,
                        imagePath = imageFile.absolutePath,
                    ),
                )
            }

            override fun onCaptureError() {
                showCaptureError()
            }
        })
    }

    private fun saveCapturedImage(jpegData: ByteArray): File? {
        val imageFile = File(cacheDir, CAPTURE_FILE_NAME)
        return try {
            imageFile.writeBytes(jpegData)
            imageFile
        } catch (error: IOException) {
            Log.e(LOG_TAG, "Capture file creation failed", error)
            null
        }
    }

    private fun showCaptureError() {
        captureButton?.isEnabled = true
        lifecycleStatus?.setText(R.string.capture_image_failed)
    }

    private fun setupFilterPanel() {
        val filterList = filterMaterialManager.initFilterList()
            .sortedBy { if (it.id == CYBER_PUNK_FILTER_ID) 0 else 1 }
        val filtersById = filterList.associateBy { it.id }
        val filterItems = filterList.map {
            SelectionPanelItem(
                id = it.id,
                name = it.displayName,
                icon = SelectionPanelIcon.FilePath(it.iconPath),
            )
        }
        filterButton?.setOnClickListener {
            SelectionPanelBottomSheet().apply {
                setOnItemSelectedListener { item ->
                    selectedFilterId = item?.id
                    val filter = item?.let { filtersById[it.id] }
                    renderEngine.setFilter(filter?.rootPath)
                    showFilterIcon(filter?.iconPath)
                }
                setItems(
                    items = filterItems,
                    emptyText = this@CaptureActivity.getString(R.string.capture_filter_empty),
                    showNoneButton = true,
                    selectedItemId = selectedFilterId,
                )
            }.show(supportFragmentManager, FILTER_PANEL_TAG)
        }
    }

    private fun showFilterIcon(iconPath: String?) {
        val button = filterButton ?: return
        if (iconPath == null) {
            button.setPadding(filterIconPadding, filterIconPadding, filterIconPadding, filterIconPadding)
            button.setImageResource(R.drawable.icon_capture_filter)
            return
        }
        button.setPadding(0, 0, 0, 0)
        button.setImageURI(Uri.fromFile(File(iconPath)))
    }

    private fun setupAlgorithmPanel() {
        findViewById<ImageButton>(R.id.capture_algorithm_button).setOnClickListener {
            SelectionPanelBottomSheet().apply {
                setItems(
                    items = emptyList(),
                    emptyText = this@CaptureActivity.getString(R.string.capture_algorithm_developing),
                )
            }.show(supportFragmentManager, ALGORITHM_PANEL_TAG)
        }
    }

    override fun onRenderReady(cameraSurface: Surface?) {
        val configuration = cameraConfiguration ?: return
        val inputSurface = cameraSurface ?: return
        if (!isCaptureResumed || outputSurface == null) return

        cameraManager.start(
            outputSurface = inputSurface,
            configuration = configuration,
        )
    }

    override fun onRenderError() {
        Log.e(LOG_TAG, "Render initialization failed")
        lifecycleStatus?.setText(R.string.capture_render_initialization_failed)
    }

    override fun onDebugInfo(
        sdkRenderMillis: Float,
        cameraFrameMillis: Float,
        framesPerSecond: Float,
    ) {
        if (!isCaptureResumed) return

        debugInfo?.text = getString(
            R.string.capture_debug_info,
            sdkRenderMillis,
            cameraFrameMillis,
            framesPerSecond,
        )
    }

    override fun onCameraStarted(configuration: CameraConfiguration) {
        if (!isCaptureResumed) return

        cameraConfiguration = configuration
        switchCameraButton?.isEnabled = true
        captureButton?.isEnabled = true
        showCameraConfiguration(configuration)
    }

    override fun onCameraStopped() {
        if (!isCaptureResumed) {
            lifecycleStatus?.setText(R.string.capture_paused)
        }
    }

    override fun onCameraError(error: CameraError) {
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
            CameraErrorCode.CAMERA_DISCONNECTED -> R.string.capture_camera_disconnected
            CameraErrorCode.CAMERA_DEVICE_FAILED -> R.string.capture_camera_device_failed
            CameraErrorCode.CAPTURE_SESSION_FAILED -> R.string.capture_camera_session_failed
        }
        lifecycleStatus?.setText(messageResId)
    }

    companion object {
        private const val LOG_TAG = "PELabCapture"
        private const val CYBER_PUNK_FILTER_ID = "cyber_punk"
        private const val CAPTURE_FILE_NAME = "captured_image.jpg"
        private const val FILTER_PANEL_TAG = "filter_panel"
        private const val ALGORITHM_PANEL_TAG = "algorithm_panel"

        fun createIntent(context: Context): Intent = Intent(context, CaptureActivity::class.java)
    }
}
