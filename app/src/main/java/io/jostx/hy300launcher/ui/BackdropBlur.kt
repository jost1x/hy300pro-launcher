package io.jostx.hy300launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import io.jostx.hy300launcher.R

/** Captures and blurs a small, static snapshot of the launcher behind a popup. */
@Suppress("DEPRECATION")
internal fun captureBlurredBackdrop(
    context: Context,
    source: View,
    leftOnScreen: Int,
    topOnScreen: Int,
    width: Int,
    height: Int,
): Bitmap? {
    if (source.width <= 0 || source.height <= 0 || width <= 0 || height <= 0) return null

    val screenshot = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    source.draw(Canvas(screenshot))

    val sourcePosition = IntArray(2)
    source.getLocationOnScreen(sourcePosition)
    val cropLeft = (leftOnScreen - sourcePosition[0]).coerceIn(0, screenshot.width - 1)
    val cropTop = (topOnScreen - sourcePosition[1]).coerceIn(0, screenshot.height - 1)
    val cropWidth = width.coerceAtMost(screenshot.width - cropLeft)
    val cropHeight = height.coerceAtMost(screenshot.height - cropTop)
    if (cropWidth <= 0 || cropHeight <= 0) {
        screenshot.recycle()
        return null
    }

    val crop = Bitmap.createBitmap(screenshot, cropLeft, cropTop, cropWidth, cropHeight)
    screenshot.recycle()
    val small = Bitmap.createScaledBitmap(crop, (cropWidth / 4).coerceAtLeast(1), (cropHeight / 4).coerceAtLeast(1), true)
    crop.recycle()

    var renderScript: RenderScript? = null
    var input: Allocation? = null
    var output: Allocation? = null
    var blur: ScriptIntrinsicBlur? = null
    var retainSmall = true
    return try {
        renderScript = RenderScript.create(context)
        input = Allocation.createFromBitmap(renderScript, small)
        output = Allocation.createTyped(renderScript, input.type)
        val blurScript = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript)).apply {
            // The backdrop is downscaled to 25%, so a large radius becomes very
            // pronounced when stretched back into the popup.
            setRadius(context.resources.getDimension(R.dimen.backdrop_blur_radius) / context.resources.displayMetrics.density)
            setInput(input)
        }
        blur = blurScript
        blurScript.forEach(output)
        val blurred = Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
        output?.copyTo(blurred)
        retainSmall = false
        blurred
    } catch (_: Exception) {
        small
    } finally {
        blur?.destroy()
        output?.destroy()
        input?.destroy()
        renderScript?.destroy()
        if (!retainSmall) small.recycle()
    }
}
