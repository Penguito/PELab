package com.penguito.effectlab.render.ui.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
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
import com.penguito.effectlab.render.sdk.ImageParams
import com.penguito.effectlab.render.sdk.PreviewResolution
import com.penguito.effectlab.render.sdk.RenderEngine
import com.penguito.effectlab.render.sdk.RenderMode
import com.penguito.effectlab.render.ui.R
import com.penguito.effectlab.render.ui.panel.SelectionPanelBottomSheet
import com.penguito.effectlab.render.ui.panel.SelectionPanelIcon
import com.penguito.effectlab.render.ui.panel.SelectionPanelItem
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

class EditorActivity : FragmentActivity(), SurfaceHolder.Callback, RenderEngine.InitListener {

    private val renderEngine by lazy { RenderEngine() }
    private val materialManager by lazy { MaterialManager(this) }

    private lateinit var previewView: SurfaceView
    private lateinit var cropOverlayView: CropOverlayView
    private lateinit var statusView: TextView
    private lateinit var nextButton: Button
    private lateinit var imageIntentData: ImageIntentData
    private var outputSurface: Surface? = null
    private val materialValues = mutableMapOf<String, Int>()
    private val selectedMaterialIds = mutableMapOf<String, String>()
    private var imageParams = ImageParams.defaults()
    private var selectedFilterId: String? = null
    private var selectedFilterRootPath: String? = null
    private val originRatio by lazy {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageIntentData.imagePath, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth.toFloat() / options.outHeight
        } else {
            0F
        }
    }
    private var cropRatio = CropRatio.ORIGINAL
    private var displayRect = RectF()
    private var cropRect = RectF()
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
        cropOverlayView = findViewById<CropOverlayView>(R.id.editor_crop_overlay).also {
            it.setOnCropRectChangedListener { rect -> cropRect = rect }
        }
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

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        updateCropRects(width, height)
    }

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

        // restore editor adjustments before rebuilding the renderer
        renderEngine.setRenderParams(imageParams)
        renderEngine.setFilter(selectedFilterRootPath)
        renderEngine.init(surface, PreviewResolution.P720, RenderMode.IMAGE, imageIntentData.imagePath, this)
    }

    private fun setupEditorActions() {
        findViewById<View>(R.id.editor_crop).setOnClickListener {
            showCropPanel()
        }
        findViewById<View>(R.id.editor_lighting).setOnClickListener {
            showImageEditPanel(
                getString(R.string.editor_lighting),
                MaterialConfig.EDIT_LIGHTING_LIST,
            )
        }
        findViewById<View>(R.id.editor_color).setOnClickListener {
            showImageEditPanel(
                getString(R.string.editor_color),
                MaterialConfig.EDIT_COLOR_LIST,
            )
        }
        findViewById<View>(R.id.editor_detail).setOnClickListener {
            showImageEditPanel(
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

    private fun showCropPanel() {
        cropOverlayView.visibility = View.VISIBLE
        cropOverlayView.setEditing(true)
        cropOverlayView.setCropRects(displayRect, cropRect)
        SelectionPanelBottomSheet().apply {
            setHeaderVisible(false)
            setPanelName(this@EditorActivity.getString(R.string.editor_crop))
            setOnItemSelectedListener { item ->
                cropRatio = CropRatio.entries.firstOrNull { it.name == item?.id }
                    ?: return@setOnItemSelectedListener
                cropRect = cropRatio.createFrame(displayRect)
                cropOverlayView.setCropRects(displayRect, cropRect)
            }
            setItems(
                items = CropRatio.entries.map {
                    SelectionPanelItem(
                        id = it.name,
                        name = this@EditorActivity.getString(it.labelResId),
                        icon = SelectionPanelIcon.Resource(it.iconResId),
                    )
                },
                emptyText = "",
                selectedItemId = cropRatio.name,
            )
        }.show(supportFragmentManager, SelectionPanelBottomSheet::class.java.simpleName)
    }

    private fun updateCropRects(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || originRatio <= 0F) return

        val previewRect = RectF(0F, 0F, width.toFloat(), height.toFloat())
        val newDisplayRect = CropRatio.fitFrame(previewRect, originRatio)
        cropRect = if (displayRect.isEmpty || cropRect.isEmpty) {
            cropRatio.createFrame(newDisplayRect)
        } else {
            mapCropRectToDisplay(displayRect, newDisplayRect)
        }
        displayRect = newDisplayRect
        cropOverlayView.setCropRects(displayRect, cropRect)
    }

    private fun mapCropRectToDisplay(previousDisplayRect: RectF, newDisplayRect: RectF): RectF {
        val horizontalScale = newDisplayRect.width() / previousDisplayRect.width()
        val verticalScale = newDisplayRect.height() / previousDisplayRect.height()
        return RectF(
            newDisplayRect.left + (cropRect.left - previousDisplayRect.left) * horizontalScale,
            newDisplayRect.top + (cropRect.top - previousDisplayRect.top) * verticalScale,
            newDisplayRect.left + (cropRect.right - previousDisplayRect.left) * horizontalScale,
            newDisplayRect.top + (cropRect.bottom - previousDisplayRect.top) * verticalScale,
        )
    }

    private fun showImageEditPanel(panelName: String, materialListPath: String) {
        val materials = materialManager.loadMaterialList(materialListPath, MaterialType.IMAGE_EDIT)
            .filterIsInstance<ImageEditMaterial>()
        if (materials.isEmpty()) return
        cropOverlayView.setEditing(false)
        var selectedMaterial = materials.firstOrNull { it.id == selectedMaterialIds[materialListPath] }
            ?: materials.first()
        selectedMaterialIds[materialListPath] = selectedMaterial.id
        materials.forEach {
            materialValues.putIfAbsent(materialKey(materialListPath, it), it.defaultValue)
        }
        val bottomSheet = SelectionPanelBottomSheet().apply {
            setHeaderVisible(false)
            setCompareVisible(true)
            setPanelName(panelName)
            setOnCompareListener(
                onStarted = {
                    // compare only this panel without changing stored values
                    val defaultParams = materials.fold(imageParams) { params, material ->
                        params.withMaterialValue(material, material.defaultValue)
                    }
                    renderEngine.setRenderParams(defaultParams)
                },
                onStopped = { renderEngine.setRenderParams(imageParams) },
            )
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
                imageParams = imageParams.withMaterialValue(selectedMaterial, it)
                renderEngine.setRenderParams(imageParams)
            }
        }
        bottomSheet.setOnItemSelectedListener { item ->
            selectedMaterial = materials.firstOrNull { it.id == item?.id }
                ?: return@setOnItemSelectedListener
            selectedMaterialIds[materialListPath] = selectedMaterial.id
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
        cropOverlayView.setEditing(false)
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
                val croppedJpegData = cropRenderedImage(jpegData)
                val imageFile = croppedJpegData?.let { savePreviewImage(it) }
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

    private fun cropRenderedImage(jpegData: ByteArray): ByteArray? {
        if (cropRect == displayRect) return jpegData

        val renderedBitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
            ?: return null
        val cropBounds = mapCropRect(renderedBitmap.width, renderedBitmap.height)
        // if it is original,
        if (cropBounds.left == 0 && cropBounds.top == 0 && cropBounds.right == renderedBitmap.width && cropBounds.bottom == renderedBitmap.height) {
            renderedBitmap.recycle()
            return jpegData
        }
        val croppedBitmap = try {
            Bitmap.createBitmap(
                renderedBitmap,
                cropBounds.left,
                cropBounds.top,
                cropBounds.width(),
                cropBounds.height(),
            )
        } finally {
            renderedBitmap.recycle()
        }

        return try {
            ByteArrayOutputStream().use { outputStream ->
                if (croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                    outputStream.toByteArray()
                } else {
                    null
                }
            }
        } finally {
            croppedBitmap.recycle()
        }
    }

    private fun mapCropRect(bitmapWidth: Int, bitmapHeight: Int): Rect {
        val horizontalScale = bitmapWidth / displayRect.width()
        val verticalScale = bitmapHeight / displayRect.height()
        val left = ((cropRect.left - displayRect.left) * horizontalScale)
            .roundToInt()
            .coerceIn(0, bitmapWidth - 1)
        val top = ((cropRect.top - displayRect.top) * verticalScale)
            .roundToInt()
            .coerceIn(0, bitmapHeight - 1)
        val right = ((cropRect.right - displayRect.left) * horizontalScale)
            .roundToInt()
            .coerceIn(left + 1, bitmapWidth)
        val bottom = ((cropRect.bottom - displayRect.top) * verticalScale)
            .roundToInt()
            .coerceIn(top + 1, bitmapHeight)
        return Rect(left, top, right, bottom)
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
