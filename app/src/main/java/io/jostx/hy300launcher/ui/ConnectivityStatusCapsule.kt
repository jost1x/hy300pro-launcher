package io.jostx.hy300launcher.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import io.jostx.hy300launcher.R

/** Compact, non-interactive status for Wi-Fi connectivity and Bluetooth power. */
class ConnectivityStatusCapsule(private val activity: Activity) : LinearLayout(activity) {
    private val wifiIcon = statusIcon(activity)
    private val bluetoothIcon = statusIcon(activity)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            activity.dp(activity.uiDimen(R.dimen.space_14)),
            activity.dp(activity.uiDimen(R.dimen.space_5)),
            activity.dp(activity.uiDimen(R.dimen.space_14)),
            activity.dp(activity.uiDimen(R.dimen.space_5)),
        )
        background = activity.glassRounded(
            activity.uiColor(R.color.launcher_glass_toolbar_fill),
            activity.uiDimen(R.dimen.radius_toolbar),
            activity.uiColor(R.color.launcher_glass_toolbar_edge),
        )
        clipToOutline = true
        elevation = activity.dp(activity.uiDimen(R.dimen.elevation_toolbar)).toFloat()
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

        val iconSize = activity.dp(activity.uiDimen(R.dimen.icon_status))
        addView(wifiIcon, LayoutParams(iconSize, iconSize).apply {
            rightMargin = activity.dp(activity.uiDimen(R.dimen.space_14))
        })
        addView(View(activity).apply {
            setBackgroundColor(activity.uiColor(R.color.launcher_glass_edge))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_1)), activity.dp(activity.uiDimen(R.dimen.space_22))).apply {
            rightMargin = activity.dp(activity.uiDimen(R.dimen.space_14))
        })
        addView(bluetoothIcon, LayoutParams(iconSize, iconSize))

        setStatus(wifiEnabled = false, wifiConnected = false, bluetoothEnabled = false)
    }

    fun setStatus(wifiEnabled: Boolean, wifiConnected: Boolean, bluetoothEnabled: Boolean) {
        val wifiDescription = when {
            wifiConnected -> "Wi-Fi conectado"
            wifiEnabled -> "Wi-Fi sin red"
            else -> "Wi-Fi apagado"
        }
        val bluetoothDescription = if (bluetoothEnabled) "Bluetooth activo" else "Bluetooth apagado"

        renderIcon(wifiIcon, if (wifiEnabled) R.drawable.ic_wifi else R.drawable.ic_wifi_off, wifiEnabled)
        renderIcon(bluetoothIcon, if (bluetoothEnabled) R.drawable.ic_bluetooth else R.drawable.ic_bluetooth_off, bluetoothEnabled)
        contentDescription = "$wifiDescription. $bluetoothDescription."
    }

    private fun renderIcon(icon: ImageView, drawable: Int, active: Boolean) {
        icon.setImageResource(drawable)
        icon.setColorFilter(activity.uiColor(if (active) R.color.launcher_text_primary else R.color.launcher_text_secondary))
    }

    private fun statusIcon(activity: Activity) = ImageView(activity).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}
