package io.jostx.hy300launcher.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.jostx.hy300launcher.R
import io.jostx.hy300launcher.model.TvApp

object LauncherPalette {
    val background = Color.rgb(17, 19, 24)
    val surface = Color.rgb(34, 38, 47)
    val accent = Color.rgb(100, 170, 255)
    val text = Color.rgb(245, 247, 251)
    val muted = Color.rgb(160, 168, 181)
    val actionSurface = Color.rgb(38, 42, 52)
    val tooltip = Color.rgb(44, 49, 60)
    val shelf = Color.rgb(31, 35, 43)
    val shelfBorder = Color.rgb(52, 58, 69)
    val power = Color.rgb(255, 83, 92)
}

fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Activity.rounded(color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        stroke?.let { setStroke(dp(2), it) }
    }

fun Activity.circle(color: Int, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        stroke?.let { setStroke(dp(2), it) }
    }

fun Activity.launcherLabel(
    value: String,
    sizeSp: Int,
    color: Int,
    bold: Boolean = false,
): TextView = TextView(this).apply {
    text = value
    textSize = sizeSp.toFloat()
    setTextColor(color)
    isFocusable = false
    isFocusableInTouchMode = false
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

class ActionIconButton(
    activity: Activity,
    title: String,
    iconRes: Int,
    iconTint: Int = LauncherPalette.text,
    private val onTooltipFocus: (View, String, Boolean) -> Unit,
) : FrameLayout(activity) {
    private var actionTitle = title

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = actionTitle
        background = activity.circle(LauncherPalette.actionSurface)
        addView(ImageView(activity).apply {
            setImageResource(iconRes)
            setColorFilter(iconTint)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(activity.dp(20), activity.dp(20), Gravity.CENTER))
        layoutParams = LayoutParams(activity.dp(40), activity.dp(40))
        setOnFocusChangeListener { view, focused ->
            background = activity.circle(
                LauncherPalette.surface,
                if (focused) LauncherPalette.accent else null,
            )
            onTooltipFocus(view, actionTitle, focused)
        }
    }

    fun setTitle(title: String) {
        actionTitle = title
        contentDescription = title
        if (hasFocus()) onTooltipFocus(this, title, true)
    }
}

class LauncherAppTile(
    activity: Activity,
    app: TvApp,
    isFavorite: Boolean,
    favoriteIndex: Int?,
    tileWidthDp: Int,
    showSelectionState: Boolean = false,
    isEditingFavorites: () -> Boolean,
    onSelect: (TvApp) -> Unit,
    onLongSelect: (TvApp) -> Boolean,
    onReorder: (Int) -> Boolean,
    reserveTooltipSpace: Boolean = true,
    scaleOnFocus: Boolean = true,
    onFocusChanged: ((View, View, String, Boolean) -> Unit)? = null,
) : LinearLayout(activity) {
    private val artworkFrame: AspectRatioFrame

    init {
        orientation = VERTICAL
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        isFocusable = true
        isFocusableInTouchMode = true
        clipChildren = false
        clipToPadding = false
        contentDescription = when {
            showSelectionState && isFavorite -> "${app.label}, visible en Inicio"
            showSelectionState -> "${app.label}, no visible en Inicio"
            isFavorite -> "${app.label}, favorito"
            else -> app.label
        }
        tag = app.packageName

        artworkFrame = AspectRatioFrame(activity).apply {
            background = activity.rounded(tileColor(app.packageName), 10)
            foreground = activity.rounded(Color.TRANSPARENT, 10)
            clipToOutline = true
            elevation = activity.dp(1).toFloat()
            val appBanner = try {
                @Suppress("DEPRECATION")
                activity.packageManager.getApplicationBanner(app.packageName)
            } catch (_: Exception) {
                null
            }
            if (appBanner != null) {
                addView(ImageView(activity).apply {
                    setImageDrawable(appBanner)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Banner de ${app.label}"
                }, LayoutParams(-1, -1))
            } else {
                val icon = ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    if (app.isInputShortcut) {
                        setImageResource(R.drawable.ic_hdmi)
                        setColorFilter(LauncherPalette.text)
                    } else {
                        setPadding(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(6))
                        try {
                            @Suppress("DEPRECATION")
                            setImageDrawable(activity.packageManager.getApplicationIcon(app.packageName))
                        } catch (_: Exception) {
                            setImageResource(android.R.drawable.sym_def_app_icon)
                        }
                    }
                }
                val iconSize = if (app.isInputShortcut) 40 else 64
                addView(icon, FrameLayout.LayoutParams(activity.dp(iconSize), activity.dp(iconSize), Gravity.CENTER))
            }
            if (showSelectionState) {
                addView(TextView(activity).apply {
                    text = if (isFavorite) "✓" else "+"
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background = activity.circle(
                        if (isFavorite) LauncherPalette.accent else Color.rgb(72, 78, 91),
                    )
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, FrameLayout.LayoutParams(activity.dp(26), activity.dp(26), Gravity.TOP or Gravity.END).apply {
                    topMargin = activity.dp(7)
                    rightMargin = activity.dp(7)
                })
            }
        }
        addView(artworkFrame, LayoutParams(-1, activity.dp(tileWidthDp * 9 / 16)))
        if (reserveTooltipSpace) {
            addView(View(activity), LayoutParams(-1, activity.dp(30)).apply { topMargin = activity.dp(4) })
        }

        setOnFocusChangeListener { view, focused ->
            if (scaleOnFocus) {
                view.animate().scaleX(if (focused) 1.045f else 1f)
                    .scaleY(if (focused) 1.045f else 1f).setDuration(110).start()
            }
            onFocusChanged?.invoke(view, artworkFrame, app.label, focused)
            artworkFrame.background = activity.rounded(tileColor(app.packageName), 10)
            artworkFrame.foreground = activity.rounded(
                Color.TRANSPARENT,
                10,
                if (focused) LauncherPalette.accent else null,
            )
            artworkFrame.elevation = activity.dp(if (focused) 5 else 1).toFloat()
        }
        setOnClickListener { onSelect(app) }
        setOnLongClickListener { onLongSelect(app) }

        if (isFavorite && favoriteIndex != null) {
            setOnKeyListener { _, keyCode, event ->
                if (!isEditingFavorites() || event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val direction = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                    else -> return@setOnKeyListener false
                }
                onReorder(direction)
            }
        }
    }
}

private class AspectRatioFrame(activity: Activity) : FrameLayout(activity) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = width * 9 / 16
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }
}

