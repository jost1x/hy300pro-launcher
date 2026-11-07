package io.jostx.hy300launcher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import io.jostx.hy300launcher.ui.HdmiPreviewView

/** Minimal full-screen HDMI client for comparing the OEM player's presentation path. */
class HdmiInputActivity : Activity() {
    private lateinit var hdmiView: HdmiPreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        hdmiView = HdmiPreviewView(
            activity = this,
            roundedCorners = false,
        )
        root.addView(hdmiView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        applyImmersiveFlags()
    }

    override fun onResume() {
        super.onResume()
        if (::hdmiView.isInitialized) hdmiView.resumePreview()
    }

    override fun onPause() {
        if (::hdmiView.isInitialized) hdmiView.pausePreview()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveFlags()
    }

    override fun onDestroy() {
        if (::hdmiView.isInitialized) hdmiView.release()
        super.onDestroy()
    }

    private fun applyImmersiveFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}
