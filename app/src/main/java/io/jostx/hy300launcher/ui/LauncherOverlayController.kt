package io.jostx.hy300launcher.ui

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import io.jostx.hy300launcher.model.TvApp

/** Coordinates modal app and settings surfaces over the home screen. */
class LauncherOverlayController(
    private val activity: Activity,
    private val root: FrameLayout,
    private val fallbackFocus: View,
    private val onHideTooltip: () -> Unit,
    private val onShowTooltip: (View, View, String) -> Unit,
    private val apps: () -> List<TvApp>,
    private val selectedPackages: () -> List<String>,
    private val onToggleHome: (TvApp) -> Unit,
    private val onReorderHomeApp: (String, Int) -> Boolean,
    private val onLaunchApp: (TvApp) -> Unit,
    private val onChooseDefaultLauncher: () -> Unit,
    private val onAbout: () -> Unit,
    private val onProjectorSettings: () -> Unit,
    private val onAndroidSettings: () -> Unit,
) {
    private var appMenu: AppMenuOverlay? = null
    private var settingsOverlay: LauncherSettingsOverlay? = null
    private var settingsMenu: SettingsDropdownMenu? = null
    private var returnFocus: View? = null
    private var returnToLauncherSettings = false

    val hasAppMenu: Boolean
        get() = appMenu != null

    val hasSettingsOverlay: Boolean
        get() = settingsOverlay != null

    val hasBlockingOverlay: Boolean
        get() = appMenu != null || settingsOverlay != null

    fun handleBack(): Boolean = when {
        appMenu != null -> { closeAppMenu(); true }
        settingsOverlay != null -> { closeLauncherSettings(); true }
        settingsMenu?.isShowing == true -> { settingsMenu?.dismiss(); true }
        else -> false
    }

    fun updateApps(items: List<TvApp>) {
        appMenu?.updateApps(items)
        appMenu?.updateSelection(selectedPackages())
    }

    fun updateSelection() {
        appMenu?.updateSelection(selectedPackages())
    }

    fun openAppCatalog() = openAppMenu(AppMenuMode.CATALOG)

    fun toggleSettingsMenu(anchor: View) {
        if (settingsMenu?.isShowing == true) {
            settingsMenu?.dismiss()
            return
        }
        onHideTooltip()
        settingsMenu = SettingsDropdownMenu(
            activity = activity,
            onLauncherSettings = ::openLauncherSettings,
            onProjectorSettings = onProjectorSettings,
            onAndroidSettings = onAndroidSettings,
        ).also { it.show(anchor) }
    }

    private fun openAppMenu(mode: AppMenuMode) {
        if (appMenu != null || settingsOverlay != null) return
        onHideTooltip()
        returnFocus = root.findFocus() ?: fallbackFocus
        appMenu = AppMenuOverlay(
            activity = activity,
            mode = mode,
            apps = apps(),
            selectedPackages = selectedPackages(),
            onClose = ::closeAppMenu,
            onOpenApp = { app ->
                closeAppMenu()
                onLaunchApp(app)
            },
            onToggleHome = onToggleHome,
            onReorderHomeApp = onReorderHomeApp,
            onAppFocusChanged = { focusAnchor, positionAnchor, title, focused ->
                if (focused) onShowTooltip(focusAnchor, positionAnchor, title)
                else onHideTooltip()
            },
        ).also { overlay ->
            root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
            overlay.focusInitialItem()
        }
    }

    private fun openLauncherSettings() {
        if (settingsOverlay != null || appMenu != null) return
        onHideTooltip()
        settingsOverlay = LauncherSettingsOverlay(
            activity = activity,
            selectedAppCount = selectedPackages().size,
            onManageHomeApps = {
                returnToLauncherSettings = true
                closeLauncherSettings(requestFocus = false)
                openAppMenu(AppMenuMode.HOME_SELECTION)
            },
            onChooseDefaultLauncher = onChooseDefaultLauncher,
            onAbout = onAbout,
        ).also { overlay ->
            root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
            overlay.focusInitialItem()
        }
    }

    private fun closeAppMenu() {
        val menu = appMenu ?: return
        root.removeView(menu)
        appMenu = null
        if (returnToLauncherSettings) {
            returnToLauncherSettings = false
            returnFocus = null
            openLauncherSettings()
            return
        }
        (returnFocus?.takeIf { it.isShown && it.isFocusable } ?: fallbackFocus).requestFocus()
        returnFocus = null
    }

    private fun closeLauncherSettings(requestFocus: Boolean = true) {
        val overlay = settingsOverlay ?: return
        root.removeView(overlay)
        settingsOverlay = null
        if (requestFocus) fallbackFocus.requestFocus()
    }

}
