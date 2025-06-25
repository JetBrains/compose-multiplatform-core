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

package androidx.pdf.compose

import android.net.Uri
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.pdf.PdfDocument
import androidx.pdf.R
import androidx.pdf.content.ExternalLink
import androidx.pdf.view.PdfView
import kotlin.random.Random

/**
 * A [Composable] that presents PDF content, provided as [PdfDocument]
 *
 * @param pdfDocument the PDF content to present
 * @param modifier the [Modifier] to be applied to the PDF viewer
 * @param minZoom the minimum zoom / scaling factor that can be applied to the PDF viewer
 * @param maxZoom the maximum zoom / scaling factor that can be applied to the PDF viewer
 */
@Composable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun PdfViewer(
    pdfDocument: PdfDocument?,
    state: PdfViewerState,
    modifier: Modifier = Modifier,
    minZoom: Float = PdfView.DEFAULT_MIN_ZOOM,
    maxZoom: Float = PdfView.DEFAULT_MAX_ZOOM,
    @DrawableRes fastScrollVerticalThumbDrawable: Int = R.drawable.fast_scroll_thumb_drawable,
    @DrawableRes
    fastScrollPageIndicatorBackgroundDrawable: Int = R.drawable.page_indicator_background,
    @DimenRes fastScrollPageIndicatorMarginEnd: Int = R.dimen.page_indicator_right_margin,
    @DimenRes fastScrollVerticalThumbMarginEnd: Int = R.dimen.scroll_thumb_margin_end,
    onUrlLinkClicked: ((Uri) -> Boolean)? = null,
) {
    // Create and remember an ID for PdfView so that it retains state across compositions and
    // recreations
    val pdfViewId = rememberSaveable { Random(System.currentTimeMillis()).nextInt() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PdfView(context).apply {
                this.id = pdfViewId
                state.pdfView = this
            }
        },
        onRelease = { view ->
            state.pdfView = null
            view.setLinkClickListener(null)
        },
        // Factory will execute exactly once; update is the correct place to supply mutable states
        update = { view ->
            view.pdfDocument = pdfDocument
            view.minZoom = minZoom
            view.maxZoom = maxZoom
            view.fastScrollVerticalThumbDrawable =
                requireNotNull(view.context.getDrawable(fastScrollVerticalThumbDrawable)) {
                    "Invalid fastScrollVerticalThumbDrawable"
                }
            view.fastScrollPageIndicatorBackgroundDrawable =
                requireNotNull(
                    view.context.getDrawable(fastScrollPageIndicatorBackgroundDrawable)
                ) {
                    "Invalid fastScrollVerticalThumbDrawable"
                }
            view.fastScrollPageIndicatorMarginEnd =
                view.resources.getDimensionPixelSize(fastScrollPageIndicatorMarginEnd)
            view.fastScrollVerticalThumbMarginEnd =
                view.resources.getDimensionPixelSize(fastScrollVerticalThumbMarginEnd)
            view.setLinkClickListener(PdfViewerLinkClickListener(onUrlLinkClicked))
        },
    )
}

/**
 * Bridge between the lambda-based [PdfViewer] API for custom link handling, and the listener
 * interface [PdfView] API for the same.
 *
 * Notably this defers to the default behavior in [PdfView] in the event [behavior] is null, i.e. by
 * returning false from [onLinkClicked]
 */
private class PdfViewerLinkClickListener(private val behavior: ((Uri) -> Boolean)?) :
    PdfView.LinkClickListener {
    override fun onLinkClicked(externalLink: ExternalLink): Boolean {
        return behavior?.invoke(externalLink.uri) ?: false
    }
}
