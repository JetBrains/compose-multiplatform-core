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
package androidx.pdf.selection

import android.content.Context
import androidx.pdf.selection.model.HyperLinkSelection
import androidx.pdf.util.ClipboardUtils

internal class HyperLinkSelectionMenuProvider(private val context: Context) :
    SelectionMenuProvider<HyperLinkSelection> {

    override suspend fun getMenuItems(selection: HyperLinkSelection): List<ContextMenuComponent> {
        val menuItems: MutableList<ContextMenuComponent> = mutableListOf()
        menuItems += getHyperLinkMenuItem(selection)
        menuItems += LinkSelectionMenuProvider.getDefaultMenuItems(context)
        return menuItems
    }

    private fun getHyperLinkMenuItem(selection: HyperLinkSelection): ContextMenuComponent {
        return DefaultSelectionMenuComponent(
            key = PdfSelectionMenuKeys.SmartActionKey,
            label = context.getString(android.R.string.copyUrl),
        ) { pdfView ->
            val localCurrentSelection = pdfView.currentSelection
            if (localCurrentSelection is HyperLinkSelection) {
                ClipboardUtils.copyToClipboard(context, localCurrentSelection.link.toString())
            }
            close()
            pdfView.clearSelection()
        }
    }
}
