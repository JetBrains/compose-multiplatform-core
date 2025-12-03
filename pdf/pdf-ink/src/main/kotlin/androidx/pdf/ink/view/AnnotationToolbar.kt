/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.pdf.ink.view

import android.content.Context
import android.content.res.ColorStateList
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import androidx.annotation.RestrictTo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.os.HandlerCompat
import androidx.pdf.ink.R
import androidx.pdf.ink.view.brush.BrushSizeSelectorView
import androidx.pdf.ink.view.brush.model.BrushSizes.highlightBrushSizes
import androidx.pdf.ink.view.brush.model.BrushSizes.penBrushSizes
import androidx.pdf.ink.view.colorpalette.ColorPaletteView
import androidx.pdf.ink.view.colorpalette.model.Color
import androidx.pdf.ink.view.colorpalette.model.Emoji
import androidx.pdf.ink.view.colorpalette.model.PaletteItem
import androidx.pdf.ink.view.colorpalette.model.getHighlightPaletteItems
import androidx.pdf.ink.view.colorpalette.model.getPenPaletteItems
import androidx.pdf.ink.view.state.AnnotationToolbarState
import androidx.pdf.ink.view.state.ToolbarEffect
import androidx.pdf.ink.view.state.ToolbarInitializer
import androidx.pdf.ink.view.state.ToolbarIntent
import androidx.pdf.ink.view.state.ToolbarIntent.ClearToolSelection
import androidx.pdf.ink.view.tool.AnnotationToolInfo
import androidx.pdf.ink.view.tool.AnnotationToolView
import androidx.pdf.ink.view.tool.model.AnnotationToolsKey.ERASER
import androidx.pdf.ink.view.tool.model.AnnotationToolsKey.HIGHLIGHTER
import androidx.pdf.ink.view.tool.model.AnnotationToolsKey.PEN
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

