package io.jostx.hy300launcher.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import io.jostx.hy300launcher.R

/** D-pad-friendly settings menu anchored to the launcher header button. */
class SettingsDropdownMenu(
    private val activity: Activity,
    private val onLauncherSettings: () -> Unit,
    private val onProjectorSettings: () -> Unit,
    private val onAndroidSettings: () -> Unit,
) {
    private var popup: PopupWindow? = null

    val isShowing: Boolean
        get() = popup?.isShowing == true

    fun show(anchor: View) {
        if (popup?.isShowing == true) return

        val menu = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
            // Outer corner = focused row corner (11dp) + menu inset (8dp).
            background = activity.rounded(LauncherPalette.surface, 19, LauncherPalette.shelfBorder)
            elevation = activity.dp(12).toFloat()
        }

        val rows = listOf(
            SettingsItem(
                R.drawable.ic_sliders,
                "Ajustes del launcher",
                onLauncherSettings,
            ),
            SettingsItem(
                R.drawable.ic_tv,
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
            menu.addView(
                row,
                LinearLayout.LayoutParams(-1, activity.dp(58)).apply {
                    if (index > 0) topMargin = activity.dp(3)
                },
            )
        }

        val width = activity.dp(260)
        val window = PopupWindow(menu, width, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = activity.dp(12).toFloat()
            setOnDismissListener {
                if (anchor.isShown) anchor.requestFocus()
            }
        }
        popup = window

        val anchorPosition = IntArray(2)
        anchor.getLocationOnScreen(anchorPosition)
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val minimumLeft = activity.dp(8)
        val maximumLeft = (screenWidth - width - minimumLeft).coerceAtLeast(minimumLeft)
        val left = (anchorPosition[0] + anchor.width - width).coerceIn(minimumLeft, maximumLeft)
        window.showAsDropDown(anchor, left - anchorPosition[0], activity.dp(8))
        rows.firstOrNull()?.post { rows.firstOrNull()?.requestFocus() }
    }

    fun dismiss() {
        popup?.dismiss()
    }

    private fun createRow(item: SettingsItem) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(activity.dp(14), 0, activity.dp(10), 0)
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = item.title
        background = activity.rounded(Color.TRANSPARENT, 11)

        addView(FrameLayout(activity).apply {
            addView(ImageView(activity).apply {
                setImageResource(item.icon)
                setColorFilter(LauncherPalette.text)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(activity.dp(22), activity.dp(22), Gravity.CENTER))
        }, LinearLayout.LayoutParams(activity.dp(28), activity.dp(34)).apply {
            rightMargin = activity.dp(12)
        })

        addView(
            activity.launcherLabel(item.title, 15, LauncherPalette.text, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f),
        )

        setOnFocusChangeListener { view, focused ->
            view.background = activity.rounded(
                if (focused) LauncherPalette.actionSurface else Color.TRANSPARENT,
                11,
                if (focused) LauncherPalette.accent else null,
            )
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
