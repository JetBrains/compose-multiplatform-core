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

import androidx.pdf.EditsDraft
import androidx.pdf.annotation.AnnotationHandleIdGenerator
import androidx.pdf.annotation.AnnotationHandleIdGenerator.composeAnnotationId
import androidx.pdf.annotation.KeyedPdfAnnotation
import androidx.pdf.annotation.models.EditId
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData
import androidx.pdf.annotation.models.PdfEdits
import androidx.pdf.annotation.models.TestPdfAnnotation

class FakeAnnotationEditsDraftState : AnnotationEditsDraftState {
    private val drafts = mutableMapOf<Int, MutableMap<String, PdfAnnotation>>()

    override fun addDraftAnnotation(keyedAnnotation: KeyedPdfAnnotation): String {
        val handle = keyedAnnotation.key
        val annotation = keyedAnnotation.annotation
        drafts.getOrPut(annotation.pageNum) { mutableMapOf() }[handle] = annotation
        return handle
    }

    override fun addDraftAnnotation(annotation: PdfAnnotation): String {
        val annotationId =
            composeAnnotationId(annotation.pageNum, AnnotationHandleIdGenerator.generateId())
        drafts.getOrPut(annotation.pageNum) { mutableMapOf() }[annotationId] = annotation
        return annotationId
    }

    override fun getDraftAnnotation(pageNum: Int, handleId: String): PdfAnnotation? {
        return drafts[pageNum]?.get(handleId)
    }

    override fun getEdits(pageNum: Int): List<PdfAnnotationData> {
        return drafts[pageNum]?.map { (handle, annot) ->
            PdfAnnotationData(EditId(pageNum, handle), annot)
        } ?: emptyList()
    }

    override fun removeAnnotation(pageNum: Int, annotationId: String): PdfAnnotation {
        return drafts[pageNum]?.remove(annotationId)!!
    }

    override fun updateDraftAnnotation(
        pageNum: Int,
        annotationId: String,
        newAnnotation: PdfAnnotation,
    ): PdfAnnotation {
        val old = drafts[pageNum]?.get(annotationId) ?: throw NoSuchElementException()
        drafts[pageNum]?.put(annotationId, newAnnotation)
        return old
    }

    // Unused interface methods stubbed
    override fun getDraftAnnotations(pageNum: Int): List<KeyedPdfAnnotation> {
        return drafts[pageNum]?.map { (key, annotation) -> KeyedPdfAnnotation(key, annotation) }
            ?: emptyList()
    }

    override fun getModificationsSnapshot(): EditsDraft {
        TODO("Not yet implemented")
    }

    override fun addEditById(id: EditId, annotation: PdfAnnotation) {}

    override fun addEdit(annotation: PdfAnnotation): EditId = EditId(0, "")

    override fun removeEdit(editId: EditId): PdfAnnotation = TestPdfAnnotation(editId.pageNum)

    override fun updateEdit(editId: EditId, annotation: PdfAnnotation): PdfAnnotation =
        TestPdfAnnotation(editId.pageNum)

    override fun toPdfEdits(): PdfEdits = PdfEdits(emptyMap())

    override fun clear() {}
}
