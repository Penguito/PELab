package com.penguito.effectlab.render.ui

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class CaptureGestureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private var scaleListener: ((Float) -> Unit)? = null
    private var focusListener: ((Float, Float) -> Unit)? = null

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleListener?.invoke(detector.scaleFactor)
                return true
            }
        },
    )
    private val tapGestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                if (width == 0 || height == 0) return false

                performClick()
                focusListener?.invoke(
                    event.x / width,
                    event.y / height,
                )
                return true
            }
        },
    )

    fun setOnScaleListener(listener: (Float) -> Unit) {
        scaleListener = listener
    }

    fun setOnFocusListener(listener: (Float, Float) -> Unit) {
        focusListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        tapGestureDetector.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
