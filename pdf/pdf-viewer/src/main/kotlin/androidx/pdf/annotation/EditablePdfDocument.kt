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

package androidx.pdf.annotation

import android.os.ParcelFileDescriptor
import androidx.annotation.RestrictTo
import androidx.pdf.PdfDocument
import androidx.pdf.annotation.models.AnnotationResult
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData

/** Represents a PDF document that allows for editing of annotations. */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public abstract class EditablePdfDocument : PdfDocument {

    /**
     * Retrieves a list of all annotations present on the specified page.
     *
     * @param pageNum The page number (0-indexed) from which to retrieve annotations.
     * @return A list of [PdfAnnotation] objects representing the annotations on the page. Returns
     *   an empty list if there are no annotation on the page.
     * @throws IllegalArgumentException if the page number is invalid.
     */
    public abstract suspend fun getAnnotationsForPage(pageNum: Int): List<PdfAnnotation>

    /**
     * Applies a list of annotation edits to the document.
     *
     * @param annotations A list of [PdfAnnotationData] representing the edits to be applied.
     * @return An [AnnotationResult] indicating the success or failure of the operation.
     */
    public abstract suspend fun applyEdits(annotations: List<PdfAnnotationData>): AnnotationResult

    /**
     * Applies a list of annotation edits from a source [ParcelFileDescriptor] to the document.
     *
     * @param sourcePfd The [ParcelFileDescriptor] containing the annotation edits.
     * @return An [AnnotationResult] indicating the success or failure of the operation.
     */
    public abstract suspend fun applyEdits(sourcePfd: ParcelFileDescriptor): AnnotationResult
}
