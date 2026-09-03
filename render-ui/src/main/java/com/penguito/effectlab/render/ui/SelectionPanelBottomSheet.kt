package com.penguito.effectlab.render.ui

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SelectionPanelBottomSheet : BottomSheetDialogFragment(R.layout.panel_fragment_selection) {
    private var items = emptyList<SelectionPanelItem>()
    private var categories = emptyList<SelectionPanelCategory>()
    private var emptyText: CharSequence = ""
    private var showNoneButton = false
    private var selectedItemId: String? = null
    private var selectedCategoryId: String? = null
    private var itemSelectedListener: ((SelectionPanelItem?) -> Unit)? = null
    private var categorySelectedListener: ((SelectionPanelCategory) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), theme).apply {
            setCanceledOnTouchOutside(true)
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<HorizontalSelectionPanel>(R.id.selection_panel).apply {
            setOnItemSelectedListener { itemSelectedListener?.invoke(it) }
            setOnCategorySelectedListener { categorySelectedListener?.invoke(it) }
            setCategories(categories, selectedCategoryId)
            setItems(
                items = items,
                emptyText = emptyText,
                showNoneButton = showNoneButton,
                selectedItemId = selectedItemId,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheetDialog = dialog as BottomSheetDialog
        bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        bottomSheetDialog.behavior.apply {
            isHideable = true
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    fun setItems(
        items: List<SelectionPanelItem>,
        emptyText: CharSequence,
        showNoneButton: Boolean = false,
        selectedItemId: String? = null,
    ) {
        this.items = items
        this.emptyText = emptyText
        this.showNoneButton = showNoneButton
        this.selectedItemId = selectedItemId
    }

    fun setCategories(
        categories: List<SelectionPanelCategory>,
        selectedCategoryId: String? = null,
    ) {
        this.categories = categories
        this.selectedCategoryId = selectedCategoryId
    }

    fun setOnItemSelectedListener(listener: (SelectionPanelItem?) -> Unit) {
        itemSelectedListener = listener
    }

    fun setOnCategorySelectedListener(listener: (SelectionPanelCategory) -> Unit) {
        categorySelectedListener = listener
    }
}
