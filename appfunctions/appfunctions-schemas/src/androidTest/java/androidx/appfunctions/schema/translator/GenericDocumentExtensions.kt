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

package androidx.appfunctions.schema.translator

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appsearch.app.EmbeddingVector
import androidx.appsearch.app.GenericDocument
import java.util.Arrays

// Sync this with GenericDocumentToPlatformConverter.java from AppSearch Jetpack.

@RequiresApi(31)
@Suppress("DEPRECATION", "UNCHECKED_CAST")
internal fun GenericDocument.toPlatformGenericDocument(): android.app.appsearch.GenericDocument {
    val platformBuilder =
        android.app.appsearch.GenericDocument.Builder<
            android.app.appsearch.GenericDocument.Builder<*>
        >(
            namespace,
            id,
            schemaType,
        )
    platformBuilder
        .setScore(score)
        .setTtlMillis(ttlMillis)
        .setCreationTimestampMillis(creationTimestampMillis)
    for (propertyName in propertyNames) {
        val property = getProperty(propertyName!!)
        if (property is Array<*> && property.isArrayOf<String>()) {
            platformBuilder.setPropertyString(propertyName, *property as Array<String?>)
        } else if (property is LongArray) {
            platformBuilder.setPropertyLong(propertyName, *property)
        } else if (property is DoubleArray) {
            platformBuilder.setPropertyDouble(propertyName, *property)
        } else if (property is BooleanArray) {
            platformBuilder.setPropertyBoolean(propertyName, *property)
        } else if (property is Array<*> && property.isArrayOf<ByteArray>()) {
            val byteValues = property
            // This is a patch for b/204677124, framework-appsearch in Android S and S_V2 will
            // crash if the user put a document with empty byte[][] or document[].
            if (
                (Build.VERSION.SDK_INT == Build.VERSION_CODES.S ||
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) && byteValues.size == 0
            ) {
                continue
            }
            platformBuilder.setPropertyBytes(propertyName, *byteValues as Array<out ByteArray>)
        } else if (property is Array<*> && property.isArrayOf<GenericDocument>()) {
            val documentValues = property
            // This is a patch for b/204677124, framework-appsearch in Android S and S_V2 will
            // crash if the user put a document with empty byte[][] or document[].
            if (
                (Build.VERSION.SDK_INT == Build.VERSION_CODES.S ||
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) && documentValues.size == 0
            ) {
                continue
            }
            val platformSubDocuments =
                arrayOfNulls<android.app.appsearch.GenericDocument>(documentValues.size)
            for (j in documentValues.indices) {
                platformSubDocuments[j] =
                    (documentValues[j] as GenericDocument).toPlatformGenericDocument()
            }
            platformBuilder.setPropertyDocument(propertyName, *platformSubDocuments)
        } else if (property is Array<*> && property.isArrayOf<EmbeddingVector>()) {
            throw UnsupportedOperationException(
                "Do not support converting GenericDocument with embedding"
            )
        } else {
            throw IllegalStateException(
                String.format(
                    "Property \"%s\" has unsupported value type %s",
                    propertyName,
                    property!!.javaClass.toString(),
                )
            )
        }
    }
    return platformBuilder.build()
}

@RequiresApi(31)
@Suppress("DEPRECATION", "UNCHECKED_CAST")
internal fun android.app.appsearch.GenericDocument.toJetpackGenericDocument(): GenericDocument {
    val jetpackBuilder =
        GenericDocument.Builder<GenericDocument.Builder<*>>(namespace, id, schemaType)
    jetpackBuilder
        .setScore(score)
        .setTtlMillis(ttlMillis)
        .setCreationTimestampMillis(creationTimestampMillis)
    for (propertyName in propertyNames) {
        val property = getProperty(propertyName)
        val PARENT_TYPES_SYNTHETIC_PROPERTY = "$${'$'}__AppSearch__parentTypes"
        if (propertyName == PARENT_TYPES_SYNTHETIC_PROPERTY) {
            check(property is Array<*> && property.isArrayOf<String>()) {
                String.format(
                    "Parents list must be of String[] type, but got %s",
                    property!!.javaClass.toString(),
                )
            }
            jetpackBuilder.setParentTypes(Arrays.asList(*property as Array<String?>))
            continue
        }
        if (property is Array<*> && property.isArrayOf<String>()) {
            jetpackBuilder.setPropertyString(propertyName, *property as Array<String?>)
        } else if (property is LongArray) {
            jetpackBuilder.setPropertyLong(propertyName, *property)
        } else if (property is DoubleArray) {
            jetpackBuilder.setPropertyDouble(propertyName, *property)
        } else if (property is BooleanArray) {
            jetpackBuilder.setPropertyBoolean(propertyName, *property)
        } else if (property is Array<*> && property.isArrayOf<ByteArray>()) {
            jetpackBuilder.setPropertyBytes(propertyName, *property as Array<ByteArray?>)
        } else if (
            property is Array<*> && property.isArrayOf<android.app.appsearch.GenericDocument>()
        ) {
            val documentValues = property
            val jetpackSubDocuments = arrayOfNulls<GenericDocument>(documentValues.size)
            for (j in documentValues.indices) {
                jetpackSubDocuments[j] =
                    (documentValues[j] as android.app.appsearch.GenericDocument)
                        .toJetpackGenericDocument()
            }
            jetpackBuilder.setPropertyDocument(propertyName, *jetpackSubDocuments)
        } else {
            throw IllegalStateException(
                String.format(
                    "Property \"%s\" has unsupported value type %s",
                    propertyName,
                    property!!.javaClass.toString(),
                )
            )
        }
    }
    return jetpackBuilder.build()
}
