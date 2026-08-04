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

package androidx.compose.ui.desktop

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.SemanticsOwner

// TODO these used to live in `noria.ui.core`, which is now owned entirely by Fleet's
//  `fleet.compose.noria` module. Fleet still spells them `noria.ui.core.WindowData` /
//  `noria.ui.core.LocalWindow` and aliases them there; drop the aliases once the noria façade goes.
/**
 * What a window publishes about itself once its content is laid out.
 *
 * [semanticsOwners] is a live view of the owners the window's scene currently has — the scene's
 * main owner plus one per attached `ComposeSceneLayer` — so a consumer that walks it observes
 * layer attach and detach. It is exposed as a plain [Collection] rather than as a query object
 * because any richer, product-specific view of it belongs outside Compose.
 */
data class WindowData(
    val windowId: LightweightWindowId,
    val semanticsOwners: Collection<SemanticsOwner>,
)

val LocalWindow: ProvidableCompositionLocal<Window> = staticCompositionLocalOf {
    error("LocalWindow is not provided")
}