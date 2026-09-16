/*
 * Copyright 2021 The Android Open Source Project
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

@file:OptIn(InternalComposeUiApi::class)
@file:JvmName("SkiaColorFilter_skikoKt")

package androidx.compose.ui.graphics

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.platform.PlatformColorFilter
import androidx.compose.ui.graphics.platform.asComposeColorFilter
import androidx.compose.ui.graphics.platform.platformColorFilter
import kotlin.jvm.JvmName
import org.jetbrains.skia.ColorFilter as SkColorFilter

internal class SkikoColorFilter(
    val skiaColorFilter: SkColorFilter,
) : PlatformColorFilter()

/** Obtain a [org.jetbrains.skia.ColorFilter] instance from this [ColorFilter] */
@OptIn(InternalComposeUiApi::class)
fun ColorFilter.asSkiaColorFilter(): SkColorFilter {
    val platform = platformColorFilter
    require(platform is SkikoColorFilter) {
        "Extracting the Skia color filter reference is only supported from ColorFilters created " +
            "by the registered Skiko implementation (registerSkikoComposeImplementation()), but " +
            "the binding was ${platform::class}"
    }
    return platform.skiaColorFilter
}

/** Create a [ColorFilter] from the given [org.jetbrains.skia.ColorFilter] instance */
@OptIn(InternalComposeUiApi::class)
fun SkColorFilter.asComposeColorFilter(): ColorFilter =
    SkikoColorFilter(this).asComposeColorFilter()
