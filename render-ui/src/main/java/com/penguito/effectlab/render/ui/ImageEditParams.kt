package com.penguito.effectlab.render.ui

import com.penguito.effectlab.render.core.material.ImageEditMaterial
import com.penguito.effectlab.render.sdk.ImageParams

internal fun ImageParams.withMaterialValue(material: ImageEditMaterial, value: Int): ImageParams {
    // normalize UI values without resetting other adjustments
    val normalizedValue = value.coerceIn(material.minimum, material.maximum) / 100.0F
    val builder = ImageParams.builder(this)
    when (material.id) {
        "brightness"  -> builder.setBrightness(normalizedValue)
        "contrast"    -> builder.setContrast(normalizedValue)
        "exposure"    -> builder.setExposure(normalizedValue)
        "highlights"  -> builder.setHighlights(normalizedValue)
        "shadows"     -> builder.setShadows(normalizedValue)
        "temperature" -> builder.setWarmth(normalizedValue)
        "tint"        -> builder.setTint(normalizedValue)
        "saturation"  -> builder.setSaturation(normalizedValue)
        "vibrance"    -> builder.setVibrance(normalizedValue)
        "grain"       -> builder.setGrain(normalizedValue)
        "vignette"    -> builder.setVignette(normalizedValue)
        else          -> throw IllegalArgumentException("Unknown image edit material: ${material.id}")
    }
    return builder.build()
}
