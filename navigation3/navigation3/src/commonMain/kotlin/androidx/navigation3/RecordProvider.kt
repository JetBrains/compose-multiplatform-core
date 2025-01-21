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

package androidx.navigation3

import androidx.compose.runtime.Composable
import kotlin.reflect.KClass

@DslMarker public annotation class RecordDsl

/** Creates an [RecordProviderBuilder] with the record providers provided in the builder. */
public inline fun recordProvider(
    noinline fallback: (unknownScreen: Any) -> NavRecord<*> = {
        throw IllegalStateException("Unknown screen $it")
    },
    builder: RecordProviderBuilder.() -> Unit
): (Any) -> NavRecord<*> = RecordProviderBuilder(fallback).apply(builder).build()

/** DSL for constructing a new [NavRecord] */
@Suppress("TopLevelBuilder")
@RecordDsl
public class RecordProviderBuilder(private val fallback: (unknownScreen: Any) -> NavRecord<*>) {
    private val clazzProviders = mutableMapOf<KClass<*>, RecordClassProvider<*>>()
    private val providers = mutableMapOf<Any, RecordProvider<*>>()

    /** Builds a [NavRecord] for the given [key] that displays [content]. */
    @Suppress("SetterReturnsThis", "MissingGetterMatchingBuilder")
    public fun <T : Any> addRecordProvider(
        key: T,
        featureMap: Map<String, Any> = emptyMap(),
        content: @Composable (T) -> Unit,
    ) {
        require(key !in providers) {
            "A `record` with the key `key` has already been added: ${key}."
        }
        providers[key] = RecordProvider(key, featureMap, content)
    }

    /** Builds a [NavRecord] for the given [clazz] that displays [content]. */
    @Suppress("SetterReturnsThis", "MissingGetterMatchingBuilder")
    public fun <T : Any> addRecordProvider(
        clazz: KClass<T>,
        featureMap: Map<String, Any> = emptyMap(),
        content: @Composable (T) -> Unit,
    ) {
        require(clazz !in clazzProviders) {
            "A `record` with the same `clazz` has already been added: ${clazz.simpleName}."
        }
        clazzProviders[clazz] = RecordClassProvider(clazz, featureMap, content)
    }

    /**
     * Returns an instance of recordProvider created from the record providers set on this builder.
     */
    @Suppress("UNCHECKED_CAST")
    public fun build(): (Any) -> NavRecord<*> = { key ->
        val recordClassProvider = clazzProviders[key::class] as? RecordClassProvider<Any>
        val recordProvider = providers[key] as? RecordProvider<Any>
        recordClassProvider?.run { NavRecord(key, featureMap, content) }
            ?: recordProvider?.run { NavRecord(key, featureMap, content) }
            ?: fallback.invoke(key)
    }
}

/** Add an record provider to the [RecordProviderBuilder] */
public fun <T : Any> RecordProviderBuilder.record(
    key: T,
    featureMap: Map<String, Any> = emptyMap(),
    content: @Composable (T) -> Unit,
) {
    addRecordProvider(key, featureMap, content)
}

/** Add an record provider to the [RecordProviderBuilder] */
public inline fun <reified T : Any> RecordProviderBuilder.record(
    featureMap: Map<String, Any> = emptyMap(),
    noinline content: @Composable (T) -> Unit,
) {
    addRecordProvider(T::class, featureMap, content)
}

/** Holds a Record class, featureMap, and content for that class */
public data class RecordClassProvider<T : Any>(
    val clazz: KClass<T>,
    val featureMap: Map<String, Any>,
    val content: @Composable (T) -> Unit,
)

/** Holds a Record class, featureMap, and content for that key */
public data class RecordProvider<T : Any>(
    val key: T,
    val featureMap: Map<String, Any>,
    val content: @Composable (T) -> Unit,
)
