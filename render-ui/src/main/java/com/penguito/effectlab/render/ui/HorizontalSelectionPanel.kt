package com.penguito.effectlab.render.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.io.File

data class SelectionPanelItem(
    val id: String,
    val name: String,
    val icon: SelectionPanelIcon? = null,
    val value: Int? = null,
    val defaultValue: Int? = null,
)

sealed interface SelectionPanelIcon {
    data class Resource(val resourceId: Int) : SelectionPanelIcon

    data class FilePath(val path: String) : SelectionPanelIcon
}

data class SelectionPanelCategory(
    val id: String,
    val name: String,
)

class HorizontalSelectionPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val noneButton: ImageButton
    private val noneDivider: View
    private val header: View
    private val seekContainer: View
    private val seekBar: SeekBar
    private val compareButton: ImageButton
    private val categoryScrollView: HorizontalScrollView
    private val categoryContainer: LinearLayout
    private val emptyView: TextView
    private val scrollView: HorizontalScrollView
    private val itemContainer: LinearLayout
    private val panelName: TextView
    private val categoryViews = mutableMapOf<String, View>()
    private val itemViews = mutableMapOf<String, View>()
    private val itemValues = mutableMapOf<String, Int>()
    private val itemDefaultValues = mutableMapOf<String, Int>()

    private var selectedCategoryId: String? = null
    private var selectedItemId: String? = null
    private var categorySelectedListener: ((SelectionPanelCategory) -> Unit)? = null
    private var itemSelectedListener: ((SelectionPanelItem?) -> Unit)? = null
    private var valueChangedListener: ((Int) -> Unit)? = null
    private var compareStartedListener: (() -> Unit)? = null
    private var compareStoppedListener: (() -> Unit)? = null
    private var isComparing = false

    init {
        LayoutInflater.from(context).inflate(R.layout.panel_layout_item, this)
        noneButton = findViewById<ImageButton>(R.id.selection_panel_none).also {
            it.setOnClickListener {
                selectedItemId = null
                updateItemSelection()
                itemSelectedListener?.invoke(null)
            }
        }
        noneDivider = findViewById(R.id.selection_panel_none_divider)
        header = findViewById(R.id.selection_panel_header)
        seekContainer = findViewById(R.id.selection_panel_seek_container)
        seekBar = findViewById<SeekBar>(R.id.selection_panel_seek_bar).also {
            it.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    selectedItemId?.let { itemId ->
                        if (itemDefaultValues.containsKey(itemId)) {
                            itemValues[itemId] = progress
                            updateItemValue(itemId)
                        }
                    }
                    if (fromUser) valueChangedListener?.invoke(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        compareButton = findViewById(R.id.selection_panel_compare)
        setupCompareButton()
        categoryScrollView = findViewById(R.id.selection_panel_category_scroll)
        categoryContainer = findViewById(R.id.selection_panel_categories)
        emptyView = findViewById(R.id.selection_panel_empty)
        scrollView = findViewById(R.id.selection_panel_scroll)
        itemContainer = findViewById(R.id.selection_panel_items)
        panelName = findViewById(R.id.selection_panel_name)
    }

    fun setItems(
        items: List<SelectionPanelItem>,
        emptyText: CharSequence,
        showNoneButton: Boolean = false,
        selectedItemId: String? = null,
    ) {
        itemContainer.removeAllViews()
        itemViews.clear()
        itemValues.clear()
        itemDefaultValues.clear()
        this.selectedItemId = selectedItemId
        noneButton.visibility = if (showNoneButton) View.VISIBLE else View.GONE
        noneDivider.visibility = noneButton.visibility

        if (items.isEmpty()) {
            emptyView.text = emptyText
            emptyView.visibility = View.VISIBLE
            scrollView.visibility = View.GONE
            updateItemSelection()
            return
        }

        emptyView.visibility = View.GONE
        scrollView.visibility = View.VISIBLE
        items.forEach {
            if (it.value != null && it.defaultValue != null) {
                itemValues[it.id] = it.value
                itemDefaultValues[it.id] = it.defaultValue
            }
            addItem(it)
        }
        updateItemSelection()
    }

    fun setCategories(
        categories: List<SelectionPanelCategory>,
        selectedCategoryId: String? = null,
    ) {
        categoryContainer.removeAllViews()
        categoryViews.clear()
        this.selectedCategoryId = selectedCategoryId
        categoryScrollView.visibility = if (categories.isEmpty()) View.INVISIBLE else View.VISIBLE
        categories.forEach(::addCategory)
        updateCategorySelection()
    }

    fun setValueRange(
        minimum: Int,
        maximum: Int,
        value: Int,
    ) {
        seekContainer.visibility = View.VISIBLE
        seekBar.min = minimum
        seekBar.max = maximum
        seekBar.progress = value
    }

    fun hideValueRange() {
        seekContainer.visibility = View.GONE
    }

    fun setHeaderVisible(visible: Boolean) {
        header.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setPanelName(name: CharSequence) {
        panelName.text = name
        panelName.visibility = if (name.isEmpty()) View.GONE else View.VISIBLE
    }

    fun setOnItemSelectedListener(listener: (SelectionPanelItem?) -> Unit) {
        itemSelectedListener = listener
    }

    fun setOnCategorySelectedListener(listener: (SelectionPanelCategory) -> Unit) {
        categorySelectedListener = listener
    }

    fun setOnValueChangedListener(listener: (Int) -> Unit) {
        valueChangedListener = listener
    }

    fun setCompareVisible(visible: Boolean) {
        compareButton.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setOnCompareListener(
        onStarted: () -> Unit,
        onStopped: () -> Unit,
    ) {
        compareStartedListener = onStarted
        compareStoppedListener = onStopped
    }

    override fun onDetachedFromWindow() {
        stopComparing()
        super.onDetachedFromWindow()
    }

    private fun addCategory(category: SelectionPanelCategory) {
        val categoryView = LayoutInflater.from(context).inflate(
            R.layout.panel_layout_category,
            categoryContainer,
            false,
        )
        categoryView.findViewById<TextView>(R.id.selection_panel_category_name).text = category.name
        categoryView.setOnClickListener {
            selectedCategoryId = category.id
            updateCategorySelection()
            categorySelectedListener?.invoke(category)
        }
        categoryContainer.addView(categoryView)
        categoryViews[category.id] = categoryView
    }

    private fun addItem(item: SelectionPanelItem) {
        val itemView = LayoutInflater.from(context).inflate(
            R.layout.panel_item_selection,
            itemContainer,
            false,
        )
        itemView.findViewById<TextView>(R.id.selection_panel_item_name).text = item.name
        item.icon?.let {
            itemView.findViewById<ImageView>(R.id.selection_panel_item_icon).showIcon(it)
        }
        itemView.contentDescription = item.name
        itemView.setOnClickListener {
            selectedItemId = item.id
            updateItemSelection()
            itemSelectedListener?.invoke(item)
        }
        itemContainer.addView(itemView)
        itemViews[item.id] = itemView
        updateItemValue(item.id)
    }

    private fun updateCategorySelection() {
        categoryViews.forEach { (categoryId, categoryView) ->
            categoryView.isSelected = categoryId == selectedCategoryId
        }
    }

    private fun updateItemSelection() {
        noneButton.isSelected = selectedItemId == null
        itemViews.forEach { (itemId, itemView) ->
            itemView.isSelected = itemId == selectedItemId
        }
    }

    private fun updateItemValue(itemId: String) {
        val value = itemValues[itemId]
        val defaultValue = itemDefaultValues[itemId]
        itemViews[itemId]?.findViewById<TextView>(R.id.selection_panel_item_value)?.apply {
            text = value?.toString()
            visibility = if (value != null && value != defaultValue) View.VISIBLE else View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCompareButton() {
        compareButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isComparing = true
                    view.isPressed = true
                    compareStartedListener?.invoke()
                }

                MotionEvent.ACTION_UP -> {
                    stopComparing()
                    view.performClick()
                }

                MotionEvent.ACTION_CANCEL -> stopComparing()
            }
            true
        }
    }

    private fun stopComparing() {
        if (!isComparing) return

        isComparing = false
        compareButton.isPressed = false
        compareStoppedListener?.invoke()
    }

    private fun ImageView.showIcon(icon: SelectionPanelIcon) {
        when (icon) {
            is SelectionPanelIcon.Resource -> {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageResource(icon.resourceId)
            }

            is SelectionPanelIcon.FilePath -> {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(Uri.fromFile(File(icon.path)))
            }
        }
    }
}
