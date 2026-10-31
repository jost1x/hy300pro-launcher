package io.jostx.hy300launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import io.jostx.hy300launcher.data.FavoritesStore
import io.jostx.hy300launcher.data.InstalledAppsRepository
import io.jostx.hy300launcher.model.TvApp
import io.jostx.hy300launcher.ui.AppMenuMode
import io.jostx.hy300launcher.ui.LauncherHomeScreen
import io.jostx.hy300launcher.ui.LauncherOverlayController
import io.jostx.hy300launcher.ui.LauncherPalette
import io.jostx.hy300launcher.ui.dp

/** Android lifecycle and launcher actions; view composition lives in UI components. */
class MainActivity : Activity() {
    private lateinit var appRepository: InstalledAppsRepository
    private lateinit var favoritesStore: FavoritesStore
    private lateinit var homeScreen: LauncherHomeScreen
    private lateinit var overlays: LauncherOverlayController
    private val apps = mutableListOf<TvApp>()
    private val favoritePackages = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appRepository = InstalledAppsRepository(packageManager, packageName)
        favoritesStore = FavoritesStore(this)
        favoritePackages += favoritesStore.load().take(FavoritesStore.MAX_FAVORITES)

        homeScreen = LauncherHomeScreen(
            activity = this,
            onPower = ::showPowerUnavailable,
            onOpenCatalog = { overlays.openAppCatalog() },
            onOpenInputs = ::openInputs,
            onOpenSettingsMenu = { anchor -> overlays.toggleSettingsMenu(anchor) },
            onLaunchApp = ::launchApp,
            onToggleFavorite = ::toggleHomeVisibility,
        )
        overlays = LauncherOverlayController(
            activity = this,
            root = homeScreen,
            fallbackFocus = homeScreen.allAppsButton,
            onHideTooltip = homeScreen::hideTooltip,
            onShowTooltip = homeScreen::showTooltip,
            apps = { apps },
            selectedPackages = { favoritePackages },
            onToggleHome = { app -> toggleHomeVisibility(app) },
            onReorderHomeApp = ::reorderHomeApp,
            onLaunchApp = ::launchApp,
            onChooseDefaultLauncher = ::openHomeSettings,
            onAbout = ::showLauncherAbout,
            onProjectorSettings = ::openProjectorSettings,
            onAndroidSettings = ::openAndroidSettings,
        )
        setContentView(homeScreen)
        applyTvWindowFlags()
        refreshApps()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyTvWindowFlags()
    }

    override fun onResume() {
        super.onResume()
        if (!::homeScreen.isInitialized) return
        when {
            overlays.hasAppMenu -> refreshMenuApps()
            !overlays.hasSettingsOverlay -> refreshApps()
        }
    }

    @Deprecated("Deprecated in Android, retained for TV Back-key handling")
    override fun onBackPressed() {
        if (!overlays.handleBack()) super.onBackPressed()
    }

    private fun refreshApps() {
        loadInstalledApps()
        if (!homeScreen.hasContentFocus) homeScreen.focusInitialItem()
    }

    private fun refreshMenuApps() {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        apps.clear()
        apps.addAll(appRepository.getLaunchableApps())
        favoritePackages.retainAll(apps.mapTo(mutableSetOf()) { it.packageName })
        normalizeFavoriteOrder()
        persistFavorites()
        homeScreen.renderFavorites(apps, favoritePackages)
        overlays.updateApps(apps)
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
        homeScreen.renderFavorites(apps, favoritePackages)
        overlays.updateSelection()
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
        homeScreen.renderFavorites(apps, favoritePackages, focusedIndex = to)
        overlays.updateSelection()
        return true
    }

    private fun persistFavorites() = favoritesStore.save(favoritePackages)

    private fun normalizeFavoriteOrder() {
        val inputsShortcut = apps.firstOrNull { it.isInputShortcut }?.packageName ?: return
        if (favoritePackages.remove(inputsShortcut)) favoritePackages.add(inputsShortcut)
    }

    private fun launchApp(app: TvApp) {
        if (overlays.hasBlockingOverlay) return
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

    private fun applyTvWindowFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
