package io.jostx.hy300launcher.data

import android.content.Context
import android.content.Intent
import io.jostx.hy300launcher.imagecontrol.FirmwareProperties
import java.io.File
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

/** HY300 Pro image controls through the firmware HAL and our authenticated Binder service. */
class ProjectorImageRepository(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val imageControl = ImageControlClient(appContext)
    var geometryUnavailableReason: String? = null
        private set
    private var cachedState: ProjectorImageState? = null

    /** Returns the neutral virtual corner map for the current firmware zoom level. */
    fun neutralCornersForZoom(zoom: Int): Map<ProjectorCorner, ProjectorPoint> =
        neutralCorners(zoomInset(zoom))

    @Synchronized
    fun readState(waitingForApproval: () -> Unit): ProjectorImageState {
        geometryUnavailableReason = try { imageControl.check(); null } catch (error: Exception) {
            error.message ?: "El servicio de imagen no está disponible"
        }
        val values = PROPERTY_NAMES.associateWith { FirmwareProperties.get(it) }
        val config = runCatching { File("/product/etc/config.ini").readText() }.getOrDefault("")
        val sysfsSupportsManual = false // The verified HY300 Pro does not expose this node.
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
            supportsZoom = geometryUnavailableReason == null && readFlag(config, "screenZoom"),
            supportsAutoKeystone = readFlag(config, "autoKeystone"),
            supportsManualKeystone = readFlag(config, "manualKeystone") && sysfsSupportsManual,
            supportsFourCorner = geometryUnavailableReason == null,
        ).also { cachedState = it }
    }

    @Synchronized
    fun setZoom(value: Int, waitingForApproval: () -> Unit): ProjectorImageState {
        val zoom = value.coerceIn(0, 20)
        val current = readState(waitingForApproval)
        val corners = scaleCorners(current.corners, current.zoom, zoom)
        imageControl.apply(matrixForCorners(corners), zoom)
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
        val state = cachedState ?: readState(waitingForApproval)
        val next = state.corners.toMutableMap()
        deltas.forEach { (corner, delta) ->
            val current = next.getValue(corner)
            next[corner] = current.copy(
                x = (current.x + delta.first).coerceIn(0, 500),
                y = (current.y + delta.second).coerceIn(0, 500),
            )
        }
        imageControl.apply(matrixForCorners(next))
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
        imageControl.apply(matrixForCorners(corners))
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
        throw IOException("El firmware no expone el control manual de trapecio")
    }

    @Synchronized
    fun setAutoKeystone(enabled: Boolean, waitingForApproval: () -> Unit): ProjectorImageState {
        if (!readState(waitingForApproval).supportsAutoKeystone) {
            throw IOException("La corrección automática no está disponible en este firmware")
        }
        val keys = listOf("persist.sys.tpryauto", "sys.temp_pass", "sys.auto_report_angle")
        val initial = keys.associateWith { FirmwareProperties.get(it) }
        try {
            FirmwareProperties.set(keys[0], if (enabled) "1" else "0")
            if (enabled) {
                FirmwareProperties.set(keys[1], "1")
                FirmwareProperties.set(keys[2], "1")
                appContext.sendBroadcast(Intent("android.intent.hotack_keystone").putExtra("ratio", 1))
                appContext.sendBroadcast(Intent("com.htc.auto_keystone"))
            }
        } catch (error: Exception) {
            initial.forEach { (key, value) -> FirmwareProperties.set(key, value) }
            throw IOException("No se pudo confirmar la corrección automática", error)
        }
        val state = readState(waitingForApproval)
        if (state.autoKeystone != enabled) throw IOException("El firmware no confirmó la corrección automática")
        return state
    }

    @Synchronized
    fun readOrientation(waitingForApproval: () -> Unit): ProjectorProjectionMode =
        orientationOrder(FirmwareProperties.get("persist.sys.prj.type"))[ProjectorOrientationBridge().read()]

    @Synchronized
    fun setOrientation(mode: ProjectorProjectionMode, waitingForApproval: () -> Unit): ProjectorProjectionMode {
        val order = orientationOrder(FirmwareProperties.get("persist.sys.prj.type"))
        val verified = order[ProjectorOrientationBridge().apply(order.indexOf(mode))]
        if (verified != mode) throw IOException("El panel no confirmó la orientación solicitada")
        if (FirmwareProperties.get("persist.sys.tpryauto") == "1") {
            try { appContext.sendBroadcast(Intent("android.intent.projective_mode")) }
            catch (error: Exception) {
                throw IOException("Orientación aplicada; no se pudo avisar a la corrección automática", error)
            }
        }
        return verified
    }

    private fun orientationOrder(panelType: String): List<ProjectorProjectionMode> =
        if (panelType.equals("t03", ignoreCase = true)) listOf(
            ProjectorProjectionMode.REAR_CEILING, ProjectorProjectionMode.CEILING,
            ProjectorProjectionMode.FRONT, ProjectorProjectionMode.REAR,
        ) else ProjectorProjectionMode.entries.toList()

    @Synchronized
    override fun close() {
        cachedState = null
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
