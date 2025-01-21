/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.pdf.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.os.Looper
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Range
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RestrictTo
import androidx.core.os.HandlerCompat
import androidx.pdf.PdfDocument
import androidx.pdf.util.ZoomUtils
import java.util.concurrent.Executors
import kotlin.math.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * A [View] for presenting PDF content, represented by [PdfDocument].
 *
 * This View supports zooming, scrolling, and flinging. Zooming is supported via pinch gesture,
 * quick scale gesture, and double tap to zoom in or snap back to fitting the page width inside its
 * bounds. Zoom can be changed using the [zoom] property, which is notably distinct from
 * [View.getScaleX] / [View.getScaleY]. Scroll position is based on the [View.getScrollX] /
 * [View.getScrollY] properties.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public open class PdfView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    View(context, attrs, defStyle) {
    /** Supply a [PdfDocument] to process the PDF content for rendering */
    public var pdfDocument: PdfDocument? = null
        set(value) {
            checkMainThread()
            value?.let {
                val reset = field != null && field?.uri != value.uri
                field = it
                if (reset) reset()
                onDocumentSet()
            }
        }

    /** The maximum scaling factor that can be applied to this View using the [zoom] property */
    // TODO(b/376299551) - Make maxZoom configurable via XML attribute
    public var maxZoom: Float = DEFAULT_MAX_ZOOM

    /** The minimum scaling factor that can be applied to this View using the [zoom] property */
    // TODO(b/376299551) - Make minZoom configurable via XML attribute
    public var minZoom: Float = DEFAULT_MIN_ZOOM

    /**
     * The zoom level of this view, as a factor of the content's natural size with when 1 pixel is
     * equal to 1 PDF point. Will always be clamped within ([minZoom], [maxZoom])
     */
    public var zoom: Float = DEFAULT_INIT_ZOOM
        set(value) {
            checkMainThread()
            field = value
            onZoomChanged()
        }

    /**
     * A set of areas to be highlighted. Each [Highlight] may be a different color. Setting this
     * property overrides any previous highlights, there is no merging behavior of new and previous
     * values.
     */
    public var highlights: List<Highlight> = listOf()
        set(value) {
            checkMainThread()
            val localPageManager =
                pageManager
                    ?: throw IllegalStateException("Can't highlightAreas without PdfDocument")
            localPageManager.setHighlights(value)
        }

    private val visiblePages: Range<Int>
        get() = pageLayoutManager?.visiblePages?.value ?: Range(0, 0)

    /** The first page in the viewport, including partially-visible pages. 0-indexed. */
    public val firstVisiblePage: Int
        get() = visiblePages.lower

    /** The number of pages visible in the viewport, including partially visible pages */
    public val visiblePagesCount: Int
        get() = if (pdfDocument != null) visiblePages.upper - visiblePages.lower + 1 else 0

    /**
     * The [CoroutineScope] used to make suspending calls to [PdfDocument]. The size of the fixed
     * thread pool is arbitrary and subject to tuning.
     */
    internal val backgroundScope: CoroutineScope =
        CoroutineScope(Executors.newFixedThreadPool(5).asCoroutineDispatcher() + SupervisorJob())

    private var pageLayoutManager: PageLayoutManager? = null
    private var pageManager: PageManager? = null
    private var visiblePagesCollector: Job? = null
    private var dimensionsCollector: Job? = null
    private var invalidationCollector: Job? = null

    private var deferredScrollPage: Int? = null
    private var deferredScrollPosition: PdfPoint? = null

    /** Used to restore saved state */
    private var stateToRestore: PdfViewSavedState? = null
    private var awaitingFirstLayout: Boolean = true
    private var scrollPositionToRestore: PointF? = null
    private var zoomToRestore: Float? = null

    private val gestureHandler = ZoomScrollGestureHandler(this@PdfView)
    private val gestureTracker = GestureTracker(context).apply { delegate = gestureHandler }

    // To avoid allocations during drawing
    private val visibleAreaRect = Rect()

    /**
     * Scrolls to the 0-indexed [pageNum], optionally animating the scroll
     *
     * This View cannot scroll to a page until it knows its dimensions. If [pageNum] is distant from
     * the currently-visible page in a large PDF, there may be some delay while dimensions are being
     * loaded from the PDF.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun scrollToPage(pageNum: Int, animateScroll: Boolean = false) {
        checkMainThread()
        val localPageLayoutManager =
            pageLayoutManager
                ?: throw IllegalStateException("Can't scrollToPage without PdfDocument")
        require(pageNum < (pdfDocument?.pageCount ?: Int.MIN_VALUE)) {
            "Page $pageNum not in document"
        }

        if (localPageLayoutManager.reach >= pageNum) {
            gotoPage(pageNum)
        } else {
            localPageLayoutManager.increaseReach(pageNum)
            deferredScrollPage = pageNum
            deferredScrollPosition = null
        }
    }

    /**
     * Scrolls to [position], optionally animating the scroll
     *
     * This View cannot scroll to a page until it knows its dimensions. If [position] is distant
     * from the currently-visible page in a large PDF, there may be some delay while dimensions are
     * being loaded from the PDF.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun scrollToPosition(position: PdfPoint, animateScroll: Boolean = false) {
        checkMainThread()
        val localPageLayoutManager =
            pageLayoutManager
                ?: throw IllegalStateException("Can't scrollToPage without PdfDocument")
        require(position.pageNum < (pdfDocument?.pageCount ?: Int.MIN_VALUE)) {
            "Page ${position.pageNum} not in document"
        }

        if (localPageLayoutManager.reach >= position.pageNum) {
            gotoPoint(position)
        } else {
            localPageLayoutManager.increaseReach(position.pageNum)
            deferredScrollPosition = position
            deferredScrollPage = null
        }
    }

    private fun gotoPage(pageNum: Int) {
        checkMainThread()
        val localPageLayoutManager =
            pageLayoutManager
                ?: throw IllegalStateException("Can't scrollToPage without PdfDocument")
        check(pageNum <= localPageLayoutManager.reach) { "Can't gotoPage that's not laid out" }

        val pageRect =
            localPageLayoutManager.getPageLocation(pageNum, getVisibleAreaInContentCoords())
        // Zoom should match the width of the page
        val zoom =
            ZoomUtils.calculateZoomToFit(
                viewportWidth.toFloat(),
                viewportHeight.toFloat(),
                pageRect.width().toFloat(),
                1f
            )
        val x = round((pageRect.left + pageRect.width() / 2f) * zoom - (viewportWidth / 2f))
        val y = round((pageRect.top + pageRect.height() / 2f) * zoom - (viewportHeight / 2f))

        // Scroll to the center of the page, then set zoom to fit the width of the page
        // TODO(b/376136621) - Animate scrolling if requested by scrollToPage API
        scrollTo(x.toInt(), y.toInt())
        this.zoom = zoom
    }

    private fun gotoPoint(position: PdfPoint) {
        checkMainThread()
        val localPageLayoutManager =
            pageLayoutManager
                ?: throw IllegalStateException("Can't scrollToPage without PdfDocument")
        check(position.pageNum <= localPageLayoutManager.reach) {
            "Can't gotoPoint on page that's not laid out"
        }

        val pageRect =
            localPageLayoutManager.getPageLocation(
                position.pageNum,
                getVisibleAreaInContentCoords()
            )
        // Zoom should match the width of the page
        val zoom =
            ZoomUtils.calculateZoomToFit(
                viewportWidth.toFloat(),
                viewportHeight.toFloat(),
                pageRect.width().toFloat(),
                1f
            )

        val x = round((pageRect.left + pageRect.width() / 2f) * zoom - (viewportWidth / 2f))
        val y = round((pageRect.top + position.pagePoint.y) * zoom - (viewportHeight / 2f))

        // Scroll to the requested Y position on the page, then set zoom to fit the width of the
        // page
        // TODO(b/376136621) - Animate scrolling if requested by scrollToPage API
        scrollTo(
            toViewCoord(x, this.zoom, scrollX).toInt(),
            toViewCoord(y, this.zoom, scrollY).toInt()
        )
        this.zoom = zoom
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val localPaginationManager = pageLayoutManager ?: return
        canvas.save()
        canvas.scale(zoom, zoom)
        for (i in visiblePages.lower..visiblePages.upper) {
            pageManager?.drawPage(
                i,
                canvas,
                localPaginationManager.getPageLocation(i, getVisibleAreaInContentCoords())
            )
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val handled = event?.let { gestureTracker.feed(it) } ?: false
        return handled || super.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pageLayoutManager?.onViewportChanged(scrollY, height, zoom)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        pageLayoutManager?.onViewportChanged(scrollY, height, zoom)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val localScrollPositionToRestore = scrollPositionToRestore
        if (awaitingFirstLayout && localScrollPositionToRestore != null) {
            scrollToRestoredPosition(localScrollPositionToRestore, zoomToRestore ?: zoom)
        }
        awaitingFirstLayout = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        stopCollectingData()
        awaitingFirstLayout = true
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startCollectingData() else stopCollectingData()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCollectingData()
        awaitingFirstLayout = true
        pageManager?.onDetached()
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val state = PdfViewSavedState(superState)
        state.contentCenterX = scrollX.toFloat() + width / 2
        state.contentCenterY = scrollY.toFloat() + height / 2
        state.zoom = zoom
        state.documentUri = pdfDocument?.uri
        state.paginationModel = pageLayoutManager?.paginationModel
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is PdfViewSavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        stateToRestore = state
        if (pdfDocument != null) {
            maybeRestoreState()
        }
    }

    /**
     * Returns true if we are able to restore a previous state from savedInstanceState
     *
     * We are not be able to restore our previous state if it pertains to a different document, or
     * if it is missing critical data like page layout information.
     */
    private fun maybeRestoreState(): Boolean {
        val localStateToRestore = stateToRestore ?: return false
        val localPdfDocument = pdfDocument ?: return false
        if (
            localPdfDocument.uri != localStateToRestore.documentUri ||
                !localStateToRestore.hasEnoughStateToRestore
        ) {
            stateToRestore = null
            return false
        }
        pageLayoutManager =
            PageLayoutManager(
                    localPdfDocument,
                    backgroundScope,
                    DEFAULT_PAGE_PREFETCH_RADIUS,
                    paginationModel = localStateToRestore.paginationModel!!
                )
                .apply { onViewportChanged(scrollY, height, zoom) }
        val positionToRestore =
            PointF(localStateToRestore.contentCenterX, localStateToRestore.contentCenterY)
        if (awaitingFirstLayout) {
            scrollPositionToRestore = positionToRestore
            zoomToRestore = localStateToRestore.zoom
        } else {
            scrollToRestoredPosition(positionToRestore, localStateToRestore.zoom)
        }

        stateToRestore = null
        return true
    }

    private fun scrollToRestoredPosition(position: PointF, zoom: Float) {
        scrollTo(round(position.x - width / 2f).toInt(), round(position.y - height / 2f).toInt())
        this.zoom = zoom
        scrollPositionToRestore = null
        zoomToRestore = null
    }

    private fun startCollectingData() {
        val mainScope =
            CoroutineScope(HandlerCompat.createAsync(handler.looper).asCoroutineDispatcher())
        pageLayoutManager?.let { manager ->
            // Don't let two copies of this run concurrently
            val dimensionsToJoin = dimensionsCollector?.apply { cancel() }
            dimensionsCollector =
                mainScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    manager.dimensions.collect {
                        // Prevent 2 copies from running concurrently
                        dimensionsToJoin?.join()
                        onPageDimensionsReceived(it.first, it.second)
                    }
                }
            // Don't let two copies of this run concurrently
            val visiblePagesToJoin = visiblePagesCollector?.apply { cancel() }
            visiblePagesCollector =
                mainScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    manager.visiblePages.collect {
                        // Prevent 2 copies from running concurrently
                        visiblePagesToJoin?.join()
                        onVisiblePagesChanged()
                    }
                }
        }
        pageManager?.let { manager ->
            val invalidationToJoin = invalidationCollector?.apply { cancel() }
            invalidationCollector =
                mainScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    // Prevent 2 copies from running concurrently
                    invalidationToJoin?.join()
                    manager.invalidationSignalFlow.collect { invalidate() }
                }
        }
    }

    private fun stopCollectingData() {
        dimensionsCollector?.cancel()
        visiblePagesCollector?.cancel()
        invalidationCollector?.cancel()
    }

    /** Start using the [PdfDocument] to present PDF content */
    private fun onDocumentSet() {
        val localPdfDocument = pdfDocument ?: return
        pageManager = PageManager(localPdfDocument, backgroundScope, DEFAULT_PAGE_PREFETCH_RADIUS)
        // We'll either create our layout manager from restored state, or instantiate a new one
        if (!maybeRestoreState()) {
            pageLayoutManager =
                PageLayoutManager(localPdfDocument, backgroundScope, DEFAULT_PAGE_PREFETCH_RADIUS)
                    .apply { onViewportChanged(scrollY, height, zoom) }
        }
        // If not, we'll start doing this when we _are_ attached to a visible window
        if (isAttachedToVisibleWindow) {
            startCollectingData()
        }
    }

    private val View.isAttachedToVisibleWindow
        get() = isAttachedToWindow && windowVisibility == VISIBLE

    /**
     * Compute what content is visible from the current position of this View. Generally invoked on
     * position or size changes.
     */
    internal fun onZoomChanged() {
        pageLayoutManager?.onViewportChanged(scrollY, height, zoom)
        if (!gestureHandler.scaleInProgress && !gestureHandler.scrollInProgress) {
            pageManager?.maybeUpdateBitmaps(visiblePages, zoom)
        }
    }

    private fun reset() {
        scrollTo(0, 0)
        zoom = DEFAULT_INIT_ZOOM
        pageManager = null
        pageLayoutManager = null
        backgroundScope.coroutineContext.cancelChildren()
        stopCollectingData()
    }

    /** React to a change in visible pages (load new pages and clean up old ones) */
    private fun onVisiblePagesChanged() {
        pageManager?.maybeUpdateBitmaps(visiblePages, zoom)
    }

    /** React to a page's dimensions being made available */
    private fun onPageDimensionsReceived(pageNum: Int, size: Point) {
        pageManager?.onPageSizeReceived(pageNum, size, visiblePages.contains(pageNum), zoom)
        // Learning the dimensions of a page can change our understanding of the content that's in
        // the viewport
        pageLayoutManager?.onViewportChanged(scrollY, height, zoom)

        val localDeferredPosition = deferredScrollPosition
        val localDeferredPage = deferredScrollPage
        if (localDeferredPosition != null && localDeferredPosition.pageNum <= pageNum) {
            gotoPoint(localDeferredPosition)
            deferredScrollPosition = null
        } else if (localDeferredPage != null && localDeferredPage <= pageNum) {
            gotoPage(pageNum)
            deferredScrollPage = null
        }
    }

    /** Set the zoom, using the given point as a pivot point to zoom in or out of */
    internal fun zoomTo(zoom: Float, pivotX: Float, pivotY: Float) {
        // TODO(b/376299551) - Restore to developer-configured initial zoom value once that API is
        // implemented
        val newZoom = if (Float.NaN.equals(zoom)) DEFAULT_INIT_ZOOM else zoom
        val deltaX = scrollDeltaNeededForZoomChange(this.zoom, newZoom, pivotX, scrollX)
        val deltaY = scrollDeltaNeededForZoomChange(this.zoom, newZoom, pivotY, scrollY)

        this.zoom = newZoom
        scrollBy(deltaX, deltaY)
    }

    private fun scrollDeltaNeededForZoomChange(
        oldZoom: Float,
        newZoom: Float,
        pivot: Float,
        scroll: Int,
    ): Int {
        // Find where the given pivot point would move to when we change the zoom, and return the
        // delta.
        val contentPivot = toContentCoord(pivot, oldZoom, scroll)
        val movedZoomViewPivot: Float = toViewCoord(contentPivot, newZoom, scroll)
        return (movedZoomViewPivot - pivot).toInt()
    }

    /**
     * Computes the part of the content visible within the outer part of this view (including this
     * view's padding) in co-ordinates of the content.
     */
    private fun getVisibleAreaInContentCoords(): Rect {
        visibleAreaRect.set(
            toContentX(-paddingLeft.toFloat()).toInt(),
            toContentY(-paddingTop.toFloat()).toInt(),
            toContentX(viewportWidth.toFloat() + paddingRight).toInt(),
            toContentY(viewportHeight.toFloat() + paddingBottom).toInt(),
        )
        return visibleAreaRect
    }

    /** The height of the viewport, minus padding */
    private val viewportHeight: Int
        get() = bottom - top - paddingBottom - paddingTop

    /** The width of the viewport, minus padding */
    private val viewportWidth: Int
        get() = right - left - paddingRight - paddingLeft

    /** Converts an X coordinate in View space to an X coordinate in content space */
    private fun toContentX(viewX: Float): Float {
        return toContentCoord(viewX, zoom, scrollX)
    }

    /** Converts a Y coordinate in View space to a Y coordinate in content space */
    private fun toContentY(viewY: Float): Float {
        return toContentCoord(viewY, zoom, scrollY)
    }

    private fun toViewCoord(contentCoord: Float, zoom: Float, scroll: Int): Float {
        return (contentCoord * zoom) - scroll
    }

    /**
     * Converts a one-dimensional coordinate in View space to a one-dimensional coordinate in
     * content space
     */
    private fun toContentCoord(viewCoord: Float, zoom: Float, scroll: Int): Float {
        return (viewCoord + scroll) / zoom
    }

    public companion object {
        public const val DEFAULT_INIT_ZOOM: Float = 1.0f
        public const val DEFAULT_MAX_ZOOM: Float = 25.0f
        public const val DEFAULT_MIN_ZOOM: Float = 0.1f

        private const val DEFAULT_PAGE_PREFETCH_RADIUS: Int = 2

        private fun checkMainThread() {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "Property must be set on the main thread"
            }
        }
    }
}
