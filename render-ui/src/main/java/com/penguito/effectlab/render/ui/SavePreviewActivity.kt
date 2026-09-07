package com.penguito.effectlab.render.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.penguito.effectlab.render.core.material.ImageExportManager
import com.penguito.effectlab.render.core.permission.AlbumWritePermissionGate
import com.penguito.effectlab.render.core.permission.PermissionResult
import com.penguito.effectlab.render.sdk.PreviewResolution
import com.penguito.effectlab.render.sdk.RenderEngine
import com.penguito.effectlab.render.sdk.RenderMode

class SavePreviewActivity : Activity(), SurfaceHolder.Callback, RenderEngine.InitListener {

    private val renderEngine by lazy { RenderEngine() }
    private val imageExportManager by lazy { ImageExportManager(this) }
    private val albumPermissionGate by lazy { AlbumWritePermissionGate(this) }

    private lateinit var previewView: SurfaceView
    private lateinit var statusView: TextView
    private lateinit var saveButton: Button
    private lateinit var imageIntentData: ImageIntentData
    private var outputSurface: Surface? = null
    private var isPreviewResumed = false
    private var isRenderReady = false
    private var saveAfterRenderReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentData = ImageIntentData.fromIntent(intent)
        if (intentData == null) {
            finish()
            return
        }

        setContentView(R.layout.activity_save_preview)
        imageIntentData = intentData
        previewView = findViewById<SurfaceView>(R.id.save_preview_surface).also {
            it.holder.addCallback(this)
        }
        statusView = findViewById(R.id.save_preview_status)
        saveButton = findViewById(R.id.save_preview_save)
        findViewById<View>(R.id.save_preview_back).setOnClickListener { finish() }
        saveButton.setOnClickListener { saveImage() }
    }

    override fun onResume() {
        super.onResume()
        isPreviewResumed = true
        resumeRender()
    }

    override fun onPause() {
        isPreviewResumed = false
        isRenderReady = false
        saveButton.isEnabled = false
        renderEngine.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (::previewView.isInitialized) {
            previewView.holder.removeCallback(this)
        }
        renderEngine.close()
        super.onDestroy()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        outputSurface = holder.surface
        resumeRender()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        outputSurface = null
        isRenderReady = false
        saveButton.isEnabled = false
        renderEngine.stop()
    }

    override fun onRenderReady(cameraSurface: Surface?) {
        isRenderReady = true
        saveButton.isEnabled = true
        if (saveAfterRenderReady) {
            saveAfterRenderReady = false
            exportRenderResult()
        }
    }

    override fun onRenderError() {
        saveAfterRenderReady = false
        isRenderReady = false
        saveButton.isEnabled = false
        statusView.setText(R.string.save_preview_render_failed)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (albumPermissionGate.resolveRequestResult(requestCode, permissions, grantResults)) {
            PermissionResult.GRANTED -> {
                if (isRenderReady) {
                    exportRenderResult()
                } else {
                    saveAfterRenderReady = true
                }
            }

            PermissionResult.DENIED -> showMessage(R.string.save_preview_album_permission_denied)

            null -> Unit
        }
    }

    private fun resumeRender() {
        val surface = outputSurface ?: return
        if (!isPreviewResumed) return

        renderEngine.init(surface, PreviewResolution.P720, RenderMode.IMAGE, imageIntentData.imagePath, this)
    }

    private fun saveImage() {
        if (albumPermissionGate.isGranted()) {
            exportRenderResult()
        } else {
            albumPermissionGate.request()
        }
    }

    private fun exportRenderResult() {
        if (!isRenderReady) return

        saveButton.isEnabled = false
        renderEngine.captureFrame(object : RenderEngine.CaptureCallback {
            override fun onCaptureCompleted(jpegData: ByteArray) {
                imageExportManager.exportImage(jpegData) { saved ->
                    saveButton.isEnabled = isRenderReady
                    showMessage(
                        if (saved) R.string.save_preview_image_saved else R.string.save_preview_image_save_failed,
                    )
                }
            }

            override fun onCaptureError() {
                saveButton.isEnabled = isRenderReady
                showMessage(R.string.save_preview_image_save_failed)
            }
        })
    }

    private fun showMessage(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show()
    }

    companion object {
        fun createIntent(
            context: Context,
            imageSource: ImageSource,
            imagePath: String,
        ): Intent = ImageIntentData(imageSource, imagePath)
            .writeTo(Intent(context, SavePreviewActivity::class.java))
    }
}
