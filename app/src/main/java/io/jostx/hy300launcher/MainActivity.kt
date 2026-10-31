package io.jostx.hy300launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import io.jostx.hy300launcher.data.FavoritesStore
import io.jostx.hy300launcher.data.InstalledAppsRepository
import io.jostx.hy300launcher.model.TvApp
import io.jostx.hy300launcher.ui.ActionIconButton
import io.jostx.hy300launcher.ui.AppMenuMode
import io.jostx.hy300launcher.ui.AppMenuOverlay
import io.jostx.hy300launcher.ui.FocusTooltipOverlay
import io.jostx.hy300launcher.ui.LauncherAppTile
import io.jostx.hy300launcher.ui.LauncherPalette
import io.jostx.hy300launcher.ui.LauncherSettingsOverlay
import io.jostx.hy300launcher.ui.SettingsDropdownMenu
import io.jostx.hy300launcher.ui.dp
import io.jostx.hy300launcher.ui.launcherLabel
import io.jostx.hy300launcher.ui.rounded

class MainActivity : Activity() {
    private lateinit var appRepository: InstalledAppsRepository
    private lateinit var favoritesStore: FavoritesStore
    private val apps = mutableListOf<TvApp>()
    private val favoritePackages = mutableListOf<String>()

    private lateinit var content: LinearLayout
    private lateinit var rootFrame: FrameLayout
    private lateinit var tooltip: FocusTooltipOverlay
    private lateinit var favoriteStrip: LinearLayout
    private lateinit var shelfTray: FrameLayout
    private lateinit var allAppsButton: ActionIconButton
    private lateinit var settingsButton: ActionIconButton
    private var appMenu: AppMenuOverlay? = null
    private var settingsOverlay: LauncherSettingsOverlay? = null
    private var settingsMenu: SettingsDropdownMenu? = null
    private var returnFocus: View? = null
    private var returnToLauncherSettings = false
    private var tileWidthDp = 136
    private var homeTileWidthDp = 136

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appRepository = InstalledAppsRepository(packageManager, packageName)
        favoritesStore = FavoritesStore(this)
        favoritePackages += favoritesStore.load().take(FavoritesStore.MAX_FAVORITES)
        buildLauncherView()
        refreshApps()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyTvWindowFlags()
    }

    override fun onResume() {
        super.onResume()
        if (!::content.isInitialized) return
        when {
            appMenu != null -> refreshMenuApps()
            settingsOverlay == null -> refreshApps()
        }
    }

    @Deprecated("Deprecated in Android, retained for TV Back-key handling")
    override fun onBackPressed() {
        when {
            appMenu != null -> closeAppMenu()
            settingsOverlay != null -> closeLauncherSettings()
            settingsMenu?.isShowing == true -> settingsMenu?.dismiss()
            else -> super.onBackPressed()
        }
    }

    private fun buildLauncherView() {
        val scrollView = createScrollView()
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(30), dp(20), dp(30), dp(28))
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        scrollView.addView(content, ViewGroup.LayoutParams(-1, -2))

        rootFrame = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        rootFrame.addView(scrollView, FrameLayout.LayoutParams(-1, -1))
        tooltip = FocusTooltipOverlay(this, rootFrame)
        setContentView(rootFrame)
        applyTvWindowFlags()

        addHeader()
        addFavoritesSection()
    }

    private fun createScrollView() = ScrollView(this).apply {
        isFillViewport = true
        setBackgroundResource(R.drawable.launcher_background)
        clipToPadding = false
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    private fun addHeader() {
        val heading = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            isFocusable = false
            isFocusableInTouchMode = false
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_tv)
            background = rounded(LauncherPalette.surface, 14)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        heading.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) })

        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(launcherLabel("HY300Pro", 23, LauncherPalette.text, bold = true))
        heading.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        heading.addView(
            actionButton(
                title = "Apagar",
                icon = R.drawable.ic_power,
                onClick = ::showPowerUnavailable,
                iconTint = LauncherPalette.power,
            ),
        )
        allAppsButton = actionButton("Todas las apps", R.drawable.ic_layout_grid) {
            openAppMenu(AppMenuMode.CATALOG)
        }
        heading.addView(allAppsButton, spacedActionParams())

        heading.addView(actionButton("Entradas", R.drawable.ic_monitor_play, onClick = ::openInputs), spacedActionParams())
        settingsButton = actionButton("Ajustes", R.drawable.ic_settings, onClick = ::toggleSettingsMenu)
        heading.addView(settingsButton, spacedActionParams())
        content.addView(heading)
    }

    private fun addFavoritesSection() {
        val titleRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(launcherLabel("Apps favoritas", 19, LauncherPalette.text, bold = true))
        content.addView(titleRow, verticalMargin(14))

        val favoritesScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        favoriteStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        favoritesScroller.addView(favoriteStrip)
        val leftShelfFade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(LauncherPalette.shelf, android.graphics.Color.TRANSPARENT),
            )
            visibility = View.INVISIBLE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val rightShelfFade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                intArrayOf(LauncherPalette.shelf, android.graphics.Color.TRANSPARENT),
            )
            visibility = View.INVISIBLE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        shelfTray = FrameLayout(this).apply {
            background = rounded(LauncherPalette.shelf, 12, LauncherPalette.shelfBorder)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            clipToOutline = true
        }
        shelfTray.addView(favoritesScroller, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        shelfTray.addView(leftShelfFade, FrameLayout.LayoutParams(dp(4), -1, Gravity.START or Gravity.CENTER_VERTICAL))
        shelfTray.addView(rightShelfFade, FrameLayout.LayoutParams(dp(4), -1, Gravity.END or Gravity.CENTER_VERTICAL))
        val updateShelfFades = {
            val maxScroll = (favoriteStrip.width - favoritesScroller.width).coerceAtLeast(0)
            leftShelfFade.visibility = if (favoritesScroller.scrollX > 0) View.VISIBLE else View.INVISIBLE
            rightShelfFade.visibility = if (favoritesScroller.scrollX < maxScroll - 1) View.VISIBLE else View.INVISIBLE
        }
        favoritesScroller.setOnScrollChangeListener { _, _, _, _, _ -> updateShelfFades() }
        favoriteStrip.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateShelfFades() }
        shelfTray.post(updateShelfFades)
        content.addView(shelfTray, verticalMargin(4))
    }

    private fun refreshApps() {
        apps.clear()
        apps.addAll(appRepository.getLaunchableApps())
        favoritePackages.retainAll(apps.mapTo(mutableSetOf()) { it.packageName })
        normalizeFavoriteOrder()
        persistFavorites()
        updateTileMetrics()
        renderFavoriteTiles()

        if (!content.hasFocus()) {
            (favoriteStrip.getChildAt(0)?.takeIf { it.isFocusable }
                ?: allAppsButton).requestFocus()
        }
    }

    private fun refreshMenuApps() {
        apps.clear()
        apps.addAll(appRepository.getLaunchableApps())
        favoritePackages.retainAll(apps.mapTo(mutableSetOf()) { it.packageName })
        normalizeFavoriteOrder()
        persistFavorites()
        updateTileMetrics()
        renderFavoriteTiles()
        appMenu?.updateApps(apps)
        appMenu?.updateSelection(favoritePackages)
    }

    private fun updateTileMetrics() {
        val availableWidthDp = (resources.configuration.screenWidthDp - 60).coerceAtLeast(430)
        val columns = (availableWidthDp / 142).coerceAtLeast(3)
        tileWidthDp = availableWidthDp / columns - 8
        val shelfViewportWidthDp = (availableWidthDp - 20).coerceAtLeast(360)
        // Show five tiles and a small preview of the next one in the initial viewport.
        homeTileWidthDp = (shelfViewportWidthDp / columns - 11).coerceAtLeast(100)
        shelfTray.layoutParams = shelfTray.layoutParams.apply {
            height = dp(homeTileWidthDp * 9 / 16 + 26)
        }
    }

    private fun renderFavoriteTiles(focusedIndex: Int? = null, focusedPackage: String? = null) {
        favoriteStrip.removeAllViews()
        val favoriteApps = favoritePackages.mapNotNull { packageName ->
            apps.find { it.packageName == packageName }
        }
        if (favoriteApps.isEmpty()) {
            favoriteStrip.addView(
                launcherLabel("Elige qué apps quieres ver aquí", 13, LauncherPalette.muted).apply {
                    gravity = Gravity.CENTER_VERTICAL
                },
                LinearLayout.LayoutParams(-1, dp(40)),
            )
            return
        }

        favoriteApps.forEachIndexed { index, app ->
            favoriteStrip.addView(createHomeTile(app, index), favoriteTileParams())
            if (focusedIndex == index || app.packageName == focusedPackage) {
                favoriteStrip.getChildAt(index)?.requestFocus()
            }
        }
    }

    private fun createHomeTile(app: TvApp, index: Int) = LauncherAppTile(
        activity = this,
        app = app,
        isFavorite = true,
        favoriteIndex = index,
        tileWidthDp = homeTileWidthDp,
        isEditingFavorites = { false },
        onSelect = ::launchApp,
        onLongSelect = { selected -> toggleHomeVisibility(selected) },
        onReorder = { false },
        reserveTooltipSpace = false,
        scaleOnFocus = false,
        onFocusChanged = { focusAnchor, positionAnchor, title, focused ->
            if (focused) tooltip.showBelow(focusAnchor, title, shelfTray, positionAnchor) else tooltip.hide()
        },
    )

    private fun openAppMenu(mode: AppMenuMode) {
        if (appMenu != null || settingsOverlay != null) return
        tooltip.hide()
        returnFocus = currentFocus ?: allAppsButton
        appMenu = AppMenuOverlay(
            activity = this,
            mode = mode,
            apps = apps,
            selectedPackages = favoritePackages,
            onClose = ::closeAppMenu,
            onOpenApp = { app ->
                closeAppMenu()
                launchApp(app)
            },
            onToggleHome = { app -> toggleHomeVisibility(app) },
            onReorderHomeApp = ::reorderHomeApp,
            onAppFocusChanged = { focusAnchor, positionAnchor, title, focused ->
                if (focused) tooltip.showFor(focusAnchor, title, positionAnchor) else tooltip.hide()
            },
        ).also { overlay ->
            rootFrame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
            overlay.focusInitialItem()
        }
    }

    private fun closeAppMenu() {
        val menu = appMenu ?: return
        rootFrame.removeView(menu)
        appMenu = null
        if (returnToLauncherSettings) {
            returnToLauncherSettings = false
            returnFocus = null
            openLauncherSettings()
            return
        }
        (returnFocus?.takeIf { it.isShown && it.isFocusable } ?: allAppsButton).requestFocus()
        returnFocus = null
    }

    private fun openLauncherSettings() {
        if (settingsOverlay != null || appMenu != null) return
        tooltip.hide()
        settingsOverlay = LauncherSettingsOverlay(
            activity = this,
            selectedAppCount = favoritePackages.size,
            onClose = ::closeLauncherSettings,
            onManageHomeApps = {
                returnToLauncherSettings = true
                closeLauncherSettings(requestFocus = false)
                openAppMenu(AppMenuMode.HOME_SELECTION)
            },
            onChooseDefaultLauncher = ::openHomeSettings,
            onAbout = ::showLauncherAbout,
        ).also { overlay ->
            rootFrame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
            overlay.focusInitialItem()
        }
    }

    private fun closeLauncherSettings(requestFocus: Boolean = true) {
        val overlay = settingsOverlay ?: return
        rootFrame.removeView(overlay)
        settingsOverlay = null
        if (requestFocus) allAppsButton.requestFocus()
    }

    private fun toggleHomeVisibility(app: TvApp): Boolean {
        val wasSelected = favoritePackages.remove(app.packageName)
        if (!wasSelected) {
            if (favoritePackages.size >= FavoritesStore.MAX_FAVORITES) {
                Toast.makeText(this, "Puedes elegir hasta ${FavoritesStore.MAX_FAVORITES} apps", Toast.LENGTH_SHORT).show()
                return false
            }
            favoritePackages.add(app.packageName)
        }
        normalizeFavoriteOrder()
        persistFavorites()
        renderFavoriteTiles()
        appMenu?.updateSelection(favoritePackages)
        val message = if (wasSelected) "${app.label} quitada de Inicio" else "${app.label} añadida a Inicio"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun reorderHomeApp(packageName: String, direction: Int): Boolean {
        val from = favoritePackages.indexOf(packageName)
        if (apps.find { it.packageName == packageName }?.isInputShortcut == true) return false
        val reorderableCount = favoritePackages.size -
            if (favoritePackages.lastOrNull()?.let { pkg -> apps.find { it.packageName == pkg }?.isInputShortcut } == true) 1 else 0
        val to = from + direction
        if (from < 0 || to !in 0 until reorderableCount) return false
        favoritePackages.add(to, favoritePackages.removeAt(from))
        normalizeFavoriteOrder()
        persistFavorites()
        renderFavoriteTiles(focusedIndex = to)
        appMenu?.updateSelection(favoritePackages)
        return true
    }

    private fun persistFavorites() = favoritesStore.save(favoritePackages)

    private fun normalizeFavoriteOrder() {
        val inputsShortcut = apps.firstOrNull { it.isInputShortcut }?.packageName ?: return
        if (favoritePackages.remove(inputsShortcut)) favoritePackages.add(inputsShortcut)
    }

    private fun launchApp(app: TvApp) {
        if (app.isInputShortcut) {
            openInputs()
            return
        }
        val intent = appRepository.getLaunchIntent(app.packageName)
        if (intent == null) {
            Toast.makeText(this, "No se pudo abrir ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No se pudo abrir ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAndroidSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Ajustes no disponibles", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleSettingsMenu() {
        if (settingsMenu?.isShowing == true) {
            settingsMenu?.dismiss()
            return
        }
        tooltip.hide()
        settingsMenu = SettingsDropdownMenu(
            activity = this,
            onLauncherSettings = ::openLauncherSettings,
            onProjectorSettings = ::openProjectorSettings,
            onAndroidSettings = ::openAndroidSettings,
        ).also { it.show(settingsButton) }
    }

    private fun openProjectorSettings() {
        val intent = Intent().setComponent(
            ComponentName("com.htc.htcsettings", "com.htc.activity.HomeActivity"),
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Ajustes del proyector no disponibles en este firmware", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "La selección del launcher no está disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLauncherAbout() {
        val version = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "1.0"
        }
        AlertDialog.Builder(this)
            .setTitle("HY300Pro Launcher")
            .setMessage("Versión ${version ?: "1.0"}\nLauncher ligero para Android TV y el proyector HY300Pro.")
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun openInputs() {
        val intent = Intent().setComponent(
            ComponentName("com.softwinner.awsource", "com.softwinner.awsource.MainActivity"),
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Selector de entradas no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPowerUnavailable() {
        AlertDialog.Builder(this)
            .setTitle("Apagar proyector")
            .setMessage("Este firmware no permite apagar el proyector desde una app. Usa el botón físico de encendido del dispositivo.")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun actionButton(
        title: String,
        icon: Int,
        iconTint: Int = LauncherPalette.text,
        onClick: () -> Unit,
    ) = ActionIconButton(this, title, icon, iconTint) { anchor, tooltipTitle, focused ->
        if (focused) tooltip.showFor(anchor, tooltipTitle) else tooltip.hide()
    }.apply {
        setOnClickListener { onClick() }
    }

    private fun applyTvWindowFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun spacedActionParams() = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
        leftMargin = dp(8)
    }

    private fun favoriteTileParams() = LinearLayout.LayoutParams(
        dp(homeTileWidthDp),
        dp(homeTileWidthDp * 9 / 16 + 2),
    ).apply {
        rightMargin = dp(10)
        topMargin = dp(3)
        bottomMargin = dp(3)
    }

    private fun verticalMargin(value: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(value)
    }
}
