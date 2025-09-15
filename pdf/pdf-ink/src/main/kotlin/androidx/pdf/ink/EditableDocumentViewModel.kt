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
import android.os.ParcelFileDescriptor
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.pdf.PdfLoader
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.annotation.EditablePdfDocument
import androidx.pdf.annotation.manager.AnnotationsManager
import androidx.pdf.annotation.models.AnnotationsDisplayState
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData
import androidx.pdf.ink.edits.AnnotationEditOperationsHandler
import androidx.pdf.ink.history.AnnotationEditsHistoryManager
import androidx.pdf.viewer.fragment.PdfDocumentViewModel
import androidx.pdf.viewer.fragment.model.PdfFragmentUiState
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class EditableDocumentViewModel(private val state: SavedStateHandle, loader: PdfLoader) :
    PdfDocumentViewModel(state, loader) {
    private lateinit var editsHistoryManager: AnnotationEditsHistoryManager
    private lateinit var editOperationsHandler: AnnotationEditOperationsHandler

    private val _annotationDisplayStateFlow = MutableStateFlow(AnnotationsDisplayState.EMPTY)

    internal val annotationsDisplayStateFlow: StateFlow<AnnotationsDisplayState> =
        _annotationDisplayStateFlow.asStateFlow()

    internal val isEditModeEnabledFlow: StateFlow<Boolean> =
        state.getStateFlow(EDIT_MODE_ENABLED_KEY, false)

    internal var isEditModeEnabled: Boolean
        get() = state[EDIT_MODE_ENABLED_KEY] ?: false
        set(value) {
            state[EDIT_MODE_ENABLED_KEY] = value
        }

    // TODO: b/441634479 Refactor to extract the document from `DocumentLoaded` UI state.
    internal var editablePdfDocument: EditablePdfDocument? = null
        set(value) {
            field = value

            if (value != null) {
                maybeInitialiseForDocument(value)
            }
        }

    @VisibleForTesting
    public override fun resetState() {
        super.resetState()
        isEditModeEnabled = false
        editablePdfDocument = null
        _annotationDisplayStateFlow.value = AnnotationsDisplayState.EMPTY
    }

    @VisibleForTesting
    private fun maybeInitialiseForDocument(document: EditablePdfDocument) {
        val documentUri = document.uri

        // If the document has changed, reset the edit states
        if (documentUri != state.get<Uri>(LOADED_DOCUMENT_URI_KEY)) {
            state[LOADED_DOCUMENT_URI_KEY] = documentUri
            editablePdfDocument = document
            editsHistoryManager = AnnotationEditsHistoryManager()
            editOperationsHandler =
                AnnotationEditOperationsHandler(document, editsHistoryManager) {
                    _annotationDisplayStateFlow
                }

            _annotationDisplayStateFlow.value =
                AnnotationsDisplayState(
                    edits = document.getAllEdits(),
                    transformationMatrices = HashMap(),
                )
        }
    }

    /** Adds a [PdfAnnotation] to the draft state. */
    internal fun addDraftAnnotation(annotation: PdfAnnotation) =
        editOperationsHandler.addDraftAnnotation(annotation)

    /** Undoes the last edit operation. */
    internal fun undo() = editOperationsHandler.undo()

    /** Redoes the last undo edit operation. */
    internal fun redo() = editOperationsHandler.redo()

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
        val document = editablePdfDocument
        if (document == null) {
            return
        }

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

    /** Saves the draft annotations to the PDF document. */
    internal suspend fun saveEdits(dest: ParcelFileDescriptor) {
        val document = editablePdfDocument ?: return

        _fragmentUiScreenState.update { PdfFragmentUiState.SavingEdits }
        try {
            val annotations =
                document
                    .getAllEdits()
                    .editsByPage
                    .flatMap { it.value }
                    .filterIsInstance<PdfAnnotationData>()

            withContext(Dispatchers.IO) {
                document.applyEdits(annotations)
                document.write(dest)
            }
            isEditModeEnabled = false
        } finally {
            _fragmentUiScreenState.update { PdfFragmentUiState.DocumentLoaded(document) }
        }
    }

    /**
     * Checks for unsaved changes by verifying if there are any edits in the edits history.
     *
     * @return `true` if unsaved changes exist, `false` if the document is not loaded or there are
     *   no changes.
     */
    internal fun hasUnsavedChanges(): Boolean =
        editablePdfDocument != null && editsHistoryManager.canUndo()

    /** Discards all uncommitted edits, reverting the document to its last saved state. */
    internal fun discardUnsavedChanges() {
        val document = editablePdfDocument ?: return

        document.clearUncommittedEdits()
        _annotationDisplayStateFlow.update { displayState ->
            displayState.copy(edits = document.getAllEdits())
        }
        isEditModeEnabled = false
    }

    @Suppress("UNCHECKED_CAST")
    internal companion object {
        const val LOADED_DOCUMENT_URI_KEY = "loadedDocumentUri"
        private const val EDIT_MODE_ENABLED_KEY = "isEditModeEnabled"

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
