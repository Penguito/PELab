package com.penguito.effectlab.render.ui

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

data class SelectionPanelItem(
    val id: String,
    val name: String,
    val icon: SelectionPanelIcon? = null,
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
    private val categoryScrollView: HorizontalScrollView
    private val categoryContainer: LinearLayout
    private val emptyView: TextView
    private val scrollView: HorizontalScrollView
    private val itemContainer: LinearLayout
    private val categoryViews = mutableMapOf<String, View>()
    private val itemViews = mutableMapOf<String, View>()

    private var selectedCategoryId: String? = null
    private var selectedItemId: String? = null
    private var categorySelectedListener: ((SelectionPanelCategory) -> Unit)? = null
    private var itemSelectedListener: ((SelectionPanelItem?) -> Unit)? = null

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
        categoryScrollView = findViewById(R.id.selection_panel_category_scroll)
        categoryContainer = findViewById(R.id.selection_panel_categories)
        emptyView = findViewById(R.id.selection_panel_empty)
        scrollView = findViewById(R.id.selection_panel_scroll)
        itemContainer = findViewById(R.id.selection_panel_items)
    }

    fun setItems(
        items: List<SelectionPanelItem>,
        emptyText: CharSequence,
        showNoneButton: Boolean = false,
        selectedItemId: String? = null,
    ) {
        itemContainer.removeAllViews()
        itemViews.clear()
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
        items.forEach(::addItem)
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

    fun setOnItemSelectedListener(listener: (SelectionPanelItem?) -> Unit) {
        itemSelectedListener = listener
    }

    fun setOnCategorySelectedListener(listener: (SelectionPanelCategory) -> Unit) {
        categorySelectedListener = listener
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

    private fun ImageView.showIcon(icon: SelectionPanelIcon) {
        when (icon) {
            is SelectionPanelIcon.Resource -> setImageResource(icon.resourceId)
            is SelectionPanelIcon.FilePath -> setImageURI(Uri.fromFile(File(icon.path)))
        }
    }
}
