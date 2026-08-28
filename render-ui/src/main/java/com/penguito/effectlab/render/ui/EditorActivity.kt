package com.penguito.effectlab.render.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import com.penguito.effectlab.render.sdk.PreviewResolution
import com.penguito.effectlab.render.sdk.RenderEngine
import com.penguito.effectlab.render.sdk.RenderMode

class EditorActivity : Activity(), SurfaceHolder.Callback, RenderEngine.InitListener {

    private val renderEngine by lazy { RenderEngine() }

    private lateinit var previewView: SurfaceView
    private lateinit var imageInfo: TextView
    private lateinit var imagePath: String
    private var outputSurface: Surface? = null
    private var isEditorResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sourceValue = intent.getStringExtra(EXTRA_IMAGE_SOURCE)
        val pathValue = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (sourceValue == null || pathValue == null) {
            finish()
            return
        }

        setContentView(R.layout.activity_editor)
        imagePath = pathValue
        val imageSource = ImageSource.valueOf(sourceValue)
        val sourceName = when (imageSource) {
            ImageSource.CAPTURE -> getString(R.string.editor_source_capture)
            ImageSource.ALBUM -> getString(R.string.editor_source_album)
        }

        previewView = findViewById(R.id.editor_preview)
        imageInfo = findViewById(R.id.editor_image_info)
        previewView.holder.addCallback(this)
        imageInfo.text = getString(R.string.editor_image_info, sourceName, imagePath,)
        findViewById<Button>(R.id.editor_back).setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        isEditorResumed = true
        resumeRender()
    }

    override fun onPause() {
        isEditorResumed = false
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

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int, ) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        outputSurface = null
        renderEngine.stop()
    }

    override fun onRenderReady(cameraSurface: Surface?) = Unit

    override fun onRenderError() {
        imageInfo.text = getString(R.string.editor_render_failed)
    }

    private fun resumeRender() {
        val surface = outputSurface ?: return
        if (!isEditorResumed) return

        renderEngine.init(surface, PreviewResolution.P720, RenderMode.IMAGE, imagePath, this)
    }

    companion object {
        private const val EXTRA_IMAGE_SOURCE = "image_source"
        private const val EXTRA_IMAGE_PATH = "image_path"

        fun createIntent(
            context: Context,
            imageSource: ImageSource,
            imagePath: String,
        ): Intent = Intent(context, EditorActivity::class.java)
            .putExtra(EXTRA_IMAGE_SOURCE, imageSource.name)
            .putExtra(EXTRA_IMAGE_PATH, imagePath)
    }
}
