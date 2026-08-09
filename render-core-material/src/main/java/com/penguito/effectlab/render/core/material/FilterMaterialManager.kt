package com.penguito.effectlab.render.core.material

import android.content.Context
import java.io.File

class FilterMaterialManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val filterRoot = File(applicationContext.filesDir, INSTALLED_FILTER_ROOT)

    fun initFilterList(): List<FilterMaterial> {
        val filterIds = applicationContext.assets.list(ASSET_FILTER_ROOT).orEmpty().sorted()

        val filterList = mutableListOf<FilterMaterial>()
        for (filterId in filterIds) {
            filterList.add(addFilterMaterial(filterId))
        }
        return filterList
    }

    private fun addFilterMaterial(filterId: String): FilterMaterial {
        val assetPath = "$ASSET_FILTER_ROOT/$filterId/$LUT_FILE_NAME"
        val filterDirectory = File(filterRoot, filterId)
        val lutFile = File(filterDirectory, LUT_FILE_NAME)

        filterDirectory.mkdirs()
        if (!lutFile.exists() || lutFile.length() == 0L) {
            applicationContext.assets.open(assetPath).use { input ->
                lutFile.outputStream().use(input::copyTo)
            }
        }
        return FilterMaterial(
            id = filterId,
            displayName = filterId.toDisplayName(),
            rootPath = filterDirectory.absolutePath,
            iconPath = lutFile.absolutePath,
        )
    }

    // temp
    private fun String.toDisplayName(): String {
        return split('_', '-').joinToString(" ") { word ->
            word.replaceFirstChar { character -> character.uppercase() }
        }
    }

    private companion object {
        const val ASSET_FILTER_ROOT = "filters"
        const val INSTALLED_FILTER_ROOT = "materials/filters"
        const val LUT_FILE_NAME = "lut.png"
    }
}
