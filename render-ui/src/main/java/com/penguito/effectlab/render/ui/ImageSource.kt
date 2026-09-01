package com.penguito.effectlab.render.ui

enum class ImageSource(val value: String) {
    CAPTURE("capture"),
    ALBUM("album"),
    ;

    companion object {
        fun fromValue(value: String?): ImageSource? = entries.find { it.value == value }
    }
}
