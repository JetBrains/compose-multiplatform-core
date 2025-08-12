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
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.strokes.Stroke
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.pdf.PdfDocument
import androidx.pdf.annotation.AnnotationsView
import androidx.pdf.annotation.AnnotationsView.PageAnnotationsData
import androidx.pdf.annotation.EditablePdfDocument
import androidx.pdf.annotation.draftstate.ImmutableAnnotationEditsDraftState
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.featureflag.PdfFeatureFlags
import androidx.pdf.ink.EditableDocumentViewModel.AnnotationsDisplayState
import androidx.pdf.ink.drawer.PdfObjectDrawerFactoryImpl
import androidx.pdf.ink.util.PageTransformCalculator
import androidx.pdf.ink.util.StrokeProcessor
import androidx.pdf.view.PdfContentLayout
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfStylingOptions
import androidx.pdf.viewer.fragment.PdfViewerFragment
import kotlinx.coroutines.launch

/** A [PdfViewerFragment] that provide annotations capabilities using ink library. */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
public open class EditablePdfViewerFragment : PdfViewerFragment, InProgressStrokesFinishedListener {

    public constructor() : super()

    public constructor(pdfStylingOptions: PdfStylingOptions) : super(pdfStylingOptions)

    private lateinit var wetStrokesView: InProgressStrokesView
    private lateinit var annotationView: AnnotationsView
    private lateinit var backPressedCallback: OnBackPressedCallback
    private lateinit var onViewportChangedListener: PdfView.OnViewportChangedListener

    private val annotationsViewModel: EditableDocumentViewModel by viewModels()
    private var strokeProcessor: StrokeProcessor? = null
    private var pageTransformCalculator: PageTransformCalculator = PageTransformCalculator()

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
        if (document is EditablePdfDocument) {
            annotationsViewModel.pdfDocument = document
            annotationsViewModel.maybeInitDraftState(documentUri)
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
        toolboxView.setOnEditClickListener { annotationsViewModel.setEditModeEnabled(true) }
        annotationView.pdfObjectDrawerFactory = PdfObjectDrawerFactoryImpl

        wetStrokesView.apply {
            addFinishedStrokesListener(this@EditablePdfViewerFragment)
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
                        annotationsViewModel.setEditModeEnabled(false)
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

        val draftState = displayState.draftState
        val transformationMatrices = displayState.transformationMatrices

        (firstVisiblePage..lastVisiblePage).forEach { pageNum ->
            val pageAnnotationData =
                createPageAnnotationsData(pageNum, draftState, transformationMatrices)
            pageRenderDataArray.put(pageNum, pageAnnotationData)
        }
        annotationView.annotations = pageRenderDataArray
    }

    private fun createPageAnnotationsData(
        pageNum: Int,
        draftState: ImmutableAnnotationEditsDraftState,
        transformationMatrices: SparseArray<Matrix>,
    ): PageAnnotationsData {
        val annotationsForPage: List<PdfAnnotation> =
            draftState.getEdits(pageNum).map { it.annotation }
        val transformMatrix = transformationMatrices.get(pageNum) ?: Matrix()
        return PageAnnotationsData(annotationsForPage, transformMatrix)
    }

    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        super.onStrokesFinished(strokes)
        wetStrokesView.removeFinishedStrokes(strokes.keys)

        strokes.values.forEach { stroke ->
            strokeProcessor?.process(stroke, pdfView.zoom)?.let { annotation ->
                annotationsViewModel.addAnnotations(annotation)
            }
        }
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
                    val pageTransformationMatrices =
                        pageTransformCalculator.calculate(
                            firstVisiblePage,
                            visiblePagesCount,
                            pageLocations,
                            zoomLevel,
                        )
                    annotationsViewModel.updateTransformationMatrices(pageTransformationMatrices)
                }
            }
        pdfView.addOnViewportChangedListener(onViewportChangedListener)
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
