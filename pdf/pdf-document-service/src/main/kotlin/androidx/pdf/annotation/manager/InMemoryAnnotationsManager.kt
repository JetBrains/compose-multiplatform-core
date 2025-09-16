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

import androidx.annotation.RestrictTo
import androidx.pdf.annotation.draftstate.AnnotationEditsDraftState
import androidx.pdf.annotation.draftstate.InMemoryAnnotationEditsDraftState
import androidx.pdf.annotation.models.EditId
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData
import androidx.pdf.annotation.models.PdfEdits
import java.util.Collections

/** Manages annotations for a PDF document, storing them in memory. */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class InMemoryAnnotationsManager(private val fetcher: PageAnnotationFetcher) :
    AnnotationsManager {
    private val annotationEditsDraftState: AnnotationEditsDraftState =
        InMemoryAnnotationEditsDraftState()

    // Tracks the existing annotations per page. If the request has been invoked and no annotations
    // have been found then the value will be empty.
    private val existingAnnotationsPerPage: MutableMap<Int, List<PdfAnnotation>> =
        Collections.synchronizedMap(HashMap())

    /**
     * Fetches annotations for the given page from the document, caches them, and adds them to the
     * draft state.
     *
     * @param pageNum The page number (0-indexed) to fetch annotations for.
     */
    private suspend fun fetchAndCacheAnnotationsForPage(pageNum: Int) {
        val existingPageAnnotations = fetcher.fetchAnnotations(pageNum)
        existingAnnotationsPerPage.put(pageNum, existingPageAnnotations)

        // Add the annotations to the draft state.
        existingPageAnnotations.forEach { annotationEditsDraftState.addEdit(it) }
    }

    /**
     * Retrieves all annotations for a given page number.
     *
     * This function first checks if the annotations for the specified page have already been
     * fetched from the document. If not, it fetches them, stores them locally, and adds them to the
     * [AnnotationEditsDraftState]. It then returns all annotations (both existing and newly added)
     * for that page from the draft state.
     *
     * @param pageNum The page number (0-indexed) to retrieve annotations for.
     * @return A list of [PdfAnnotationData] for the specified page.
     */
    override suspend fun getAnnotationsForPage(pageNum: Int): List<PdfAnnotationData> {
        if (existingAnnotationsPerPage[pageNum] == null) {
            fetchAndCacheAnnotationsForPage(pageNum)
        }
        return annotationEditsDraftState.getEdits(pageNum)
    }

    /**
     * Adds a new annotation to the draft state.
     *
     * @param annotation The [PdfAnnotation] to add.
     * @return The [EditId] assigned to the newly added annotation in the draft state.
     */
    override fun addAnnotation(annotation: PdfAnnotation): EditId =
        annotationEditsDraftState.addEdit(annotation)

    /**
     * Removes an annotation from the draft state.
     *
     * @param editId The [EditId] of the annotation to remove.
     * @return The removed [PdfAnnotation].
     * @throws NoSuchElementException if the annotation with the given [editId] is not found.
     */
    public fun removeAnnotation(editId: EditId): PdfAnnotation =
        annotationEditsDraftState.removeEdit(editId)

    /**
     * Updates an existing annotation in the draft state.
     *
     * @param editId The [EditId] of the annotation to update.
     * @param annotation The new [PdfAnnotation] data.
     * @return The updated [PdfAnnotation].
     * @throws NoSuchElementException if the annotation with the given [editId] is not found.
     */
    public fun updateAnnotation(editId: EditId, annotation: PdfAnnotation): PdfAnnotation =
        annotationEditsDraftState.updateEdit(editId, annotation)

    /**
     * Returns an immutable snapshot of the current annotation draft state including unedited
     * existing annotations as well.
     *
     * @return An [PdfEdits] representing the current draft.
     */
    override fun getSnapshot(): PdfEdits = annotationEditsDraftState.toPdfEdits()
}
