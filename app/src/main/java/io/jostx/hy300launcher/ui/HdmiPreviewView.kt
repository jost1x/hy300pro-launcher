package io.jostx.hy300launcher.ui

import android.app.Activity
import android.content.Context
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.net.Uri
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import io.jostx.hy300launcher.R
import io.jostx.hy300launcher.data.ProjectorInputRepository

/** Live HDMI 1 surface using the same passthrough URI as the OEM TV app. */
class HdmiPreviewView(
    activity: Activity,
    roundedCorners: Boolean = true,
) : FrameLayout(activity) {
    private val tvView = TvView(activity)
    private val status = TextView(activity).apply {
        text = "Conectando con HDMI 1…"
        setTextColor(activity.uiColor(R.color.launcher_text_primary))
        setTextAppearance(R.style.LauncherTextCaption)
        gravity = Gravity.CENTER
        setPadding(activity.dp(activity.uiDimen(R.dimen.space_12)), 0, activity.dp(activity.uiDimen(R.dimen.space_12)), 0)
        isFocusable = false
        isFocusableInTouchMode = false
    }
    private val tvInputManager = activity.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
    private val inputId = ProjectorInputRepository.HDMI1_INPUT_ID
    private val channelUri = Uri.parse(
        "content://android.media.tv/passthrough/${Uri.encode(inputId)}",
    )
    private var tuned = false

    private val callback = object : TvView.TvInputCallback() {
        override fun onVideoAvailable(inputId: String) {
            if (inputId == this@HdmiPreviewView.inputId) {
                status.visibility = View.GONE
            }
        }

        override fun onVideoUnavailable(inputId: String, reason: Int) {
            if (inputId == this@HdmiPreviewView.inputId) {
                status.text = if (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING) {
                    "Conectando con HDMI 1…"
                } else {
                    "Sin señal en HDMI 1"
                }
                status.visibility = View.VISIBLE
            }
        }

        override fun onConnectionFailed(inputId: String) {
            if (inputId == this@HdmiPreviewView.inputId) {
                status.text = "Vista previa HDMI 1 no disponible"
                status.visibility = View.VISIBLE
            }
        }

        override fun onDisconnected(inputId: String) {
            if (inputId == this@HdmiPreviewView.inputId) {
                status.text = "HDMI 1 desconectado"
                status.visibility = View.VISIBLE
            }
        }
    }

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        if (roundedCorners) {
            background = activity.rounded(
                activity.uiColor(R.color.launcher_surface),
                activity.uiDimen(R.dimen.radius_surface),
            )
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        } else {
            setBackgroundColor(Color.BLACK)
        }
        addView(tvView, LayoutParams(-1, -1))
        addView(status, LayoutParams(-1, -1))
        tvView.setCallback(callback)
    }

    fun resumePreview() {
        if (tuned) return
        if (tvInputManager.tvInputList.none { it.id == inputId }) {
            status.text = "Entrada HDMI 1 no disponible"
            status.visibility = View.VISIBLE
            return
        }
        status.text = "Conectando con HDMI 1…"
        status.visibility = View.VISIBLE
        tvView.tune(inputId, channelUri)
        tuned = true
    }

    fun pausePreview() {
        if (!tuned) return
        tvView.reset()
        tuned = false
    }

    fun release() {
        pausePreview()
        tvView.setCallback(null)
    }
}
