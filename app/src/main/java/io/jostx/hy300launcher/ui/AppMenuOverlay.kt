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
        val availableWidth = (activity.resources.configuration.screenWidthDp - activity.uiDimen(R.dimen.catalog_horizontal_inset))
            .coerceAtLeast(activity.uiDimen(R.dimen.catalog_min_width))
        columns = (availableWidth / activity.uiDimen(R.dimen.catalog_tile_base_width))
            .coerceAtLeast(activity.resources.getInteger(R.integer.launcher_grid_min_columns))
        tileWidthDp = availableWidth / columns - activity.uiDimen(R.dimen.catalog_tile_gap)

        orientation = VERTICAL
        setPadding(activity.dp(activity.uiDimen(R.dimen.space_30)), activity.dp(activity.uiDimen(R.dimen.space_20)), activity.dp(activity.uiDimen(R.dimen.space_30)), activity.dp(activity.uiDimen(R.dimen.space_18)))
        setBackgroundResource(R.drawable.launcher_background)
        isFocusable = false
        isFocusableInTouchMode = false

        val header = LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerText = LinearLayout(activity).apply { orientation = VERTICAL }
        headerText.addView(activity.launcherLabel(title(), R.style.LauncherTextTitle, bold = true))
        headerText.addView(activity.launcherLabel(subtitle(), R.style.LauncherTextCaption))
        header.addView(headerText, LayoutParams(0, -2, 1f))

        closeButton = TextView(activity).apply {
            text = "Volver"
            setTextAppearance(R.style.LauncherTextBody)
            gravity = Gravity.CENTER
            setPadding(activity.dp(activity.uiDimen(R.dimen.space_18)), 0, activity.dp(activity.uiDimen(R.dimen.space_18)), 0)
            isFocusable = true
            isFocusableInTouchMode = true
            background = activity.glassRounded(
                activity.uiColor(R.color.launcher_glass_toolbar_fill),
                activity.uiDimen(R.dimen.radius_action),
                activity.uiColor(R.color.launcher_glass_toolbar_edge),
            )
            setOnFocusChangeListener { view, focused ->
                view.background = if (focused) activity.glassRounded(
                    activity.uiColor(R.color.launcher_glass_focus_panel),
                    activity.uiDimen(R.dimen.radius_action),
                    activity.uiColor(R.color.launcher_glass_focus_edge),
                    activity.uiDimen(R.dimen.focus_ring_toolbar),
                ) else activity.glassRounded(
                    activity.uiColor(R.color.launcher_glass_toolbar_fill),
                    activity.uiDimen(R.dimen.radius_action),
                    activity.uiColor(R.color.launcher_glass_toolbar_edge),
                )
            }
            setOnClickListener { onClose() }
        }
        header.addView(closeButton, LayoutParams(-2, activity.dp(activity.uiDimen(R.dimen.space_44))))
        addView(header, LayoutParams(-1, activity.dp(activity.uiDimen(R.dimen.space_52))))

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
            topMargin = activity.dp(activity.uiDimen(R.dimen.space_14))
            bottomMargin = activity.dp(activity.uiDimen(R.dimen.space_8))
        })

        addView(activity.launcherLabel(footer(), R.style.LauncherTextCaption), LayoutParams(-1, -2))
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
                height = activity.dp(tileWidthDp * 9 / 16 + activity.uiDimen(R.dimen.space_34))
                setMargins(activity.dp(activity.uiDimen(R.dimen.space_4)), activity.dp(activity.uiDimen(R.dimen.space_4)), activity.dp(activity.uiDimen(R.dimen.space_4)), activity.dp(activity.uiDimen(R.dimen.space_5)))
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