class FocusTooltipOverlay(
    private val activity: Activity,
    private val container: FrameLayout,
) {
    private val tooltip = activity.launcherLabel("", 13, Color.WHITE).apply {
        setPadding(activity.dp(14), activity.dp(8), activity.dp(14), activity.dp(8))
        background = activity.rounded(LauncherPalette.tooltip, 9)
        elevation = activity.dp(5).toFloat()
        visibility = View.GONE
    }

    init {
        container.addView(tooltip, FrameLayout.LayoutParams(-2, -2))
    }

    fun showFor(anchor: View, title: String, positionView: View = anchor) {
        anchor.post {
            if (!anchor.hasFocus()) return@post
            container.bringChildToFront(tooltip)
            val position = IntArray(2)
            val containerPosition = IntArray(2)
            positionView.getLocationOnScreen(position)
            container.getLocationOnScreen(containerPosition)
            tooltip.text = title
            tooltip.visibility = View.VISIBLE
            tooltip.measure(
                View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(container.height, View.MeasureSpec.AT_MOST),
            )
            val left = (position[0] - containerPosition[0] + positionView.width / 2 - tooltip.measuredWidth / 2)
                .coerceIn(activity.dp(8), (container.width - tooltip.measuredWidth - activity.dp(8)).coerceAtLeast(activity.dp(8)))
            val top = (position[1] - containerPosition[1] + positionView.height + activity.dp(7))
                .coerceAtMost(container.height - tooltip.measuredHeight - activity.dp(8))
            val params = tooltip.layoutParams as FrameLayout.LayoutParams
            params.leftMargin = left
            params.topMargin = top.coerceAtLeast(activity.dp(8))
            tooltip.layoutParams = params
        }
    }

    fun showBelow(anchor: View, title: String, boundary: View, positionView: View = anchor) {
        anchor.post {
            if (!anchor.hasFocus()) return@post
            container.bringChildToFront(tooltip)
            val anchorPosition = IntArray(2)
            val boundaryPosition = IntArray(2)
            val containerPosition = IntArray(2)
            positionView.getLocationOnScreen(anchorPosition)
            boundary.getLocationOnScreen(boundaryPosition)
            container.getLocationOnScreen(containerPosition)
            tooltip.text = title
            tooltip.visibility = View.VISIBLE
            tooltip.measure(
                View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(container.height, View.MeasureSpec.AT_MOST),
            )
            val left = (anchorPosition[0] - containerPosition[0] + positionView.width / 2 - tooltip.measuredWidth / 2)
                .coerceIn(activity.dp(8), (container.width - tooltip.measuredWidth - activity.dp(8)).coerceAtLeast(activity.dp(8)))
            val belowBoundary = boundaryPosition[1] - containerPosition[1] + boundary.height + activity.dp(7)
            val top = belowBoundary.coerceAtMost(container.height - tooltip.measuredHeight - activity.dp(8))
            val params = tooltip.layoutParams as FrameLayout.LayoutParams
            params.leftMargin = left
            params.topMargin = top.coerceAtLeast(activity.dp(8))
            tooltip.layoutParams = params
        }
    }

    fun hide() {
        tooltip.visibility = View.GONE
    }
}

private fun tileColor(key: String): Int {
    val identity = key.lowercase()
    when {
        "aurora" in identity -> return Color.rgb(57, 48, 117)
        "smarttube" in identity -> return Color.rgb(36, 39, 47)
        "youtube" in identity -> return Color.rgb(59, 31, 37)
    }
    val colors = intArrayOf(
        Color.rgb(38, 52, 72), Color.rgb(49, 42, 67), Color.rgb(35, 56, 55),
        Color.rgb(67, 48, 34), Color.rgb(52, 47, 44), Color.rgb(38, 57, 68),
    )
    return colors[(key.hashCode() and Int.MAX_VALUE) % colors.size]
}
