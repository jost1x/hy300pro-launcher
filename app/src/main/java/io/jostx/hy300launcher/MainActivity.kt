package io.jostx.hy300launcher

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import io.jostx.hy300launcher.data.Adb
import io.jostx.hy300launcher.data.FavoritesStore
import io.jostx.hy300launcher.data.InstalledAppsRepository
import io.jostx.hy300launcher.model.TvApp
import io.jostx.hy300launcher.ui.AppMenuMode
import io.jostx.hy300launcher.ui.LauncherHomeScreen
import io.jostx.hy300launcher.ui.LauncherOverlayController
import io.jostx.hy300launcher.ui.dp
import io.jostx.hy300launcher.ui.glassRounded
import io.jostx.hy300launcher.ui.uiColor
import io.jostx.hy300launcher.ui.uiDimen
import java.io.IOException
import java.util.concurrent.Executors

/** Android lifecycle and launcher actions; view composition lives in UI components. */
class MainActivity : Activity() {
    private lateinit var appRepository: InstalledAppsRepository
    private lateinit var favoritesStore: FavoritesStore
    private lateinit var homeScreen: LauncherHomeScreen
    private lateinit var overlays: LauncherOverlayController
    private val apps = mutableListOf<TvApp>()
    private val favoritePackages = mutableListOf<String>()
    private val powerExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var powerRequestPending = false
    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private var wifiStatusCallback: ConnectivityManager.NetworkCallback? = null
    private var bluetoothReceiverRegistered = false
    private val bluetoothStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshConnectivityStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appRepository = InstalledAppsRepository(packageManager, packageName)
        favoritesStore = FavoritesStore(this)
        favoritePackages += favoritesStore.load().take(FavoritesStore.MAX_FAVORITES)

        homeScreen = LauncherHomeScreen(
            activity = this,
            onPower = ::showPowerConfirmation,
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
        refreshConnectivityStatus()
        when {
            overlays.hasAppMenu -> refreshMenuApps()
            !overlays.hasSettingsOverlay -> refreshApps()
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(bluetoothStatusReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        bluetoothReceiverRegistered = true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = postConnectivityStatusRefresh()
            override fun onLost(network: Network) = postConnectivityStatusRefresh()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
                postConnectivityStatusRefresh()
        }
        wifiStatusCallback = callback
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            callback,
        )
        refreshConnectivityStatus()
    }

    override fun onStop() {
        wifiStatusCallback?.let { callback ->
            connectivityManager.unregisterNetworkCallback(callback)
            wifiStatusCallback = null
        }
        if (bluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothStatusReceiver)
            bluetoothReceiverRegistered = false
        }
        super.onStop()
    }

    @Deprecated("Deprecated in Android, retained for TV Back-key handling")
    override fun onBackPressed() {
        if (!overlays.handleBack()) super.onBackPressed()
    }

    override fun onDestroy() {
        powerExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun refreshApps() {
        loadInstalledApps()
        if (!homeScreen.hasContentFocus) homeScreen.focusInitialItem()
    }

    private fun postConnectivityStatusRefresh() {
        mainHandler.post(::refreshConnectivityStatus)
    }

    private fun refreshConnectivityStatus() {
        if (!::homeScreen.isInitialized) return
        val activeNetwork = connectivityManager.activeNetwork
        val wifiConnected = activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: false
        val wifiEnabled = Settings.Global.getInt(contentResolver, "wifi_on", 0) == 1
        val bluetoothEnabled = Settings.Global.getInt(contentResolver, "bluetooth_on", 0) == 1
        homeScreen.updateConnectivityStatus(wifiEnabled, wifiConnected, bluetoothEnabled)
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
            ComponentName("com.htc.htcsettings", "com.htc.activity.ProjectionActivity"),
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Ajustes de proyección no disponibles en este firmware", Toast.LENGTH_SHORT).show()
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
        showLauncherDialog(
            title = "HY300Pro Launcher",
            message = "Versión ${version ?: "1.0"}\nLauncher ligero para Android TV y el proyector HY300Pro.",
            button = "Cerrar",
        )
    }

    private fun showLauncherDialog(title: String, message: String, button: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(button, null)
            .create()
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(glassRounded(
                uiColor(R.color.launcher_glass_dialog_fill),
                uiDimen(R.dimen.radius_dialog),
                uiColor(R.color.launcher_glass_dialog_edge),
            ))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.68f }
        }
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

    private fun showPowerConfirmation() {
        if (powerRequestPending) return
        powerRequestPending = true
        powerExecutor.execute {
            try {
                Adb.connect(applicationContext, POWER_AUTH_TIMEOUT_MS) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Acepta en el proyector «Permitir depuración USB» y marca «Permitir siempre».",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }.use { adb ->
                    val result = adb.shell(OPEN_SHUTDOWN_CONFIRMATION)
                    if (!result.contains("Starting: Intent") ||
                        result.contains("Error:") ||
                        result.contains("Exception") ||
                        result.contains("Permission Denial")
                    ) {
                        throw IOException("No se pudo abrir la confirmación del sistema")
                    }
                }
            } catch (error: IOException) {
                mainHandler.post { showPowerUnavailable(error) }
            } catch (_: SecurityException) {
                mainHandler.post { showPowerUnavailable(IOException("Permiso de depuración denegado")) }
            } finally {
                mainHandler.post { powerRequestPending = false }
            }
        }
    }

    private fun showPowerUnavailable(error: IOException) {
        val message = when {
            error.message?.contains("port 5555 closed") == true ->
                "Activa la depuración USB en el proyector y vuelve a intentarlo."
            error.message?.contains("wasn't accepted") == true || error.message?.contains("rejected") == true ->
                "Acepta la solicitud «Permitir depuración USB» en el proyector y vuelve a intentarlo."
            else -> "No se pudo abrir la confirmación de apagado. ${error.message.orEmpty()}"
        }
        showLauncherDialog("No se pudo abrir el apagado", message, "Entendido")
    }

    private companion object {
        const val POWER_AUTH_TIMEOUT_MS = 120_000
        const val OPEN_SHUTDOWN_CONFIRMATION =
            "am start -a com.android.internal.intent.action.REQUEST_SHUTDOWN" +
                " -n android/com.android.internal.app.ShutdownActivity" +
                " --ez android.intent.extra.KEY_CONFIRM true"
    }

    private fun applyTvWindowFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
