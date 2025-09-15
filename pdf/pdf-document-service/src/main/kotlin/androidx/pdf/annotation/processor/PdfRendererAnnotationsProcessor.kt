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

package androidx.pdf.annotation.processor

import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.pdf.adapter.PdfDocumentRenderer
import androidx.pdf.annotation.converters.PdfAnnotationConvertersFactory
import androidx.pdf.annotation.models.AddEditResult
import androidx.pdf.annotation.models.AnnotationResult
import androidx.pdf.annotation.models.EditId
import androidx.pdf.annotation.models.ModifyEditResult
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
internal class PdfRendererAnnotationsProcessor(private val renderer: PdfDocumentRenderer) :
    PdfAnnotationsProcessor {
    override fun process(annotations: List<PdfAnnotationData>): AnnotationResult {
        // TODO: Sort the list by page num and then add so that we don't have to always open/close
        // pages

        val (success, failures) = annotations.partition { applyAnnotationData(data = it) }
        return AnnotationResult(success = success, failures = failures.map { it.annotation })
    }

    override fun processAddEdits(annotations: List<PdfAnnotationData>): AddEditResult {
        // TODO: b/440914015 - Implements Add/Remove/Update Batch operations for PdfEdits in
        // PdfRenderer
        return AddEditResult(success = emptyList(), failures = emptyList())
    }

    override fun processUpdateEdits(annotations: List<PdfAnnotationData>): ModifyEditResult {
        // TODO: b/440914015 - Implements Add/Remove/Update Batch operations for PdfEdits in
        // PdfRenderer
        return ModifyEditResult(success = emptyList(), failures = emptyList())
    }

    override fun processRemoveEdits(editIds: List<EditId>): ModifyEditResult {
        // TODO: b/440914015 - Implements Add/Remove/Update Batch operations for PdfEdits in
        // PdfRenderer
        return ModifyEditResult(success = emptyList(), failures = emptyList())
    }

    // TODO: Revisit this code. We are losing exception info and generated annotation id
    private fun applyAnnotationData(data: PdfAnnotationData): Boolean {
        val converter = PdfAnnotationConvertersFactory.create<PdfAnnotation>(data.annotation)

        try {
            val convertedAnnotation = converter.convert(data.annotation)

            renderer.withPage(pageNum = data.editId.pageNum) { page ->
                val unused = page.addPageAnnotation(convertedAnnotation)
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
