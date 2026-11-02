package io.jostx.hy300launcher.ui

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.FocusFinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.jostx.hy300launcher.R
import io.jostx.hy300launcher.data.ProjectorCorner
import io.jostx.hy300launcher.data.ProjectorImageRepository
import io.jostx.hy300launcher.data.ProjectorImageState
import java.io.Closeable
import java.util.concurrent.Executors

/** Native image controls backed by the HY300 Pro firmware's local ADB interface. */
class ImageCorrectionOverlay(private val activity: Activity) : LinearLayout(activity), Closeable {
    private val repository = ProjectorImageRepository(activity)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val modes = mutableListOf<ModeRow>()
    private lateinit var status: TextView
    private lateinit var zoomValue: TextView
    private lateinit var footer: TextView
    private lateinit var manualRow: ModeRow
    private lateinit var preview: ProjectionShapePreview
    private lateinit var contentContainer: FrameLayout
    private lateinit var overviewBody: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var headerSubtitle: TextView
    private var cornerEditor: FrameLayout? = null
    private lateinit var headerView: LinearLayout
    private var editingCorners = false
    private var state: ProjectorImageState? = null
    private var selectedMode = -1
    private var selectedCorner = 0
    private var pendingZoom = 0
    private var busy = false

    init {
        orientation = VERTICAL
        setPadding(
            activity.dp(activity.uiDimen(R.dimen.space_48)),
            activity.dp(activity.uiDimen(R.dimen.space_27)),
            activity.dp(activity.uiDimen(R.dimen.space_48)),
            activity.dp(activity.uiDimen(R.dimen.space_27)),
        )
        setBackgroundResource(R.drawable.launcher_background)
        clipChildren = false
        clipToPadding = false

        headerView = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = HORIZONTAL
        }
        val titleBlock = LinearLayout(activity).apply { orientation = VERTICAL }
        headerTitle = activity.launcherLabel("Imagen", R.style.LauncherTextHeading, bold = true)
        headerSubtitle = activity.launcherLabel("Zoom y corrección de geometría", R.style.LauncherTextBody).apply {
                setTextColor(activity.uiColor(R.color.launcher_text_secondary))
            }
        titleBlock.addView(headerTitle)
        titleBlock.addView(
            headerSubtitle,
            LayoutParams(-1, -2).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_3)) },
        )
        headerView.addView(titleBlock, LayoutParams(0, -2, 1f))
        status = statusPill("Conectando…")
        headerView.addView(status)
        addView(headerView, LayoutParams(-1, activity.dp(activity.uiDimen(R.dimen.space_64))))

        val body = LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val previewPanel = LinearLayout(activity).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(activity.dp(activity.uiDimen(R.dimen.space_22)), activity.dp(activity.uiDimen(R.dimen.space_20)), activity.dp(activity.uiDimen(R.dimen.space_22)), activity.dp(activity.uiDimen(R.dimen.space_20)))
            background = activity.rounded(activity.uiColor(R.color.launcher_surface), activity.uiDimen(R.dimen.radius_surface))
        }
        previewPanel.addView(
            activity.launcherLabel("VISTA DE AJUSTE", R.style.LauncherTextCaption, bold = true).apply {
                setTextColor(activity.uiColor(R.color.launcher_text_secondary))
            },
            LayoutParams(-1, -2).apply { bottomMargin = activity.dp(activity.uiDimen(R.dimen.space_8)) },
        )
        val overviewPreview = ProjectionShapePreview(activity, showCalibrationGrid = false)
        preview = overviewPreview
        previewPanel.addView(overviewPreview, LayoutParams(-1, 0, 1f))
        previewPanel.addView(
            activity.launcherLabel("Esquema ilustrativo", R.style.LauncherTextCaption).apply {
                setTextColor(activity.uiColor(R.color.launcher_text_secondary))
            },
            LayoutParams(-1, -2).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_6)) },
        )
        body.addView(previewPanel, LayoutParams(0, -1, 1.15f))

        val controls = LinearLayout(activity).apply {
            orientation = VERTICAL
            setPadding(activity.dp(activity.uiDimen(R.dimen.space_22)), 0, 0, 0)
            clipChildren = false
        }
        val modeData = listOf(
            Triple(R.drawable.ic_monitor_play, "Automática", "Ajusta la geometría disponible"),
            Triple(R.drawable.ic_sliders, "Trapecio manual", "Ajuste manual no expuesto por el firmware"),
            Triple(R.drawable.ic_layout_grid, "Cuatro esquinas", "Elige un vértice y desplázalo"),
        )
        modeData.forEachIndexed { index, (icon, name, detail) ->
            val row = ModeRow(activity, icon, name, detail) {
                when (index) {
                    0 -> toggleAutoKeystone()
                    1 -> showUnavailable("El firmware no expone el control manual de trapecio en este equipo.")
                    2 -> openCornerEditor()
                }
            }
            modes += row
            controls.addView(
                row,
                LayoutParams(-1, activity.dp(activity.uiDimen(R.dimen.space_54))).apply {
                    topMargin = activity.dp(activity.uiDimen(if (index == 0) R.dimen.space_6 else R.dimen.space_4))
                },
            )
            if (index == 1) manualRow = row
        }

        val zoom = LinearLayout(activity).apply {
            orientation = VERTICAL
            setPadding(activity.dp(activity.uiDimen(R.dimen.space_16)), activity.dp(activity.uiDimen(R.dimen.space_12)), activity.dp(activity.uiDimen(R.dimen.space_16)), activity.dp(activity.uiDimen(R.dimen.space_12)))
            background = activity.rounded(activity.uiColor(R.color.launcher_action_surface), activity.uiDimen(R.dimen.radius_surface))
        }
        val zoomHeader = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        zoomHeader.addView(activity.launcherLabel("Zoom", R.style.LauncherTextBody, bold = true), LayoutParams(0, -2, 1f))
        zoomValue = activity.launcherLabel("—", R.style.LauncherTextTitle, bold = true)
        zoomHeader.addView(zoomValue)
        zoom.addView(zoomHeader, LayoutParams(-1, -2))
        val zoomActions = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = HORIZONTAL
            setPadding(0, activity.dp(activity.uiDimen(R.dimen.space_8)), 0, 0)
        }
        zoomActions.addView(adjustButton("−", "Reducir zoom") { changeZoom(-1) }, LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_44)), activity.dp(activity.uiDimen(R.dimen.space_40))))
        zoomActions.addView(View(activity).apply {
            background = activity.rounded(activity.uiColor(R.color.launcher_text_secondary), activity.uiDimen(R.dimen.radius_small))
        }, LayoutParams(0, activity.dp(activity.uiDimen(R.dimen.space_2)), 1f).apply {
            leftMargin = activity.dp(activity.uiDimen(R.dimen.space_12)); rightMargin = activity.dp(activity.uiDimen(R.dimen.space_12))
        })
        zoomActions.addView(adjustButton("+", "Aumentar zoom") { changeZoom(1) }, LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_44)), activity.dp(activity.uiDimen(R.dimen.space_40))))
        zoom.addView(zoomActions, LayoutParams(-1, -2))
        controls.addView(zoom, LayoutParams(-1, -2).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_8)) })
        body.addView(controls, LayoutParams(0, -2, 0.9f).apply { leftMargin = activity.dp(activity.uiDimen(R.dimen.space_20)) })
        overviewBody = body
        contentContainer = FrameLayout(activity)
        contentContainer.addView(body, FrameLayout.LayoutParams(-1, -1))
        addView(contentContainer, LayoutParams(-1, 0, 1f).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_14)) })

        footer = activity.launcherLabel("Conectando con el control de imagen del proyector…", R.style.LauncherTextCaption).apply {
            setTextColor(activity.uiColor(R.color.launcher_text_secondary))
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(footer, LayoutParams(-1, activity.dp(activity.uiDimen(R.dimen.space_34))).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_8)) })
        selectCorner(0)
        loadState()
    }

    fun focusInitialItem() {
        post {
            if (editingCorners) cornerEditor?.requestFocus()
            else modes.firstOrNull()?.requestFocus()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!editingCorners) return super.dispatchKeyEvent(event)
        val corner = ProjectorImageState.CORNER_ORDER[selectedCorner]
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.action == KeyEvent.ACTION_DOWN) nudgeCorner(corner, "←")
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) nudgeCorner(corner, "→")
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) nudgeCorner(corner, "↑")
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) nudgeCorner(corner, "↓")
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    selectCorner((selectedCorner + 1) % ProjectorImageState.CORNER_ORDER.size)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    fun handleBack(): Boolean {
        if (!editingCorners) return false
        editingCorners = false
        cornerEditor?.visibility = View.GONE
        overviewBody.visibility = View.VISIBLE
        headerView.visibility = View.VISIBLE
        footer.visibility = View.VISIBLE
        setPadding(
            activity.dp(activity.uiDimen(R.dimen.space_48)),
            activity.dp(activity.uiDimen(R.dimen.space_27)),
            activity.dp(activity.uiDimen(R.dimen.space_48)),
            activity.dp(activity.uiDimen(R.dimen.space_27)),
        )
        contentContainer.layoutParams = (contentContainer.layoutParams as LayoutParams).apply {
            topMargin = activity.dp(activity.uiDimen(R.dimen.space_14))
        }
        headerTitle.text = "Imagen"
        headerSubtitle.text = "Zoom y corrección de geometría"
        footer.text = "Zoom y geometría comparten la transformación del firmware; el último control usado prevalece."
        modes.getOrNull(2)?.requestFocus()
        return true
    }

    override fun close() {
        worker.execute { repository.close() }
        worker.shutdown()
    }

    override fun focusSearch(focused: View?, direction: Int): View? =
        FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: focused

    private fun loadState() = runOnWorker("Conectando…") { repository.readState(::showApprovalPrompt) }

    private fun openCornerEditor() {
        val current = state ?: return showUnavailable("Espera a que se lea el estado del proyector.")
        if (!current.supportsFourCorner) return showUnavailable("El firmware no ofrece ajuste de cuatro esquinas.")
        selectMode(2)
        editingCorners = true
        headerTitle.text = "Cuatro esquinas"
        headerSubtitle.text = "Retícula de referencia 9 × 5"
        overviewBody.visibility = View.GONE
        headerView.visibility = View.GONE
        footer.visibility = View.GONE
        setPadding(0, 0, 0, 0)
        contentContainer.layoutParams = (contentContainer.layoutParams as LayoutParams).apply { topMargin = 0 }
        val editor = cornerEditor ?: buildCornerEditor().also {
            cornerEditor = it
            contentContainer.addView(it, FrameLayout.LayoutParams(-1, -1))
        }
        editor.visibility = View.VISIBLE
        selectCorner(selectedCorner)
        editor.requestFocus()
    }

    private fun buildCornerEditor() = FrameLayout(activity).apply {
        clipChildren = false
        val editorPreview = ProjectionShapePreview(activity, showCalibrationGrid = true)
        preview = editorPreview
        addView(editorPreview, FrameLayout.LayoutParams(-1, -1))
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        addView(
            LinearLayout(activity).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                contentDescription = "Flechas para mover la esquina. Enter para pasar a la siguiente."
                setPadding(
                    activity.dp(activity.uiDimen(R.dimen.space_10)),
                    activity.dp(activity.uiDimen(R.dimen.space_4)),
                    activity.dp(activity.uiDimen(R.dimen.space_10)),
                    activity.dp(activity.uiDimen(R.dimen.space_4)),
                )
                background = activity.rounded(activity.uiColor(R.color.launcher_glass_dialog_fill), activity.uiDimen(R.dimen.radius_toolbar))
                addView(
                    ImageView(activity).apply {
                        setImageResource(R.drawable.ic_move)
                        setColorFilter(activity.uiColor(R.color.launcher_text_primary))
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LayoutParams(activity.dp(activity.uiDimen(R.dimen.icon_small)), activity.dp(activity.uiDimen(R.dimen.icon_small))),
                )
                addView(
                    activity.launcherLabel("Mover", R.style.LauncherTextCaption),
                    LayoutParams(-2, -2).apply { leftMargin = activity.dp(activity.uiDimen(R.dimen.space_4)) },
                )
                addView(
                    ImageView(activity).apply {
                        setImageResource(R.drawable.ic_corner_down_left)
                        setColorFilter(activity.uiColor(R.color.launcher_text_primary))
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LayoutParams(activity.dp(activity.uiDimen(R.dimen.icon_small)), activity.dp(activity.uiDimen(R.dimen.icon_small))).apply {
                        leftMargin = activity.dp(activity.uiDimen(R.dimen.space_10))
                    },
                )
                addView(
                    activity.launcherLabel("Siguiente", R.style.LauncherTextCaption),
                    LayoutParams(-2, -2).apply { leftMargin = activity.dp(activity.uiDimen(R.dimen.space_4)) },
                )
            },
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = activity.dp(activity.uiDimen(R.dimen.space_8))
            },
        )
    }

    private fun toggleAutoKeystone() {
        val current = state ?: return showUnavailable("Espera a que se lea el estado del proyector.")
        if (!current.supportsAutoKeystone) return showUnavailable("La corrección automática no está habilitada en este firmware.")
        runOnWorker("Aplicando corrección automática…") {
            repository.setAutoKeystone(!current.autoKeystone, ::showApprovalPrompt)
        }
    }

    private fun changeZoom(delta: Int) {
        val current = state ?: return showUnavailable("Espera a que se lea el estado del proyector.")
        if (!current.supportsZoom) return showUnavailable("El firmware no ofrece zoom de imagen.")
        if (busy) return
        val base = if (pendingZoom == 0) current.zoom else pendingZoom
        pendingZoom = (base + delta).coerceIn(0, 20)
        zoomValue.text = "${pendingZoom} / 20"
        runOnWorker("Aplicando zoom…") { repository.setZoom(pendingZoom, ::showApprovalPrompt) }
    }

    private fun adjustCorner(corner: ProjectorCorner, dx: Int, dy: Int) {
        if (selectedMode != 2) return showUnavailable("Selecciona primero «Cuatro esquinas».")
        val current = state ?: return showUnavailable("Espera a que se lea el estado del proyector.")
        if (!current.supportsFourCorner) return showUnavailable("El firmware no ofrece ajuste de cuatro esquinas.")
        worker.execute {
            try {
                val updated = repository.adjustCorner(corner, dx, dy, ::showApprovalPrompt)
                main.post {
                    state = updated
                    status.text = "Conectado"
                }
            } catch (error: Exception) {
                main.post {
                    status.text = "Sin conexión"
                    val detail = error.message?.let { "No se aplicó el movimiento: $it" }
                        ?: "No se pudo ajustar la esquina."
                    Toast.makeText(activity, detail, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun nudgeCorner(corner: ProjectorCorner, direction: String) {
        val rightSide = corner == ProjectorCorner.TOP_RIGHT || corner == ProjectorCorner.BOTTOM_RIGHT
        val bottomSide = corner == ProjectorCorner.BOTTOM_RIGHT || corner == ProjectorCorner.BOTTOM_LEFT
        when (direction) {
            "←" -> adjustCorner(corner, if (rightSide) 1 else -1, 0)
            "→" -> adjustCorner(corner, if (rightSide) -1 else 1, 0)
            "↑" -> adjustCorner(corner, 0, if (bottomSide) 1 else -1)
            "↓" -> adjustCorner(corner, 0, if (bottomSide) -1 else 1)
        }
    }

    private fun runOnWorker(message: String, action: () -> ProjectorImageState) {
        if (busy) return
        busy = true
        status.text = message
        worker.execute {
            try {
                val updated = action()
                main.post {
                    state = updated
                    pendingZoom = updated.zoom
                    busy = false
                    status.text = "Conectado"
                    zoomValue.text = "${updated.zoom} / 20"
                    modes.getOrNull(0)?.setModeSelected(updated.autoKeystone)
                    modes.getOrNull(0)?.setSubtitle(if (updated.supportsAutoKeystone) "Ajuste automático por firmware" else "No disponible en este firmware")
                    manualRow.setSubtitle(if (updated.supportsManualKeystone) "Corrección vertical con control del firmware" else "No disponible en este firmware")
                    footer.text = when {
                        editingCorners -> "${cornerLabel(ProjectorImageState.CORNER_ORDER[selectedCorner])}: usa los controles para moverla."
                        !updated.supportsFourCorner -> "El firmware no ofrece ajuste de cuatro esquinas."
                        else -> "Zoom y geometría comparten la transformación del firmware; el último control usado prevalece."
                    }
                }
            } catch (error: Exception) {
                main.post {
                    busy = false
                    status.text = "Sin conexión"
                    footer.text = error.message?.let { "No se aplicó el cambio: $it" } ?: "No se pudo leer o cambiar la imagen del proyector."
                    if (state == null) Toast.makeText(activity, "No se pudo conectar con los controles del proyector.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showApprovalPrompt() {
        main.post {
            status.text = "Autoriza depuración"
            footer.text = "Acepta en el proyector la solicitud de depuración para usar estos controles."
        }
    }

    private fun selectMode(index: Int) {
        selectedMode = index
        modes.forEachIndexed { itemIndex, row -> row.setModeSelected(itemIndex == selectedMode) }
        if (index == 2) footer.text = "${cornerLabel(ProjectorImageState.CORNER_ORDER[selectedCorner])}: usa los controles para mover el vértice."
    }

    private fun selectCorner(index: Int) {
        selectedCorner = index.coerceIn(0, ProjectorImageState.CORNER_ORDER.lastIndex)
        preview.selectedCorner = selectedCorner
        cornerEditor?.contentDescription = "Cuatro esquinas. Esquina activa: ${cornerLabel(ProjectorImageState.CORNER_ORDER[selectedCorner])}. Usa las flechas para mover y Enter para pasar a la siguiente."
        if (::footer.isInitialized && selectedMode == 2) {
            footer.text = "${cornerLabel(ProjectorImageState.CORNER_ORDER[selectedCorner])}: usa los controles para mover el vértice."
        }
    }

    private fun cornerLabel(corner: ProjectorCorner) = when (corner) {
        ProjectorCorner.TOP_LEFT -> "Superior izquierda"
        ProjectorCorner.TOP_RIGHT -> "Superior derecha"
        ProjectorCorner.BOTTOM_RIGHT -> "Inferior derecha"
        ProjectorCorner.BOTTOM_LEFT -> "Inferior izquierda"
    }

    private fun showUnavailable(message: String) {
        footer.text = message
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private fun statusPill(text: String) = TextView(activity).apply {
        this.text = text
        setTextAppearance(R.style.LauncherTextCaption)
        setTextColor(activity.uiColor(R.color.launcher_text_secondary))
        gravity = Gravity.CENTER
        minWidth = activity.dp(activity.uiDimen(R.dimen.space_100))
        setPadding(activity.dp(activity.uiDimen(R.dimen.space_14)), activity.dp(activity.uiDimen(R.dimen.space_8)), activity.dp(activity.uiDimen(R.dimen.space_14)), activity.dp(activity.uiDimen(R.dimen.space_8)))
        background = activity.rounded(activity.uiColor(R.color.launcher_action_surface), activity.uiDimen(R.dimen.radius_toolbar))
    }

    private fun adjustButton(label: String, description: String, onClick: () -> Unit) = TextView(activity).apply {
        text = label
        setTextAppearance(R.style.LauncherTextTitle)
        setTextColor(activity.uiColor(R.color.launcher_text_secondary))
        gravity = Gravity.CENTER
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = description
        background = activity.rounded(activity.uiColor(R.color.launcher_surface), activity.uiDimen(R.dimen.radius_action))
        setOnClickListener { onClick() }
        setOnFocusChangeListener { view, focused ->
            view.background = activity.rounded(
                if (focused) activity.uiColor(R.color.launcher_glass_focus_panel) else activity.uiColor(R.color.launcher_surface),
                activity.uiDimen(R.dimen.radius_action),
                if (focused) activity.uiColor(R.color.launcher_glass_focus_edge) else null,
                if (focused) activity.uiDimen(R.dimen.space_1) else null,
            )
        }
    }

    private class ModeRow(
        private val activity: Activity,
        icon: Int,
        title: String,
        description: String,
        onSelect: () -> Unit,
    ) : LinearLayout(activity) {
        private var selected = false
        private val subtitle = activity.launcherLabel(description, R.style.LauncherTextCaption).apply {
            setTextColor(activity.uiColor(R.color.launcher_text_secondary))
            maxLines = 1
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(activity.uiDimen(R.dimen.space_14)), activity.dp(activity.uiDimen(R.dimen.space_4)), activity.dp(activity.uiDimen(R.dimen.space_12)), activity.dp(activity.uiDimen(R.dimen.space_4)))
            isFocusable = true
            isFocusableInTouchMode = true
            addView(FrameLayout(activity).apply {
                addView(android.widget.ImageView(activity).apply {
                    setImageResource(icon)
                    setColorFilter(activity.uiColor(R.color.launcher_text_primary))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, FrameLayout.LayoutParams(activity.dp(activity.uiDimen(R.dimen.icon_medium)), activity.dp(activity.uiDimen(R.dimen.icon_medium)), Gravity.CENTER))
            }, LayoutParams(activity.dp(activity.uiDimen(R.dimen.space_40)), activity.dp(activity.uiDimen(R.dimen.space_40))).apply { rightMargin = activity.dp(activity.uiDimen(R.dimen.space_10)) })
            val textBlock = LinearLayout(activity).apply { orientation = VERTICAL }
            textBlock.addView(activity.launcherLabel(title, R.style.LauncherTextBody, bold = true))
            textBlock.addView(subtitle, LayoutParams(-1, -2).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_2)) })
            addView(textBlock, LayoutParams(0, -2, 1f))
            contentDescription = "$title. $description"
            setOnClickListener { onSelect() }
            setOnFocusChangeListener { view, focused ->
                view.animate().cancel()
                view.animate().scaleX(if (focused) 1.02f else 1f).scaleY(if (focused) 1.02f else 1f)
                    .setDuration(activity.resources.getInteger(R.integer.motion_row_focus).toLong()).start()
                updateBackground(focused)
            }
            updateBackground(false)
        }

        fun setSubtitle(value: String) { subtitle.text = value }

        fun setModeSelected(value: Boolean) {
            selected = value
            updateBackground(hasFocus())
        }

        private fun updateBackground(focused: Boolean) {
            background = activity.rounded(
                when {
                    focused -> activity.uiColor(R.color.launcher_glass_focus_panel)
                    selected -> activity.uiColor(R.color.launcher_action_surface)
                    else -> activity.uiColor(R.color.launcher_surface)
                },
                activity.uiDimen(R.dimen.radius_action),
                if (focused) activity.uiColor(R.color.launcher_glass_focus_edge) else null,
                if (focused) activity.uiDimen(R.dimen.space_1) else null,
            )
        }
    }

    private class ProjectionShapePreview(
        private val activity: Activity,
        private val showCalibrationGrid: Boolean,
    ) : View(activity) {
        var selectedCorner: Int = 0
            set(value) { field = value; invalidate() }
        private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = activity.uiColor(R.color.launcher_background_mid) }
        private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = activity.uiColor(R.color.launcher_glass_focus_fill)
            style = Paint.Style.FILL
        }
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = activity.uiColor(R.color.launcher_text_primary)
            style = Paint.Style.STROKE
            strokeWidth = activity.dp(activity.uiDimen(R.dimen.space_2)).toFloat()
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = activity.uiColor(R.color.launcher_text_primary)
            alpha = 92
            style = Paint.Style.STROKE
            strokeWidth = activity.dp(activity.uiDimen(R.dimen.space_1)).toFloat()
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val inset = if (showCalibrationGrid) minOf(width * 0.025f, height * 0.04f) else minOf(width * 0.08f, height * 0.12f)
            val availableWidth = (width - inset * 2f).coerceAtLeast(1f)
            val availableHeight = (height - inset * 2f).coerceAtLeast(1f)
            val frameWidth = minOf(availableWidth, availableHeight * 16f / 9f)
            val frameHeight = frameWidth * 9f / 16f
            val left = (width - frameWidth) / 2f
            val right = left + frameWidth
            val top = (height - frameHeight) / 2f
            val bottom = top + frameHeight
            val frame = RectF(left, top, right, bottom)
            canvas.drawRoundRect(frame, activity.dp(activity.uiDimen(R.dimen.radius_small)).toFloat(), activity.dp(activity.uiDimen(R.dimen.radius_small)).toFloat(), screenPaint)
            val points = if (showCalibrationGrid) {
                listOf(
                    left + frame.width() * 0.025f to top + frame.height() * 0.04f,
                    right - frame.width() * 0.025f to top + frame.height() * 0.04f,
                    right - frame.width() * 0.025f to bottom - frame.height() * 0.04f,
                    left + frame.width() * 0.025f to bottom - frame.height() * 0.04f,
                )
            } else {
                listOf(
                    left + frame.width() * 0.10f to top + frame.height() * 0.08f,
                    right - frame.width() * 0.08f to top + frame.height() * 0.03f,
                    right - frame.width() * 0.03f to bottom - frame.height() * 0.11f,
                    left + frame.width() * 0.05f to bottom - frame.height() * 0.04f,
                )
            }
            val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.first, p.second) else lineTo(p.first, p.second) }; close() }
            canvas.drawPath(path, shapePaint)
            if (showCalibrationGrid) {
                val tl = points[0]
                val tr = points[1]
                val br = points[2]
                val bl = points[3]
                fun mix(start: Pair<Float, Float>, end: Pair<Float, Float>, t: Float) =
                    (start.first + (end.first - start.first) * t) to (start.second + (end.second - start.second) * t)
                for (column in 1 until 9) {
                    val t = column / 9f
                    val topPoint = mix(tl, tr, t)
                    val bottomPoint = mix(bl, br, t)
                    canvas.drawLine(topPoint.first, topPoint.second, bottomPoint.first, bottomPoint.second, gridPaint)
                }
                for (row in 1 until 5) {
                    val t = row / 5f
                    val leftPoint = mix(tl, bl, t)
                    val rightPoint = mix(tr, br, t)
                    canvas.drawLine(leftPoint.first, leftPoint.second, rightPoint.first, rightPoint.second, gridPaint)
                }
            }
            canvas.drawPath(path, edgePaint)
            points.forEachIndexed { index, (x, y) ->
                dotPaint.color = activity.uiColor(if (index == selectedCorner) R.color.launcher_accent else R.color.launcher_text_primary)
                canvas.drawCircle(x, y, activity.dp(activity.uiDimen(if (index == selectedCorner) R.dimen.space_7 else R.dimen.space_5)).toFloat(), dotPaint)
            }
        }
    }
}
