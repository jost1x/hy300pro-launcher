package io.jostx.hy300launcher.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.jostx.hy300launcher.R
import io.jostx.hy300launcher.model.TvApp

fun Activity.uiColor(resourceId: Int): Int = resources.getColor(resourceId, theme)

fun Activity.uiDimen(resourceId: Int): Int = (resources.getDimension(resourceId) / resources.displayMetrics.density).toInt()

fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Activity.rounded(
    color: Int,
    radiusDp: Int,
    stroke: Int? = null,
    strokeWidthDp: Int? = null,
): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        stroke?.let { setStroke(dp(strokeWidthDp ?: uiDimen(R.dimen.space_2)), it) }
    }

fun Activity.circle(color: Int, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        stroke?.let { setStroke(dp(uiDimen(R.dimen.space_2)), it) }
    }

fun Activity.glassCircle(fillColor: Int, edgeColor: Int, strokeWidthDp: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fillColor)
        setStroke(dp(strokeWidthDp ?: uiDimen(R.dimen.space_1)), edgeColor)
    }

fun Activity.glassRounded(
    fillColor: Int,
    radiusDp: Int,
    edgeColor: Int,
    strokeWidthDp: Int? = null,
): GradientDrawable {
    return GradientDrawable().apply {
        setColor(fillColor)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(strokeWidthDp ?: uiDimen(R.dimen.space_1)), edgeColor)
    }
}

