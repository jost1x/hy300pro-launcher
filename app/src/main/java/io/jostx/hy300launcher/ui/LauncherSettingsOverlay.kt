package io.jostx.hy300launcher.ui

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import io.jostx.hy300launcher.R

/** Full-screen, D-pad-friendly settings page for launcher-specific options. */
class LauncherSettingsOverlay(
    private val activity: Activity,
    selectedAppCount: Int,
    private val onManageHomeApps: () -> Unit,
    private val onChooseDefaultLauncher: () -> Unit,
    private val onAbout: () -> Unit,
) : LinearLayout(activity) {
    private val rows = mutableListOf<View>()

    init {
        orientation = VERTICAL
        setPadding(activity.dp(48), activity.dp(27), activity.dp(48), activity.dp(27))
        setBackgroundResource(R.drawable.launcher_background)
        isFocusable = false
        isFocusableInTouchMode = false

        val header = LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = VERTICAL }
        heading.addView(activity.launcherLabel("Ajustes del launcher", 26, LauncherPalette.text, bold = true))
        header.addView(heading, LayoutParams(0, -2, 1f))
        addView(header, LayoutParams(-1, activity.dp(58)))

        val scroll = ScrollView(activity).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = FOCUS_AFTER_DESCENDANTS
            clipChildren = false
            clipToPadding = false
        }
        val content = LinearLayout(activity).apply {
            orientation = VERTICAL
            setPadding(activity.dp(12), 0, activity.dp(12), 0)
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(content, LayoutParams(-1, -2))

        content.addView(
            activity.launcherLabel("Pantalla de inicio", 14, LauncherPalette.muted, bold = true),
            LayoutParams(-1, -2).apply { topMargin = activity.dp(24); bottomMargin = activity.dp(10) },
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
            LayoutParams(-1, -2).apply { topMargin = activity.dp(10) },
        )

        content.addView(
            activity.launcherLabel("Información", 14, LauncherPalette.muted, bold = true),
            LayoutParams(-1, -2).apply { topMargin = activity.dp(26); bottomMargin = activity.dp(10) },
        )
        content.addView(
            settingRow(
                R.drawable.ic_info,
                "Acerca del launcher",
                "Versión y detalles de la aplicación",
                onAbout,
            ),
        )

        addView(scroll, LayoutParams(-1, 0, 1f).apply { topMargin = activity.dp(8) })
    }

    fun focusInitialItem() {
        post { rows.firstOrNull()?.requestFocus() }
    }

    private fun settingRow(iconRes: Int, title: String, description: String, onClick: () -> Unit) =
        LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(20), activity.dp(14), activity.dp(22), activity.dp(14))
            isFocusable = true
            isFocusableInTouchMode = true
            background = activity.rounded(LauncherPalette.surface, 12)
            addView(FrameLayout(activity).apply {
                addView(ImageView(activity).apply {
                    setImageResource(iconRes)
                    setColorFilter(LauncherPalette.text)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, FrameLayout.LayoutParams(activity.dp(25), activity.dp(25), Gravity.CENTER))
            }, LayoutParams(activity.dp(44), activity.dp(44)).apply { rightMargin = activity.dp(18) })

            val text = LinearLayout(activity).apply {
                orientation = VERTICAL
                addView(activity.launcherLabel(title, 16, LauncherPalette.text, bold = true))
                addView(activity.launcherLabel(description, 13, LauncherPalette.muted).apply {
                    maxLines = 2
                }, LayoutParams(-1, -2).apply { topMargin = activity.dp(3) })
            }
            addView(text, LayoutParams(0, -2, 1f))
            addView(activity.launcherLabel("›", 28, LauncherPalette.muted), LayoutParams(-2, -2).apply {
                leftMargin = activity.dp(16)
            })

            setOnFocusChangeListener { view, focused ->
                view.animate().scaleX(if (focused) 1.025f else 1f)
                    .scaleY(if (focused) 1.025f else 1f).setDuration(120).start()
                view.background = activity.rounded(
                    if (focused) Color.rgb(35, 48, 68) else LauncherPalette.surface,
                    14,
                    if (focused) LauncherPalette.accent else null,
                )
                view.elevation = if (focused) activity.dp(6).toFloat() else 0f
            }
            setOnClickListener { onClick() }
            rows += this
        }.also { row ->
            row.layoutParams = LayoutParams(-1, activity.dp(84))
        }
}
