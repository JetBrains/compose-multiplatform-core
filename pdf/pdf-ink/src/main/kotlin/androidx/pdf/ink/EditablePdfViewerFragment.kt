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
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresExtension
import androidx.annotation.RestrictTo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.forEach
import androidx.fragment.app.viewModels
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
import androidx.pdf.ink.util.StrokeProcessor
import androidx.pdf.view.PdfContentLayout
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfStylingOptions
import androidx.pdf.viewer.fragment.PdfViewerFragment
import kotlinx.coroutines.launch

/** A [PdfViewerFragment] that provide annotations capabilities using ink library. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
public open class EditablePdfViewerFragment : PdfViewerFragment {

    public constructor() : super()

    public constructor(pdfStylingOptions: PdfStylingOptions) : super(pdfStylingOptions)

    private lateinit var wetStrokesView: InProgressStrokesView
    private lateinit var annotationView: AnnotationsView
    private lateinit var savingOverlay: FrameLayout
    private lateinit var backPressedCallback: OnBackPressedCallback
    private lateinit var onViewportChangedListener: PdfView.OnViewportChangedListener
    private lateinit var wetStrokesOnFinishedListener: WetStrokesOnFinishedListener

    private val annotationsViewModel: EditableDocumentViewModel by viewModels {
        EditableDocumentViewModel.Factory
    }
    private var strokeProcessor: StrokeProcessor? = null
    private var pageTransformCalculator: PageTransformCalculator = PageTransformCalculator()

    /**
     * Writes the current state of the document, including any edits, to the given destination.
     *
     * @param dest The [ParcelFileDescriptor] to write the document to.
     * @param onCompletion A callback function to be invoked when the write operation is complete.
     */
    public fun writeTo(dest: ParcelFileDescriptor, onCompletion: () -> Unit) {
        savingOverlay.visibility = VISIBLE

        annotationsViewModel.saveEdits(dest) {
            savingOverlay.visibility = GONE
            annotationsViewModel.isEditModeEnabled = false
            onCompletion()
        }
    }

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

        savingOverlay =
            inflater.inflate(R.layout.saving_progress_overlay, rootView, false) as FrameLayout
        rootView.addView(savingOverlay)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        strokeProcessor = StrokeProcessor(this::getPageBoundsFromViewCoordinates)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    annotationsViewModel.isEditModeEnabledFlow.collect { isEnabled ->
                        updateUiForEditMode(isEnabled)
                    }
                }
                launch {
                    annotationsViewModel.annotationsDisplayStateFlow.collect { displayState ->
                        updateAnnotationsView(displayState)
                    }
                }
            }
        }

        setupTouchListeners()
        setupBackPressedCallback()
        attachOnViewportChangedListener()
    }

    /**
     * If the document is an [EditablePdfDocument], sets it for editing and initializes draft state.
     * This method must call `super.onLoadDocumentSuccess(document)` first.
     *
     * @param document The loaded [PdfDocument].
     */
    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        if (documentUri != null && document is EditablePdfDocument) {
            annotationsViewModel.editablePdfDocument = document
        } else {
            annotationsViewModel.editablePdfDocument = null
        }
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

    private fun setupTouchListeners() {
        toolboxView.setOnEditClickListener { annotationsViewModel.isEditModeEnabled = true }

        wetStrokesOnFinishedListener =
            WetStrokesOnFinishedListener(
                wetStrokesView = wetStrokesView,
                strokeProcessor = strokeProcessor,
                pdfViewZoomProvider = { pdfView.zoom },
                annotationsViewModel = annotationsViewModel,
            )
        wetStrokesView.apply {
            addFinishedStrokesListener(wetStrokesOnFinishedListener)
            setOnTouchListener(WetStrokesViewTouchHandler(this, ::getPageBoundsFromViewCoordinates))
        }

        val annotationsViewOnTouchListener =
            AnnotationsViewOnTouchListener(
                WetStrokesViewTouchEventDispatcher(),
                PdfViewTouchEventDispatcher(),
            )
        pdfContainer.setOnTouchListener(annotationsViewOnTouchListener)
    }

    private fun setupBackPressedCallback() {
        backPressedCallback =
            object : OnBackPressedCallback(false) {
                    override fun handleOnBackPressed() {
                        // TODO: b/426125449 - Add a dialog box for saving or discarding changes.
                        annotationsViewModel.isEditModeEnabled = false
                    }
                }
                .also {
                    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
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
                    annotationsViewModel.fetchAnnotationsForPageRange(
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
        annotationsViewModel.updateTransformationMatrices(transformationMatrices)
    }

    /**
     * Returns the [PageBoundsProvider.PageBounds] for the page that contains the given view
     * coordinates, or null if no page contains the coordinates.
     */
    private fun getPageBoundsFromViewCoordinates(
        viewX: Float,
        viewY: Float,
    ): PageBoundsProvider.PageBounds? {
        val pageLocations: SparseArray<RectF> = pdfView.getCurrentPageLocations()
        pageLocations.forEach { pageNum, pageBounds ->
            if (pageBounds.contains(viewX, viewY)) {
                return PageBoundsProvider.PageBounds(pageNum = pageNum, bounds = pageBounds)
            }
        }
        return null
    }

    /**
     * A functional interface to provide the bounds of the current page based on touch coordinates.
     * The coordinates are relative to the WetStrokesView.
     *
     * @return [RectF] representing the page bounds, or null if the coordinates are outside any
     *   page.
     */
    internal fun interface PageBoundsProvider {
        fun getCurrentPageBounds(viewX: Float, viewY: Float): PageBounds?

        data class PageBounds(val pageNum: Int, val bounds: RectF)
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
