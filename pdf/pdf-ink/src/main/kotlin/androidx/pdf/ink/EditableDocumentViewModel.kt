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
import android.util.SparseArray
import androidx.annotation.RestrictTo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.pdf.annotation.EditablePdfDocument
import androidx.pdf.annotation.draftstate.ImmutableAnnotationEditsDraftState
import androidx.pdf.annotation.draftstate.SimpleAnnotationEditsDraftState
import androidx.pdf.annotation.models.PdfAnnotation
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@RestrictTo(RestrictTo.Scope.LIBRARY)
internal class EditableDocumentViewModel(private val state: SavedStateHandle) : ViewModel() {

    private var draftPfd = createDraftPfd()
    private var currentTransformationMatrices = SparseArray<Matrix>()
    private var annotationEditsDraftState = SimpleAnnotationEditsDraftState(draftPfd)
    private val _annotationDisplayStateFlow = MutableStateFlow(AnnotationsDisplayState())
    internal var pdfDocument: EditablePdfDocument? = null

    val annotationsDisplayStateFlow: StateFlow<AnnotationsDisplayState> =
        _annotationDisplayStateFlow.asStateFlow()

    val isEditModeEnabledFlow: StateFlow<Boolean> = state.getStateFlow(EDIT_MODE_ENABLED_KEY, false)

    fun setEditModeEnabled(isEditModeEnabled: Boolean) {
        state[EDIT_MODE_ENABLED_KEY] = isEditModeEnabled
    }

    fun maybeInitDraftState(documentUri: Uri?) {
        // If the document has changed, reset the edit states
        if (documentUri != null && documentUri != state.get<Uri>(DOCUMENT_URI_KEY)) {
            state[EDIT_MODE_ENABLED_KEY] = false
            state[DOCUMENT_URI_KEY] = documentUri

            // Close any existing draft state.
            if (draftPfd.fileDescriptor.valid()) draftPfd.close()
            draftPfd = createDraftPfd() // Initialize a new draft state.

            annotationEditsDraftState = SimpleAnnotationEditsDraftState(draftPfd)
            _annotationDisplayStateFlow.value = AnnotationsDisplayState()
        }
    }

    /** Adds a [PdfAnnotation] to the draft state. */
    fun addAnnotations(annotation: PdfAnnotation) {
        annotationEditsDraftState.addEdit(annotation)
        _annotationDisplayStateFlow.update { currentState ->
            currentState.copy(draftState = annotationEditsDraftState.toImmutableDraftState())
        }
    }

    /** Updates the transformation matrices for rendering annotations. */
    fun updateTransformationMatrices(pageTransformationMatrices: SparseArray<Matrix>) {
        currentTransformationMatrices = pageTransformationMatrices
        _annotationDisplayStateFlow.update { currentState ->
            currentState.copy(transformationMatrices = currentTransformationMatrices)
        }
    }

    override fun onCleared() {
        super.onCleared()
        draftPfd.close()
    }

    internal data class AnnotationsDisplayState(
        val draftState: ImmutableAnnotationEditsDraftState =
            ImmutableAnnotationEditsDraftState(emptyMap()),
        val transformationMatrices: SparseArray<Matrix> = SparseArray(),
    )

    internal companion object {
        const val DOCUMENT_URI_KEY = "documentUri"
        private const val EDIT_MODE_ENABLED_KEY = "isEditModeEnabled"

        private fun createDraftPfd(): ParcelFileDescriptor {
            val tempFile = Files.createTempFile("PDF_ANNOTATIONS_DRAFT", ".txt").toFile()
            return ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE)
        }
    }
}
