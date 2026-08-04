/*
 * Copyright 2026 The Android Open Source Project
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

/**
 * Whether [Modifier.testData] and [Modifier.markTestSubtree] contribute anything.
 *
 * Off by default. Air sets it from `isFleetTestMode` at application init.
 *
 * Test data is published through Compose semantics, so a changed value invalidates its layout
 * node's semantics configuration. Most of Air's payloads are `State<…>`-wrapped and therefore
 * stable, but a few are not — a raw `LayoutCoordinates` republished per layout for every visible
 * tree cell, a freshly allocated lambda pair per recomposition per text link. Since test data has
 * no production consumer, gating removes that cost entirely: with the flag off both modifiers are
 * the identity, which is exactly what shipped before this was implemented.
 *
 * Read at modifier-construction time, so it must be set before the first composition that uses it.
 * Changing it afterwards affects only modifiers constructed later.
 *
 * **Stricter constraint found in review:** the three KDT window classes (`GtkWindow`,
 * `MacOsWindow`, `LinuxWindow`) gate their `semanticsOwnerListener` on this flag —
 * `get() = if (TestDataMode.isEnabled) this else null` — and owner registration is event-driven
 * and happens exactly once per owner: the main owner registers in
 * `CanvasLayersComposeSceneImpl.init`, and each layer registers on attach and unregisters on
 * close. There is no re-registration path. That means this flag must be correct *before any
 * window or scene layer is constructed*, which is stricter than "before the first composition":
 * flipping it to `true` after a window has already attached leaves that window's entire subtree
 * permanently invisible to `getAllTestNodes()`, because `onSemanticsOwnerAppended` already fired
 * with a `null` listener for that owner and is never retried. Conversely, flipping it to `false`
 * while a layer is live and then letting that layer detach leaves a stale `SemanticsOwner` in the
 * registered set forever, since `onSemanticsOwnerRemoved` will also observe a `null` listener at
 * that point. Set this before constructing the first window or scene, not merely before the first
 * composition.
 */
object TestDataMode {
    var isEnabled: Boolean = false
}
