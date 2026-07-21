package com.penguito.effectlab

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.penguito.effectlab.render.ui.RenderSdkStatus

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.native_bridge_test).text = getString(
            R.string.native_bridge_ready,
            RenderSdkStatus.getNativeBridgeInfo(),
        )
    }

}
