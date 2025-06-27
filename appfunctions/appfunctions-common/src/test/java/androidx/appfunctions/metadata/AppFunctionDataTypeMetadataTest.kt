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

package androidx.appfunctions.metadata

import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata.Companion.TYPE_INT
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata.Companion.TYPE_LONG
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata.Companion.TYPE_STRING
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppFunctionDataTypeMetadataTest {

    @Test
    fun appFunctionArrayTypeMetadata_equalsAndHashCode() {
        val properties2 = mapOf("prop2" to AppFunctionPrimitiveTypeMetadata(TYPE_STRING, true))

        val arrayType1a =
            AppFunctionArrayTypeMetadata(AppFunctionPrimitiveTypeMetadata(TYPE_INT, true), false)
        val arrayType1b =
            AppFunctionArrayTypeMetadata(AppFunctionPrimitiveTypeMetadata(TYPE_INT, true), false)
        val arrayType2 =
            AppFunctionArrayTypeMetadata(
                AppFunctionObjectTypeMetadata(properties2, emptyList(), "qualifiedName", false),
                false
            )

        assertThat(arrayType1a).isEqualTo(arrayType1b)
        assertThat(arrayType1a.hashCode()).isEqualTo(arrayType1b.hashCode())

        assertThat(arrayType1a).isNotEqualTo(arrayType2)
        assertThat(arrayType1a.hashCode()).isNotEqualTo(arrayType2.hashCode())
    }

    @Test
    fun appFunctionArrayTypeMetadata_toAppFunctionDataTypeMetadataDocument_returnsCorrectDocument() {
        val arrayType =
            AppFunctionArrayTypeMetadata(AppFunctionPrimitiveTypeMetadata(TYPE_INT, true), false)

        val document = arrayType.toAppFunctionDataTypeMetadataDocument()

        assertThat(document)
            .isEqualTo(
                AppFunctionDataTypeMetadataDocument(
                    type = AppFunctionArrayTypeMetadata.TYPE,
                    itemType =
                        AppFunctionDataTypeMetadataDocument(
                            type = TYPE_INT,
                            isNullable = true,
                        ),
                    isNullable = false,
                )
            )
    }

    @Test
    fun appFunctionAllOfTypeMetadata_equalsAndHashCode() {
        val properties1 = mapOf("prop1" to AppFunctionPrimitiveTypeMetadata(TYPE_INT, false))
        val objectType =
            AppFunctionObjectTypeMetadata(properties1, listOf("prop1"), "qualifiedName", false)
        val referenceType = AppFunctionReferenceTypeMetadata("type1", false)
        val allOfDataType1 =
            AppFunctionAllOfTypeMetadata(
                matchAll = listOf(objectType),
                isNullable = true,
                qualifiedName = null
            )
        val allOfDataType2 =
            AppFunctionAllOfTypeMetadata(
                matchAll = listOf(objectType, referenceType),
                isNullable = true,
                qualifiedName = "allOf1"
            )
        val allOfDataType2a =
            AppFunctionAllOfTypeMetadata(
                matchAll = listOf(objectType, referenceType),
                isNullable = true,
                qualifiedName = "allOf1"
            )

        assertThat(allOfDataType1).isNotEqualTo(allOfDataType2)
        assertThat(allOfDataType1.hashCode()).isNotEqualTo(allOfDataType2.hashCode())

        assertThat(allOfDataType2).isEqualTo(allOfDataType2a)
        assertThat(allOfDataType2.hashCode()).isEqualTo(allOfDataType2a.hashCode())
    }

    @Test
    fun appFunctionAllOfTypeMetadata_toAppFunctionDatatypeMetadataDocument_returnsCorrectDocument() {
        val properties1 = mapOf("prop1" to AppFunctionPrimitiveTypeMetadata(TYPE_INT, false))
        val objectType =
            AppFunctionObjectTypeMetadata(properties1, listOf("prop1"), "qualifiedName", false)
        val referenceType = AppFunctionReferenceTypeMetadata("type1", false)
        val allOfDataType =
            AppFunctionAllOfTypeMetadata(
                matchAll = listOf(referenceType, objectType),
                isNullable = true,
                qualifiedName = "allOf1"
            )

        val document = allOfDataType.toAppFunctionDataTypeMetadataDocument()

        assertThat(document.allOf)
            .containsExactly(
                referenceType.toAppFunctionDataTypeMetadataDocument(),
                objectType.toAppFunctionDataTypeMetadataDocument()
            )
        assertThat(document.type).isEqualTo(AppFunctionAllOfTypeMetadata.TYPE)
        assertThat(document.isNullable).isTrue()
        assertThat(document.dataTypeReference).isNull()
        assertThat(document.itemType).isNull()
        assertThat(document.properties).isEmpty()
        assertThat(document.objectQualifiedName).isNotEmpty()
        assertThat(document.objectQualifiedName).isEqualTo("allOf1")
    }

    @Test
    fun appFunctionObjectTypeMetadata_equalsAndHashCode() {
        val properties1 = mapOf("prop1" to AppFunctionPrimitiveTypeMetadata(TYPE_INT, false))
        val properties2 = mapOf("prop2" to AppFunctionPrimitiveTypeMetadata(TYPE_STRING, true))

        val objectType1a =
            AppFunctionObjectTypeMetadata(properties1, listOf("prop1"), "qualifiedName", false)
        val objectType1b =
            AppFunctionObjectTypeMetadata(properties1, listOf("prop1"), "qualifiedName", false)
        val objectType2 =
            AppFunctionObjectTypeMetadata(properties2, listOf("prop2"), "qualifiedName", true)

        assertThat(objectType1a).isEqualTo(objectType1b)
        assertThat(objectType1a.hashCode()).isEqualTo(objectType1b.hashCode())

        assertThat(objectType1a).isNotEqualTo(objectType2)
        assertThat(objectType1a.hashCode()).isNotEqualTo(objectType2.hashCode())
    }

    @Test
    fun appFunctionObjectTypeMetadata_toAppFunctionDataTypeMetadataDocument_returnsCorrectDocument() {
        val primitiveTypeInt = AppFunctionPrimitiveTypeMetadata(TYPE_INT, true)
        val primitiveTypeLong = AppFunctionPrimitiveTypeMetadata(TYPE_LONG, false)
        val properties =
            mapOf(
                "prop1" to primitiveTypeInt,
                "prop2" to primitiveTypeLong,
            )
        val isNullable = false
        val qualifiedName = "qualifiedName"
        val requiredProperties = listOf("prop1", "prop2")
        val appFunctionObjectTypeMetadata =
            AppFunctionObjectTypeMetadata(properties, requiredProperties, qualifiedName, isNullable)

        val convertedDocument =
            appFunctionObjectTypeMetadata.toAppFunctionDataTypeMetadataDocument()

        val expectedPrimitiveDocumentProperties1 =
            AppFunctionNamedDataTypeMetadataDocument(
                name = "prop1",
                dataTypeMetadata =
                    AppFunctionDataTypeMetadataDocument(type = TYPE_INT, isNullable = true)
            )
        val expectedPrimitiveDocumentProperties2 =
            AppFunctionNamedDataTypeMetadataDocument(
                name = "prop2",
                dataTypeMetadata =
                    AppFunctionDataTypeMetadataDocument(type = TYPE_LONG, isNullable = false)
            )
        val expectedAppFunctionDataTypeMetadataDocument =
            AppFunctionDataTypeMetadataDocument(
                type = AppFunctionObjectTypeMetadata.TYPE,
                properties =
                    listOf(
                        expectedPrimitiveDocumentProperties1,
                        expectedPrimitiveDocumentProperties2,
                    ),
                required = requiredProperties,
                objectQualifiedName = "qualifiedName",
                isNullable = false,
            )
        assertThat(convertedDocument).isEqualTo(expectedAppFunctionDataTypeMetadataDocument)
    }

    @Test
    fun appFunctionReferenceTypeMetadata_equalsAndHashCode() {
        val ref1a = AppFunctionReferenceTypeMetadata("type1", false)
        val ref1b = AppFunctionReferenceTypeMetadata("type1", false)
        val ref2 = AppFunctionReferenceTypeMetadata("type2", true)

        assertThat(ref1a).isEqualTo(ref1b)
        assertThat(ref1a.hashCode()).isEqualTo(ref1b.hashCode())

        assertThat(ref1a).isNotEqualTo(ref2)
        assertThat(ref1a.hashCode()).isNotEqualTo(ref2.hashCode())
    }

    @Test
    fun appFunctionReferenceTypeMetadata_toAppFunctionDataTypeMetadataDocument_returnsCorrectDocument() {
        val referenceType =
            AppFunctionReferenceTypeMetadata(
                referenceDataType = "#components/dataTypes/Test",
                isNullable = true,
            )

        val document = referenceType.toAppFunctionDataTypeMetadataDocument()

        assertThat(document)
            .isEqualTo(
                AppFunctionDataTypeMetadataDocument(
                    type = AppFunctionReferenceTypeMetadata.TYPE,
                    dataTypeReference = "#components/dataTypes/Test",
                    isNullable = true,
                )
            )
    }

    @Test
    fun appFunctionPrimitiveTypeMetadata_equalsAndHashCode() {
        val primitive1a = AppFunctionPrimitiveTypeMetadata(TYPE_INT, false)
        val primitive1b = AppFunctionPrimitiveTypeMetadata(TYPE_INT, false)
        val primitive2 = AppFunctionPrimitiveTypeMetadata(TYPE_STRING, false)

        assertThat(primitive1a).isEqualTo(primitive1b)
        assertThat(primitive1a.hashCode()).isEqualTo(primitive1b.hashCode())

        assertThat(primitive1a).isNotEqualTo(primitive2)
        assertThat(primitive1a.hashCode()).isNotEqualTo(primitive2.hashCode())
    }

    @Test
    fun appFunctionPrimitiveTypeMetadata_toAppFunctionDataTypeMetadataDocument_returnsCorrectDocument() {
        val primitiveTypeInt = AppFunctionPrimitiveTypeMetadata(TYPE_INT, true)
        val primitiveTypeLong = AppFunctionPrimitiveTypeMetadata(TYPE_LONG, false)

        assertThat(primitiveTypeInt.toAppFunctionDataTypeMetadataDocument())
            .isEqualTo(AppFunctionDataTypeMetadataDocument(type = TYPE_INT, isNullable = true))
        assertThat(primitiveTypeLong.toAppFunctionDataTypeMetadataDocument())
            .isEqualTo(AppFunctionDataTypeMetadataDocument(type = TYPE_LONG, isNullable = false))
    }

    @Test
    fun appFunctionDataTypeMetadataDocument_toAppFunctionPrimitiveTypeMetadata_returnsCorrectMetadata() {
        // Test all primitive types. Only Parameterized TestRunner is allowed in AndroidX tests
        // which injects at class level and all tests will run for each combination, hence manually
        // iterating over the values.
        AppFunctionDataTypeMetadata.PRIMITIVE_TYPES.forEach { type ->
            val document = AppFunctionDataTypeMetadataDocument(type = type, isNullable = false)

            val metadata = document.toAppFunctionDataTypeMetadata()

            assertThat(metadata)
                .isEqualTo(AppFunctionPrimitiveTypeMetadata(type = type, isNullable = false))
        }
    }

    @Test
    fun appFunctionDataTypeMetadataDocument_toAppFunctionArrayTypeMetadata_returnsCorrectMetadata() {
        val document =
            AppFunctionDataTypeMetadataDocument(
                type = AppFunctionDataTypeMetadata.TYPE_ARRAY,
                itemType =
                    AppFunctionDataTypeMetadataDocument(
                        type = AppFunctionDataTypeMetadata.TYPE_STRING,
                        isNullable = false
                    ),
                isNullable = true
            )

        val metadata = document.toAppFunctionDataTypeMetadata()

        assertThat(metadata)
            .isEqualTo(
                AppFunctionArrayTypeMetadata(
                    itemType =
                        AppFunctionPrimitiveTypeMetadata(
                            type = AppFunctionDataTypeMetadata.TYPE_STRING,
                            isNullable = false
                        ),
                    isNullable = true
                )
            )
    }

    @Test
    fun appFunctionDataTypeMetadataDocument_toAppFunctionObjectTypeMetadata_returnsCorrectMetadata() {
        val document =
            AppFunctionDataTypeMetadataDocument(
                type = AppFunctionDataTypeMetadata.TYPE_OBJECT,
                properties =
                    listOf(
                        AppFunctionNamedDataTypeMetadataDocument(
                            name = "property1",
                            dataTypeMetadata =
                                AppFunctionDataTypeMetadataDocument(
                                    type = AppFunctionDataTypeMetadata.TYPE_INT,
                                    isNullable = false
                                )
                        )
                    ),
                required = listOf("property1"),
                objectQualifiedName = "ObjectType",
                isNullable = false
            )

        val metadata = document.toAppFunctionDataTypeMetadata()

        assertThat(metadata)
            .isEqualTo(
                AppFunctionObjectTypeMetadata(
                    properties =
                        mapOf(
                            "property1" to
                                AppFunctionPrimitiveTypeMetadata(
                                    type = AppFunctionDataTypeMetadata.TYPE_INT,
                                    isNullable = false
                                )
                        ),
                    required = listOf("property1"),
                    qualifiedName = "ObjectType",
                    isNullable = false
                )
            )
    }

    @Test
    fun appFunctionDataTypeMetadataDocument_toAppFunctionReferenceTypeMetadata_returnsCorrectMetadata() {
        val document =
            AppFunctionDataTypeMetadataDocument(
                type = AppFunctionDataTypeMetadata.TYPE_REFERENCE,
                dataTypeReference = "someReference",
                isNullable = true
            )

        val metadata = document.toAppFunctionDataTypeMetadata()

        assertThat(metadata)
            .isEqualTo(
                AppFunctionReferenceTypeMetadata(
                    referenceDataType = "someReference",
                    isNullable = true
                )
            )
    }

    @Test
    fun appFunctionDataTypeMetadataDocument_toAppFunctionAllOfTypeMetadata_returnsCorrectMetadata() {
        val document =
            AppFunctionDataTypeMetadataDocument(
                type = AppFunctionDataTypeMetadata.TYPE_ALL_OF,
                allOf =
                    listOf(
                        AppFunctionDataTypeMetadataDocument(
                            type = AppFunctionDataTypeMetadata.TYPE_INT,
                            isNullable = false
                        )
                    ),
                objectQualifiedName = "AllOfType",
                isNullable = false
            )

        val metadata = document.toAppFunctionDataTypeMetadata()

        assertThat(metadata)
            .isEqualTo(
                AppFunctionAllOfTypeMetadata(
                    matchAll =
                        listOf(
                            AppFunctionPrimitiveTypeMetadata(
                                type = AppFunctionDataTypeMetadata.TYPE_INT,
                                isNullable = false
                            )
                        ),
                    qualifiedName = "AllOfType",
                    isNullable = false
                )
            )
    }
}
