package com.penguito.effectlab.render.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.penguito.effectlab.render.core.material.FilterMaterial
import com.penguito.effectlab.render.core.material.ImageEditMaterial
import com.penguito.effectlab.render.core.material.MaterialConfig
import com.penguito.effectlab.render.core.material.MaterialManager
import com.penguito.effectlab.render.core.material.MaterialType
import com.penguito.effectlab.render.sdk.PreviewResolution
import com.penguito.effectlab.render.sdk.RenderEngine
import com.penguito.effectlab.render.sdk.RenderMode
import java.io.File
import java.io.IOException

class EditorActivity : FragmentActivity(), SurfaceHolder.Callback, RenderEngine.InitListener {

    private val renderEngine by lazy { RenderEngine() }
    private val materialManager by lazy { MaterialManager(this) }

    private lateinit var previewView: SurfaceView
    private lateinit var statusView: TextView
    private lateinit var nextButton: Button
    private lateinit var imageIntentData: ImageIntentData
    private var outputSurface: Surface? = null
    private val materialValues = mutableMapOf<String, Int>()
    private var selectedFilterId: String? = null
    private var selectedFilterRootPath: String? = null
    private var isEditorResumed = false
    private var isRenderReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentData = ImageIntentData.fromIntent(intent)
        if (intentData == null) {
            finish()
            return
        }

