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

package androidx.pdf.ink.fragment

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.pdf.FakeEditablePdfDocument
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.annotation.EditablePdfDocument
import androidx.pdf.annotation.models.AnnotationsDisplayState
import androidx.pdf.annotation.models.PathPdfObject
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData
import androidx.pdf.annotation.models.StampAnnotation
import androidx.pdf.coroutines.collectTill
import androidx.pdf.ink.EditableDocumentViewModel
import androidx.pdf.ink.model.ApplyEditsState
import androidx.pdf.ink.state.AnnotationDrawingMode
import androidx.pdf.ink.view.tool.Eraser
import androidx.pdf.ink.view.tool.Highlighter
import androidx.pdf.ink.view.tool.Pen
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class EditableDocumentViewModelTest {

    private lateinit var annotationsViewModel: EditableDocumentViewModel
    private lateinit var savedStateHandle: SavedStateHandle

    private val appContext =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private var defaultDocumentUri: Uri? = null
    private var editablePdfDocument: EditablePdfDocument? = null

    @Before
    fun setup() {
        defaultDocumentUri = Uri.fromFile(File("test1.pdf"))
        editablePdfDocument = FakeEditablePdfDocument(uri = requireNotNull(defaultDocumentUri))
        savedStateHandle = SavedStateHandle()
        annotationsViewModel =
            EditableDocumentViewModel(savedStateHandle, SandboxedPdfLoader(appContext, dispatcher))

        annotationsViewModel.maybeInitialiseForDocument(requireNotNull(editablePdfDocument))
    }

    @Test
    fun resetState_clearsAnnotationStateAndDisablesEditMode() = runTest {
        val annotation = createAnnotation(pageNum = 0)
        annotationsViewModel.addDraftAnnotation(annotation)
        annotationsViewModel.isEditModeEnabled = true

        annotationsViewModel.resetState()

        assertThat(annotationsViewModel.isEditModeEnabledFlow.first()).isFalse()
        assertThat(annotationsViewModel.annotationsDisplayStateFlow.first())
            .isEqualTo(AnnotationsDisplayState.EMPTY)
    }

    @Test
    fun addDraftAnnotation_updatesDraftState_forSingleAnnotation() = runTest {
        val annotation = createAnnotation(pageNum = 0)

        annotationsViewModel.addDraftAnnotation(annotation)
        val firstPageEdits: List<PdfAnnotationData>? =
            annotationsViewModel.annotationsDisplayStateFlow.value.edits.editsByPage[0]
                ?.filterIsInstance<PdfAnnotationData>()

        assertThat(firstPageEdits).isNotNull()
        assertThat(firstPageEdits).hasSize(1)

        val addedAnnotationData = firstPageEdits!!.first()
        assertThat(addedAnnotationData.annotation).isEqualTo(annotation)
    }

    @Test
    fun addAnnotations_updatesDraftState_forMultipleDraftAnnotationOnSamePage() = runTest {
        val annotation1 = createAnnotation(pageNum = 0, bounds = RectF(0f, 0f, 50f, 50f))
        val annotation2 = createAnnotation(pageNum = 0, bounds = RectF(50f, 50f, 100f, 100f))

        annotationsViewModel.addDraftAnnotation(annotation1)
        annotationsViewModel.addDraftAnnotation(annotation2)

        val firstPageEdits: List<PdfAnnotationData>? =
            annotationsViewModel.annotationsDisplayStateFlow.value.edits.editsByPage[0]
                ?.filterIsInstance<PdfAnnotationData>()
        assertThat(firstPageEdits).isNotNull()
        assertThat(firstPageEdits).hasSize(2)

        val annotationsInState = firstPageEdits!!.map { it.annotation }
        assertThat(annotationsInState).containsExactly(annotation1, annotation2)
    }

    @Test
    fun addAnnotations_updatesDraftState_forMultipleDraftAnnotationOnDifferentPages() = runTest {
        val annotationPage0 = createAnnotation(pageNum = 0)
        val annotationPage1 = createAnnotation(pageNum = 1)

        annotationsViewModel.addDraftAnnotation(annotationPage0)
        annotationsViewModel.addDraftAnnotation(annotationPage1)

        val firstPageEdits: List<PdfAnnotationData>? =
            annotationsViewModel.annotationsDisplayStateFlow.value.edits.editsByPage[0]
                ?.filterIsInstance<PdfAnnotationData>()
        assertThat(firstPageEdits).isNotNull()
        assertThat(firstPageEdits).hasSize(1)
        assertThat(firstPageEdits!!.first().annotation).isEqualTo(annotationPage0)

        val secondPageEdits: List<PdfAnnotationData>? =
            annotationsViewModel.annotationsDisplayStateFlow.value.edits.editsByPage[1]
                ?.filterIsInstance<PdfAnnotationData>()
        assertThat(secondPageEdits).isNotNull()
        assertThat(secondPageEdits).hasSize(1)
        assertThat(secondPageEdits!!.first().annotation).isEqualTo(annotationPage1)
    }

    @Test
    fun updateTransformationMatrices_updatesFlow() = runTest {
        val matrixPage0 = Matrix().apply { setScale(1f, 1f) }
        val matrixPage1 = Matrix().apply { setTranslate(10f, 10f) }
        val newMatrices =
            HashMap<Int, Matrix>().apply {
                put(0, matrixPage0)
                put(1, matrixPage1)
            }

        annotationsViewModel.updateTransformationMatrices(newMatrices)
        val emittedState = annotationsViewModel.annotationsDisplayStateFlow.first()

        assertThat(emittedState.transformationMatrices.size).isEqualTo(2)
        assertThat(emittedState.transformationMatrices.get(0)).isEqualTo(matrixPage0)
        assertThat(emittedState.transformationMatrices.get(1)).isEqualTo(matrixPage1)
    }

    @Test
    fun fetchAnnotationsForPageRange_updatesStateWithExistingAnnotations() = runTest {
        val existingAnnotation = createAnnotation(pageNum = 0)
        val documentWithAnnotation =
            FakeEditablePdfDocument(initialEdits = listOf(existingAnnotation))
        annotationsViewModel.maybeInitialiseForDocument(documentWithAnnotation)

        annotationsViewModel.fetchAnnotationsForPageRange(0, 0)

        val firstPageEdits: List<PdfAnnotationData>? =
            annotationsViewModel.annotationsDisplayStateFlow.value.edits.editsByPage[0]
                ?.filterIsInstance<PdfAnnotationData>()

        assertThat(firstPageEdits).isNotNull()
        assertThat(firstPageEdits).hasSize(1)
        assertThat(firstPageEdits!!.first().annotation).isEqualTo(existingAnnotation)
    }

    @Test
    fun maybeInitialiseForDocument_resetsState_whenDocumentUriChanges() = runTest {
        val initialAnnotation = createAnnotation(pageNum = 0)
        annotationsViewModel.addDraftAnnotation(initialAnnotation)

        val initialDocUri = Uri.fromFile(File("test1.pdf"))
        savedStateHandle[EditableDocumentViewModel.LOADED_DOCUMENT_URI_KEY] = initialDocUri

        assertThat(annotationsViewModel.annotationsDisplayStateFlow.value.edits.editsByPage)
            .isNotEmpty()

        // Change document URI
        val newDocUri = Uri.fromFile(File("test2.pdf"))
        annotationsViewModel.maybeInitialiseForDocument(FakeEditablePdfDocument(uri = newDocUri))

        // Verify state reset
        assertThat(annotationsViewModel.annotationsDisplayStateFlow.value.edits.editsByPage)
            .isEmpty()
        assertThat(savedStateHandle.get<Uri>(EditableDocumentViewModel.LOADED_DOCUMENT_URI_KEY))
            .isEqualTo(newDocUri)
    }

    @Test
    fun maybeInitialiseForDocument_doesNotResetState_whenDocumentUriIsTheSame() = runTest {
        val docUri = Uri.fromFile(File("test.pdf"))
        savedStateHandle[EditableDocumentViewModel.LOADED_DOCUMENT_URI_KEY] = docUri

        annotationsViewModel.maybeInitialiseForDocument(FakeEditablePdfDocument(uri = docUri))

        val initialAnnotation = createAnnotation(pageNum = 0)
        annotationsViewModel.addDraftAnnotation(initialAnnotation)

        val initialEdits = annotationsViewModel.annotationsDisplayStateFlow.value.edits.editsByPage

        assertThat(annotationsViewModel.annotationsDisplayStateFlow.value.edits.editsByPage)
            .isEqualTo(initialEdits)
        assertThat(savedStateHandle.get<Uri>(EditableDocumentViewModel.LOADED_DOCUMENT_URI_KEY))
            .isEqualTo(docUri)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun applyDraftEdits_updatesStatusFlow_onSuccess() = runTest {
        val annotation = createAnnotation(pageNum = 0)
        annotationsViewModel.addDraftAnnotation(annotation)

        val applyStates = mutableListOf<ApplyEditsState>()
        val collectJob =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                annotationsViewModel.applyEditsStatus.collectTill(applyStates) { state ->
                    state is ApplyEditsState.Success
                }
            }
        // Force the collector to start and capture the initial 'Ready' state
        testScheduler.advanceUntilIdle()

        annotationsViewModel.applyDraftEdits()
        // Assert annotation interactions is disabled while applying edits
        assertThat(annotationsViewModel.isAnnotationInteractionEnabled.value).isFalse()
        collectJob.join()

        assertThat(applyStates).isNotEmpty()
        assertThat(applyStates.first()).isEqualTo(ApplyEditsState.Ready)
        assertThat(applyStates).contains(ApplyEditsState.InProgress)
        assertThat(applyStates.last()).isInstanceOf(ApplyEditsState.Success::class.java)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun applyDraftEdits_updatesStatusFlow_onFailure_whenDocumentIsNull() = runTest {
        // Reset state forces editablePdfDocument to be null and throw exception when trying to
        // apply edits
        annotationsViewModel.resetState()

        val applyStates = mutableListOf<ApplyEditsState>()
        val collectJob =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                annotationsViewModel.applyEditsStatus.collectTill(applyStates) { state ->
                    state is ApplyEditsState.Failure
                }
            }

        // Force the collector to start and capture the initial 'Ready' state
        testScheduler.advanceUntilIdle()

        annotationsViewModel.applyDraftEdits()
        // Assert annotation interactions is disabled while applying edits
        assertThat(annotationsViewModel.isAnnotationInteractionEnabled.value).isFalse()
        collectJob.join()

        // Verify the sequence of states
        assertThat(applyStates).isNotEmpty()
        assertThat(applyStates.first()).isEqualTo(ApplyEditsState.Ready)
        assertThat(applyStates.last()).isInstanceOf(ApplyEditsState.Failure::class.java)
        assertThat((applyStates.last() as ApplyEditsState.Failure).error.message)
            .isEqualTo("Document not available for saving.")
    }

    @Test
    fun setCurrentToolInfo_updatesDrawingMode_whenPenSelected() = runTest {
        val penTool = Pen(brushSize = 2.0f, color = Color.RED)
        annotationsViewModel.setCurrentToolInfo(penTool)

        val drawingMode = annotationsViewModel.drawingMode.first()
        assertThat(drawingMode).isInstanceOf(AnnotationDrawingMode.PenMode::class.java)
        assertThat((drawingMode as AnnotationDrawingMode.PenMode).brush.size).isEqualTo(2.0f)
        assertThat(drawingMode.brush.colorIntArgb).isEqualTo(Color.RED)
    }

    @Test
    fun setCurrentToolInfo_updatesDrawingMode_whenHighlighterSelected() = runTest {
        val highlighterTool = Highlighter(brushSize = 10.0f, color = Color.YELLOW, emoji = null)
        annotationsViewModel.setCurrentToolInfo(highlighterTool)

        val drawingMode = annotationsViewModel.drawingMode.first()
        assertThat(drawingMode).isInstanceOf(AnnotationDrawingMode.HighlighterMode::class.java)
        assertThat((drawingMode as AnnotationDrawingMode.HighlighterMode).brush.size)
            .isEqualTo(10.0f)
        assertThat(drawingMode.brush.colorIntArgb).isEqualTo(Color.YELLOW)
    }

    @Test
    fun setCurrentToolInfo_updatesDrawingMode_whenEraserSelected() = runTest {
        annotationsViewModel.setCurrentToolInfo(Eraser)

        val drawingMode = annotationsViewModel.drawingMode.first()
        assertThat(drawingMode).isEqualTo(AnnotationDrawingMode.EraserMode)
    }

    @Test
    fun setAnnotationVisibility_updatesIsAnnotationInteractionEnabled() = runTest {
        // Enable edit mode for this test
        annotationsViewModel.isEditModeEnabled = true
        // Hide annotations
        annotationsViewModel.areAnnotationsVisible = false
        assertThat(annotationsViewModel.isAnnotationInteractionEnabled.first()).isFalse()

        annotationsViewModel.areAnnotationsVisible = true
        assertThat(annotationsViewModel.isAnnotationInteractionEnabled.first()).isTrue()
    }

    @Test
    fun updateEditMode_updatesIsAnnotationInteractionEnabled() = runTest {
        // Mark annotations visible throughout test
        annotationsViewModel.areAnnotationsVisible = true

        // Exit edit mode
        annotationsViewModel.isEditModeEnabled = false
        assertThat(annotationsViewModel.isAnnotationInteractionEnabled.first()).isFalse()
        // Enter edit mode
        annotationsViewModel.isEditModeEnabled = true
        assertThat(annotationsViewModel.isAnnotationInteractionEnabled.first()).isTrue()
    }

    @Test
    fun initialDrawingMode_isPenWithDefaultBrush() = runTest {
        val drawingMode = annotationsViewModel.drawingMode.first()
        assertThat(drawingMode).isInstanceOf(AnnotationDrawingMode.PenMode::class.java)
    }

    @Test
    fun initialAreAnnotationsEnabled_isTrue() = runTest {
        // Enter edit mode
        annotationsViewModel.isEditModeEnabled = true

        assertThat(annotationsViewModel.isAnnotationInteractionEnabled.first()).isTrue()
    }

    @Test
    fun isPdfViewGestureActive_updatesIsAnnotationInteractionEnabled() = runTest {
        // Enable edit mode and mark annotations visible to allow interaction
        annotationsViewModel.isEditModeEnabled = true
        annotationsViewModel.areAnnotationsVisible = true

        assertThat(annotationsViewModel.isPdfViewGestureActive).isFalse()
        assertThat(annotationsViewModel.isAnnotationInteractionEnabled.value).isTrue()

        // Disable interaction by activating gesture
        annotationsViewModel.isPdfViewGestureActive = true
        assertThat(annotationsViewModel.isAnnotationInteractionEnabled.value).isFalse()

        // Re-enable interaction by deactivating gesture
        annotationsViewModel.isPdfViewGestureActive = false
        assertThat(annotationsViewModel.isAnnotationInteractionEnabled.value).isTrue()
    }

    fun createAnnotation(
        pageNum: Int = 0,
        bounds: RectF = RectF(0f, 0f, 100f, 100f),
    ): PdfAnnotation {
        return StampAnnotation(
            pageNum = pageNum,
            bounds = bounds,
            pdfObjects =
                listOf(PathPdfObject(brushColor = 0, brushWidth = 0f, inputs = emptyList())),
        )
    }
}
