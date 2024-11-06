/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.room.compiler.codegen.compat

import androidx.room.compiler.codegen.JAnnotationSpecBuilder
import androidx.room.compiler.codegen.JCodeBlockBuilder
import androidx.room.compiler.codegen.JFunSpecBuilder
import androidx.room.compiler.codegen.JPropertySpecBuilder
import androidx.room.compiler.codegen.JTypeSpecBuilder
import androidx.room.compiler.codegen.KAnnotationSpecBuilder
import androidx.room.compiler.codegen.KCodeBlockBuilder
import androidx.room.compiler.codegen.KFunSpecBuilder
import androidx.room.compiler.codegen.KPropertySpecBuilder
import androidx.room.compiler.codegen.KTypeSpecBuilder
import androidx.room.compiler.codegen.XAnnotationSpec
import androidx.room.compiler.codegen.XClassName
import androidx.room.compiler.codegen.XCodeBlock
import androidx.room.compiler.codegen.XFunSpec
import androidx.room.compiler.codegen.XMemberName
import androidx.room.compiler.codegen.XName
import androidx.room.compiler.codegen.XPropertySpec
import androidx.room.compiler.codegen.XTypeName
import androidx.room.compiler.codegen.XTypeSpec
import androidx.room.compiler.codegen.java.JavaAnnotationSpec
import androidx.room.compiler.codegen.java.JavaCodeBlock
import androidx.room.compiler.codegen.java.JavaFunSpec
import androidx.room.compiler.codegen.java.JavaPropertySpec
import androidx.room.compiler.codegen.java.JavaTypeSpec
import androidx.room.compiler.codegen.kotlin.KotlinAnnotationSpec
import androidx.room.compiler.codegen.kotlin.KotlinCodeBlock
import androidx.room.compiler.codegen.kotlin.KotlinFunSpec
import androidx.room.compiler.codegen.kotlin.KotlinPropertySpec
import androidx.room.compiler.codegen.kotlin.KotlinTypeSpec
import androidx.room.compiler.processing.XNullability
import com.squareup.kotlinpoet.javapoet.JClassName
import com.squareup.kotlinpoet.javapoet.JTypeName
import com.squareup.kotlinpoet.javapoet.KClassName
import com.squareup.kotlinpoet.javapoet.KTypeName
import com.squareup.kotlinpoet.javapoet.toJTypeName
import com.squareup.kotlinpoet.javapoet.toKTypeName

object XConverters {
    @JvmStatic fun XName.toJavaPoet() = java

    @JvmStatic fun XMemberName.toJavaPoet() = java

    @JvmStatic fun XTypeName.toJavaPoet() = java

    @JvmStatic fun XClassName.toJavaPoet() = java