fun Activity.launcherLabel(
    value: String,
    appearance: Int,
    bold: Boolean = false,
): TextView = TextView(this).apply {
    text = value
    setTextAppearance(appearance)
    isFocusable = false
    isFocusableInTouchMode = false
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

class ActionIconButton(
    private val activity: Activity,
    title: String,
    iconRes: Int,
    iconTint: Int? = null,
    private val onFocusChanged: ((Boolean) -> Unit)? = null,
) : FrameLayout(activity) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = title
        background = activity.circle(Color.TRANSPARENT)
        addView(ImageView(activity).apply {
            setImageResource(iconRes)
            setColorFilter(iconTint ?: activity.uiColor(R.color.launcher_text_primary))
            contentDescription = null
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(
            activity.dp(activity.uiDimen(R.dimen.icon_large)),
            activity.dp(activity.uiDimen(R.dimen.icon_large)),
            Gravity.CENTER,
        ))
        layoutParams = LayoutParams(-2, activity.dp(activity.uiDimen(R.dimen.action_target)))
        setOnFocusChangeListener { view, focused ->
            background = if (focused) {
                activity.glassCircle(
                    activity.uiColor(R.color.launcher_glass_focus_fill),
                    activity.uiColor(R.color.launcher_glass_focus_edge),
                    activity.uiDimen(R.dimen.space_1),
                )
            } else {
                activity.circle(Color.TRANSPARENT)
            }
            view.animate().cancel()
            view.animate()
                .scaleX(if (focused) 1.03f else 1f)
                .scaleY(if (focused) 1.03f else 1f)
                .setDuration(activity.resources.getInteger(R.integer.motion_focus).toLong())
                .start()
            view.elevation = activity.dp(if (focused) activity.uiDimen(R.dimen.elevation_action_focus) else 0).toFloat()
            onFocusChanged?.invoke(focused)
        }
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
            background = activity.rounded(tileColor(activity, app.packageName), activity.uiDimen(R.dimen.radius_tile))
            foreground = activity.rounded(Color.TRANSPARENT, activity.uiDimen(R.dimen.radius_tile))
            clipToOutline = true
            elevation = activity.dp(activity.uiDimen(R.dimen.space_1)).toFloat()
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
                        setColorFilter(activity.uiColor(R.color.launcher_text_primary))
                    } else {
                        setPadding(activity.dp(activity.uiDimen(R.dimen.space_6)), activity.dp(activity.uiDimen(R.dimen.space_6)), activity.dp(activity.uiDimen(R.dimen.space_6)), activity.dp(activity.uiDimen(R.dimen.space_6)))
                        try {
                            @Suppress("DEPRECATION")
                            setImageDrawable(activity.packageManager.getApplicationIcon(app.packageName))
                        } catch (_: Exception) {
                            setImageResource(android.R.drawable.sym_def_app_icon)
                        }
                    }
                }
                val iconSize = activity.uiDimen(if (app.isInputShortcut) R.dimen.icon_input_shortcut else R.dimen.icon_tile_app)
                addView(icon, FrameLayout.LayoutParams(activity.dp(iconSize), activity.dp(iconSize), Gravity.CENTER))
            }
            if (showSelectionState) {
                addView(TextView(activity).apply {
                    text = if (isFavorite) "✓" else "+"
                    setTextAppearance(R.style.LauncherTextBody)
                    setTextColor(activity.uiColor(R.color.launcher_text_primary))
                    gravity = Gravity.CENTER
                    background = activity.circle(
                        if (isFavorite) activity.uiColor(R.color.launcher_accent) else activity.uiColor(R.color.launcher_badge_idle),
                    )
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, FrameLayout.LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_26)), activity.dp(activity.uiDimen(R.dimen.space_26)), Gravity.TOP or Gravity.END).apply {
                    topMargin = activity.dp(activity.uiDimen(R.dimen.space_7))
                    rightMargin = activity.dp(activity.uiDimen(R.dimen.space_7))
                })
            }
        }
        addView(artworkFrame, LayoutParams(-1, activity.dp(tileWidthDp * 9 / 16)))
        if (reserveTooltipSpace) {
            addView(View(activity), LayoutParams(-1, activity.dp(activity.uiDimen(R.dimen.space_30))).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_4)) })
        }

        setOnFocusChangeListener { view, focused ->
            if (scaleOnFocus) {
                view.animate().cancel()
                view.animate().scaleX(if (focused) 1.045f else 1f)
                    .scaleY(if (focused) 1.045f else 1f)
                    .setDuration(activity.resources.getInteger(R.integer.motion_focus).toLong()).start()
            }
            onFocusChanged?.invoke(view, artworkFrame, app.label, focused)
            artworkFrame.background = activity.rounded(tileColor(activity, app.packageName), activity.uiDimen(R.dimen.radius_tile))
            artworkFrame.foreground = activity.rounded(
                Color.TRANSPARENT,
                activity.uiDimen(R.dimen.radius_tile),
                if (focused) activity.uiColor(R.color.launcher_accent) else null,
                if (focused) activity.uiDimen(R.dimen.focus_ring) else null,
            )
            artworkFrame.elevation = activity.dp(activity.uiDimen(if (focused) R.dimen.elevation_focus else R.dimen.elevation_low)).toFloat()
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
    private val handler = Handler(Looper.getMainLooper())
    private var autoHide: Runnable? = null
    private var animationToken = 0
    private val tooltip = activity.launcherLabel("", R.style.LauncherTextCaption).apply {
        setPadding(activity.dp(activity.uiDimen(R.dimen.space_14)), activity.dp(activity.uiDimen(R.dimen.space_8)), activity.dp(activity.uiDimen(R.dimen.space_14)), activity.dp(activity.uiDimen(R.dimen.space_8)))
        background = activity.glassRounded(
            activity.uiColor(R.color.launcher_glass_tooltip),
            activity.uiDimen(R.dimen.radius_small),
            activity.uiColor(R.color.launcher_glass_toolbar_edge),
        )
        elevation = activity.dp(activity.uiDimen(R.dimen.space_5)).toFloat()
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
            prepareTooltip(title)
            val left = (position[0] - containerPosition[0] + positionView.width / 2 - tooltip.measuredWidth / 2)
                .coerceIn(activity.dp(activity.uiDimen(R.dimen.space_8)), (container.width - tooltip.measuredWidth - activity.dp(activity.uiDimen(R.dimen.space_8))).coerceAtLeast(activity.dp(activity.uiDimen(R.dimen.space_8))))
            val top = (position[1] - containerPosition[1] + positionView.height + activity.dp(activity.uiDimen(R.dimen.space_7)))
                .coerceAtMost(container.height - tooltip.measuredHeight - activity.dp(activity.uiDimen(R.dimen.space_8)))
            val params = tooltip.layoutParams as FrameLayout.LayoutParams
            params.leftMargin = left
            params.topMargin = top.coerceAtLeast(activity.dp(activity.uiDimen(R.dimen.space_8)))
            tooltip.layoutParams = params
            finishShowingTooltip()
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
            prepareTooltip(title)
            val left = (anchorPosition[0] - containerPosition[0] + positionView.width / 2 - tooltip.measuredWidth / 2)
                .coerceIn(activity.dp(activity.uiDimen(R.dimen.space_8)), (container.width - tooltip.measuredWidth - activity.dp(activity.uiDimen(R.dimen.space_8))).coerceAtLeast(activity.dp(activity.uiDimen(R.dimen.space_8))))
            val belowBoundary = boundaryPosition[1] - containerPosition[1] + boundary.height + activity.dp(activity.uiDimen(R.dimen.space_7))
            val top = belowBoundary.coerceAtMost(container.height - tooltip.measuredHeight - activity.dp(activity.uiDimen(R.dimen.space_8)))
            val params = tooltip.layoutParams as FrameLayout.LayoutParams
            params.leftMargin = left
            params.topMargin = top.coerceAtLeast(activity.dp(activity.uiDimen(R.dimen.space_8)))
            tooltip.layoutParams = params
            finishShowingTooltip()
        }
    }

    fun hide() {
        cancelAutoHide()
        animateTooltipOut()
    }

    private fun prepareTooltip(title: String) {
        cancelAutoHide()
        animationToken++
        tooltip.animate().cancel()
        if (tooltip.visibility != View.VISIBLE) {
            tooltip.alpha = 0f
            tooltip.translationY = activity.dp(activity.uiDimen(R.dimen.space_4)).toFloat()
            tooltip.visibility = View.VISIBLE
        }
        tooltip.text = title
        tooltip.measure(
            View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(container.height, View.MeasureSpec.AT_MOST),
        )
    }

    private fun finishShowingTooltip() {
        animationToken++
        tooltip.animate().cancel()
        tooltip.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(activity.resources.getInteger(R.integer.motion_tooltip_in).toLong())
            .start()

        val dismiss = Runnable {
            autoHide = null
            animateTooltipOut()
        }
        autoHide = dismiss
        handler.postDelayed(dismiss, activity.resources.getInteger(R.integer.tooltip_timeout).toLong())
    }

    private fun animateTooltipOut() {
        if (tooltip.visibility != View.VISIBLE) return
        val token = ++animationToken
        tooltip.animate().cancel()
        tooltip.animate()
            .alpha(0f)
            .translationY(activity.dp(activity.uiDimen(R.dimen.space_4)).toFloat())
            .setDuration(activity.resources.getInteger(R.integer.motion_tooltip_out).toLong())
            .withEndAction {
                if (token == animationToken) tooltip.visibility = View.GONE
            }
            .start()
    }

    private fun cancelAutoHide() {
        autoHide?.let(handler::removeCallbacks)
        autoHide = null
    }
}

private fun tileColor(activity: Activity, key: String): Int {
    val identity = key.lowercase()
    when {
        "aurora" in identity -> return activity.uiColor(R.color.launcher_tile_aurora)
        "smarttube" in identity -> return activity.uiColor(R.color.launcher_tile_smarttube)
        "youtube" in identity -> return activity.uiColor(R.color.launcher_tile_youtube)
    }
    val colors = intArrayOf(
        R.color.launcher_tile_1, R.color.launcher_tile_2, R.color.launcher_tile_3,
        R.color.launcher_tile_4, R.color.launcher_tile_5, R.color.launcher_tile_6,
    )
    return activity.uiColor(colors[(key.hashCode() and Int.MAX_VALUE) % colors.size])
}
