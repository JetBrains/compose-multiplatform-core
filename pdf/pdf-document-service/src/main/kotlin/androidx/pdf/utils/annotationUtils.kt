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

package androidx.pdf.utils

import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.component.PdfAnnotation as AospPdfAnnotation
import android.graphics.pdf.component.PdfPageObject
import android.graphics.pdf.component.PdfPagePathObject
import android.graphics.pdf.component.StampAnnotation as AospStampAnnotation
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresExtension
import androidx.annotation.RestrictTo
import androidx.pdf.annotation.models.PathPdfObject
import androidx.pdf.annotation.models.PathPdfObject.PathInput
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData
import androidx.pdf.annotation.models.PdfObject
import androidx.pdf.annotation.models.StampAnnotation
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.reflect.TypeToken
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException

/** Tolerance level for path approximation. */
private const val ACCEPTABLE_TOLERANCE_IN_PATH = 0.5f

/**
 * Writes a list of [PdfAnnotationData] objects to a [ParcelFileDescriptor].
 *
 * @param pfd The [ParcelFileDescriptor] to write to.
 * @param annotations The list of [PdfAnnotationData] objects to write.
 * @throws IOException If there is an error writing to the file.
 */
internal fun writeAnnotationsToFile(
    pfd: ParcelFileDescriptor,
    annotations: List<PdfAnnotationData>,
) {
    val fileDescriptor: FileDescriptor = pfd.fileDescriptor
    // It is the responsibility of the caller to close this pfd.
    FileOutputStream(fileDescriptor).use { outputStream ->
        val gson = Gson()

        // Serialize the list of annotationsData to a json string
        val jsonString = gson.toJson(annotations)
        outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    // To reuse the same PFD for reading after writing, we need to reset its file pointer
    pfd.resetToStartingPosition()
}

/**
 * Reads a list of [PdfAnnotationData] objects from a [ParcelFileDescriptor].
 *
 * @param pfd The [ParcelFileDescriptor] to read from.
 * @return A list of [PdfAnnotationData] objects read from the file.
 */
internal fun readAnnotationsFromPfd(pfd: ParcelFileDescriptor): List<PdfAnnotationData> {
    // TODO: b/434864732 - Use stream to read annotations from file
    val jsonString = readFromPfd(pfd)
    val type = object : TypeToken<List<PdfAnnotationData>>() {}.type
    val gson =
        GsonBuilder()
            .registerTypeAdapter(PdfAnnotation::class.java, getStampAnnotationDeserializer())
            .create()
    val annotations = gson.fromJson<List<PdfAnnotationData>>(jsonString, type)
    return annotations ?: listOf()
}

/** Returns a [JsonDeserializer] for [StampAnnotation]. */
internal fun getStampAnnotationDeserializer(): JsonDeserializer<StampAnnotation> {
    return JsonDeserializer { json, _, context ->
        val jsonObject = json.asJsonObject

        val pageNum = jsonObject.get("pageNum").asInt
        val bounds = context.deserialize<RectF>(jsonObject.get("bounds"), RectF::class.java)

        val pdfObjectsArray = jsonObject.getAsJsonArray("pdfObjects")
        val pathPdfObjects = mutableListOf<PathPdfObject>()
        for (pdfObjectElement in pdfObjectsArray) {
            pathPdfObjects.add(context.deserialize(pdfObjectElement, PathPdfObject::class.java))
        }
        StampAnnotation(pageNum, bounds, pathPdfObjects)
    }
}

/**
 * Converts this [PdfObject] to its corresponding AOSP [PdfPageObject] representation.
 *
 * @return The AOSP [PdfPageObject] equivalent of this [PdfObject].
 */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public fun PdfObject.toAospPdfPageObject(): PdfPageObject {
    return when (this) {
        is PathPdfObject -> {
            val path = this.inputs.getPathFromPathInputs()
            val pagePathObject =
                PdfPagePathObject(path).apply {
                    strokeWidth = brushWidth
                    strokeColor = brushColor
                    renderMode = PdfPagePathObject.RENDER_MODE_STROKE
                }
            pagePathObject
        }
    }
}

/**
 * Converts this [PdfAnnotation] to its corresponding AOSP [AospPdfAnnotation] representation.
 *
 * @return The AOSP [AospPdfAnnotation] equivalent of this [PdfAnnotation].
 */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public fun PdfAnnotation.toAospAnnotation(): AospPdfAnnotation {
    return when (this) {
        is StampAnnotation -> this.toAospStampAnnotation()
        // TODO: Add other types of annotations
        else -> throw UnsupportedOperationException("Unsupported annotation type: $this")
    }
}

/**
 * Converts this [StampAnnotation] to its corresponding AOSP [AospStampAnnotation] representation.
 *
 * @return The AOSP [AospStampAnnotation] equivalent of this [StampAnnotation].
 */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public fun StampAnnotation.toAospStampAnnotation(): AospStampAnnotation {
    val aospStampAnnotation = AospStampAnnotation(bounds)
    for (pdfObject in pdfObjects) {
        aospStampAnnotation.addObject(pdfObject.toAospPdfPageObject())
    }
    return aospStampAnnotation
}

/**
 * Creates a [Path] object from a list of [PathInput] points.
 *
 * @return A [Path] object constructed from the input points. Returns an empty Path if the input
 *   list is empty.
 */
internal fun List<PathInput>.getPathFromPathInputs(): Path {
    val pathInputs = this
    if (pathInputs.isEmpty()) return Path()

    val path = Path()
    path.moveTo(pathInputs[0].x, pathInputs[0].y)
    for (i in 1 until pathInputs.size) {
        path.lineTo(pathInputs[i].x, pathInputs[i].y)
    }
    return path
}

/**
 * Creates a a list of [PathInput] points from [Path] object.
 *
 * @return A list of [PathInput] constructed from the path object. Returns an empty list if the path
 *   is empty.
 */
internal fun Path.getPathInputsFromPath(): List<PathInput> {
    val pathInputs = mutableListOf<PathInput>()
    val approx: FloatArray = this.approximate(ACCEPTABLE_TOLERANCE_IN_PATH)

    for (i in 0 until approx.size step 3) {
        pathInputs.add(PathInput(approx[i + 1], approx[i + 2]))
    }
    return pathInputs
}

/**
 * Converts this [AospPdfAnnotation] to its corresponding AOSP [PdfAnnotation] representation.
 *
 * @return The [PdfAnnotation] equivalent of this AOSP [AospPdfAnnotation].
 */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public fun AospPdfAnnotation.toPdfAnnotation(pageNum: Int): PdfAnnotation? {
    return when (this) {
        is AospStampAnnotation -> this.toStampAnnotation(pageNum = pageNum)
        // TODO: Add other types of annotations
        else -> null
    }
}

/**
 * Converts this AOSP [AospStampAnnotation] to its corresponding [StampAnnotation] representation.
 *
 * @return The [StampAnnotation] equivalent of this AOSP [AospStampAnnotation].
 */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public fun AospStampAnnotation.toStampAnnotation(pageNum: Int): StampAnnotation {
    val pdfObjects = mutableListOf<PdfObject>()
    for (aospPdfObject in objects) {
        aospPdfObject.toPdfObject()?.let { pdfObject -> pdfObjects.add(pdfObject) }
    }
    return StampAnnotation(pageNum, bounds, pdfObjects)
}

/**
 * Converts this AOSP [PdfPageObject] to its corresponding [PdfObject] representation.
 *
 * @return The [PdfObject] equivalent of this AOSP [PdfPageObject] .
 */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public fun PdfPageObject.toPdfObject(): PdfObject? {
    return when (this) {
        is PdfPagePathObject -> {
            val pathInputs = this.toPath().getPathInputsFromPath()
            PathPdfObject(strokeColor, strokeWidth, pathInputs)
        }
        else -> null
    }
}
