package io.jostx.hy300launcher.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import io.jostx.hy300launcher.R
import io.jostx.hy300launcher.model.TvApp

/** Owns the launcher home layout and favorite app shelf. */
class LauncherHomeScreen(
    private val activity: Activity,
    private val onPower: () -> Unit,
    private val onOpenCatalog: () -> Unit,
    private val onOpenInputs: () -> Unit,
    private val onOpenSettingsMenu: (View) -> Unit,
    private val onLaunchApp: (TvApp) -> Unit,
    private val onToggleFavorite: (TvApp) -> Boolean,
) : FrameLayout(activity) {
    private val content: LinearLayout
    private val tooltip: FocusTooltipOverlay
    private val favoriteStrip: LinearLayout
    private val shelfTray: FrameLayout
    val allAppsButton: ActionIconButton
    val settingsButton: ActionIconButton
    private var homeTileWidthDp = 136

    init {
        clipChildren = false
        clipToPadding = false
        val scrollView = ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundResource(R.drawable.launcher_background)
            clipToPadding = false
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(30), activity.dp(20), activity.dp(30), activity.dp(28))
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        scrollView.addView(content, ViewGroup.LayoutParams(-1, -2))
        addView(scrollView, FrameLayout.LayoutParams(-1, -1))
        tooltip = FocusTooltipOverlay(activity, this)

        val heading = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            isFocusable = false
            isFocusableInTouchMode = false
        }
        val logo = ImageView(activity).apply {
            setImageResource(R.drawable.ic_tv)
            background = activity.rounded(LauncherPalette.surface, 14)
            setPadding(activity.dp(9), activity.dp(9), activity.dp(9), activity.dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        heading.addView(logo, LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)).apply {
            rightMargin = activity.dp(12)
        })
        val brand = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(activity.launcherLabel("HY300Pro", 23, LauncherPalette.text, bold = true))
        heading.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(actionButton("Apagar", R.drawable.ic_power, LauncherPalette.power) { onPower() })
        allAppsButton = actionButton("Todas las apps", R.drawable.ic_layout_grid) { onOpenCatalog() }
        heading.addView(allAppsButton, spacedActionParams())
        heading.addView(actionButton("Entradas", R.drawable.ic_monitor_play) { onOpenInputs() }, spacedActionParams())
        settingsButton = actionButton("Ajustes", R.drawable.ic_settings) { view -> onOpenSettingsMenu(view) }
        heading.addView(settingsButton, spacedActionParams())
        content.addView(heading)

        val titleRow = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(activity.launcherLabel("Apps favoritas", 19, LauncherPalette.text, bold = true))
        content.addView(titleRow, verticalMargin(14))

        val favoritesScroller = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        favoriteStrip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        favoritesScroller.addView(favoriteStrip)
        val leftShelfFade = shelfFade(GradientDrawable.Orientation.LEFT_RIGHT)
        val rightShelfFade = shelfFade(GradientDrawable.Orientation.RIGHT_LEFT)
        shelfTray = FrameLayout(activity).apply {
            background = activity.rounded(LauncherPalette.shelf, 12, LauncherPalette.shelfBorder)
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            clipToOutline = true
        }
        shelfTray.addView(favoritesScroller, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        shelfTray.addView(leftShelfFade, FrameLayout.LayoutParams(activity.dp(4), -1, Gravity.START or Gravity.CENTER_VERTICAL))
        shelfTray.addView(rightShelfFade, FrameLayout.LayoutParams(activity.dp(4), -1, Gravity.END or Gravity.CENTER_VERTICAL))
        val updateFades = {
            val maxScroll = (favoriteStrip.width - favoritesScroller.width).coerceAtLeast(0)
            leftShelfFade.visibility = if (favoritesScroller.scrollX > 0) View.VISIBLE else View.INVISIBLE
            rightShelfFade.visibility = if (favoritesScroller.scrollX < maxScroll - 1) View.VISIBLE else View.INVISIBLE
        }
        favoritesScroller.setOnScrollChangeListener { _, _, _, _, _ -> updateFades() }
        favoriteStrip.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateFades() }
        shelfTray.post(updateFades)
        content.addView(shelfTray, verticalMargin(4))
    }

    val hasContentFocus: Boolean
        get() = content.hasFocus()

    fun hideTooltip() = tooltip.hide()

    fun showTooltip(anchor: View, positionAnchor: View, title: String) =
        tooltip.showFor(anchor, title, positionAnchor)

    fun focusInitialItem() {
        (favoriteStrip.getChildAt(0)?.takeIf { it.isFocusable } ?: allAppsButton).requestFocus()
    }

    fun renderFavorites(apps: List<TvApp>, favoritePackages: List<String>, focusedIndex: Int? = null) {
        updateTileMetrics()
        favoriteStrip.removeAllViews()
        val favoriteApps = favoritePackages.mapNotNull { packageName -> apps.find { it.packageName == packageName } }
        if (favoriteApps.isEmpty()) {
            favoriteStrip.addView(
                activity.launcherLabel("Elige qué apps quieres ver aquí", 13, LauncherPalette.muted).apply {
                    gravity = Gravity.CENTER_VERTICAL
                },
                LinearLayout.LayoutParams(-1, activity.dp(40)),
            )
            return
        }
        favoriteApps.forEachIndexed { index, app ->
            favoriteStrip.addView(createHomeTile(app, index), favoriteTileParams())
            if (focusedIndex == index) favoriteStrip.getChildAt(index)?.requestFocus()
        }
    }

    private fun createHomeTile(app: TvApp, index: Int) = LauncherAppTile(
        activity = activity,
        app = app,
        isFavorite = true,
        favoriteIndex = index,
        tileWidthDp = homeTileWidthDp,
        isEditingFavorites = { false },
        onSelect = onLaunchApp,
        onLongSelect = onToggleFavorite,
        onReorder = { false },
        reserveTooltipSpace = false,
        scaleOnFocus = false,
        onFocusChanged = { focusAnchor, positionAnchor, title, focused ->
            if (focused) tooltip.showBelow(focusAnchor, title, shelfTray, positionAnchor) else tooltip.hide()
        },
    )

    private fun updateTileMetrics() {
        val availableWidthDp = (activity.resources.configuration.screenWidthDp - 60).coerceAtLeast(430)
        val columns = (availableWidthDp / 142).coerceAtLeast(3)
        val shelfViewportWidthDp = (availableWidthDp - 20).coerceAtLeast(360)
        // Show five tiles and a small preview of the next one in the initial viewport.
        homeTileWidthDp = (shelfViewportWidthDp / columns - 11).coerceAtLeast(100)
        shelfTray.layoutParams = shelfTray.layoutParams.apply {
            height = activity.dp(homeTileWidthDp * 9 / 16 + 30)
        }
    }

    private fun shelfFade(orientation: GradientDrawable.Orientation) = View(activity).apply {
        background = GradientDrawable(
            orientation,
            intArrayOf(LauncherPalette.shelf, Color.TRANSPARENT),
        )
        visibility = View.INVISIBLE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun actionButton(
        title: String,
        icon: Int,
        iconTint: Int = LauncherPalette.text,
        onClick: (View) -> Unit,
    ) = ActionIconButton(activity, title, icon, iconTint) { anchor, tooltipTitle, focused ->
        if (focused) tooltip.showFor(anchor, tooltipTitle) else tooltip.hide()
    }.apply {
        setOnClickListener { onClick(this) }
    }

    private fun spacedActionParams() = LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)).apply {
        leftMargin = activity.dp(8)
    }

    private fun favoriteTileParams() = LinearLayout.LayoutParams(
        activity.dp(homeTileWidthDp),
        activity.dp(homeTileWidthDp * 9 / 16 + 2),
    ).apply {
        rightMargin = activity.dp(10)
        topMargin = activity.dp(3)
        bottomMargin = activity.dp(3)
    }

    private fun verticalMargin(value: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = activity.dp(value)
    }
}
