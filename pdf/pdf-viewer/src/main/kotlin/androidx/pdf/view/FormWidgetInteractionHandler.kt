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

import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.os.DeadObjectException
import androidx.pdf.PdfDocument
import androidx.pdf.R
import androidx.pdf.exceptions.RequestFailedException
import androidx.pdf.exceptions.RequestMetadata
import androidx.pdf.models.FormEditRecord
import androidx.pdf.models.FormWidgetInfo
import androidx.pdf.util.FORM_APPLY_EDIT_REQUEST_NAME
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Handles user interaction with different types of form widgets in a PDF document and assembles a
 * [FormEditRecord] based on the interaction. The class also handles responsibilities like creating
 * drop-down menus or creating text fields for user input.
 */
internal class FormWidgetInteractionHandler(
    private val context: Context,
    private val pdfDocument: PdfDocument,
    private val backgroundScope: CoroutineScope,
    private val errorFlow: MutableSharedFlow<Throwable>,
) {

    private val _invalidatedAreas =
        MutableSharedFlow<Pair<Int, List<Rect>>>(replay = pdfDocument.pageCount)

    val invalidatedAreas: SharedFlow<Pair<Int, List<Rect>>>
        get() = _invalidatedAreas

    private var currentApplyEditJob: Job? = null

    /** Entry point to handle interaction with the formWidget. */
    fun handleInteraction(touchPoint: PdfPoint, formWidgetInfo: FormWidgetInfo) {
        if (formWidgetInfo.readOnly) return

        val pageNum = touchPoint.pageNum
        val pdfCoordinates = touchPoint.pagePoint
        // switch case to delegate to the appropriate handler
        when (formWidgetInfo.widgetType) {
            FormWidgetInfo.WIDGET_TYPE_CHECKBOX,
            FormWidgetInfo.WIDGET_TYPE_RADIOBUTTON,
            FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON -> {
                handleInteractionWithClickTypeWidget(pageNum, pdfCoordinates, formWidgetInfo)
            }
            FormWidgetInfo.WIDGET_TYPE_TEXTFIELD -> {
                handleInteractionWithTextWidget(pageNum, formWidgetInfo)
            }
            FormWidgetInfo.WIDGET_TYPE_LISTBOX,
            FormWidgetInfo.WIDGET_TYPE_COMBOBOX -> {
                handleInteractionWithChoiceSelectionWidget(pageNum, formWidgetInfo)
            }
        }
    }

    /** Implements logic to take user input in a click-type widget. */
    fun handleInteractionWithClickTypeWidget(
        pageNum: Int,
        pdfCoordinates: PointF,
        formWidgetInfo: FormWidgetInfo,
    ) {
        val formEditRecord =
            FormEditRecord(
                pageNum,
                formWidgetInfo.widgetIndex,
                clickPoint = Point(pdfCoordinates.x.toInt(), pdfCoordinates.y.toInt()),
            )
        applyEditRecord(pageNum, formEditRecord)
    }

    /** Implements logic to take user input in a text field. Once done assembles a FormEditRecord */
    fun handleInteractionWithTextWidget(pageNum: Int, formWidgetInfo: FormWidgetInfo) {}

    /**
     * Creates a drop-down menu with a list of options. Once option is selected by the user,
     * assembles a FormEditRecord
     */
    fun handleInteractionWithChoiceSelectionWidget(pageNum: Int, formWidgetInfo: FormWidgetInfo) {
        if (formWidgetInfo.multiSelect) {
            showMultiChoiceSelectMenu(formWidgetInfo)
        } else {
            showSingleChoiceSelectMenu(formWidgetInfo)
        }
    }

    private fun showSingleChoiceSelectMenu(formWidgetInfo: FormWidgetInfo) {
        var selectedItemIndex = 0
        val listItemValues: List<String> = formWidgetInfo.listItems.map { it.label }

        MaterialAlertDialogBuilder(context)
            .setSingleChoiceItems(listItemValues.toTypedArray(), selectedItemIndex) { dialog, which
                ->
                selectedItemIndex = which
            }
            .setPositiveButton(context.getString(R.string.confirm_selection)) { dialog, which ->
                // TODO(b/410008872): Function to assemble FormEditRecord based on user selection.
                dialog.dismiss()
            }
            .show()
    }

    private fun showMultiChoiceSelectMenu(formWidgetInfo: FormWidgetInfo) {
        val selectedItems = BooleanArray(formWidgetInfo.listItems.size)
        val listItemValues: List<String> = formWidgetInfo.listItems.map { it.label }
        MaterialAlertDialogBuilder(context)
            .setMultiChoiceItems(listItemValues.toTypedArray(), selectedItems) {
                dialog,
                which,
                isChecked ->
                selectedItems[which] = isChecked
            }
            .setPositiveButton(context.getString(R.string.confirm_selection)) { dialog, which ->
                // TODO(b/410008872): Function to assemble FormEditRecord based on user selection.
                dialog.dismiss()
            }
            .show()
    }

    /** Calls pdfDocument.applyEdit inside a CoroutineScope */
    fun applyEditRecord(pageNum: Int, formEditRecord: FormEditRecord) {
        val previousApplyEditJob = currentApplyEditJob
        currentApplyEditJob =
            backgroundScope.launch {
                previousApplyEditJob?.join()
                try {
                    _invalidatedAreas.emit(
                        pageNum to pdfDocument.applyEdit(pageNum, formEditRecord)
                    )
                } catch (error: Exception) {
                    when (error) {
                        is DeadObjectException,
                        is IllegalArgumentException -> {
                            val exception =
                                RequestFailedException(
                                    requestMetadata =
                                        RequestMetadata(
                                            requestName = FORM_APPLY_EDIT_REQUEST_NAME,
                                            pageRange = pageNum..pageNum,
                                        ),
                                    throwable = error,
                                )
                            errorFlow.emit(exception)
                        }
                    }
                }
            }
    }
}
