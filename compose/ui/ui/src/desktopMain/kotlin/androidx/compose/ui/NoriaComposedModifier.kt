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

package androidx.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.NoInspectorInfo
import noria.NoriaContext

/**
 * Declare a just-in-time composition of a [Modifier] that will be composed for each element it
 * modifies. [composed] may be used to implement **stateful modifiers** that have
 * instance-specific state for each modified element, allowing the same [Modifier] instance to be
 * safely reused for multiple elements while maintaining element-specific state.
 *
 * If [inspectorInfo] is specified this modifier will be visible to tools during development.
 * Specify the name and arguments of the original modifier.
 *
 * Example usage:
 * @sample androidx.compose.ui.samples.InspectorInfoInComposedModifierSample
 * @sample androidx.compose.ui.samples.InspectorInfoInComposedModifierWithArgumentsSample
 *
 * [materialize] must be called to create instance-specific modifiers if you are directly
 * applying a [Modifier] to an element tree node.
 */
fun Modifier.noriaComposed(
    inspectorInfo: InspectorInfo.() -> Unit = NoInspectorInfo,
    factory: @Composable Modifier.(NoriaContext) -> Modifier
): Modifier = this.composed(
    inspectorInfo,
    factory = { factory(NoriaContext) }
)

/**
 * Declare a just-in-time composition of a [Modifier] that will be composed for each element it
 * modifies. [composed] may be used to implement **stateful modifiers** that have
 * instance-specific state for each modified element, allowing the same [Modifier] instance to be
 * safely reused for multiple elements while maintaining element-specific state.
 *
 * When keys are provided, [composed] produces a [Modifier] that will compare [equals] to
 * another modifier constructed with the same keys in order to take advantage of caching and
 * skipping optimizations. [fullyQualifiedName] should be the fully-qualified `import` name for
 * your modifier factory function, e.g. `com.example.myapp.ui.fancyPadding`.
 *
 * If [inspectorInfo] is specified this modifier will be visible to tools during development.
 * Specify the name and arguments of the original modifier.
 *
 * Example usage:
 * @sample androidx.compose.ui.samples.InspectorInfoInComposedModifierSample
 * @sample androidx.compose.ui.samples.InspectorInfoInComposedModifierWithArgumentsSample
 *
 * [materialize] must be called to create instance-specific modifiers if you are directly
 * applying a [Modifier] to an element tree node.
 */
@ExperimentalComposeUiApi
fun Modifier.noriaComposed(
    fullyQualifiedName: String,
    key1: Any?,
    inspectorInfo: InspectorInfo.() -> Unit = NoInspectorInfo,
    factory: @Composable Modifier.(NoriaContext) -> Modifier
): Modifier = this.composed(
    fullyQualifiedName,
    key1,
    inspectorInfo,
    factory = { factory(NoriaContext) }
)

/**
 * Declare a just-in-time composition of a [Modifier] that will be composed for each element it
 * modifies. [composed] may be used to implement **stateful modifiers** that have
 * instance-specific state for each modified element, allowing the same [Modifier] instance to be
 * safely reused for multiple elements while maintaining element-specific state.
 *
 * When keys are provided, [composed] produces a [Modifier] that will compare [equals] to
 * another modifier constructed with the same keys in order to take advantage of caching and
 * skipping optimizations. [fullyQualifiedName] should be the fully-qualified `import` name for
 * your modifier factory function, e.g. `com.example.myapp.ui.fancyPadding`.
 *
 * If [inspectorInfo] is specified this modifier will be visible to tools during development.
 * Specify the name and arguments of the original modifier.
 *
 * Example usage:
 * @sample androidx.compose.ui.samples.InspectorInfoInComposedModifierSample
 * @sample androidx.compose.ui.samples.InspectorInfoInComposedModifierWithArgumentsSample
 *
 * [materialize] must be called to create instance-specific modifiers if you are directly
 * applying a [Modifier] to an element tree node.
 */
@ExperimentalComposeUiApi
fun Modifier.noriaComposed(
    fullyQualifiedName: String,
    key1: Any?,
    key2: Any?,
    inspectorInfo: InspectorInfo.() -> Unit = NoInspectorInfo,
    factory: @Composable Modifier.(NoriaContext) -> Modifier
): Modifier = this.composed(
    fullyQualifiedName,
    key1, key2,
    inspectorInfo,
    factory = { factory(NoriaContext) }
)

/**
 * Declare a just-in-time composition of a [Modifier] that will be composed for each element it
 * modifies. [composed] may be used to implement **stateful modifiers** that have
 * instance-specific state for each modified element, allowing the same [Modifier] instance to be
 * safely reused for multiple elements while maintaining element-specific state.
 *
 * When keys are provided, [composed] produces a [Modifier] that will compare [equals] to
 * another modifier constructed with the same keys in order to take advantage of caching and
 * skipping optimizations. [fullyQualifiedName] should be the fully-qualified `import` name for
 * your modifier factory function, e.g. `com.example.myapp.ui.fancyPadding`.
 *
 * If [inspectorInfo] is specified this modifier will be visible to tools during development.
 * Specify the name and arguments of the original modifier.
 *
 * Example usage:
 * @sample androidx.compose.ui.samples.InspectorInfoInComposedModifierSample
 * @sample androidx.compose.ui.samples.InspectorInfoInComposedModifierWithArgumentsSample
 *
 * [materialize] must be called to create instance-specific modifiers if you are directly
 * applying a [Modifier] to an element tree node.
 */
@ExperimentalComposeUiApi
fun Modifier.noriaComposed(
    fullyQualifiedName: String,
    key1: Any?,
    key2: Any?,
    key3: Any?,
    inspectorInfo: InspectorInfo.() -> Unit = NoInspectorInfo,
    factory: @Composable Modifier.(NoriaContext) -> Modifier
): Modifier = this.composed(
    fullyQualifiedName,
    key1, key2, key3,
    inspectorInfo,
    factory = { factory(NoriaContext) }
)

/**
 * Declare a just-in-time composition of a [Modifier] that will be composed for each element it
 * modifies. [composed] may be used to implement **stateful modifiers** that have
 * instance-specific state for each modified element, allowing the same [Modifier] instance to be
 * safely reused for multiple elements while maintaining element-specific state.
 *
 * When keys are provided, [composed] produces a [Modifier] that will compare [equals] to
 * another modifier constructed with the same keys in order to take advantage of caching and
 * skipping optimizations. [fullyQualifiedName] should be the fully-qualified `import` name for
 * your modifier factory function, e.g. `com.example.myapp.ui.fancyPadding`.
 *
 * If [inspectorInfo] is specified this modifier will be visible to tools during development.
 * Specify the name and arguments of the original modifier.
 *
 * Example usage:
 * @sample androidx.compose.ui.samples.InspectorInfoInComposedModifierSample
 * @sample androidx.compose.ui.samples.InspectorInfoInComposedModifierWithArgumentsSample
 *
 * [materialize] must be called to create instance-specific modifiers if you are directly
 * applying a [Modifier] to an element tree node.
 */
@ExperimentalComposeUiApi
fun Modifier.noriaComposed(
    fullyQualifiedName: String,
    vararg keys: Any?,
    inspectorInfo: InspectorInfo.() -> Unit = NoInspectorInfo,
    factory: @Composable Modifier.(NoriaContext) -> Modifier
): Modifier = this.composed(
    fullyQualifiedName,
    *keys,
    inspectorInfo,
    factory = { factory(NoriaContext) }
)