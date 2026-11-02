package io.jostx.hy300launcher.ui

import android.app.Activity
import android.graphics.drawable.BitmapDrawable
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TextClock
import android.text.format.DateFormat
import io.jostx.hy300launcher.R
import io.jostx.hy300launcher.model.TvApp
import java.util.Locale
import kotlin.math.roundToInt

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
    private lateinit var connectivityStatusCapsule: ConnectivityStatusCapsule
    private var homeTileWidthDp = activity.uiDimen(R.dimen.home_tile_default_width)
    private val horizontalSafeInsetDp = maxOf(
        activity.uiDimen(R.dimen.safe_inset_horizontal),
        (activity.resources.configuration.screenWidthDp * 0.05f).roundToInt(),
    )

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
            val verticalSafeInsetDp = maxOf(
                activity.uiDimen(R.dimen.safe_inset_vertical),
                (activity.resources.configuration.screenHeightDp * 0.05f).roundToInt(),
            )
            setPadding(
                activity.dp(horizontalSafeInsetDp),
                activity.dp(verticalSafeInsetDp),
                activity.dp(horizontalSafeInsetDp),
                activity.dp(verticalSafeInsetDp),
            )
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
        val brand = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val clock = TextClock(activity).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextAppearance(R.style.LauncherTextClock)
            setTextColor(activity.uiColor(R.color.launcher_text_primary))
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        val date = TextClock(activity).apply {
            format12Hour = datePattern
            format24Hour = datePattern
            setTextAppearance(R.style.LauncherTextCaption)
            includeFontPadding = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        brand.addView(clock)
        brand.addView(date)
        heading.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        connectivityStatusCapsule = ConnectivityStatusCapsule(activity)
        val toolbarCapsuleHeightDp =
            activity.uiDimen(R.dimen.action_target) + activity.uiDimen(R.dimen.space_5) * 2
        heading.addView(connectivityStatusCapsule, LinearLayout.LayoutParams(
            -2,
            activity.dp(toolbarCapsuleHeightDp),
        ).apply {
            leftMargin = activity.dp(activity.uiDimen(R.dimen.space_8))
        })
        val actionTabs = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val horizontalInset = activity.dp(activity.uiDimen(R.dimen.space_6))
            val verticalInset = activity.dp(activity.uiDimen(R.dimen.space_5))
            setPadding(horizontalInset, verticalInset, horizontalInset, verticalInset)
            background = activity.glassRounded(
                activity.uiColor(R.color.launcher_glass_toolbar_fill),
                activity.uiDimen(R.dimen.radius_toolbar),
                activity.uiColor(R.color.launcher_glass_toolbar_edge),
            )
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            elevation = activity.dp(activity.uiDimen(R.dimen.elevation_toolbar)).toFloat()
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        addActionTab(actionTabs, actionButton("Apagar", R.drawable.ic_power, activity.uiColor(R.color.launcher_power)) { onPower() })
        allAppsButton = actionButton("Todas las apps", R.drawable.ic_layout_grid) { onOpenCatalog() }
        addActionTab(actionTabs, allAppsButton)
        addActionTab(actionTabs, actionButton("Entradas", R.drawable.ic_monitor_play) { onOpenInputs() })
        settingsButton = actionButton("Ajustes", R.drawable.ic_settings) { view -> onOpenSettingsMenu(view) }
        addActionTab(actionTabs, settingsButton)
        heading.addView(actionTabs, LinearLayout.LayoutParams(-2, -2).apply {
            leftMargin = activity.dp(activity.uiDimen(R.dimen.space_8))
        })
        content.addView(heading)
        installGlassBackdrop(connectivityStatusCapsule, scrollView)
        installGlassBackdrop(actionTabs, scrollView)

        val titleRow = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(activity.launcherLabel("Apps favoritas", R.style.LauncherTextSection, bold = true))
        content.addView(titleRow, verticalMargin(activity.uiDimen(R.dimen.space_14)))

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
            background = activity.rounded(activity.uiColor(R.color.launcher_shelf), activity.uiDimen(R.dimen.radius_surface), activity.uiColor(R.color.launcher_shelf_border))
            setPadding(activity.dp(activity.uiDimen(R.dimen.space_10)), activity.dp(activity.uiDimen(R.dimen.space_10)), activity.dp(activity.uiDimen(R.dimen.space_10)), activity.dp(activity.uiDimen(R.dimen.space_10)))
            clipToOutline = true
        }
        shelfTray.addView(favoritesScroller, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        shelfTray.addView(leftShelfFade, FrameLayout.LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_4)), -1, Gravity.START or Gravity.CENTER_VERTICAL))
        shelfTray.addView(rightShelfFade, FrameLayout.LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_4)), -1, Gravity.END or Gravity.CENTER_VERTICAL))
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

    fun updateConnectivityStatus(wifiEnabled: Boolean, wifiConnected: Boolean, bluetoothEnabled: Boolean) {
        connectivityStatusCapsule.setStatus(wifiEnabled, wifiConnected, bluetoothEnabled)
    }

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
            favoriteStrip.addView(createEmptyFavoritesAction())
            return
        }
        val tiles = favoriteApps.mapIndexed { index, app -> createHomeTile(app, index) }
        tiles.forEachIndexed { index, tile ->
            tile.id = View.generateViewId()
            tile.nextFocusLeftId = tiles.getOrNull(index - 1)?.id ?: tile.id
        }
        tiles.forEachIndexed { index, tile ->
            tile.nextFocusRightId = tiles.getOrNull(index + 1)?.id ?: tile.id
            favoriteStrip.addView(tile, favoriteTileParams())
            if (focusedIndex == index) tile.requestFocus()
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
        scaleOnFocus = true,
        onFocusChanged = { focusAnchor, positionAnchor, title, focused ->
            if (focused) {
                tooltip.showBelow(focusAnchor, title, shelfTray, positionAnchor)
                focusAnchor.post {
                    if (!focusAnchor.hasFocus()) return@post
                    val visibleRect = Rect()
                    focusAnchor.getDrawingRect(visibleRect)
                    focusAnchor.requestRectangleOnScreen(visibleRect, false)
                }
            } else {
                tooltip.hide()
            }
        },
    )

    private fun updateTileMetrics() {
        val availableWidthDp = (
            activity.resources.configuration.screenWidthDp - horizontalSafeInsetDp * 2
            ).coerceAtLeast(activity.uiDimen(R.dimen.catalog_min_width))
        val columns = (availableWidthDp / activity.uiDimen(R.dimen.catalog_tile_base_width))
            .coerceAtLeast(activity.resources.getInteger(R.integer.launcher_grid_min_columns))
        val shelfViewportWidthDp = (availableWidthDp - activity.uiDimen(R.dimen.home_shelf_width_inset))
            .coerceAtLeast(activity.uiDimen(R.dimen.home_shelf_min_width))
        // Show five tiles and a small preview of the next one in the initial viewport.
        homeTileWidthDp = (shelfViewportWidthDp / columns - activity.uiDimen(R.dimen.home_tile_gap))
            .coerceAtLeast(activity.uiDimen(R.dimen.home_tile_min_width))
        shelfTray.layoutParams = shelfTray.layoutParams.apply {
            height = activity.dp(homeTileWidthDp * 9 / 16 + activity.uiDimen(R.dimen.space_40))
        }
    }

    private fun createEmptyFavoritesAction() = FrameLayout(activity).apply {
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = "Añadir apps favoritas"
        background = activity.glassRounded(
            activity.uiColor(R.color.launcher_glass_toolbar_fill),
            activity.uiDimen(R.dimen.radius_action),
            activity.uiColor(R.color.launcher_glass_toolbar_edge),
        )
        addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_layout_grid)
            setColorFilter(activity.uiColor(R.color.launcher_accent))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_22)), activity.dp(activity.uiDimen(R.dimen.space_22)), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            leftMargin = activity.dp(activity.uiDimen(R.dimen.space_14))
        })
        addView(activity.launcherLabel("Añadir apps favoritas", R.style.LauncherTextBody, bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(activity.uiDimen(R.dimen.space_46)), 0, activity.dp(activity.uiDimen(R.dimen.space_14)), 0)
        }, LayoutParams(-1, -1))
        layoutParams = LinearLayout.LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_252)), activity.dp(activity.uiDimen(R.dimen.space_64)))
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
            view.animate().cancel()
            view.animate().scaleX(if (focused) 1.04f else 1f)
                .scaleY(if (focused) 1.04f else 1f)
                .setDuration(activity.resources.getInteger(R.integer.motion_focus).toLong()).start()
        }
        setOnClickListener { onOpenCatalog() }
    }

    private fun shelfFade(orientation: GradientDrawable.Orientation) = View(activity).apply {
        background = GradientDrawable(
            orientation,
            intArrayOf(activity.uiColor(R.color.launcher_shelf), Color.TRANSPARENT),
        )
        visibility = View.INVISIBLE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun installGlassBackdrop(surface: View, source: View) {
        surface.post {
            if (surface.width <= 0 || surface.height <= 0 || source.width <= 0 || source.height <= 0) return@post
            val position = IntArray(2)
            surface.getLocationOnScreen(position)
            val glassOverlay = surface.background ?: return@post
            surface.visibility = View.INVISIBLE
            val bitmap = captureBlurredBackdrop(
                activity,
                source,
                position[0],
                position[1],
                surface.width,
                surface.height,
            )
            surface.visibility = View.VISIBLE
            if (bitmap != null) {
                surface.background = LayerDrawable(arrayOf(
                    BitmapDrawable(resources, bitmap).apply {
                        gravity = Gravity.FILL
                        isFilterBitmap = true
                    },
                    glassOverlay,
                ))
                surface.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(
                            0,
                            0,
                            view.width,
                            view.height,
                        activity.dp(activity.uiDimen(R.dimen.radius_toolbar)).toFloat(),
                        )
                    }
                }
            }
        }
    }

    private fun actionButton(
        title: String,
        icon: Int,
        iconTint: Int = activity.uiColor(R.color.launcher_text_primary),
        onClick: (View) -> Unit,
    ) = ActionIconButton(activity, title, icon, iconTint).apply {
        setOnClickListener { onClick(this) }
    }

    private fun addActionTab(container: LinearLayout, button: ActionIconButton) {
        if (container.childCount > 0) {
            container.addView(View(activity), LinearLayout.LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_4)), activity.dp(activity.uiDimen(R.dimen.space_1))))
        }
        container.addView(
            button,
            LinearLayout.LayoutParams(
                activity.dp(activity.uiDimen(R.dimen.action_target)),
                activity.dp(activity.uiDimen(R.dimen.action_target)),
            ),
        )
    }

    private fun favoriteTileParams() = LinearLayout.LayoutParams(
        activity.dp(homeTileWidthDp),
        activity.dp(homeTileWidthDp * 9 / 16 + activity.uiDimen(R.dimen.space_2)),
    ).apply {
        leftMargin = activity.dp(activity.uiDimen(R.dimen.space_10))
        rightMargin = activity.dp(activity.uiDimen(R.dimen.space_10))
        topMargin = activity.dp(activity.uiDimen(R.dimen.space_3))
        bottomMargin = activity.dp(activity.uiDimen(R.dimen.space_3))
    }

    private fun verticalMargin(value: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = activity.dp(value)
    }
}
