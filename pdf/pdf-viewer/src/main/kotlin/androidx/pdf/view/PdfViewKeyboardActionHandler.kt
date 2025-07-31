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

package androidx.pdf.view

import androidx.pdf.util.ClipboardUtils

/**
 * Performs actions in response to keyboard shortcuts detected by [PdfViewExternalInputManager]
 *
 * @param pdfView The view on which the actions are to be taken.
 */
internal class PdfViewKeyboardActionHandler(pdfView: PdfView) :
    PdfViewExternalInputHandler(pdfView) {

    override val horizontalScrollFactor = HORIZONTAL_SCROLL_FACTOR
    override val verticalScrollFactor = VERTICAL_SCROLL_FACTOR

    private val pivotX: Float
        get() = (pdfView.left + pdfView.right) / 2f

    private val pivotY: Float
        get() = pdfView.top.toFloat()

    fun copySelection() {
        val text = (pdfView.currentSelection as? TextSelection)?.text ?: return
        ClipboardUtils.copyToClipboard(pdfView.context, text.toString())
        pdfView.clearSelection()
    }

    fun zoomIn() {
        zoomIn(pivotX, pivotY)
    }

    fun zoomOut() {
        zoomOut(pivotX, pivotY)
    }

    fun zoomToDefault() {
        applyZoom(pdfView.getDefaultZoom(), pivotX, pivotY)
    }

    private companion object {
        const val HORIZONTAL_SCROLL_FACTOR = 20
        const val VERTICAL_SCROLL_FACTOR = 20
    }
}
