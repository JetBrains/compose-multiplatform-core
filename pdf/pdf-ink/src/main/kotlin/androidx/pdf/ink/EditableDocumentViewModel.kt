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

package androidx.pdf.ink

import android.graphics.Matrix
import android.net.Uri
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.pdf.PdfDocument
import androidx.pdf.PdfLoader
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.annotation.EditablePdfDocument
import androidx.pdf.annotation.manager.AnnotationsManager
import androidx.pdf.annotation.models.AnnotationsDisplayState
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData
import androidx.pdf.ink.edits.AnnotationEditOperationsHandler
import androidx.pdf.ink.history.AnnotationEditsHistoryManager
import androidx.pdf.ink.model.ApplyEditsState
import androidx.pdf.ink.state.AnnotationDrawingMode
import androidx.pdf.ink.util.InkDefaults
import androidx.pdf.ink.view.tool.AnnotationToolInfo
import androidx.pdf.ink.view.tool.Eraser
import androidx.pdf.ink.view.tool.Highlighter
import androidx.pdf.ink.view.tool.Pen
import androidx.pdf.viewer.fragment.PdfDocumentViewModel
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class EditableDocumentViewModel(private val state: SavedStateHandle, loader: PdfLoader) :
    PdfDocumentViewModel(state, loader) {
    private var editsHistoryManager: AnnotationEditsHistoryManager? = null
    private var editOperationsHandler: AnnotationEditOperationsHandler? = null

    private val _annotationDisplayStateFlow = MutableStateFlow(AnnotationsDisplayState.EMPTY)

    private val _canUndo = MutableStateFlow(false)
    internal val canUndo: StateFlow<Boolean>
        get() = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    internal val canRedo: StateFlow<Boolean>
        get() = _canRedo.asStateFlow()

    internal val annotationsDisplayStateFlow: StateFlow<AnnotationsDisplayState> =
        _annotationDisplayStateFlow.asStateFlow()

    internal val isEditModeEnabledFlow: StateFlow<Boolean> =
        state.getStateFlow(EDIT_MODE_ENABLED_KEY, false)

    internal var isEditModeEnabled: Boolean
        get() = state[EDIT_MODE_ENABLED_KEY] ?: false
        set(value) {
            state[EDIT_MODE_ENABLED_KEY] = value
            updateAnnotationInteractionState()
        }

    internal var areAnnotationsVisible: Boolean
        get() = state[ANNOTATION_VISIBLE_KEY] ?: true
        set(value) {
            state[ANNOTATION_VISIBLE_KEY] = value
            updateAnnotationInteractionState()
        }

    internal var areAnnotationsVisibleFlow: StateFlow<Boolean> =
        state.getStateFlow(ANNOTATION_VISIBLE_KEY, true)

    private val _applyEditsStatus = MutableStateFlow<ApplyEditsState>(ApplyEditsState.Ready)
    internal val applyEditsStatus: StateFlow<ApplyEditsState> = _applyEditsStatus.asStateFlow()

    // TODO: b/441634479 Refactor to extract the document from `DocumentLoaded` UI state.
    private var editablePdfDocument: EditablePdfDocument? = null

    private val _drawingMode =
        MutableStateFlow<AnnotationDrawingMode>(
            AnnotationDrawingMode.PenMode(InkDefaults.PEN_BRUSH)
        )
    internal val drawingMode: StateFlow<AnnotationDrawingMode>
        get() = _drawingMode.asStateFlow()

    private val _isAnnotationInteractionEnabled =
        MutableStateFlow(isEditModeEnabled && areAnnotationsVisible)
    internal val isAnnotationInteractionEnabled: StateFlow<Boolean> =
        _isAnnotationInteractionEnabled.asStateFlow()

    internal var isPdfViewGestureActive: Boolean = false
        set(value) {
            field = value
            updateAnnotationInteractionState()
        }

    @VisibleForTesting
    public override fun resetState() {
        super.resetState()
        isEditModeEnabled = false
        editablePdfDocument = null
        _annotationDisplayStateFlow.value = AnnotationsDisplayState.EMPTY
    }

    internal fun maybeInitialiseForDocument(document: PdfDocument) {
        if (document is EditablePdfDocument) {
            val documentUri = document.uri

            // If the document has changed, reset the edit states
            if (documentUri != state.get<Uri>(LOADED_DOCUMENT_URI_KEY)) {
                setupManagersAndHandlers(documentUri, document)
            } else if (editablePdfDocument == null) {
                editablePdfDocument = document
            }
        } else {
            editablePdfDocument = null
        }
    }

    /** Adds a [PdfAnnotation] to the draft state. */
    internal fun addDraftAnnotation(annotation: PdfAnnotation) {
        if (editOperationsHandler == null) {
            setupManagersAndHandlers(
                documentUri = state.get<Uri>(LOADED_DOCUMENT_URI_KEY),
                document = editablePdfDocument,
            )
        }
        editOperationsHandler?.addDraftAnnotation(annotation)
    }

    /** Undoes the last edit operation. */
    internal fun undo() {
        if (editOperationsHandler == null) {
            setupManagersAndHandlers(
                documentUri = state.get<Uri>(LOADED_DOCUMENT_URI_KEY),
                document = editablePdfDocument,
            )
        }
        editOperationsHandler?.undo()
    }

    /** Redoes the last undo edit operation. */
    internal fun redo() {
        if (editOperationsHandler == null) {
            setupManagersAndHandlers(
                documentUri = state.get<Uri>(LOADED_DOCUMENT_URI_KEY),
                document = editablePdfDocument,
            )
        }
        editOperationsHandler?.redo()
    }

    /** Updates the transformation matrices for rendering annotations. */
    internal fun updateTransformationMatrices(transformationMatrices: Map<Int, Matrix>) {
        if (editablePdfDocument != null) {
            _annotationDisplayStateFlow.update { displayState ->
                displayState.copy(transformationMatrices = transformationMatrices)
            }
        }
    }

    /**
     * Fetches annotations from the [AnnotationsManager] for the defined page range.
     *
     * @param startPage The starting page number (inclusive).
     * @param endPage The ending page number (inclusive).
     */
    internal fun fetchAnnotationsForPageRange(startPage: Int, endPage: Int) {
        val document = editablePdfDocument ?: return

        viewModelScope.launch {
            val annotationsByPage = mutableMapOf<Int, List<PdfAnnotationData>>()
            for (page in startPage..endPage) {
                annotationsByPage[page] = document.getEditsForPage(page)
            }
            _annotationDisplayStateFlow.update { displayState ->
                displayState.copy(edits = document.getAllEdits())
            }
        }
    }

    internal fun applyDraftEdits() {
        val document = editablePdfDocument
        if (document == null) {
            _applyEditsStatus.value =
                ApplyEditsState.Failure(IllegalStateException("Document not available for saving."))
            return
        }
        _applyEditsStatus.value = ApplyEditsState.InProgress
        updateAnnotationInteractionState()

        viewModelScope.launch {
            try {
                val annotations =
                    document
                        .getAllEdits()
                        .editsByPage
                        .flatMap { it.value }
                        .filterIsInstance<PdfAnnotationData>()

                document.applyEdits(annotations)

                val handle = document.createWriteHandle()

                editsHistoryManager?.clear()
                _applyEditsStatus.value = ApplyEditsState.Success(handle)
            } catch (e: Exception) {
                _applyEditsStatus.value = ApplyEditsState.Failure(e)
            } finally {
                updateAnnotationInteractionState()
            }
        }
    }

    internal fun resetApplyEditsStatus() {
        _applyEditsStatus.value = ApplyEditsState.Ready
    }

    /**
     * Checks for unsaved changes by verifying if there are any edits in the edits history.
     *
     * @return `true` if unsaved changes exist, `false` if the document is not loaded or there are
     *   no changes.
     */
    internal fun hasUnsavedChanges(): Boolean =
        editablePdfDocument != null && editsHistoryManager?.canUndo?.value ?: false

    /** Discards all uncommitted edits, reverting the document to its last saved state. */
    internal fun discardUnsavedChanges() {
        val document = editablePdfDocument ?: return

        document.clearUncommittedEdits()
        editsHistoryManager?.clear()

        _annotationDisplayStateFlow.update { displayState ->
            displayState.copy(edits = document.getAllEdits())
        }
        isEditModeEnabled = false
    }

    private fun setupManagersAndHandlers(documentUri: Uri?, document: EditablePdfDocument?) {
        if (documentUri != null && document != null) {
            state[LOADED_DOCUMENT_URI_KEY] = documentUri
            editablePdfDocument = document

            val localEditsHistoryManager = AnnotationEditsHistoryManager()
            val localEditOperationsHandler =
                AnnotationEditOperationsHandler(document, localEditsHistoryManager) {
                    _annotationDisplayStateFlow
                }

            editsHistoryManager = localEditsHistoryManager
            editOperationsHandler = localEditOperationsHandler

            viewModelScope.launch {
                localEditsHistoryManager.canUndo.collect { canUndo -> _canUndo.value = canUndo }
            }

            viewModelScope.launch {
                localEditsHistoryManager.canRedo.collect { canRedo -> _canRedo.value = canRedo }
            }

            _annotationDisplayStateFlow.value =
                AnnotationsDisplayState(
                    edits = document.getAllEdits(),
                    transformationMatrices = HashMap(),
                )
        } else {
            editablePdfDocument = null
        }
    }

    internal fun setCurrentToolInfo(toolInfo: AnnotationToolInfo) {
        when (toolInfo) {
            is Pen -> {
                val brush =
                    Brush.createWithColorIntArgb(
                        family = StockBrushes.pressurePen(),
                        colorIntArgb = toolInfo.color,
                        size = toolInfo.brushSize,
                        epsilon = InkDefaults.EPSILON_VALUE,
                    )
                _drawingMode.value = AnnotationDrawingMode.PenMode(brush)
            }

            is Highlighter -> {
                if (toolInfo.color != null) {
                    val brush =
                        Brush.createWithColorIntArgb(
                            family = StockBrushes.highlighter(),
                            colorIntArgb = toolInfo.color,
                            size = toolInfo.brushSize,
                            epsilon = InkDefaults.EPSILON_VALUE,
                        )
                    _drawingMode.value = AnnotationDrawingMode.HighlighterMode(brush)
                } else {
                    // TODO: Add support for emoji highlighter
                }
            }

            is Eraser -> _drawingMode.value = AnnotationDrawingMode.EraserMode
        }
    }

    private fun updateAnnotationInteractionState() {
        _isAnnotationInteractionEnabled.value =
            isEditModeEnabled &&
                areAnnotationsVisible &&
                _applyEditsStatus.value != ApplyEditsState.InProgress &&
                !isPdfViewGestureActive
    }

    @Suppress("UNCHECKED_CAST")
    internal companion object {
        const val LOADED_DOCUMENT_URI_KEY = "loadedDocumentUri"
        private const val EDIT_MODE_ENABLED_KEY = "isEditModeEnabled"

        private const val ANNOTATION_VISIBLE_KEY = "isAnnotationVisible"
        val Factory: ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    // Get the Application object from extras
                    val application = checkNotNull(extras[APPLICATION_KEY])
                    // Create a SavedStateHandle for this ViewModel from extras
                    val savedStateHandle = extras.createSavedStateHandle()

                    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
                    return (EditableDocumentViewModel(
                        savedStateHandle,
                        SandboxedPdfLoader(application, dispatcher),
                    ))
                        as T
                }
            }
    }
}
