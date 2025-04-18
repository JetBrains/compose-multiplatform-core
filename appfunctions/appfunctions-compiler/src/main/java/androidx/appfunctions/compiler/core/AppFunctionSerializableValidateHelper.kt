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

package androidx.appfunctions.compiler.core

import androidx.appfunctions.compiler.core.AppFunctionTypeReference.Companion.SUPPORTED_TYPES_STRING
import androidx.appfunctions.compiler.core.AppFunctionTypeReference.Companion.isSupportedType
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Visibility

/** A helper to validate an AppFunctionSerializable declaration. */
class AppFunctionSerializableValidateHelper(
    private val annotatedSerializable: AnnotatedAppFunctionSerializable
) {

    /** Validates if the primary constructor is valid. */
    fun validatePrimaryConstructor() {
        val primaryConstructor = annotatedSerializable.primaryConstructor
        if (primaryConstructor == null) {
            throw ProcessingException(
                "A valid AppFunctionSerializable must have a primary constructor.",
                annotatedSerializable.attributeNode
            )
        }
        val primaryConstructorDeclaration = checkNotNull(primaryConstructor)
        if (primaryConstructorDeclaration.parameters.isEmpty()) {
            throw ProcessingException(
                "A valid AppFunctionSerializable must have a non-empty primary constructor.",
                annotatedSerializable.attributeNode
            )
        }

        if (primaryConstructorDeclaration.getVisibility() != Visibility.PUBLIC) {
            throw ProcessingException(
                "A valid AppFunctionSerializable must have  a public primary constructor.",
                annotatedSerializable.attributeNode
            )
        }

        for (parameter in primaryConstructorDeclaration.parameters) {
            if (!parameter.isVal) {
                throw ProcessingException(
                    "All parameters in @AppFunctionSerializable primary constructor must have getters",
                    parameter
                )
            }
        }
    }

    /** Validate if the parameters are valid. */
    fun validateParameters() {
        val parametersToValidate =
            annotatedSerializable
                .getProperties()
                .associateBy { checkNotNull(it.name) }
                .toMutableMap()

        val superTypesWithSerializableAnnotation =
            annotatedSerializable.findSuperTypesWithSerializableAnnotation()
        val superTypesWithCapabilityAnnotation =
            annotatedSerializable.findSuperTypesWithCapabilityAnnotation()
        validateSuperTypes(superTypesWithSerializableAnnotation, superTypesWithCapabilityAnnotation)

        for (superType in superTypesWithSerializableAnnotation) {
            val superTypeAnnotatedSerializable =
                AnnotatedAppFunctionSerializable(superType).validate()
            for (superTypeProperty in superTypeAnnotatedSerializable.getProperties()) {
                // Parameter has now been visited
                val parameterInSuperType = parametersToValidate.remove(superTypeProperty.name)
                if (parameterInSuperType == null) {
                    throw ProcessingException(
                        "All parameters in @AppFunctionSerializable " +
                            "supertypes must be present in subtype",
                        superTypeProperty.type
                    )
                }
                validateSerializableParameter(parameterInSuperType)
            }
        }

        for (superType in superTypesWithCapabilityAnnotation) {
            val capabilityProperties = superType.getDeclaredProperties()

            for (superTypeProperty in capabilityProperties) {
                // Parameter has now been visited
                val parameterInSuperType =
                    parametersToValidate.remove(superTypeProperty.simpleName.asString())
                if (parameterInSuperType == null) {
                    throw ProcessingException(
                        "All Properties in @AppFunctionSchemaCapability " +
                            "supertypes must be present in subtype",
                        superTypeProperty
                    )
                }
                validateSerializableParameter(parameterInSuperType)
            }
        }

        // Validate the remaining parameters
        if (parametersToValidate.isNotEmpty()) {
            for ((_, parameterToValidate) in parametersToValidate) {
                validateSerializableParameter(parameterToValidate)
            }
        }
    }

    /**
     * Validates that the super types of the serializable [annotatedSerializable] are valid.
     *
     * A super type of a serializable class must be annotated with either
     * [androidx.appfunctions.AppFunctionSchemaCapability] or
     * [androidx.appfunctions.AppFunctionSerializable]. A class cannot be annotated with both
     * annotations.
     *
     * @param superTypesWithSerializableAnnotation a set of [KSClassDeclaration] for all super types
     *   of the [annotatedSerializable] that are annotated with
     *   [androidx.appfunctions.AppFunctionSerializable].
     * @param superTypesWithCapabilityAnnotation a set of [KSClassDeclaration] for all super types
     *   of the [annotatedSerializable] that are annotated with
     *   [androidx.appfunctions.AppFunctionSchemaCapability].
     */
    private fun validateSuperTypes(
        superTypesWithSerializableAnnotation: Set<KSClassDeclaration>,
        superTypesWithCapabilityAnnotation: Set<KSClassDeclaration>
    ) {
        val classesWithMultipleAnnotations =
            superTypesWithSerializableAnnotation.intersect(superTypesWithCapabilityAnnotation)
        if (classesWithMultipleAnnotations.isNotEmpty()) {
            throw ProcessingException(
                "A class cannot be annotated with both @AppFunctionSerializable and " +
                    "@AppFunctionSchemaCapability.",
                classesWithMultipleAnnotations.first() // Choose the first one as a sample
            )
        }
    }

    private fun validateSerializableParameter(propertyDeclaration: AppFunctionPropertyDeclaration) {
        if (propertyDeclaration.isGenericType) {
            // Don't validate a generic type. Whether a generic type is valid or not would be
            // validated when it is parameterized.
            return
        }
        if (!isSupportedType(propertyDeclaration.type)) {
            throw ProcessingException(
                "AppFunctionSerializable properties must be one of the following types:\n" +
                    SUPPORTED_TYPES_STRING +
                    ", an @AppFunctionSerializable or a list of @AppFunctionSerializable\nbut found " +
                    propertyDeclaration.type.toTypeName(),
                propertyDeclaration.type
            )
        }
    }
}
