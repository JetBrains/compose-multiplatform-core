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

import androidx.appfunctions.compiler.core.IntrospectionHelper.AppFunctionSchemaDefinitionAnnotation
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.CompileTimeAppFunctionMetadata
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile

/** Represents the annotated @AppFunctionSchemaDefinition. */
class AnnotatedAppFunctionSchemaDefinition(
    private val classDeclaration: KSClassDeclaration,
) {
    /** The qualified name of the @AppFunctionSchemaDefinition */
    val qualifiedName: String by lazy { classDeclaration.ensureQualifiedName() }

    /** Gets all annotated nodes. */
    fun getAllAnnotated(): List<KSAnnotated> {
        return listOf(classDeclaration)
    }

    /** Gets the source files of @AppFunctionSchemaDefinition */
    fun getSourceFiles(): Set<KSFile> {
        return buildSet {
            val containingFile = classDeclaration.containingFile
            if (containingFile != null) {
                add(containingFile)
            }
        }
    }

    // TODO(b/403525399): Reuse the logic from AnnotatedAppFunction to create metadata
    /** Creates [CompileTimeAppFunctionMetadata] from @AppFunctionSchemaDefinition. */
    fun createAppFunctionMetadata(): CompileTimeAppFunctionMetadata {
        val metadataCreatorHelper = AppFunctionMetadataCreatorHelper()
        val annotation =
            classDeclaration.annotations.findAnnotation(
                AppFunctionSchemaDefinitionAnnotation.CLASS_NAME
            )
                ?: throw ProcessingException(
                    "Class not annotated with @AppFunctionSchemaDefinition",
                    classDeclaration
                )
        val annotationProperties =
            metadataCreatorHelper.computeAppFunctionAnnotationProperties(
                schemaDefinitionAnnotation = annotation
            )

        return CompileTimeAppFunctionMetadata(
            id =
                "${annotationProperties.schemaCategory}/${annotationProperties.schemaName}/${annotationProperties.schemaVersion}",
            isEnabledByDefault = true,
            schema = annotationProperties.getAppFunctionSchemaMetadata(),
            parameters = emptyList(),
            response =
                AppFunctionResponseMetadata(AppFunctionReferenceTypeMetadata("placeholder", false)),
        )
    }
}
