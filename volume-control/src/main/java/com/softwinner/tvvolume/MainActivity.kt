package com.softwinner.tvvolume

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Standalone volume panel; it does not add volume controls to the launcher. */
class MainActivity : Activity() {
    private lateinit var audio: TvAudioControl
    private lateinit var valueLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var meter: VolumeMeter
    private lateinit var muteButton: TextView
    private var volume = 0
    private var muted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = TvAudioControl(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(64), dp(36), dp(64), dp(36))
            setBackgroundColor(0xff101218.toInt())
        }
        root.addView(TextView(this).apply {
            text = "VOLUMEN"
            textSize = 16f
            setTextColor(0xffaeb6c5.toInt())
            letterSpacing = 0.12f
        }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(14) })
        valueLabel = TextView(this).apply {
            textSize = 52f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        root.addView(valueLabel, LinearLayout.LayoutParams(-1, -2))
        statusLabel = TextView(this).apply {
            textSize = 16f
            setTextColor(0xffaeb6c5.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(statusLabel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        meter = VolumeMeter(this)
        root.addView(meter, LinearLayout.LayoutParams(-1, dp(40)).apply {
            topMargin = dp(22)
            bottomMargin = dp(24)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(actionButton("−", "Bajar volumen") { changeVolume(-1) })
        muteButton = actionButton("Silenciar", "Silenciar audio", ::toggleMute)
        actions.addView(muteButton, LinearLayout.LayoutParams(dp(190), dp(64)).apply {
            leftMargin = dp(14)
            rightMargin = dp(14)
        })
        actions.addView(actionButton("+", "Subir volumen") { changeVolume(1) })
        root.addView(actions, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Usa izquierda y derecha para ajustar · Atrás para salir"
            textSize = 14f
            setTextColor(0xff8d95a4.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        setContentView(root)
        refreshState()
        muteButton.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (::audio.isInitialized) refreshState()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (event.repeatCount == 0) changeVolume(1)
            true
        }
        KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_LEFT -> {
            if (event.repeatCount == 0) changeVolume(-1)
            true
        }
        KeyEvent.KEYCODE_VOLUME_MUTE -> {
            if (event.repeatCount == 0) toggleMute()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    private fun actionButton(label: String, description: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = if (label == "−" || label == "+") 28f else 18f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        contentDescription = description
        isFocusable = true
        isFocusableInTouchMode = true
        background = buttonBackground(false)
        setOnFocusChangeListener { view, focused ->
            view.background = buttonBackground(focused)
            view.animate().scaleX(if (focused) 1.04f else 1f).scaleY(if (focused) 1.04f else 1f)
                .setDuration(120).start()
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(72), dp(64))
    }

    private fun buttonBackground(focused: Boolean) = GradientDrawable().apply {
        setColor(if (focused) 0xff29496c.toInt() else 0xff20242c.toInt())
        cornerRadius = dp(14).toFloat()
        if (focused) setStroke(dp(2), 0xff76b9ff.toInt())
    }

    private fun changeVolume(delta: Int) {
        runCatching { audio.setVolume((volume + delta).coerceIn(0, TvAudioControl.MAX_VOLUME)) }
            .onSuccess { refreshState() }
            .onFailure { showFailure(it) }
    }

    private fun toggleMute() {
        runCatching { audio.setMuted(!muted) }
            .onSuccess { refreshState() }
            .onFailure { showFailure(it) }
    }

    private fun refreshState() {
        runCatching {
            volume = audio.getVolume()
            muted = audio.isMuted()
            valueLabel.text = "$volume%"
            statusLabel.text = if (muted) "Silenciado" else "Altavoz"
            muteButton.text = if (muted) "Activar sonido" else "Silenciar"
            muteButton.contentDescription = if (muted) "Activar sonido" else "Silenciar audio"
            meter.update(volume, muted)
        }.onFailure { showFailure(it) }
    }

    private fun showFailure(error: Throwable) {
        statusLabel.text = error.cause?.message ?: error.message ?: "Control de volumen no disponible"
        statusLabel.setTextColor(0xffffb4ab.toInt())
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
