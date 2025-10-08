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
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresExtension
import androidx.annotation.RestrictTo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.forEach
import androidx.fragment.app.viewModels
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.pdf.PdfDocument
import androidx.pdf.annotation.AnnotationsView
import androidx.pdf.annotation.AnnotationsView.PageAnnotationsData
import androidx.pdf.annotation.EditablePdfDocument
import androidx.pdf.annotation.models.AnnotationsDisplayState
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfEdits
import androidx.pdf.featureflag.PdfFeatureFlags
import androidx.pdf.ink.util.PageTransformCalculator
import androidx.pdf.view.PdfContentLayout
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfStylingOptions
import androidx.pdf.viewer.fragment.PdfViewerFragment
import java.util.Collections
import kotlinx.coroutines.launch

/** A [PdfViewerFragment] that provide annotations capabilities using ink library. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
public open class EditablePdfViewerFragment : PdfViewerFragment {

    public constructor() : super()

    public constructor(pdfStylingOptions: PdfStylingOptions) : super(pdfStylingOptions)

    private lateinit var wetStrokesView: InProgressStrokesView
    private lateinit var annotationView: AnnotationsView
    private lateinit var backPressedCallback: OnBackPressedCallback
    private lateinit var onViewportChangedListener: PdfView.OnViewportChangedListener
    private lateinit var wetStrokesOnFinishedListener: WetStrokesOnFinishedListener

    override val documentViewModel: EditableDocumentViewModel by viewModels {
        EditableDocumentViewModel.Factory
    }
    private var pageTransformCalculator: PageTransformCalculator = PageTransformCalculator()
    private val strokeIdToPageNumMap: MutableMap<InProgressStrokeId, Int> =
        Collections.synchronizedMap(mutableMapOf<InProgressStrokeId, Int>())

    /**
     * Writes the current state of the document, including any edits, to the given destination.
     *
     * @param dest The [ParcelFileDescriptor] to write the document to.
     * @throws IllegalStateException If the document is not available (e.g., not loaded or lost due
     *   to process death).
     */
    public suspend fun writeTo(dest: ParcelFileDescriptor): Unit = documentViewModel.saveEdits(dest)

    /** Undoes the last edit. If there are no more edits to undo, this is a no-op. */
    public fun undo(): Unit = documentViewModel.undo()

    /** Redoes the last undone edit. If there are no more edits to redo, this is a no-op. */
    public fun redo(): Unit = documentViewModel.redo()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView =
            super.onCreateView(inflater, container, savedInstanceState) as ConstraintLayout

        wetStrokesView =
            InProgressStrokesView(requireContext()).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                visibility = GONE
            }

        annotationView =
            AnnotationsView(requireContext()).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }

        val pdfContentLayout =
            rootView.findViewById<PdfContentLayout>(
                androidx.pdf.viewer.fragment.R.id.pdfContentLayout
            )
        pdfContentLayout.addView(annotationView)
        pdfContentLayout.addView(wetStrokesView)

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    documentViewModel.isEditModeEnabledFlow.collect { isEnabled ->
                        updateUiForEditMode(isEnabled)
                    }
                }
                launch {
                    documentViewModel.annotationsDisplayStateFlow.collect { displayState ->
                        updateAnnotationsView(displayState)
                    }
                }
            }
        }

        setupTouchListeners()
        setupBackPressedCallback()
        attachOnViewportChangedListener()
        setupDiscardChangesDialogListener()
    }

    /**
     * If the document is an [EditablePdfDocument], sets it for editing and initializes draft state.
     * This method must call `super.onLoadDocumentSuccess(document)` first.
     *
     * @param document The loaded [PdfDocument].
     */
    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        documentViewModel.maybeInitialiseForDocument(document)
    }

    override fun onDestroyView() {
        // Clean up the listener to avoid potential memory leaks
        super.onDestroyView()
        pdfView.removeOnViewportChangedListener(onViewportChangedListener)
    }

    private fun updateUiForEditMode(isEnabled: Boolean) {
        PdfFeatureFlags.isMultiTouchScrollEnabled = isEnabled
        backPressedCallback.isEnabled = isEnabled
        wetStrokesView.visibility = if (isEnabled) VISIBLE else GONE
        toolboxView.visibility = if (isEnabled) GONE else VISIBLE
    }

    private fun setupDiscardChangesDialogListener() {
        childFragmentManager.setFragmentResultListener(
            DiscardChangesDialog.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, _ ->
            documentViewModel.discardUnsavedChanges()
        }
    }

    private fun setupTouchListeners() {
        toolboxView.setOnEditClickListener { documentViewModel.isEditModeEnabled = true }

        wetStrokesOnFinishedListener =
            WetStrokesOnFinishedListener(
                wetStrokesView = wetStrokesView,
                strokeIdToPageNumMap = strokeIdToPageNumMap,
                annotationsViewModel = documentViewModel,
            )
        wetStrokesView.apply {
            addFinishedStrokesListener(wetStrokesOnFinishedListener)
            setOnTouchListener(
                WetStrokesViewTouchHandler(this, ::getPageInfoFromViewCoordinates) {
                    strokeId,
                    pageNum ->
                    strokeIdToPageNumMap[strokeId] = pageNum
                }
            )
        }

        val annotationsViewOnTouchListener =
            AnnotationsViewOnTouchListener(
                requireContext(),
                WetStrokesViewTouchEventDispatcher(),
                PdfViewTouchEventDispatcher(),
            )
        pdfContainer.setOnTouchListener(annotationsViewOnTouchListener)
    }

    private fun setupBackPressedCallback() {
        backPressedCallback =
            object : OnBackPressedCallback(enabled = false) {
                override fun handleOnBackPressed() {
                    if (documentViewModel.hasUnsavedChanges()) {
                        showDiscardChangesDialog()
                    } else {
                        documentViewModel.isEditModeEnabled = false
                    }
                }
            }
        requireActivity()
            .onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, backPressedCallback)
    }

    private fun showDiscardChangesDialog() {
        val dialog =
            (childFragmentManager.findFragmentByTag(DiscardChangesDialog.TAG)
                as? DiscardChangesDialog) ?: DiscardChangesDialog()

        if (!dialog.isAdded) {
            dialog.show(childFragmentManager, DiscardChangesDialog.TAG)
        }
    }

    private fun updateAnnotationsView(displayState: AnnotationsDisplayState) {
        val pageRenderDataArray = SparseArray<PageAnnotationsData>()
        val firstVisiblePage = pdfView.firstVisiblePage
        val lastVisiblePage = firstVisiblePage + pdfView.visiblePagesCount - 1

        val edits = displayState.edits
        val transformationMatrices = displayState.transformationMatrices

        (firstVisiblePage..lastVisiblePage).forEach { pageNum ->
            val pageAnnotationData =
                createPageAnnotationsData(pageNum, edits, transformationMatrices)
            pageRenderDataArray.put(pageNum, pageAnnotationData)
        }
        annotationView.annotations = pageRenderDataArray
    }

    private fun createPageAnnotationsData(
        pageNum: Int,
        edits: PdfEdits,
        transformationMatrices: Map<Int, Matrix>,
    ): PageAnnotationsData {
        val annotationsForPage: List<PdfAnnotation> =
            edits.getEditsForPage(pageNum).map { it.edit }.filterIsInstance<PdfAnnotation>()
        val transformMatrix = transformationMatrices[pageNum] ?: Matrix()
        return PageAnnotationsData(annotationsForPage, transformMatrix)
    }

    private fun attachOnViewportChangedListener() {
        onViewportChangedListener =
            object : PdfView.OnViewportChangedListener {
                override fun onViewportChanged(
                    firstVisiblePage: Int,
                    visiblePagesCount: Int,
                    pageLocations: SparseArray<RectF>,
                    zoomLevel: Float,
                ) {
                    val firstVisiblePage = pdfView.firstVisiblePage
                    val lastVisiblePage = firstVisiblePage + pdfView.visiblePagesCount - 1

                    updateTransformationMatrices(
                        firstVisiblePage,
                        visiblePagesCount,
                        pageLocations,
                        zoomLevel,
                    )
                    documentViewModel.fetchAnnotationsForPageRange(
                        startPage = firstVisiblePage,
                        endPage = lastVisiblePage,
                    )
                }
            }
        pdfView.addOnViewportChangedListener(onViewportChangedListener)
    }

    private fun updateTransformationMatrices(
        firstVisiblePage: Int,
        visiblePagesCount: Int,
        pageLocations: SparseArray<RectF>,
        zoomLevel: Float,
    ) {
        val transformationMatrices =
            pageTransformCalculator.calculate(
                firstVisiblePage,
                visiblePagesCount,
                pageLocations,
                zoomLevel,
            )
        documentViewModel.updateTransformationMatrices(transformationMatrices)
    }

    /**
     * Returns the [PageInfoProvider.PageInfo] for the page that contains the given view
     * coordinates, or null if no page contains the coordinates.
     */
    private fun getPageInfoFromViewCoordinates(
        viewX: Float,
        viewY: Float,
    ): PageInfoProvider.PageInfo? {
        val pageLocations: SparseArray<RectF> = pdfView.getCurrentPageLocations()
        val currentZoom = pdfView.zoom
        pageLocations.forEach { pageNum, pageBounds ->
            if (pageBounds.contains(viewX, viewY)) {
                return PageInfoProvider.PageInfo(
                    pageNum = pageNum,
                    bounds = pageBounds,
                    zoom = currentZoom,
                )
            }
        }
        return null
    }

    /**
     * A functional interface that provides page-specific information (e.g. page number, bounds,
     * zoom level) for given touch coordinates. Returns `null` if the coordinates are outside any
     * page area.
     *
     * The coordinates are relative to the WetStrokesView.
     */
    internal fun interface PageInfoProvider {
        fun getCurrentPageInfo(viewX: Float, viewY: Float): PageInfo?

        data class PageInfo(val pageNum: Int, val bounds: RectF, val zoom: Float)
    }

    internal inner class WetStrokesViewTouchEventDispatcher : TouchEventDispatcher {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            return wetStrokesView.dispatchTouchEvent(event)
        }
    }

    internal inner class PdfViewTouchEventDispatcher : TouchEventDispatcher {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            return pdfView.onTouchEvent(event)
        }
    }
}
