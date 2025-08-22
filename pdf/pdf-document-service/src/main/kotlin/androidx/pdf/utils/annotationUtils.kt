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
import android.os.ParcelFileDescriptor
import androidx.annotation.RestrictTo
import androidx.pdf.annotation.models.PathPdfObject
import androidx.pdf.annotation.models.PathPdfObject.PathInput
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData
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
@RestrictTo(RestrictTo.Scope.LIBRARY)
public fun Path.getPathInputsFromPath(): List<PathInput> {
    val pathInputs = mutableListOf<PathInput>()
    val approx: FloatArray = this.approximate(ACCEPTABLE_TOLERANCE_IN_PATH)

    for (i in 0 until approx.size step 3) {
        pathInputs.add(PathInput(approx[i + 1], approx[i + 2]))
    }
    return pathInputs
}
