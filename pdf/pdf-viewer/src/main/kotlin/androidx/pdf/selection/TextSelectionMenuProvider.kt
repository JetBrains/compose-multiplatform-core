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

import android.app.PendingIntent
import android.content.Context
import android.os.LocaleList
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import androidx.pdf.featureflag.PdfFeatureFlags
import androidx.pdf.util.ClipboardUtils
import androidx.pdf.view.TextSelection

internal class TextSelectionMenuProvider(private val context: Context) {
    private var textClassificationManager: TextClassificationManager? = null
    private var textClassifier: TextClassifier? = null

    init {
        textClassificationManager =
            context.getSystemService(Context.TEXT_CLASSIFICATION_SERVICE)
                as? TextClassificationManager?
        textClassifier = textClassificationManager?.textClassifier
    }

    public fun getMenuItems(textSelection: TextSelection): List<ContextMenuComponent> {
        val menuItems: MutableList<ContextMenuComponent> = mutableListOf()
        if (PdfFeatureFlags.isSmartActionMenuComponentEnabled) {
            menuItems.addAll(getSmartMenuItems(textSelection))
        }
        menuItems.addAll(getDefaultMenuItems())
        return menuItems
    }

    private fun getDefaultMenuItems(): List<ContextMenuComponent> {
        val defaultMenuItems =
            listOf<ContextMenuComponent>(
                DefaultSelectionMenuComponent(
                    key = PdfSelectionMenuKeys.CopyKey,
                    label = context.getString(android.R.string.copy),
                ) { pdfView ->
                    // We can't copy the current selection if no text is selected
                    val text = (pdfView.currentSelection as? TextSelection)?.text
                    if (text != null) ClipboardUtils.copyToClipboard(context, text.toString())
                    // close the context menu upon copy action
                    close()
                    // After completion of action the selection should be cleared.
                    pdfView.clearSelection()
                },
                DefaultSelectionMenuComponent(
                    key = PdfSelectionMenuKeys.SelectAllKey,
                    label = context.getString(android.R.string.selectAll),
                ) { pdfView ->
                    val page = pdfView.currentSelection?.bounds?.first()?.pageNum
                    // We can't select all if we don't know what page the selection is on, or if
                    // we don't know the size of that page
                    if (page != null) {
                        // Action mode for old selection should be closed which will be triggered
                        // after select all is completed with current selection.
                        close()
                        pdfView.selectAllTextOnPage(page)
                    }
                },
            )
        return defaultMenuItems
    }

    private fun getSmartMenuItems(textSelection: TextSelection): List<ContextMenuComponent> {
        val smartMenuItems: MutableList<ContextMenuComponent> = mutableListOf()
        // Cannot add smart menu items if text classifier is not present on device
        val localTextClassifier = textClassifier ?: return smartMenuItems
        val textLength = textSelection.text.length
        // This is the char limit for the textClassifier library to produce
        // any meaningful action item.
        if (textLength > MAX_CHAR_LIMIT) {
            return smartMenuItems
        }
        val textClassification =
            localTextClassifier.classifyText(
                textSelection.text,
                0,
                textLength,
                LocaleList.getAdjustedDefault(),
            )
        textClassification.actions?.forEach { action ->
            smartMenuItems.add(
                SmartSelectionMenuComponent(
                    key = PdfSelectionMenuKeys.SmartActionKey,
                    label = action.title as String,
                    contentDescription = action.contentDescription as? String?,
                    leadingIcon = action.icon.loadDrawable(context),
                    onClick = { pdfView ->
                        try {
                            action.actionIntent.send()
                        } catch (e: PendingIntent.CanceledException) {
                            // TODO(b/431669141): Propagate Exception to Host App.
                        } finally {
                            close()
                            pdfView.clearSelection()
                        }
                    },
                )
            )
        }
        return smartMenuItems
    }

    private companion object {
        const val MAX_CHAR_LIMIT = 500
    }
}
