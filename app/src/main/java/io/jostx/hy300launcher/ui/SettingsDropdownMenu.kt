package io.jostx.hy300launcher.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import io.jostx.hy300launcher.R

/** D-pad-friendly settings menu anchored to the launcher header button. */
class SettingsDropdownMenu(
    private val activity: Activity,
    private val onLauncherSettings: () -> Unit,
    private val onImageCorrection: () -> Unit,
    private val onProjectorSettings: () -> Unit,
    private val onAndroidSettings: () -> Unit,
) {
    private var popup: PopupWindow? = null

    val isShowing: Boolean
        get() = popup?.isShowing == true

    fun show(anchor: View) {
        if (popup?.isShowing == true) return

        val menuContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(activity.uiDimen(R.dimen.space_8)), activity.dp(activity.uiDimen(R.dimen.space_8)), activity.dp(activity.uiDimen(R.dimen.space_8)), activity.dp(activity.uiDimen(R.dimen.space_8)))
        }

        val rows = listOf(
            SettingsItem(
                R.drawable.ic_sliders,
                "Ajustes del launcher",
                onLauncherSettings,
            ),
            SettingsItem(
                R.drawable.ic_tv,
                "Imagen del proyector",
                onImageCorrection,
            ),
            SettingsItem(
                R.drawable.ic_monitor_play,
                "Ajustes del proyector",
                onProjectorSettings,
            ),
            SettingsItem(
                R.drawable.ic_settings,
                "Ajustes Android",
                onAndroidSettings,
            ),
        ).map { item -> createRow(item) }

        rows.forEachIndexed { index, row ->
            menuContent.addView(
                row,
                LinearLayout.LayoutParams(-1, activity.dp(activity.uiDimen(R.dimen.space_58))).apply {
                    if (index > 0) topMargin = activity.dp(activity.uiDimen(R.dimen.space_3))
                },
            )
        }

        val width = activity.dp(activity.uiDimen(R.dimen.space_260))
        menuContent.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val height = menuContent.measuredHeight
        val anchorPosition = IntArray(2)
        anchor.getLocationOnScreen(anchorPosition)
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val minimumLeft = activity.dp(activity.uiDimen(R.dimen.space_8))
        val maximumLeft = (screenWidth - width - minimumLeft).coerceAtLeast(minimumLeft)
        val left = (anchorPosition[0] + anchor.width - width).coerceIn(minimumLeft, maximumLeft)
        val top = anchorPosition[1] + anchor.height + activity.dp(activity.uiDimen(R.dimen.space_8))

        val decor = activity.window.decorView
        val backdropBitmap = captureBlurredBackdrop(activity, decor, left, top, width, height)
        val backdrop = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            setImageBitmap(backdropBitmap)
            background = activity.rounded(Color.TRANSPARENT, (activity.uiDimen(R.dimen.radius_popup_inner) + activity.uiDimen(R.dimen.popup_inset)))
            clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val glassOverlay = View(activity).apply {
            // Outer corner = focused row corner (11dp) + menu inset (8dp).
            background = activity.glassRounded(
                activity.uiColor(R.color.launcher_glass_menu),
                activity.uiDimen(R.dimen.radius_popup_inner) + activity.uiDimen(R.dimen.popup_inset),
                activity.uiColor(R.color.launcher_glass_menu_edge),
            )
            isClickable = false
            isFocusable = false
        }
        val menu = FrameLayout(activity).apply {
            background = activity.rounded(Color.TRANSPARENT, (activity.uiDimen(R.dimen.radius_popup_inner) + activity.uiDimen(R.dimen.popup_inset)))
            clipToOutline = true
            clipChildren = true
            addView(backdrop, FrameLayout.LayoutParams(-1, -1))
            addView(glassOverlay, FrameLayout.LayoutParams(-1, -1))
            addView(menuContent, FrameLayout.LayoutParams(-1, -1))
        }

        val window = PopupWindow(menu, width, height, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = activity.dp(activity.uiDimen(R.dimen.elevation_menu)).toFloat()
            setOnDismissListener {
                backdrop.setImageDrawable(null)
                backdropBitmap?.takeIf { !it.isRecycled }?.recycle()
                if (anchor.isShown) anchor.requestFocus()
            }
        }
        popup = window

        window.showAsDropDown(anchor, left - anchorPosition[0], activity.dp(activity.uiDimen(R.dimen.space_8)))
        rows.firstOrNull()?.post { rows.firstOrNull()?.requestFocus() }
    }

    fun dismiss() {
        popup?.dismiss()
    }

    private fun createRow(item: SettingsItem) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(activity.dp(activity.uiDimen(R.dimen.space_14)), 0, activity.dp(activity.uiDimen(R.dimen.space_10)), 0)
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = item.title
        background = activity.rounded(Color.TRANSPARENT, activity.uiDimen(R.dimen.radius_popup_inner))

        addView(FrameLayout(activity).apply {
            addView(ImageView(activity).apply {
                setImageResource(item.icon)
                setColorFilter(activity.uiColor(R.color.launcher_text_primary))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_22)), activity.dp(activity.uiDimen(R.dimen.space_22)), Gravity.CENTER))
        }, LinearLayout.LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_28)), activity.dp(activity.uiDimen(R.dimen.space_34))).apply {
            rightMargin = activity.dp(activity.uiDimen(R.dimen.space_12))
        })

        addView(
            activity.launcherLabel(item.title, R.style.LauncherTextBody, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f),
        )

        setOnFocusChangeListener { view, focused ->
            view.background = if (focused) {
                activity.glassRounded(
                    activity.uiColor(R.color.launcher_glass_focus_fill),
                    activity.uiDimen(R.dimen.radius_popup_inner),
                    activity.uiColor(R.color.launcher_glass_focus_edge),
                    strokeWidthDp = activity.uiDimen(R.dimen.space_1),
                )
            } else {
                activity.rounded(Color.TRANSPARENT, activity.uiDimen(R.dimen.radius_popup_inner))
            }
        }
        setOnClickListener {
            popup?.dismiss()
            item.onSelect()
        }
    }

    private data class SettingsItem(
        val icon: Int,
        val title: String,
        val onSelect: () -> Unit,
    )
}
