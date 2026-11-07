package io.jostx.hy300launcher.ui

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.jostx.hy300launcher.R
import io.jostx.hy300launcher.data.HdmiPlayer

/** Full-screen, D-pad-friendly settings page for launcher-specific options. */
class LauncherSettingsOverlay(
    private val activity: Activity,
    selectedAppCount: Int,
    private val onManageHomeApps: () -> Unit,
    private val onChooseDefaultLauncher: () -> Unit,
    private val onAbout: () -> Unit,
    selectedHdmiPlayer: HdmiPlayer,
    private val onSelectHdmiPlayer: (HdmiPlayer) -> Unit,
) : LinearLayout(activity) {
    private val rows = mutableListOf<View>()

    init {
        orientation = VERTICAL
        setPadding(activity.dp(activity.uiDimen(R.dimen.space_48)), activity.dp(activity.uiDimen(R.dimen.space_27)), activity.dp(activity.uiDimen(R.dimen.space_48)), activity.dp(activity.uiDimen(R.dimen.space_27)))
        setBackgroundResource(R.drawable.launcher_background)
        isFocusable = false
        isFocusableInTouchMode = false

        val header = LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = VERTICAL }
        heading.addView(activity.launcherLabel("Ajustes del launcher", R.style.LauncherTextHeading, bold = true))
        header.addView(heading, LayoutParams(0, -2, 1f))
        addView(header, LayoutParams(-1, activity.dp(activity.uiDimen(R.dimen.space_58))))

        val scroll = ScrollView(activity).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = FOCUS_AFTER_DESCENDANTS
            clipChildren = false
            clipToPadding = false
        }
        val content = LinearLayout(activity).apply {
            orientation = VERTICAL
            setPadding(activity.dp(activity.uiDimen(R.dimen.space_12)), 0, activity.dp(activity.uiDimen(R.dimen.space_12)), 0)
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(content, LayoutParams(-1, -2))

        content.addView(
            activity.launcherLabel("Pantalla de inicio", R.style.LauncherTextCaption, bold = true),
            LayoutParams(-1, -2).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_24)); bottomMargin = activity.dp(activity.uiDimen(R.dimen.space_10)) },
        )
        content.addView(
            settingRow(
                R.drawable.ic_layout_grid,
                "Elegir apps de Inicio",
                "$selectedAppCount de 8 apps seleccionadas",
                onManageHomeApps,
            ),
        )
        content.addView(
            settingRow(
                R.drawable.ic_tv,
                "Launcher predeterminado",
                "Elige qué app se abre al pulsar Inicio",
                onChooseDefaultLauncher,
            ),
            LayoutParams(-1, -2).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_10)) },
        )

        content.addView(
            activity.launcherLabel("Entrada HDMI", R.style.LauncherTextCaption, bold = true),
            LayoutParams(-1, -2).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_26)); bottomMargin = activity.dp(activity.uiDimen(R.dimen.space_10)) },
        )
        var activeHdmiPlayer = selectedHdmiPlayer
        lateinit var hdmiPlayerDescription: TextView
        content.addView(
            settingRow(
                R.drawable.ic_monitor_play,
                "Reproductor HDMI",
                "${activeHdmiPlayer.label} · selección activa",
                onClick = {
                    activeHdmiPlayer = if (activeHdmiPlayer == HdmiPlayer.LAUNCHER) HdmiPlayer.OEM else HdmiPlayer.LAUNCHER
                    onSelectHdmiPlayer(activeHdmiPlayer)
                    hdmiPlayerDescription.text = "${activeHdmiPlayer.label} · selección activa"
                },
                onDescriptionViewCreated = { hdmiPlayerDescription = it },
            ),
            LayoutParams(-1, -2).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_10)) },
        )

        content.addView(
            activity.launcherLabel("Información", R.style.LauncherTextCaption, bold = true),
            LayoutParams(-1, -2).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_26)); bottomMargin = activity.dp(activity.uiDimen(R.dimen.space_10)) },
        )
        content.addView(
            settingRow(
                R.drawable.ic_info,
                "Acerca del launcher",
                "Versión y detalles de la aplicación",
                onAbout,
            ),
        )

        addView(scroll, LayoutParams(-1, 0, 1f).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_8)) })
    }

    fun focusInitialItem() {
        post { rows.firstOrNull()?.requestFocus() }
    }

    private fun settingRow(
        iconRes: Int,
        title: String,
        description: String,
        onClick: () -> Unit,
        onDescriptionViewCreated: ((TextView) -> Unit)? = null,
    ) =
        LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(activity.uiDimen(R.dimen.space_20)), activity.dp(activity.uiDimen(R.dimen.space_14)), activity.dp(activity.uiDimen(R.dimen.space_22)), activity.dp(activity.uiDimen(R.dimen.space_14)))
            isFocusable = true
            isFocusableInTouchMode = true
            background = activity.rounded(activity.uiColor(R.color.launcher_surface), activity.uiDimen(R.dimen.radius_surface))
            addView(FrameLayout(activity).apply {
                addView(ImageView(activity).apply {
                    setImageResource(iconRes)
                    setColorFilter(activity.uiColor(R.color.launcher_text_primary))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, FrameLayout.LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_25)), activity.dp(activity.uiDimen(R.dimen.space_25)), Gravity.CENTER))
            }, LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_44)), activity.dp(activity.uiDimen(R.dimen.space_44))).apply { rightMargin = activity.dp(activity.uiDimen(R.dimen.space_18)) })

            val descriptionView = activity.launcherLabel(description, R.style.LauncherTextCaption).apply {
                maxLines = 2
            }
            onDescriptionViewCreated?.invoke(descriptionView)
            val text = LinearLayout(activity).apply {
                orientation = VERTICAL
                addView(activity.launcherLabel(title, R.style.LauncherTextBody, bold = true))
                addView(descriptionView, LayoutParams(-1, -2).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_3)) })
            }
            addView(text, LayoutParams(0, -2, 1f))
            addView(activity.launcherLabel("›", R.style.LauncherTextTitle), LayoutParams(-2, -2).apply {
                leftMargin = activity.dp(activity.uiDimen(R.dimen.space_16))
            })

            setOnFocusChangeListener { view, focused ->
                view.animate().scaleX(if (focused) 1.025f else 1f)
                    .scaleY(if (focused) 1.025f else 1f).setDuration(activity.resources.getInteger(R.integer.motion_row_focus).toLong()).start()
                view.background = activity.rounded(
                    if (focused) activity.uiColor(R.color.launcher_glass_focus_panel) else activity.uiColor(R.color.launcher_surface),
                    activity.uiDimen(R.dimen.radius_action),
                    if (focused) activity.uiColor(R.color.launcher_glass_focus_edge) else null,
                    if (focused) activity.uiDimen(R.dimen.focus_ring_toolbar) else null,
                )
                view.elevation = if (focused) activity.dp(activity.uiDimen(R.dimen.elevation_focus)).toFloat() else 0f
            }
            setOnClickListener { onClick() }
            rows += this
        }.also { row ->
            row.layoutParams = LayoutParams(-1, activity.dp(activity.uiDimen(R.dimen.space_84)))
        }
}
