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

package androidx.pdf.annotation.draftstate

import androidx.pdf.annotation.AnnotationHandleIdGenerator
import androidx.pdf.annotation.KeyedPdfAnnotation
import androidx.pdf.annotation.models.EditId
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData
import androidx.pdf.annotation.models.PdfEdits
import androidx.pdf.annotation.models.TestPdfAnnotation

class FakeAnnotationEditsDraftState : AnnotationEditsDraftState {
    private val drafts = mutableMapOf<Int, MutableMap<String, PdfAnnotation>>()

    override fun addDraftAnnotation(annotation: PdfAnnotation): String {
        val handle = AnnotationHandleIdGenerator.generateId()
        drafts.getOrPut(annotation.pageNum) { mutableMapOf() }[handle] = annotation
        return handle
    }

    override fun getDraftAnnotation(pageNum: Int, handleId: String): PdfAnnotation? {
        return drafts[pageNum]?.get(handleId)
    }

    override fun getEdits(pageNum: Int): List<PdfAnnotationData> {
        return drafts[pageNum]?.map { (handle, annot) ->
            PdfAnnotationData(EditId(pageNum, handle), annot)
        } ?: emptyList()
    }

    override fun removeAnnotation(pageNum: Int, handleId: String): PdfAnnotation {
        return drafts[pageNum]?.remove(handleId)!!
    }

    override fun updateDraftAnnotation(
        pageNum: Int,
        handleId: String,
        newAnnotation: PdfAnnotation,
    ): PdfAnnotation {
        val old = drafts[pageNum]?.get(handleId) ?: throw NoSuchElementException()
        drafts[pageNum]?.put(handleId, newAnnotation)
        return old
    }

    // Unused interface methods stubbed
    override fun getDraftAnnotations(pageNum: Int): List<KeyedPdfAnnotation> = emptyList()

    override fun addEditById(id: EditId, annotation: PdfAnnotation) {}

    override fun addEdit(annotation: PdfAnnotation): EditId = EditId(0, "")

    override fun removeEdit(editId: EditId): PdfAnnotation = TestPdfAnnotation(editId.pageNum)

    override fun updateEdit(editId: EditId, annotation: PdfAnnotation): PdfAnnotation =
        TestPdfAnnotation(editId.pageNum)

    override fun toPdfEdits(): PdfEdits = PdfEdits(emptyMap())

    override fun clear() {}
}
