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

package androidx.pdf.annotation.manager

import androidx.pdf.annotation.AnnotationHandleIdGenerator.composeAnnotationId
import androidx.pdf.annotation.AnnotationHandleIdGenerator.decomposeAnnotationId
import androidx.pdf.annotation.KeyedPdfAnnotation
import androidx.pdf.annotation.draftstate.AnnotationEditsDraftState
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.operations.AnnotationOperationsTracker
import androidx.pdf.annotation.operations.KeyedAnnotationOperation
import androidx.pdf.annotation.registry.AnnotationHandleRegistry
import androidx.pdf.annotation.repository.AnnotationsRepository

/**
 * The internal orchestrator for managing PDF annotations, serving as the primary implementation of
 * [PdfAnnotationsManager].
 *
 * This class provides a unified view of annotations by combining persisted data from the underlying
 * PDF document with in-memory draft edits. It is responsible for reconciling these two sources,
 * tracking all user-initiated operations (add, remove, update), and ensuring data consistency.
 *
 * @param draftState Manages in-memory annotations that have not yet been saved.
 * @param annotationsRepository The single source of truth for annotations persisted in the PDF.
 * @param handleRegistry Maintains a mapping between persisted annotation IDs and stable,
 *   session-unique handle IDs.
 * @param operationsTracker Tracks pending operations (add, remove, update) on annotations, which
 *   are used to reconcile the final state.
 */
internal class PdfDocumentAnnotationsManager(
    private val draftState: AnnotationEditsDraftState,
    private val annotationsRepository: AnnotationsRepository,
    private val handleRegistry: AnnotationHandleRegistry,
    private val operationsTracker: AnnotationOperationsTracker,
) : PdfAnnotationsManager {
    override suspend fun getAnnotations(pageNum: Int): List<KeyedPdfAnnotation> {
        // TODO(b/462603193): Remove the map once draft state returns KeyedPdfAnnotation
        val draftAnnotations =
            draftState.getEdits(pageNum).map {
                KeyedPdfAnnotation(key = it.editId.toString(), annotation = it.annotation)
            }
        val persistedAnnotations = annotationsRepository.getAnnotationsForPage(pageNum)

        val reconciledAnnotations = reconcileAnnotations(persistedAnnotations)
        return reconciledAnnotations + draftAnnotations
    }

    override fun addAnnotation(annotation: PdfAnnotation): String {
        val draftId = draftState.addDraftAnnotation(annotation)
        operationsTracker.addEntry(
            operationType = KeyedAnnotationOperation.OperationType.ADD,
            key = draftId,
            annotation,
        )
        return composeAnnotationId(pageNum = annotation.pageNum, id = draftId)
    }

    override suspend fun removeAnnotation(annotationId: String): PdfAnnotation? {
        val (pageNum, handleId) = decomposeAnnotationId(annotationId)
        val sourceId = handleRegistry.getSourceId(handleId)

        val annotationToRemove: PdfAnnotation? =
            if (sourceId != null) {
                val updatedContent = operationsTracker.getUpdatedAnnotation(handleId)
                updatedContent ?: annotationsRepository.getAnnotation(pageNum, sourceId)?.annotation
            } else {
                draftState.removeAnnotation(pageNum, handleId)
            }

        if (annotationToRemove != null) {
            operationsTracker.addEntry(
                operationType = KeyedAnnotationOperation.OperationType.REMOVE,
                key = handleId,
                annotation = annotationToRemove,
            )
        }

        return annotationToRemove
    }

    override suspend fun updateAnnotation(
        annotationId: String,
        newAnnotation: PdfAnnotation,
    ): PdfAnnotation {
        val (pageNum, handleId) = decomposeAnnotationId(annotationId)

        val previousAnnotation: PdfAnnotation = run {
            val pendingUpdate = operationsTracker.getUpdatedAnnotation(handleId)
            if (pendingUpdate != null) return@run pendingUpdate

            val previousDraftAnnotation = draftState.getDraftAnnotation(pageNum, handleId)
            if (previousDraftAnnotation != null) {
                return@run previousDraftAnnotation
            }

            val sourceId =
                handleRegistry.getSourceId(handleId)
                    ?: throw NoSuchElementException(
                        "Cannot update: ID $handleId not found on page $pageNum"
                    )

            annotationsRepository.getAnnotation(pageNum, sourceId)?.annotation
                ?: throw NoSuchElementException("Annotation $sourceId not found in repository")
        }

        val persistedAnnotationId = handleRegistry.getSourceId(handleId)
        if (persistedAnnotationId == null) {
            draftState.updateDraftAnnotation(pageNum, handleId, newAnnotation)
        }
        operationsTracker.addEntry(
            operationType = KeyedAnnotationOperation.OperationType.UPDATE,
            key = handleId,
            annotation = newAnnotation,
        )
        return previousAnnotation
    }

    private fun reconcileAnnotations(
        keyedAnnotations: List<KeyedPdfAnnotation>
    ): List<KeyedPdfAnnotation> {
        return keyedAnnotations.mapNotNull { annotation ->
            // Persisted annotations need to have a proxy id so that the caller can have a
            // unified id.
            val handleId = handleRegistry.getHandleId(annotation.key)

            if (operationsTracker.isDeleted(handleId)) {
                return@mapNotNull null
            }
            val updatedContent = operationsTracker.getUpdatedAnnotation(handleId)
            if (updatedContent != null) {
                return@mapNotNull KeyedPdfAnnotation(handleId, updatedContent)
            }

            KeyedPdfAnnotation(key = handleId, annotation = annotation.annotation)
        }
    }
}
