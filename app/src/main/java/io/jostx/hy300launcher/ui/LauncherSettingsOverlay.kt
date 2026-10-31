package io.jostx.hy300launcher.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.jostx.hy300launcher.R

/** Full-screen, D-pad-friendly settings page for launcher-specific options. */
class LauncherSettingsOverlay(
    private val activity: Activity,
    selectedAppCount: Int,
    private val onClose: () -> Unit,
    private val onManageHomeApps: () -> Unit,
    private val onChooseDefaultLauncher: () -> Unit,
    private val onAbout: () -> Unit,
) : LinearLayout(activity) {
    private val rows = mutableListOf<View>()
    private lateinit var closeButton: TextView

    init {
        orientation = VERTICAL
        setPadding(activity.dp(30), activity.dp(20), activity.dp(30), activity.dp(20))
        setBackgroundResource(R.drawable.launcher_background)
        isFocusable = false
        isFocusableInTouchMode = false

        val header = LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = VERTICAL }
        heading.addView(activity.launcherLabel("Ajustes del launcher", 23, LauncherPalette.text, bold = true))
        heading.addView(activity.launcherLabel("Personaliza tu pantalla de Inicio", 13, LauncherPalette.muted))
        header.addView(heading, LayoutParams(0, -2, 1f))

        closeButton = TextView(activity).apply {
            text = "Volver"
            textSize = 14f
            setTextColor(LauncherPalette.text)
            gravity = Gravity.CENTER
            setPadding(activity.dp(18), 0, activity.dp(18), 0)
            isFocusable = true
            isFocusableInTouchMode = true
            background = activity.rounded(LauncherPalette.surface, 12)
            setOnFocusChangeListener { view, focused ->
                view.background = activity.rounded(
                    LauncherPalette.surface,
                    12,
                    if (focused) LauncherPalette.accent else null,
                )
            }
            setOnClickListener { onClose() }
        }
        header.addView(closeButton, LayoutParams(-2, activity.dp(44)))
        addView(header, LayoutParams(-1, activity.dp(54)))

        val scroll = ScrollView(activity).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = FOCUS_AFTER_DESCENDANTS
            clipChildren = false
            clipToPadding = false
        }
        val content = LinearLayout(activity).apply {
            orientation = VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(content, LayoutParams(-1, -2))

        content.addView(
            activity.launcherLabel("Inicio", 14, LauncherPalette.muted, bold = true),
            LayoutParams(-1, -2).apply { topMargin = activity.dp(22); bottomMargin = activity.dp(9) },
        )
        content.addView(
            settingRow(
                R.drawable.ic_layout_grid,
                "Elegir apps de Inicio",
                "$selectedAppCount de 8 seleccionadas · elige y ordena tus apps favoritas",
                onManageHomeApps,
            ),
        )
        content.addView(
            settingRow(
                R.drawable.ic_tv,
                "Launcher predeterminado",
                "Elige qué aplicación se abre al pulsar Inicio en el control remoto",
                onChooseDefaultLauncher,
            ),
            LayoutParams(-1, -2).apply { topMargin = activity.dp(9) },
        )

        content.addView(
            activity.launcherLabel("Información", 14, LauncherPalette.muted, bold = true),
            LayoutParams(-1, -2).apply { topMargin = activity.dp(22); bottomMargin = activity.dp(9) },
        )
        content.addView(
            settingRow(
                R.drawable.ic_info,
                "Acerca del launcher",
                "Versión instalada y detalles de esta aplicación",
                onAbout,
            ),
        )

        addView(scroll, LayoutParams(-1, 0, 1f).apply { topMargin = activity.dp(4) })
    }

    fun focusInitialItem() {
        post { (rows.firstOrNull() ?: closeButton).requestFocus() }
    }

    private fun settingRow(iconRes: Int, title: String, description: String, onClick: () -> Unit) =
        LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(18), activity.dp(13), activity.dp(20), activity.dp(13))
            isFocusable = true
            isFocusableInTouchMode = true
            background = activity.rounded(LauncherPalette.surface, 12)
            addView(FrameLayout(activity).apply {
                addView(ImageView(activity).apply {
                    setImageResource(iconRes)
                    setColorFilter(LauncherPalette.text)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, FrameLayout.LayoutParams(activity.dp(25), activity.dp(25), Gravity.CENTER))
            }, LayoutParams(activity.dp(42), activity.dp(42)).apply { rightMargin = activity.dp(17) })

            val text = LinearLayout(activity).apply {
                orientation = VERTICAL
                addView(activity.launcherLabel(title, 16, LauncherPalette.text, bold = true))
                addView(activity.launcherLabel(description, 12, LauncherPalette.muted).apply {
                    maxLines = 2
                }, LayoutParams(-1, -2).apply { topMargin = activity.dp(3) })
            }
            addView(text, LayoutParams(0, -2, 1f))
            addView(activity.launcherLabel("›", 28, LauncherPalette.muted), LayoutParams(-2, -2).apply {
                leftMargin = activity.dp(16)
            })

            setOnFocusChangeListener { view, focused ->
                view.animate().scaleX(if (focused) 1.015f else 1f)
                    .scaleY(if (focused) 1.015f else 1f).setDuration(100).start()
                view.background = activity.rounded(
                    if (focused) LauncherPalette.actionSurface else LauncherPalette.surface,
                    12,
                    if (focused) LauncherPalette.accent else null,
                )
            }
            setOnClickListener { onClick() }
            rows += this
        }.also { row ->
            row.layoutParams = LayoutParams(-1, activity.dp(76))
        }
}
