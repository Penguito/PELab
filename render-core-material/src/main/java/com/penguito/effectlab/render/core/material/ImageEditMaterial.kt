package com.penguito.effectlab.render.core.material

data class ImageEditMaterial(
    val id: String,
    val displayName: String,
    val iconResourceId: Int,
    val minimum: Int,
    val maximum: Int,
    val defaultValue: Int,
) : Material

sealed interface Material

enum class MaterialType {
    IMAGE_EDIT,
    FILTER,
}

object MaterialConfig {
    const val EDIT_LIGHTING_LIST = "materials/edit_lighting_list.json"
    const val EDIT_COLOR_LIST = "materials/edit_color_list.json"
    const val EDIT_DETAIL_LIST = "materials/edit_detail_list.json"
    const val FILTER_LIST = "materials/filter_list.json"
}
