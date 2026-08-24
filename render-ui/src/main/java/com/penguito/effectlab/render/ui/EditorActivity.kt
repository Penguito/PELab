package com.penguito.effectlab.render.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import java.io.File

class EditorActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sourceValue = intent.getStringExtra(EXTRA_IMAGE_SOURCE)
        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (sourceValue == null || imagePath == null) {
            finish()
            return
        }

        setContentView(R.layout.activity_editor)
        val imageSource = ImageSource.valueOf(sourceValue)
        val imageUri = Uri.fromFile(File(imagePath))
        val sourceName = when (imageSource) {
            ImageSource.CAPTURE -> getString(R.string.editor_source_capture)
            ImageSource.ALBUM -> getString(R.string.editor_source_album)
        }
        findViewById<ImageView>(R.id.editor_image).setImageURI(imageUri)
        findViewById<TextView>(R.id.editor_image_info).text = getString(
            R.string.editor_image_info,
            sourceName,
            imagePath,
        )
        findViewById<Button>(R.id.editor_back).setOnClickListener {
            finish()
        }
    }

    companion object {
        private const val EXTRA_IMAGE_SOURCE = "image_source"
        private const val EXTRA_IMAGE_PATH = "image_path"

        fun createIntent(
            context: Context,
            imageSource: ImageSource,
            imagePath: String,
        ): Intent = Intent(context, EditorActivity::class.java)
            .putExtra(EXTRA_IMAGE_SOURCE, imageSource.name)
            .putExtra(EXTRA_IMAGE_PATH, imagePath)
    }
}
