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

package androidx.appfunctions.compiler.processors

import androidx.appfunctions.compiler.core.AppFunctionComponentRegistryGenerator
import androidx.appfunctions.compiler.core.AppFunctionComponentRegistryGenerator.AppFunctionComponent
import androidx.appfunctions.compiler.core.AppFunctionSymbolResolver
import androidx.appfunctions.compiler.core.IntrospectionHelper.AppFunctionComponentRegistryAnnotation
import androidx.appfunctions.compiler.core.ensureQualifiedName
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated

/**
 * Generates the registry of all @AppFunction symbols for later aggregation processing.
 *
 * For example, if there are two functions in the module "myLibrary":
 * ```
 * package com.android.example
 *
 * class NoteFunction: CreateNote {
 *   @AppFunction
 *   override suspend fun createNote(): Note { ... }
 * }
 *
 * class TaskFunction: CreateTask {
 *   @AppFunction
 *   override suspend fun createTask(): Task { ... }
 * }
 * ```
 *
 * A single registry would be generated:
 * ```
 * package appfunctions_aggregated_deps
 *
 * @AppFunctionComponentRegistry(
 *   componentCategory = "FUNCTION",
 *   componentNames = [
 *     "com.android.example.NoteFunction.createNote",
 *     "com.android.example.TaskFunction.createTask",
 *   ]
 * )
 * @Generated
 * public class `$Mylibrary_FunctionComponentRegistry`
 * ```
 *
 * **Important:** [androidx.appfunctions.compiler.processors.AppFunctionFunctionRegistryProcessor]
 * will process exactly once for each compilation unit to generate a single registry for looking up
 * all AppFunctions within the compilation unit.
 */
class AppFunctionFunctionRegistryProcessor(private val codeGenerator: CodeGenerator) :
    SymbolProcessor {

    private var hasProcessed = false

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (hasProcessed) return emptyList()
        hasProcessed = true

        val annotatedAppFunctions =
            AppFunctionSymbolResolver(resolver).resolveAnnotatedAppFunctions()
        val functionComponents =
            annotatedAppFunctions.flatMap { annotatedAppFunction ->
                buildList {
                    for (function in annotatedAppFunction.appFunctionDeclarations) {
                        add(
                            AppFunctionComponent(
                                qualifiedName = function.ensureQualifiedName(),
                                sourceFiles = annotatedAppFunction.getSourceFiles(),
                            )
                        )
                    }
                }
            }

        AppFunctionComponentRegistryGenerator(codeGenerator)
            .generateRegistry(
                resolver.getModuleName().asString(),
                AppFunctionComponentRegistryAnnotation.Category.FUNCTION,
                functionComponents
            )
        return emptyList()
    }
}