/**
 * A toolbar that hosts a set of annotation tools for interacting with a PDF document.
 *
 * This custom [android.view.ViewGroup] contains a predefined set of [AnnotationToolView] buttons
 * such as pen, highlighter, eraser, etc. aligned based on the [LinearLayout.orientation] set.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class AnnotationToolbar
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    ConstraintLayout(context, attrs, defStyle) {

    /**
     * A [android.view.ViewGroup] containing all the annotation tools button.
     *
     * Custom tools can be dynamically added to this container using [LinearLayout.addView].
     */
    public val toolTray: LinearLayout

    /**
     * Controls the enabled state of the undo button.
     *
     * This property should be updated by an external controller that manages the annotation
     * undo/redo stack.
     */
    public var canUndo: Boolean = false
        set(value) {
            field = value
            viewmodel.onAction(ToolbarIntent.UndoAvailabilityChanged(value))
        }

    /**
     * Controls the enabled state of the redo button.
     *
     * This property should be updated by an external controller that manages the annotation
     * undo/redo stack.
     */
    public var canRedo: Boolean = false
        set(value) {
            field = value
            viewmodel.onAction(ToolbarIntent.RedoAvailabilityChanged(value))
        }

    private var annotationToolbarListener: AnnotationToolbarListener? = null

    /** Set the listener for [AnnotationToolbar] events. */
    public fun setAnnotationToolbarListener(listener: AnnotationToolbarListener) {
        annotationToolbarListener = listener
    }

    /** Clears any selection of tools on [AnnotationToolbar]. No-op if no tool is selected. */
    public fun clearToolSelection() {
        viewmodel.onAction(ClearToolSelection)
    }

    private val viewmodel =
        AnnotationToolbarViewModel(ToolbarInitializer.createInitialState(context))

    private val pen: AnnotationToolView
    private val highlighter: AnnotationToolView
    private val eraser: AnnotationToolView
    private val colorPaletteButton: AnnotationToolView
    private val undo: AnnotationToolView
    private val redo: AnnotationToolView
    private val toggleAnnotation: AnnotationToolView

    private val brushSizeSelectorView: BrushSizeSelectorView

    private val colorPaletteView: ColorPaletteView

    private var whileAttachedToVisibleWindowJob: Job? = null

    private val penPaletteItems = getPenPaletteItems(context)
    private val highlighterPaletteItems = getHighlightPaletteItems(context)

    // Required to disable any animation while performing screenshot tests
    @VisibleForTesting internal var areAnimationsEnabled: Boolean = true

    init {
        LayoutInflater.from(context).inflate(R.layout.annotation_toolbar, this, true)
        background = context.getDrawable(R.drawable.annotation_toolbar_background)

        toolTray = findViewById(R.id.toolbar)
        brushSizeSelectorView = findViewById(R.id.brush_size_selector)
        colorPaletteView = findViewById(R.id.color_palette)
        pen = findViewById(R.id.pen_button)
        highlighter = findViewById(R.id.highlighter_button)
        eraser = findViewById(R.id.eraser_button)
        colorPaletteButton = findViewById(R.id.color_palette_button)
        undo = findViewById(R.id.undo_button)
        redo = findViewById(R.id.redo_button)
        toggleAnnotation = findViewById(R.id.toggle_annotation_button)

        setupChildViews()
    }

    private fun setupChildViews() {
        setupToolTray()
        setupBrushSizeSlider()
        setupColorPalette()
    }

    private fun setupToolTray() {
        // default orientation
        toolTray.orientation = HORIZONTAL

        // Set click listeners for all tool views
        pen.setOnClickListener { viewmodel.onAction(ToolbarIntent.PenToolClicked) }
        highlighter.setOnClickListener { viewmodel.onAction(ToolbarIntent.HighlighterToolClicked) }
        eraser.setOnClickListener { viewmodel.onAction(ToolbarIntent.EraserToolClicked) }
        colorPaletteButton.setOnClickListener {
            viewmodel.onAction(ToolbarIntent.ToggleColorPalette)
        }
        undo.setOnClickListener { viewmodel.onAction(ToolbarIntent.UndoClicked) }
        redo.setOnClickListener { viewmodel.onAction(ToolbarIntent.RedoClicked) }
        toggleAnnotation.setOnClickListener {
            viewmodel.onAction(ToolbarIntent.ToggleAnnotationVisibility)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        whileAttachedToVisibleWindowJob?.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        whileAttachedToVisibleWindowJob?.cancel()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        val toJoin = whileAttachedToVisibleWindowJob?.apply { cancel() }
        if (visibility != VISIBLE) {
            whileAttachedToVisibleWindowJob = null
        } else {
            whileAttachedToVisibleWindowJob =
                CoroutineScope(HandlerCompat.createAsync(handler.looper).asCoroutineDispatcher())
                    .launch(start = CoroutineStart.UNDISPATCHED) {
                        // Don't let two copies of this run concurrently
                        toJoin?.join()
                        collectUiStates()
                    }
        }
    }

    private suspend fun collectUiStates() = coroutineScope {
        launch {
            viewmodel.state.collect { state ->
                pen.isSelected = state.selectedTool == PEN
                highlighter.isSelected = state.selectedTool == HIGHLIGHTER
                eraser.isSelected = state.selectedTool == ERASER

                updateColorPaletteIcon(state)
                updateBrushSlider(state)
                updateColorPalette(state)

                // Enable tools only if annotations are enabled on toolbar
                pen.isEnabled = state.isAnnotationVisible
                highlighter.isEnabled = state.isAnnotationVisible
                eraser.isEnabled = state.isAnnotationVisible
                // Undo/Redo will be enabled based on current stack of annotations
                undo.isEnabled = state.canUndo && state.isAnnotationVisible
                redo.isEnabled = state.canRedo && state.isAnnotationVisible

                toggleAnnotation.isSelected = !state.isAnnotationVisible
            }
        }

        launch {
            viewmodel.effects.collect {
                when (it) {
                    is ToolbarEffect.ToolUpdated -> {
                        annotationToolbarListener?.onToolChanged(it.toolInfo)
                    }
                    is ToolbarEffect.Undo -> {
                        annotationToolbarListener?.onUndo()
                    }

                    is ToolbarEffect.Redo -> {
                        annotationToolbarListener?.onRedo()
                    }
                    is ToolbarEffect.AnnotationVisibilityChanged -> {
                        annotationToolbarListener?.onAnnotationVisibilityChanged(it.isVisible)
                    }
                }
            }
        }
    }

    private fun setupBrushSizeSlider() {
        brushSizeSelectorView.brushSizeSlider.addOnChangeListener { _, value, _ ->
            viewmodel.onAction(ToolbarIntent.BrushSizeChanged(value.roundToInt()))
        }
    }

    private fun setupColorPalette() {
        colorPaletteView.setPaletteItemSelectedListener(
            object : ColorPaletteView.PaletteItemSelectedListener {
                override fun onItemSelected(index: Int, paletteItem: PaletteItem) {
                    viewmodel.onAction(ToolbarIntent.ColorSelected(index, paletteItem))
                }
            }
        )
    }

    private fun updateBrushSlider(state: AnnotationToolbarState) {
        val shouldBeVisible = state.isBrushSizeSliderVisible
        val isCurrentlyVisible = brushSizeSelectorView.visibility == VISIBLE

        // Only start a transition if the visibility is actually changing.
        if (shouldBeVisible != isCurrentlyVisible) {
            addAutoTransition()
        }

        if (shouldBeVisible) {
            var selectedBrushSizeIndex = 0
            var brushPreviewSize = 0f
            when (state.selectedTool) {
                PEN -> {
                    selectedBrushSizeIndex = viewmodel.state.value.penState.selectedBrushSizeIndex
                    brushPreviewSize = penBrushSizes[selectedBrushSizeIndex].toPx(context)
                }

                HIGHLIGHTER -> {
                    selectedBrushSizeIndex =
                        viewmodel.state.value.highlighterState.selectedBrushSizeIndex
                    brushPreviewSize = highlightBrushSizes[selectedBrushSizeIndex].toPx(context)
                }
            }

            with(brushSizeSelectorView) {
                brushSizeSlider.value = selectedBrushSizeIndex.toFloat()
                brushPreviewView.brushSize = brushPreviewSize
                visibility = VISIBLE
            }
        } else {
            brushSizeSelectorView.visibility = GONE
        }
    }

    private fun updateColorPalette(state: AnnotationToolbarState) {
        val shouldBeVisible = state.isColorPaletteVisible
        val isCurrentlyVisible = colorPaletteView.visibility == VISIBLE

        // Update palette items regardless of visibility to prevent stale content flash.
        when (state.selectedTool) {
            PEN -> {
                val currentSelectedIndex = state.penState.selectedColorIndex
                colorPaletteView.updatePaletteItems(
                    paletteItems = penPaletteItems,
                    currentSelectedIndex = currentSelectedIndex,
                )
            }

            HIGHLIGHTER -> {
                val selectedItemIndex = state.highlighterState.selectedColorIndex
                colorPaletteView.updatePaletteItems(
                    paletteItems = highlighterPaletteItems,
                    currentSelectedIndex = selectedItemIndex,
                )
            }
        }

        // Only start a transition if the visibility is actually changing.
        if (shouldBeVisible != isCurrentlyVisible) {
            addAutoTransition()
        }

        colorPaletteView.visibility = if (shouldBeVisible) VISIBLE else GONE
    }

    private fun updateColorPaletteIcon(state: AnnotationToolbarState) {
        colorPaletteButton.isEnabled = state.isAnnotationVisible && state.isColorPaletteEnabled
        colorPaletteButton.isChecked = state.isColorPaletteVisible

        val paletteItem =
            when (state.selectedTool) {
                PEN -> state.penState.paletteItem
                HIGHLIGHTER -> state.highlighterState.paletteItem
                else -> null
            }

        when (paletteItem) {
            is Color -> {
                colorPaletteButton.iconTint = ColorStateList.valueOf(paletteItem.color)
                colorPaletteButton.setIconResource(R.drawable.color_palette_icon_drawable)
            }

            is Emoji -> {
                colorPaletteButton.icon = ContextCompat.getDrawable(context, paletteItem.emoji)
                colorPaletteButton.iconTint = null
            }
        }
    }

    private fun addAutoTransition() {
        if (!areAnimationsEnabled) return

        val transition =
            AutoTransition()
                .setDuration(AUTO_TRANSITION_DURATION)
                .setInterpolator(DecelerateInterpolator())
                // By excluding the pop-up views themselves from the transition, we prevent
                // their internal animations (like Fade) from conflicting with the parent's
                // layout animation.
                .excludeTarget(brushSizeSelectorView, true)
                .excludeTarget(colorPaletteView, true)

        TransitionManager.beginDelayedTransition(this@AnnotationToolbar, transition)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = ToolbarSavedState(superState)
        savedState.toolbarState = viewmodel.state.value
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is ToolbarSavedState) {
            super.onRestoreInstanceState(state.superState)
            viewmodel.restoreState(state.toolbarState)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    /**
     * Interface definition for a callback to be invoked when interaction occurs with the
     * [AnnotationToolbar].
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public interface AnnotationToolbarListener {
        /**
         * Called every time the selected tool or its attributes (e.g., color, size) are changed.
         *
         * @param toolInfo An [AnnotationToolInfo] object containing the state of the currently
         *   selected tool.
         */
        public fun onToolChanged(toolInfo: AnnotationToolInfo)

        /** Called when a undo button is clicked if [canUndo] is set to enabled. */
        public fun onUndo()

        /** Called when a redo button is clicked if [canRedo] is set to enabled. */
        public fun onRedo()

        /**
         * Called when the annotation visibility toggle button is clicked.
         *
         * @param isVisible `true` if annotations are now set to be visible, `false` otherwise.
         */
        public fun onAnnotationVisibilityChanged(isVisible: Boolean)
    }

    private fun Int.toPx(context: Context): Float {
        return this.toFloat() * context.resources.displayMetrics.density
    }

    internal companion object {
        private const val AUTO_TRANSITION_DURATION = 250L
    }
}
