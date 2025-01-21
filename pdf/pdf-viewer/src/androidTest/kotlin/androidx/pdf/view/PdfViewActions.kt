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

import android.graphics.PointF
import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.matcher.ViewMatchers
import org.hamcrest.Matcher
import org.hamcrest.Matchers

/** Performs a [ViewAction] that scrolls [PdfView] by [dy] pixels in the Y direction */
internal fun ViewInteraction.scrollByY(dy: Int) = this.perform(ScrollPdfViewByPixels(dy = dy))

/** Performs a [ViewAction] that sets [PdfView.zoom] to [newZoom] */
internal fun ViewInteraction.zoomTo(newZoom: Float) = this.perform(ZoomPdfView(newZoom))

/** Performs a [ViewAction] that calls [PdfView.scrollToPage] with the provided [pageNum] */
internal fun ViewInteraction.scrollToPage(pageNum: Int) = this.perform(ScrollPdfViewToPage(pageNum))

/** Performs a [ViewAction] that calls [PdfView.scrollToPosition] with the provided [pdfPoint] */
internal fun ViewInteraction.scrollToPosition(pdfPoint: PdfPoint) =
    this.perform(ScrollPdfViewToPage(pdfPoint))

/** [ViewAction] which scrolls a [PdfView] by ([dx], [dy]) */
private class ScrollPdfViewByPixels(val dx: Int = 0, val dy: Int = 0) : ViewAction {
    init {
        require(dx != 0 || dy != 0) { "Must scroll in at least 1 dimension" }
    }

    override fun getConstraints(): Matcher<View> =
        Matchers.allOf(
            ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE),
            ViewMatchers.isAssignableFrom(PdfView::class.java)
        )

    override fun getDescription() = "Scroll PdfView by $dx, $dy"

    override fun perform(uiController: UiController, view: View) {
        view.scrollBy(dx, dy)
        uiController.loopMainThreadUntilIdle()
    }
}

/** [ViewAction] which sets [PdfView.zoom] */
private class ZoomPdfView(val newZoom: Float) : ViewAction {
    override fun getConstraints(): Matcher<View> =
        Matchers.allOf(
            ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE),
            ViewMatchers.isAssignableFrom(PdfView::class.java)
        )

    override fun getDescription() = "Zoom PdfView to $newZoom"

    override fun perform(uiController: UiController, view: View) {
        // This should be guaranteed by our constraints, but this makes smartcasts work nicely
        check(view is PdfView)
        view.zoom = newZoom
        uiController.loopMainThreadUntilIdle()
    }
}

/**
 * [ViewAction] which invokes [PdfView.scrollToPage] if only a page number is provided, or
 * [PdfView.scrollToPosition] if a point on the page is provided
 */
private class ScrollPdfViewToPage : ViewAction {
    private val pageNum: Int
    private val pointOnPage: PointF?

    constructor(pageNum: Int) {
        this.pageNum = pageNum
        pointOnPage = null
    }

    constructor(point: PdfPoint) {
        pageNum = point.pageNum
        pointOnPage = point.pagePoint
    }

    override fun getConstraints(): Matcher<View> =
        Matchers.allOf(
            ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE),
            ViewMatchers.isAssignableFrom(PdfView::class.java)
        )

    override fun getDescription(): String {
        return if (pointOnPage != null) {
            "Scroll to $pointOnPage on $pageNum"
        } else {
            "Scroll to $pageNum"
        }
    }

    override fun perform(uiController: UiController, view: View) {
        // This should be guaranteed by our constraints, but this makes smartcasts work nicely
        check(view is PdfView)
        if (pointOnPage != null) {
            view.scrollToPosition(PdfPoint(pageNum, pointOnPage), animateScroll = false)
        } else {
            view.scrollToPage(pageNum, animateScroll = false)
        }
        uiController.loopMainThreadUntilIdle()
    }
}
