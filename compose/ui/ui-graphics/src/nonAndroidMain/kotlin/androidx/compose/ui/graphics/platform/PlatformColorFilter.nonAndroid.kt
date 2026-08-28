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

package androidx.compose.ui.graphics.platform

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.NativeColorFilter

/**
 * Opaque platform binding held by [ColorFilter] so ui-graphics stays decoupled from any concrete
 * graphics backend. The registered backend provides the implementation and the interop extensions
 * to and from its native color-filter type.
 */
@InternalComposeUiApi
open class PlatformColorFilter

/**
 * Exposes the underlying platform color-filter binding to the registered backend so it can recover
 * its native object without depending on [ColorFilter]'s internal representation.
 */
@InternalComposeUiApi
val ColorFilter.platformColorFilter: PlatformColorFilter
    get() = nativeColorFilter

/** Wraps a platform color-filter binding into a [ColorFilter]. */
@InternalComposeUiApi
fun PlatformColorFilter.asComposeColorFilter(): ColorFilter = ColorFilter(this)