        setContentView(R.layout.activity_editor)
        imageIntentData = intentData
        previewView = findViewById<SurfaceView>(R.id.editor_preview).also {
            it.holder.addCallback(this)
        }
        statusView = findViewById(R.id.editor_status)
        nextButton = findViewById(R.id.editor_next)
        findViewById<View>(R.id.editor_back).setOnClickListener { finish() }
        nextButton.setOnClickListener { openSavePreview() }
        setupEditorActions()
    }

    override fun onResume() {
        super.onResume()
        isEditorResumed = true
        resumeRender()
    }

    override fun onPause() {
        isEditorResumed = false
        isRenderReady = false
        nextButton.isEnabled = false
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
        nextButton.isEnabled = false
        renderEngine.stop()
    }

    override fun onRenderReady(cameraSurface: Surface?) {
        isRenderReady = true
        nextButton.isEnabled = true
    }

    override fun onRenderError() {
        isRenderReady = false
        nextButton.isEnabled = false
        statusView.setText(R.string.editor_render_failed)
    }

    private fun resumeRender() {
        val surface = outputSurface ?: return
        if (!isEditorResumed) return

        renderEngine.init(surface, PreviewResolution.P720, RenderMode.IMAGE, imageIntentData.imagePath, this)
    }

    private fun setupEditorActions() {
        findViewById<View>(R.id.editor_crop).setOnClickListener {
            showEditPanel(getString(R.string.editor_crop))
        }
        findViewById<View>(R.id.editor_lighting).setOnClickListener {
            showEditPanelWithSeekBar(
                getString(R.string.editor_lighting),
                MaterialConfig.EDIT_LIGHTING_LIST,
            )
        }
        findViewById<View>(R.id.editor_color).setOnClickListener {
            showEditPanelWithSeekBar(
                getString(R.string.editor_color),
                MaterialConfig.EDIT_COLOR_LIST,
            )
        }
        findViewById<View>(R.id.editor_detail).setOnClickListener {
            showEditPanelWithSeekBar(
                getString(R.string.editor_detail),
                MaterialConfig.EDIT_DETAIL_LIST,
            )
        }
        val filterVisibility = if (imageIntentData.imageSource == ImageSource.ALBUM) View.VISIBLE else View.GONE
        findViewById<View>(R.id.editor_filter).apply {
            visibility = filterVisibility
            setOnClickListener { showFilterPanel() }
        }
        findViewById<View>(R.id.editor_filter_name).visibility = filterVisibility
    }

    private fun showEditPanel(panelName: String) {
        SelectionPanelBottomSheet().apply {
            setHeaderVisible(false)
            setPanelName(panelName)
            setItems(
                items = emptyList(),
                emptyText = this@EditorActivity.getString(R.string.editor_feature_developing),
            )
        }.show(supportFragmentManager, SelectionPanelBottomSheet::class.java.simpleName)
    }

    private fun showEditPanelWithSeekBar(panelName: String, materialListPath: String) {
        val materials = materialManager.loadMaterialList(materialListPath, MaterialType.IMAGE_EDIT)
            .filterIsInstance<ImageEditMaterial>()
        var selectedMaterial = materials.first()
        materials.forEach {
            materialValues.putIfAbsent(materialKey(materialListPath, it), it.defaultValue)
        }
        val bottomSheet = SelectionPanelBottomSheet().apply {
            setHeaderVisible(false)
            setCompareVisible(true)
            setPanelName(panelName)
            setItems(
                items = materials.map { createSelectionItem(materialListPath, it) },
                emptyText = "",
                selectedItemId = selectedMaterial.id,
            )
            setValueRange(
                minimum = selectedMaterial.minimum,
                maximum = selectedMaterial.maximum,
                initialValue = materialValue(materialListPath, selectedMaterial),
            )
            setOnValueChangedListener {
                materialValues[materialKey(materialListPath, selectedMaterial)] = it
            }
        }
        bottomSheet.setOnItemSelectedListener { item ->
            selectedMaterial = materials.first { it.id == item?.id }
            bottomSheet.setValueRange(
                selectedMaterial.minimum,
                selectedMaterial.maximum,
                materialValue(materialListPath, selectedMaterial),
            )
        }
        bottomSheet.show(supportFragmentManager, SelectionPanelBottomSheet::class.java.simpleName)
    }

    private fun createSelectionItem(
        materialListPath: String,
        material: ImageEditMaterial,
    ): SelectionPanelItem {
        return SelectionPanelItem(
            id = material.id,
            name = material.displayName,
            icon = SelectionPanelIcon.Resource(material.iconResourceId),
            value = materialValue(materialListPath, material),
            defaultValue = material.defaultValue,
        )
    }

    private fun showFilterPanel() {
        val filterList = materialManager.loadMaterialList(MaterialConfig.FILTER_LIST, MaterialType.FILTER)
            .filterIsInstance<FilterMaterial>()
        val filtersById = filterList.associateBy { it.id }
        val filterItems = filterList.map {
            SelectionPanelItem(
                id = it.id,
                name = it.displayName,
                icon = SelectionPanelIcon.FilePath(it.iconPath),
            )
        }
        SelectionPanelBottomSheet().apply {
            setCompareVisible(true)
            setPanelName(this@EditorActivity.getString(R.string.editor_filter))
            setOnCompareListener(
                onStarted = { renderEngine.setFilter(null) },
                onStopped = { renderEngine.setFilter(selectedFilterRootPath) },
            )
            setOnItemSelectedListener { item ->
                selectedFilterId = item?.id
                selectedFilterRootPath = item?.let { filtersById[it.id]?.rootPath }
                renderEngine.setFilter(selectedFilterRootPath)
            }
            setItems(
                items = filterItems,
                emptyText = this@EditorActivity.getString(R.string.capture_filter_empty),
                showNoneButton = true,
                selectedItemId = selectedFilterId,
            )
        }.show(supportFragmentManager, SelectionPanelBottomSheet::class.java.simpleName)
    }

    private fun materialKey(materialListPath: String, material: ImageEditMaterial): String {
        return "$materialListPath/${material.id}"
    }

    private fun materialValue(materialListPath: String, material: ImageEditMaterial): Int {
        return materialValues[materialKey(materialListPath, material)] ?: material.defaultValue
    }

    private fun openSavePreview() {
        if (!isRenderReady) return

        nextButton.isEnabled = false
        renderEngine.captureFrame(object : RenderEngine.CaptureCallback {
            override fun onCaptureCompleted(jpegData: ByteArray) {
                val imageFile = savePreviewImage(jpegData)
                nextButton.isEnabled = isRenderReady
                if (imageFile == null) {
                    statusView.setText(R.string.editor_preview_creation_failed)
                    return
                }
                startActivity(
                    SavePreviewActivity.createIntent(
                        context = this@EditorActivity,
                        imageSource = imageIntentData.imageSource,
                        imagePath = imageFile.absolutePath,
                    ),
                )
            }

            override fun onCaptureError() {
                nextButton.isEnabled = isRenderReady
                statusView.setText(R.string.editor_preview_creation_failed)
            }
        })
    }

    private fun savePreviewImage(jpegData: ByteArray): File? {
        return try {
            val imageFile = File.createTempFile(javaClass.simpleName, null, cacheDir)
            imageFile.writeBytes(jpegData)
            imageFile
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            imageSource: ImageSource,
            imagePath: String,
        ): Intent = ImageIntentData(imageSource, imagePath)
            .writeTo(Intent(context, EditorActivity::class.java))
    }
}
