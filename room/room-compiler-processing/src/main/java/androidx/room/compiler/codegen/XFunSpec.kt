/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.room.compiler.codegen

import androidx.room.compiler.codegen.java.JavaFunSpec
import androidx.room.compiler.codegen.java.toJavaVisibilityModifier
import androidx.room.compiler.codegen.kotlin.KotlinFunSpec
import androidx.room.compiler.codegen.kotlin.toKotlinVisibilityModifier
import androidx.room.compiler.processing.FunSpecHelper
import androidx.room.compiler.processing.MethodSpecHelper
import androidx.room.compiler.processing.XMethodElement
import androidx.room.compiler.processing.XType
import com.squareup.kotlinpoet.KModifier

interface XFunSpec : TargetLanguage {

    val name: String

    interface Builder : TargetLanguage {

        fun addAnnotation(annotation: XAnnotationSpec): Builder

        fun addAbstractModifier(): Builder

        // TODO(b/247247442): Maybe make a XParameterSpec ?
        fun addParameter(
            typeName: XTypeName,
            name: String,
            annotations: List<XAnnotationSpec> = emptyList()
        ): Builder

        fun addCode(code: XCodeBlock): Builder

        fun addStatement(format: String, vararg args: Any?) =
            addCode(XCodeBlock.builder(language).addStatement(format, *args).build())

        fun callSuperConstructor(vararg args: XCodeBlock): Builder

        fun returns(typeName: XTypeName): Builder

        fun build(): XFunSpec
    }

    companion object {
        @JvmStatic
        fun builder(
            language: CodeLanguage,
            name: String,
            visibility: VisibilityModifier,
            isOpen: Boolean = false,
            isOverride: Boolean = false
        ) = builder(language, XName.of(name), visibility, isOpen, isOverride)

        @JvmStatic
        fun builder(
            language: CodeLanguage,
            name: XName,
            visibility: VisibilityModifier,
            isOpen: Boolean = false,
            isOverride: Boolean = false
        ): Builder {
            return when (language) {
                CodeLanguage.JAVA -> {
                    JavaFunSpec.Builder(
                        JFunSpec.methodBuilder(name.java).apply {
                            addModifiers(visibility.toJavaVisibilityModifier())
                            // TODO(b/247242374) Add nullability annotations for non-private params
                            // if (!isOpen) {
                            //    addModifiers(Modifier.FINAL)
                            // }
                            if (isOverride) {
                                addAnnotation(Override::class.java)
                            }
                        }
                    )
                }
                CodeLanguage.KOTLIN -> {
                    KotlinFunSpec.Builder(
                        KFunSpec.builder(name.kotlin).apply {
                            addModifiers(visibility.toKotlinVisibilityModifier())
                            if (isOpen) {
                                addModifiers(KModifier.OPEN)
                            }
                            if (isOverride) {
                                addModifiers(KModifier.OVERRIDE)
                            }
                        }
                    )
                }
            }
        }

        @JvmStatic
        fun constructorBuilder(language: CodeLanguage, visibility: VisibilityModifier): Builder {
            return when (language) {
                CodeLanguage.JAVA -> {
                    JavaFunSpec.Builder(
                        JFunSpec.constructorBuilder().apply {
                            addModifiers(visibility.toJavaVisibilityModifier())
                        }
                    )
                }
                CodeLanguage.KOTLIN -> {
                    KotlinFunSpec.Builder(
                        KFunSpec.constructorBuilder().apply {
                            // Workaround for the unreleased fix in
                            // https://github.com/square/kotlinpoet/pull/1342
                            if (visibility != VisibilityModifier.PUBLIC) {
                                addModifiers(visibility.toKotlinVisibilityModifier())
                            }
                        }
                    )
                }
            }
        }

        @JvmStatic
        fun overridingBuilder(
            language: CodeLanguage,
            element: XMethodElement,
            owner: XType
        ): Builder {
            return when (language) {
                CodeLanguage.JAVA ->
                    JavaFunSpec.Builder(MethodSpecHelper.overridingWithFinalParams(element, owner))
                CodeLanguage.KOTLIN ->
                    KotlinFunSpec.Builder(FunSpecHelper.overriding(element, owner))
            }
        }
    }
}
