package com.penguito.effectlab.render.core.material

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class MaterialManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val filterRoot = File(applicationContext.filesDir, INSTALLED_FILTER_ROOT)

    fun loadMaterialList(configPath: String, materialType: MaterialType): List<Material> {
        val configuration = applicationContext.assets.open(configPath)
            .bufferedReader()
            .use { JSONObject(it.readText()) }
        return configuration.collectMaterials().map {
            when (materialType) {
                MaterialType.IMAGE_EDIT -> it.toImageEditMaterial()
                MaterialType.FILTER -> it.toFilterMaterial()
            }
        }
    }

    private fun JSONObject.collectMaterials(): List<JSONObject> {
        val materials = mutableListOf<JSONObject>()
        optJSONArray(MATERIALS_KEY)?.addObjectsTo(materials)
        optJSONArray(CATEGORIES_KEY)?.let { categories ->
            for (index in 0 until categories.length()) {
                materials.addAll(categories.getJSONObject(index).collectMaterials())
            }
        }
        return materials
    }

    private fun JSONArray.addObjectsTo(materials: MutableList<JSONObject>) {
        for (index in 0 until length()) {
            materials.add(getJSONObject(index))
        }
    }

    private fun JSONObject.toImageEditMaterial(): ImageEditMaterial {
        return ImageEditMaterial(
            id = getString(ID_KEY),
            displayName = getString(DISPLAY_NAME_KEY),
            iconResourceId = getResourceId(ICON_RESOURCE_KEY, "drawable"),
            minimum = getInt(MINIMUM_KEY),
            maximum = getInt(MAXIMUM_KEY),
            defaultValue = getInt(DEFAULT_VALUE_KEY),
        )
    }

    private fun JSONObject.toFilterMaterial(): FilterMaterial {
        val filterId = getString(ID_KEY)
        val assetRoot = getString(ASSET_ROOT_KEY)
        val filterDirectory = File(filterRoot, filterId)
        val lutFile = File(filterDirectory, LUT_FILE_NAME)

        filterDirectory.mkdirs()
        applicationContext.assets.open("$assetRoot/$LUT_FILE_NAME").use { input ->
            lutFile.outputStream().use(input::copyTo)
        }
        return FilterMaterial(
            id = filterId,
            displayName = getString(DISPLAY_NAME_KEY),
            rootPath = filterDirectory.absolutePath,
            iconPath = lutFile.absolutePath,
        )
    }

    private fun JSONObject.getResourceId(key: String, type: String): Int {
        return applicationContext.resources.getIdentifier(getString(key), type, applicationContext.packageName)
    }

    private companion object {
        const val INSTALLED_FILTER_ROOT = "materials/filters"
        const val LUT_FILE_NAME = "lut.png"
        const val CATEGORIES_KEY = "categories"
        const val MATERIALS_KEY = "materials"
        const val ID_KEY = "id"
        const val DISPLAY_NAME_KEY = "displayName"
        const val ICON_RESOURCE_KEY = "iconResource"
        const val ASSET_ROOT_KEY = "assetRoot"
        const val MINIMUM_KEY = "minimum"
        const val MAXIMUM_KEY = "maximum"
        const val DEFAULT_VALUE_KEY = "defaultValue"
    }
}
