package com.penguito.effectlab

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.penguito.effectlab.render.ui.CaptureLauncher
import com.penguito.effectlab.render.ui.EditorActivity
import com.penguito.effectlab.render.ui.ImageSource
import com.penguito.effectlab.render.ui.PhotoPickerLauncher
import com.penguito.effectlab.render.ui.RenderSdkStatus

class MainActivity : Activity() {
    private lateinit var captureLauncher: CaptureLauncher
    private lateinit var photoPickerLauncher: PhotoPickerLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        captureLauncher = CaptureLauncher(
            activity = this,
            onPermissionDenied = {
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
            },
        )
        photoPickerLauncher = PhotoPickerLauncher(
            activity = this,
            onImageSelected = { imageUri ->
                startActivity(
                    EditorActivity.createIntent(
                        context = this,
                        imageSource = ImageSource.ALBUM,
                        imagePath = imageUri.toString(),
                    ),
                )
            },
        )
        findViewById<TextView>(R.id.native_bridge_test).text = getString(
            R.string.native_bridge_ready,
            RenderSdkStatus.getNativeBridgeInfo(),
        )
        findViewById<Button>(R.id.capture_test).setOnClickListener {
            captureLauncher.launch()
        }
        findViewById<Button>(R.id.select_photo).setOnClickListener {
            photoPickerLauncher.launch()
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        photoPickerLauncher.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        captureLauncher.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
