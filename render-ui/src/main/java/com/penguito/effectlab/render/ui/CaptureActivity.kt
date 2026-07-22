package com.penguito.effectlab.render.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.penguito.effectlab.render.core.permission.CameraPermissionGate

class CaptureActivity : Activity() {
    private val permissionGate by lazy { CameraPermissionGate(this) }
    private var lifecycleStatus: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!permissionGate.isGranted()) {
            finish()
            return
        }

        setContentView(R.layout.activity_capture)
        lifecycleStatus = findViewById(R.id.capture_status)
    }

    override fun onResume() {
        super.onResume()
        if (!permissionGate.isGranted()) {
            finish()
            return
        }
        resumeCapture()
    }

    override fun onPause() {
        pauseCapture()
        super.onPause()
    }

    private fun resumeCapture() {
        Log.d(LOG_TAG, "Capture lifecycle resumed")
        lifecycleStatus?.setText(R.string.capture_resumed)
    }

    private fun pauseCapture() {
        Log.d(LOG_TAG, "Capture lifecycle paused")
        lifecycleStatus?.setText(R.string.capture_paused)
    }

    companion object {
        private const val LOG_TAG = "PELabCapture"

        fun createIntent(context: Context): Intent = Intent(context, CaptureActivity::class.java)
    }
}
