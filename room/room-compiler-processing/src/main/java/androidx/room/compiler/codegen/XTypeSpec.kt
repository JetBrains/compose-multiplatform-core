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

import androidx.room.compiler.codegen.java.JavaCodeBlock
import androidx.room.compiler.codegen.java.JavaTypeSpec
import androidx.room.compiler.codegen.kotlin.KotlinCodeBlock
import androidx.room.compiler.codegen.kotlin.KotlinTypeSpec
import androidx.room.compiler.processing.XElement
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.javapoet.JTypeSpec
import com.squareup.kotlinpoet.javapoet.KTypeSpec

interface XTypeSpec : TargetLanguage {

    val name: String?

    interface Builder : TargetLanguage {
        fun superclass(typeName: XTypeName): Builder

        fun addSuperinterface(typeName: XTypeName): Builder

        fun addAnnotation(annotation: XAnnotationSpec): Builder

        fun addProperty(propertySpec: XPropertySpec): Builder

        fun addFunction(functionSpec: XFunSpec): Builder

        fun addType(typeSpec: XTypeSpec): Builder

        fun setPrimaryConstructor(functionSpec: XFunSpec): Builder

        fun setVisibility(visibility: VisibilityModifier): Builder

        fun addAbstractModifier(): Builder

        fun addOriginatingElement(element: XElement): Builder

        fun build(): XTypeSpec

        fun addProperty(
            name: String,
            typeName: XTypeName,
            visibility: VisibilityModifier,
            isMutable: Boolean = false,
            initExpr: XCodeBlock? = null,
        ) = apply {
            val builder = XPropertySpec.builder(language, name, typeName, visibility, isMutable)
            if (initExpr != null) {
                builder.initializer(initExpr)
            }
            addProperty(builder.build())
        }
    }

    companion object {
        @JvmStatic
        fun classBuilder(language: CodeLanguage, name: String, isOpen: Boolean = false) =
            classBuilder(language, XName.of(name), isOpen)

        @JvmStatic
        fun classBuilder(language: CodeLanguage, className: XClassName, isOpen: Boolean = false) =
            classBuilder(
                language,
                XName.of(className.java.simpleName(), className.kotlin.simpleName),
                isOpen
            )

        @JvmStatic
        fun classBuilder(language: CodeLanguage, name: XName, isOpen: Boolean = false): Builder {
            return when (language) {
                CodeLanguage.JAVA ->
                    JavaTypeSpec.Builder(
                        JTypeSpec.classBuilder(name.java).apply {
                            if (!isOpen) {
                                addModifiers(JModifier.FINAL)
                            }
                        }
                    )
                CodeLanguage.KOTLIN ->
                    KotlinTypeSpec.Builder(
                        KTypeSpec.classBuilder(name.kotlin).apply {
                            if (isOpen) {
                                addModifiers(KModifier.OPEN)
                            }
                        }
                    )
            }
        }

        @JvmStatic
        fun anonymousClassBuilder(
            language: CodeLanguage,
            argsFormat: String = "",
            vararg args: Any
        ): Builder {
            return when (language) {
                CodeLanguage.JAVA ->
                    JavaTypeSpec.Builder(
                        JTypeSpec.anonymousClassBuilder(
                            XCodeBlock.of(language, argsFormat, *args).let {
                                check(it is JavaCodeBlock)
                                it.actual
                            }
                        )
                    )
                CodeLanguage.KOTLIN ->
                    KotlinTypeSpec.Builder(
                        KTypeSpec.anonymousClassBuilder().apply {
                            if (args.isNotEmpty()) {
                                addSuperclassConstructorParameter(
                                    XCodeBlock.of(language, argsFormat, *args).let {
                                        check(it is KotlinCodeBlock)
                                        it.actual
                                    }
                                )
                            }
                        }
                    )
            }
        }

        @JvmStatic
        fun companionObjectBuilder(language: CodeLanguage): Builder {
            return when (language) {
                CodeLanguage.JAVA ->
                    JavaTypeSpec.Builder(
                        JTypeSpec.classBuilder("Companion")
                            .addModifiers(JModifier.PUBLIC, JModifier.STATIC)
                    )
                CodeLanguage.KOTLIN -> KotlinTypeSpec.Builder(KTypeSpec.companionObjectBuilder())
            }
        }
    }
}
