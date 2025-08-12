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

import android.graphics.Rect
import android.os.DeadObjectException
import androidx.pdf.PdfDocument
import androidx.pdf.exceptions.RequestFailedException
import androidx.pdf.exceptions.RequestMetadata
import androidx.pdf.models.FormEditRecord
import androidx.pdf.util.FORM_APPLY_EDIT_REQUEST_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Handles restoring the form filling state by applying [FormEditRecord]s to the [PdfDocument].
 * Notifies the caller via [onRestoreTaskComplete] when the restoration is complete, providing a map
 * of invalidated areas.
 */
internal class PdfFormFillingStateManager(
    private val pdfDocument: PdfDocument,
    private val backgroundScope: CoroutineScope,
    private val errorFlow: MutableSharedFlow<Throwable>,
    private val onRestoreTaskStarted: () -> Unit,
    private val onRestoreTaskComplete: (pagesInvalidatedAreas: Map<Int, List<Rect>>) -> Unit,
) {
    private var applyEditJob: Job? = null

    private val formEditTypesToCompress =
        setOf(FormEditRecord.EDIT_TYPE_SET_TEXT, FormEditRecord.EDIT_TYPE_SET_INDICES)

    fun restoreFormFillingState(pdfEditRecords: List<FormEditRecord>?) {
        if (pdfEditRecords == null) return
        val compressedEditRecords = compressFormEdits(pdfEditRecords)
        val pagesInvalidatedAreas = mutableMapOf<Int, List<Rect>>()
        val previousApplyEditJob = applyEditJob
        onRestoreTaskStarted()
        applyEditJob =
            backgroundScope.launch {
                previousApplyEditJob?.cancelAndJoin()
                compressedEditRecords.forEach { editRecord ->
                    val invalidatedRects = tryApplyEdit(editRecord)
                    if (invalidatedRects.isNotEmpty()) {
                        pagesInvalidatedAreas[editRecord.pageNumber] =
                            pagesInvalidatedAreas.getOrDefault(editRecord.pageNumber, emptyList()) +
                                invalidatedRects
                    }
                }
                onRestoreTaskComplete(pagesInvalidatedAreas.toMap())
            }
    }

    private fun compressFormEdits(formEditRecords: List<FormEditRecord>): List<FormEditRecord> {
        val compressedEdits = mutableListOf<FormEditRecord>()

        for (edit in formEditRecords) {
            compressedEdits.removeIf {
                edit.type in formEditTypesToCompress &&
                    it.pageNumber == edit.pageNumber &&
                    it.widgetIndex == edit.widgetIndex
            }
            compressedEdits.add(edit)
        }
        return compressedEdits
    }

    private suspend fun tryApplyEdit(editRecord: FormEditRecord): List<Rect> {
        try {
            return pdfDocument.applyEdit(editRecord.pageNumber, editRecord)
        } catch (error: Exception) {
            when (error) {
                is DeadObjectException -> {
                    val exception =
                        RequestFailedException(
                            requestMetadata =
                                RequestMetadata(
                                    requestName = FORM_APPLY_EDIT_REQUEST_NAME,
                                    pageRange = editRecord.pageNumber..editRecord.pageNumber,
                                ),
                            throwable = error,
                        )
                    errorFlow.emit(exception)
                }
            }
            return emptyList()
        }
    }
}
