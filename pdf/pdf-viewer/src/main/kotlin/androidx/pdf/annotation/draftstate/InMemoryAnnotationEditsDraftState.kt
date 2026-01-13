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

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.pdf.EditsDraft
import androidx.pdf.MutableEditsDraft
import androidx.pdf.annotation.AnnotationHandleIdGenerator
import androidx.pdf.annotation.AnnotationHandleIdGenerator.composeAnnotationId
import androidx.pdf.annotation.KeyedPdfAnnotation
import androidx.pdf.annotation.models.EditId
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData
import androidx.pdf.annotation.models.PdfEdits
import java.util.Collections
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A simple implementation of [AnnotationEditsDraftState] that stores annotation edits in memory.
 *
 * ## Thread-Safety
 *
 * This class is thread-safe. All public methods that modify the internal state are protected by a
 * [ReentrantLock] to ensure atomicity of compound operations. Read operations rely on the
 * thread-safe nature of the underlying synchronized collections.
 *
 * ## Time Complexity
 *
 * The time complexity of the operations is as follows:
 * - [getEdits]: O(k) where k is the number of annotations on the page (due to list creation).
 * - [addEdit]: O(1) on average.
 * - [removeEdit]: O(1) on average.
 * - [updateEdit]: O(1) on average.
 * - [toPdfEdits]: O(N) where N is the total number of edits across all pages.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class InMemoryAnnotationEditsDraftState() : AnnotationEditsDraftState {
    /**
     * The primary data store. Maps a page number to its own map of edits. The inner map uses
     * [EditId] as the key for efficient lookups and [PdfAnnotationData] as the value.
     *
     * A [LinkedHashMap] is used as the inner map to preserve the insertion order of annotations,
     * which is a critical requirement.
     */
    private val editsByPage: MutableMap<Int, MutableMap<EditId, PdfAnnotationData>> =
        Collections.synchronizedMap(HashMap())

    /**
     * The primary data store for all draft annotations. The inner map uses [String] as the key for
     * efficient lookups and [KeyedPdfAnnotation] as the value.
     *
     * A [LinkedHashMap] is used as the inner map to preserve the insertion order of annotations,
     * which is a critical requirement.
     */
    private val draftAnnotationsPerPage: MutableMap<Int, MutableMap<String, KeyedPdfAnnotation>> =
        Collections.synchronizedMap(HashMap())

    private val lock: ReentrantLock = ReentrantLock()

    override fun getDraftAnnotation(pageNum: Int, handleId: String): PdfAnnotation? {
        return draftAnnotationsPerPage[pageNum]?.get(handleId)?.annotation
    }

    override fun getDraftAnnotations(pageNum: Int): List<KeyedPdfAnnotation> {
        return draftAnnotationsPerPage[pageNum]?.values?.toList() ?: emptyList()
    }

    override fun getModificationsSnapshot(): EditsDraft {
        val mutableEditsDraft = MutableEditsDraft()
        draftAnnotationsPerPage.forEach { (_, pageAnnotationsMap) ->
            pageAnnotationsMap.forEach { (_, keyedAnnotation) ->
                mutableEditsDraft.insert(keyedAnnotation.annotation)
            }
        }
        return mutableEditsDraft.toEditsDraft()
    }

    override fun getEdits(pageNum: Int): List<PdfAnnotationData> =
        editsByPage[pageNum]?.values?.toList() ?: emptyList()

    override fun addDraftAnnotation(keyedAnnotation: KeyedPdfAnnotation): String {
        val pageNum = keyedAnnotation.annotation.pageNum
        val handleId = keyedAnnotation.key
        val keyedPdfAnnotation = KeyedPdfAnnotation(handleId, keyedAnnotation.annotation)

        lock.withLock {
            val draftPageAnnotations =
                draftAnnotationsPerPage.getOrPut(pageNum) {
                    // Using LinkedHashMap to maintain insertion order for each page's annotations.
                    Collections.synchronizedMap(LinkedHashMap())
                }
            draftPageAnnotations[handleId] = keyedPdfAnnotation
        }
        return handleId
    }

    override fun addDraftAnnotation(annotation: PdfAnnotation): String {
        val pageNum = annotation.pageNum
        val handleId = composeAnnotationId(pageNum, id = AnnotationHandleIdGenerator.generateId())
        val keyedPdfAnnotation = KeyedPdfAnnotation(handleId, annotation)

        lock.withLock {
            val draftPageAnnotations =
                draftAnnotationsPerPage.getOrPut(pageNum) {
                    // Using LinkedHashMap to maintain insertion order for each page's annotations.
                    Collections.synchronizedMap(LinkedHashMap())
                }
            draftPageAnnotations[handleId] = keyedPdfAnnotation
        }
        return handleId
    }

    override fun addEditById(id: EditId, annotation: PdfAnnotation) {
        val pageNum = annotation.pageNum
        val annotationData = PdfAnnotationData(id, annotation)

        lock.withLock {
            val pageEdits =
                editsByPage.getOrPut(pageNum) {
                    // Using LinkedHashMap to maintain insertion order for each page's annotations.
                    Collections.synchronizedMap(LinkedHashMap())
                }
            pageEdits[id] = annotationData
        }
    }

    override fun addEdit(annotation: PdfAnnotation): EditId {
        val pageNum = annotation.pageNum
        val editId = createEditIdForPage(pageNum)
        val annotationData = PdfAnnotationData(editId, annotation)

        lock.withLock {
            val pageEdits =
                editsByPage.getOrPut(pageNum) {
                    // Using LinkedHashMap to maintain insertion order for each page's annotations.
                    Collections.synchronizedMap(LinkedHashMap())
                }
            pageEdits[editId] = annotationData
        }

        return editId
    }

    override fun removeAnnotation(pageNum: Int, annotationId: String): PdfAnnotation {
        lock.withLock {
            val draftPageAnnotations =
                draftAnnotationsPerPage[pageNum]
                    ?: throw NoSuchElementException("No annotations present on page ${pageNum}.")

            val removedData =
                draftPageAnnotations.remove(annotationId)
                    ?: throw NoSuchElementException("Annotation with ID $annotationId not found.")

            if (draftPageAnnotations.isEmpty()) {
                draftAnnotationsPerPage.remove(pageNum)
            }
            return removedData.annotation
        }
    }

    override fun removeEdit(editId: EditId): PdfAnnotation {
        lock.withLock {
            val pageEdits =
                editsByPage[editId.pageNum]
                    ?: throw NoSuchElementException(
                        "No annotations present on page ${editId.pageNum}."
                    )

            val removedData =
                pageEdits.remove(editId)
                    ?: throw NoSuchElementException("Annotation with ID $editId not found.")

            if (pageEdits.isEmpty()) {
                editsByPage.remove(editId.pageNum)
            }
            return removedData.annotation
        }
    }

    override fun updateDraftAnnotation(
        pageNum: Int,
        annotationId: String,
        newAnnotation: PdfAnnotation,
    ): PdfAnnotation {
        lock.withLock {
            val draftPageAnnotations =
                draftAnnotationsPerPage[pageNum]
                    ?: throw NoSuchElementException("No annotations present on page ${pageNum}.")

            if (!draftPageAnnotations.containsKey(annotationId)) {
                throw NoSuchElementException("Annotation with ID $annotationId not found.")
            }

            val keyedAnnotation = KeyedPdfAnnotation(annotationId, newAnnotation)
            val previousData =
                requireNotNull(draftPageAnnotations.put(annotationId, keyedAnnotation))
            return previousData.annotation
        }
    }

    override fun updateEdit(editId: EditId, annotation: PdfAnnotation): PdfAnnotation {
        lock.withLock {
            val pageEdits =
                editsByPage[editId.pageNum]
                    ?: throw NoSuchElementException(
                        "No annotations present on page ${editId.pageNum}."
                    )

            if (!pageEdits.containsKey(editId)) {
                throw NoSuchElementException("Annotation with ID $editId not found.")
            }

            val annotationData = PdfAnnotationData(editId, annotation)
            val previousData = pageEdits.put(editId, annotationData)!!
            return previousData.annotation
        }
    }

    /**
     * Creates a snapshot of all current edits.
     *
     * @return A [PdfEdits] object containing a copy of all edits.
     */
    override fun toPdfEdits(): PdfEdits {
        lock.withLock {
            val snapshot =
                editsByPage.entries.associate { (pageNum, pageEdits) ->
                    pageNum to pageEdits.values.toList()
                }
            return PdfEdits(snapshot)
        }
    }

    /** Clears all annotation edits from the draft state. */
    override fun clear() {
        lock.withLock {
            editsByPage.clear()
            draftAnnotationsPerPage.clear()
        }
    }

    /**
     * Retrieves the mutable map of edits to savedAnnotation for a given edit ID.
     *
     * @param editId The ID of the edit to retrieve.
     * @return The [PdfAnnotation] for the given edit ID.
     * @throws NoSuchElementException if the edit with the given ID is not found.
     */
    @VisibleForTesting
    public fun getPdfAnnotationForId(editId: EditId): PdfAnnotation {
        return editsByPage[editId.pageNum]?.get(editId)?.annotation
            ?: throw NoSuchElementException("Annotation with ID $editId not found.")
    }

    /**
     * Generates a new unique [EditId] for a given page number.
     *
     * @param pageNum The page number for which to generate the new ID.
     * @return A new, unique [EditId].
     */
    private fun createEditIdForPage(pageNum: Int): EditId {
        return EditId(pageNum, UUID.randomUUID().toString())
    }
}
