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
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import androidx.annotation.NonNull
import androidx.annotation.VisibleForTesting
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import androidx.pdf.R
import androidx.pdf.content.PdfPageGotoLinkContent
import androidx.pdf.content.PdfPageLinkContent
import androidx.pdf.util.ExternalLinks
import androidx.pdf.util.buildPageIndicatorLabel

/**
 * Accessibility delegate for PdfView that provides a virtual view hierarchy for pages.
 *
 * This helper class allows accessibility services to interact with individual pages as virtual
 * views, enabling navigation and content exploration.
 */
internal class AccessibilityPageHelper(
    private val pdfView: PdfView,
    private val pageLayoutManager: PageLayoutManager,
    private val pageManager: PageManager,
) : ExploreByTouchHelper(pdfView) {

    private val gotoLinks: MutableMap<Int, LinkWrapper<PdfPageGotoLinkContent>> = mutableMapOf()
    private val urlLinks: MutableMap<Int, LinkWrapper<PdfPageLinkContent>> = mutableMapOf()
    private val totalPages = pdfView.pdfDocument?.pageCount ?: 0
    private var isLinksLoaded = false

    private val fastScrollVerticalThumbDrawableId = FAST_SCROLLER_OFFSET + 1
    private val fastScrollPageIndicatorBackgroundDrawableId = FAST_SCROLLER_OFFSET + 2

    public override fun getVirtualViewAt(x: Float, y: Float): Int {
        val visiblePages = pageLayoutManager.visiblePages.value

        if (
            pdfView.lastFastScrollerVisibility &&
                pdfView.fastScroller?.isPointOnThumb(x, y, pdfView.width) == true
        ) {
            return fastScrollVerticalThumbDrawableId
        }

        if (
            pdfView.lastFastScrollerVisibility &&
                pdfView.fastScroller?.isPointOnIndicator(
                    pdfView.context,
                    pageLayoutManager.fullyVisiblePages.value,
                    x,
                    y,
                    totalPages,
                    pdfView.width,
                    pdfView.scrollX,
                ) == true
        ) {
            return fastScrollPageIndicatorBackgroundDrawableId
        }

        val contentX = pdfView.toContentX(x).toInt()
        val contentY = pdfView.toContentY(y).toInt()

        if (!isLinksLoaded) {
            loadPageLinks()
        }

        gotoLinks.entries
            .find { (_, wrapper) ->
                wrapper.linkBounds.contains(contentX.toFloat(), contentY.toFloat())
            }
            ?.let {
                return it.key
            }

        urlLinks.entries
            .find { (_, wrapper) ->
                wrapper.linkBounds.contains(contentX.toFloat(), contentY.toFloat())
            }
            ?.let {
                return it.key
            }

        // Check if the coordinates fall within the visible page bounds
        return (visiblePages.pages.lower..visiblePages.pages.upper).firstOrNull { page ->
            pageLayoutManager
                .getPageLocation(page, pdfView.getVisibleAreaInContentCoords())
                .contains(contentX, contentY)
        } ?: HOST_ID
    }

    public override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        val visiblePages = pageLayoutManager.visiblePages.value
        loadPageLinks()

        virtualViewIds.apply {
            addAll(visiblePages.pages.lower..visiblePages.pages.upper)
            addAll(gotoLinks.keys)
            addAll(urlLinks.keys)
            if (isFastScrollerStateValid()) {
                add(fastScrollVerticalThumbDrawableId)
                add(fastScrollPageIndicatorBackgroundDrawableId)
            }
        }
    }

    public override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        @NonNull node: AccessibilityNodeInfoCompat
    ) {
        if (!isLinksLoaded) loadPageLinks()

        when {
            virtualViewId == fastScrollVerticalThumbDrawableId -> populateFastScrollThumbNode(node)
            virtualViewId == fastScrollPageIndicatorBackgroundDrawableId ->
                populateFastScrollPageIndicatorNode(node)
            virtualViewId < totalPages -> populateNodeForPage(virtualViewId, node)
            else -> {
                // Populate node for GoTo links and URL links
                gotoLinks[virtualViewId]?.let { populateGotoLinkNode(it, node) }
                urlLinks[virtualViewId]?.let { populateUrlLinkNode(it, node) }
            }
        }
    }

    private fun populateFastScrollThumbNode(node: AccessibilityNodeInfoCompat) {
        if (!isFastScrollerStateValid()) {
            node.contentDescription = ""
            node.setBoundsInScreen(Rect())
            node.isFocusable = false
            return
        }

        val thumbBounds = pdfView.fastScroller?.getThumbScreenBounds() ?: Rect()
        node.apply {
            contentDescription = pdfView.context.getString(R.string.fast_scroller_thumb)
            setBoundsInScreenFromBoundsInParent(node, thumbBounds)
            isFocusable = pdfView.lastFastScrollerVisibility
        }
    }

    private fun populateFastScrollPageIndicatorNode(node: AccessibilityNodeInfoCompat) {
        if (!isFastScrollerStateValid()) {
            node.contentDescription = ""
            node.setBoundsInScreen(Rect())
            node.isFocusable = false
            return
        }

        val indicatorBounds = pdfView.fastScroller?.getIndicatorScreenBounds() ?: Rect()
        val currentLabel =
            buildPageIndicatorLabel(
                pdfView.context,
                pageLayoutManager.fullyVisiblePages.value,
                totalPages,
                R.string.desc_page_single,
                R.string.desc_page_single
            )
        node.apply {
            contentDescription = currentLabel
            setBoundsInScreenFromBoundsInParent(node, indicatorBounds)
            isFocusable = pdfView.lastFastScrollerVisibility
        }
    }

    /**
     * Checks and sets the AccessibilityNodeInfoCompat to an invalid state if the fast scroller
     * state is invalid.
     *
     * @return True if the fast scroller state is valid, false otherwise.
     */
    private fun isFastScrollerStateValid(): Boolean =
        pdfView.lastFastScrollerVisibility && pdfView.positionIsStable

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?
    ): Boolean {
        // This view does not handle any actions.
        return false
    }

    private fun populateNodeForPage(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        val pageText = pageManager.pages[virtualViewId]?.pageText
        val pageBounds =
            pageLayoutManager.getPageLocation(
                virtualViewId,
                pdfView.getVisibleAreaInContentCoords()
            )

        node.apply {
            contentDescription =
                pageText?.let { getContentDescriptionForPage(pdfView.context, virtualViewId, it) }
                    ?: getDefaultDesc(pdfView.context, virtualViewId)

            setBoundsInScreenFromBoundsInParent(
                node,
                scalePageBounds(RectF(pageBounds), pdfView.zoom)
            )
            isFocusable = true
        }
    }

    private fun populateGotoLinkNode(
        linkWrapper: LinkWrapper<PdfPageGotoLinkContent>,
        node: AccessibilityNodeInfoCompat
    ) {
        val bounds = scalePageBounds(linkWrapper.linkBounds, pdfView.zoom)

        node.apply {
            contentDescription =
                pdfView.context.getString(
                    R.string.desc_goto_link,
                    linkWrapper.content.destination.pageNumber + 1
                )
            setBoundsInScreenFromBoundsInParent(this, bounds)
            isFocusable = true
        }
    }

    private fun populateUrlLinkNode(
        linkWrapper: LinkWrapper<PdfPageLinkContent>,
        node: AccessibilityNodeInfoCompat
    ) {
        val bounds = scalePageBounds(linkWrapper.linkBounds, pdfView.zoom)
        node.apply {
            contentDescription =
                ExternalLinks.getDescription(linkWrapper.content.uri.toString(), pdfView.context)
            setBoundsInScreenFromBoundsInParent(node, bounds)
            isFocusable = true
        }
    }

    /**
     * Calculates the adjusted bounds of a link relative to the full content of the PDF.
     *
     * @param pageNumber The 0-indexed page number.
     * @param linkBounds The bounds of the link on the page.
     * @return The adjusted bounds in the content coordinate system.
     */
    fun getLinkBounds(pageNumber: Int, linkBounds: RectF): RectF {
        val pageBounds =
            pageLayoutManager.getPageLocation(pageNumber, pdfView.getVisibleAreaInContentCoords())
        return RectF(
            linkBounds.left + pageBounds.left,
            linkBounds.top + pageBounds.top,
            linkBounds.right + pageBounds.left,
            linkBounds.bottom + pageBounds.top
        )
    }

    /**
     * Scales the bounds of a page based on the current zoom level.
     *
     * @param bounds The original bounds to scale.
     * @param zoom The zoom level.
     * @return The scaled bounds as a Rect.
     */
    @VisibleForTesting
    fun scalePageBounds(bounds: RectF, zoom: Float): Rect {
        return Rect(
            (bounds.left * zoom).toInt(),
            (bounds.top * zoom).toInt(),
            (bounds.right * zoom).toInt(),
            (bounds.bottom * zoom).toInt()
        )
    }

    /**
     * Loads the links for the visible pages.
     *
     * This method fetches the GoTo links and URL links for the currently visible pages and stores
     * them in the corresponding maps.
     */
    fun loadPageLinks() {
        val visiblePages = pageLayoutManager.visiblePages.value

        // Clear existing links and fetch new ones for the visible pages
        gotoLinks.clear()
        urlLinks.clear()

        var cumulativeId = totalPages

        (visiblePages.pages.lower..visiblePages.pages.upper).forEach { pageIndex ->
            pageManager.pages[pageIndex]?.links?.let { links ->
                links.gotoLinks.forEach { link ->
                    gotoLinks[cumulativeId] =
                        LinkWrapper(pageIndex, link, getLinkBounds(pageIndex, link.bounds.first()))
                    cumulativeId++
                }
                links.externalLinks.forEach { link ->
                    urlLinks[cumulativeId] =
                        LinkWrapper(pageIndex, link, getLinkBounds(pageIndex, link.bounds.first()))
                    cumulativeId++
                }
            }
        }
        isLinksLoaded = true
    }

    /**
     * Updates accessibility node with extracted page text when ready.
     *
     * @param pageNum 0-indexed page number.
     */
    fun onPageTextReady(pageNum: Int) {
        val pageText = pageManager.pages.get(pageNum)?.pageText

        if (pageText != null) {
            // Update accessibility node with new text.
            invalidateVirtualView(pageNum)
        }
    }

    companion object {
        internal const val FAST_SCROLLER_OFFSET: Int = 1000001

        /**
         * Builds the content description for a page.
         *
         * @param context The context for accessing resources.
         * @param pageText The extracted text content of the page, or null if not loaded.
         * @param pageNum The 0-indexed page number.
         * @return The content description string.
         */
        private fun getContentDescriptionForPage(
            context: Context,
            pageNum: Int,
            pageText: String?
        ): String {
            return when {
                pageText == null -> getDefaultDesc(context, pageNum)
                pageText.trim().isEmpty() -> context.getString(R.string.desc_empty_page)
                else -> context.getString(R.string.desc_page_with_text, pageNum + 1, pageText)
            }
        }

        /**
         * Gets the default content description for a page. This is used as a placeholder while the
         * actual content description is loading.
         *
         * @param context The context for accessing resources.
         * @param pageNum The 0-indexed page number.
         * @return The default content description string.
         */
        private fun getDefaultDesc(context: Context, pageNum: Int): String {
            return context.getString(R.string.desc_page, pageNum + 1)
        }
    }
}

/**
 * A wrapper class for links in the PDF document.
 *
 * @param T The type of link content (GotoLink or URL link).
 * @param pageNumber The 0-indexed page number where the link is located.
 * @param content The link's content (GotoLink or URL link).
 * @param linkBounds The link's bounds in the full PDF's content coordinates.
 */
private data class LinkWrapper<T>(val pageNumber: Int, val content: T, val linkBounds: RectF)
