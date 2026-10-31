package io.jostx.hy300launcher.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.jostx.hy300launcher.R
import io.jostx.hy300launcher.model.TvApp

enum class AppMenuMode {
    CATALOG,
    HOME_SELECTION,
}

class AppMenuOverlay(
    private val activity: Activity,
    private val mode: AppMenuMode,
    apps: List<TvApp>,
    selectedPackages: List<String>,
    private val onClose: () -> Unit,
    private val onOpenApp: (TvApp) -> Unit,
    private val onToggleHome: (TvApp) -> Unit,
    private val onReorderHomeApp: (String, Int) -> Boolean,
    private val onAppFocusChanged: (View, View, String, Boolean) -> Unit,
) : LinearLayout(activity) {
    private var apps = apps.toList()
    private var selectedPackages = selectedPackages.toMutableList()
    private val columns: Int
    private val tileWidthDp: Int
    private val grid: GridLayout
    private val closeButton: TextView

    init {
        val availableWidth = (activity.resources.configuration.screenWidthDp - 60).coerceAtLeast(430)
        columns = (availableWidth / 142).coerceAtLeast(3)
        tileWidthDp = availableWidth / columns - 8

        orientation = VERTICAL
        setPadding(activity.dp(30), activity.dp(20), activity.dp(30), activity.dp(18))
        setBackgroundResource(R.drawable.launcher_background)
        isFocusable = false
        isFocusableInTouchMode = false

        val header = LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerText = LinearLayout(activity).apply { orientation = VERTICAL }
        headerText.addView(activity.launcherLabel(title(), 22, LauncherPalette.text, bold = true))
        headerText.addView(activity.launcherLabel(subtitle(), 12, LauncherPalette.muted))
        header.addView(headerText, LayoutParams(0, -2, 1f))

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
        addView(header, LayoutParams(-1, activity.dp(52)))

        grid = GridLayout(activity).apply {
            columnCount = columns
            useDefaultMargins = false
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(grid, ViewGroup.LayoutParams(-1, -2))
        }
        addView(scroll, LayoutParams(-1, 0, 1f).apply {
            topMargin = activity.dp(14)
            bottomMargin = activity.dp(8)
        })

        addView(activity.launcherLabel(footer(), 12, LauncherPalette.muted), LayoutParams(-1, -2))
        renderTiles()
    }

    fun updateSelection(packages: List<String>) {
        val focusedPackage = focusedAppPackage()
        selectedPackages = packages.toMutableList()
        renderTiles(focusedPackage)
    }

    fun updateApps(items: List<TvApp>) {
        val focusedPackage = focusedAppPackage()
        apps = items.toList()
        renderTiles(focusedPackage)
    }

    fun focusInitialItem() {
        post {
            (grid.getChildAt(0) ?: closeButton).requestFocus()
        }
    }

    private fun renderTiles(focusedPackage: String? = null) {
        grid.removeAllViews()
        val orderedApps = if (mode == AppMenuMode.HOME_SELECTION) {
            selectedPackages.mapNotNull { packageName -> apps.find { it.packageName == packageName } } +
                apps.filterNot { it.packageName in selectedPackages }
        } else apps

        orderedApps.forEach { app ->
            val selected = app.packageName in selectedPackages
            val selectedIndex = selectedPackages.indexOf(app.packageName).takeIf { it >= 0 }
            val tile = LauncherAppTile(
                activity = activity,
                app = app,
                isFavorite = selected,
                favoriteIndex = selectedIndex,
                tileWidthDp = tileWidthDp,
                showSelectionState = mode == AppMenuMode.HOME_SELECTION,
                isEditingFavorites = { mode == AppMenuMode.HOME_SELECTION },
                onSelect = { selectedApp ->
                    if (mode == AppMenuMode.HOME_SELECTION) onToggleHome(selectedApp)
                    else onOpenApp(selectedApp)
                },
                onLongSelect = { selectedApp ->
                    onToggleHome(selectedApp)
                    true
                },
                onReorder = { direction -> onReorderHomeApp(app.packageName, direction) },
                onFocusChanged = onAppFocusChanged,
            )
            val params = GridLayout.LayoutParams().apply {
                width = activity.dp(tileWidthDp)
                height = activity.dp(tileWidthDp * 9 / 16 + 34)
                setMargins(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(5))
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED)
            }
            grid.addView(tile, params)
            if (app.packageName == focusedPackage) tile.requestFocus()
        }
    }

    private fun focusedAppPackage(): String? = (0 until grid.childCount)
        .mapNotNull { grid.getChildAt(it) }
        .firstOrNull { it.hasFocus() }
        ?.tag as? String

    private fun title() = when (mode) {
        AppMenuMode.CATALOG -> "Todas las apps"
        AppMenuMode.HOME_SELECTION -> "Elegir apps para Inicio"
    }

    private fun subtitle() = when (mode) {
        AppMenuMode.CATALOG -> "Selecciona una app para abrirla"
        AppMenuMode.HOME_SELECTION -> "Elige cuáles aparecen en la pantalla de Inicio"
    }

    private fun footer() = when (mode) {
        AppMenuMode.CATALOG -> "Mantén OK en una app para añadirla o quitarla de Inicio"
        AppMenuMode.HOME_SELECTION -> "OK selecciona · ◀ ▶ ordena las apps elegidas · máximo 8"
    }
}
