/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.foundation.gestures

import androidx.compose.runtime.compositionLocalOf

/**
 * The scroll behaviour a platform uses when nothing has been provided into
 * [LocalScrollConfig].
 */
internal expect val defaultScrollConfig: ScrollConfig

/**
 * TEMPORARY: this lives in `skikoMain` rather than `desktopMain` only so that Fleet's
 * common code, which reaches for it unconditionally, still links on web and native.
 * Outside desktop it is a stub: those platforms resolve their scroll behaviour directly
 * in [platformScrollConfig] and never read this local, so providing a value into it has
 * no effect there. Move it back to `desktopMain` once Air no longer depends on desktop
 * stubs in Web and mobile.
 */
// TODO(demin): Chrome on Windows/Linux uses different scroll strategy
//  (always the same scroll offset, bounds-independent).
//  Figure out why and decide if we can use this strategy instead of the current one.
val LocalScrollConfig = compositionLocalOf { defaultScrollConfig }
