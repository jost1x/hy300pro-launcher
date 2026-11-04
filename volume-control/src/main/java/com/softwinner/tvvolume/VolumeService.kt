package com.softwinner.tvvolume

import android.app.Dialog
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Receives the firmware's keyevent extra and draws the volume OSD. */
class VolumeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audio: TvAudioControl
    private var dialog: Dialog? = null
    private var meter: VolumeMeter? = null
    private var valueLabel: TextView? = null
    private var muteLabel: TextView? = null
    private val dismissOverlay = Runnable { hideOverlay() }

    private val localBinder = object : Binder() {
        @Suppress("unused")
        fun getService(): VolumeService = this@VolumeService
    }

    override fun onCreate() {
        super.onCreate()
        audio = TvAudioControl(this)
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val event = intent?.let(::readKeyEvent)
        if (event != null) handler.post { handleKeyEvent(event) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        super.onDestroy()
    }

    private fun readKeyEvent(intent: Intent): KeyEvent? = try {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra("keyevent") as? KeyEvent
    } catch (_: Exception) {
        null
    }

    private fun handleKeyEvent(event: KeyEvent) {
        try {
            showOverlay()
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0 && event.repeatCount % REPEAT_INTERVAL == 0) {
                        adjustForKey(event.keyCode)
                    }
                }
                KeyEvent.ACTION_UP -> adjustForKey(event.keyCode)
            }
            updateOverlay()
            handler.removeCallbacks(dismissOverlay)
            handler.postDelayed(dismissOverlay, OVERLAY_DURATION_MS)
        } catch (error: Exception) {
            android.util.Log.e(TAG, "No se pudo aplicar el evento de volumen", error)
            hideOverlay()
        }
    }

    private fun adjustForKey(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> audio.setVolume((audio.getVolume() + 1).coerceAtMost(TvAudioControl.MAX_VOLUME))
            KeyEvent.KEYCODE_VOLUME_DOWN -> audio.setVolume((audio.getVolume() - 1).coerceAtLeast(0))
            KeyEvent.KEYCODE_VOLUME_MUTE -> audio.setMuted(!audio.isMuted())
        }
    }

    private fun showOverlay() {
        if (dialog?.isShowing == true) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(20), dp(28), dp(20))
            background = GradientDrawable().apply {
                setColor(0xee171a21.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x5576b9ff)
            }
        }
        val heading = TextView(this).apply {
            text = "VOLUMEN"
            textSize = 13f
            letterSpacing = 0.1f
            setTextColor(0xffaeb6c5.toInt())
        }
        panel.addView(heading)
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        valueLabel = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
        line.addView(valueLabel, LinearLayout.LayoutParams(dp(84), -2))
        meter = VolumeMeter(this)
        line.addView(meter, LinearLayout.LayoutParams(0, dp(32), 1f).apply { leftMargin = dp(12) })
        panel.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        muteLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(0xffaeb6c5.toInt())
        }
        panel.addView(muteLabel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        dialog = Dialog(this).apply {
            setContentView(panel)
            setCancelable(false)
            window?.apply {
                setType(WINDOW_TYPE_VOLUME_OVERLAY)
                addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                )
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(dp(430), WindowManager.LayoutParams.WRAP_CONTENT)
                attributes = attributes.apply {
                    gravity = Gravity.TOP or Gravity.END
                    y = dp(54)
                    x = dp(42)
                }
            }
            show()
            window?.setLayout(dp(430), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        updateOverlay()
    }

    private fun updateOverlay() {
        val currentVolume = audio.getVolume()
        val isMuted = audio.isMuted()
        valueLabel?.text = "$currentVolume%"
        meter?.update(currentVolume, isMuted)
        muteLabel?.text = if (isMuted) "Silenciado" else "Altavoz"
    }

    private fun hideOverlay() {
        dialog?.dismiss()
        dialog = null
        meter = null
        valueLabel = null
        muteLabel = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "HY300VolumeService"
        const val REPEAT_INTERVAL = 2
        const val OVERLAY_DURATION_MS = 2_000L
        const val WINDOW_TYPE_VOLUME_OVERLAY = 2020
    }
}
