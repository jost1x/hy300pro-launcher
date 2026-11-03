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
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
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
    private lateinit var zoomSlider: SeekBar
    private lateinit var footer: TextView
    private lateinit var manualRow: ModeRow
    private var preview: ProjectionShapePreview? = null
    private lateinit var contentContainer: FrameLayout
    private lateinit var overviewBody: FrameLayout
    private lateinit var headerTitle: TextView
    private lateinit var headerSubtitle: TextView
    private var cornerEditor: FrameLayout? = null
    private var cornerControlsPanel: View? = null
    private var cornerStatus: TextView? = null
    private var applyCornerDraftButton: TextView? = null
    private var cancelCornerDraftButton: TextView? = null
    private val cornerChoiceButtons = mutableListOf<TextView>()
    private var draftCorners: MutableMap<ProjectorCorner, io.jostx.hy300launcher.data.ProjectorPoint>? = null
    private var cornerSessionStart: Map<ProjectorCorner, io.jostx.hy300launcher.data.ProjectorPoint>? = null
    private var draftActionsFocused = false
    private var cornerSessionEnding = false
    private var cornerSessionEndAction: CornerSessionEndAction? = null
    private lateinit var headerView: LinearLayout
    private var editingCorners = false
    private var state: ProjectorImageState? = null
    private var selectedMode = -1
    private var selectedCorner = 0
    private var pendingZoom = 0
    private var busy = false
    private val pendingCornerDeltas = linkedMapOf<ProjectorCorner, Pair<Int, Int>>()
    private var cornerBatchScheduled = false
    private var cornerBatchInFlight = false
    private var cornerBatchId = 0L
    private var cornerFlushRequested = false
    private var closing = false
    private val flushCornerBatch = Runnable {
        cornerBatchScheduled = false
        sendPendingCornerBatch()
    }
    private val forceCornerBatch = Runnable {
        cornerBatchScheduled = false
        sendPendingCornerBatch(forceAfterInFlight = true)
    }

    companion object {
        private const val CORNER_BATCH_WINDOW_MS = 150L
        private const val CORNER_BATCH_MAX_WAIT_MS = 300L
    }

    private enum class CornerSessionEndAction { COMMIT, ROLLBACK }

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

        val body = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
        }

        val controls = LinearLayout(activity).apply {
            orientation = VERTICAL
            clipChildren = false
        }
        val modeData = listOf(
            Triple(R.drawable.ic_monitor_play, "Automática", "Ajusta la geometría disponible"),
            Triple(R.drawable.ic_sliders, "Trapecio manual", "Ajuste manual no expuesto por el firmware"),
            Triple(R.drawable.ic_layout_grid, "Cuatro esquinas", "Calibra en vivo con retícula 16:9"),
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
        zoomSlider = SeekBar(activity).apply {
            max = 20
            progress = 0
            keyProgressIncrement = 1
            isFocusable = true
            isFocusableInTouchMode = true
            isEnabled = false
            contentDescription = "Zoom de imagen, de 0 a 20. Usa izquierda y derecha para ajustar."
            setProgressDrawable(resources.getDrawable(R.drawable.launcher_zoom_progress, activity.theme))
            thumb = resources.getDrawable(R.drawable.launcher_zoom_thumb, activity.theme)
            setPadding(
                activity.dp(activity.uiDimen(R.dimen.space_10)),
                activity.dp(activity.uiDimen(R.dimen.space_4)),
                activity.dp(activity.uiDimen(R.dimen.space_10)),
                activity.dp(activity.uiDimen(R.dimen.space_4)),
            )
            background = activity.rounded(activity.uiColor(R.color.launcher_action_surface), activity.uiDimen(R.dimen.radius_action))
            setOnFocusChangeListener { view, focused ->
                view.background = activity.rounded(
                    if (focused) activity.uiColor(R.color.launcher_glass_focus_panel) else activity.uiColor(R.color.launcher_action_surface),
                    activity.uiDimen(R.dimen.radius_action),
                    if (focused) activity.uiColor(R.color.launcher_glass_focus_edge) else null,
                    if (focused) activity.uiDimen(R.dimen.space_1) else null,
                )
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) changeZoom(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        zoom.addView(zoomSlider, LayoutParams(-1, activity.dp(activity.uiDimen(R.dimen.action_target))).apply {
            topMargin = activity.dp(activity.uiDimen(R.dimen.space_8))
        })
        controls.addView(zoom, LayoutParams(-1, -2).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_8)) })
        body.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
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
        if (draftActionsFocused) {
            return super.dispatchKeyEvent(event)
        }
        val corner = ProjectorImageState.CORNER_ORDER[selectedCorner]
        if (cornerSessionEnding) return true
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
                    draftActionsFocused = true
                    cornerControlsPanel?.visibility = View.VISIBLE
                    cornerChoiceButtons.getOrNull(selectedCorner)?.requestFocus()
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    fun handleBack(): Boolean {
        if (!editingCorners) return false
        endCornerSession(CornerSessionEndAction.ROLLBACK)
        return true
    }

    override fun close() {
        closing = true
        sendPendingCornerBatch(forceAfterInFlight = true)
        if (!cornerBatchInFlight && pendingCornerDeltas.isEmpty()) closeRepository()
    }

    override fun focusSearch(focused: View?, direction: Int): View? =
        FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: focused

    private fun loadState() = runOnWorker("Conectando…") { repository.readState(::showApprovalPrompt) }

    private fun openCornerEditor() {
        val current = state ?: return showUnavailable("Espera a que se lea el estado del proyector.")
        if (!current.supportsFourCorner) return showUnavailable("El firmware no ofrece ajuste de cuatro esquinas.")
        selectMode(2)
        cornerSessionStart = current.corners.toMap()
        draftCorners = current.corners.toMutableMap()
        cornerSessionEnding = false
        cornerFlushRequested = false
        selectedCorner = 0
        draftActionsFocused = false
        editingCorners = true
        headerTitle.text = "Cuatro esquinas"
        headerSubtitle.text = "Ajusta las esquinas hasta ver celdas casi cuadradas"
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
        cornerControlsPanel?.visibility = View.GONE
        selectCorner(selectedCorner)
        preview?.selectedCorner = selectedCorner
        overviewBody.clearFocus()
        editor.post {
            editor.requestFocus()
        }
    }

    private fun buildCornerEditor() = FrameLayout(activity).apply {
        clipChildren = false
        val editorPreview = ProjectionShapePreview(activity)
        preview = editorPreview
        addView(editorPreview, FrameLayout.LayoutParams(-1, -1))
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = FOCUS_BEFORE_DESCENDANTS
        setOnFocusChangeListener { _, focused ->
            if (focused) {
                draftActionsFocused = false
                cornerControlsPanel?.visibility = View.GONE
            }
        }
        val controlsPanel = LinearLayout(activity).apply {
                orientation = VERTICAL
                setPadding(
                    activity.dp(activity.uiDimen(R.dimen.space_12)),
                    activity.dp(activity.uiDimen(R.dimen.space_6)),
                    activity.dp(activity.uiDimen(R.dimen.space_12)),
                    activity.dp(activity.uiDimen(R.dimen.space_6)),
                )
                background = activity.rounded(activity.uiColor(R.color.launcher_glass_dialog_fill), activity.uiDimen(R.dimen.radius_toolbar))
                addView(LinearLayout(activity).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    contentDescription = "Usa las flechas para ajustar la esquina activa en la proyección. Enter abre selección de esquina y acciones."
                    addView(ImageView(activity).apply {
                        setImageResource(R.drawable.ic_move)
                        setColorFilter(activity.uiColor(R.color.launcher_text_primary))
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LayoutParams(activity.dp(activity.uiDimen(R.dimen.icon_small)), activity.dp(activity.uiDimen(R.dimen.icon_small))))
                    addView(activity.launcherLabel("Mover", R.style.LauncherTextCaption), LayoutParams(-2, -2).apply { leftMargin = activity.dp(activity.uiDimen(R.dimen.space_4)) })
                    addView(ImageView(activity).apply {
                        setImageResource(R.drawable.ic_corner_down_left)
                        setColorFilter(activity.uiColor(R.color.launcher_text_primary))
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LayoutParams(activity.dp(activity.uiDimen(R.dimen.icon_small)), activity.dp(activity.uiDimen(R.dimen.icon_small))).apply { leftMargin = activity.dp(activity.uiDimen(R.dimen.space_10)) })
                    addView(activity.launcherLabel("Enter · esquinas / acciones", R.style.LauncherTextCaption), LayoutParams(-2, -2).apply { leftMargin = activity.dp(activity.uiDimen(R.dimen.space_4)) })
                    cornerStatus = activity.launcherLabel("Listo", R.style.LauncherTextCaption).apply {
                        setTextColor(activity.uiColor(R.color.launcher_text_secondary))
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    addView(cornerStatus, LayoutParams(-2, -2).apply { leftMargin = activity.dp(activity.uiDimen(R.dimen.space_10)) })
                }, LayoutParams(-1, activity.dp(activity.uiDimen(R.dimen.space_30))))

                val labels = listOf("Sup. izq.", "Sup. der.", "Inf. der.", "Inf. izq.")
                val icons = listOf(
                    R.drawable.ic_corner_top_left,
                    R.drawable.ic_corner_top_right,
                    R.drawable.ic_corner_bottom_right,
                    R.drawable.ic_corner_bottom_left,
                )
                val choicesByCorner = arrayOfNulls<TextView>(4)
                val cornerRows = LinearLayout(activity).apply { orientation = VERTICAL }
                listOf(listOf(0, 1), listOf(3, 2)).forEachIndexed { rowIndex, rowIndices ->
                    val row = LinearLayout(activity).apply {
                        orientation = HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    rowIndices.forEachIndexed { columnIndex, index ->
                        val choice = adjustButton(
                            labels[index],
                            "Editar esquina ${cornerLabel(ProjectorImageState.CORNER_ORDER[index])}",
                            icons[index],
                        ) {
                            draftActionsFocused = false
                            selectCorner(index)
                            cornerEditor?.requestFocus()
                        }
                        choicesByCorner[index] = choice
                        row.addView(choice, LayoutParams(0, activity.dp(activity.uiDimen(R.dimen.action_target)), 1f).apply {
                            if (columnIndex > 0) leftMargin = activity.dp(activity.uiDimen(R.dimen.space_6))
                        })
                    }
                    cornerRows.addView(row, LayoutParams(-1, activity.dp(activity.uiDimen(R.dimen.action_target))).apply {
                        if (rowIndex > 0) topMargin = activity.dp(activity.uiDimen(R.dimen.space_4))
                    })
                }
                addView(cornerRows, LayoutParams(-1, -2).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_4)) })
                cornerChoiceButtons.clear()
                cornerChoiceButtons.addAll(choicesByCorner.filterNotNull())

                addView(LinearLayout(activity).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    cancelCornerDraftButton = adjustButton("Cancelar", "Restaurar las esquinas iniciales de esta sesión", R.drawable.ic_x) { discardCornerDraft() }
                    addView(cancelCornerDraftButton, LayoutParams(0, activity.dp(activity.uiDimen(R.dimen.action_target)), 1f).apply { rightMargin = activity.dp(activity.uiDimen(R.dimen.space_6)) })
                    applyCornerDraftButton = adjustButton("Aplicar", "Conservar el ajuste actual del proyector", R.drawable.ic_check) { confirmCornerDraft() }
                    addView(applyCornerDraftButton, LayoutParams(0, activity.dp(activity.uiDimen(R.dimen.action_target)), 1f).apply { leftMargin = activity.dp(activity.uiDimen(R.dimen.space_6)) })
                }, LayoutParams(-1, activity.dp(activity.uiDimen(R.dimen.action_target))).apply {
                    topMargin = activity.dp(activity.uiDimen(R.dimen.space_6))
                })
                updateCornerChoiceStyles()
        }
        controlsPanel.visibility = View.GONE
        cornerControlsPanel = controlsPanel
        addView(controlsPanel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            leftMargin = activity.dp(activity.uiDimen(R.dimen.space_12))
            rightMargin = activity.dp(activity.uiDimen(R.dimen.space_12))
            bottomMargin = activity.dp(activity.uiDimen(R.dimen.space_8))
        })
    }

    private fun toggleAutoKeystone() {
        val current = state ?: return showUnavailable("Espera a que se lea el estado del proyector.")
        if (!current.supportsAutoKeystone) return showUnavailable("La corrección automática no está habilitada en este firmware.")
        runOnWorker("Aplicando corrección automática…") {
            repository.setAutoKeystone(!current.autoKeystone, ::showApprovalPrompt)
        }
    }

    private fun changeZoom(value: Int) {
        val current = state ?: return
        if (!current.supportsZoom) {
            zoomSlider.progress = current.zoom
            return showUnavailable("El firmware no ofrece zoom de imagen.")
        }
        pendingZoom = value.coerceIn(0, 20)
        zoomValue.text = "$pendingZoom / 20"
        if (busy) return
        applyPendingZoom()
    }

    private fun applyPendingZoom() {
        if (busy || !::zoomSlider.isInitialized) return
        val current = state ?: return
        if (!current.supportsZoom) return
        val target = pendingZoom
        if (target == current.zoom) return
        busy = true
        status.text = "Aplicando zoom…"
        worker.execute {
            try {
                val updated = repository.setZoom(target, ::showApprovalPrompt)
                main.post {
                    state = updated
                    busy = false
                    status.text = "Conectado"
                    if (pendingZoom == target) {
                        pendingZoom = updated.zoom
                        zoomValue.text = "${updated.zoom} / 20"
                    } else {
                        applyPendingZoom()
                    }
                }
            } catch (error: Exception) {
                main.post {
                    busy = false
                    pendingZoom = state?.zoom ?: target
                    zoomSlider.progress = pendingZoom
                    zoomValue.text = "$pendingZoom / 20"
                    status.text = "Sin conexión"
                    footer.text = error.message?.let { "No se aplicó el zoom: $it" } ?: "No se pudo cambiar el zoom."
                }
            }
        }
    }

    private fun adjustCorner(corner: ProjectorCorner, dx: Int, dy: Int) {
        if (selectedMode != 2) return showUnavailable("Selecciona primero «Cuatro esquinas».")
        if (cornerSessionEnding) return
        val draft = draftCorners ?: return
        val point = draft.getValue(corner)
        val next = point.copy(x = (point.x + dx).coerceIn(0, 500), y = (point.y + dy).coerceIn(0, 500))
        if (next == point) return
        draft[corner] = next
        val pending = pendingCornerDeltas[corner] ?: (0 to 0)
        pendingCornerDeltas[corner] = (pending.first + next.x - point.x) to (pending.second + next.y - point.y)
        selectCorner(selectedCorner)
        cornerStatus?.text = "Pendiente · X ${next.x} · Y ${next.y}"
        scheduleCornerBatch(resetWindow = true)
    }

    private fun discardCornerDraft() = endCornerSession(CornerSessionEndAction.ROLLBACK)

    private fun confirmCornerDraft() = endCornerSession(CornerSessionEndAction.COMMIT)

    private fun endCornerSession(action: CornerSessionEndAction) {
        if (!editingCorners || cornerSessionEnding) return
        cornerSessionEnding = true
        cornerSessionEndAction = action
        main.removeCallbacks(flushCornerBatch)
        main.removeCallbacks(forceCornerBatch)
        cornerBatchScheduled = false
        if (action == CornerSessionEndAction.ROLLBACK) pendingCornerDeltas.clear()
        cornerStatus?.text = if (action == CornerSessionEndAction.COMMIT) "Confirmando ajuste…" else "Restaurando ajuste inicial…"
        continueCornerBatches()
    }

    private fun runCornerRollback() {
        val original = cornerSessionStart ?: return finishCornerSession("Ajuste conservado.")
        cornerBatchInFlight = true
        cornerStatus?.text = "Restaurando ajuste inicial…"
        worker.execute {
            try {
                val updated = repository.setCorners(original, ::showApprovalPrompt)
                main.post {
                    state = updated
                    cornerBatchInFlight = false
                    finishCornerSession("Ajuste inicial restaurado; no se conservaron los cambios.")
                }
            } catch (error: Exception) {
                main.post {
                    cornerBatchInFlight = false
                    cornerSessionEnding = false
                    cornerSessionEndAction = null
                    val detail = error.message ?: "No se pudo restaurar el ajuste inicial."
                    cornerStatus?.text = "No restaurado · vuelve a pulsar Cancelar"
                    status.text = "Sin conexión"
                    Toast.makeText(activity, detail, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun finishCornerSession(message: String) {
        draftCorners = null
        cornerSessionStart = null
        pendingCornerDeltas.clear()
        cornerFlushRequested = false
        cornerSessionEnding = false
        cornerSessionEndAction = null
        cornerBatchScheduled = false
        draftActionsFocused = false
        editingCorners = false
        cornerEditor?.visibility = View.GONE
        overviewBody.visibility = View.VISIBLE
        headerView.visibility = View.VISIBLE
        footer.visibility = View.VISIBLE
        setPadding(activity.dp(activity.uiDimen(R.dimen.space_48)), activity.dp(activity.uiDimen(R.dimen.space_27)), activity.dp(activity.uiDimen(R.dimen.space_48)), activity.dp(activity.uiDimen(R.dimen.space_27)))
        contentContainer.layoutParams = (contentContainer.layoutParams as LayoutParams).apply { topMargin = activity.dp(activity.uiDimen(R.dimen.space_14)) }
        headerTitle.text = "Imagen"
        headerSubtitle.text = "Zoom y corrección de geometría"
        footer.text = message
        modes.getOrNull(2)?.requestFocus()
    }

    private fun scheduleCornerBatch(resetWindow: Boolean) {
        if (cornerBatchScheduled && resetWindow) main.removeCallbacks(flushCornerBatch)
        if (!cornerBatchScheduled) {
            cornerBatchScheduled = true
            main.postDelayed(forceCornerBatch, CORNER_BATCH_MAX_WAIT_MS)
        }
        main.postDelayed(flushCornerBatch, CORNER_BATCH_WINDOW_MS)
    }

    private fun sendPendingCornerBatch(forceAfterInFlight: Boolean = false) {
        main.removeCallbacks(flushCornerBatch)
        main.removeCallbacks(forceCornerBatch)
        cornerBatchScheduled = false
        if (pendingCornerDeltas.isEmpty()) return continueCornerBatches()
        if (cornerBatchInFlight) {
            cornerFlushRequested = cornerFlushRequested || forceAfterInFlight
            return
        }
        val deltas = pendingCornerDeltas.toMap()
        pendingCornerDeltas.clear()
        cornerFlushRequested = false
        val batchId = ++cornerBatchId
        cornerBatchInFlight = true
        cornerStatus?.text = "Aplicando…"
        worker.execute {
            try {
                val updated = repository.adjustCorners(deltas, ::showApprovalPrompt)
                main.post {
                    cornerBatchInFlight = false
                    if (batchId == cornerBatchId && !closing) {
                        state = updated
                        status.text = "Conectado"
                        if (pendingCornerDeltas.isEmpty() && !cornerSessionEnding) {
                            draftCorners = updated.corners.toMutableMap()
                            val activeCorner = ProjectorImageState.CORNER_ORDER[selectedCorner]
                            val point = updated.corners.getValue(activeCorner)
                            cornerStatus?.text = "Enviado · ${point.x}, ${point.y}"
                        }
                    }
                    continueCornerBatches()
                }
            } catch (error: Exception) {
                main.post {
                    cornerBatchInFlight = false
                    if (batchId == cornerBatchId && !closing) {
                        pendingCornerDeltas.clear()
                        draftCorners = state?.corners?.toMutableMap()
                        status.text = "Sin conexión"
                        cornerStatus?.text = "No aplicado"
                        val detail = error.message?.let { "No se aplicó el movimiento: $it" }
                            ?: "No se pudo ajustar la esquina."
                        Toast.makeText(activity, detail, Toast.LENGTH_SHORT).show()
                        if (cornerSessionEndAction != CornerSessionEndAction.ROLLBACK) {
                            cornerSessionEnding = true
                            cornerSessionEndAction = CornerSessionEndAction.ROLLBACK
                            cornerStatus?.text = "Error al aplicar · restaurando ajuste inicial…"
                        }
                    }
                    continueCornerBatches()
                }
            }
        }
    }

    private fun continueCornerBatches() {
        if (cornerBatchInFlight) return
        if (cornerSessionEnding) {
            when (cornerSessionEndAction) {
                CornerSessionEndAction.COMMIT -> {
                    if (pendingCornerDeltas.isNotEmpty()) sendPendingCornerBatch(forceAfterInFlight = true)
                    else finishCornerSession("Calibración aplicada y conservada.")
                }
                CornerSessionEndAction.ROLLBACK -> {
                    pendingCornerDeltas.clear()
                    runCornerRollback()
                }
                null -> Unit
            }
        } else if (pendingCornerDeltas.isNotEmpty()) {
            if (cornerFlushRequested || closing) {
                sendPendingCornerBatch(forceAfterInFlight = true)
            } else if (!cornerBatchScheduled) {
                scheduleCornerBatch(resetWindow = false)
            }
        } else if (closing) {
            closeRepository()
        }
    }

    private fun closeRepository() {
        worker.execute { repository.close() }
        worker.shutdown()
    }

    private fun nudgeCorner(corner: ProjectorCorner, direction: String) {
        val rightSide = corner == ProjectorCorner.TOP_RIGHT || corner == ProjectorCorner.BOTTOM_RIGHT
        val bottomSide = corner == ProjectorCorner.BOTTOM_RIGHT || corner == ProjectorCorner.BOTTOM_LEFT
        when (direction) {
            "←" -> adjustCorner(corner, if (rightSide) 5 else -5, 0)
            "→" -> adjustCorner(corner, if (rightSide) -5 else 5, 0)
            "↑" -> adjustCorner(corner, 0, if (bottomSide) 5 else -5)
            "↓" -> adjustCorner(corner, 0, if (bottomSide) -5 else 5)
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
                    zoomSlider.progress = updated.zoom
                    zoomSlider.isEnabled = updated.supportsZoom
                    modes.getOrNull(0)?.setModeSelected(updated.autoKeystone)
                    modes.getOrNull(0)?.setSubtitle(if (updated.supportsAutoKeystone) "Ajuste automático por firmware" else "No disponible en este firmware")
                    manualRow.setSubtitle(if (updated.supportsManualKeystone) "Corrección vertical con control del firmware" else "No disponible en este firmware")
                    footer.text = when {
                        editingCorners -> "${cornerLabel(ProjectorImageState.CORNER_ORDER[selectedCorner])}: usa los controles para moverla."
                        !updated.supportsFourCorner -> "El firmware no ofrece ajuste de cuatro esquinas."
                        else -> "El zoom conserva la forma definida por las cuatro esquinas."
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
        preview?.selectedCorner = selectedCorner
        preview?.invalidate()
        updateCornerChoiceStyles()
        val point = draftCorners?.get(ProjectorImageState.CORNER_ORDER[selectedCorner])
        cornerStatus?.text = point?.let { "X ${it.x} · Y ${it.y}" } ?: "Listo"
        cornerEditor?.contentDescription = "Calibración en vivo con retícula fija 16:9 y celdas casi cuadradas. Esquina activa: ${cornerLabel(ProjectorImageState.CORNER_ORDER[selectedCorner])}. Las flechas ajustan la proyección; Enter abre accesos a las esquinas y a las acciones."
        if (::footer.isInitialized && selectedMode == 2) {
            footer.text = "${cornerLabel(ProjectorImageState.CORNER_ORDER[selectedCorner])} · ajuste visible en la proyección."
        }
    }

    private fun updateCornerChoiceStyles() {
        cornerChoiceButtons.forEachIndexed { index, button ->
            val focused = button.hasFocus()
            val selected = index == selectedCorner
            button.background = activity.rounded(
                when {
                    focused -> activity.uiColor(R.color.launcher_glass_focus_panel)
                    selected -> activity.uiColor(R.color.launcher_action_surface)
                    else -> activity.uiColor(R.color.launcher_surface)
                },
                activity.uiDimen(R.dimen.radius_action),
                when {
                    focused -> activity.uiColor(R.color.launcher_glass_focus_edge)
                    selected -> activity.uiColor(R.color.launcher_accent)
                    else -> null
                },
                if (focused || selected) activity.uiDimen(R.dimen.space_1) else null,
            )
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

    private fun adjustButton(label: String, description: String, icon: Int? = null, onClick: () -> Unit) = TextView(activity).apply {
        text = label
        setTextAppearance(R.style.LauncherTextTitle)
        setTextColor(activity.uiColor(R.color.launcher_text_secondary))
        gravity = Gravity.CENTER
        setPadding(activity.dp(activity.uiDimen(R.dimen.space_16)), 0, activity.dp(activity.uiDimen(R.dimen.space_16)), 0)
        icon?.let { iconResource ->
            val drawable = resources.getDrawable(iconResource, activity.theme).mutate().apply {
                setTint(activity.uiColor(R.color.launcher_text_primary))
            }
            setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
            compoundDrawablePadding = activity.dp(activity.uiDimen(R.dimen.space_8))
        }
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = description
        background = activity.rounded(activity.uiColor(R.color.launcher_surface), activity.uiDimen(R.dimen.radius_action))
        setOnClickListener { onClick() }
        setOnFocusChangeListener { view, focused ->
            if (cornerChoiceButtons.contains(view)) updateCornerChoiceStyles()
            else view.background = activity.rounded(
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

    private class ProjectionShapePreview(private val activity: Activity) : View(activity) {
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
        private val referenceFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = activity.uiColor(R.color.launcher_accent)
            alpha = 230
            style = Paint.Style.STROKE
            strokeWidth = activity.dp(activity.uiDimen(R.dimen.space_2)).toFloat()
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val inset = activity.dp(activity.uiDimen(R.dimen.radius_small)).toFloat()
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
            val points = listOf(
                left to top,
                right to top,
                right to bottom,
                left to bottom,
            )
            val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.first, p.second) else lineTo(p.first, p.second) }; close() }
            canvas.drawPath(path, shapePaint)
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
            val squareLeft = mix(mix(tl, tr, 3f / 9f), mix(bl, br, 3f / 9f), 1f / 5f)
            val squareRight = mix(mix(tl, tr, 6f / 9f), mix(bl, br, 6f / 9f), 1f / 5f)
            val squareBottomRight = mix(mix(tl, tr, 6f / 9f), mix(bl, br, 6f / 9f), 4f / 5f)
            val squareBottomLeft = mix(mix(tl, tr, 3f / 9f), mix(bl, br, 3f / 9f), 4f / 5f)
            canvas.drawPath(Path().apply {
                moveTo(squareLeft.first, squareLeft.second)
                lineTo(squareRight.first, squareRight.second)
                lineTo(squareBottomRight.first, squareBottomRight.second)
                lineTo(squareBottomLeft.first, squareBottomLeft.second)
                close()
            }, referenceFramePaint)
            canvas.drawPath(path, edgePaint)
            points.forEachIndexed { index, (x, y) ->
                dotPaint.color = activity.uiColor(if (index == selectedCorner) R.color.launcher_accent else R.color.launcher_text_primary)
                canvas.drawCircle(x, y, activity.dp(activity.uiDimen(if (index == selectedCorner) R.dimen.space_7 else R.dimen.space_5)).toFloat(), dotPaint)
            }
        }
    }


}
