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
        val corners = mapOf(
            ProjectorCorner.TOP_LEFT to ProjectorPoint(int("persist.display.keystone_ltx"), int("persist.display.keystone_lty")),
            ProjectorCorner.TOP_RIGHT to ProjectorPoint(int("persist.display.keystone_rtx"), int("persist.display.keystone_rty")),
            ProjectorCorner.BOTTOM_RIGHT to ProjectorPoint(int("persist.display.keystone_rbx"), int("persist.display.keystone_rby")),
            ProjectorCorner.BOTTOM_LEFT to ProjectorPoint(int("persist.display.keystone_lbx"), int("persist.display.keystone_lby")),
        )
        return ProjectorImageState(
            zoom = (20 - int("persist.sys.zoom_value", 20)).coerceIn(0, 20),
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
        val inset = ((20 - zoom) * (1000.0 / 120.0)).roundToInt()
        val overscan = 80 + zoom
        val matrix = List(8) { inset }
        connection.shell(
            "setprop persist.sys.zoom_value ${20 - zoom}; " +
                "setprop persist.sys.zoom.value '${matrix.joinToString(",")}'; " +
                "setprop persist.vendor.overscan.main 'overscan $overscan,$overscan,$overscan,$overscan'",
        )
        applySurfaceMatrix(connection, matrix)
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
    ): ProjectorImageState {
        val connection = rootConnection(waitingForApproval)
        val state = cachedState ?: readState(waitingForApproval)
        val current = state.corners.getValue(corner)
        val updated = current.copy(
            x = (current.x + deltaX).coerceIn(0, 500),
            y = (current.y + deltaY).coerceIn(0, 500),
        )
        val properties = when (corner) {
            ProjectorCorner.TOP_LEFT -> listOf("persist.display.keystone_ltx" to updated.x, "persist.display.keystone_lty" to updated.y)
            ProjectorCorner.TOP_RIGHT -> listOf("persist.display.keystone_rtx" to updated.x, "persist.display.keystone_rty" to updated.y)
            ProjectorCorner.BOTTOM_RIGHT -> listOf("persist.display.keystone_rbx" to updated.x, "persist.display.keystone_rby" to updated.y)
            ProjectorCorner.BOTTOM_LEFT -> listOf("persist.display.keystone_lbx" to updated.x, "persist.display.keystone_lby" to updated.y)
        }
        val next = state.corners + (corner to updated)
        val command = properties.joinToString("; ") { (name, number) -> "setprop $name $number" } +
            "; " + surfaceMatrixCommand(matrixForCorners(next))
        val result = connection.shell(command)
        verifySurfaceMatrixResult(result)
        return state.copy(corners = next).also { cachedState = it }
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

    private fun applySurfaceMatrix(connection: Adb, matrix: List<Int>) {
        val result = connection.shell(surfaceMatrixCommand(matrix))
        verifySurfaceMatrixResult(result)
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
