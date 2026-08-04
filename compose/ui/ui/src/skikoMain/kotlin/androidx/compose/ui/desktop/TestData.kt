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

package noria.ui.core

import androidx.compose.ui.Modifier

fun <T : Any> Modifier.testData(key: TestDataKey<T>, value: T): Modifier =
    if (!TestDataMode.isEnabled) this
    else this then TestDataElement(TestDataEntry.Payload(key, value))

data class TestDataKey<T : Any>(val id: String)

abstract class TestDataKeys(private val prefix: String) {
    fun <T : Any> key(id: String): TestDataKey<T> = TestDataKey("${prefix}.${id}")

    fun path(id: String): String = "${prefix}.${id}"
}

/**
 * Brackets this element's test-data subtree with `Enter(id)` / `Exit(id)` sentinel nodes in the
 * flattened stream, reproducing Noria's `markTestSubtree`.
 *
 * KNOWN DIVERGENCE FROM NORIA: overlay content is no longer nested under its anchor. Noria's
 * `Modifier.overlay` emitted its focus node at the anchor, so popup test data fell inside the
 * anchor's subtree. This fork's `noria.foundation.layout.OverlayHost` subcomposes overlays as
 * siblings under the host box, so popup test data lands at the end of the window's stream instead,
 * and a path query that crosses a popup boundary will not match. See
 * `TestDataTest.overlayContentIsNotNestedUnderItsAnchor_knownDivergenceFromNoria`.
 */
fun Modifier.markTestSubtree(id: String): Modifier =
    if (!TestDataMode.isEnabled) this
    else this then TestDataElement(TestDataEntry.Subtree(id))
