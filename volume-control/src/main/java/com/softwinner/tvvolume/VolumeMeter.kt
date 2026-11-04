package com.softwinner.tvvolume

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

internal class VolumeMeter(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var volume = 0
    private var muted = false

    init {
        contentDescription = "Nivel de volumen"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun update(value: Int, isMuted: Boolean) {
        volume = value.coerceIn(0, TvAudioControl.MAX_VOLUME)
        muted = isMuted
        contentDescription = if (muted) "Volumen silenciado" else "Volumen $volume por ciento"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val height = min(20f * density, this.height.toFloat())
        val top = (this.height - height) / 2f
        val radius = height / 2f
        bounds.set(0f, top, this.width.toFloat(), top + height)
        paint.color = 0xff343944.toInt()
        canvas.drawRoundRect(bounds, radius, radius, paint)
        if (!muted && volume > 0) {
            bounds.right = this.width * volume / TvAudioControl.MAX_VOLUME.toFloat()
            paint.color = 0xff76b9ff.toInt()
            canvas.drawRoundRect(bounds, radius, radius, paint)
        }
    }
}
