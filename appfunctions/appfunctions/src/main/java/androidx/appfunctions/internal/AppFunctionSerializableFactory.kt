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

package androidx.appfunctions.internal

import androidx.annotation.RestrictTo
import androidx.appfunctions.AppFunctionData

/**
 * An interface for factory classes that convert between a class annotated with
 * [androidx.appfunctions.AppFunctionSerializable] and [androidx.appfunctions.AppFunctionData].
 *
 * Each class annotated with [androidx.appfunctions.AppFunctionSerializable] will have a generated
 * class that implements this interface.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AppFunctionSerializableFactory<T : Any> {
    /**
     * Deserializes the given [androidx.appfunctions.AppFunctionData] into an instance of the
     * AppFunctionSerializable annotated class.
     *
     * Type mismatch: An [IllegalArgumentException] if a property is stored as a different type in
     * [appFunctionData].
     */
    public fun fromAppFunctionData(appFunctionData: AppFunctionData): T

    /** Serializes the given class into an [AppFunctionData]. */
    public fun toAppFunctionData(appFunctionSerializable: T): AppFunctionData

    /**
     * Contains the information about the type parameter.
     *
     * The class is used by [AppFunctionSerializableFactory] for generic serializable to resolve the
     * type information in runtime.
     */
    public sealed class TypeParameter<T> {
        /** The [TypeParameter] for Kotlin primitive types. */
        public data class PrimitiveTypeParameter<T>(
            /** The type class. */
            val clazz: Class<T>,
        ) : TypeParameter<T>()

        /** The [TypeParameter] for [List] type. */
        public data class ListTypeParameter<I, T : List<*>?>(
            /** The item type class. */
            val itemClazz: Class<I>,
        ) : TypeParameter<T>()
    }
}