    @JvmStatic
    fun XAnnotationSpec.toJavaPoet() =
        when (this) {
            is JavaAnnotationSpec -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XAnnotationSpec.Builder.toJavaPoet() =
        when (this) {
            is JavaAnnotationSpec.Builder -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XCodeBlock.toJavaPoet() =
        when (this) {
            is JavaCodeBlock -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XCodeBlock.Builder.toJavaPoet() =
        when (this) {
            is JavaCodeBlock.Builder -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XFunSpec.toJavaPoet() =
        when (this) {
            is JavaFunSpec -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XFunSpec.Builder.toJavaPoet() =
        when (this) {
            is JavaFunSpec.Builder -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XPropertySpec.toJavaPoet() =
        when (this) {
            is JavaPropertySpec -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XPropertySpec.Builder.toJavaPoet() =
        when (this) {
            is JavaPropertySpec.Builder -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XTypeSpec.toJavaPoet() =
        when (this) {
            is JavaTypeSpec -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XTypeSpec.Builder.toJavaPoet() =
        when (this) {
            is JavaTypeSpec.Builder -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic fun XName.toKotlinPoet() = kotlin

    @JvmStatic fun XMemberName.toKotlinPoet() = kotlin

    @JvmStatic fun XTypeName.toKotlinPoet() = kotlin

    @JvmStatic fun XClassName.toKotlinPoet() = kotlin

    @JvmStatic
    fun XAnnotationSpec.toKotlinPoet() =
        when (this) {
            is KotlinAnnotationSpec -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XAnnotationSpec.Builder.toKotlinPoet() =
        when (this) {
            is KotlinAnnotationSpec.Builder -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XCodeBlock.toKotlinPoet() =
        when (this) {
            is KotlinCodeBlock -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XCodeBlock.Builder.toKotlinPoet() =
        when (this) {
            is KotlinCodeBlock.Builder -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XFunSpec.toKotlinPoet() =
        when (this) {
            is KotlinFunSpec -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XFunSpec.Builder.toKotlinPoet() =
        when (this) {
            is KotlinFunSpec.Builder -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XPropertySpec.toKotlinPoet() =
        when (this) {
            is KotlinPropertySpec -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XPropertySpec.Builder.toKotlinPoet() =
        when (this) {
            is KotlinPropertySpec.Builder -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XTypeSpec.toKotlinPoet() =
        when (this) {
            is KotlinTypeSpec -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun XTypeSpec.Builder.toKotlinPoet() =
        when (this) {
            is KotlinTypeSpec.Builder -> actual
            else -> error("Unsupported type: $this")
        }

    @JvmStatic
    fun JClassName.toXPoet() =
        XClassName.get(this.packageName(), *this.simpleNames().toTypedArray())

    @JvmStatic
    fun KClassName.toXPoet() = XClassName.get(this.packageName, *this.simpleNames.toTypedArray())

    @JvmStatic
    fun toXPoet(jClassName: JClassName, kClassName: KClassName) =
        XClassName(jClassName, kClassName, kClassName.nullability())

    @JvmStatic fun JTypeName.toXPoet() = toXPoet(this, toKTypeName())

    @JvmStatic fun KTypeName.toXPoet() = toXPoet(this.toJTypeName(), this)

    @JvmStatic
    fun toXPoet(jTypeName: JTypeName, kTypeName: KTypeName) =
        XTypeName(jTypeName, kTypeName, kTypeName.nullability())

    private fun KTypeName.nullability() =
        if (isNullable) {
            XNullability.NULLABLE
        } else {
            XNullability.UNKNOWN
        }

    @JvmStatic
    fun XAnnotationSpec.Builder.applyToJavaPoet(block: JAnnotationSpecBuilder.() -> Unit) = apply {
        if (this is JavaAnnotationSpec.Builder) {
            actual.block()
        }
    }

    @JvmStatic
    fun XCodeBlock.Builder.applyToJavaPoet(block: JCodeBlockBuilder.() -> Unit) = apply {
        if (this is JavaCodeBlock.Builder) {
            actual.block()
        }
    }

    @JvmStatic
    fun XFunSpec.Builder.applyToJavaPoet(block: JFunSpecBuilder.() -> Unit) = apply {
        if (this is JavaFunSpec.Builder) {
            actual.block()
        }
    }

    @JvmStatic
    fun XPropertySpec.Builder.applyToJavaPoet(block: JPropertySpecBuilder.() -> Unit) = apply {
        if (this is JavaPropertySpec.Builder) {
            actual.block()
        }
    }

    @JvmStatic
    fun XTypeSpec.Builder.applyToJavaPoet(block: JTypeSpecBuilder.() -> Unit) = apply {
        if (this is JavaTypeSpec.Builder) {
            actual.block()
        }
    }

    @JvmStatic
    fun XAnnotationSpec.Builder.applyToKotlinPoet(block: KAnnotationSpecBuilder.() -> Unit) =
        apply {
            if (this is KotlinAnnotationSpec.Builder) {
                actual.block()
            }
        }

    @JvmStatic
    fun XCodeBlock.Builder.applyToKotlinPoet(block: KCodeBlockBuilder.() -> Unit) = apply {
        if (this is KotlinCodeBlock.Builder) {
            actual.block()
        }
    }

    @JvmStatic
    fun XFunSpec.Builder.applyToKotlinPoet(block: KFunSpecBuilder.() -> Unit) = apply {
        if (this is KotlinFunSpec.Builder) {
            actual.block()
        }
    }

    @JvmStatic
    fun XPropertySpec.Builder.applyToKotlinPoet(block: KPropertySpecBuilder.() -> Unit) = apply {
        if (this is KotlinPropertySpec.Builder) {
            actual.block()
        }
    }

    @JvmStatic
    fun XTypeSpec.Builder.applyToKotlinPoet(block: KTypeSpecBuilder.() -> Unit) = apply {
        if (this is KotlinTypeSpec.Builder) {
            actual.block()
        }
    }
}
