package io.jostx.hy300launcher.data

import android.content.Context
import java.io.Closeable
import java.io.IOException
import kotlin.math.roundToInt

enum class ProjectorCorner(val propertySuffix: String) {
    TOP_LEFT("ltx"),
    TOP_RIGHT("rtx"),
    BOTTOM_RIGHT("rbx"),
    BOTTOM_LEFT("lbx"),
}

enum class ProjectorProjectionMode { FRONT, REAR, CEILING, REAR_CEILING }

data class ProjectorPoint(val x: Int, val y: Int)

data class ProjectorImageState(
    val zoom: Int,
    val autoKeystone: Boolean,
    val manualKeystoneEnabled: Boolean,
    val manualKeystoneValue: Int,
    val corners: Map<ProjectorCorner, ProjectorPoint>,
    val supportsZoom: Boolean,
    val supportsAutoKeystone: Boolean,
    val supportsManualKeystone: Boolean,
    val supportsFourCorner: Boolean,
) {
    companion object {
        val CORNER_ORDER = listOf(
            ProjectorCorner.TOP_LEFT,
            ProjectorCorner.TOP_RIGHT,
            ProjectorCorner.BOTTOM_RIGHT,
            ProjectorCorner.BOTTOM_LEFT,
        )
    }
}

/** Firmware bridge for the HY300 Pro's OEM image pipeline, accessed through its local root ADB daemon. */
class ProjectorImageRepository(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private var adb: Adb? = null
    private var cachedState: ProjectorImageState? = null

    /** Returns the neutral virtual corner map for the current firmware zoom level. */
    fun neutralCornersForZoom(zoom: Int): Map<ProjectorCorner, ProjectorPoint> =
        neutralCorners(zoomInset(zoom))

    @Synchronized
    fun readState(waitingForApproval: () -> Unit): ProjectorImageState {
        val connection = rootConnection(waitingForApproval)
        val raw = connection.shell(
            "for p in ${PROPERTY_NAMES.joinToString(" ")}; do printf '%s=' \"\$p\"; getprop \"\$p\"; done; " +
                "printf '__SYSFS__='; [ -e '$MANUAL_KEYSTONE_NODE' ] && echo yes || echo no; " +
                "printf '__CONFIG__\\n'; cat /product/etc/config.ini 2>/dev/null",
        )
        val values = raw.lineSequence()
            .takeWhile { it != "__CONFIG__" }
            .mapNotNull { line ->
                val split = line.indexOf('=')
                if (split <= 0) null else line.substring(0, split) to line.substring(split + 1).trim()
            }.toMap()
        val config = raw.substringAfter("__CONFIG__\n", "")
        val sysfsSupportsManual = raw.lineSequence().firstOrNull { it.startsWith("__SYSFS__=") }
            ?.substringAfter('=') == "yes"
        fun int(name: String, default: Int = 0) = values[name]?.toIntOrNull() ?: default
        fun bool(name: String) = values[name] == "1" || values[name].equals("true", ignoreCase = true)
        val zoom = (20 - int("persist.sys.zoom_value", 20)).coerceIn(0, 20)
        val persistedCorners = mapOf(
            ProjectorCorner.TOP_LEFT to ProjectorPoint(int("persist.display.keystone_ltx"), int("persist.display.keystone_lty")),
            ProjectorCorner.TOP_RIGHT to ProjectorPoint(int("persist.display.keystone_rtx"), int("persist.display.keystone_rty")),
            ProjectorCorner.BOTTOM_RIGHT to ProjectorPoint(int("persist.display.keystone_rbx"), int("persist.display.keystone_rby")),
            ProjectorCorner.BOTTOM_LEFT to ProjectorPoint(int("persist.display.keystone_lbx"), int("persist.display.keystone_lby")),
        )
        // Older zoom writes changed SurfaceFlinger without updating the corner properties.
        // If all persisted corners are untouched at zero, materialize the current neutral zoom shape.
        val corners = if (zoom < 20 && persistedCorners.values.all { it.x == 0 && it.y == 0 }) {
            neutralCorners(zoomInset(zoom))
        } else {
            persistedCorners
        }
        return ProjectorImageState(
            zoom = zoom,
            autoKeystone = bool("persist.sys.tpryauto"),
            manualKeystoneEnabled = bool("persist.sys.prj.keystone"),
            manualKeystoneValue = int("persist.sys.prj.manualkeystone").coerceIn(0, 90),
            corners = corners,
            supportsZoom = readFlag(config, "screenZoom"),
            supportsAutoKeystone = readFlag(config, "autoKeystone"),
            supportsManualKeystone = readFlag(config, "manualKeystone") && sysfsSupportsManual,
            supportsFourCorner = true,
        ).also { cachedState = it }
    }

    @Synchronized
    fun setZoom(value: Int, waitingForApproval: () -> Unit): ProjectorImageState {
        val zoom = value.coerceIn(0, 20)
        val connection = rootConnection(waitingForApproval)
        val current = cachedState ?: readState(waitingForApproval)
        val corners = scaleCorners(current.corners, current.zoom, zoom)
        val matrix = matrixForCorners(corners)
        val overscan = 80 + zoom
        val properties = cornerProperties(corners).joinToString("; ") { (name, number) -> "setprop $name $number" }
        val result = connection.shell(
            "setprop persist.sys.zoom_value ${20 - zoom}; " +
                "setprop persist.vendor.overscan.main 'overscan $overscan,$overscan,$overscan,$overscan'; " +
                "$properties; " +
                "${surfaceMatrixCommand(matrix)}; " +
                "setprop persist.sys.zoom.value '${matrix.joinToString(",")}'",
        )
        verifySurfaceMatrixResult(result)
        val state = readState(waitingForApproval)
        if (state.zoom != zoom) throw IOException("El firmware no confirmó el valor de zoom")
        return state
    }

    @Synchronized
    fun adjustCorner(
        corner: ProjectorCorner,
        deltaX: Int,
        deltaY: Int,
        waitingForApproval: () -> Unit,
    ): ProjectorImageState = adjustCorners(mapOf(corner to (deltaX to deltaY)), waitingForApproval)

    @Synchronized
    fun adjustCorners(
        deltas: Map<ProjectorCorner, Pair<Int, Int>>,
        waitingForApproval: () -> Unit,
    ): ProjectorImageState {
        val connection = rootConnection(waitingForApproval)
        val state = cachedState ?: readState(waitingForApproval)
        val next = state.corners.toMutableMap()
        deltas.forEach { (corner, delta) ->
            val current = next.getValue(corner)
            next[corner] = current.copy(
                x = (current.x + delta.first).coerceIn(0, 500),
                y = (current.y + delta.second).coerceIn(0, 500),
            )
        }
        val matrix = matrixForCorners(next)
        val properties = cornerProperties(next).joinToString("; ") { (name, number) -> "setprop $name $number" }
        val command = "$properties; ${surfaceMatrixCommand(matrix)}; " +
            "setprop persist.sys.zoom.value '${matrix.joinToString(",")}'"
        val result = connection.shell(command)
        verifySurfaceMatrixResult(result)
        val verified = readState(waitingForApproval)
        if (verified.corners != next) throw IOException("El firmware no confirmó el movimiento de la esquina")
        return verified.also { cachedState = it }
    }

    @Synchronized
    fun setCorners(
        corners: Map<ProjectorCorner, ProjectorPoint>,
        waitingForApproval: () -> Unit,
    ): ProjectorImageState {
        require(ProjectorCorner.entries.all { corner ->
            corners[corner]?.let { it.x in 0..500 && it.y in 0..500 } == true
        }) { "El ajuste calculado está fuera del rango permitido." }
        val connection = rootConnection(waitingForApproval)
        val matrix = matrixForCorners(corners)
        val properties = cornerProperties(corners).joinToString("; ") { (name, number) -> "setprop $name $number" }
        val result = connection.shell(
            "$properties; ${surfaceMatrixCommand(matrix)}; " +
                "setprop persist.sys.zoom.value '${matrix.joinToString(",")}'",
        )
        verifySurfaceMatrixResult(result)
        val verified = readState(waitingForApproval)
        if (verified.corners != corners) throw IOException("El firmware no confirmó las cuatro esquinas")
        return verified.also { cachedState = it }
    }

    @Synchronized
    fun setManualKeystone(
        enabled: Boolean,
        value: Int,
        waitingForApproval: () -> Unit,
    ): ProjectorImageState {
        val connection = rootConnection(waitingForApproval)
        if (!readState(waitingForApproval).supportsManualKeystone) {
            throw IOException("El firmware no expone el control manual de trapecio")
        }
        val level = value.coerceIn(0, 90)
        connection.shell(
            "setprop persist.sys.prj.keystone ${if (enabled) 1 else 0}; " +
                "setprop persist.sys.prj.manualkeystone $level; " +
                "echo $level > '$MANUAL_KEYSTONE_NODE'",
        )
        return readState(waitingForApproval)
    }

    @Synchronized
    fun setAutoKeystone(enabled: Boolean, waitingForApproval: () -> Unit): ProjectorImageState {
        val connection = rootConnection(waitingForApproval)
        if (enabled) {
            connection.shell(
                "setprop persist.sys.tpryauto 1; " +
                    "setprop sys.temp_pass 1; setprop sys.auto_report_angle 1; " +
                    "am broadcast --user 0 -a android.intent.hotack_keystone --ei ratio 1; " +
                    "am broadcast --user 0 -a com.htc.auto_keystone",
            )
        } else {
            connection.shell("setprop persist.sys.tpryauto 0")
        }
        val state = readState(waitingForApproval)
        if (state.autoKeystone != enabled) throw IOException("El firmware no confirmó la corrección automática")
        return state
    }

    @Synchronized
    fun readOrientation(waitingForApproval: () -> Unit): ProjectorProjectionMode =
        orientationResult(rootConnection(waitingForApproval), "read").first

    @Synchronized
    fun setOrientation(mode: ProjectorProjectionMode, waitingForApproval: () -> Unit): ProjectorProjectionMode {
        val connection = rootConnection(waitingForApproval)
        val (_, panelType) = orientationResult(connection, "read")
        val raw = orientationOrder(panelType).indexOf(mode)
        val (verified, _) = orientationResult(connection, "set $raw")
        if (verified != mode) throw IOException("El panel no confirmó la orientación solicitada")
        // Notify the automatic correction service, as the firmware does after a panel change.
        val notification = connection.shell(
            "if [ \"\$" + "(getprop persist.sys.tpryauto)\" = 1 ]; then " +
                "am broadcast --user 0 -a android.intent.projective_mode; fi",
        )
        if (notification.contains("Exception") || notification.contains("Error")) {
            throw IOException("Orientación aplicada; no se pudo avisar a la corrección automática")
        }
        return verified
    }

    private fun orientationResult(connection: Adb, operation: String): Pair<ProjectorProjectionMode, String> {
        // app_process loads our own APK; no OEM application classes or components are used.
        val apkPath = appContext.applicationInfo.sourceDir.replace("'", "'\"'\"'")
        val output = connection.shell(
            "CLASSPATH='$apkPath' app_process /system/bin " +
                "io.jostx.hy300launcher.data.ProjectorOrientationBridge $operation",
        )
        val lines = output.lineSequence().map { it.trim() }.toList()
        lines.firstOrNull { it.startsWith("PANEL_ERROR=") }?.let {
            throw IOException(it.substringAfter('='))
        }
        val raw = lines.firstOrNull { it.startsWith("PANEL_MODE=") }?.substringAfter('=')?.toIntOrNull()
        val panelType = lines.firstOrNull { it.startsWith("PANEL_TYPE=") }?.substringAfter('=') ?: ""
        val mode = raw?.let { orientationOrder(panelType).getOrNull(it) }
            ?: throw IOException("El servicio de orientación no está disponible en este firmware")
        return mode to panelType
    }

    private fun orientationOrder(panelType: String): List<ProjectorProjectionMode> =
        if (panelType.equals("t03", ignoreCase = true)) listOf(
            ProjectorProjectionMode.REAR_CEILING, ProjectorProjectionMode.CEILING,
            ProjectorProjectionMode.FRONT, ProjectorProjectionMode.REAR,
        ) else ProjectorProjectionMode.entries.toList()

    @Synchronized
    override fun close() {
        adb?.close()
        adb = null
        cachedState = null
    }

    private fun rootConnection(waitingForApproval: () -> Unit): Adb {
        adb?.let { existing ->
            if (existing.shell("id -u").trim() == "0") return existing
            existing.close()
            adb = null
        }
        val connected = Adb.connect(appContext, ADB_APPROVAL_TIMEOUT_MS) { waitingForApproval() }
        val uid = connected.shell("id -u").trim()
        if (uid != "0") {
            connected.close()
            throw IOException("El control requiere el acceso de sistema del firmware")
        }
        adb = connected
        return connected
    }

    private fun surfaceMatrixCommand(matrix: List<Int>): String {
        require(matrix.size == 8)
        val args = matrix.joinToString(" ") { "f ${it / 1000.0}" }
        return "service call SurfaceFlinger 1050 $args"
    }

    private fun verifySurfaceMatrixResult(result: String) {
        if (result.contains("Exception", ignoreCase = true) || result.contains("error", ignoreCase = true)) {
            throw IOException("SurfaceFlinger rechazó la corrección de imagen")
        }
    }

    /** Apply the zoom as a uniform scale around the display center to the current four-corner shape. */
    private fun scaleCorners(
        corners: Map<ProjectorCorner, ProjectorPoint>,
        fromZoom: Int,
        toZoom: Int,
    ): Map<ProjectorCorner, ProjectorPoint> {
        val oldScale = zoomScale(fromZoom)
        val newScale = zoomScale(toZoom)
        val scale = newScale / oldScale
        fun mapPoint(corner: ProjectorCorner, point: ProjectorPoint): ProjectorPoint {
            val rightSide = corner == ProjectorCorner.TOP_RIGHT || corner == ProjectorCorner.BOTTOM_RIGHT
            val bottomSide = corner == ProjectorCorner.BOTTOM_RIGHT || corner == ProjectorCorner.BOTTOM_LEFT
            val x = if (rightSide) 1000 - point.x else point.x
            val y = if (bottomSide) 1000 - point.y else point.y
            val scaledX = (500 + (x - 500) * scale).roundToInt().coerceIn(0, 1000)
            val scaledY = (500 + (y - 500) * scale).roundToInt().coerceIn(0, 1000)
            return ProjectorPoint(
                x = (if (rightSide) 1000 - scaledX else scaledX).coerceIn(0, 500),
                y = (if (bottomSide) 1000 - scaledY else scaledY).coerceIn(0, 500),
            )
        }
        return corners.mapValues { (corner, point) -> mapPoint(corner, point) }
    }

    private fun zoomInset(zoom: Int) = ((20 - zoom.coerceIn(0, 20)) * (1000.0 / 120.0)).roundToInt()

    private fun zoomScale(zoom: Int) = 1.0 - (2.0 * zoomInset(zoom) / 1000.0)

    private fun neutralCorners(inset: Int) = ProjectorCorner.entries.associateWith { ProjectorPoint(inset, inset) }

    private fun cornerProperties(corners: Map<ProjectorCorner, ProjectorPoint>) = listOf(
        "persist.display.keystone_ltx" to corners.getValue(ProjectorCorner.TOP_LEFT).x,
        "persist.display.keystone_lty" to corners.getValue(ProjectorCorner.TOP_LEFT).y,
        "persist.display.keystone_rtx" to corners.getValue(ProjectorCorner.TOP_RIGHT).x,
        "persist.display.keystone_rty" to corners.getValue(ProjectorCorner.TOP_RIGHT).y,
        "persist.display.keystone_rbx" to corners.getValue(ProjectorCorner.BOTTOM_RIGHT).x,
        "persist.display.keystone_rby" to corners.getValue(ProjectorCorner.BOTTOM_RIGHT).y,
        "persist.display.keystone_lbx" to corners.getValue(ProjectorCorner.BOTTOM_LEFT).x,
        "persist.display.keystone_lby" to corners.getValue(ProjectorCorner.BOTTOM_LEFT).y,
    )

    private fun matrixForCorners(corners: Map<ProjectorCorner, ProjectorPoint>): List<Int> {
        fun point(corner: ProjectorCorner) = corners.getValue(corner)
        val tl = point(ProjectorCorner.TOP_LEFT)
        val tr = point(ProjectorCorner.TOP_RIGHT)
        val br = point(ProjectorCorner.BOTTOM_RIGHT)
        val bl = point(ProjectorCorner.BOTTOM_LEFT)
        return listOf(bl.x, bl.y, tl.x, tl.y, tr.x, tr.y, br.x, br.y)
    }

    private fun readFlag(config: String, name: String): Boolean {
        val matches = Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*(true|false)")
            .findAll(config)
            .toList()
        return matches.lastOrNull()?.groupValues?.get(1) == "true"
    }

    companion object {
        private const val ADB_APPROVAL_TIMEOUT_MS = 12_000
        private const val MANUAL_KEYSTONE_NODE = "/sys/devices/ff140000.i2c/i2c-2/2-001b/manual_Keystone_mode"
        private val PROPERTY_NAMES = listOf(
            "persist.sys.zoom_value",
            "persist.sys.tpryauto",
            "persist.sys.prj.keystone",
            "persist.sys.prj.manualkeystone",
            "persist.sys.keystone_lr",
            "persist.display.keystone_ltx",
            "persist.display.keystone_lty",
            "persist.display.keystone_rtx",
            "persist.display.keystone_rty",
            "persist.display.keystone_rbx",
            "persist.display.keystone_rby",
            "persist.display.keystone_lbx",
            "persist.display.keystone_lby",
        )
    }
}
